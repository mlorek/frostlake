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
 * GET reads a VARIANT at a number, a text, a BOOLEAN or a VARIANT; a FLOAT, a DECFLOAT, a date and a binary index
 * are refused by their type as the call compiles, the first argument named FILE, while an ARRAY and an OBJECT
 * take a FLOAT index (all live-verified).
 */
public class GetVariantIndexTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void variantIndexes() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GET': (FILE, FLOAT)",
            "SELECT GET(PARSE_JSON('[1,2,3]'), 1::FLOAT)");
        assertAnswer("3", "SELECT GET(PARSE_JSON('[1,2,3]'), 1.5::NUMBER(2,1))");
        assertAnswer("2", "SELECT GET(PARSE_JSON('[1,2,3]'), 1::NUMBER(38,0))");
        assertAnswer("null", "SELECT GET(PARSE_JSON('[1,2,3]'), TRUE)");
        assertAnswer("null", "SELECT GET(PARSE_JSON('[1,2,3]'), '1')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GET': (FILE, DATE)",
            "SELECT GET(PARSE_JSON('[1,2,3]'), '2024-01-01'::DATE)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GET': (FILE, DECFLOAT(38))",
            "SELECT GET(PARSE_JSON('[1,2,3]'), DECFLOAT '1')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GET': (FILE, FLOAT)",
            "SELECT GET(PARSE_JSON('{\"a\":1}'), 1::FLOAT)");
        assertAnswer("null", "SELECT GET([1,2,3], 1::FLOAT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GET': (FILE, FLOAT)",
            "SELECT GET(PARSE_JSON('[1,2,3]'), 1::DOUBLE)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GET': (FILE, FLOAT)",
            "SELECT GET(PARSE_JSON('[1,2,3]'), NULL::FLOAT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GET': (FILE, BINARY(67108864))",
            "SELECT GET(PARSE_JSON('[1,2,3]'), TO_BINARY('01', 'HEX'))");
        assertAnswer("null", "SELECT GET(OBJECT_CONSTRUCT('a', 1), 1::FLOAT)");
        assertAnswer("null", "SELECT GET(PARSE_JSON('[1,2,3]')::VARIANT, PARSE_JSON('1'))");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
