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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SYSTEM$TYPEOF is a compile-time function: it names its argument's DECLARED type and computes
 * nothing, so an argument whose VALUE would be refused — a median that does not fit its type, an
 * overflowing negation — is typed all the same. A select list whose aggregates all sit inside typeof
 * calls is still validated as an aggregate query (a bare column beside it is refused) but RUNS as a
 * scan, one row per input row, unless the account could have answered the wrapped aggregate without
 * scanning — from a partition's statistics or a constant — in which case one row comes back. Any
 * aggregate written outside a typeof, a GROUP BY or a HAVING keeps the aggregate shape.
 */
public class SystemTypeofFoldTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE aw (c20_10 NUMBER(20,10), c38_35 NUMBER(38,35), k VARCHAR, d DATE, "
            + "ts TIMESTAMP_NTZ, b BOOLEAN, f FLOAT, i NUMBER(38,0))");
        engine.execute("INSERT INTO aw SELECT 2.5, 2.5, 'a', '2020-01-01', '2020-01-01 10:00:00', TRUE, 1.5, 7 "
            + "UNION ALL SELECT 3.5, 3.5, 'b', '2020-01-02', '2020-01-02 10:00:00', FALSE, 2.5, 9");
        engine.execute("CREATE TABLE ar (a NUMBER(38,0))");
        engine.execute("INSERT INTO ar SELECT 99999999999999999999999999999999999999");
        engine.execute("CREATE TABLE s1 (a NUMBER(38,0))");
        engine.execute("INSERT INTO s1 SELECT 50000000000000000000000000000000000000 "
            + "UNION ALL SELECT 50000000000000000000000000000000000000");
        engine.execute("CREATE TABLE e0 (a NUMBER(10,2), i INT)");
    }

    private ResultSet query(final String sql) {
        return engine.executeQuery(sql);
    }

    private String first(final String sql) {
        return String.valueOf(query(sql).getRows().get(0).getValue(0));
    }

    private void assertShape(final String sql, final int rowCount, final String typeText) {
        final ResultSet result = query(sql);
        assertEquals(rowCount, result.getRowCount(), sql);
        for (int i = 0; i < result.getRowCount(); i++) {
            assertEquals(typeText, String.valueOf(result.getRows().get(i).getValue(0)), sql);
        }
    }

    private void assertRefused(final String sql, final String sentence) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertTrue(refused.getMessage().contains(sentence), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aFoldedAggregateAnswersOneRowPerInputRow() {
        assertShape("SELECT SYSTEM$TYPEOF(AVG(c20_10)) FROM aw", 2, "NUMBER(34,12)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(MEDIAN(c38_35)) FROM aw", 2, "NUMBER(38,38)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(AVG(c38_35)) FROM aw", 2, "NUMBER(38,35)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw", 2, "NUMBER(32,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(i)) FROM aw", 2, "NUMBER(38,0)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(1)) FROM aw", 2, "NUMBER(13,0)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10) + 1) FROM aw", 2, "NUMBER(33,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(MIN(k)) FROM aw", 2, "VARCHAR(16777216)[LOB]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(LENGTH(k))) FROM aw", 2, "NUMBER(18,0)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(MIN(f)) FROM aw", 2, "FLOAT[DOUBLE]");
        assertShape("SELECT SYSTEM$TYPEOF(ANY_VALUE(k)) FROM aw", 2, "VARCHAR(16777216)[LOB]");
        assertShape("SELECT SYSTEM$TYPEOF(LISTAGG(k)) FROM aw", 2, "VARCHAR(134217728)[LOB]");
        assertShape("SELECT SYSTEM$TYPEOF(ARRAY_AGG(k)) FROM aw", 2, "ARRAY[LOB]");
        assertShape("SELECT SYSTEM$TYPEOF(STDDEV(c20_10)) FROM aw", 2, "FLOAT[DOUBLE]");
        assertShape("SELECT SYSTEM$TYPEOF(VARIANCE(c20_10)) FROM aw", 2, "NUMBER(38,12)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(BOOLOR_AGG(b)) FROM aw", 2, "BOOLEAN[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(COUNT(DISTINCT k)) FROM aw", 2, "NUMBER(18,0)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(COUNT_IF(b)) FROM aw", 2, "NUMBER(13,0)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(k)) FROM aw", 2, "FLOAT[DOUBLE]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)), SYSTEM$TYPEOF(MAX(c20_10)) FROM aw", 2, "NUMBER(32,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw ORDER BY 1", 2, "NUMBER(32,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw WHERE c20_10 > 3", 1, "NUMBER(32,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw LIMIT 1", 1, "NUMBER(32,10)[SB16]");
        assertShape("SELECT DISTINCT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw", 1, "NUMBER(32,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(x.c20_10)) FROM aw x JOIN aw y ON TRUE", 4, "NUMBER(32,10)[SB16]");
        assertShape("SELECT * FROM (SELECT SYSTEM$TYPEOF(SUM(c20_10)) AS t FROM aw)", 2, "NUMBER(32,10)[SB16]");
        assertEquals("2", first("WITH c AS (SELECT SYSTEM$TYPEOF(SUM(c20_10)) AS t FROM aw) SELECT COUNT(*) FROM c"));
        assertShape("SELECT LENGTH(SYSTEM$TYPEOF(SUM(c20_10))) FROM aw", 2, "19");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)) || 'x' FROM aw", 2, "NUMBER(32,10)[SB16]x");
        // A scan that keeps nothing answers nothing; a relation with no rows at all answers one row.
        assertEquals(0, query("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw WHERE FALSE").getRowCount());
        assertEquals(0, query("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw WHERE c20_10 > 100").getRowCount());
        assertEquals(0, query("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM (SELECT * FROM aw WHERE c20_10 > 100)").getRowCount());
        assertEquals(0, query("SELECT SYSTEM$TYPEOF(SUM(x.c20_10)) FROM aw x, e0 y").getRowCount());
        assertShape("SELECT SYSTEM$TYPEOF(SUM(a)) FROM e0", 1, "NUMBER(22,2)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(AVG(a)) FROM e0", 1, "NUMBER(28,8)[SB1]");
    }

    @Test
    public void aStatisticsAnswerableAggregateKeepsTheAggregateShape() {
        assertShape("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw", 1, "NUMBER(18,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(COUNT(c20_10)) FROM aw", 1, "NUMBER(18,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(COUNT(k)) FROM aw", 1, "NUMBER(18,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(COUNT(1)) FROM aw", 1, "NUMBER(18,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw LIMIT 1", 1, "NUMBER(18,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM e0", 1, "NUMBER(18,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(1)) FROM aw", 1, "NUMBER(1,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(MIN(1)) FROM aw", 1, "NUMBER(1,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(c20_10)) FROM aw", 1, "NUMBER(20,10)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(MIN(c20_10)) FROM aw", 1, "NUMBER(20,10)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(i)) FROM aw", 1, "NUMBER(38,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(d)) FROM aw", 1, "DATE[SB4]");
        assertShape("SELECT SYSTEM$TYPEOF(MIN(ts)) FROM aw", 1, "TIMESTAMP_NTZ(9)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(b)) FROM aw", 1, "BOOLEAN[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(c20_10)) FROM aw WHERE c20_10 > 3", 1, "NUMBER(20,10)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(c20_10) + 1) FROM aw", 1, "NUMBER(21,10)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(c20_10 + 1)) FROM aw", 1, "NUMBER(21,10)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(MAX(a)) FROM e0", 1, "NUMBER(10,2)[SB1]");
        assertEquals(1, query("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw WHERE c20_10 > 3").getRowCount());
    }

    @Test
    public void anAggregateOutsideTheTypeofKeepsTheAggregateShape() {
        final ResultSet beside = query("SELECT SYSTEM$TYPEOF(SUM(c20_10)), SUM(c20_10) FROM aw");
        assertEquals(1, beside.getRowCount());
        assertEquals("NUMBER(32,10)[SB16]", String.valueOf(beside.getRows().get(0).getValue(0)));
        assertEquals("6.0000000000", String.valueOf(beside.getRows().get(0).getValue(1)));
        final ResultSet text = query("SELECT SYSTEM$TYPEOF(SUM(c20_10)), MAX(k) FROM aw");
        assertEquals(1, text.getRowCount());
        assertEquals("b", String.valueOf(text.getRows().get(0).getValue(1)));
        assertEquals(1, query("SELECT SYSTEM$TYPEOF(COUNT(*)), COUNT(*) FROM aw").getRowCount());
        assertEquals(1, query("SELECT SYSTEM$TYPEOF(COUNT(*)), SUM(c20_10) FROM aw").getRowCount());
        assertEquals(1, query("SELECT SYSTEM$TYPEOF(MAX(k)), SUM(c20_10) FROM aw").getRowCount());
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw HAVING COUNT(*) > 0", 1, "NUMBER(32,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw HAVING SUM(c20_10) > 0", 1, "NUMBER(32,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw GROUP BY k", 2, "NUMBER(32,10)[SB16]");
        assertEquals(2, query("SELECT SYSTEM$TYPEOF(COUNT(*)) FROM aw GROUP BY k").getRowCount());
        assertShape("SELECT SYSTEM$TYPEOF(MAX(c20_10)) FROM aw GROUP BY k", 2, "NUMBER(20,10)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(SUM(SUM(c20_10)) OVER ()) FROM aw GROUP BY k", 2, "NUMBER(38,10)[SB16]");
        final ResultSet grouped = query("SELECT COUNT(*), SYSTEM$TYPEOF(SUM(c20_10)) FROM aw GROUP BY k");
        assertEquals(2, grouped.getRowCount());
        assertEquals("1", String.valueOf(grouped.getRows().get(0).getValue(0)));
        assertEquals("NUMBER(32,10)[SB16]", String.valueOf(grouped.getRows().get(0).getValue(1)));
    }

    @Test
    public void theArgumentIsTypedAndNeverEvaluated() {
        assertShape("SELECT SYSTEM$TYPEOF(-(-a - 70141183460469231731687303715884105729)) FROM ar", 1, "NUMBER(38,0)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(AVG(a)) FROM s1", 2, "NUMBER(38,6)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(AVG(c38_35) OVER ()) FROM aw LIMIT 1", 1, "NUMBER(38,38)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(1/0)", 1, "NUMBER(7,6)[SB4]");
        assertShape("SELECT SYSTEM$TYPEOF('a'::NUMBER)", 1, "NUMBER(38,0)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(COUNT(*) OVER ()) FROM aw", 2, "NUMBER(18,0)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(ROW_NUMBER() OVER (ORDER BY k)) FROM aw", 2, "NUMBER(18,0)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF(LAG(c20_10) OVER (ORDER BY k)) FROM aw", 2, "NUMBER(20,10)[SB8]");
        assertShape("SELECT SYSTEM$TYPEOF((SELECT SUM(c20_10) FROM aw))", 1, "NUMBER(32,10)[SB16]");
        assertShape("SELECT SYSTEM$TYPEOF(1) FROM aw", 2, "NUMBER(1,0)[SB1]");
        assertShape("SELECT SYSTEM$TYPEOF(c20_10) FROM aw GROUP BY c20_10", 2, "NUMBER(20,10)[SB8]");
    }

    @Test
    public void theSelectListIsStillValidatedAsAnAggregateQuery() {
        assertRefused("SELECT SYSTEM$TYPEOF(SUM(c20_10)), k FROM aw", "[AW.K] is not a valid group by expression");
        assertRefused("SELECT SYSTEM$TYPEOF(SUM(c20_10)), c20_10 * 2 FROM aw",
            "[AW.C20_10] is not a valid group by expression");
        assertRefused("SELECT SYSTEM$TYPEOF(MAX(c20_10)), k FROM aw", "[AW.K] is not a valid group by expression");
        assertRefused("SELECT k FROM aw WHERE SYSTEM$TYPEOF(SUM(c20_10)) = 'x'",
            "Invalid aggregate function in where clause [SUM(AW.C20_10)]");
        assertRefused("SELECT SYSTEM$TYPEOF(SUM(c20_10)) AS t FROM aw QUALIFY ROW_NUMBER() OVER (ORDER BY k) = 1",
            "[AW.K] is not a valid group by expression");
        assertRefused("SELECT k FROM aw ORDER BY SYSTEM$TYPEOF(SUM(c20_10))", "[AW.K] is not a valid group by expression");
        assertRefused("SELECT SYSTEM$TYPEOF(SUM(c20_10)) FROM aw GROUP BY 1",
            "[SYSTEM$TYPEOF(SUM(C20_10))] is not a valid group by expression");
    }
}
