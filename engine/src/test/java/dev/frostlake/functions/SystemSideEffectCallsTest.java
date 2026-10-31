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
 * The side-effect SYSTEM$ calls as the account answers them: SYSTEM$PIPE_FORCE_RESUME is a function - a view over
 * it is created, a missing pipe and an argument that is no text are refused - a SQL function body naming a block
 * statement (SYSTEM$LOG, SYSTEM$ADD_EVENT, SYSTEM$SET_SPAN_ATTRIBUTES) is refused at CREATE, an owner's-rights
 * procedure refuses SYSTEM$SET_RETURN_VALUE beside SYSTEM$ABORT_TRANSACTION, SYSTEM$SET_RETURN_VALUE runs in a
 * task alone, and SYSTEM$TASK_DEPENDENTS_ENABLE refuses a task that is not there (all live-verified).
 */
public class SystemSideEffectCallsTest extends MeasuredCellsTestSupport {

    @Test
    public void sideEffectCalls() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("View SV52 successfully created.",
            "CREATE OR REPLACE VIEW sv52 AS SELECT SYSTEM$PIPE_FORCE_RESUME('nosuch_pipe') AS c");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'nosuch_pipe' does not exist or not authorized.",
            "SELECT SYSTEM$PIPE_FORCE_RESUME('nosuch_pipe')");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'nosuch_pipe' does not exist or not authorized.",
            "SELECT SYSTEM$PIPE_FORCE_RESUME('nosuch_pipe', 'staleness_check_override')");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'nosuch_pipe' does not exist or not authorized.",
            "SELECT SYSTEM$PIPE_FORCE_RESUME('nosuch_pipe', 'bogus_option')");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'test_db.test_schema.nosuch_pipe' does not exist or not "
                + "authorized.",
            "SELECT SYSTEM$PIPE_FORCE_RESUME('test_db.test_schema.nosuch_pipe')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$PIPE_FORCE_RESUME()], expected 1, got 0",
            "SELECT SYSTEM$PIPE_FORCE_RESUME()");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$PIPE_FORCE_RESUME('a', 'b', 'c')] expected 2, got 3",
            "SELECT SYSTEM$PIPE_FORCE_RESUME('a', 'b', 'c')");
        assertAnswer("REFUSED SQL compilation error: | argument needs to be a string: '1'",
            "SELECT SYSTEM$PIPE_FORCE_RESUME(1)");
        assertAnswer("REFUSED SQL compilation error: | argument needs to be a string: 'null'",
            "SELECT SYSTEM$PIPE_FORCE_RESUME(NULL)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$LOG",
            "CREATE OR REPLACE FUNCTION sf52() RETURNS VARCHAR AS 'SYSTEM$LOG(''info'', ''x'')'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "CREATE OR REPLACE FUNCTION sf52() RETURNS VARCHAR AS 'SYSTEM$LOG_INFO(''x'')'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$ADD_EVENT",
            "CREATE OR REPLACE FUNCTION sf52() RETURNS VARCHAR AS 'SYSTEM$ADD_EVENT(''x'')'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function NOSUCH_FN.",
            "CREATE OR REPLACE FUNCTION sf52() RETURNS VARCHAR AS 'NOSUCH_FN(1)'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$ADD_EVENT",
            "CREATE OR REPLACE VIEW sv52 AS SELECT SYSTEM$ADD_EVENT('x') AS c");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FN.",
            "CREATE OR REPLACE VIEW sv52 AS SELECT SYSTEM$NOSUCH_FN(1) AS c");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$ADD_EVENT",
            "SELECT SYSTEM$ADD_EVENT('x')");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FN.",
            "SELECT SYSTEM$NOSUCH_FN(1)");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$LOG", "SELECT SYSTEM$LOG('info', 'x')");
        assertAnswer("Function SP52 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp52() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN LET x "
                + "VARCHAR := (SELECT SYSTEM$CANCEL_QUERY('01b0-nosuch')); RETURN x; END; $$");
        assertAnswer("invalid UUID", "CALL sp52()");
        assertAnswer("Function SP52 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp52() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN LET x "
                + "VARCHAR := (SELECT SYSTEM$SET_RETURN_VALUE('x')); RETURN x; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error:  | Query called from a stored procedure contains a function with side effects "
                + "[SYSTEM$SET_RETURN_VALUE].",
            "CALL sp52()");
        assertAnswer("Function SP52 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp52() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN LET x "
                + "VARCHAR := (SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('nosuch_task')); RETURN x; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error: | invalid value [nosuch_task] for parameter 'RECURSIVE_TASK_ENABLE'",
            "CALL sp52()");
        assertAnswer("Function SP52 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp52() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN LET x "
                + "VARCHAR := (SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('nosuch_task')); RETURN x; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : Task "
                + "nosuch_task not found or not authorized. If the task was dropped or replaced after a previous "
                + "execution started, use SYSTEM$CANCEL_QUERY along with the query id to cancel the run.",
            "CALL sp52()");
        assertAnswer("Function SP52 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp52() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN LET x "
                + "VARCHAR := (SELECT SYSTEM$ABORT_TRANSACTION(12)); RETURN x; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : SQL "
                + "compilation error:  | Query called from a stored procedure contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION].",
            "CALL sp52()");
        assertAnswer("Function SP52 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp52() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN LET x "
                + "VARCHAR := (SELECT SYSTEM$WAIT(0)); RETURN x; END; $$");
        assertAnswer("waited 0 seconds", "CALL sp52()");
        assertAnswer("Function SP52 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp52() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN LET x "
                + "VARCHAR := (SELECT SYSTEM$CANCEL_QUERY('01b0-nosuch')); RETURN x; END; $$");
        assertAnswer("invalid UUID", "CALL sp52()");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void taskPipeAndReturnValueArguments() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED SQL compilation error: | invalid value [nosuch_task] for parameter "
                + "'RECURSIVE_TASK_ENABLE'",
            "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('nosuch_task')");
        assertAnswer("REFUSED SQL compilation error: | invalid value [test_db.test_schema.nosuch_task] for parameter "
                + "'RECURSIVE_TASK_ENABLE'",
            "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('test_db.test_schema.nosuch_task')");
        assertAnswer("REFUSED SQL compilation error: | invalid value [\"NoSuch\"] for parameter "
                + "'RECURSIVE_TASK_ENABLE'",
            "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('\"NoSuch\"')");
        assertAnswer("null", "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE(NULL)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function SYSTEM$TASK_DEPENDENTS_ENABLE needs to "
                + "be constant, found 'CAST(1 AS VARCHAR(134217728))'",
            "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE(1)");
        assertAnswer("REFUSED SQL compilation error: | invalid value [] for parameter 'RECURSIVE_TASK_ENABLE'",
            "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('')");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$TASK_DEPENDENTS_ENABLE()], expected 1, got 0",
            "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE()");
        assertAnswer("REFUSED SQL compilation error: | argument needs to be a string: '1.5'",
            "SELECT SYSTEM$PIPE_FORCE_RESUME(1.5)");
        assertAnswer("REFUSED SQL compilation error: | argument needs to be a string: 'TRUE'",
            "SELECT SYSTEM$PIPE_FORCE_RESUME(TRUE)");
        assertAnswer("REFUSED SQL compilation error: | Pipe 'nosuch_pipe' does not exist or not authorized.",
            "SELECT SYSTEM$PIPE_FORCE_RESUME('nosuch_pipe', 1)");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function SYSTEM$PIPE_FORCE_RESUME needs to be "
                + "constant, found '\"values\".C'",
            "SELECT SYSTEM$PIPE_FORCE_RESUME(c) FROM (SELECT 'nosuch_pipe' c)");
        assertAnswer("REFUSED SQL compilation error: | argument 0 to function SYSTEM$PIPE_FORCE_RESUME needs to be "
                + "constant, found '\"values\".N'",
            "SELECT SYSTEM$PIPE_FORCE_RESUME(n) FROM (SELECT 1 n)");
        assertAnswer("View SV53 successfully created.",
            "CREATE OR REPLACE VIEW sv53 AS SELECT SYSTEM$PIPE_FORCE_RESUME(1) AS c");
        assertAnswer("REFUSED SQL compilation error: | Pipe '' does not exist or not authorized.",
            "SELECT SYSTEM$PIPE_FORCE_RESUME('')");
        assertAnswer("REFUSED Function SYSTEM$SET_RETURN_VALUE must be called from within a task.",
            "SELECT SYSTEM$SET_RETURN_VALUE('x')");
        assertAnswer("Function SP53 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp53() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN LET x "
                + "VARCHAR := (SELECT SYSTEM$SET_RETURN_VALUE('x')); RETURN x; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 24 : Function "
                + "SYSTEM$SET_RETURN_VALUE must be called from within a task.",
            "CALL sp53()");
        assertAnswer("Function SP53 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp53() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN SELECT "
                + "SYSTEM$SET_RETURN_VALUE('x'); RETURN 'ok'; END; $$");
        assertAnswer("REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 7 : SQL compilation "
                + "error:  | Query called from a stored procedure contains a function with side effects "
                + "[SYSTEM$SET_RETURN_VALUE].",
            "CALL sp53()");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_WARN",
            "CREATE OR REPLACE FUNCTION sf53() RETURNS VARCHAR AS 'SYSTEM$LOG_WARN(''x'')'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$LOG",
            "CREATE OR REPLACE FUNCTION sf53() RETURNS VARCHAR AS 'UPPER(SYSTEM$LOG(''info'', ''x''))'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$ADD_EVENT",
            "CREATE OR REPLACE FUNCTION sf53() RETURNS VARCHAR AS 'SELECT SYSTEM$ADD_EVENT(''x'')'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$SET_SPAN_ATTRIBUTES",
            "CREATE OR REPLACE FUNCTION sf53() RETURNS VARCHAR AS 'SYSTEM$SET_SPAN_ATTRIBUTES(OBJECT_CONSTRUCT(''a'',"
                + " 1))'");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$SET_SPAN_ATTRIBUTES",
            "SELECT SYSTEM$SET_SPAN_ATTRIBUTES(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer("Function SF53 successfully created.",
            "CREATE OR REPLACE FUNCTION sf53() RETURNS VARCHAR AS 'SYSTEM$EXPLAIN_PLAN_JSON(''SELECT 1'')'");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
