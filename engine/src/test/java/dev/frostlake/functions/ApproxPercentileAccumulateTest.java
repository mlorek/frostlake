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
import dev.frostlake.storage.Row;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * APPROX_PERCENTILE_ACCUMULATE, _COMBINE and _ESTIMATE: the digest state (sorted unit centroids in the
 * fifteen-decimal exponent form), the merge, the two estimate regimes (exact over unit weights, the
 * digest's mean-position interpolation over any other), the argument refusals and the whole-partition
 * window family. Every expectation is live-verified.
 */
public class ApproxPercentileAccumulateTest extends BaseDatabaseTest {

    /** The state of wf as a result cell: every DOUBLE in the fifteen-decimal form. */
    private static final String STATE_OF_WF = "{\"state\":[1.500000000000000e+00,1.000000000000000e+00,"
        + "2.500000000000000e+00,1.000000000000000e+00,3.500000000000000e+00,1.000000000000000e+00,3.500000000000000e+00,"
        + "1.000000000000000e+00],\"type\":\"tdigest\",\"version\":1}";
    /** The same state through a text conversion, which spells it exactly as the result cell does. */
    private static final String STATE_OF_WF_TEXT = STATE_OF_WF;
    private static final String EMPTY_STATE = "{\"state\":[],\"type\":\"tdigest\",\"version\":1}";

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE wf (n NUMBER(10,2), s VARCHAR, k VARCHAR)");
        engine.execute("INSERT INTO wf SELECT 1.5, 'a', 'x' UNION ALL SELECT 2.5, 'b', 'x' UNION ALL SELECT 3.5, 'b', 'y' "
            + "UNION ALL SELECT 3.5, 'c', 'y' UNION ALL SELECT NULL, NULL, 'y'");
        engine.execute("CREATE TABLE wt (s VARCHAR, n NUMBER(10,2), f FLOAT, i INTEGER, d DATE, b BOOLEAN, v VARIANT, bin BINARY)");
        engine.execute("INSERT INTO wt SELECT 'c', 3.5, 0.1, 7, '2024-01-03', TRUE, PARSE_JSON('{\"a\":1}'), TO_BINARY('AB') "
            + "UNION ALL SELECT 'b', 1.5, 0.2, 3, '2024-01-01', FALSE, PARSE_JSON('\"x\"'), TO_BINARY('CD') "
            + "UNION ALL SELECT 'a', 2.5, 0.1, 3, '2024-01-02', TRUE, PARSE_JSON('[1]'), TO_BINARY('EF') "
            + "UNION ALL SELECT 'b', 1.5, 0.3, 9, '2024-01-01', TRUE, PARSE_JSON('{\"a\":1}'), TO_BINARY('01')");
        engine.execute("CREATE TABLE wr (n NUMBER(10,2), b NUMBER(38,0), s VARCHAR)");
        engine.execute("INSERT INTO wr SELECT 1.10, 12345678, 'a' UNION ALL SELECT 2.00, 12345678, 'a' "
            + "UNION ALL SELECT 1.10, 7, NULL");
        engine.execute("CREATE TABLE wp (v FLOAT)");
        engine.execute("INSERT INTO wp SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 "
            + "UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10");
    }

    private List<Row> rows(final String sql) {
        return engine.executeQuery(sql).getRows();
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private String text(final String sql) {
        return String.valueOf(row(sql).getValue(0));
    }

    private double number(final String sql) {
        return ((Number) row(sql).getValue(0)).doubleValue();
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private void assertRefusal(final String sql, final String... fragments) {
        final String message = refusal(sql);
        for (final String fragment : fragments) {
            assertTrue(message.contains(fragment), sql + " -> " + message);
        }
    }

    /** A state built from the given centroid list, for the estimate's weighted regime. */
    private static String state(final String centroids) {
        return "PARSE_JSON('{\"state\":[" + centroids + "],\"type\":\"tdigest\",\"version\":1}')";
    }

    @Test
    public void theStateListsSortedUnitCentroids() {
        assertEquals(STATE_OF_WF, text("SELECT APPROX_PERCENTILE_ACCUMULATE(n) FROM wf"));
        assertEquals("{\"state\":[1.000000000000000e-01,1.000000000000000e+00,1.000000000000000e-01,1.000000000000000e+00,"
            + "2.000000000000000e-01,1.000000000000000e+00,3.000000000000000e-01,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}",
            text("SELECT APPROX_PERCENTILE_ACCUMULATE(f) FROM wt"));
        assertEquals("{\"state\":[3.000000000000000e+00,1.000000000000000e+00,3.000000000000000e+00,1.000000000000000e+00,"
            + "7.000000000000000e+00,1.000000000000000e+00,9.000000000000000e+00,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}",
            text("SELECT APPROX_PERCENTILE_ACCUMULATE(i) FROM wt"));
        final Row widths = row("SELECT APPROX_PERCENTILE_ACCUMULATE(n), APPROX_PERCENTILE_ACCUMULATE(b) FROM wr");
        assertEquals("{\"state\":[1.100000000000000e+00,1.000000000000000e+00,1.100000000000000e+00,1.000000000000000e+00,"
            + "2.000000000000000e+00,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}", String.valueOf(widths.getValue(0)));
        assertEquals("{\"state\":[7.000000000000000e+00,1.000000000000000e+00,1.234567800000000e+07,1.000000000000000e+00,"
            + "1.234567800000000e+07,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}",
            String.valueOf(widths.getValue(1)));
        assertEquals(EMPTY_STATE, text("SELECT APPROX_PERCENTILE_ACCUMULATE(n) FROM wf WHERE n > 100"));
        final List<Row> grouped = rows("SELECT APPROX_PERCENTILE_ACCUMULATE(n) FROM wf GROUP BY k ORDER BY k");
        assertEquals(2, grouped.size());
        assertEquals("{\"state\":[1.500000000000000e+00,1.000000000000000e+00,2.500000000000000e+00,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}", String.valueOf(grouped.get(0).getValue(0)));
        assertEquals("{\"state\":[3.500000000000000e+00,1.000000000000000e+00,3.500000000000000e+00,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}", String.valueOf(grouped.get(1).getValue(0)));
        assertEquals(STATE_OF_WF_TEXT, text("SELECT TO_VARCHAR(APPROX_PERCENTILE_ACCUMULATE(n)) FROM wf"));
        final Row member = row("SELECT APPROX_PERCENTILE_ACCUMULATE(n):state[0], TYPEOF(APPROX_PERCENTILE_ACCUMULATE(n):state[0]), "
            + "TYPEOF(APPROX_PERCENTILE_ACCUMULATE(n)), SYSTEM$TYPEOF(APPROX_PERCENTILE_ACCUMULATE(n)), "
            + "SYSTEM$TYPEOF(APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), 0.5)) FROM wf");
        assertEquals("1.5", String.valueOf(member.getValue(0)));
        assertEquals("DOUBLE", String.valueOf(member.getValue(1)));
        assertEquals("OBJECT", String.valueOf(member.getValue(2)));
        assertEquals("OBJECT[LOB]", String.valueOf(member.getValue(3)));
        assertEquals("FLOAT[DOUBLE]", String.valueOf(member.getValue(4)));
    }

    @Test
    public void estimateOverUnitCentroidsIsApproxPercentile() {
        assertEquals(3.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), 0.5) FROM wf"), 1e-12);
        assertEquals(3.5, number("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), 0.9) FROM wf"), 1e-12);
        assertEquals(1.5, number("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), 0) FROM wt"), 1e-12);
        assertEquals(3.5, number("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), 1) FROM wt"), 1e-12);
        assertEquals(3.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), '0.5') FROM wf"), 1e-12);
        final double[] fractions = {0.05, 0.1, 0.25, 0.5, 0.9, 0.95};
        final double[] expected = {1.45, 1.9, 3.25, 5.5, 9.1, 9.55};
        for (int i = 0; i < fractions.length; i++) {
            final Row both = row("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(v), " + fractions[i] + "), "
                + "APPROX_PERCENTILE(v, " + fractions[i] + ") FROM wp");
            assertEquals(expected[i], ((Number) both.getValue(0)).doubleValue(), 1e-12, "fraction " + fractions[i]);
            assertEquals(expected[i], ((Number) both.getValue(1)).doubleValue(), 1e-12, "fraction " + fractions[i]);
        }
        final Row two = row("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(v), 0.25), "
            + "APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(v), 0.75) FROM wp WHERE v <= 2");
        assertEquals(1.25, ((Number) two.getValue(0)).doubleValue(), 1e-12);
        assertEquals(1.75, ((Number) two.getValue(1)).doubleValue(), 1e-12);
        final Row tenths = row("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(f), 0.5), APPROX_PERCENTILE(f, 0.5) FROM wt");
        assertEquals(0.15, ((Number) tenths.getValue(0)).doubleValue(), 1e-12);
        assertEquals(((Number) tenths.getValue(1)).doubleValue(), ((Number) tenths.getValue(0)).doubleValue(), 1e-12);
        final Row thousands = row("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(SEQ4()), 0.5), "
            + "APPROX_PERCENTILE(SEQ4(), 0.5) FROM TABLE(GENERATOR(ROWCOUNT => 2000))");
        assertEquals(999.5, ((Number) thousands.getValue(0)).doubleValue(), 1e-9);
        assertEquals(999.5, ((Number) thousands.getValue(1)).doubleValue(), 1e-9);
    }

    @Test
    public void estimateOverWeightedCentroidsInterpolatesByMeanPosition() {
        final String skewed = state("1,1,9,3");
        assertEquals(7.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + skewed + ", 0.5)"), 1e-12);
        assertEquals(3.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + skewed + ", 0.25)"), 1e-12);
        assertEquals(0.6, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + skewed + ", 0.1)"), 1e-12);
        assertEquals(13.4, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + skewed + ", 0.9)"), 1e-12);
        assertEquals(8.6, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + skewed + ", 0.6)"), 1e-12);
        assertEquals(-1.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + skewed + ", 0)"), 1e-12);
        assertEquals(15.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + skewed + ", 1)"), 1e-12);
        final String mixed = state("1,1,5,1,9,2");
        assertEquals(19.0 / 3, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + mixed + ", 0.5)"), 1e-9);
        assertEquals(3.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + mixed + ", 0.25)"), 1e-12);
        assertEquals(10.6, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + mixed + ", 0.9)"), 1e-12);
        final String doubled = state("1,2,5,2,9,2");
        assertEquals(5.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + doubled + ", 0.5)"), 1e-12);
        assertEquals(2.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + doubled + ", 0.25)"), 1e-12);
        assertEquals(-0.4, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + doubled + ", 0.05)"), 1e-12);
        assertEquals(11.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + doubled + ", 1)"), 1e-12);
        final String three = state("1,1,9,3,20,1");
        assertEquals(9.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + three + ", 0.5)"), 1e-12);
        assertEquals(20.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + three + ", 0.9)"), 1e-12);
        assertEquals(1.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + three + ", 0.1)"), 1e-12);
        assertEquals(5.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + state("5,2") + ", 0.5)"), 1e-12);
        assertEquals(5.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + state("5,1") + ", 0.5)"), 1e-12);
        assertEquals(1.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + state("1,3") + ", 0.9)"), 1e-12);
        assertEquals(1.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + state("1,0.5,9,0.5") + ", 0.25)"), 1e-12);
        // Unit weights read exactly, however the state is ordered.
        assertEquals(3.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + state("1,1,5,1,9,1") + ", 0.25)"), 1e-12);
        assertEquals(3.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(" + state("9,1,1,1,5,1") + ", 0.25)"), 1e-12);
    }

    @Test
    public void combineGathersEveryCentroid() {
        assertEquals(STATE_OF_WF,
            text("SELECT APPROX_PERCENTILE_COMBINE(st) FROM (SELECT APPROX_PERCENTILE_ACCUMULATE(n) AS st FROM wf GROUP BY k)"));
        assertEquals(3.0, number("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_COMBINE(st), 0.5) FROM "
            + "(SELECT APPROX_PERCENTILE_ACCUMULATE(n) AS st FROM wf GROUP BY k)"), 1e-12);
        assertEquals("{\"state\":[1.500000000000000e+00,1.000000000000000e+00,1.500000000000000e+00,1.000000000000000e+00,"
            + "2.500000000000000e+00,1.000000000000000e+00,3.500000000000000e+00,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}",
            text("SELECT APPROX_PERCENTILE_COMBINE(st) FROM (SELECT APPROX_PERCENTILE_ACCUMULATE(n) AS st FROM wt GROUP BY s)"));
        assertEquals(EMPTY_STATE,
            text("SELECT APPROX_PERCENTILE_COMBINE(st) FROM (SELECT APPROX_PERCENTILE_ACCUMULATE(n) AS st FROM wf WHERE n > 100)"));
        assertEquals(EMPTY_STATE, text("SELECT APPROX_PERCENTILE_COMBINE(NULL) FROM wf"));
        assertEquals("{\"state\":[1.100000000000000e+00,1.000000000000000e+00,1.100000000000000e+00,1.000000000000000e+00,"
            + "2.000000000000000e+00,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}",
            text("SELECT APPROX_PERCENTILE_COMBINE(st) FROM (SELECT APPROX_PERCENTILE_ACCUMULATE(n) AS st FROM wr UNION ALL SELECT NULL)"));
        // A constant state is a state per row, its weights kept: five rows of [9 x3].
        assertEquals("{\"state\":[9.000000000000000e+00,3.000000000000000e+00,9.000000000000000e+00,3.000000000000000e+00,"
            + "9.000000000000000e+00,3.000000000000000e+00,9.000000000000000e+00,3.000000000000000e+00,"
            + "9.000000000000000e+00,3.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}",
            text("SELECT APPROX_PERCENTILE_COMBINE(" + state("9,3") + ") FROM wf"));
        assertRefusal("SELECT APPROX_PERCENTILE_COMBINE(PARSE_JSON('{\"a\":1}')) FROM wf",
            "First argument of the function must be an object which maps the key 'state' to an array");
    }

    @Test
    public void argumentsAreRefusedAsLiveRefusesThem() {
        assertRefusal("SELECT APPROX_PERCENTILE_ACCUMULATE(d) FROM wt", "SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'APPROX_PERCENTILE_ACCUMULATE': (DATE)");
        assertRefusal("SELECT APPROX_PERCENTILE_ACCUMULATE(b) FROM wt", "error line 1 at position 7",
            "Invalid argument types for function 'APPROX_PERCENTILE_ACCUMULATE': (BOOLEAN)");
        assertRefusal("SELECT APPROX_PERCENTILE_ACCUMULATE(bin) FROM wt", "error line 1 at position 7",
            "Invalid argument types for function 'APPROX_PERCENTILE_ACCUMULATE': (BINARY(8388608))");
        assertRefusal("SELECT APPROX_PERCENTILE_ACCUMULATE(s) FROM wf", "Numeric value 'a' is not recognized");
        assertRefusal("SELECT APPROX_PERCENTILE_ACCUMULATE(v) FROM wt", "Failed to cast variant value {\"a\":1} to REAL");
        assertRefusal("SELECT APPROX_PERCENTILE_ACCUMULATE(n, 0.5) FROM wt", "error line 1 at position 7",
            "too many arguments for function [APPROX_PERCENTILE_ACCUMULATE(WT.N, 0.5)] expected 1, got 2");
        assertRefusal("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), 1.5) FROM wt", "SQL compilation error:\n"
            + "Invalid value [1.5] for function 'APPROX_PERCENTILE_ESTIMATE', parameter 2: Percentile must be between 0 and 1 inclusive.");
        assertRefusal("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), f) FROM wt GROUP BY f",
            "SQL compilation error:\nargument 2 to function APPROX_PERCENTILE_ESTIMATE needs to be constant, found 'WT.F'");
        assertRefusal("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n)) FROM wt", "error line 1 at position 7",
            "not enough arguments for function [APPROX_PERCENTILE_ESTIMATE(", "expected 2, got 1");
        assertRefusal("SELECT APPROX_PERCENTILE_ESTIMATE(PARSE_JSON('{\"a\":1}'), 0.5) FROM wt",
            "First argument of the function must be an object which maps the key 'state' to an array");
        assertNull(row("SELECT APPROX_PERCENTILE_ESTIMATE(NULL, 0.5) FROM wt LIMIT 1").getValue(0));
        assertNull(row("SELECT APPROX_PERCENTILE_ESTIMATE(APPROX_PERCENTILE_ACCUMULATE(n), 0.5) FROM wt WHERE n > 100").getValue(0));
    }

    @Test
    public void windowFormsAreWholePartitionOnly() {
        assertEquals(STATE_OF_WF, text("SELECT APPROX_PERCENTILE_ACCUMULATE(n) OVER () FROM wf LIMIT 1"));
        assertEquals("{\"state\":[1.100000000000000e+00,1.000000000000000e+00,2.000000000000000e+00,1.000000000000000e+00],"
            + "\"type\":\"tdigest\",\"version\":1}",
            text("SELECT APPROX_PERCENTILE_ACCUMULATE(n) OVER (PARTITION BY s ORDER BY n ROWS BETWEEN UNBOUNDED PRECEDING AND "
                + "UNBOUNDED FOLLOWING) FROM wr ORDER BY s LIMIT 1"));
        assertEquals(STATE_OF_WF, text("SELECT APPROX_PERCENTILE_COMBINE(st) OVER () FROM "
            + "(SELECT APPROX_PERCENTILE_ACCUMULATE(n) AS st FROM wf GROUP BY k) LIMIT 1"));
        assertRefusal("SELECT APPROX_PERCENTILE_ACCUMULATE(n) OVER (ORDER BY n) FROM wf LIMIT 1",
            "error line 1 at position 39", "Cumulative window frame unsupported for function APPROX_PERCENTILE_ACCUMULATE");
        assertRefusal("SELECT APPROX_PERCENTILE_COMBINE(st) OVER (ORDER BY st) FROM "
            + "(SELECT APPROX_PERCENTILE_ACCUMULATE(n) AS st FROM wf GROUP BY k)",
            "Cumulative window frame unsupported for function APPROX_PERCENTILE_COMBINE");
    }
}
