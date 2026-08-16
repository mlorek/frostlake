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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numeric aggregates over the families they do not take — BOOLEAN, DATE, TIME, the timestamps,
 * BINARY, ARRAY, OBJECT — refused at COMPILE time, each member in its own words. Live-verified, cell by
 * cell, with VARIANT, text, FLOAT and NUMBER accepted by every one of them:
 *
 * <ul>
 *   <li>AVG is refused in SUM's name for the bare call ("Invalid argument types for function 'SUM':
 *       (BOOLEAN)", at the call) and in its own for the windowed one — live plans a bare average as a
 *       sum over a count;</li>
 *   <li>STDDEV, STDDEV_POP, STDDEV_SAMP, VARIANCE, VAR_POP, VAR_SAMP, REGR_R2, KURTOSIS and SKEW are
 *       refused in the multiplication's name with the type twice ("'*': (DATE, DATE)") — the internal
 *       sum of squares is reached first;</li>
 *   <li>MEDIAN and the percentiles refuse a temporal, a BINARY or a semi-structured value as
 *       "incompatible types: [TIME(9)] and [NUMBER(9,0)]", unpositioned;</li>
 *   <li>CORR, the covariances, REGR_SLOPE, REGR_INTERCEPT and REGR_SYY refuse either argument as the
 *       DOUBLE conversion live plans under a null guard on the other: "invalid type [TO_DOUBLE(IFF(AT.N
 *       IS NULL, SYSTEM$NULL_TO_BOOLEAN(NULL), AT.BO))] for parameter 'TO_DOUBLE'"; REGR_AVGX, REGR_SXX
 *       and REGR_COUNT convert only their X (the second argument) and REGR_AVGY only its Y (the first),
 *       and take anything in the other place; REGR_R2 squares the offending argument like the variances,
 *       and REGR_SXY names its product with the X's type first ("'*': (NUMBER(10,2), BOOLEAN)");</li>
 *   <li>APPROX_PERCENTILE is refused in its accumulator's name with the value's type alone, anchored
 *       where live anchors it: "error line 0 at position -1".</li>
 * </ul>
 *
 * <p>Compile time means over an empty table, inside a CREATE VIEW (at the body's offset in the
 * statement), inside SYSTEM$TYPEOF, over a literal, and in any select-list position. Frostlake used
 * to answer every one of these.
 *
 * <p>NOT COVERED: MEDIAN and the percentiles over a BOOLEAN, which is an internal error on the
 * account; and the VALUES over a VARIANT, where the two engines' carriers differ.
 */
public class AggregateArgumentFamilyTest extends BaseDatabaseTest {

