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
 * An ORDER BY key that cannot compute its value for a row - a conversion, a division by zero, a date or a JSON
 * text it cannot read - raises the error the same expression raises anywhere else, in a plain, DISTINCT,
 * grouped or set-operation query and in a window's ORDER BY; only a name nothing resolves is an invalid
 * identifier (live-verified).
 */
public class OrderByRowFaultTest extends MeasuredCellsTestSupport {

    @Test
    public void aKeyRaisesItsOwnFault() {
        try {
            assertAnswer(
                "Table S4 successfully created.",
                "CREATE OR REPLACE TABLE s4 (v VARCHAR, n NUMBER)");
            assertAnswer(
                "2",
                "INSERT INTO s4 VALUES ('abc', 0), ('1', 1)");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v::NUMBER FROM s4");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v FROM s4 ORDER BY v::NUMBER");
            assertAnswer(
                "REFUSED Division by zero",
                "SELECT v FROM s4 ORDER BY 1 / n");
            assertAnswer(
                "REFUSED Date 'abc' is not recognized",
                "SELECT v FROM s4 ORDER BY TO_DATE(v)");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v FROM s4 ORDER BY v::NUMBER DESC NULLS FIRST");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v, n FROM s4 ORDER BY n, v::NUMBER");
            assertAnswer(
                "1 ;; abc",
                "SELECT v FROM s4 ORDER BY TRY_TO_NUMBER(v)");
            assertAnswer(
                "1",
                "SELECT v FROM s4 WHERE n = 1 ORDER BY v::NUMBER");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH'",
                "SELECT v FROM s4 ORDER BY nosuch");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'NOSUCH'",
                "SELECT v FROM s4 ORDER BY nosuch::NUMBER");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 26 | invalid identifier 'S9.V'",
                "SELECT v FROM s4 ORDER BY s9.v");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v AS w FROM s4 ORDER BY w::NUMBER");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT DISTINCT v FROM s4 ORDER BY v::NUMBER");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v, COUNT(*) FROM s4 GROUP BY v ORDER BY v::NUMBER");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v FROM s4 UNION ALL SELECT v FROM s4 ORDER BY v::NUMBER");
            assertAnswer(
                "REFUSED Division by zero",
                "SELECT v FROM s4 ORDER BY 1 / (n - n)");
            assertAnswer(
                "REFUSED Error parsing JSON: unknown keyword \"abc\", pos 4",
                "SELECT v FROM s4 ORDER BY PARSE_JSON(v)");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v FROM s4 ORDER BY v::NUMBER LIMIT 1");
            assertAnswer(
                "REFUSED Numeric value 'abc' is not recognized",
                "SELECT v, ROW_NUMBER() OVER (ORDER BY v::NUMBER) FROM s4");
        } finally {
            answer(
                "DROP TABLE IF EXISTS s4");
        }
    }
}
