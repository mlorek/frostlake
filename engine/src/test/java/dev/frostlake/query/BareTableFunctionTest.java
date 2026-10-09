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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A FROM clause reads a table function's call inside TABLE( … ) or after LATERAL and nowhere else: written bare,
 * the parenthesis after the name is a syntax error, whatever the name is - FLATTEN, a table's, a scalar
 * function's.
 */
public class BareTableFunctionTest extends BaseDatabaseTest {

    private static final String FLATTEN = "FLATTEN(INPUT => PARSE_JSON('[1]'))";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("CREATE OR REPLACE FUNCTION tf1(n INT) RETURNS TABLE (v INT) AS 'SELECT n'");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private void refusedAt(final int position, final String sql) {
        final String refusal = refusalOf(sql);
        assertTrue(refusal.startsWith("SQL compilation error:\nsyntax error line 1 at position " + position
            + " unexpected '('."), sql + " -> " + refusal);
    }

    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aBareCallIsRefusedAtItsParenthesis() {
        refusedAt(21, "SELECT * FROM FLATTEN(input => PARSE_JSON('[1]'))");
        refusedAt(25, "SELECT * FROM FZ, FLATTEN(INPUT => PARSE_JSON('[1]')) f");
        refusedAt(29, "SELECT 1 FROM FZ JOIN FLATTEN(INPUT => PARSE_JSON('[1]')) f ON TRUE");
        refusedAt(34, "SELECT 1 FROM FZ LEFT JOIN FLATTEN(INPUT => PARSE_JSON('[1]')) f");
        refusedAt(35, "SELECT 1 FROM FZ CROSS JOIN FLATTEN(INPUT => PARSE_JSON('[1]')) f");
        refusedAt(30, "SELECT COUNT(*) FROM GENERATOR(ROWCOUNT => 3)");
        refusedAt(28, "SELECT * FROM SPLIT_TO_TABLE('a,b', ',')");
        refusedAt(25, "SELECT * FROM RESULT_SCAN(LAST_QUERY_ID())");
        refusedAt(52, "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TASK_HISTORY() WHERE 1 = 0");
        refusedAt(17, "SELECT * FROM tf1(1)");
        refusedAt(32, "WITH c AS (SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]'))) SELECT COUNT(*) FROM c");
        refusedAt(43, "SELECT COUNT(*) FROM (SELECT * FROM FLATTEN(INPUT => PARSE_JSON('[1]')))");
    }

    @Test
    public void aTablesNameOrAScalarFunctionsIsReadNoDifferently() {
        refusedAt(16, "SELECT * FROM t1('x')");
        refusedAt(17, "SELECT * FROM t1 ('x')");
        refusedAt(17, "SELECT * FROM t1 (a)");
        refusedAt(17, "SELECT * FROM t1 ()");
        refusedAt(30, "SELECT * FROM IDENTIFIER('t1')('x')");
        refusedAt(19, "SELECT 1 FROM UPPER('a') z");
        refusedAt(23, "SELECT 1 FROM FZ, UPPER('a') z");
        refusedAt(27, "SELECT 1 FROM FZ JOIN UPPER('a') z ON TRUE");
        refusedAt(24, "DELETE FROM t1 USING t2 ('x')");
        refusedAt(28, "UPDATE t1 SET a = 1 FROM t2 ('x')");
        refusedAt(32, "INSERT INTO t2 SELECT 1 FROM t1 ('x')");
        // A view's query is read the same way when the view is created.
        refusedAt(57, "CREATE OR REPLACE VIEW btf_v AS SELECT * FROM fz, FLATTEN(INPUT => PARSE_JSON('[1]')) f");
    }

    @Test
    public void aCallIsReadInsideTableAndAfterLateral() {
        assertEquals("1", scalar("SELECT COUNT(*) FROM TABLE(" + FLATTEN + ")"));
        assertEquals("1", scalar("SELECT COUNT(*) FROM LATERAL " + FLATTEN));
        assertEquals("2", scalar("SELECT COUNT(*) FROM FZ, LATERAL " + FLATTEN + " f"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM FZ JOIN LATERAL " + FLATTEN + " f"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM FZ CROSS JOIN LATERAL " + FLATTEN + " f"));
        assertEquals("4", scalar("SELECT COUNT(*) FROM FZ, TABLE(GENERATOR(ROWCOUNT => 2))"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM LATERAL GENERATOR(ROWCOUNT => 2)"));
        // A user-defined table function after LATERAL, reading the row beside it.
        assertEquals("2", scalar("SELECT COUNT(*) FROM FZ, LATERAL tf1(FZ.id)"));
        assertEquals("1", scalar("SELECT v FROM TABLE(tf1(1))"));
        assertEquals("SQL compilation error:\nUnknown table function UPPER",
            refusalOf("SELECT COUNT(*) FROM FZ, LATERAL UPPER('a')"));
    }

    @Test
    public void whatElseFollowsATableInParenthesesStillDoes() {
        assertEquals("1", scalar("SELECT c1 FROM t1 x (c1) ORDER BY 1"));
        assertEquals("1", scalar("SELECT a FROM (VALUES (1)) v(a)"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM t1 SAMPLE (100)"));
        assertEquals("2", scalar("SELECT COUNT(*) FROM t1 TABLESAMPLE (100)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 20 unexpected ''y''.",
            refusalOf("SELECT * FROM t1 x ('y')"));
    }
}
