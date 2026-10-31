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

package dev.frostlake.features;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The functions with side effects no view, table query or SQL function body may hold: SYSTEM$ABORT_TRANSACTION,
 * SYSTEM$CANCEL_QUERY, SYSTEM$CANCEL_ALL_QUERIES, SYSTEM$SET_RETURN_VALUE, SYSTEM$TASK_DEPENDENTS_ENABLE and
 * SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS, written bare, in any case or quoted; SYSTEM$WAIT and SYSTEM$TYPEOF
 * are none, a task's body may call one, and a policy's body or a materialized view may not (all live-verified).
 */
public class SideEffectFunctionFamilyTest extends MeasuredCellsTestSupport {

    @Test
    public void theSideEffectFamily() {
        engine.execute("CREATE OR REPLACE TABLE xt (a INT)");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$CANCEL_QUERY]",
            "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$CANCEL_QUERY('01b0-nosuch') AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$CANCEL_ALL_QUERIES]",
            "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$CANCEL_ALL_QUERIES(0) AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$TASK_DEPENDENTS_ENABLE]",
            "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('nosuch_task') AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$SET_RETURN_VALUE]",
            "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$SET_RETURN_VALUE('x') AS c");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$LOG",
            "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$LOG('info', 'x') AS c");
        assertAnswer("REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$LOG_INFO('x') AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS]",
            "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('nosuch_task') AS c");
        assertAnswer("View SV successfully created.", "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$WAIT(0) AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$ABORT_TRANSACTION(12) AS c");
        assertAnswer("View SV successfully created.", "CREATE OR REPLACE VIEW sv AS SELECT SYSTEM$TYPEOF(1) AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$CANCEL_QUERY]",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS 'SYSTEM$CANCEL_QUERY(''01b0-nosuch'')'");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$CANCEL_ALL_QUERIES]",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS 'SYSTEM$CANCEL_ALL_QUERIES(0)'");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$TASK_DEPENDENTS_ENABLE]",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS 'SYSTEM$TASK_DEPENDENTS_ENABLE(''nosuch_task'')'");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$SET_RETURN_VALUE]",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS 'SYSTEM$SET_RETURN_VALUE(''x'')'");
        assertAnswer("Function SF successfully created.",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS 'SYSTEM$PIPE_FORCE_RESUME(''nosuch_pipe'')'");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS]",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS "
                + "'SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(''nosuch_task'')'");
        assertAnswer("Function SF successfully created.",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS 'SYSTEM$WAIT(0)'");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS 'SYSTEM$ABORT_TRANSACTION(12)'");
        assertAnswer("Function SF successfully created.",
            "CREATE OR REPLACE FUNCTION sf() RETURNS VARCHAR AS 'SYSTEM$TYPEOF(1)'");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE VIEW sv AS SELECT \"SYSTEM$ABORT_TRANSACTION\"(12) AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE VIEW sv AS SELECT system$abort_transaction(12) AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE MATERIALIZED VIEW smv AS SELECT a, SYSTEM$ABORT_TRANSACTION(12) AS c FROM xt");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE MASKING POLICY smp AS (v VARCHAR) RETURNS VARCHAR -> SYSTEM$ABORT_TRANSACTION(12)");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE ROW ACCESS POLICY srap AS (v INT) RETURNS BOOLEAN -> SYSTEM$ABORT_TRANSACTION(12) = "
                + "'x'");
        assertAnswer("Task STK successfully created.",
            "CREATE OR REPLACE TASK stk SCHEDULE = '60 MINUTE' AS SELECT SYSTEM$ABORT_TRANSACTION(12)");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE SECURE VIEW sv AS SELECT SYSTEM$ABORT_TRANSACTION(12) AS c");
        assertAnswer("REFUSED SQL compilation error:  | View or UDF contains a function with side effects "
                + "[SYSTEM$ABORT_TRANSACTION]",
            "CREATE OR REPLACE VIEW sv AS SELECT a FROM xt WHERE SYSTEM$ABORT_TRANSACTION(a) IS NULL");
    }
}
