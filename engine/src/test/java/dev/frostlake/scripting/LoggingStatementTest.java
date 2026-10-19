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
package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * SYSTEM$LOG and SYSTEM$LOG_<level> are statements of a block and no functions: a block runs them, evaluating
 * their arguments, and a query or a CALL that names one is refused as naming an unknown function. A statement
 * of that spelling under a name that names none does not compile, and SYSTEM$TYPEOF,
 * SYSTEM$GET_TASK_GRAPH_CONFIG and SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS answer and refuse as the
 * account does (live-verified).
 */
public class LoggingStatementTest extends MeasuredCellsTestSupport {

    @Test
    public void typesAndLoggingInABlock() {
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(1)");
        assertAnswer(
            "VARCHAR(1)[LOB]",
            "SELECT SYSTEM$TYPEOF('a')");
        assertAnswer(
            "NULL[LOB]",
            "SELECT SYSTEM$TYPEOF(NULL)");
        assertAnswer(
            "FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(1.5::FLOAT)");
        assertAnswer(
            "DATE[SB4]",
            "SELECT SYSTEM$TYPEOF('2024-01-02'::DATE)");
        assertAnswer(
            "BOOLEAN[SB1]",
            "SELECT SYSTEM$TYPEOF(TRUE)");
        assertAnswer(
            "ARRAY[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_CONSTRUCT(1))");
        assertAnswer(
            "VARIANT[LOB]",
            "SELECT SYSTEM$TYPEOF(PARSE_JSON('1'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$TYPEOF(1, 2)] expected 1, got 2",
            "SELECT SYSTEM$TYPEOF(1, 2)");
        assertAnswer(
            "VARCHAR[LOB]",
            "SELECT SYSTEM$TYPEOF(SYSTEM$TYPEOF(1))");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG_INFO('m'); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG_TRACE('m'); SYSTEM$LOG_DEBUG('m'); SYSTEM$LOG_WARN('m'); SYSTEM$LOG_ERROR('m'); "
                + "SYSTEM$LOG_FATAL('m'); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "SELECT SYSTEM$LOG_INFO('m')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG",
            "SELECT SYSTEM$LOG('info', 'm')");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG_INFO(); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG_INFO(1); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG_INFO(NULL); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "CALL SYSTEM$LOG_INFO('m')");
    }

    @Test
    public void taskFunctionsOutsideATask() {
        assertAnswer(
            "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task.",
            "SELECT SYSTEM$GET_TASK_GRAPH_CONFIG()");
        assertAnswer(
            "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task.",
            "SELECT SYSTEM$GET_TASK_GRAPH_CONFIG('a')");
        assertAnswer(
            "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task.",
            "SELECT SYSTEM$GET_TASK_GRAPH_CONFIG(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$GET_TASK_GRAPH_CONFIG('a', 'b')] expected 1, got 2",
            "SELECT SYSTEM$GET_TASK_GRAPH_CONFIG('a', 'b')");
        assertAnswer(
            "REFUSED Task nosuch_task_zq7 not found or not authorized. If the task was dropped or replaced after "
                + "a previous execution started, use SYSTEM$CANCEL_QUERY along with the query id to cancel the run.",
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('nosuch_task_zq7')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS()], expected 1, got 0",
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS()");
        assertAnswer(
            "null",
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(NULL)");
    }

    @Test
    public void loggingNamesAreNoFunctions() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        answer(
            "CREATE OR REPLACE TABLE tb (n NUMBER(5,0), w NUMBER(20,0), f FLOAT, neg INT, d NUMBER(6,1))");
        answer(
            "INSERT INTO tb VALUES (300, 70000, 5.5, -5, 100.5), (17, 1, 2.0, 5, 3.4), (4, 4294967296, 9.0, -128, "
                + "0.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FUNCTION_ZQ7.",
            "SELECT SYSTEM$NOSUCH_FUNCTION_ZQ7()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FUNCTION_ZQ7.",
            "SELECT SYSTEM$NOSUCH_FUNCTION_ZQ7(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_FUNCTION_ZQ7.",
            "CALL SYSTEM$NOSUCH_FUNCTION_ZQ7()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_WARN",
            "SELECT SYSTEM$LOG_WARN('m') FROM ta");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "SELECT SYSTEM$LOG_INFO()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "SELECT SYSTEM$LOG_INFO('m') FROM te");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "SELECT 1 FROM ta WHERE SYSTEM$LOG_INFO('m') IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "SELECT SYSTEM$TYPEOF(SYSTEM$LOG_INFO('m'))");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 15 : SQL compilation "
                + "error: | Unknown function SYSTEM$LOG_INFO",
            "BEGIN LET x := SYSTEM$LOG_INFO('m'); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG('info', 'm'); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG('nosuch', 'm'); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG('info'); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG(); RETURN 1; END;");
        assertAnswer(
            "1",
            "BEGIN SYSTEM$LOG_INFO('a', 'b'); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown functionSYSTEM$LOG_NOSUCH does not match any known "
                + "functions {1}",
            "BEGIN SYSTEM$LOG_NOSUCH('m'); RETURN 1; END;");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'NOSUCH_ZQ7'",
            "BEGIN SYSTEM$LOG_INFO(nosuch_zq7); RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 22 : Division by zero",
            "BEGIN SYSTEM$LOG_INFO(1 / 0); RETURN 1; END;");
        assertAnswer(
            "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 13 : SQL compilation "
                + "error: | Unknown function SYSTEM$LOG_INFO",
            "BEGIN RETURN SYSTEM$LOG_INFO('m'); END;");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG",
            "CALL SYSTEM$LOG('info', 'm')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$LOG_INFO",
            "SELECT system$log_info('m')");
    }

    @Test
    public void aTaskNamedByOneConstantText() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('a', 'b')] expected 1, got 2",
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS "
                + "needs to be constant, found 'CAST(1 AS VARCHAR(134217728))'",
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(1)");
        assertAnswer(
            "REFUSED Task  not found or not authorized. If the task was dropped or replaced after a previous "
                + "execution started, use SYSTEM$CANCEL_QUERY along with the query id to cancel the run.",
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('')");
        assertAnswer(
            "null",
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(NULL::VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS()], expected 1, got 0",
            "CALL SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown table function SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS",
            "SELECT * FROM TABLE(SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS())");
    }
}
