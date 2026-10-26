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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL function whose body is a query reads each parameter as its argument binds it, the parameter winning
 * over a same-named column of the body: a constant argument types the call's interval, and a column argument
 * a parameter read inside an aggregate is refused as a subquery the function object cannot evaluate, where
 * a parameter read in a WHERE or not read at all answers (all live-verified).
 */
public class QueryBodiedFunctionArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void constantsAndColumnArguments() {
        engine.execute("CREATE OR REPLACE FUNCTION c_lat2(value NUMBER) RETURNS NUMBER AS 'SELECT SUM(value) FROM "
            + "TABLE(FLATTEN(INPUT => PARSE_JSON(''[1,2]'')))'");
        engine.execute("CREATE OR REPLACE TABLE people (id INT, name VARCHAR)");
        engine.execute("INSERT INTO people VALUES (1, 'a'), (2, 'bb'), (3, 'ccc')");
        engine.execute("CREATE OR REPLACE FUNCTION c_max(id NUMBER) RETURNS NUMBER AS 'SELECT MAX(id) FROM people'");
        engine.execute("CREATE OR REPLACE FUNCTION c_n(x NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people'");
        engine.execute("CREATE OR REPLACE FUNCTION c_w(x NUMBER) RETURNS NUMBER AS 'SELECT COUNT(*) FROM people "
            + "WHERE id > x'");
        engine.execute("CREATE OR REPLACE FUNCTION c_v(v VARCHAR) RETURNS VARCHAR AS 'SELECT MAX(v) FROM "
            + "TABLE(FLATTEN(INPUT => PARSE_JSON(''[1,2]'')))'");
        engine.execute("CREATE OR REPLACE FUNCTION f_e(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        assertAnswer("2|NUMBER(38,0)[SB8]", "SELECT c_lat2(1.25::FLOAT), SYSTEM$TYPEOF(c_lat2(1.25::FLOAT))");
        assertAnswer("20|NUMBER(14,0)[SB8]", "SELECT c_lat2(10), SYSTEM$TYPEOF(c_lat2(10))");
        assertAnswer("-14|NUMBER(13,0)[SB8]", "SELECT c_lat2(-7), SYSTEM$TYPEOF(c_lat2(-7))");
        assertAnswer("12345|NUMBER(5,0)[SB2]", "SELECT c_max(12345), SYSTEM$TYPEOF(c_max(12345))");
        assertAnswer("-12345|NUMBER(5,0)[SB2]", "SELECT c_max(-12345), SYSTEM$TYPEOF(c_max(-12345))");
        assertAnswer("123456789|NUMBER(9,0)[SB4]", "SELECT c_max(123456789), SYSTEM$TYPEOF(c_max(123456789))");
        assertAnswer("10|NUMBER(2,0)[SB1]", "SELECT c_max(10), SYSTEM$TYPEOF(c_max(10))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: C_LAT2",
            "SELECT c_lat2(id), SYSTEM$TYPEOF(c_lat2(id)) FROM people ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: C_LAT2",
            "SELECT c_lat2(id + 1) FROM people ORDER BY 1");
        assertAnswer("2 ;; 2 ;; 2", "SELECT c_lat2(1) FROM people ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: C_MAX",
            "SELECT c_max(id) FROM people ORDER BY 1");
        assertAnswer("3 ;; 3 ;; 3", "SELECT c_n(id) FROM people ORDER BY 1");
        assertAnswer("0 ;; 1 ;; 2", "SELECT c_w(id) FROM people ORDER BY 1");
        assertAnswer("1", "SELECT c_w(2)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: C_V",
            "SELECT c_v(name) FROM people ORDER BY 1");
        assertAnswer("2 ;; 3 ;; 4", "SELECT f_e(id) FROM people ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated inside Function "
                + "object: C_LAT2",
            "SELECT id, c_lat2(id) FROM people WHERE id = 1");
    }
}
