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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numeric aggregates over a VARIANT or VARCHAR argument: which carrier and declared type each
 * family answers with. MEDIAN and PERCENTILE_CONT convert every value to a whole number and
 * interpolate at three decimals (NUMBER(12,3)); PERCENTILE_DISC hands back the picked value's whole
 * number (NUMBER(9,0)); the computing family — SUM, AVG, VARIANCE and its kin, STDDEV — takes the
 * FLOAT tier; MODE, MIN and MAX keep the VARIANT (or the VARCHAR) as it is. A VARIANT boolean converts
 * as 1 / 0 for the interpolating aggregates, a VARIANT string spelling no number refuses at row time.
 * All live-verified.
 */
public class VariantInputAggregateCarrierTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE va (vt VARIANT, s VARCHAR(5), n NUMBER(10,2), vd VARIANT, vs VARIANT, f FLOAT)");
        engine.execute("INSERT INTO va SELECT TO_VARIANT(2), '3', 2.50, PARSE_JSON('2.5'), PARSE_JSON('\"2\"'), 2.5 "
            + "UNION ALL SELECT TO_VARIANT(4), '5', 4.50, PARSE_JSON('4.5'), PARSE_JSON('\"4\"'), 4.5");
    }

    /** The single row — a typeof-only select over aggregates answers one row per input row on both engines, so those carry LIMIT 1. */
    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private String text(final String sql) {
        return String.valueOf(row(sql).getValue(0));
    }

    private static String cell(final Row row, final int index) {
        return String.valueOf(row.getValue(index));
    }

    private static void assertDouble(final double expected, final Object value, final String what) {
        assertTrue(value instanceof Double, what + " should be a double, was " + (value == null ? "null" : value.getClass()));
        assertEquals(expected, ((Double) value).doubleValue(), 1e-9, what);
    }

    private void assertRefusal(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage().contains(fragment), sql + " -> " + refused.getMessage());
    }

    @Test
    public void declaredTypesOverAVariant() {
        final Row ordering = row("SELECT SYSTEM$TYPEOF(MEDIAN(vt)), SYSTEM$TYPEOF(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY vt)), "
            + "SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY vt)), SYSTEM$TYPEOF(MODE(vt)), SYSTEM$TYPEOF(MIN(vt)), "
            + "SYSTEM$TYPEOF(MAX(vt)) FROM va LIMIT 1");
        assertEquals("NUMBER(12,3)[SB8]", cell(ordering, 0));
        assertEquals("NUMBER(12,3)[SB8]", cell(ordering, 1));
        assertEquals("NUMBER(9,0)[SB4]", cell(ordering, 2));
        assertEquals("VARIANT[LOB]", cell(ordering, 3));
        assertEquals("VARIANT[LOB]", cell(ordering, 4));
        assertEquals("VARIANT[LOB]", cell(ordering, 5));
        final Row computing = row("SELECT SYSTEM$TYPEOF(VARIANCE(vt)), SYSTEM$TYPEOF(VAR_POP(vt)), SYSTEM$TYPEOF(VAR_SAMP(vt)), "
            + "SYSTEM$TYPEOF(STDDEV(vt)), SYSTEM$TYPEOF(SUM(vt)), SYSTEM$TYPEOF(AVG(vt)) FROM va LIMIT 1");
        for (int i = 0; i < 6; i++) {
            assertEquals("FLOAT[DOUBLE]", cell(computing, i), "column " + i);
        }
        final Row decimals = row("SELECT SYSTEM$TYPEOF(MEDIAN(vd)), SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY vd)), "
            + "SYSTEM$TYPEOF(MODE(vd)), SYSTEM$TYPEOF(MIN(vd)), SYSTEM$TYPEOF(SUM(vd)), SYSTEM$TYPEOF(AVG(vd)) FROM va LIMIT 1");
        assertEquals("NUMBER(12,3)[SB8]", cell(decimals, 0));
        assertEquals("NUMBER(9,0)[SB4]", cell(decimals, 1));
        assertEquals("VARIANT[LOB]", cell(decimals, 2));
        assertEquals("VARIANT[LOB]", cell(decimals, 3));
        assertEquals("FLOAT[DOUBLE]", cell(decimals, 4));
        assertEquals("FLOAT[DOUBLE]", cell(decimals, 5));
        final Row strings = row("SELECT SYSTEM$TYPEOF(MEDIAN(vs)), SYSTEM$TYPEOF(MODE(vs)), SYSTEM$TYPEOF(MIN(vs)), "
            + "SYSTEM$TYPEOF(SUM(vs)) FROM va LIMIT 1");
        assertEquals("NUMBER(12,3)[SB8]", cell(strings, 0));
        assertEquals("VARIANT[LOB]", cell(strings, 1));
        assertEquals("VARIANT[LOB]", cell(strings, 2));
        assertEquals("FLOAT[DOUBLE]", cell(strings, 3));
        final Row windowed = row("SELECT SYSTEM$TYPEOF(MODE(vt) OVER ()), SYSTEM$TYPEOF(MIN(vt) OVER ()), "
            + "SYSTEM$TYPEOF(MEDIAN(vt) OVER ()), SYSTEM$TYPEOF(VARIANCE(vt) OVER ()) FROM va LIMIT 1");
        assertEquals("VARIANT[LOB]", cell(windowed, 0));
        assertEquals("VARIANT[LOB]", cell(windowed, 1));
        assertEquals("NUMBER(12,3)[SB8]", cell(windowed, 2));
        assertEquals("FLOAT[DOUBLE]", cell(windowed, 3));
    }

    @Test
    public void declaredTypesOverAVarchar() {
        final Row typed = row("SELECT SYSTEM$TYPEOF(MEDIAN(s)), SYSTEM$TYPEOF(PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY s)), "
            + "SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY s)), SYSTEM$TYPEOF(MODE(s)), SYSTEM$TYPEOF(MIN(s)), "
            + "SYSTEM$TYPEOF(VARIANCE(s)), SYSTEM$TYPEOF(STDDEV(s)), SYSTEM$TYPEOF(SUM(s)), SYSTEM$TYPEOF(AVG(s)) FROM va LIMIT 1");
        assertEquals("NUMBER(12,3)[SB8]", cell(typed, 0));
        assertEquals("NUMBER(12,3)[SB8]", cell(typed, 1));
        assertEquals("NUMBER(9,0)[SB4]", cell(typed, 2));
        assertEquals("VARCHAR(5)[LOB]", cell(typed, 3));
        assertEquals("VARCHAR(5)[LOB]", cell(typed, 4));
        for (int i = 5; i < 9; i++) {
            assertEquals("FLOAT[DOUBLE]", cell(typed, i), "column " + i);
        }
    }

    @Test
    public void valuesOverAVariant() {
        final Row ordering = row("SELECT MEDIAN(vt), PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY vt), "
            + "PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY vt), MIN(vt), MAX(vt) FROM va");
        assertEquals("3.000", cell(ordering, 0));
        assertEquals("3.000", cell(ordering, 1));
        assertEquals("2", cell(ordering, 2));
        assertEquals("2", cell(ordering, 3));
        assertEquals("4", cell(ordering, 4));
        final Row computing = row("SELECT VARIANCE(vt), VAR_POP(vt), VAR_SAMP(vt), STDDEV(vt), SUM(vt), AVG(vt) FROM va");
        assertDouble(2.0, computing.getValue(0), "VARIANCE");
        assertDouble(1.0, computing.getValue(1), "VAR_POP");
        assertDouble(2.0, computing.getValue(2), "VAR_SAMP");
        assertDouble(Math.sqrt(2.0), computing.getValue(3), "STDDEV");
        assertDouble(6.0, computing.getValue(4), "SUM");
        assertDouble(3.0, computing.getValue(5), "AVG");
        final Row decimals = row("SELECT MEDIAN(vd), PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY vd), MIN(vd), VARIANCE(vd), "
            + "SUM(vd), AVG(vd) FROM va");
        assertEquals("4.000", cell(decimals, 0));
        assertEquals("3", cell(decimals, 1));
        assertEquals("2.5", cell(decimals, 2));
        assertDouble(2.0, decimals.getValue(3), "VARIANCE over decimals");
        assertDouble(7.0, decimals.getValue(4), "SUM over decimals");
        assertDouble(3.5, decimals.getValue(5), "AVG over decimals");
        final Row strings = row("SELECT MEDIAN(vs), PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY vs), TO_VARCHAR(MIN(vs)), "
            + "VARIANCE(vs), SUM(vs) FROM va");
        assertEquals("3.000", cell(strings, 0));
        assertEquals("2", cell(strings, 1));
        assertEquals("2", cell(strings, 2));
        assertDouble(2.0, strings.getValue(3), "VARIANCE over numeric strings");
        assertDouble(6.0, strings.getValue(4), "SUM over numeric strings");
        final Row kinds = row("SELECT TYPEOF(MODE(vt)), TYPEOF(MIN(vt)), TYPEOF(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY vt)), "
            + "TYPEOF(MAX(vt)), TYPEOF(MODE(vs)), TYPEOF(MIN(vs)), TYPEOF(MODE(vd)), TYPEOF(MIN(vd)), "
            + "TYPEOF(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY vd)) FROM va");
        assertEquals("INTEGER", cell(kinds, 0));
        assertEquals("INTEGER", cell(kinds, 1));
        assertEquals("INTEGER", cell(kinds, 2));
        assertEquals("INTEGER", cell(kinds, 3));
        assertEquals("VARCHAR", cell(kinds, 4));
        assertEquals("VARCHAR", cell(kinds, 5));
        assertEquals("DECIMAL", cell(kinds, 6));
        assertEquals("DECIMAL", cell(kinds, 7));
        assertEquals("INTEGER", cell(kinds, 8));
        final Row windowed = row("SELECT MIN(vt) OVER (), MEDIAN(vt) OVER () FROM va LIMIT 1");
        assertEquals("2", cell(windowed, 0));
        assertEquals("3.000", cell(windowed, 1));
    }

    @Test
    public void valuesOverAVarchar() {
        final Row typed = row("SELECT MEDIAN(s), PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY s), "
            + "PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY s), MIN(s), VARIANCE(s), STDDEV(s), SUM(s), AVG(s) FROM va");
        assertEquals("4.000", cell(typed, 0));
        assertEquals("4.000", cell(typed, 1));
        assertEquals("3", cell(typed, 2));
        assertEquals("3", cell(typed, 3));
        assertDouble(2.0, typed.getValue(4), "VARIANCE over text");
        assertDouble(Math.sqrt(2.0), typed.getValue(5), "STDDEV over text");
        assertDouble(8.0, typed.getValue(6), "SUM over text");
        assertDouble(4.0, typed.getValue(7), "AVG over text");
        // A text key is ordered as text and the picked value converted: '10' sorts before '9'.
        final Row ordered = row("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY s), MIN(s), "
            + "SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY s)) FROM (SELECT '10' s UNION ALL SELECT '9')");
        assertEquals("10", cell(ordered, 0));
        assertEquals("10", cell(ordered, 1));
        assertEquals("NUMBER(9,0)[SB4]", cell(ordered, 2));
        assertRefusal("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY s) FROM (SELECT 'x' s UNION ALL SELECT 'y')",
            "Numeric value 'x' is not recognized");
    }

    @Test
    public void textConversionsOfTheResults() {
        final Row texts = row("SELECT TO_VARCHAR(STDDEV(vt)), TO_VARCHAR(VARIANCE(vt)), TO_VARCHAR(MEDIAN(vt)), TO_VARCHAR(STDDEV(s)) FROM va");
        assertEquals("1.414213562", cell(texts, 0));
        assertEquals("2", cell(texts, 1));
        assertEquals("3.000", cell(texts, 2));
        assertEquals("1.414213562", cell(texts, 3));
    }

    @Test
    public void exactInputsKeepTheirOwnTier() {
        final Row exact = row("SELECT MEDIAN(n), VARIANCE(n), SYSTEM$TYPEOF(MEDIAN(n)), SYSTEM$TYPEOF(VARIANCE(n)), "
            + "SYSTEM$TYPEOF(STDDEV(n)) FROM va");
        assertEquals("3.50000", cell(exact, 0));
        assertEquals("2.0000000000", cell(exact, 1));
        assertEquals("NUMBER(13,5)[SB4]", cell(exact, 2));
        assertEquals("NUMBER(38,10)[SB16]", cell(exact, 3));
        assertEquals("FLOAT[DOUBLE]", cell(exact, 4));
        final Row approximate = row("SELECT MEDIAN(f), VARIANCE(f), SYSTEM$TYPEOF(MEDIAN(f)), SYSTEM$TYPEOF(VARIANCE(f)) FROM va");
        assertDouble(3.5, approximate.getValue(0), "MEDIAN over FLOAT");
        assertDouble(2.0, approximate.getValue(1), "VARIANCE over FLOAT");
        assertEquals("FLOAT[DOUBLE]", cell(approximate, 2));
        assertEquals("FLOAT[DOUBLE]", cell(approximate, 3));
    }

    @Test
    public void booleanAndUnconvertibleVariants() {
        assertRefusal("SELECT MEDIAN(vt) FROM (SELECT PARSE_JSON('\"x\"') vt UNION ALL SELECT TO_VARIANT(7))",
            "Failed to cast variant value \"x\" to FIXED");
        assertEquals("4.000", text("SELECT MEDIAN(vt) FROM (SELECT PARSE_JSON('true') vt UNION ALL SELECT TO_VARIANT(7))"));
        final Row booleans = row("SELECT MIN(vt), MEDIAN(vt) FROM (SELECT PARSE_JSON('true') vt UNION ALL SELECT PARSE_JSON('false'))");
        assertEquals("false", cell(booleans, 0));
        assertEquals("0.500", cell(booleans, 1));
    }
}
