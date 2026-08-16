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

/**
 * A name used twice in one column list, which Frostlake ACCEPTED — creating a table with two columns of
 * the same name, the quiet shape that nothing marks until a query cannot say which one it means.
 *
 * <p>★ THE QUOTED PAIR IS THE WHOLE RULE, and it is why the comparison is over CANONICAL names rather
 * than the text as written:
 *
 * <pre>
 *   (c INT, "c" INT)     ACCEPTED — a quoted lowercase c is a DIFFERENT column
 *   ("C" INT, c INT)     duplicate column name 'C'
 *   ("c" INT, "c" INT)   duplicate column name 'c'   ← the echo keeps the quoted case
 * </pre>
 *
 * The third cell is the one that shows the echoed name is the CANONICAL one and not simply upper-cased.
 *
 * <p>★ A BAD WIDTH OUTRANKS IT, which is why the check runs after the column list is parsed rather than
 * while it is read: {@code (c VARCHAR(0), c INT)} reports the character-length refusal.
 *
 * <p>★ THE SAME SENTENCE COVERS THE LISTS THAT CARRY NO TYPES — a CTAS's names-only list and a view's
 * column list — so it belongs to the list, not to the column definition.
 *
 * <p>★ A STRUCTURED OBJECT'S FIELDS HAVE A DIFFERENT SENTENCE ON PURPOSE: capital D, the name VERBATIM
 * rather than canonical, and a POSITION. Two rules that look alike and are not.
 */
public class DuplicateColumnNameTest extends BaseDatabaseTest {

    private String statement(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        } finally {
            try {
                engine.execute("DROP TABLE IF EXISTS dupc");
                engine.execute("DROP VIEW IF EXISTS dupv");
            } catch (final RuntimeException ignored) {
                // cleanup only
            }
        }
    }

    private static final String DUP_C = "SQL compilation error:|duplicate column name 'C'";

    /** ★ The headline, and the case rule: unquoted names fold, so c and C collide. */
    @Test
    public void arepeatedNameIsRefused() {
        assertEquals(DUP_C, statement("CREATE OR REPLACE TABLE dupc (c INT, c INT)"));
        assertEquals(DUP_C, statement("CREATE OR REPLACE TABLE dupc (c INT, C INT)"));
    }

    /** Three of the same name report ONCE, not once per pair. */
    @Test
    public void threeOfTheSameNameReportOnce() {
        assertEquals(DUP_C, statement("CREATE OR REPLACE TABLE dupc (c INT, c INT, c INT)"));
    }

    /** ★ THE QUOTED PAIR: a quoted lowercase name is a different column; a quoted upper one collides. */
    @Test
    public void aquotedNameIsComparedCanonically() {
        assertEquals("ACCEPTED", statement("CREATE OR REPLACE TABLE dupc (c INT, \"c\" INT)"),
            "quoted lowercase c is not the unquoted C");
        assertEquals(DUP_C, statement("CREATE OR REPLACE TABLE dupc (\"C\" INT, c INT)"));
    }

    /** ★ The echoed name is the CANONICAL one, so a quoted pair reports its own case. */
    @Test
    public void theechoedNameKeepsTheQuotedCase() {
        assertEquals("SQL compilation error:|duplicate column name 'c'",
            statement("CREATE OR REPLACE TABLE dupc (\"c\" INT, \"c\" INT)"));
    }

    /** ★ A bad WIDTH outranks the duplicate, which fixes where the check runs. */
    @Test
    public void abadWidthOutranksTheDuplicate() {
        assertEquals("SQL compilation error: error line 1 at position 40|Invalid character length: 0."
            + " Must be between 1 and 134,217,728.",
            statement("CREATE OR REPLACE TABLE dupc (c VARCHAR(0), c INT)"));
    }

    /** ★ The lists that carry no types share the sentence. */
    @Test
    public void thenameOnlyListsShareIt() {
        assertEquals(DUP_C, statement("CREATE OR REPLACE TABLE dupc (c, c) AS SELECT 1, 2"));
        assertEquals(DUP_C, statement("CREATE OR REPLACE VIEW dupv (c, c) AS SELECT 1, 2"));
    }

    /** ★ A structured OBJECT's fields refuse with a DIFFERENT sentence, positioned and verbatim. */
    @Test
    public void astructuredFieldHasItsOwnSentence() {
        assertEquals("SQL compilation error: error line 1 at position 46|Duplicate field name 'x'",
            statement("CREATE OR REPLACE TABLE dupc (o OBJECT(x INT, x INT))"));
    }

    /** Distinct names are untouched. */
    @Test
    public void distinctNamesAreUntouched() {
        assertEquals("ACCEPTED", statement("CREATE OR REPLACE TABLE dupc (a INT, b INT)"));
    }
}
