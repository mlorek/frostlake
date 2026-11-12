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
 * GET reads a VARIANT at a number, a text, a BOOLEAN, a FLOAT or a VARIANT, a FLOAT reading nothing, as over an ARRAY
 * and an OBJECT. A date, a time, a timestamp or a DECFLOAT index reads as its text, so it names an OBJECT's key and no
 * ARRAY element; a binary index is refused by its type as the call compiles (all live-verified).
 */
public class GetVariantIndexTypeTest extends MeasuredCellsTestSupport {

    private static final String BINARY_REFUSED = "REFUSED SQL compilation error: error line 1 at position 7 | Invalid "
        + "argument types for function 'GET': (VARIANT, ";

    @Test
    public void variantIndexes() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertCells(new String[][] {
            {"SELECT GET(PARSE_JSON('[1,2,3]'), 1::FLOAT)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), 1.5::NUMBER(2,1))", "3"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), 1::NUMBER(38,0))", "2"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), TRUE)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), '1')", "null"},
            {"SELECT GET(PARSE_JSON('{\"a\":1}'), 1::FLOAT)", "null"},
            {"SELECT GET([1,2,3], 1::FLOAT)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), 1::DOUBLE)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), NULL::FLOAT)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), TO_BINARY('01', 'HEX'))", BINARY_REFUSED + "BINARY(67108864))"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), X'01')", BINARY_REFUSED + "BINARY(1))"},
            {"SELECT GET(OBJECT_CONSTRUCT('a', 1), 1::FLOAT)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]')::VARIANT, PARSE_JSON('1'))", "null"},
            {"SELECT GET(OBJECT_CONSTRUCT('TRUE', 'x', 'true', 'y'), TRUE)", "y"}});
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void aTemporalOrADecfloatIndexReadsAsItsText() {
        assertCells(new String[][] {
            {"SELECT GET(PARSE_JSON('[1,2,3]'), '2024-01-01'::DATE)", "null"},
            {"SELECT GET(PARSE_JSON('{\"2024-01-01\":1}'), '2024-01-01'::DATE)", "1"},
            {"SELECT GET(OBJECT_CONSTRUCT('2024-01-01', 'x'), '2024-01-01'::DATE)", "x"},
            {"SELECT GET(OBJECT_CONSTRUCT('10:00:00', 'x'), '10:00'::TIME)", "x"},
            {"SELECT GET(OBJECT_CONSTRUCT('2024-01-01 00:00:00.000', 'x'), '2024-01-01'::TIMESTAMP_NTZ)", "x"},
            {"SELECT GET(OBJECT_CONSTRUCT('2024-01-01 00:00:00', 'x'), '2024-01-01'::TIMESTAMP_NTZ)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), '2024-01-01'::TIMESTAMP_NTZ)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), '10:00'::TIME)", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), DECFLOAT '1')", "null"},
            {"SELECT GET(PARSE_JSON('[1,2,3]'), 1::DECFLOAT)", "null"},
            {"SELECT GET(OBJECT_CONSTRUCT('1', 'x'), DECFLOAT '1')", "x"},
            {"SELECT GET(OBJECT_CONSTRUCT('1.5', 'x'), DECFLOAT '1.5')", "x"},
            {"SELECT GET(OBJECT_CONSTRUCT('1', 'x'), 1)", "null"}});
    }
}
