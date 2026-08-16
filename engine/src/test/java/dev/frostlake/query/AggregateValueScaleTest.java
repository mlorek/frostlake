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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The VALUE a computed aggregate answers, at the scale its column declares.
 *
 * <p>★ THE VALUES WERE NEVER EXACT TO BEGIN WITH. The declared types already matched live; the gap was
 * read as a presentation one, and it was not. MEDIAN, PERCENTILE_CONT and PERCENTILE_DISC each
 * collapsed every input to a {@code double} before doing anything — so a NUMBER(10,2) column's 2.00
 * arrived as 2.0 with no scale left to present at, and a NUMBER(5,4)'s median came back as
 * {@code 2.0E-4}, in scientific notation, where live gives 0.0002000. Nothing downstream could have
 * repaired that; the fix is to keep the values as they arrive and derive the scale from them.
 *
 * <p>★ THE INTERPOLATING FAMILY ADDS THREE DECIMALS, the picking family adds none. MEDIAN is
 * PERCENTILE_CONT at one half and they agree cell for cell; PERCENTILE_DISC hands back one of the
 * inputs and so hands back its column's own scale. That split is what shows the rule belongs to what
 * COMPUTES a value.
 *
 * <p>★ A FLOAT COLUMN NEEDED THE DECLARED TYPE, not the values. Its values were stored exactly, so a
 * 1.5 from a FLOAT and one from a NUMBER(2,1) were the same object — and the first fix padded the FLOAT
 * answer to 2.5000 where live gives 2.5. The accumulators take the argument's declared tier from the
 * caller, which still guards the exact carrier a FLOAT-declared expression can arrive in.
 *
 * <p>★ SUM WAS THE SAME BUG WEARING A DIFFERENT HAT: it decided its tier from
 * {@code stripTrailingZeros().scale()}, so a sum of 1.00, 2.00 and 4.00 looked whole and came back as
 * the integer 7 rather than 7.00. The column's own scale, not the digits that survived it, is what
 * decides.
 *
 * <p>Left for their own tasks, and deliberately not asserted here: VARIANCE and STDDEV, which differ
 * in DIGITS rather than scale and whose live values change with the input type; the unordered window
 * AVG, which declares FEWER decimals than it produces and so needs narrowing rather than padding; and
 * MODE, whose answer over this fixture is a tie and undetermined on both engines.
 */
