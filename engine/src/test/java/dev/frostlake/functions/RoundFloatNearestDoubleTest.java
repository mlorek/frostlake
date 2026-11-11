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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * ROUND of a FLOAT to a number of decimals answers the double nearest the rounded decimal, a tie away from zero
 * (live-verified): {@code ROUND(6.180339887498949::FLOAT, 9)} equals {@code 6.180339887::FLOAT},
 * {@code ROUND(-2.5::FLOAT, 0)} is -3, {@code ROUND(-1250::FLOAT, -2)} -1300 and {@code ROUND(1E20::FLOAT, 2)} 1E20.
 */
public class RoundFloatNearestDoubleTest extends MeasuredCellsTestSupport {

    @Test
    public void theNearestDouble() {
        assertCells(new String[][] {
            {"SELECT ROUND(1.23456789012345678::FLOAT, 15) = 1.234567890123457::FLOAT, "
                + "ROUND(1.23456789012345678::FLOAT, 15) = 1.23456789::FLOAT",
                "true|false"},
            {"SELECT ROUND(1.23456789012345678::FLOAT, 10) = 1.2345678901::FLOAT, "
                + "ROUND(1.23456789012345678::FLOAT, 12) = 1.234567890123::FLOAT",
                "true|true"},
            {"SELECT ROUND(1.23456789012345678::FLOAT, 16) = 1.2345678901234568::FLOAT, "
                + "ROUND(1.23456789012345678::FLOAT, 17) = 1.23456789012345678::FLOAT",
                "true|true"},
            {"SELECT TO_VARCHAR(ROUND(1.23456789012345678::FLOAT, 15)), TO_VARCHAR(1.23456789012345678::FLOAT)",
                "1.23456789|1.23456789"},
            {"SELECT ROUND(0.1::FLOAT + 0.2::FLOAT, 15) = 0.3::FLOAT, ROUND(0.1::FLOAT + 0.2::FLOAT, 16) = 0.3::FLOAT",
                "true|false"},
            {"SELECT ROUND(1E20::FLOAT, 2) = 1E20::FLOAT, ROUND(-0.04::FLOAT, 1), ROUND(-0.4::FLOAT, 0)",
                "true|0.0|0.0"},
            {"SELECT ROUND(5.5::FLOAT), ROUND(-5.5::FLOAT), ROUND(-0.5::FLOAT), ROUND(0.49999999999999994::FLOAT)",
                "6.0|-6.0|-1.0|0.0"},
            {"SELECT ROUND(1.25::FLOAT, 32768), ROUND(123.456::FLOAT, -400), ROUND(0::FLOAT, 30)", "NaN|NaN|0.0"},
            {"SELECT ROUND(2.5::FLOAT, 0) = 3, ROUND(-2.5::FLOAT, 0) = -3, ROUND(-1250::FLOAT, -2) = -1300",
                "true|true|true"},
            {"SELECT ROUND(UNIFORM(0::FLOAT, 1::FLOAT, 1), 9) = 0.618033989::FLOAT", "true"},
        });
    }

    @Test
    public void tiesAndScales() {
        assertCells(new String[][] {
            {"SELECT ROUND(6.180339887498949::FLOAT, 9) = 6.180339887::FLOAT", "true"},
            {"SELECT ROUND(2.360679774997897::FLOAT, 9) = 2.360679775::FLOAT", "true"},
            {"SELECT ROUND(-1.143416214223267::FLOAT, 9) = -1.143416214::FLOAT", "true"},
            {"SELECT ROUND(UNIFORM(0::FLOAT, 1::FLOAT, 1), 9)", "0.618033989"},
            {"SELECT LISTAGG(TO_VARCHAR(ROUND(NORMAL(0, 1, SEQ4()), 9)), ',') WITHIN GROUP (ORDER BY SEQ4()) "
                + "FROM TABLE(GENERATOR(ROWCOUNT => 40))",
                "0.22497048,-1.143416214,-0.784699588,-0.365971322,-2.386766595,0.134259682,0.981516045,1.141060328"
                + ",-1.502089338,0.58887835,0.474907473,-0.432066078,-1.500052037,-0.084003887,-0.776391154,0.2784234"
                + "12,0.28899281,1.753968909,0.059611969,0.243995141,-0.156017464,0.137523859,-1.359333615,-0.6760033"
                + "23,-0.311629911,0.574972761,0.165448445,1.134325621,0.966802199,-2.434263147,0.142924419,1.6717082"
                + "14,-0.640929895,-1.549713864,0.560812521,-0.313420415,0.483040724,0.391905038,1.548186782,-0.25917"
                + "3066"},
            {"SELECT ROUND(1.005::FLOAT, 2), ROUND(2.675::FLOAT, 2), ROUND(2.5::FLOAT, 0), ROUND(-2.5::FLOAT, 0)",
                "1.0|2.68|3.0|-3.0"},
            {"SELECT ROUND(1234.5678::FLOAT, -2), ROUND(1250::FLOAT, -2), ROUND(-1250::FLOAT, -2), "
                + "ROUND(0.1::FLOAT + 0.2::FLOAT, 15)",
                "1200.0|1300.0|-1300.0|0.3"},
            {"SELECT ROUND(0.285::FLOAT, 2), ROUND(1.115::FLOAT, 2), ROUND(8.345::FLOAT, 2), ROUND(0.5::FLOAT, "
                + "0), ROUND(1.5::FLOAT, 0)",
                "0.28|1.12|8.35|1.0|2.0"},
            {"SELECT ROUND(3.14159265358979::FLOAT, 4), ROUND(-3.14159265358979::FLOAT, 4), ROUND(1e20::FLOAT, "
                + "2), ROUND(1.7976931348623157e308::FLOAT, 2)",
                "3.1416|-3.1416|1.0E20|Infinity"},
        });
    }
}
