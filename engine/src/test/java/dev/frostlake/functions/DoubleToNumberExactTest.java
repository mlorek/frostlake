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
 * A double becomes an exact NUMBER at its exact binary value, never at its shortest decimal spelling: a FLOAT's cast
 * and a VARIANT's, TO_NUMBER and TO_DECIMAL, and a write into a NUMBER column all hold 2.675 as 2.67 at scale 2 and
 * 1e38 as the 99999999999999997748809823456034029568 under 10^38 it is (all live-verified).
 */
public class DoubleToNumberExactTest extends MeasuredCellsTestSupport {

    @Test
    public void aDoubleConvertsAtItsExactBinaryValue() {
        assertAnswer("99999999999999997748809823456034029568", "SELECT PARSE_JSON('1e38')::INT");
        assertAnswer("99999999999999997748809823456034029568", "SELECT 1e38::FLOAT::NUMBER");
        assertShapedAnswer("0.<ID19>", "SELECT 0.1::FLOAT::NUMBER(20,19)");
        assertShapedAnswer("0.<ID19>", "SELECT PARSE_JSON('0.1e0')::NUMBER(20,19)");
        assertAnswer("1.100000000000000088817841970013", "SELECT 1.1::FLOAT::NUMBER(38,30)");
        assertShapedAnswer("0.<ID19>", "SELECT TO_NUMBER(0.1::FLOAT, 20, 19)");
        assertAnswer("2.67|1.00|3",
            "SELECT 2.675::FLOAT::NUMBER(10,2), 1.005::FLOAT::NUMBER(10,2), 2.5::FLOAT::NUMBER(10,0)");
        assertAnswer("2.67|2.68", "SELECT PARSE_JSON('2.675e0')::NUMBER(10,2), PARSE_JSON('2.675')::NUMBER(10,2)");
        assertAnswer("2.67|99999999999999997748809823456034029568",
            "SELECT TO_DECIMAL(2.675::FLOAT, 10, 2), TO_NUMBER(1e38::FLOAT)");
        assertAnswer("99999999999999997748809823456034029568", "SELECT 9.999999999999999e37::FLOAT::NUMBER");
        assertAnswer("REFUSED SQL compilation error: Error line 1 at position 43 | Integer literal is out of "
                + "representable range: 100000000000000000000000000000000000000",
            "SELECT CAST(1e38::FLOAT AS NUMBER(38,0)) = 100000000000000000000000000000000000000");
        assertAnswer("Table FZ successfully created.", "CREATE TABLE fz (n NUMBER(10,2))");
        assertAnswer("1", "INSERT INTO fz SELECT 2.675::FLOAT");
        assertAnswer("1", "INSERT INTO fz VALUES (2.675::FLOAT)");
        assertAnswer("2.67 ;; 2.67", "SELECT n FROM fz ORDER BY 1");
    }
}
