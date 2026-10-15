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
package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A CALL of a built-in function: its column is typed as the SELECT of the call is, a count of arguments the
 * function does not take is placed nowhere, and a function that answers only over a window has none
 * (live-verified).
 */
public class CallBuiltInColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void theColumnOfTheCall() {
        answer(
            "CREATE OR REPLACE TABLE cb (a INT)");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        assertShape(
            "UPPER:VARCHAR(3)",
            "CALL UPPER('a')");
        assertShape(
            "CONCAT:VARCHAR(2)",
            "CALL CONCAT('a', 'b')");
        assertShape(
            "ABS:NUMBER(2,0)",
            "CALL ABS(-1)");
        assertShape(
            "LENGTH:NUMBER(18,0)",
            "CALL LENGTH('abc')");
        assertShape(
            "LPAD:VARCHAR(134217728)",
            "CALL LPAD('a', 5, 'x')");
        assertShape(
            "REPEAT:VARCHAR(134217728)",
            "CALL REPEAT('ab', 3)");
        assertShape(
            "IFF:VARCHAR(3)",
            "CALL IFF(TRUE, 'a', 'bcd')");
        assertShape(
            "COALESCE:VARCHAR(134217728)",
            "CALL COALESCE(NULL, 'ab')");
        assertShape(
            "TO_NUMBER:NUMBER(38,0)",
            "CALL TO_NUMBER('12')");
        assertShape(
            "ARRAY_CONSTRUCT:ARRAY",
            "CALL ARRAY_CONSTRUCT(1)");
        assertShape(
            "SYSTEM$TYPEOF:VARCHAR(134217728)",
            "CALL SYSTEM$TYPEOF(1)");
        assertShape(
            "UPPER:VARCHAR(15)",
            "CALL UPPER('abc' || 'de')");
        assertShape(
            "TO_VARCHAR:VARCHAR(134217728)",
            "CALL TO_VARCHAR(12)");
        assertShape(
            "ROUND:NUMBER(5,2)",
            "CALL ROUND(1.256, 2)");
        assertAnswer(
            "A",
            "CALL UPPER('a')");
    }

    @Test
    public void refusalsOfTheCall() {
        answer(
            "CREATE OR REPLACE TABLE cb (a INT)");
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[UPPER()], expected 1, got 0",
            "CALL UPPER()");
        assertAnswer(
            "REFUSED SQL compilation error: | Missing window specification for function [ROW_NUMBER()].",
            "CALL ROW_NUMBER()");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.CORE.NOSUCH.",
            "CALL SNOWFLAKE.CORE.NOSUCH(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function "
                + "[UPPER('a', 'b')] expected 1, got 2",
            "CALL UPPER('a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCHFN.",
            "CALL NOSUCHFN(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function TEST_SCHEMA.NOSUCH.",
            "CALL test_schema.nosuch(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function SNOWFLAKE.NOSUCH.NOSUCH.",
            "CALL SNOWFLAKE.NOSUCH.NOSUCH(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown user-defined function NOSUCHDB.S.F.",
            "CALL NOSUCHDB.S.F(1)");
        assertAnswer(
            "1",
            "CALL COUNT(1)");
        assertAnswer(
            "1",
            "CALL SUM(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Missing window specification for function [RANK()].",
            "CALL RANK()");
        assertAnswer(
            "REFUSED SQL compilation error: | Missing window specification for function [LAG(1)].",
            "CALL LAG(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | not enough arguments for function "
                + "[ABS()], expected 1, got 0",
            "CALL ABS()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | too many arguments for function [ABS(1, "
                + "2)] expected 1, got 2",
            "CALL ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '<EOF>'.",
            "CALL CURRENT_DATE");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function SPLIT_TO_TABLE.",
            "CALL SPLIT_TO_TABLE('a', ',')");
    }
}
