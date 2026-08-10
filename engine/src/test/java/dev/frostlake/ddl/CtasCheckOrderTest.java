/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CTAS compiles its BODY before either of its column checks — the order live reports in, and the one
 * a CREATE VIEW already followed. Frostlake checked the column names first, so a statement that was
 * both unnamable and uncompilable answered "Missing column specification" where live answers with the
 * body's own refusal.
 *
 * <pre>
 *   AS SELECT UPPER(s) FROM ss            Missing column specification      unnamable ALONE
 *   AS SELECT UPPER(o) AS c FROM ss       the body's argument-type error    uncompilable ALONE
 *   AS SELECT UPPER(o) FROM ss            the body's argument-type error    BOTH — the body wins
 *   (a, b) AS SELECT s AS c FROM ss       Invalid column definition list    a wrong COUNT
 *   (a, b) AS SELECT UPPER(o) AS c …      the body's argument-type error    both — the body again
 * </pre>
 *
 * <p>The count check is new here as well: the names-only list padded the missing name from the result
 * and created the table, and the typed list reached the write path and failed with "Row column count
 * mismatch" — a sentence the account does not have.
 */
public class CtasCheckOrderTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ss (o OBJECT, s VARCHAR(10), n NUMBER)");
        engine.execute("INSERT INTO ss SELECT OBJECT_CONSTRUCT('k', 1), 'abc', 1");
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    /** Each check alone reports itself. */
    @Test
    public void eachCheckAloneReportsItself() {
        assertEquals("SQL compilation error: Missing column specification",
            refusal("CREATE TABLE c1 AS SELECT UPPER(s) FROM ss"));
        assertTrue(refusal("CREATE TABLE c2 AS SELECT UPPER(o) AS c FROM ss")
            .contains("error line 1 at position 26 Invalid argument types for function 'UPPER'"),
            refusal("CREATE TABLE c2 AS SELECT UPPER(o) AS c FROM ss"));
    }

    /** Together, the BODY wins — for an unnamable item and for an unknown function alike. */
    @Test
    public void theBodyWinsOverTheNamingCheck() {
        assertTrue(refusal("CREATE TABLE c3 AS SELECT UPPER(o) FROM ss")
            .contains("error line 1 at position 26 Invalid argument types for function 'UPPER'"),
            refusal("CREATE TABLE c3 AS SELECT UPPER(o) FROM ss"));
        assertTrue(refusal("CREATE TABLE c7 AS SELECT nosuchfn(s) FROM ss")
            .contains("Unknown function NOSUCHFN."),
            refusal("CREATE TABLE c7 AS SELECT nosuchfn(s) FROM ss"));
        assertTrue(refusal("CREATE TABLE c8 AS SELECT x FROM nosuchtable")
            .contains("Object 'NOSUCHTABLE' does not exist"),
            refusal("CREATE TABLE c8 AS SELECT x FROM nosuchtable"));
    }

    /** A wrong column COUNT is refused, in both spellings of the list. */
    @Test
    public void aWrongColumnCountIsRefused() {
        assertEquals("SQL compilation error: Invalid column definition list",
            refusal("CREATE TABLE c4 (a, b) AS SELECT s AS c FROM ss"));
        assertEquals("SQL compilation error: Invalid column definition list",
            refusal("CREATE TABLE c12 (a INT, b INT) AS SELECT s AS c FROM ss"));
    }

    /** And the body wins over THAT check too. */
    @Test
    public void theBodyWinsOverTheCountCheck() {
        assertTrue(refusal("CREATE TABLE c5 (a, b) AS SELECT UPPER(o) AS c FROM ss")
            .contains("error line 1 at position 33 Invalid argument types for function 'UPPER'"),
            refusal("CREATE TABLE c5 (a, b) AS SELECT UPPER(o) AS c FROM ss"));
        assertTrue(refusal("CREATE TABLE c14 (a INT, b INT) AS SELECT UPPER(o) AS c FROM ss")
            .contains("error line 1 at position 42 Invalid argument types for function 'UPPER'"),
            refusal("CREATE TABLE c14 (a INT, b INT) AS SELECT UPPER(o) AS c FROM ss"));
    }

    /** The statements that are fine stay fine — a star, a plain column, a matching list. */
    @Test
    public void theLegalFormsAreUntouched() {
        assertEquals("accepted", refusal("CREATE TABLE c9 AS SELECT * FROM ss"));
        assertEquals("accepted", refusal("CREATE TABLE c11 AS SELECT s FROM ss"));
        assertEquals("accepted", refusal("CREATE TABLE c13 (a VARCHAR) AS SELECT s AS c FROM ss"));
        assertEquals("accepted", refusal("CREATE TABLE c15 (a, b, c) AS SELECT o, s, n FROM ss"));
    }

    /** An unaliased LITERAL is unnamable too — a column reference names itself, a literal does not. */
    @Test
    public void aLiteralIsUnnamableButAColumnIsNot() {
        assertEquals("SQL compilation error: Missing column specification",
            refusal("CREATE TABLE c10 AS SELECT 1 FROM ss"));
        assertEquals("accepted", refusal("CREATE TABLE c16 AS SELECT n FROM ss"));
    }
}
