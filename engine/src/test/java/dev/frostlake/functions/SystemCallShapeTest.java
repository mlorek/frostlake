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
 * A SYSTEM$ function is called as any other function is: under a qualifier it names a routine nobody declared,
 * a name written twice is refused at its second value, a quantifier and a WITHIN GROUP by the call's echo, and
 * a number of arguments the function does not take with the values alone echoed. SYSTEM$TYPEOF takes a named
 * argument; the functions that take none say so once their count is right (live-verified).
 */
public class SystemCallShapeTest extends MeasuredCellsTestSupport {

    @Test
    public void namedArgumentsAndQualifiers() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function SYSTEM$ABORT_TRANSACTION does "
                + "not support named arguments",
            "SELECT SYSTEM$ABORT_TRANSACTION(id => 12)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$ABORT_TRANSACTION.",
            "SELECT SNOWFLAKE.SYSTEM$ABORT_TRANSACTION(12)");
        assertAnswer(
            "Could not abort txn: 12",
            "SELECT \"SYSTEM$ABORT_TRANSACTION\"(12)");
        assertAnswer(
            "Could not abort txn: 12",
            "SELECT \"system$abort_transaction\"(12)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | positional argument cannot follow named "
                + "argument in function call 'SYSTEM$TYPEOF'",
            "SELECT SYSTEM$TYPEOF(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$TYPEOF(1, 2)] expected 1, got 2",
            "SELECT SYSTEM$TYPEOF(1, a => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 34 | duplicate named argument A for function "
                + "SYSTEM$TYPEOF",
            "SELECT SYSTEM$TYPEOF(a => 1, a => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$TYPEOF(1, 2)] expected 1, got 2",
            "SELECT SYSTEM$TYPEOF(a => 1, b => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function SYSTEM$WAIT does not support "
                + "named arguments",
            "SELECT SYSTEM$WAIT(amount => 0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function SYSTEM$WAIT does not support "
                + "named arguments",
            "SELECT SYSTEM$WAIT(0, unit => 'SECONDS')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$TYPEOF.",
            "SELECT SNOWFLAKE.SYSTEM$TYPEOF(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function TEST_DB.TEST_SCHEMA.SYSTEM$TYPEOF.",
            "SELECT test_db.test_schema.SYSTEM$TYPEOF(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function X.SYSTEM$TYPEOF.",
            "SELECT x.SYSTEM$TYPEOF(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function A.B.C.SYSTEM$TYPEOF.",
            "SELECT a.b.c.SYSTEM$TYPEOF(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | function SYSTEM$WAIT does not support "
                + "named arguments",
            "CALL SYSTEM$WAIT(amount => 0)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$WAIT.",
            "CALL SNOWFLAKE.SYSTEM$WAIT(0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function SYSTEM$STREAM_HAS_DATA does not "
                + "support named arguments",
            "SELECT SYSTEM$STREAM_HAS_DATA(a => 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function "
                + "SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS does not support named arguments",
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(a => 'x')");
    }

    @Test
    public void faultsUnknownNamesAndQuantifiers() {
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'.",
            "SELECT SYSTEM$TYPEOF(a =>)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SYSTEM$NOSUCH_ZQ7.",
            "SELECT SYSTEM$NOSUCH_ZQ7(a => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$NOSUCH_ZQ7.",
            "SELECT SNOWFLAKE.SYSTEM$NOSUCH_ZQ7(1)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT \"SYSTEM$TYPEOF\"(1)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT \"system$typeof\"(1)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(a => 1) FROM (SELECT 1 AS a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$TYPEOF.",
            "SELECT SNOWFLAKE.SYSTEM$TYPEOF(a => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function SYSTEM$CLUSTERING_DEPTH does "
                + "not support named arguments",
            "SELECT SYSTEM$CLUSTERING_DEPTH(a => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | positional argument cannot follow named "
                + "argument in function call 'SYSTEM$GET_TAG'",
            "SELECT SYSTEM$GET_TAG(a => 'x', 'y', 'z')");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "BEGIN LET x := SYSTEM$TYPEOF(a => 1); RETURN x; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT SYSTEM$TYPEOF(1,)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier "
                + "'SNOWFLAKE.SYSTEM$TYPEOF'",
            "SELECT SNOWFLAKE.SYSTEM$TYPEOF");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$TYPEOF.",
            "SELECT SNOWFLAKE.SYSTEM$TYPEOF()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined table function SNOWFLAKE.SYSTEM$TYPEOF",
            "SELECT * FROM TABLE(SNOWFLAKE.SYSTEM$TYPEOF(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'SYSTEM$TYPEOF(DISTINCT 1)'",
            "SELECT SYSTEM$TYPEOF(DISTINCT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'all' for function 'SYSTEM$TYPEOF(ALL 1)'",
            "SELECT SYSTEM$TYPEOF(ALL 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$TYPEOF()], expected 1, got 0",
            "SELECT SYSTEM$TYPEOF(*)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.CORTEX.SYSTEM$TYPEOF.",
            "SELECT SNOWFLAKE.CORTEX.SYSTEM$TYPEOF(1)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT IDENTIFIER('SYSTEM$TYPEOF')(1)");
    }

    @Test
    public void otherFunctionsAndTheOrderOfRefusals() {
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(x => 1)");
        assertAnswer(
            "REFUSED Unknown domain: c.",
            "SELECT SYSTEM$GET_TAG(tag_name => 'a', object_name => 'b', object_domain => 'c')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 42 | duplicate named argument A for function "
                + "SYSTEM$TYPEOF",
            "SELECT SYSTEM$TYPEOF(A => 1, B => 2, A => 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function SYSTEM$LAST_CHANGE_COMMIT_TIME "
                + "does not support named arguments",
            "SELECT SYSTEM$LAST_CHANGE_COMMIT_TIME(a => 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | Table 'X' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE X.",
            "SELECT SYSTEM$CLUSTERING_INFORMATION(a => 'x')");
        assertAnswer(
            "REFUSED Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task.",
            "SELECT SYSTEM$GET_TASK_GRAPH_CONFIG(a => 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$CURRENT_USER_TASK_NAME(1)] expected 0, got 1",
            "SELECT SYSTEM$CURRENT_USER_TASK_NAME(a => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'SYSTEM$TYPEOF(DISTINCT 1)'",
            "SELECT SYSTEM$TYPEOF(DISTINCT a => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'SYSTEM$TYPEOF(DISTINCT 1)'",
            "SELECT SYSTEM$TYPEOF(distinct 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function 'SYSTEM$WAIT(DISTINCT 0)'",
            "SELECT SYSTEM$WAIT(DISTINCT 0)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$WAIT()], expected 1, got 0",
            "SELECT SYSTEM$WAIT(*)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$WAIT.",
            "SELECT snowflake.system$wait(0)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.SYSTEM$TYPEOF.",
            "SELECT \"SNOWFLAKE\".SYSTEM$TYPEOF(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function X.Y.SYSTEM$TYPEOF.",
            "CALL x.y.SYSTEM$TYPEOF(1)");
        assertAnswer(
            "NUMBER(1,0)[SB1]",
            "CALL SYSTEM$TYPEOF(a => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'DISTINCT'.",
            "CALL SYSTEM$TYPEOF(DISTINCT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected '*'.",
            "CALL SYSTEM$TYPEOF(*)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid function type [SYSTEM$TYPEOF] for window function.",
            "SELECT SYSTEM$TYPEOF(1) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | Function SYSTEM$TYPEOF does not support WITHIN GROUP clause.",
            "SELECT SYSTEM$TYPEOF(1) WITHIN GROUP (ORDER BY 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'IGNORE'.",
            "SELECT SYSTEM$TYPEOF(1 IGNORE NULLS)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | positional argument cannot follow named "
                + "argument in function call 'SYSTEM$TYPEOF'",
            "SELECT SYSTEM$TYPEOF(a => 1, 2, b => 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | duplicate named argument A for function "
                + "SYSTEM$WAIT",
            "SELECT SYSTEM$WAIT(a => 0, a => 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$WAIT(0, 1, 2)] expected 2, got 3",
            "SELECT SYSTEM$WAIT(0, 1, a => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[SYSTEM$ABORT_TRANSACTION(12, 13)] expected 1, got 2",
            "SELECT SYSTEM$ABORT_TRANSACTION(12, id => 13)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid use of 'distinct' for function "
                + "'SYSTEM$ABORT_TRANSACTION(DISTINCT 12)'",
            "SELECT SYSTEM$ABORT_TRANSACTION(DISTINCT 12)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[SYSTEM$ABORT_TRANSACTION()], expected 1, got 0",
            "SELECT SYSTEM$ABORT_TRANSACTION(*)");
    }
}
