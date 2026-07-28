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
 * TO_NUMBER (and its synonyms TO_DECIMAL / TO_NUMERIC): a (precision, scale) pair rounds the value to
 * the requested scale (HALF_AWAY_FROM_ZERO), and string inputs tolerate group separators, currency
 * symbols and whitespace. Previously all extra arguments were ignored and a formatted string threw.
 */
public class ToNumberTest extends BaseDatabaseTest {

    private double num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    @Test
    public void plainValue() {
        // Bare TO_NUMBER defaults to NUMBER(38,0): the value is rounded to a whole number (Snowflake).
        assertEquals(4.0, num("SELECT TO_NUMBER(3.9)"), 1e-9);
    }

    @Test
    public void precisionScaleRoundsToScale() {
        assertEquals(4.0, num("SELECT TO_NUMBER(3.9, 38, 0)"), 1e-9);
        assertEquals(3.15, num("SELECT TO_NUMBER(3.145, 10, 2)"), 1e-9);
    }

    @Test
    public void stripsGroupSeparators() {
        // The comma group separator is stripped; the bare call then rounds to NUMBER(38,0): 1,234.56 -> 1235.
        assertEquals(1235.0, num("SELECT TO_NUMBER('1,234.56')"), 1e-9);
    }

    @Test
    public void stripsCurrencyAndRoundsToScale() {
        assertEquals(1234.57, num("SELECT TO_NUMBER('$1,234.567', 10, 2)"), 1e-9);
    }

    @Test
    public void formatStringDoesNotBlockParsing() {
        assertEquals(1234.56, num("SELECT TO_NUMBER('1,234.56', '9,999.99')"), 1e-9);
    }

    @Test
    public void toDecimalAndToNumericAreSynonyms() {
        assertEquals(3.14, num("SELECT TO_DECIMAL('3.14', 10, 2)"), 1e-9);
        assertEquals(99.0, num("SELECT TO_NUMERIC('  99 ')"), 1e-9);
    }
}
