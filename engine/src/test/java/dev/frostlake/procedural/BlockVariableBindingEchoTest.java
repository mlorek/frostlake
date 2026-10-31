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
package dev.frostlake.procedural;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block binds each variable its own SQL expressions read - a CASE, a comparison, IS NULL, a LET's or an IF's
 * expression - as the conversion of the variable's value to its declared type, and a refusal echoes that binding
 * (live-verified): {@code CAST('5' AS NUMBER(38,0))} for an INT, {@code CAST('2024-01-02' AS DATE)} for a DATE, a
 * timestamp's or a time's text at its precision, {@code CAST(PARSE_JSON(...) AS VARIANT)} a member a line, the
 * literal of a text and {@code CAST(null AS T)} for a NULL of any other type. An operator's operand is the binding in
 * parentheses, a conversion's refusal prints it as {@code TO_NUMBER('5')}, and the constant rules of RANDOM, UNIFORM
 * and LAST_QUERY_ID judge the binding: {@code RANDOM(n + 1)} is seeded, and {@code LAST_QUERY_ID(i + 1)} over an INT
 * holding 1 is found '1 + 1'.
 */
public class BlockVariableBindingEchoTest extends MeasuredCellsTestSupport {

    /** Quantified calls and conversions inside CASE, IS NULL, IN and LET. */
    @Test
    public void callsInsideCasesEchoBindings() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE WHEN TRUE THEN ABS(ALL n) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE WHEN TRUE THEN ABS(ALL -n) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL NEGATE(CAST('5' AS NUMBER(38,0))))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET a INT := 1; LET b INT := 2; RETURN CASE WHEN TRUE THEN ABS(ALL a + b) "
                + "END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 46 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL (CAST('1' AS NUMBER(38,0))) + (CAST('2' AS "
                + "NUMBER(38,0))))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE WHEN TRUE THEN ABS(ALL n * 2 + 1) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL ((CAST('5' AS NUMBER(38,0))) * 2) + 1)'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := -1.5; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT n) END; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('-1.50' AS NUMBER(10,2)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := 'a'; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT s) END; END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT 'a')'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := 'a'; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT s || 'b') "
                + "END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT 'a' || 'b')'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := 'it''s'; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT s) END; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT 'it''s')'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET f FLOAT := 1.5; RETURN CASE WHEN TRUE THEN ABS(ALL f) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('1.5' AS FLOAT))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET f FLOAT := 1.5; RETURN CASE WHEN TRUE THEN ABS(ALL f + 1) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL (CAST('1.5' AS FLOAT)) + 1)'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET d DATE := '2024-01-02'; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT d) END; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 42 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('2024-01-02' AS DATE))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET b BOOLEAN := TRUE; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT b) END; END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('true' AS BOOLEAN))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET v VARIANT := PARSE_JSON('1'); RETURN CASE WHEN TRUE THEN ABS(ALL v) END; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST(PARSE_JSON('1') AS VARIANT))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT; RETURN CASE WHEN TRUE THEN ABS(ALL n) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 25 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST(null AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 5; RETURN CASE WHEN TRUE THEN ABS(ALL x) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1.25; RETURN CASE WHEN TRUE THEN ABS(ALL x) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('1.25' AS FLOAT))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 'q'; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT x) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 28 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT 'q')'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; LET r INT := CASE WHEN TRUE THEN ABS(ALL n) END; RETURN r; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; IF (CASE WHEN TRUE THEN ABS(ALL n) END = 5) THEN RETURN 1; "
                + "END IF; RETURN 0; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 27 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n) IS NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN NOT (ABS(ALL n) = 5); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n) BETWEEN 1 AND 9; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN ABS(ALL n) IN (1, 5); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE n WHEN 5 THEN ABS(ALL n) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE WHEN TRUE THEN TO_OBJECT(n) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid type [TO_OBJECT(TO_NUMBER('5'))] for parameter 'TO_OBJECT'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := 'a'; RETURN CASE WHEN TRUE THEN TO_OBJECT(s) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 36 : SQL compilation "
                + "error: | invalid type [TO_OBJECT('a')] for parameter 'TO_OBJECT'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE WHEN TRUE THEN TO_OBJECT(n + 1) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid type [TO_OBJECT((TO_NUMBER('5')) + 1)] for parameter 'TO_OBJECT'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(38,0) := 123456789012345678901234567890; RETURN CASE WHEN TRUE "
                + "THEN ABS(ALL n) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('123456789012345678901234567890' AS "
                + "NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE n INT DEFAULT 5; BEGIN RETURN CASE WHEN TRUE THEN ABS(ALL n) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE n NUMBER(6,3) DEFAULT 2; BEGIN RETURN CASE WHEN TRUE THEN ABS(ALL n) END; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 47 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('2.000' AS NUMBER(6,3)))'"},
        });
    }

    /** Semi-structured, binary, NULL, text, temporal and untyped variables. */
    @Test
    public void bindingsOfEveryType() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET v VARIANT := PARSE_JSON('{\"a\": 1, \"b\": [1, 2]}'); RETURN CASE WHEN "
                + "TRUE THEN ABS(ALL v) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 68 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST(PARSE_JSON('{ | \"a\": 1, | \"b\": [ | 1, "
                + "| 2 | ] | }') AS VARIANT))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET v VARIANT := TO_VARIANT('x'); RETURN CASE WHEN TRUE THEN ABS(ALL v) END; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 48 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST(PARSE_JSON('\"x\"') AS VARIANT))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET o OBJECT := OBJECT_CONSTRUCT('a', 1); RETURN CASE WHEN TRUE THEN "
                + "UPPER(DISTINCT o) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST(PARSE_JSON('{ | \"a\": 1 | }') "
                + "AS OBJECT))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET a ARRAY := ARRAY_CONSTRUCT(1, 2); RETURN CASE WHEN TRUE THEN "
                + "UPPER(DISTINCT a) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 52 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST(PARSE_JSON('[ | 1, | 2 | ]') "
                + "AS ARRAY))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET b BINARY := TO_BINARY('4142', 'HEX'); RETURN CASE WHEN TRUE THEN "
                + "UPPER(DISTINCT b) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 56 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('4142' AS BINARY(67108864)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT s) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT null)'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR(5); RETURN CASE WHEN TRUE THEN UPPER(DISTINCT s) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT null)'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2); RETURN CASE WHEN TRUE THEN ABS(ALL n) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 34 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST(null AS NUMBER(10,2)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := 'a\\\\b'; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT s) END; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT 'a\\\\b')'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET d DATE; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT d) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST(null AS DATE))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET b BOOLEAN; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT b) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 29 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST(null AS BOOLEAN))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 1.25; RETURN SYSTEM$TYPEOF(x); END; $$",
                "FLOAT[DOUBLE]"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET x := 5; RETURN SYSTEM$TYPEOF(x); END; $$",
                "NUMBER(38,0)[SB16]"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE WHEN TRUE THEN UPPER(DISTINCT n::VARCHAR) END; "
                + "END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST(CAST('5' AS NUMBER(38,0)) AS "
                + "VARCHAR(134217728)))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE WHEN TRUE THEN ABS(ALL n) + ABS(ALL n) END; END; "
                + "$$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid use of 'all' for function 'ABS(ALL CAST('5' AS NUMBER(38,0)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE t TIMESTAMP_NTZ DEFAULT '2024-01-02 03:04:05'; BEGIN RETURN CASE WHEN TRUE "
                + "THEN UPPER(DISTINCT t) END; EXCEPTION WHEN OTHER THEN RETURN REPLACE(REPLACE(REPLACE(SQLERRM, '-', "
                + "'~'), ':', '#'), '+', 'P'); END; $$",
                "SQL compilation error# | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('2024~01~02 "
                + "03#04#05.000000000' AS TIMESTAMP_NTZ(9)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE t TIMESTAMP_NTZ DEFAULT '2024-01-02 03:04:05.123'; BEGIN RETURN CASE WHEN "
                + "TRUE THEN UPPER(DISTINCT t) END; EXCEPTION WHEN OTHER THEN RETURN REPLACE(REPLACE(REPLACE(SQLERRM, "
                + "'-', '~'), ':', '#'), '+', 'P'); END; $$",
                "SQL compilation error# | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('2024~01~02 "
                + "03#04#05.123000000' AS TIMESTAMP_NTZ(9)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE t TIMESTAMP_TZ DEFAULT '2024-01-02 03:04:05 +02:00'; BEGIN RETURN CASE WHEN "
                + "TRUE THEN UPPER(DISTINCT t) END; EXCEPTION WHEN OTHER THEN RETURN REPLACE(REPLACE(REPLACE(SQLERRM, "
                + "'-', '~'), ':', '#'), '+', 'P'); END; $$",
                "SQL compilation error# | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('2024~01~02 "
                + "03#04#05.000000000 P02#00' AS TIMESTAMP_TZ(9)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE t TIMESTAMP_LTZ DEFAULT '2024-01-02 03:04:05'; BEGIN RETURN CASE WHEN TRUE "
                + "THEN UPPER(DISTINCT t) END; EXCEPTION WHEN OTHER THEN RETURN REPLACE(REPLACE(REPLACE(SQLERRM, '-', "
                + "'~'), ':', '#'), '+', 'P'); END; $$",
                "SQL compilation error# | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('2024~01~02 "
                + "03#04#05.000000000' AS TIMESTAMP_LTZ(9)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE t TIME DEFAULT '03:04:05'; BEGIN RETURN CASE WHEN TRUE THEN UPPER(DISTINCT "
                + "t) END; EXCEPTION WHEN OTHER THEN RETURN REPLACE(REPLACE(REPLACE(SQLERRM, '-', '~'), ':', '#'), "
                + "'+', 'P'); END; $$",
                "SQL compilation error# | invalid use of 'distinct' for function 'UPPER(DISTINCT "
                + "CAST('03#04#05.000000000' AS TIME(9)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE t TIMESTAMP_NTZ(3) DEFAULT '2024-01-02 03:04:05.5'; BEGIN RETURN CASE WHEN "
                + "TRUE THEN UPPER(DISTINCT t) END; EXCEPTION WHEN OTHER THEN RETURN REPLACE(REPLACE(REPLACE(SQLERRM, "
                + "'-', '~'), ':', '#'), '+', 'P'); END; $$",
                "SQL compilation error# | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('2024~01~02 "
                + "03#04#05.500' AS TIMESTAMP_NTZ(3)))'"},
            {"EXECUTE IMMEDIATE $$ DECLARE d DATE DEFAULT '2024-01-02'; BEGIN RETURN CASE WHEN TRUE THEN "
                + "UPPER(DISTINCT d) END; EXCEPTION WHEN OTHER THEN RETURN REPLACE(REPLACE(REPLACE(SQLERRM, '-', '~'), "
                + "':', '#'), '+', 'P'); END; $$",
                "SQL compilation error# | invalid use of 'distinct' for function 'UPPER(DISTINCT CAST('2024~01~02' AS "
                + "DATE))'"},
        });
    }

    /** RANDOM, UNIFORM and a top-level TO_OBJECT over variables. */
    @Test
    public void constantArgumentsReadBindings() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN TO_OBJECT(n); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid type [TO_OBJECT(TO_NUMBER('5'))] for parameter 'TO_OBJECT'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN TO_OBJECT(n + 1); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | invalid type [TO_OBJECT((TO_NUMBER('5')) + 1)] for parameter 'TO_OBJECT'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN RANDOM(n + 1) IS NOT NULL; END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN RANDOM(n + 1) = RANDOM(n + 1); END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN RANDOM(n * 2) = RANDOM(n * 2); END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN RANDOM(-n) = RANDOM(-n); END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN RANDOM(ABS(n)) = RANDOM(ABS(n)); END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := '5'; RETURN RANDOM(s) = RANDOM(s); END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN UNIFORM(1, n + 1, RANDOM(7)) BETWEEN 1 AND 6; END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN UNIFORM(n, n + 10, 5) BETWEEN 5 AND 15; END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.5; RETURN RANDOM(n + 1) = RANDOM(n + 1); END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET f FLOAT := 1.5; RETURN RANDOM(f) = RANDOM(f); END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT; RETURN RANDOM(n + 1) IS NULL; END; $$",
                "false"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n INT := 5; RETURN CASE WHEN TRUE THEN RANDOM(n * 2) = RANDOM(n * 2) END; "
                + "END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1.5; RETURN LAST_QUERY_ID(n * 2) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(1.5 * 2 AS "
                + "NUMBER(18,0))'"},
        });
    }

    /** LAST_QUERY_ID's index over variables, folded as the account folds it. */
    @Test
    public void queryIndexOverBindings() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i - 1) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 - 1'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(1 + i) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 + 1'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i + i) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 + 1'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i * i) IS NOT NULL; END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(2 * i) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(2 AS "
                + "NUMBER(38,0))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 2; RETURN LAST_QUERY_ID(i * 3) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '2 * 3'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 0; RETURN LAST_QUERY_ID(i * 3) IS NOT NULL; END; $$",
                "false"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(-i) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'NEGATE(1)'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i / 1) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found "
                + "'CAST(SCALED_ROUND_INT_DIVIDE(1, 1) AS NUMBER(32,0))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i::INT + 0) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '(1) + 0'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET n NUMBER(10,2) := 1; RETURN LAST_QUERY_ID(n * 1) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 39 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(CAST(1 AS "
                + "NUMBER(11,2)) AS NUMBER(18,0))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET f FLOAT := 1; RETURN LAST_QUERY_ID(f + 1) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 32 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'CAST(1.0 + 1.0 AS "
                + "NUMBER(18,0))'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET s VARCHAR := '1'; RETURN LAST_QUERY_ID(s || '') IS NOT NULL; END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; LET r VARCHAR := LAST_QUERY_ID(i + 1); RETURN r; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 40 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 + 1'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i + 1); END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 + 1'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN CASE WHEN TRUE THEN LAST_QUERY_ID(i + 1) END; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 + 1'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(ABS(i)) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found 'ABS(1)'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i + 1 + 1) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '(1 + 1) + 1'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID((i + 1) * 1) IS NOT NULL; END; $$",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 30 : SQL compilation "
                + "error: | argument 1 to function LAST_QUERY_ID needs to be constant, found '1 + 1'"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 3; RETURN LAST_QUERY_ID(i * 1) IS NOT NULL; END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := -1; RETURN LAST_QUERY_ID(i * 1) IS NOT NULL; END; $$",
                "true"},
            {"EXECUTE IMMEDIATE $$ BEGIN LET i INT := 1; RETURN LAST_QUERY_ID(i) IS NOT NULL; END; $$",
                "true"},
        });
    }
}
