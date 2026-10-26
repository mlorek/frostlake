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

package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A cast to LONG written with :: and the token after the word: told unless a whole select item may end there
 * (all live-verified).
 */
public class LongCastReadOnLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void longCastReadOn() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 15 unexpected 'AS'.",
            "SELECT 1::LONG AS b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'.",
            "SELECT 1::LONG, 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'.",
            "SELECT 1::LONG FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'LONG'.",
            "SELECT (1::LONG)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 15 unexpected '||'.",
            "SELECT 1::LONG || 'a'");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 15 unexpected '+'.",
            "SELECT 1::LONG + 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 15 unexpected 'x'.",
            "SELECT 1::LONG x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'.",
            "SELECT 1::LONG WHERE TRUE");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'.",
            "SELECT a::LONG FROM t1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 14 unexpected '::'.",
            "SELECT 1::LONG::INT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 14 unexpected '('.",
            "SELECT 1::LONG(10)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 15 unexpected 'AS'.",
            "SELECT 1::LONG AS b, 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'LONG'.",
            "SELECT CAST(1 AS LONG) AS b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 15 unexpected '='.",
            "SELECT 1::LONG = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'.",
            "SELECT 1::LONG ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 14 unexpected ')'.",
            "SELECT 1::LONG)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'LONGS'.", "SELECT 1::LONGS AS b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'LONG'. | syntax "
                + "error line 1 at position 15 unexpected 'IS'.",
            "SELECT 1::LONG IS NULL");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'long'. | syntax "
                + "error line 1 at position 15 unexpected 'AS'.",
            "SELECT 1::long AS b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'LONG'. | syntax "
                + "error line 1 at position 17 unexpected 'AS'.",
            "SELECT 1 :: LONG AS b");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
