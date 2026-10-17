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
 * A text function reads a number by the text the number converts to (live-verified): a decimal below a millionth by
 * its digits in place - UPPER(0.0000001) is 0.0000001, LENGTH 9 - a scaled number with its scale, and a FLOAT as
 * FLOAT::VARCHAR spells it, 1e+20 and 1.5e-07; LIKE matches the same text.
 */
public class TextFunctionNumberTextTest extends MeasuredCellsTestSupport {

    @Test
    public void aDecimalIsReadByItsDigitsInPlace() {
        assertCells(new String[][] {
            {"SELECT UPPER(0.0000001), LENGTH(0.0000001), 0.0000001 LIKE '%E%', CONCAT(0.0000001, 'x')",
                "0.0000001|9|false|0.0000001x"},
            {"SELECT LOWER(0.0000001), REVERSE(0.0000001), SUBSTR(0.0000001, 1, 4), TRIM(0.0000001)",
                "0.0000001|1000000.0|0.00|0.0000001"},
            {"SELECT LPAD(0.0000001, 12, '*'), REPLACE(0.0000001, '0', 'o'), SPLIT_PART(0.0000001, '.', 2)",
                "***0.0000001|o.oooooo1|0000001"},
            {"SELECT POSITION('1' IN 0.0000001), CONTAINS(0.0000001, 'E'), STARTSWITH(0.0000001, '0.')",
                "9|false|true"},
            {"SELECT UPPER(1e20::FLOAT), LENGTH(1e20::FLOAT), UPPER(1.5::FLOAT), UPPER(0.1::FLOAT)", "1E+20|5|1.5|0.1"},
            {"SELECT UPPER(123456789012345678901234567890), LENGTH(-0.5), UPPER(100::NUMBER(10,2))",
                "123456789012345678901234567890|4|100.00"},
            {"SELECT UPPER('2024-01-15'::DATE), LENGTH('2024-01-15'::DATE)", "2024-01-15|10"},
            {"SELECT LENGTH(UPPER('2024-01-15 10:11:12'::TIMESTAMP_NTZ)), UPPER('10:11:12'::TIME)", "23|10:11:12"},
            {"SELECT UPPER(TRUE), LENGTH(FALSE), INITCAP(0.0000001), LEFT(0.0000001, 3), RIGHT(0.0000001, 3)",
                "TRUE|5|0.0000001|0.0|001"},
            {"SELECT 0.0000001::VARIANT::VARCHAR, TO_VARIANT(0.0000001)::VARCHAR, PARSE_JSON('0.0000001')::VARCHAR",
                "0.0000001|0.0000001|0.0000001"},
            {"SELECT TO_JSON(0.0000001::VARIANT), TO_JSON(TO_VARIANT(0.0000001)), TYPEOF(0.0000001::VARIANT)",
                "0.0000001|0.0000001|DECIMAL"},
            {"SELECT ARRAY_CONSTRUCT(0.0000001::VARIANT)::VARCHAR, OBJECT_CONSTRUCT('k', 0.0000001::VARIANT)::VARCHAR",
                "[0.0000001]|{\"k\":0.0000001}"},
        });
    }

    @Test
    public void aFloatIsReadAsItsVarcharText() {
        assertCells(new String[][] {
            {"SELECT UPPER(1.5e-7::FLOAT), UPPER(123456789.123::FLOAT), UPPER(-0.0::FLOAT), UPPER('NaN'::FLOAT)",
                "1.5E-07|123456789.123|-0|NAN"},
            {"SELECT UPPER(1e15::FLOAT), UPPER(1e16::FLOAT), UPPER(0.0001::FLOAT), UPPER(0.00001::FLOAT)",
                "1E+15|1E+16|0.0001|1E-05"},
            {"SELECT REVERSE(1e20::FLOAT), CONCAT(1e20::FLOAT, ''), 1e20::FLOAT || '', LENGTH(1e20::FLOAT::VARCHAR)",
                "02+e1|1e+20|1e+20|5"},
            {"SELECT UPPER(0.0000001::NUMBER(10,8)), UPPER(TO_VARIANT(0.0000001)), UPPER(PARSE_JSON('1e-7'))",
                "0.00000010|0.0000001|1E-07"},
        });
    }
}
