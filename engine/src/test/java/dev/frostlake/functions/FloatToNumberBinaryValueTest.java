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
 * A FLOAT converted to a NUMBER, by TO_NUMBER, TO_DECIMAL or a cast, takes the double's binary value: 0.1 at
 * scale 20 is 0.10000000000000000555 and 2.675 at scale 2 is 2.67 (all live-verified).
 */
public class FloatToNumberBinaryValueTest extends MeasuredCellsTestSupport {

    @Test
    public void aFloatConvertsByItsBinaryValue() {
        engine.execute("CREATE OR REPLACE TABLE fxt (n NUMBER(38,20), m NUMBER(10,2))");
        assertAnswer("0.10000000000000000555", "SELECT TO_NUMBER(0.1::FLOAT, 38, 20)");
        assertAnswer("0.10000000000000000555", "SELECT 0.1::FLOAT::NUMBER(38,20)");
        assertAnswer("2.67", "SELECT TO_NUMBER(2.675::FLOAT, 10, 2)");
        assertAnswer("2.67", "SELECT CAST(2.675::FLOAT AS NUMBER(10,2))");
        assertAnswer("1.00", "SELECT TO_DECIMAL(1.005::FLOAT, 10, 2)");
        assertAnswer("1", "INSERT INTO fxt SELECT 0.1::FLOAT, 2.675::FLOAT");
        answer("SELECT n, m FROM fxt");
        assertAnswer("100000000000000000000", "SELECT TO_NUMBER(1e20::FLOAT)");
        assertAnswer("12345678901234566097272832", "SELECT TO_NUMBER(1.2345678901234567e25::FLOAT)");
    }
}
