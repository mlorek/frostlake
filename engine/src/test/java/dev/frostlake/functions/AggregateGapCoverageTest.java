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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Aggregate paths coverage showed untested: the linear-regression family over known points
 * (with NULL-pair skipping), MAX_BY/MIN_BY including the top-N array form, single-row and
 * all-NULL edges of the statistical aggregates, and the GENERATOR / SPLIT_TO_TABLE table
 * functions with their full output column sets.
 */
public class AggregateGapCoverageTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // y = 2x + 1 exactly, so every regression statistic is a clean constant;
        // the NULL-bearing pairs must be skipped by every REGR_ function.
        engine.execute("CREATE TABLE pts (x INTEGER, y INTEGER)");
        engine.execute("INSERT INTO pts VALUES (1, 3), (2, 5), (3, 7), (NULL, 9), (4, NULL)");
    }

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private double num(final String sql) {
        return ((Number) one(sql)).doubleValue();
    }

    @Test
    public void regressionFamilyOverExactLine() {
        assertEquals(2.0, num("SELECT REGR_SLOPE(y, x) FROM pts"), 1e-9);
        assertEquals(1.0, num("SELECT REGR_INTERCEPT(y, x) FROM pts"), 1e-9);
        assertEquals(1.0, num("SELECT REGR_R2(y, x) FROM pts"), 1e-9);
        assertEquals(3.0, num("SELECT REGR_COUNT(y, x) FROM pts"), 1e-9); // NULL pairs skipped
        assertEquals(2.0, num("SELECT REGR_AVGX(y, x) FROM pts"), 1e-9);
        assertEquals(5.0, num("SELECT REGR_AVGY(y, x) FROM pts"), 1e-9);
    }

    @Test
    public void regressionSumsOfSquares() {
        assertEquals(2.0, num("SELECT REGR_SXX(y, x) FROM pts"), 1e-9);  // sum (x-avgx)^2
        assertEquals(8.0, num("SELECT REGR_SYY(y, x) FROM pts"), 1e-9);
        assertEquals(4.0, num("SELECT REGR_SXY(y, x) FROM pts"), 1e-9);
        // a single usable pair has no spread: slope undefined -> NULL
        assertNull(one("SELECT REGR_SLOPE(y, x) FROM pts WHERE x = 1"));
    }

    @Test
    public void maxByMinByScalarAndTopN() {
        engine.execute("CREATE TABLE emp (name VARCHAR, pay INTEGER)");
        engine.execute("INSERT INTO emp VALUES ('a', 10), ('b', 30), ('c', 20)");
        assertEquals("b", String.valueOf(one("SELECT MAX_BY(name, pay) FROM emp")));
        assertEquals("a", String.valueOf(one("SELECT MIN_BY(name, pay) FROM emp")));
        // the N-arg form returns an ARRAY of the top values by the sort column
        assertEquals("[\"b\",\"c\"]",
            String.valueOf(one("SELECT TO_JSON(MAX_BY(name, pay, 2)) FROM emp")));
        assertEquals("[\"a\",\"c\"]",
            String.valueOf(one("SELECT TO_JSON(MIN_BY(name, pay, 2)) FROM emp")));
        // NULL sort keys never win
        engine.execute("INSERT INTO emp VALUES ('z', NULL)");
        assertEquals("b", String.valueOf(one("SELECT MAX_BY(name, pay) FROM emp")));
    }

    @Test
    public void statisticalEdgesSingleRowAndAllNull() {
        engine.execute("CREATE TABLE s1 (v INTEGER)");
        engine.execute("INSERT INTO s1 VALUES (42)");
        assertNull(one("SELECT STDDEV_SAMP(v) FROM s1"));   // sample stats need n >= 2
        assertNull(one("SELECT VAR_SAMP(v) FROM s1"));
        assertEquals(0.0, num("SELECT STDDEV_POP(v) FROM s1"), 1e-9);
        assertEquals(0.0, num("SELECT VAR_POP(v) FROM s1"), 1e-9);
        engine.execute("CREATE TABLE s0 (v INTEGER)");
        engine.execute("INSERT INTO s0 VALUES (NULL), (NULL)");
        assertNull(one("SELECT AVG(v) FROM s0"));
        assertNull(one("SELECT STDDEV_POP(v) FROM s0"));
        assertEquals(0L, ((Number) one("SELECT COUNT(v) FROM s0")).longValue());
    }

    @Test
    public void generatorRowcountProducesExactRows() {
        final ResultSet rs = engine.executeQuery(
            "SELECT SEQ4() AS s FROM TABLE(GENERATOR(ROWCOUNT => 4)) ORDER BY s");
        assertEquals(4, rs.getRowCount());
        assertEquals(0L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(3).getValue(0)).longValue());
    }

    @Test
    public void splitToTableEmitsAllOutputColumns() {
        final ResultSet rs = engine.executeQuery(
            "SELECT t.seq, t.index, t.value FROM TABLE(SPLIT_TO_TABLE('a,b,c', ',')) t"
            + " ORDER BY t.index");
        assertEquals(3, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals("a", rs.getRows().get(0).getValue(2));
        assertEquals("c", rs.getRows().get(2).getValue(2));
    }

    @Test
    public void splitToTableLateralPerRow() {
        engine.execute("CREATE TABLE csvs (id INTEGER, s VARCHAR)");
        engine.execute("INSERT INTO csvs VALUES (1, 'x,y'), (2, 'z')");
        final ResultSet rs = engine.executeQuery(
            "SELECT c.id, t.value FROM csvs c, LATERAL SPLIT_TO_TABLE(c.s, ',') t"
            + " ORDER BY c.id, t.index");
        assertEquals(3, rs.getRowCount());
        assertEquals("x", rs.getRows().get(0).getValue(1));
        assertEquals("z", rs.getRows().get(2).getValue(1));
    }

    @Test
    public void aggregatesRejectImpossibleInputsButAcceptDistinct() {
        engine.execute("CREATE TABLE d (v INTEGER)");
        engine.execute("INSERT INTO d VALUES (1), (1), (2)");
        assertEquals(3L, ((Number) one("SELECT SUM(DISTINCT v) FROM d")).longValue());
        assertEquals(2L, ((Number) one("SELECT COUNT(DISTINCT v) FROM d")).longValue());
        assertEquals(1.5, num("SELECT AVG(DISTINCT v) FROM d"), 1e-9);
        assertTrue(num("SELECT MEDIAN(v) FROM d") == 1.0);
    }
}