public class AggregateValueScaleTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE av (n380 NUMBER(38,0), n102 NUMBER(10,2),"
            + " n54 NUMBER(5,4), n21 NUMBER(2,1), n305 NUMBER(30,5), n11 NUMBER(1,1),"
            + " fl FLOAT, g INT)");
        engine.execute("INSERT INTO av VALUES (1, 1.00, 0.0001, 1.1, 1.00000, 0.1, 1.5, 1),"
            + " (2, 2.00, 0.0002, 2.2, 2.00000, 0.2, 2.5, 1),"
            + " (4, 4.00, 0.0004, 4.4, 4.00000, 0.4, 4.5, 2)");
    }

    /** Every row's every column, joined. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder all = new StringBuilder();
        while (rs.next()) {
            if (all.length() > 0) {
                all.append(",");
            }
            for (int c = 0; c < rs.getColumns().size(); c++) {
                if (c > 0) {
                    all.append("/");
                }
                all.append(String.valueOf(rs.getValue(c)));
            }
        }
        return all.toString();
    }

    /** ★ MEDIAN answers at the input's scale plus three, across every exact width. */
    @Test
    public void medianAnswersAtThreeMoreDecimals() {
        assertEquals("2.000", answer("SELECT MEDIAN(n380) FROM av"));
        assertEquals("2.00000", answer("SELECT MEDIAN(n102) FROM av"));
        assertEquals("2.2000", answer("SELECT MEDIAN(n21) FROM av"));
        assertEquals("2.00000000", answer("SELECT MEDIAN(n305) FROM av"));
        assertEquals("0.2000", answer("SELECT MEDIAN(n11) FROM av"));
    }

    /** ★ The n54 cell: not a missing zero but a whole notation, and the tell that nothing was typed. */
    @Test
    public void asmallMedianIsNotScientific() {
        assertEquals("0.0002000", answer("SELECT MEDIAN(n54) FROM av"));
    }

    /** ★ PERCENTILE_CONT is MEDIAN's rule, MEDIAN being PERCENTILE_CONT at one half. */
    @Test
    public void percentileContAgreesWithMedian() {
        assertEquals("2.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) FROM av"));
        assertEquals("0.0002000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n54) FROM av"));
        assertEquals("2.000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n380) FROM av"));
        assertEquals("1.00000",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) FROM av WHERE n380 = 1"),
            "one row interpolates nothing and is still presented at the declared scale");
    }

    /** ★ PERCENTILE_DISC PICKS a value, so it adds nothing — it keeps the column's own scale. */
    @Test
    public void percentileDiscKeepsTheColumnsOwnScale() {
        assertEquals("2", answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n380) FROM av"));
        assertEquals("2.00",
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n102) FROM av"));
        assertEquals("0.0002",
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n54) FROM av"));
        assertEquals("2.00000",
            answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY n305) FROM av"));
    }

    /** ★ A FLOAT argument takes the double tier — no scale to add three to. */
    @Test
    public void afloatArgumentTakesTheDoubleTier() {
        assertEquals("2.5", answer("SELECT MEDIAN(fl) FROM av"));
        assertEquals("2.5", answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fl) FROM av"));
        assertEquals("2.5", answer("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY fl) FROM av"));
    }

    /** ★ SUM keeps its column's scale even when every digit past the point is a zero. */
    @Test
    public void sumKeepsItsColumnsScale() {
        assertEquals("7.00", answer("SELECT SUM(n102) FROM av"));
        assertEquals("7.00000", answer("SELECT SUM(n305) FROM av"));
        assertEquals("7", answer("SELECT SUM(n380) FROM av"), "a scale-0 column still sums whole");
        assertEquals("7.7", answer("SELECT SUM(n21) FROM av"));
        assertEquals("0.0007", answer("SELECT SUM(n54) FROM av"));
    }

    /** The same rule per GROUP, not only over the whole table. */
    @Test
    public void everyGroupIsPresentedAlike() {
        assertEquals("1/1.50000,2/4.00000",
            answer("SELECT g, MEDIAN(n102) FROM av GROUP BY g ORDER BY g"));
        assertEquals("1/3.00,2/4.00", answer("SELECT g, SUM(n102) FROM av GROUP BY g ORDER BY g"));
        assertEquals("1/1.50000000,2/4.00000000",
            answer("SELECT g, AVG(n102) FROM av GROUP BY g ORDER BY g"),
            "AVG's own scale rule was already right and must not move");
    }

    /** ★ An EMPTY group stays NULL — the scale is applied to a value, never invented for one. */
    @Test
    public void anEmptyGroupStaysNull() {
        assertEquals("null", answer("SELECT MEDIAN(n102) FROM av WHERE 1 = 0"));
        assertEquals("null", answer("SELECT SUM(n102) FROM av WHERE 1 = 0"));
        assertEquals("null", answer("SELECT AVG(n102) FROM av WHERE 1 = 0"));
        assertEquals("null",
            answer("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n102) FROM av WHERE 1 = 0"));
    }

    /** The window forms share the accumulators, so they share the rule. */
    @Test
    public void thewindowFormsFollowTheSameRule() {
        assertEquals("2.00000,2.00000,2.00000",
            answer("SELECT MEDIAN(n102) OVER () FROM av ORDER BY n380"));
        assertEquals("7.00,7.00,7.00", answer("SELECT SUM(n102) OVER () FROM av ORDER BY n380"));
    }

    /** The PASS-THROUGH aggregates were right all along, and stay right. */
    @Test
    public void thePassThroughAggregatesAreUntouched() {
        assertEquals("1.00", answer("SELECT MIN(n102) FROM av"));
        assertEquals("4.00", answer("SELECT MAX(n102) FROM av"));
        assertEquals("0.0001", answer("SELECT MIN(n54) FROM av"));
    }
}
