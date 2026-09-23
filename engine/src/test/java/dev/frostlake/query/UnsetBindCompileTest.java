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

/**
 * An unset bind ({@code :1}, {@code :name}) outside a scripting block is refused while the statement compiles, as
 * {@code Bind variable :1 not set.} at its colon, wherever it stands and whether or not a row reaches it: a
 * WHERE or a join condition over an empty table, a branch never taken, a subquery, a DML statement. It resolves
 * with the column names, in written order among them and in their clause order; a LIMIT, OFFSET or FETCH count
 * written as a bind is judged after every clause's names and the ORDER BY position, ahead of everything else.
 */
public class UnsetBindCompileTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE T (a INT, b INT)");
        engine.execute("CREATE TABLE FULL_T (a INT, b INT)");
        engine.execute("INSERT INTO FULL_T VALUES (1, 2)");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private static String notSet(final int position, final String bind) {
        return "SQL compilation error: error line 1 at position " + position + "\nBind variable " + bind
            + " not set.";
    }

    private static String invalid(final int position, final String name) {
        return "SQL compilation error: error line 1 at position " + position + "\ninvalid identifier '" + name + "'";
    }

    @Test
    public void aWhereOrAJoinConditionOverAnEmptyTableIsRefusedWhileCompiling() {
        assertEquals(notSet(26, ":1"), refusal("SELECT a FROM T WHERE a = :1"));
        assertEquals(notSet(41, ":1"), refusal("SELECT f.a FROM FULL_T f JOIN T ON f.a = :1"));
        assertEquals(notSet(48, ":1"), refusal("SELECT t.a FROM T t LEFT JOIN FULL_T f ON f.a = :1"));
        assertEquals(notSet(31, ":1"), refusal("SELECT a FROM FULL_T WHERE a = :1"));
        assertEquals(notSet(26, ":abc"), refusal("SELECT a FROM T WHERE a = :abc"));
        assertEquals(notSet(26, ":2"), refusal("SELECT a FROM T WHERE a = :2 OR a = :1"));
    }

    @Test
    public void aBindNoRowReachesIsRefusedToo() {
        assertEquals(notSet(43, ":1"), refusal("SELECT a FROM T WHERE CASE WHEN FALSE THEN :1 ELSE 1 END = 1"));
        assertEquals(notSet(32, ":1"), refusal("SELECT 1 FROM T WHERE FALSE AND :1 = 1"));
        assertEquals(notSet(7, ":1"), refusal("SELECT :1 WHERE FALSE"));
        assertEquals(notSet(26, ":1"), refusal("SELECT a FROM T WHERE a = :1 LIMIT 0"));
    }

    @Test
    public void everyClauseAndEveryNestedQuery() {
        assertEquals(notSet(7, ":1"), refusal("SELECT :1 FROM T"));
        assertEquals(notSet(29, ":1"), refusal("SELECT a FROM T GROUP BY a + :1"));
        assertEquals(notSet(38, ":1"), refusal("SELECT a FROM T GROUP BY a HAVING a = :1"));
        assertEquals(notSet(28, ":1"), refusal("SELECT a FROM T QUALIFY a = :1"));
        assertEquals(notSet(29, ":1"), refusal("SELECT a FROM T ORDER BY a + :1"));
        assertEquals(notSet(59, ":1"), refusal("SELECT a FROM T WHERE a IN (SELECT b FROM FULL_T WHERE b = :1)"));
        assertEquals(notSet(41, ":1"), refusal("SELECT a FROM (SELECT a FROM T WHERE a = :1)"));
        assertEquals(notSet(37, ":1"), refusal("WITH c AS (SELECT a FROM T WHERE a = :1) SELECT * FROM c"));
        assertEquals(notSet(37, ":1"), refusal("WITH c AS (SELECT a FROM T WHERE a = :1) SELECT 1"));
        assertEquals(notSet(26, ":1"), refusal("SELECT a FROM T WHERE a = :1 UNION ALL SELECT 1"));
        assertEquals(notSet(26, ":1"), refusal("EXECUTE IMMEDIATE 'SELECT a FROM T WHERE a = :1'"));
    }

    @Test
    public void aBindRanksWithTheNamesInWrittenAndClauseOrder() {
        assertEquals(invalid(7, "NOSUCH"), refusal("SELECT nosuch FROM T WHERE a = :1"));
        assertEquals(invalid(22, "NOSUCH"), refusal("SELECT a FROM T WHERE nosuch = :1"));
        assertEquals(notSet(26, ":1"), refusal("SELECT a FROM T WHERE a = :1 AND nosuch = 1"));
        assertEquals(invalid(35, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch = :1"));
        assertEquals(notSet(35, ":1"), refusal("SELECT f.a FROM FULL_T f JOIN T ON :1 = T.nosuch"));
        assertEquals(notSet(41, ":1"), refusal("SELECT f.a FROM FULL_T f JOIN T ON f.a = :1 WHERE nosuch = 1"));
        assertEquals(notSet(26, ":1"), refusal("SELECT a FROM T WHERE a = :1 GROUP BY nosuch"));
        assertEquals(invalid(7, "NOSUCH"), refusal("SELECT nosuch FROM T GROUP BY a HAVING a = :1"));
    }

    @Test
    public void aBindOutranksAnUnknownFunctionAndTypesButNotAHavingOrdinal() {
        assertEquals(notSet(36, ":1"), refusal("SELECT a FROM T WHERE NOSUCHFN(a) = :1"));
        assertEquals(notSet(51, ":1"), refusal("SELECT f.a FROM FULL_T f JOIN T ON NOSUCHFN(f.a) = :1"));
        assertEquals(notSet(26, ":1"), refusal("SELECT a FROM T WHERE a = :1 AND a"));
        assertEquals(notSet(30, ":1"), refusal("SELECT a FROM T WHERE a = SUM(:1)"));
        assertEquals(notSet(28, ":1"), refusal("SELECT a FROM T ORDER BY 5, :1"));
        assertEquals("SQL compilation error:\n[5] is not a valid order by expression",
            refusal("SELECT a FROM T GROUP BY a HAVING :1 = 1 ORDER BY 5"));
    }

    @Test
    public void dmlStatementsEchoTheNameAsWritten() {
        assertEquals(notSet(29, ":Abc"), refusal("UPDATE T SET a = 1 WHERE a = :Abc"));
        assertEquals(notSet(24, ":Abc"), refusal("DELETE FROM T WHERE a = :Abc"));
        assertEquals(notSet(22, ":Abc"), refusal("INSERT INTO T VALUES (:Abc, 1)"));
        assertEquals(notSet(21, ":Abc"), refusal("INSERT INTO T SELECT :Abc, 1 FROM T"));
        assertEquals(notSet(17, ":1"), refusal("UPDATE T SET a = :1"));
        assertEquals(notSet(45, ":1"), refusal("CREATE TABLE CT AS SELECT a FROM T WHERE a = :1"));
        assertEquals(notSet(37, ":1"),
            refusal("MERGE INTO T USING FULL_T s ON T.a = :1 WHEN MATCHED THEN UPDATE SET b = 1"));
    }

    @Test
    public void aLimitOffsetOrFetchBindIsRefusedRatherThanReadAsNoLimit() {
        assertEquals(notSet(27, ":abc"), refusal("SELECT a FROM FULL_T LIMIT :abc"));
        assertEquals(notSet(36, ":abc"), refusal("SELECT a FROM FULL_T LIMIT 1 OFFSET :abc"));
        assertEquals(notSet(27, ":abc"), refusal("SELECT a FROM FULL_T LIMIT :abc OFFSET :def"));
        assertEquals(notSet(33, ":abc"), refusal("SELECT a FROM FULL_T FETCH FIRST :abc ROWS ONLY"));
        assertEquals(notSet(23, ":abc"), refusal("SELECT a FROM T OFFSET :abc ROWS FETCH NEXT 1 ROWS ONLY"));
        assertEquals(notSet(42, ":abc"), refusal("SELECT * FROM (SELECT a FROM FULL_T LIMIT :abc)"));
        assertEquals(notSet(60, ":abc"), refusal("SELECT a FROM FULL_T WHERE a IN (SELECT a FROM FULL_T LIMIT :abc)"));
        assertEquals(notSet(58, ":abc"), refusal("SELECT a FROM FULL_T UNION ALL SELECT a FROM FULL_T LIMIT :abc"));
        assertEquals(notSet(15, ":abc"), refusal("SELECT 1 LIMIT :abc"));
        assertEquals(notSet(27, ":abc"), refusal("SELECT 1 WHERE FALSE LIMIT :abc"));
        assertEquals(notSet(34, ":abc"), refusal("SELECT 1 UNION ALL SELECT 2 LIMIT :abc"));
    }

    @Test
    public void aLimitBindWaitsForEveryClauseNameAndThePositionButOutranksTheRest() {
        assertEquals(invalid(7, "NOSUCH"), refusal("SELECT nosuch FROM T LIMIT :abc"));
        assertEquals(invalid(22, "NOSUCH"), refusal("SELECT a FROM T WHERE nosuch = 1 LIMIT :abc"));
        assertEquals(invalid(34, "NOSUCH"), refusal("SELECT a FROM T GROUP BY a HAVING nosuch = 1 LIMIT :abc"));
        assertEquals(invalid(24, "NOSUCH"), refusal("SELECT a FROM T QUALIFY nosuch = 1 LIMIT :abc"));
        assertEquals(invalid(35, "T.NOSUCH"), refusal("SELECT f.a FROM FULL_T f JOIN T ON T.nosuch = 1 LIMIT :abc"));
        assertEquals(invalid(33, "NOSUCH"), refusal("SELECT a FROM T UNION ALL SELECT nosuch FROM T LIMIT :abc"));
        assertEquals(invalid(15, "NOSUCH"), refusal("SELECT 1 WHERE nosuch = 1 LIMIT :abc"));
        assertEquals("SQL compilation error:\n[5] is not a valid order by expression",
            refusal("SELECT a FROM T ORDER BY 5 LIMIT :abc"));
        assertEquals(notSet(32, ":abc"), refusal("SELECT NOSUCHFN(a) FROM T LIMIT :abc"));
        assertEquals(notSet(56, ":abc"), refusal("SELECT a FROM T WHERE a IN (SELECT nosuch FROM T) LIMIT :abc"));
        assertEquals(notSet(32, ":abc"), refusal("SELECT UPPER(1, 2) FROM T LIMIT :abc"));
        assertEquals(notSet(30, ":abc"), refusal("SELECT a FROM T WHERE a LIMIT :abc"));
        assertEquals(notSet(30, ":abc"), refusal("SELECT a, SUM(b) FROM T LIMIT :abc"));
        assertEquals(notSet(31, ":zz"), refusal("SELECT a FROM FULL_T WHERE a = :zz LIMIT :abc"));
    }

    @Test
    public void aRowCountBindInADefinitionIsTheDefinitionsRefusal() {
        final String refused = "Bind variables not allowed in view and UDF definitions.";
        assertEquals("SQL compilation error: error line 1 at position 45\n" + refused,
            refusal("CREATE VIEW vl AS SELECT a FROM FULL_T LIMIT :abc"));
        assertEquals("SQL compilation error: error line 1 at position 55\n" + refused,
            refusal("CREATE VIEW vl2 AS SELECT a FROM FULL_T LIMIT 1 OFFSET :abc"));
        assertEquals("SQL compilation error: error line 1 at position 52\n" + refused,
            refusal("CREATE VIEW vl3 AS SELECT a FROM FULL_T FETCH FIRST :abc ROWS ONLY"));
        assertEquals("SQL compilation error: error line 1 at position 61\n" + refused,
            refusal("CREATE VIEW vl4 AS SELECT * FROM (SELECT a FROM FULL_T LIMIT :abc)"));
        assertEquals("SQL compilation error: error line 1 at position 50\n" + refused,
            refusal("CREATE VIEW vl5 AS SELECT a FROM FULL_T WHERE a = :x LIMIT :abc"));
        assertEquals("SQL compilation error: error line 1 at position 28\n" + refused,
            refusal("CREATE FUNCTION fl1() RETURNS TABLE (a INT) AS 'SELECT a FROM FULL_T LIMIT :abc'"));
    }

    @Test
    public void aBlockVariableStillBindsTheSameShapes() {
        final ResultSet counted = engine.executeQuery("""
            DECLARE
              n INT DEFAULT 1;
              r INT;
            BEGIN
              SELECT COUNT(*) INTO :r FROM (SELECT a FROM FULL_T LIMIT :n);
              RETURN r;
            END""");
        assertEquals("1", String.valueOf(counted.getRows().get(0).getValue(0)));
        final ResultSet filtered = engine.executeQuery("""
            DECLARE
              n INT DEFAULT 5;
              r INT;
            BEGIN
              SELECT COUNT(*) INTO :r FROM FULL_T WHERE a < :n;
              RETURN r;
            END""");
        assertEquals("1", String.valueOf(filtered.getRows().get(0).getValue(0)));
    }
}
