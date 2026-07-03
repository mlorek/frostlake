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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The 3-argument ROUND honours the rounding-mode argument: {@code HALF_TO_EVEN} (banker's rounding) vs
 * the default {@code HALF_AWAY_FROM_ZERO}. Previously the mode was parsed but silently dropped.
 */
public class RoundModeTest extends BaseDatabaseTest {

    private double round(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void halfToEvenRoundsTiesToNearestEven() {
        assertEquals(2.0, round("SELECT ROUND(2.5, 0, 'HALF_TO_EVEN')"), 1e-9);
        assertEquals(4.0, round("SELECT ROUND(3.5, 0, 'HALF_TO_EVEN')"), 1e-9);
        assertEquals(0.0, round("SELECT ROUND(-0.5, 0, 'HALF_TO_EVEN')"), 1e-9);
        assertEquals(2.0, round("SELECT ROUND(2.4, 0, 'HALF_TO_EVEN')"), 1e-9);
    }

    @Test
    public void halfAwayFromZeroIsTheDefault() {
        assertEquals(3.0, round("SELECT ROUND(2.5, 0)"), 1e-9);
        assertEquals(3.0, round("SELECT ROUND(2.5, 0, 'HALF_AWAY_FROM_ZERO')"), 1e-9);
        assertEquals(-1.0, round("SELECT ROUND(-0.5, 0)"), 1e-9);
    }

    @Test
    public void modeIsCaseInsensitive() {
        assertEquals(2.0, round("SELECT ROUND(2.5, 0, 'half_to_even')"), 1e-9);
    }
}
