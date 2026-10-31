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
 * Functions written once over a shared base answer as one another: RPAD refuses a BOOLEAN length as LPAD does,
 * REGEXP_SUBSTR_ALL and REGEXP_EXTRACT_ALL refuse a BINARY as REGEXP_SUBSTR does, CHARINDEX answers NULL for a
 * NULL start under a collation as POSITION does, and the IGNORE_NULLS extremes keep the first of a signed-zero tie
 * and refuse a GEOGRAPHY as GREATEST and LEAST do (all live-verified).
 */
public class FunctionTwinAnswersTest extends MeasuredCellsTestSupport {

    @Test
    public void sharedAnswers() {
        engine.execute("CREATE OR REPLACE TABLE dg (id INT, g GEOGRAPHY)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'RPAD': (VARCHAR(1), BOOLEAN)",
            "SELECT RPAD('a', TRUE)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LPAD': (VARCHAR(1), BOOLEAN)",
            "SELECT LPAD('a', TRUE)");
        assertAnswer("axy|xya", "SELECT RPAD('a', 3, 'xy'), LPAD('a', 3, 'xy')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'REGEXP_SUBSTR_ALL': (BINARY(67108864), VARCHAR(1))",
            "SELECT REGEXP_SUBSTR_ALL(TO_BINARY('61', 'HEX'), 'a')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'REGEXP_EXTRACT_ALL': (BINARY(67108864), VARCHAR(1))",
            "SELECT REGEXP_EXTRACT_ALL(TO_BINARY('61', 'HEX'), 'a')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'REGEXP_SUBSTR': (BINARY(67108864), VARCHAR(1))",
            "SELECT REGEXP_SUBSTR(TO_BINARY('61', 'HEX'), 'a')");
        assertAnswer("null", "SELECT CHARINDEX('A', COLLATE('cab', 'en-ci'), NULL)");
        assertAnswer("null", "SELECT POSITION('A', COLLATE('cab', 'en-ci'), NULL)");
        assertAnswer("2", "SELECT CHARINDEX('A', COLLATE('cab', 'en-ci'), 1)");
        assertAnswer("-0.0", "SELECT GREATEST_IGNORE_NULLS(-0.0::FLOAT, 0.0::FLOAT)");
        assertAnswer("0.0", "SELECT GREATEST_IGNORE_NULLS(0.0::FLOAT, -0.0::FLOAT)");
        assertAnswer("0.0", "SELECT LEAST_IGNORE_NULLS(0.0::FLOAT, -0.0::FLOAT)");
        assertAnswer("-0.0", "SELECT LEAST_IGNORE_NULLS(-0.0::FLOAT, 0.0::FLOAT)");
        assertAnswer("-0.0", "SELECT GREATEST(-0.0::FLOAT, 0.0::FLOAT)");
        assertAnswer("-0.0", "SELECT GREATEST_IGNORE_NULLS(NULL, -0.0::FLOAT, 0.0::FLOAT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GREATEST_IGNORE_NULLS': (GEOGRAPHY, GEOGRAPHY)",
            "SELECT GREATEST_IGNORE_NULLS(g, g) FROM dg");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LEAST_IGNORE_NULLS': (GEOGRAPHY, GEOGRAPHY)",
            "SELECT LEAST_IGNORE_NULLS(g, g) FROM dg");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GREATEST': (GEOGRAPHY, GEOGRAPHY)",
            "SELECT GREATEST(g, g) FROM dg");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GREATEST_IGNORE_NULLS': (GEOGRAPHY, NULL)",
            "SELECT GREATEST_IGNORE_NULLS(g, NULL) FROM dg");
    }
}
