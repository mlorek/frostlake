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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A function takes no ROW. Its argument count is judged first, and then the row is refused by type at the call:
 * Invalid argument types for most, COALESCE named as the IFNULL of its first two arguments and AVG as SUM; the
 * constructors, MIN, MAX and MODE as a type they do not support; a conversion as an invalid type for its own
 * parameter; ANY_VALUE as a select item of several columns. A window function refuses it as the plain call
 * does, and a user-defined function by its bare name. EQUAL_NULL and DECODE compare a row with rows of as many
 * values, SEARCH reads a row of columns, and CASE takes rows as the two sides of its comparison alone. Every
 * answer is live-verified.
 */
public class RowValueArgumentTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute(
            "INSERT INTO t VALUES (1, 'y', '2024-01-04'), (2, 'x', '2024-01-03'), (3, 'x', '2024-01-02'), (1, 'x',"
                + " '2024-01-01'), (NULL, 'z', NULL), (2, NULL, '2024-01-05')");
        engine.execute(
            "CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute(
            "CREATE OR REPLACE FUNCTION fr(x INT) RETURNS INT AS $$ x + 1 $$");
    }

    @Test
    public void aRowAsAnArgument() {
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT ARRAY_CONSTRUCT((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT COALESCE((1, 2), (3, 4))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT COALESCE((1, 2), (3, 4), (5, 6))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT COALESCE((1, 2), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT COALESCE(3, (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NVL': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT NVL((1, 2), (3, 4))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT IFNULL((1, 2), (3, 4))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (BOOLEAN, ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT IFF(TRUE, (1, 2), (3, 4))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0), NUMBER(1,0))",
            "SELECT IFF((1, 2), 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NULLIF': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT NULLIF((1, 2), (1, 2))");
        assertAnswer(
            "true",
            "SELECT EQUAL_NULL((1, 2), (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LEAST': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT LEAST((1, 2), (3, 4))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GREATEST': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT GREATEST((1, 2), (3, 4))");
        assertAnswer(
            "x",
            "SELECT DECODE((1, 2), (1, 2), 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR(ROW(1, 2))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TO_JSON': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT TO_JSON((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT HASH((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TYPEOF': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT TYPEOF((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_AGG': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT ARRAY_AGG((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT COUNT((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT COUNT(DISTINCT (a, b)) FROM t");
        assertAnswer(
            "4",
            "SELECT COUNT(DISTINCT a, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MIN does not support ROW(NUMBER(38,0), VARCHAR(16777216)) "
                + "argument type",
            "SELECT MIN((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(38,0), VARCHAR(16777216)) "
                + "argument type",
            "SELECT MAX((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT SUM((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ABS': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ABS((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'UPPER': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT UPPER((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LENGTH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT LENGTH((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONCAT': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT CONCAT((1, 2), 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | Function OBJECT_CONSTRUCT does not support ROW(NUMBER(1,0), "
                + "NUMBER(1,0)) argument type",
            "SELECT OBJECT_CONSTRUCT('k', (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_CONTAINS': (ROW(NUMBER(1,0), NUMBER(1,0)), ARRAY)",
            "SELECT ARRAY_CONTAINS((1, 2), ARRAY_CONSTRUCT())");
        assertAnswer(
            "1 ;; 1 ;; 1 ;; 1 ;; 1 ;; 1",
            "SELECT ROW_NUMBER() OVER (PARTITION BY (a, b) ORDER BY a) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LISTAGG': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT LISTAGG((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT ANY_VALUE((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NOSUCH_FN.",
            "SELECT nosuch_fn((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ZEROIFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ZEROIFNULL((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT ARRAY_CONSTRUCT((1, 2), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT ARRAY_CONSTRUCT(3, (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT ARRAY_CONSTRUCT(((1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_SIZE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ARRAY_SIZE((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_ARRAY(ROW(1, 2))] for parameter 'TO_ARRAY'",
            "SELECT TO_ARRAY((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARIANT(ROW(1, 2))] for parameter 'TO_VARIANT'",
            "SELECT TO_VARIANT((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function 'IS "
                + "NOT NULL': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT NVL2((1, 2), 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ROUND': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ROUND((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ROUND': (NUMBER(2,1), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ROUND(1.5, (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (BOOLEAN, NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT IFF(TRUE, 1, (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[COALESCE(ROW(1, 2))], expected 2, got 1",
            "SELECT COALESCE((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GREATEST': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT GREATEST((1, 2), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MOD': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT MOD((1, 2), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SQRT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT SQRT((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function OBJECT_CONSTRUCT does not support ROW(NUMBER(1,0), "
                + "NUMBER(1,0)) argument type for keys",
            "SELECT OBJECT_CONSTRUCT((1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(38,0), "
                + "VARCHAR(16777216)) argument type",
            "SELECT ARRAY_CONSTRUCT((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (ROW(NUMBER(38,0), VARCHAR(16777216)), ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT COALESCE((a, b), (a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_CHAR(ROW(1, 2))] for parameter 'TO_CHAR'",
            "SELECT TO_CHAR((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT HASH((1, 2), 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MD5': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT MD5((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '('.",
            "SELECT COUNT(*) FILTER (WHERE (1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: Date/time component [ROW(1, 2) ]for function DATE_TRUNC needs to be "
                + "an identifier or a string literal.",
            "SELECT DATE_TRUNC((1, 2), d) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'DATE_ADDDAYSTODATE': (ROW(NUMBER(1,0), NUMBER(1,0)), DATE)",
            "SELECT DATEADD(day, (1, 2), d) FROM t");
        assertAnswer(
            "a",
            "SELECT CONCAT_WS((1, 2), 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT_COMPACT does not support ROW(NUMBER(1,0), "
                + "NUMBER(1,0)) argument type",
            "SELECT ARRAY_CONSTRUCT_COMPACT((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NULL)",
            "SELECT COALESCE((1, 2), NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NVL': (NULL, ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT NVL(NULL, (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT SUM((a, a)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT AVG((a, a)) FROM t");
        assertAnswer(
            "1 ;; 2 ;; 3 ;; 4 ;; 5 ;; 6",
            "SELECT RANK() OVER (ORDER BY (b, a)) FROM t ORDER BY 1");
    }

    @Test
    public void conversionsCaseAndTheFunctionsThatCompare() {
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [ANY]",
            "SELECT CASE WHEN TRUE THEN (1, 2) ELSE 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [ANY]",
            "SELECT CASE WHEN TRUE THEN (1, 2) ELSE (3, 4) END");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [VARCHAR(1)]",
            "SELECT CASE WHEN TRUE THEN 'a' WHEN FALSE THEN (1, 2) END");
        assertAnswer(
            "1",
            "SELECT CASE (1, 2) WHEN (1, 2) THEN 1 WHEN (3, 4) THEN 2 END");
        assertAnswer(
            "0",
            "SELECT CASE (1, 2) WHEN (1, 3) THEN 1 ELSE 0 END");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2, 3)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            "SELECT CASE (1, 2) WHEN (1, 2, 3) THEN 1 END");
        assertAnswer(
            "0",
            "SELECT CASE (1, NULL) WHEN (1, NULL) THEN 1 ELSE 0 END");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [ANY]",
            "SELECT CASE WHEN (1, 2) = (1, 2) THEN (1, 2) END");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [NUMBER(38,0)]",
            "SELECT CASE a WHEN (1, 2) THEN 1 END FROM t");
        assertAnswer(
            "1 ;; 0 ;; 0 ;; 0 ;; 0 ;; 0",
            "SELECT CASE (a, b) WHEN (1, 'x') THEN 1 ELSE 0 END FROM t ORDER BY a, b");
        assertAnswer(
            "1",
            "SELECT IFF((1, 2) = (1, 2), 1, 0)");
        assertAnswer(
            "true",
            "SELECT NVL((1, 2) = (1, 2), FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_NUMBER(ROW(1, 2))] for parameter 'TO_NUMBER'",
            "SELECT TO_NUMBER((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE(ROW(1, 2))] for parameter 'TO_DATE'",
            "SELECT TO_DATE((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN(ROW(1, 2))] for parameter 'TO_BOOLEAN'",
            "SELECT TO_BOOLEAN((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DOUBLE(ROW(1, 2))] for parameter 'TO_DOUBLE'",
            "SELECT TO_DOUBLE((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT(ROW(1, 2))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BINARY(ROW(1, 2))] for parameter 'TO_BINARY'",
            "SELECT TO_BINARY((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(ROW(1, 2))] for parameter "
                + "'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME(ROW(1, 2))] for parameter 'TO_TIME'",
            "SELECT TO_TIME((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_TO_NUMBER(ROW(1, 2))] for parameter 'TO_NUMBER'",
            "SELECT TRY_TO_NUMBER((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DECIMAL(ROW(1, 2))] for parameter 'TO_DECIMAL'",
            "SELECT TO_DECIMAL((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS NUMBER(38,0))] for parameter "
                + "'TO_NUMBER'",
            "SELECT (1, 2)::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS VARIANT)] for parameter 'TO_VARIANT'",
            "SELECT (1, 2)::VARIANT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS ARRAY)] for parameter 'TO_ARRAY'",
            "SELECT (1, 2)::ARRAY");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS DATE)] for parameter 'TO_DATE'",
            "SELECT CAST((1, 2) AS DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS OBJECT)] for parameter 'TO_OBJECT'",
            "SELECT (1, 2)::OBJECT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_CAST(ROW(1, 2))] for parameter 'TO_VARCHAR'",
            "SELECT TRY_CAST((1, 2) AS VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS BOOLEAN)] for parameter 'TO_BOOLEAN'",
            "SELECT (1, 2)::BOOLEAN");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST(ROW(1, 2) AS FLOAT)] for parameter 'TO_DOUBLE'",
            "SELECT (1, 2)::FLOAT");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP_NTZ(ROW(1, 2))] for parameter "
                + "'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP_NTZ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PARSE_JSON': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT PARSE_JSON((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TRY_PARSE_JSON': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT TRY_PARSE_JSON((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_ARRAY': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT IS_ARRAY((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT IS_NULL_VALUE((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_AGG': (ROW(NUMBER(38,0), VARCHAR(16777216)), NUMBER(1,0))",
            "SELECT OBJECT_AGG((a, b), 1) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LAG': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT LAG((a, b)) OVER (ORDER BY a) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT SUM((a, a)) OVER () FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT COUNT((a, b)) OVER () FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NTILE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT NTILE((1, 2)) OVER (ORDER BY a) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MIN does not support ROW(NUMBER(38,0), VARCHAR(16777216)) "
                + "argument type",
            "SELECT MIN((a, b)) OVER () FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_AGG': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT ARRAY_AGG((a, b)) OVER () FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH_AGG': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT HASH_AGG((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MODE does not support ROW(NUMBER(38,0), VARCHAR(16777216)) "
                + "argument type",
            "SELECT MODE((a, b)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LISTAGG': (VARCHAR(16777216), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT LISTAGG(b, (1, 2)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FR': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT fr((1, 2))");
        assertAnswer(
            "2",
            "SELECT fr(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'FR': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT TEST_SCHEMA.fr((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'EQUAL_NULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT EQUAL_NULL((1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2, 3)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            "SELECT EQUAL_NULL((1, 2), (1, 2, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'EQUAL_NULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT DECODE((1, 2), 1, 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [NUMBER(1,0)]",
            "SELECT DECODE(1, (1, 2), 'x')");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [ANY]",
            "SELECT DECODE(1, 1, (1, 2))");
        assertAnswer(
            "y",
            "SELECT DECODE((1, 2), (1, 3), 'x', 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONCAT': (ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1), VARCHAR(1))",
            "SELECT CONCAT_WS(',', (1, 2), 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONCAT': (VARCHAR(1), ROW(NUMBER(1,0), NUMBER(1,0)), VARCHAR(1))",
            "SELECT CONCAT_WS((1, 2), 'a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | argument 1 to function SEARCH needs to be constant, found 'ROW(1, "
                + "2)'",
            "SELECT SEARCH((a, b), (1, 2)) FROM t");
    }
}
