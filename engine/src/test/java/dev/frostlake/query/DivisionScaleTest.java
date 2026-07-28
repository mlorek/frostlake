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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Division follows Snowflake's result scale — {@code max(S1, min(S1 + 6, 12))} with S1 the dividend's
 * scale — rounding half away from zero. Integer / integer therefore has six fractional digits:
 * {@code 1/3 = 0.333333}, not a 10- or 18-digit Java expansion. DIV0/DIV0NULL divide the same way.
 */
public class DivisionScaleTest extends BaseDatabaseTest {

    private BigDecimal qbd(final String sql) {
        return new BigDecimal(engine.executeQuery(sql).getRows().get(0).getValue(0).toString());
    }

    @Test
    public void testIntegerDivisionScaleSix() {
        assertEquals(0, qbd("SELECT 1/3").compareTo(new BigDecimal("0.333333")));
        assertEquals(0, qbd("SELECT 2/3").compareTo(new BigDecimal("0.666667")));
    }

    @Test
    public void testExactDivisionUnchangedValue() {
        assertEquals(0, qbd("SELECT 10/4").compareTo(new BigDecimal("2.5")));
    }

    @Test
    public void testFractionalDividendGetsScalePlusSix() {
        // S1 = 1 -> result scale 7.
        assertEquals(0, qbd("SELECT 0.5/3").compareTo(new BigDecimal("0.1666667")));
    }

    @Test
    public void testDiv0MatchesOperatorScale() {
        assertEquals(0, qbd("SELECT DIV0(1, 3)").compareTo(new BigDecimal("0.333333")));
        assertEquals(0, qbd("SELECT DIV0(1, 0)").compareTo(BigDecimal.ZERO));
        assertEquals(0, qbd("SELECT DIV0NULL(1, NULL)").compareTo(BigDecimal.ZERO));
    }

    @Test
    public void testNegativeDivisionRoundsAwayFromZero() {
        assertEquals(0, qbd("SELECT -1/3").compareTo(new BigDecimal("-0.333333")));
        assertEquals(0, qbd("SELECT -2/3").compareTo(new BigDecimal("-0.666667")));
    }
}
