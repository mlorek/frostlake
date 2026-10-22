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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A cast to a NUMBER-keyed MAP converts each key to its number: keys that convert to one number are one entry,
 * the value of the source key that sorts last as text, a key that converts to no number is refused as that text's
 * cast is, and a key type with a scale is no MAP key at all (all live-verified).
 */
public class MapKeyCastTest extends MeasuredCellsTestSupport {

    @Test
    public void aKeyIsConvertedAsItsTextsCastConvertsIt() {
        assertAnswer("{\"2\":\"c\"}", "SELECT {'2':'b', '02':'a', '2.0':'c'}::MAP(INT,VARCHAR)");
        assertAnswer("{\"a\":2,\"b\":2}", "SELECT {'b':'2', 'a':'02'}::MAP(VARCHAR,INT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion", "SELECT {'01':1, '1':'x'}::MAP(INT,INT)");
        assertAnswer("REFUSED Typed object schema mismatch in conversion", "SELECT {'1':'x', '01':1}::MAP(INT,INT)");
        assertAnswer("REFUSED Numeric value 'x1' is not recognized", "SELECT {'x1':'a'}::MAP(INT,VARCHAR)");
        assertAnswer("REFUSED Numeric value '' is not recognized", "SELECT {'':'a'}::MAP(INT,VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type NUMBER(5,2)'.",
            "SELECT {'1x':'a'}::MAP(NUMBER(5,2),VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type NUMBER(5,2)'.",
            "SELECT {'1.234':'a'}::MAP(NUMBER(5,2),VARCHAR), MAP_KEYS({'1.234':'a'}::MAP(NUMBER(5,2),VARCHAR))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'MAP with key type NUMBER(5,2)'.",
            "SELECT {'1.235':'a', '1.234':'b'}::MAP(NUMBER(5,2),VARCHAR)");
        assertAnswer("REFUSED Numeric value '1000' is out of range", "SELECT {'1000':'a'}::MAP(NUMBER(3,0),VARCHAR)");
    }
}
