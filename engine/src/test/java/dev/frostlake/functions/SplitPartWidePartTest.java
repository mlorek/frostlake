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
 * SPLIT_PART's whole part past 32 bits: refused while the statement compiles where the plan knows its value - a
 * literal, an expression of literals, the one value a column's statistics leave it - echoed as the plan prints it;
 * refused as the row is read in its own type where the plan cannot know it, and a text spelling such a number refused
 * as out of range; a NULL text or separator answers NULL first (all live-verified).
 */
public class SplitPartWidePartTest extends MeasuredCellsTestSupport {

    @Test
    public void wideParts() {
        engine.execute("CREATE OR REPLACE TABLE tb1 (i NUMBER(38,0))");
        engine.execute("INSERT INTO tb1 VALUES (3000000000)");
        engine.execute("CREATE OR REPLACE TABLE tb2 (i NUMBER(38,0))");
        engine.execute("INSERT INTO tb2 VALUES (3000000000), (1)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '10000000000000000000000000'",
            "SELECT SPLIT_PART('a,b', ',', 10000000000000000000000000)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '3000000000'",
            "SELECT SPLIT_PART('a,b', ',', 3000000000)");
        assertAnswer("", "SELECT SPLIT_PART('a,b', ',', 2147483647)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '2147483648'",
            "SELECT SPLIT_PART('a,b', ',', 2147483648)");
        assertAnswer("", "SELECT SPLIT_PART('a,b', ',', -2147483648)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '-2147483649'",
            "SELECT SPLIT_PART('a,b', ',', -2147483649)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '-3000000000'",
            "SELECT SPLIT_PART('a,b', ',', -3000000000)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '3000000000'",
            "SELECT SPLIT_PART('a,b', ',', 3000000000::NUMBER(10,0))");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '3000000000 + 0'",
            "SELECT SPLIT_PART('a,b', ',', 3000000000 + 0)");
        assertAnswer("", "SELECT SPLIT_PART('a,b', ',', 2147483648 - 1)");
        assertAnswer("REFUSED Numeric value '3000000000' is out of range",
            "SELECT SPLIT_PART('a,b', ',', '3000000000')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){nullable}, value 3e+09",
            "SELECT SPLIT_PART('a,b', ',', 3000000000::FLOAT)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '2147483648'",
            "SELECT SPLIT_PART('a,b', ',', 2147483648.0)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: 'CAST(10000000000 AS NUMBER(38,0))'",
            "SELECT SPLIT_PART('a,b', ',', 1e10::NUMBER)");
        assertAnswer("null", "SELECT SPLIT_PART('a,b', ',', NULL::NUMBER(38,0))");
        assertAnswer("a", "SELECT SPLIT_PART('a,b', ',', 0)");
        assertAnswer("b", "SELECT SPLIT_PART('a,b', ',', -1)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '2147483647 + 1'",
            "SELECT SPLIT_PART('a,b', ',', 2147483647 + 1)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: 'ABS(-3000000000)'",
            "SELECT SPLIT_PART('a,b', ',', ABS(-3000000000))");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '\"values\".N'",
            "SELECT SPLIT_PART('a,b', ',', n) FROM (SELECT 3000000000 n)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: 'TB1.I'",
            "SELECT SPLIT_PART('a,b', ',', i) FROM tb1");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](38,0){nullable}, value 3000000000",
            "SELECT SPLIT_PART('a,b', ',', i) FROM tb2");
        assertAnswer("", "SELECT SPLIT_PART('a,b', ',', i) FROM tb1 WHERE i < 0");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '\"values\".N'",
            "SELECT SPLIT_PART('a,b', ',', n) FROM (SELECT 2147483648 n)");
        assertAnswer("b", "SELECT SPLIT_PART('a,b', ',', n) FROM (SELECT 2 n)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: 'SCALED_ROUND_INT_DIVIDE(TB1.I, 1)'",
            "SELECT SPLIT_PART('a,b', ',', i / 1) FROM tb1");
        assertAnswer("a", "SELECT SPLIT_PART('a,b', ',', i - 2999999999) FROM tb1");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '3000000000'",
            "SELECT SPLIT_PART('a,b', ',', 3000000000) FROM tb2");
        assertAnswer("null", "SELECT SPLIT_PART(NULL, ',', 3000000000)");
        assertAnswer("null", "SELECT SPLIT_PART('a,b', NULL, 3000000000)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SPLIT_PART('a,b', ',', 3000000000, 1)] expected 3, got 4",
            "SELECT SPLIT_PART('a,b', ',', 3000000000, 1)");
        assertAnswer("REFUSED SQL compilation error: | argument 3 to function SPLIT_PART needs to be an integer, "
                + "found: '3000000000'",
            "SELECT SPLIT_PART('a,b', 1, 3000000000)");
    }
}
