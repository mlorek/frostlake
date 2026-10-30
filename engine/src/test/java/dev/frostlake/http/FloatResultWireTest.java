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

package dev.frostlake.http;

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A FLOAT result cell crosses the JSON wire as its double with every digit, as the account's default Arrow results
 * carry it (live-verified): {@code 0.1 + 0.2} is 0.30000000000000004 and the largest double stays a number. The
 * account's JSON results round a FLOAT to at most fifteen significant digits, which reads {@code 0.1 + 0.2} as 0.3
 * and the largest double as Infinity; the wire does not follow them.
 */
public class FloatResultWireTest {

    private final DatabaseEngine engine = new DatabaseEngine();

    private List<Object> wireRow(final String sql) {
        return ResultSetData.from(engine.executeQuery(sql)).getRows().get(0);
    }

    @Test
    public void aFloatCrossesWithEveryDigit() {
        assertEquals(Arrays.<Object>asList(Double.valueOf(0.30000000000000004), Double.valueOf(0.30000000000000004),
            Double.valueOf(1.4142135623730951)),
            wireRow("SELECT 0.1::FLOAT + 0.2::FLOAT, 0.30000000000000004::FLOAT, SQRT(2)"));
        assertEquals(Arrays.<Object>asList(Double.valueOf(1.100000023841858), Double.valueOf(9.007199254740992E15),
            Double.valueOf(1.2345678901234567E9)),
            wireRow("SELECT 1.100000023841858::FLOAT, 9007199254740993::FLOAT, 1234567890.123456789::FLOAT"));
    }

    @Test
    public void theRangeEdgesCrossAsNumbers() {
        assertEquals(Arrays.<Object>asList(Double.valueOf(Double.MIN_NORMAL), Double.valueOf(Double.MAX_VALUE)),
            wireRow("SELECT 2.2250738585072014e-308::FLOAT, 1.7976931348623157e308::FLOAT"));
    }

    @Test
    public void edgesPassAsTheyAre() {
        assertEquals(Arrays.<Object>asList(Double.valueOf(Double.NaN), Double.valueOf(Double.POSITIVE_INFINITY),
            Double.valueOf(0.0), Double.valueOf(-0.0), Double.valueOf(1.0E-20), Double.valueOf(1.0E30)),
            wireRow("SELECT 'NaN'::FLOAT, 'inf'::FLOAT, 0::FLOAT, -0::FLOAT, 1e-20::FLOAT, 1.0E30::FLOAT"));
    }
}
