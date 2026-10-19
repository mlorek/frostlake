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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A SQL function whose body is a query that reads a relation is typed with its parameters bound as the call binds
 * them (live-verified): a parameter wins over a same-named column of the body's FROM - SUM(value) over a FLATTEN
 * reads the parameter, not FLATTEN's VALUE - and takes its argument's type where the argument needs no conversion,
 * so c_lat2(10) is NUMBER(14,0) as the SUM of a NUMBER(2,0) is.
 */
public class SqlFunctionQueryBodyTypingTest extends MeasuredCellsTestSupport {

    @Test
    public void parametersShadowTheBodysColumns() {
        engine.execute("CREATE OR REPLACE FUNCTION c_lat2(value NUMBER) RETURNS NUMBER AS 'SELECT SUM(value) FROM "
            + "TABLE(FLATTEN(INPUT => PARSE_JSON(''[1,2]'')))'");
        engine.execute("CREATE OR REPLACE FUNCTION c_v(value VARCHAR) RETURNS VARCHAR AS 'SELECT MAX(value) FROM "
            + "TABLE(FLATTEN(INPUT => PARSE_JSON(''[\"a\",\"bb\"]'')))'");
        engine.execute("CREATE OR REPLACE TABLE people (id INT)");
        engine.execute("INSERT INTO people VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE FUNCTION c_max(id NUMBER) RETURNS NUMBER AS 'SELECT MAX(id) FROM people'");
        engine.execute("CREATE OR REPLACE FUNCTION c_avg(id NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'SELECT AVG(id) "
            + "FROM people'");
        assertCells(new String[][] {
            // A qualified f.value reads FLATTEN's VALUE, whose SUM is a FLOAT the RETURNS clause does not take.
            {"CREATE OR REPLACE FUNCTION c_q(value NUMBER) RETURNS NUMBER AS 'SELECT SUM(f.value) FROM "
                + "TABLE(FLATTEN(INPUT => PARSE_JSON(''[1,2]''))) f'",
                "REFUSED Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'FLOAT'"},
            {"SELECT c_lat2(10), SYSTEM$TYPEOF(c_lat2(10))", "20|NUMBER(14,0)[SB8]"},
            {"SELECT c_lat2(10.5), SYSTEM$TYPEOF(c_lat2(10.5))", "21.0|NUMBER(15,1)[SB8]"},
            {"SELECT c_v('abc'), SYSTEM$TYPEOF(c_v('abc'))", "abc|VARCHAR(3)[LOB]"},
            {"SELECT c_max(10), SYSTEM$TYPEOF(c_max(10))", "10|NUMBER(2,0)[SB1]"},
            {"SELECT c_avg(10), SYSTEM$TYPEOF(c_avg(10))", "10.00000000|NUMBER(28,8)[SB16]"},
            {"SELECT c_avg(1.255), SYSTEM$TYPEOF(c_avg(1.255))", "1.255000000|NUMBER(22,9)[SB16]"},
        });
    }
}