    private static final String[] REFUSED = {"bo", "d", "tm", "ts", "ltz", "bn", "ar", "ob"};
    private static final String[] REFUSED_TYPES = {"BOOLEAN", "DATE", "TIME(9)", "TIMESTAMP_NTZ(9)",
        "TIMESTAMP_LTZ(9)", "BINARY(4)", "ARRAY", "OBJECT"};
    private static final String[] REFUSED_FAMILIES = {"BOOLEAN", "DATE", "TIME", "TIMESTAMP_NTZ",
        "TIMESTAMP_LTZ", "BINARY", "ARRAY", "OBJECT"};

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE at (n NUMBER(10,2), bo BOOLEAN, d DATE, tm TIME, ts TIMESTAMP_NTZ,"
            + " ltz TIMESTAMP_LTZ, bn BINARY(4), ar ARRAY, ob OBJECT, vt VARIANT, s VARCHAR(5), f FLOAT)");
        engine.execute("INSERT INTO at SELECT 1.5, TRUE, '2020-01-01', '10:00:00', '2020-01-01 10:00:00',"
            + " '2020-01-01 10:00:00', TO_BINARY('AB'), ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('a', 1), PARSE_JSON('2'), '3', 1.5");
        engine.execute("INSERT INTO at SELECT 2.5, FALSE, '2020-01-02', '11:00:00', '2020-01-02 10:00:00',"
            + " '2020-01-02 10:00:00', TO_BINARY('CD'), ARRAY_CONSTRUCT(2), OBJECT_CONSTRUCT('a', 2), PARSE_JSON('4'), '5', 2.5");
    }

    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED " + rs.getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String argumentTypes(final int position, final String function, final String types) {
        return "SQL compilation error: error line 1 at position " + position
            + "|Invalid argument types for function '" + function + "': (" + types + ")";
    }

    /** ★ AVG: SUM's name bare, its own windowed, at the call. */
    @Test
    public void averageIsRefusedInSumsNameBareAndItsOwnWindowed() {
        for (int i = 0; i < REFUSED.length; i++) {
            assertEquals(argumentTypes(7, "SUM", REFUSED_TYPES[i]), outcome("SELECT AVG(" + REFUSED[i] + ") FROM at"));
            assertEquals(argumentTypes(7, "AVG", REFUSED_TYPES[i]),
                outcome("SELECT AVG(" + REFUSED[i] + ") OVER () FROM at"));
        }
        assertEquals(argumentTypes(7, "SUM", "BOOLEAN"), outcome("SELECT AVG(TRUE) FROM at"), "a literal too");
        assertEquals(argumentTypes(7, "SUM", "DATE"), outcome("SELECT AVG(DATE '2020-01-01') FROM at"));
        assertEquals(argumentTypes(10, "SUM", "BOOLEAN"), outcome("SELECT n, AVG(bo) FROM at GROUP BY n"),
            "at the call's own place");
        assertEquals(argumentTypes(7, "SUM", "BOOLEAN"), outcome("SELECT AVG(bo) FROM at WHERE FALSE"),
            "a compile-time refusal fires over zero rows");
        assertEquals(argumentTypes(36, "SUM", "BOOLEAN"),
            outcome("CREATE OR REPLACE VIEW av AS SELECT AVG(bo) AS a FROM at"), "a view body at its offset");
        assertEquals(argumentTypes(21, "SUM", "BOOLEAN"), outcome("SELECT SYSTEM$TYPEOF(AVG(bo)) FROM at"));
        for (final String taken : new String[] {"vt", "s", "f", "n"}) {
            assertTrue(outcome("SELECT AVG(" + taken + ") FROM at").startsWith("ACCEPTED"), taken);
        }
        assertEquals(argumentTypes(7, "SUM", "BOOLEAN"), outcome("SELECT SUM(bo) FROM at"), "SUM itself");
    }

    /** ★ The squares family: the multiplication's name, the type twice. */
    @Test
    public void theSquaresFamilyIsRefusedInTheMultiplicationsName() {
        for (final String function : new String[] {"STDDEV", "STDDEV_POP", "STDDEV_SAMP", "VARIANCE", "VAR_POP",
                "VAR_SAMP", "KURTOSIS", "SKEW"}) {
            for (int i = 0; i < REFUSED.length; i++) {
                assertEquals(argumentTypes(7, "*", REFUSED_TYPES[i] + ", " + REFUSED_TYPES[i]),
                    outcome("SELECT " + function + "(" + REFUSED[i] + ") FROM at"), function + " " + REFUSED[i]);
            }
            assertTrue(outcome("SELECT " + function + "(vt) FROM at").startsWith("ACCEPTED"), function);
            assertTrue(outcome("SELECT " + function + "(s) FROM at").startsWith("ACCEPTED"), function);
        }
        assertEquals(argumentTypes(7, "*", "BOOLEAN, BOOLEAN"), outcome("SELECT STDDEV(bo) OVER () FROM at"),
            "windowed too, in the same name");
        assertEquals(argumentTypes(7, "*", "DATE, DATE"), outcome("SELECT STDDEV(d) FROM at WHERE FALSE"),
            "and over zero rows");
        assertEquals(argumentTypes(10, "*", "BOOLEAN, BOOLEAN"), outcome("SELECT n, STDDEV(bo) FROM at GROUP BY n"));
        assertEquals(argumentTypes(7, "*", "BOOLEAN, BOOLEAN"), outcome("SELECT REGR_R2(n, bo) FROM at"),
            "REGR_R2 squares the offending argument at either position");
        assertEquals(argumentTypes(7, "*", "DATE, DATE"), outcome("SELECT REGR_R2(d, n) FROM at"));
        assertEquals(argumentTypes(7, "*", "NUMBER(10,2), BOOLEAN"), outcome("SELECT REGR_SXY(bo, n) FROM at"),
            "REGR_SXY multiplies x by y, the X — the second argument — listed first");
        assertEquals(argumentTypes(7, "*", "ARRAY, NUMBER(10,2)"), outcome("SELECT REGR_SXY(n, ar) FROM at"));
        assertEquals(argumentTypes(7, "*", "NUMBER(10,2), DATE"), outcome("SELECT REGR_SXY(d, n) FROM at"));
    }

    /** ★ MEDIAN and the percentiles: incompatible types, unpositioned. */
    @Test
    public void medianAndThePercentilesRefuseIncompatibleTypes() {
        for (final String[] call : new String[][] {{"MEDIAN(%s)", ""}, {"PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY %s)", ""},
                {"PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY %s)", ""}, {"MEDIAN(%s)", " OVER ()"}}) {
            for (int i = 1; i < REFUSED.length; i++) {
                assertEquals("SQL compilation error:|incompatible types: [" + REFUSED_TYPES[i] + "] and [NUMBER(9,0)]",
                    outcome("SELECT " + call[0].replace("%s", REFUSED[i]) + call[1] + " FROM at"), call[0] + " " + REFUSED[i]);
            }
            assertTrue(outcome("SELECT " + call[0].replace("%s", "vt") + call[1] + " FROM at").startsWith("ACCEPTED"), call[0]);
            assertTrue(outcome("SELECT " + call[0].replace("%s", "n") + call[1] + " FROM at").startsWith("ACCEPTED"), call[0]);
        }
        assertEquals("SQL compilation error:|incompatible types: [DATE] and [NUMBER(9,0)]",
            outcome("SELECT MEDIAN(d) FROM at WHERE FALSE"), "over zero rows");
    }

    /** ★ The two-argument statistics: the guarded DOUBLE conversion, at the argument each converts, no position. */
    @Test
    public void theTwoArgumentStatisticsRefuseTheDoubleConversion() {
        for (final String function : new String[] {"CORR", "COVAR_POP", "COVAR_SAMP", "REGR_SLOPE", "REGR_INTERCEPT",
                "REGR_SYY"}) {
            for (int i = 0; i < REFUSED.length; i++) {
                assertEquals(doubleConversion(REFUSED[i], REFUSED_FAMILIES[i]),
                    outcome("SELECT " + function + "(" + REFUSED[i] + ", n) FROM at"), function + " " + REFUSED[i]);
                assertEquals(doubleConversion(REFUSED[i], REFUSED_FAMILIES[i]),
                    outcome("SELECT " + function + "(n, " + REFUSED[i] + ") FROM at"), function + " " + REFUSED[i] + " second");
            }
            assertTrue(outcome("SELECT " + function + "(vt, n) FROM at").startsWith("ACCEPTED"), function);
            assertTrue(outcome("SELECT " + function + "(s, f) FROM at").startsWith("ACCEPTED"), function);
        }
        // The members that convert ONE argument refuse only that one: the X (second) for REGR_AVGX,
        // REGR_SXX and REGR_COUNT, the Y (first) for REGR_AVGY — the other is merely null-checked.
        for (final String function : new String[] {"REGR_AVGX", "REGR_SXX", "REGR_COUNT"}) {
            for (int i = 0; i < REFUSED.length; i++) {
                assertEquals(doubleConversion(REFUSED[i], REFUSED_FAMILIES[i]),
                    outcome("SELECT " + function + "(n, " + REFUSED[i] + ") FROM at"), function + " " + REFUSED[i]);
                assertTrue(outcome("SELECT " + function + "(" + REFUSED[i] + ", n) FROM at").startsWith("ACCEPTED"),
                    function + " takes any Y: " + REFUSED[i]);
            }
        }
        for (int i = 0; i < REFUSED.length; i++) {
            assertEquals(doubleConversion(REFUSED[i], REFUSED_FAMILIES[i]),
                outcome("SELECT REGR_AVGY(" + REFUSED[i] + ", n) FROM at"), "REGR_AVGY " + REFUSED[i]);
            assertTrue(outcome("SELECT REGR_AVGY(n, " + REFUSED[i] + ") FROM at").startsWith("ACCEPTED"),
                "REGR_AVGY takes any X: " + REFUSED[i]);
        }
        assertEquals(doubleConversion("bo", "BOOLEAN"), outcome("SELECT CORR(bo, n) OVER () FROM at"), "windowed too");
        assertEquals(doubleConversion("d", "DATE"), outcome("SELECT CORR(d, n) FROM at WHERE FALSE"), "over zero rows");
    }

    private static String doubleConversion(final String column, final String family) {
        return "SQL compilation error:|invalid type [TO_DOUBLE(IFF(AT.N IS NULL, SYSTEM$NULL_TO_" + family
            + "(NULL), AT." + column.toUpperCase() + "))] for parameter 'TO_DOUBLE'";
    }

    /** ★ APPROX_PERCENTILE: the accumulator's name, the value's type alone, anchored nowhere. */
    @Test
    public void approximatePercentileIsRefusedInItsAccumulatorsName() {
        for (int i = 0; i < REFUSED.length; i++) {
            assertEquals("SQL compilation error: error line 0 at position -1|Invalid argument types for function"
                + " 'APPROX_PERCENTILE_ACCUMULATE': (" + REFUSED_TYPES[i] + ")",
                outcome("SELECT APPROX_PERCENTILE(" + REFUSED[i] + ", 0.5) FROM at"), REFUSED[i]);
        }
        assertTrue(outcome("SELECT APPROX_PERCENTILE(vt, 0.5) FROM at").startsWith("ACCEPTED"));
        assertTrue(outcome("SELECT APPROX_PERCENTILE(s, 0.5) FROM at").startsWith("ACCEPTED"));
        assertTrue(outcome("SELECT APPROX_PERCENTILE(n, 0.5) FROM at").startsWith("ACCEPTED"));
    }
}
