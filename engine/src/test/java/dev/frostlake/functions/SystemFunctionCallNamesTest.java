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
 * A SYSTEM$ name a SQL function's body calls is judged by the account's own list of them: a name it does not have
 * is an unknown function, one of its functions the engine never runs is taken, a table function written where a
 * value is read is unknown while one read as a TABLE(...) source is judged by its columns, and
 * SYSTEM$AUTO_REFRESH_STATUS is a function with side effects no view, CTAS or body may hold (all live-verified).
 */
public class SystemFunctionCallNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void accountNames() {
        engine.execute("SHOW BUILTIN FUNCTIONS LIKE 'SYSTEM$%'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FN.",
            "CREATE OR REPLACE FUNCTION sf52() RETURNS VARCHAR AS 'SYSTEM$NOSUCH_FN(1)'");
        assertAnswer("Function SF53 successfully created.",
            "CREATE OR REPLACE FUNCTION sf53() RETURNS VARCHAR AS 'SYSTEM$EXPLAIN_PLAN_JSON(''SELECT 1'')'");
        assertAnswer("Function SF54 successfully created.",
            "CREATE OR REPLACE FUNCTION sf54() RETURNS VARCHAR AS 'SYSTEM$TYPEOF(1)'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FN.",
            "CREATE OR REPLACE FUNCTION sf55() RETURNS VARCHAR AS 'SYSTEM$NOSUCH_FN()'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FN.",
            "CREATE OR REPLACE FUNCTION sf56() RETURNS VARCHAR AS 'UPPER(SYSTEM$NOSUCH_FN(1))'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FN.",
            "CREATE OR REPLACE FUNCTION sf57() RETURNS TABLE (a VARCHAR) AS 'SELECT SYSTEM$NOSUCH_FN(1)'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FN.",
            "SELECT SYSTEM$NOSUCH_FN(1)");
    }

    @Test
    public void engineNames() {
        assertAnswer("Function SG1 successfully created.",
            "CREATE OR REPLACE FUNCTION sg1() RETURNS VARCHAR AS 'SYSTEM$ALLOWLIST()'");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$AUTO_REFRESH_STATUS]",
            "CREATE OR REPLACE FUNCTION sg2() RETURNS VARCHAR AS 'SYSTEM$AUTO_REFRESH_STATUS(''x'')'");
        assertAnswer("Function SG3 successfully created.",
            "CREATE OR REPLACE FUNCTION sg3() RETURNS VARCHAR AS 'SYSTEM$GET_SNOWFLAKE_PLATFORM_INFO()'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$STREAM_BACKLOG.",
            "CREATE OR REPLACE FUNCTION sg4() RETURNS VARCHAR AS 'SYSTEM$STREAM_BACKLOG(''x'')'");
        assertAnswer("Function SG5 successfully created.",
            "CREATE OR REPLACE FUNCTION sg5() RETURNS VARCHAR AS 'SYSTEM$STREAM_GET_TABLE_TIMESTAMP(''x'')'");
        assertAnswer("REFUSED Declared return type 'VARCHAR(134217728)' is incompatible with actual return type "
                + "'BOOLEAN'",
            "CREATE OR REPLACE FUNCTION sg6() RETURNS VARCHAR AS 'SYSTEM$STREAM_HAS_DATA(''x'')'");
        assertAnswer("Function SG7 successfully created.",
            "CREATE OR REPLACE FUNCTION sg7() RETURNS VARCHAR AS 'SYSTEM$WHITELIST()'");
        assertAnswer("Function SG8 successfully created.",
            "CREATE OR REPLACE FUNCTION sg8() RETURNS VARCHAR AS 'SYSTEM$WAIT(1)'");
        assertAnswer("REFUSED SQL compilation error: | Tag 'T' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TAG T.",
            "CREATE OR REPLACE FUNCTION sg9() RETURNS VARCHAR AS 'SYSTEM$GET_TAG(''t'', ''x'', ''table'')'");
    }

    @Test
    public void tableFunctionsAndSideEffects() {
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$AUTO_REFRESH_STATUS]",
            "CREATE OR REPLACE VIEW sv61 AS SELECT SYSTEM$AUTO_REFRESH_STATUS('x') AS s");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$AUTO_REFRESH_STATUS]",
            "CREATE OR REPLACE TABLE st61 AS SELECT SYSTEM$AUTO_REFRESH_STATUS('x') AS s");
        assertAnswer("Table ST62 successfully created.",
            "CREATE OR REPLACE TABLE st62 (a VARCHAR DEFAULT SYSTEM$AUTO_REFRESH_STATUS('x'))");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$AUTO_REFRESH_STATUS]",
            "CREATE OR REPLACE FUNCTION sg61() RETURNS VARCHAR AS 'system$auto_refresh_status(''x'')'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$STREAM_BACKLOG.",
            "SELECT SYSTEM$STREAM_BACKLOG('x')");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$STREAM_BACKLOG.",
            "CREATE OR REPLACE FUNCTION sg62() RETURNS VARCHAR AS 'SELECT SYSTEM$STREAM_BACKLOG(''x'')'");
        assertAnswer("REFUSED Mismatch between declared return signature column count (1) and actual column count (6)",
            "CREATE OR REPLACE FUNCTION sg63() RETURNS TABLE (a VARCHAR) AS 'SELECT * FROM "
                + "TABLE(SYSTEM$STREAM_BACKLOG(''x''))'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$STREAM_BACKLOG.",
            "CREATE OR REPLACE VIEW sv62 AS SELECT SYSTEM$STREAM_BACKLOG('x') AS s");
    }

    @Test
    public void backlogSource() {
        engine.execute("CREATE OR REPLACE TABLE ar62 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$STREAM_BACKLOG.",
            "SELECT SYSTEM$STREAM_BACKLOG()");
        assertAnswer("REFUSED Invalid STREAM 'X'. STREAM_BACKLOG expectes a fully or partially qualified Stream name.",
            "SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG('x'))");
        assertAnswer("REFUSED Declared return type 'VARCHAR(134217728)' for column 'B' is incompatible with actual "
                + "return type 'TIMESTAMP_LTZ(9)'",
            "CREATE OR REPLACE FUNCTION sg64() RETURNS TABLE (a VARCHAR, b VARCHAR, c VARCHAR, d VARCHAR, e VARCHAR, "
                + "f VARCHAR) AS 'SELECT * FROM TABLE(SYSTEM$STREAM_BACKLOG(''x''))'");
    }
}
