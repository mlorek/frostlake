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
 * Which of two refusals an expression earns. What an operator's operands are refused for comes first, in the
 * order written - a call's argument types, an arithmetic operator's, a sign's - and a concatenation's chain is
 * judged last of all: (TRUE || 'a') || IS_NULL_VALUE('x') is IS_NULL_VALUE's refusal, 1 + ('a' IS NULL) +
 * ABS(TRUE) the first addition's. Sibling expressions are judged in the order written (live-verified).
 */
public class OperandRefusalOrderTest extends MeasuredCellsTestSupport {

    @Test
    public void callsBeforeTheirConcatenation() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT IS_NULL_VALUE('x')");
        assertAnswer(
            "truea",
            "SELECT TRUE || 'a'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT (TRUE || 'a') || IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT IS_NULL_VALUE('x') || (TRUE || 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT ('a' IS NULL) || ' / ' || NVL(IS_NULL_VALUE('x')::VARCHAR, 'null')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ABS': (BOOLEAN)",
            "SELECT ABS('a' IS NULL) || IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT IS_NULL_VALUE('x') || ABS('a' IS NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3))",
            "SELECT ('a' IS NULL) || ' / '");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT NVL(IS_NULL_VALUE('x')::VARCHAR, 'null')");
        assertAnswer(
            "false",
            "SELECT IS_NULL_VALUE(1)");
        assertAnswer(
            "true",
            "SELECT IS_NULL_VALUE(PARSE_JSON('null'))");
    }

    @Test
    public void operatorsCallsAndClauses() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3))",
            "SELECT ('a' IS NULL) || ' / ', IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT IS_NULL_VALUE('x'), ('a' IS NULL) || ' / '");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(1))",
            "SELECT 1 WHERE ('a' IS NULL) || 'x' = IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ABS': (BOOLEAN)",
            "SELECT ABS(TRUE) + IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'ABS': (BOOLEAN)",
            "SELECT ('a' IS NULL) || ABS('b' IS NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function '+': "
                + "(NUMBER(1,0), BOOLEAN)",
            "SELECT 1 + ('a' IS NULL) + ABS(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 42 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT ('a' IS NULL) || (('b' IS NULL) || IS_NULL_VALUE('x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NEGATE': (BOOLEAN)",
            "SELECT -('a' IS NULL) || IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(1))",
            "SELECT IFF(('a' IS NULL) || 'x' = 'y', IS_NULL_VALUE('x'), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3))",
            "SELECT UPPER(('a' IS NULL) || ' / ') || IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3), BOOLEAN)",
            "SELECT ('a' IS NULL) || ' / ' || ('b' IS NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT ARRAY_CONSTRUCT(1) || 'x' || IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(3))",
            "SELECT ('a' IS NULL) || ' / ' AS c ORDER BY IS_NULL_VALUE('x')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | Invalid argument types for function "
                + "'||': (BOOLEAN, VARCHAR(1))",
            "SELECT CASE WHEN ('a' IS NULL) || 'x' = 'y' THEN IS_NULL_VALUE('x') END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT ('a' IS NULL) || CAST(IS_NULL_VALUE('x') AS VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT ('a' IS NULL) || IS_NULL_VALUE('x') IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function '+': "
                + "(NUMBER(1,0), ARRAY)",
            "SELECT 1 + ARRAY_CONSTRUCT(1) + ABS(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_FN_ZQ7.",
            "SELECT ('a' IS NULL) || nosuch_fn_zq7(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | too many arguments for function "
                + "[LENGTH(1, 2)] expected 1, got 2",
            "SELECT ('a' IS NULL) || LENGTH(1, 2)");
    }
}
