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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the FRACTION-returning rankings declare, and what an APPROXIMATE operand does to arithmetic.
 * Both came out of asking whether "live rounds a double to ten decimals" is one rule; it is not.
 *
 * <p>TO_VARCHAR of a double already agreed before any of this — {@code TO_VARCHAR(SQRT(2))} is
 * {@code 1.414213562} on both engines — so Frostlake's double-to-text is right and the ten decimals
 * are not the thing to fix. What was wrong was narrower and more interesting:
 *
 * <pre>
 *   CUME_DIST / PERCENT_RANK   declared VARCHAR, where live declares FLOAT
 *   RATIO_TO_REPORT            declared VARCHAR, where live declares NUMBER(38,6) — it is EXACT,
 *                              which neither its name nor its neighbours suggest
 *   &lt;FLOAT&gt; / &lt;FLOAT&gt;          answered 0.3333333 — SEVEN digits
 * </pre>
 *
 * <p>The division is the one worth remembering. A FLOAT column's value was STORED exactly, so the
 * arithmetic — which picks its rule from the runtime class — read it as fixed-point and took
 * Snowflake's NUMBER division scale of MIN(s + 6, 12), giving seven digits for a quotient that should
 * carry a double's. The declared type is what says "approximate", not the class the value arrived in,
 * and that rule still guards the exact carrier a FLOAT-declared expression can arrive in now that the
 * column itself holds a double.
 */
public class ApproximateResultShapeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, f FLOAT, g FLOAT)");
        engine.execute("INSERT INTO gw SELECT 1, 1.0, 3.0");
        engine.execute("INSERT INTO gw SELECT 2, 2.0, 3.0");
        engine.execute("INSERT INTO gw SELECT 3, 3.0, 7.0");
    }

    /** The declared type of the first result column. */
    private String typeOf(final String sql) {
        final DataType type = engine.executeQuery(sql).getColumns().get(0).getDataType();
        if (type == null) {
            return "null";
        }
        return type instanceof NumericType && !NumericType.isApproximate(type)
            ? type.getName() + "(" + ((NumericType) type).getPrecision() + ","
                + ((NumericType) type).getScale() + ")"
            : type.getName();
    }

    /** Every row's first column, joined — read through TO_VARCHAR so the ENGINE renders it. */
    private String rendered(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT TO_VARCHAR(" + expr + ") FROM gw ORDER BY a");
        final StringBuilder all = new StringBuilder();
        while (rs.next()) {
            if (all.length() > 0) {
                all.append(",");
            }
            all.append(String.valueOf(rs.getValue(0)));
        }
        return all.toString();
    }

    /** The two APPROXIMATE rankings. */
    @Test
    public void cumeDistAndPercentRankAreApproximate() {
        assertEquals("FLOAT", typeOf("SELECT CUME_DIST() OVER (ORDER BY a) FROM gw"));
        assertEquals("FLOAT", typeOf("SELECT PERCENT_RANK() OVER (ORDER BY a) FROM gw"));
        assertEquals("FLOAT", typeOf("SELECT CUME_DIST() OVER (PARTITION BY g ORDER BY a) FROM gw"));
    }

    /** And the one that is EXACT, which is the odd one of the three. */
    @Test
    public void ratioToReportIsExactToSixDecimals() {
        assertEquals("NUMBER(38,6)", typeOf("SELECT RATIO_TO_REPORT(a) OVER () FROM gw"));
    }

    /** A double renders at ten significant digits, which Frostlake already did. */
    @Test
    public void aDoubleRendersAtTenSignificantDigits() {
        assertEquals("0.3333333333,0.6666666667,1", rendered("CUME_DIST() OVER (ORDER BY a)"));
        assertEquals("1.414213562,1.414213562,1.414213562", rendered("SQRT(2)"));
        assertEquals("2.718281828,2.718281828,2.718281828", rendered("EXP(1)"));
        assertEquals("3.141592654,3.141592654,3.141592654", rendered("PI()"));
    }

    /** A FLOAT divided by a FLOAT keeps a double's precision, not a NUMBER division's seven digits. */
    @Test
    public void floatDivisionKeepsItsPrecision() {
        assertEquals("0.3333333333,0.6666666667,0.4285714286", rendered("f / g"));
        assertEquals("0.3333333333,0.3333333333,0.3333333333", rendered("1.0::FLOAT / 3.0::FLOAT"));
        assertEquals("-0.3333333333,-0.3333333333,-0.3333333333",
            rendered("-1.0::FLOAT / 3.0::FLOAT"));
    }

    /** The EXACT division beside it is untouched — it still carries Snowflake's NUMBER scale. */
    @Test
    public void exactDivisionIsUntouched() {
        assertEquals("NUMBER(7,6)", typeOf("SELECT 1 / 3 FROM gw"));
        assertEquals("0.333333,0.333333,0.333333", rendered("1 / 3"));
        assertEquals("2.000000", String.valueOf(
            engine.executeQuery("SELECT TO_VARCHAR(AVG(a)) FROM gw").getRows().get(0).getValue(0)));
    }

    /**
     * A FLOAT column read straight back is left alone by all of this; its rendering is pinned by the
     * stored-float rendering cells rather than here.
     */
    @Test
    public void aPlainFloatColumnStillOrdersAndCompares() {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM gw WHERE g > 3");
        rs.next();
        assertEquals("1", String.valueOf(rs.getValue(0)));
        assertEquals("FLOAT", typeOf("SELECT g FROM gw"));
    }
}
