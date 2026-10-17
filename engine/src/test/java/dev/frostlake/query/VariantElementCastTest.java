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

package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * An element or a path read out of a semi-structured value is a VARIANT, and a cast reads it as one (live-verified):
 * {@code [1,2][0]::DATE}, {@code GET([1,2], 0)::DATE}, {@code {'n': 1}:n::DATE} and {@code GET({'n': 1}, 'n')::DATE}
 * are "Failed to cast variant value 1 to DATE", as {@code PARSE_JSON('1')::DATE} is, and a number is no TIME, BOOLEAN
 * or BINARY either; a text element converts as a VARIANT text does, and a number reaches a number, a text and a
 * timestamp.
 */
public class VariantElementCastTest extends MeasuredCellsTestSupport {

    @Test
    public void anElementIsCastAsTheVariantItIs() {
        engine.execute("CREATE OR REPLACE TABLE vt (a ARRAY, o OBJECT, v VARIANT)");
        engine.execute("INSERT INTO vt SELECT [1, '2020-01-02', TRUE, 1.5, '12:00:00'], {'n': 1, 'd': '2020-01-02', "
            + "'b': TRUE}, PARSE_JSON('[1]')");
        assertCells(new String[][] {
            {"SELECT TO_ARRAY([1,2,3]::VECTOR(INT,3))[0]::DATE", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT PARSE_JSON('1')::DATE", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT [1,2][0]::DATE", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT GET([1,2], 0)::DATE", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT TO_VARIANT(1)::DATE", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT ['2020-01-02'][0]::DATE", "2020-01-02"},
            {"SELECT {'n': 1}:n::DATE", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT GET({'n': 1}, 'n')::DATE", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT [1][0]::TIME", "REFUSED Failed to cast variant value 1 to TIME"},
            {"SELECT [1][0]::BOOLEAN", "REFUSED Failed to cast variant value 1 to BOOLEAN"},
            {"SELECT [0][0]::BOOLEAN", "REFUSED Failed to cast variant value 0 to BOOLEAN"},
            {"SELECT [1][0]::BINARY", "REFUSED Failed to cast variant value 1 to BINARY"},
            {"SELECT ['ab'][0]::BINARY", "AB"},
            {"SELECT [1][0]::VARCHAR", "1"},
            {"SELECT [1.5][0]::INT", "2"},
            {"SELECT ['1'][0]::INT", "1"},
            {"SELECT [TRUE][0]::INT", "1"},
            {"SELECT [TRUE][0]::VARCHAR", "true"},
            {"SELECT ['x'][0]::BOOLEAN", "REFUSED Failed to cast variant value \"x\" to BOOLEAN"},
            {"SELECT ['true'][0]::BOOLEAN", "true"},
            {"SELECT a[1]::DATE FROM vt", "2020-01-02"},
            {"SELECT a[3]::INT FROM vt", "2"},
            {"SELECT a[4]::TIME FROM vt", "12:00"},
            {"SELECT o:d::DATE FROM vt", "2020-01-02"},
            {"SELECT TRY_CAST([1][0] AS DATE)",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types VARIANT "
                + "and DATE"},
            {"SELECT [1][0]::DATE FROM vt WHERE 1 = 0", ""},
            {"SELECT TO_DATE([1][0])", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT CAST(GET([1,2], 0) AS DATE)", "REFUSED Failed to cast variant value 1 to DATE"},
            {"SELECT [1, 2][0]::NUMBER(10,2)", "1.00"},
            {"SELECT ['2020-01-02 10:00:00'][0]::TIMESTAMP_NTZ", "2020-01-02T10:00"},
            {"SELECT [NULL][0]::DATE", "null"},
            {"SELECT [][0]::DATE", "null"},
        });
    }
}
