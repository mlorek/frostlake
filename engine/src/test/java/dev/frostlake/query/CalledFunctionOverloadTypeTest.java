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
 * A SQL function called with as many arguments as several of its overloads take is settled by the arguments' declared
 * types while the statement compiles, and the overload it reaches has its body judged then: {@code f_o(1)}, {@code
 * f_o(a)} over an INT column and {@code f_o(NULL)} reach {@code f_o(p INT)}, whose table is gone, and are refused over
 * an empty table and behind {@code WHERE FALSE}; a text reaches {@code f_o(p VARCHAR)} (live-verified).
 */
public class CalledFunctionOverloadTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void theArgumentTypesSettleTheOverload() {
        engine.execute("CREATE OR REPLACE TABLE ue (c INT)");
        engine.execute("CREATE OR REPLACE FUNCTION f_o(p INT) RETURNS INT AS 'SELECT COUNT(*) FROM ue'");
        engine.execute("CREATE OR REPLACE FUNCTION f_o(p VARCHAR) RETURNS INT AS 'SELECT 1'");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, s VARCHAR)");
        engine.execute("DROP TABLE ue");
        assertCells(new String[][] {
            {"SELECT f_o(1) FROM t",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.UE."},
            {"SELECT f_o(a) FROM t",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.UE."},
            {"SELECT f_o('a') FROM t", ""},
            {"SELECT f_o(s) FROM t", ""},
            {"SELECT f_o(1.5) FROM t",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.UE."},
            {"SELECT f_o(NULL) FROM t",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.UE."},
            {"SELECT f_o(a::VARCHAR) FROM t", ""},
            {"SELECT f_o(TRUE) FROM t", ""},
            {"SELECT f_o(1)",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.UE."},
            {"SELECT f_o('a')", "1"},
            {"SELECT f_o(CURRENT_DATE()) FROM t", ""},
            {"SELECT f_o(1::FLOAT) FROM t", ""},
            {"SELECT f_o(s::INT) FROM t",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.UE."},
            {"SELECT 1 FROM t WHERE f_o(a) > 0",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.UE."},
            {"SELECT f_o(1) FROM t WHERE FALSE",
                "REFUSED SQL compilation error: | Object 'TEST_DB.TEST_SCHEMA.UE' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on TABLE "
                + "TEST_DB.TEST_SCHEMA.UE."},
        });
    }
}
