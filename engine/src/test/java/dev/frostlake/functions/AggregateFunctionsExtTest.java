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
import dev.frostlake.functions.aggregate.PercentileCont;
import dev.frostlake.functions.aggregate.PercentileDisc;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AggregateFunctionsExtTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE nums (id INTEGER, v DOUBLE, flag BOOLEAN)");
        engine.execute("INSERT INTO nums VALUES (1, 10.0, true)");
        engine.execute("INSERT INTO nums VALUES (2, 20.0, false)");
        engine.execute("INSERT INTO nums VALUES (3, 30.0, true)");
        engine.execute("INSERT INTO nums VALUES (4, 40.0, true)");
        engine.execute("INSERT INTO nums VALUES (5, NULL, NULL)");
    }

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    // ── Statistical aliases ───────────────────────────────────────────────────

    @Test public void testStdDevSamp() {
        // Sample stddev of [10,20,30,40] ≈ 12.91
        double v = ((Number) q("SELECT STDDEV_SAMP(v) FROM nums")).doubleValue();
        assertEquals(((Number) q("SELECT STDDEV(v) FROM nums")).doubleValue(), v, 0.001);
    }

    @Test public void testStdDevPop() {
        // Population stddev of [10,20,30,40] = 11.18
        double v = ((Number) q("SELECT STDDEV_POP(v) FROM nums")).doubleValue();
        assertEquals(11.18, v, 0.01);
    }

    @Test public void testVarSamp() {
        double v = ((Number) q("SELECT VAR_SAMP(v) FROM nums")).doubleValue();
        assertEquals(((Number) q("SELECT VARIANCE(v) FROM nums")).doubleValue(), v, 0.001);
    }

    @Test public void testVarPop() {
        double v = ((Number) q("SELECT VAR_POP(v) FROM nums")).doubleValue();
        assertEquals(125.0, v, 0.001); // (10-25)^2 + (20-25)^2 + (30-25)^2 + (40-25)^2 / 4 = 125
    }

    // ── Boolean aggregates ────────────────────────────────────────────────────

    @Test public void testBoolorAgg() {
        assertEquals(true, q("SELECT BOOLOR_AGG(flag) FROM nums"));
    }

    @Test public void testBoolandAgg() {
        assertEquals(false, q("SELECT BOOLAND_AGG(flag) FROM nums"));
    }

    @Test public void testBoolandAggAllTrue() {
        engine.execute("CREATE TABLE allTrue (f BOOLEAN)");
        engine.execute("INSERT INTO allTrue VALUES (true),(true),(true)");
        assertEquals(true, engine.executeQuery("SELECT BOOLAND_AGG(f) FROM allTrue").getRows().get(0).getValue(0));
    }

    @Test public void testBoolxorAgg() {
        // 3 trues → odd → XOR = true
        assertNotNull(q("SELECT BOOLXOR_AGG(flag) FROM nums"));
    }

    // ── Bitwise aggregates ────────────────────────────────────────────────────

    @Test public void testBitandAgg() {
        // 1 & 2 & 3 & 4 & 5 = 0
        Long v = (Long) q("SELECT BITAND_AGG(id) FROM nums");
        assertEquals(0L, v);
    }

    @Test public void testBitorAgg() {
        // 1 | 2 | 3 | 4 | 5 = 7
        Long v = (Long) q("SELECT BITOR_AGG(id) FROM nums");
        assertEquals(7L, v);
    }

    @Test public void testBitxorAgg() {
        assertNotNull(q("SELECT BITXOR_AGG(id) FROM nums"));
    }

    // ── COUNT_IF ──────────────────────────────────────────────────────────────

    @Test public void testCountIf() {
        Long v = (Long) q("SELECT COUNT_IF(flag) FROM nums");
        assertEquals(3L, v); // three true values
    }

    @Test public void testCountIfFalse() {
        // COUNT_IF with a pre-computed boolean column
        engine.execute("CREATE TABLE flags (active BOOLEAN)");
        engine.execute("INSERT INTO flags VALUES (true),(false),(true),(true),(false)");
        Long v = (Long) engine.executeQuery("SELECT COUNT_IF(active) FROM flags").getRows().get(0).getValue(0);
        assertEquals(3L, v);
    }

    // ── MEDIAN ────────────────────────────────────────────────────────────────

    @Test public void testMedianOdd() {
        engine.execute("CREATE TABLE med (v DOUBLE)");
        engine.execute("INSERT INTO med VALUES (1),(3),(5)");
        assertEquals(3.0, ((Number) engine.executeQuery("SELECT MEDIAN(v) FROM med").getRows().get(0).getValue(0)).doubleValue(), 0.001);
    }

    @Test public void testMedianEven() {
        Double v = (Double) q("SELECT MEDIAN(v) FROM nums"); // [10,20,30,40] → 25
        assertEquals(25.0, v, 0.001);
    }

    // ── MODE ──────────────────────────────────────────────────────────────────

    @Test public void testMode() {
        engine.execute("CREATE TABLE mode_t (v INTEGER)");
        engine.execute("INSERT INTO mode_t VALUES (1),(2),(2),(3),(2)");
        assertEquals(2L, ((Number) engine.executeQuery("SELECT MODE(v) FROM mode_t").getRows().get(0).getValue(0)).longValue());
    }

    // ── ANY_VALUE ─────────────────────────────────────────────────────────────

    @Test public void testAnyValue() {
        Object v = q("SELECT ANY_VALUE(id) FROM nums");
        assertNotNull(v);
    }

    // ── APPROX_COUNT_DISTINCT ─────────────────────────────────────────────────

    @Test public void testApproxCountDistinct() {
        Long v = (Long) q("SELECT APPROX_COUNT_DISTINCT(id) FROM nums");
        assertEquals(5L, v); // exact for small cardinalities
    }

    // ── SKEW ──────────────────────────────────────────────────────────────────

    @Test public void testSkewSymmetric() {
        // Symmetric distribution → skew ≈ 0
        engine.execute("CREATE TABLE sym (v DOUBLE)");
        engine.execute("INSERT INTO sym VALUES (1),(2),(3),(4),(5)");
        double skew = ((Number) engine.executeQuery("SELECT SKEW(v) FROM sym").getRows().get(0).getValue(0)).doubleValue();
        assertEquals(0.0, skew, 0.01);
    }

    // ── KURTOSIS ─────────────────────────────────────────────────────────────

    @Test public void testKurtosisReturnsValue() {
        // Needs at least 4 values
        Object v = q("SELECT KURTOSIS(v) FROM nums");
        assertNotNull(v);
    }

    // ── CORR ──────────────────────────────────────────────────────────────────

    @Test public void testCorrPerfectPositive() {
        engine.execute("CREATE TABLE corr_t (x DOUBLE, y DOUBLE)");
        engine.execute("INSERT INTO corr_t VALUES (1,2),(2,4),(3,6),(4,8)");
        double corr = ((Number) engine.executeQuery("SELECT CORR(x, y) FROM corr_t").getRows().get(0).getValue(0)).doubleValue();
        assertEquals(1.0, corr, 0.001);
    }

    // ── COVAR_POP / COVAR_SAMP ────────────────────────────────────────────────

    @Test public void testCovarPop() {
        engine.execute("CREATE TABLE cov_t (x DOUBLE, y DOUBLE)");
        engine.execute("INSERT INTO cov_t VALUES (1,2),(2,4),(3,6)");
        double cov = ((Number) engine.executeQuery("SELECT COVAR_POP(x, y) FROM cov_t").getRows().get(0).getValue(0)).doubleValue();
        assertTrue(cov > 0);
    }

    @Test public void testCovarSamp() {
        engine.execute("CREATE TABLE covs_t (x DOUBLE, y DOUBLE)");
        engine.execute("INSERT INTO covs_t VALUES (1,2),(2,4),(3,6)");
        double cov = ((Number) engine.executeQuery("SELECT COVAR_SAMP(x, y) FROM covs_t").getRows().get(0).getValue(0)).doubleValue();
        assertTrue(cov > 0);
    }

    // ── PERCENTILE_CONT / PERCENTILE_DISC — basic accumulator test ────────────

    @Test public void testPercentileCont50() {
        // Test via direct accumulator (WITHIN GROUP syntax not yet in grammar)
        PercentileCont.PercentileContAccumulator acc =
            new PercentileCont.PercentileContAccumulator(0.5);
        acc.accumulate(10.0); acc.accumulate(20.0); acc.accumulate(30.0); acc.accumulate(40.0);
        assertEquals(25.0, ((Number) acc.getResult()).doubleValue(), 0.001);
    }

    @Test public void testPercentileDisc50() {
        PercentileDisc.PercentileDiscAccumulator acc =
            new PercentileDisc.PercentileDiscAccumulator(0.5);
        acc.accumulate(10.0); acc.accumulate(20.0); acc.accumulate(30.0); acc.accumulate(40.0);
        assertNotNull(acc.getResult());
    }
}
