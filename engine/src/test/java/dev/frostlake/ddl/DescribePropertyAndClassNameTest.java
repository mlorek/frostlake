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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DESCRIBE takes generic properties after its object, refused as invalid parameters; DESC ACCOUNT names no
 * account; a word written after a DESC that names no kind ends the statement; and SHOW and DROP read a name
 * that is no kind of theirs as a class, which does not exist.
 */
public class DescribePropertyAndClassNameTest extends BaseDatabaseTest {

    /** Every row's cells, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                out.append(out.length() > 0 ? " | " : "").append(row.getValues());
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private static String invalidParameter(final String name) {
        return "SQL compilation error:|invalid parameter '" + name + "'";
    }

    private static String noClass(final String name) {
        return "SQL compilation error: Object type or Class '" + name + "' does not exist or not authorized.";
    }

    private static final String NO_OBJECT = "SQL compilation error:|Object does not exist, or operation cannot be performed.";

    @Test
    public void aPropertyAfterTheObjectIsAnInvalidParameter() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        assertEquals(invalidParameter("x"), answer("DESCRIBE TABLE t1 x = 1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE TABLE nosuch x = 'a'"));
        assertEquals(invalidParameter("\"x\""), answer("DESCRIBE TABLE t1 \"x\" = 1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE TABLE t1 x = y.z"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE TABLE t1 x = -1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE TABLE t1 x = (1)"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE TABLE t1 x = NULL"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE TABLE t1 x = y z = 1"));
        assertEquals(invalidParameter("LIMIT"), answer("DESCRIBE TABLE t1 LIMIT = 1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE TABLE t1 x = 1 TYPE = foo"));
        assertEquals("SQL compilation error:|invalid value [foo] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 TYPE = foo x = 1"));
        assertEquals("SQL compilation error:|invalid value [1.5] for parameter 'TYPE'",
            answer("DESCRIBE TABLE t1 TYPE = 1.5"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE SCHEMA test_schema x = 1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE DATABASE test_db x = 1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE STREAM s x = 1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE WAREHOUSE w x = 1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE FUNCTION f(INT) x = 1"));
        assertEquals(invalidParameter("x"), answer("DESCRIBE STAGE st x = 1"));
        assertEquals(syntax(24, "+"), answer("DESCRIBE TABLE t1 x = 1 + 2"));
        assertEquals(syntax(24, "AND"), answer("DESCRIBE TABLE t1 x = 1 AND 2"));
        assertEquals(syntax(23, "<EOF>"), answer("DESCRIBE TABLE t1 x = -"));
    }

    @Test
    public void aPropertyWithoutItsValueRunsIntoTheNextToken() {
        assertEquals(syntax(27, "<EOF>"), answer("DESCRIBE DATABASE test_db x"));
        assertEquals(syntax(28, "y"), answer("DESCRIBE DATABASE test_db x y"));
        assertEquals(syntax(30, "<EOF>"), answer("DESCRIBE DATABASE test_db TYPE"));
        assertEquals(syntax(29, "<EOF>"), answer("DESCRIBE DATABASE test_db x ="));
        assertEquals(syntax(19, "<EOF>"), answer("DESCRIBE TABLE t1 x"));
        assertEquals(syntax(34, "<EOF>"), answer("DESCRIBE TABLE t1 TYPE = COLUMNS x"));
    }

    @Test
    public void anAccountIsNeverFound() {
        assertEquals(NO_OBJECT, answer("DESC ACCOUNT x"));
        assertEquals(NO_OBJECT, answer("DESC ACCOUNT \"x\""));
        assertEquals(NO_OBJECT, answer("DESC ACCOUNT x.y"));
        assertEquals(NO_OBJECT, answer("DESC ACCOUNT IDENTIFIER('x')"));
        assertEquals(invalidParameter("y"), answer("DESC ACCOUNT x y = 1"));
        assertEquals(syntax(16, "<EOF>"), answer("DESCRIBE ACCOUNT"));
        assertEquals(syntax(16, "<EOF>"), answer("DESC ACCOUNT x y"));
        assertEquals(syntax(15, "="), answer("DESC ACCOUNT x = 1"));
    }

    @Test
    public void aTypeAfterAKindlessDescribeIsRefusedAtTheType() {
        assertEquals(syntax(10, "TYPE"), answer("DESC t1 x TYPE = STAGE"));
        assertEquals(syntax(10, "TYPE"), answer("DESC t1 x TYPE = COLUMNS"));
        assertEquals(syntax(10, "TYPE"), answer("DESC t1 x TYPE"));
        assertEquals("Unsupported feature 'DESCRIBE T1'.", answer("DESC t1 x"));
    }

    @Test
    public void showReadsAnUnknownNameAsAClass() {
        assertEquals(noClass("\"x\""), answer("SHOW \"x\""));
        assertEquals(noClass("X"), answer("SHOW x"));
        assertEquals(noClass("X"), answer("SHOW \"X\""));
        assertEquals(noClass("\"a b\""), answer("SHOW \"a b\""));
        assertEquals(noClass("XYZ"), answer("SHOW xyz LIKE 'a'"));
        assertEquals(noClass("X"), answer("SHOW TERSE x"));
        assertEquals(noClass("X"), answer("SHOW x IN ACCOUNT"));
        assertEquals(noClass("X"), answer("SHOW x IN DATABASE nosuch"));
        assertEquals(noClass("X"), answer("SHOW x STARTS WITH 'a'"));
        assertEquals(noClass("\"x\""), answer("SHOW \"x\" LIMIT 1"));
        assertEquals(noClass("TEST_DB.TEST_SCHEMA.CLS"), answer("SHOW test_schema.cls"));
        assertEquals(noClass("TEST_DB.PUBLIC.CLS"), answer("SHOW test_db..cls"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.X' does not exist or not authorized."), answer("SHOW x.y"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.USER' does not exist or not authorized."),
            answer("SHOW USER.x"));
        assertEquals(hinted("SQL compilation error:|Database 'X' does not exist or not authorized."), answer("SHOW x.y.z"));
        assertEquals(NO_OBJECT, answer("SHOW x.y.z.w"));
        assertEquals(syntax(7, "y"), answer("SHOW x y"));
        assertEquals(syntax(9, "x"), answer("SHOW \"x\" x"));
        assertEquals(syntax(11, "<EOF>"), answer("SHOW x LIKE"));
        assertEquals(syntax(5, "T"), answer("SHOW T"));
        assertEquals(syntax(5, "ALERT"), answer("SHOW ALERT"));
        assertEquals(syntax(9, "<EOF>"), answer("SHOW USER"));
        assertEquals(syntax(5, "1"), answer("SHOW 1"));
    }

    @Test
    public void dropReadsAnUnknownKindAsAClass() {
        assertEquals(syntax(8, "<EOF>"), answer("DROP \"x\""));
        assertEquals(syntax(6, "<EOF>"), answer("DROP x"));
        assertEquals(syntax(8, "<EOF>"), answer("DROP x.y"));
        assertEquals(syntax(10, "<EOF>"), answer("DROP \"x\".y"));
        assertEquals(syntax(18, "<EOF>"), answer("DROP \"x\" IF EXISTS"));
        assertEquals(noClass("\"x\""), answer("DROP \"x\" y"));
        assertEquals(noClass("X"), answer("DROP x y"));
        assertEquals(noClass("TABLEX"), answer("DROP TABLEX t"));
        assertEquals(noClass("X"), answer("DROP x IF EXISTS y"));
        assertEquals(noClass("X"), answer("DROP x \"y\""));
        assertEquals(noClass("\"x\""), answer("DROP \"x\" CASCADE"));
        assertEquals(noClass("X"), answer("DROP x RESTRICT"));
        assertEquals("Unsupported feature 'DROP X'.", answer("DROP x y CASCADE"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.X' does not exist or not authorized."), answer("DROP x.y z"));
        assertEquals(hinted("SQL compilation error:|Schema 'TEST_DB.USER' does not exist or not authorized."),
            answer("DROP USER.x y"));
        assertEquals(hinted("SQL compilation error:|Database 'X' does not exist or not authorized."), answer("DROP x.y.z w"));
        assertEquals(NO_OBJECT, answer("DROP x.y.z.w v"));
        assertEquals(syntax(9, "z"), answer("DROP x y z"));
        assertEquals(syntax(5, "1"), answer("DROP 1"));
    }
}
