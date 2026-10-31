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
 * TO_VARCHAR and TO_CHAR take no format over a semi-structured value: a VARIANT, an OBJECT, an ARRAY and an XML
 * value with a second argument, a NULL one included, are refused for their count (all live-verified).
 */
public class ToVarcharStructuredArityTest extends MeasuredCellsTestSupport {

    @Test
    public void aFormatOverASemiStructuredValueIsRefused() {
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(PARSE_JSON('1'), 'X')] expected 1, got 2",
            "SELECT TO_VARCHAR(PARSE_JSON('1'), 'X')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(OBJECT_CONSTRUCT('a', 1), 'X')] expected 1, got 2",
            "SELECT TO_VARCHAR(OBJECT_CONSTRUCT('a', 1), 'X')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(ARRAY_CONSTRUCT(1), 'X')] expected 1, got 2",
            "SELECT TO_VARCHAR(ARRAY_CONSTRUCT(1), 'X')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(TO_VARIANT(1), '999')] expected 1, got 2",
            "SELECT TO_VARCHAR(TO_VARIANT(1), '999')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_CHAR(PARSE_XML('<r><a>1</a></r>'), 'X')] expected 1, got 2",
            "SELECT TO_CHAR(PARSE_XML('<r><a>1</a></r>'), 'X')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(PARSE_JSON('\"2024-01-01\"'), 'YYYY')] expected 1, got 2",
            "SELECT TO_VARCHAR(PARSE_JSON('\"2024-01-01\"'), 'YYYY')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[TO_VARCHAR(PARSE_XML('<r><a>1</a></r>'), NULL)] expected 1, got 2",
            "SELECT TO_VARCHAR(PARSE_XML('<r><a>1</a></r>'), NULL)");
    }
}
