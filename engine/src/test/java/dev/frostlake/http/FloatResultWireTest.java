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
 * A FLOAT result cell crosses the JSON wire with the digits the account's JSON results carry: at most nine decimals
 * and fifteen significant digits from 1 up, ten significant digits below 1 (live-verified), while the engine's own
 * value is unchanged.
 */
public class FloatResultWireTest {

    private final DatabaseEngine engine = new DatabaseEngine();

    private List<Object> wireRow(final String sql) {
        return ResultSetData.from(engine.executeQuery(sql)).getRows().get(0);
    }

    @Test
    public void aFloatCrossesWithTheAccountsDigits() {
        assertEquals(Arrays.<Object>asList(Double.valueOf(1.100000024), Double.valueOf(1.1)),
            wireRow("SELECT 1.100000023841858::FLOAT, 1.1::FLOAT"));
        assertEquals(Arrays.<Object>asList(Double.valueOf(0.3), Double.valueOf(0.3333333333),
            Double.valueOf(0.6666666667)), wireRow("SELECT 0.1::FLOAT + 0.2::FLOAT, 1 / 3::FLOAT, 2::FLOAT / 3"));
        assertEquals(Arrays.<Object>asList(Double.valueOf(123456.7890625), Double.valueOf(1.23456789012346E9),
            Double.valueOf(9.00719925474099E15)),
            wireRow("SELECT 123456.7890625::FLOAT, 1234567890.123456789::FLOAT, 9007199254740993::FLOAT"));
        assertEquals(Arrays.<Object>asList(Double.valueOf(-1.23456789E-4), Double.valueOf(1.23456789E-5),
            Double.valueOf(3.141592654), Double.valueOf(1.23456789E7)),
            wireRow("SELECT -0.000123456789012::FLOAT, 1.23456789012345e-5::FLOAT, 3.141592653589793::FLOAT, "
                + "12345678.9::FLOAT"));
    }

    @Test
    public void edgesPassAsTheyAre() {
        assertEquals(Arrays.<Object>asList(Double.valueOf(Double.NaN), Double.valueOf(Double.POSITIVE_INFINITY),
            Double.valueOf(0.0), Double.valueOf(-0.0), Double.valueOf(1.0E-20), Double.valueOf(1.0E30)),
            wireRow("SELECT 'NaN'::FLOAT, 'inf'::FLOAT, 0::FLOAT, -0::FLOAT, 1e-20::FLOAT, 1.0E30::FLOAT"));
    }

    @Test
    public void theEnginesValueIsUnchanged() {
        assertEquals(Double.valueOf(1.100000023841858),
            engine.executeQuery("SELECT 1.100000023841858::FLOAT").getRows().get(0).getValue(0));
    }
}
