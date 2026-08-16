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

/**
 * The variance family's precision, which depends on the INPUT's declared scale.
 *
 * <p>★ VARIANCE / VAR_SAMP / VAR_POP declare {@code NUMBER(38, min(12, 2 × input_scale + 6))} — scale 0
 * gives 6 decimals, scale 2 gives 10, and anything from 3 up is capped at 12.
 *
 * <p>★ STDDEV / STDDEV_SAMP / STDDEV_POP declare FLOAT for EVERY input, NUMBER ones included, and their
 * value is the square root of the variance ALREADY ROUNDED to the variance's declared scale. That is
 * what makes the same three numbers give two different answers:
 *
 * <pre>
 *   STDDEV over NUMBER(38,0)   sqrt(2.333333)      1.527525123
 *   STDDEV over NUMBER(10,2)   sqrt(2.3333333333)  1.527525232
 * </pre>
 *
 * <p>Live is not computing at a hidden precision — it takes the root of a variance that has already
 * been rounded, and prints the result at the FLOAT text width of ten significant digits.
 *
 * <p>★ EVERY CELL GOES THROUGH TO_VARCHAR ON PURPOSE. {@code getValue()} hands back the raw Double,
 * whose {@code toString} is Java's 17-digit form, while a live result arrives already rendered by the
 * account — so comparing the raw values makes the two engines look further apart than they are. The
 * text path is where the width rule lives, and it is the only comparable surface (see the harness's
 * VARCHAR blind spot).
 */
public class VarianceFamilyPrecisionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // The same three numbers in two declared widths, so only the input's SCALE differs.
        engine.execute("CREATE OR REPLACE TABLE vp (n380 NUMBER(38,0), n102 NUMBER(10,2),"
            + " n305 NUMBER(30,5), i INT)");
        engine.execute("INSERT INTO vp VALUES (1, 1.00, 1.00000, 1)");
        engine.execute("INSERT INTO vp VALUES (2, 2.00, 2.00000, 2)");
        engine.execute("INSERT INTO vp VALUES (4, 4.00, 4.00000, 4)");
        // 1 and 3 have variance 2 EXACTLY, which separates the computation's precision from the
        // presentation of an exact result.
        engine.execute("CREATE OR REPLACE TABLE ve (n380 NUMBER(38,0), n102 NUMBER(10,2))");
        engine.execute("INSERT INTO ve VALUES (1, 1.00)");
        engine.execute("INSERT INTO ve VALUES (3, 3.00)");
    }

    /** The rendered text of a one-row, one-column query. */
    private String text(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** VARIANCE's scale is 2 × the input's + 6, capped at 12. */
    @Test
    public void theVarianceScaleFollowsTheInputScale() {
        assertEquals("2.333333", text("SELECT TO_VARCHAR(VARIANCE(n380)) FROM vp"));
        assertEquals("2.3333333333", text("SELECT TO_VARCHAR(VARIANCE(n102)) FROM vp"));
        assertEquals("2.333333333333", text("SELECT TO_VARCHAR(VARIANCE(n305)) FROM vp"));
        assertEquals("2.333333", text("SELECT TO_VARCHAR(VARIANCE(i)) FROM vp"));
    }

    /** VAR_POP takes the same scale, over its own denominator. */
    @Test
    public void theSameScaleAppliesToVarPop() {
        assertEquals("1.555556", text("SELECT TO_VARCHAR(VAR_POP(n380)) FROM vp"));
        assertEquals("1.5555555556", text("SELECT TO_VARCHAR(VAR_POP(n102)) FROM vp"));
        assertEquals("2.333333", text("SELECT TO_VARCHAR(VAR_SAMP(n380)) FROM vp"));
        assertEquals("2.3333333333", text("SELECT TO_VARCHAR(VAR_SAMP(n102)) FROM vp"));
    }

    /**
     * ★ STDDEV is the root of the ROUNDED variance, so the same numbers at two declared scales give
     * two different answers. This is the cell the whole task was filed for.
     */
    @Test
    public void stddevIsTheRootOfTheRoundedVariance() {
        assertEquals("1.527525123", text("SELECT TO_VARCHAR(STDDEV(n380)) FROM vp"));
        assertEquals("1.527525232", text("SELECT TO_VARCHAR(STDDEV(n102)) FROM vp"));
        assertEquals("1.527525232", text("SELECT TO_VARCHAR(STDDEV(n305)) FROM vp"));
        assertEquals("1.527525123", text("SELECT TO_VARCHAR(STDDEV(i)) FROM vp"));
    }

    /** STDDEV_SAMP is its alias, and STDDEV_POP does the same over the population variance. */
    @Test
    public void theSampAndPopVariantsFollow() {
        assertEquals("1.527525123", text("SELECT TO_VARCHAR(STDDEV_SAMP(n380)) FROM vp"));
        assertEquals("1.527525232", text("SELECT TO_VARCHAR(STDDEV_SAMP(n102)) FROM vp"));
        assertEquals("1.247219307", text("SELECT TO_VARCHAR(STDDEV_POP(n380)) FROM vp"));
        assertEquals("1.247219129", text("SELECT TO_VARCHAR(STDDEV_POP(n102)) FROM vp"));
    }

    /** An EXACT variance still carries its declared scale, and its root the float width. */
    @Test
    public void anExactVarianceKeepsItsDeclaredScale() {
        assertEquals("2.000000", text("SELECT TO_VARCHAR(VARIANCE(n380)) FROM ve"));
        assertEquals("2.0000000000", text("SELECT TO_VARCHAR(VARIANCE(n102)) FROM ve"));
        assertEquals("1.414213562", text("SELECT TO_VARCHAR(STDDEV(n380)) FROM ve"));
        assertEquals("1.414213562", text("SELECT TO_VARCHAR(STDDEV(n102)) FROM ve"));
    }
}
