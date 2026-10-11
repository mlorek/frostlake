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
 * A subquery of several columns given to a function is the ROW of its items, refused in the words of the
 * function's family: most as argument types the function does not take, at the call - an aggregate under the
 * name it is planned as - the constructors, MIN, MAX and MODE as a type they do not support, MEDIAN as
 * incompatible types, a conversion as an invalid type with the subquery printed from its plan, and a CASE as a
 * result that converts to nothing. The same holds in a WHERE, a HAVING, a QUALIFY and an ORDER BY, under a
 * sign and inside an aggregate's own argument. Every answer is live-verified.
 */
public class SubqueryRowArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void anAggregatesArgument() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_AGG': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ARRAY_AGG((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_AGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT ARRAY_AGG((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH_AGG': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT HASH_AGG((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH_AGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT HASH_AGG((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT COUNT((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT COUNT((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(1,0), NUMBER(1,0)) "
                + "argument type",
            "SELECT MAX((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(38,0), NUMBER(38,0)) "
                + "argument type",
            "SELECT MAX((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MIN does not support ROW(NUMBER(1,0), NUMBER(1,0)) "
                + "argument type",
            "SELECT MIN((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MIN does not support ROW(NUMBER(38,0), NUMBER(38,0)) "
                + "argument type",
            "SELECT MIN((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT SUM((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT SUM((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT AVG((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SUM': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT AVG((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT ANY_VALUE((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 17 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT ANY_VALUE((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LISTAGG': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT LISTAGG((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LISTAGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT LISTAGG((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HLL_ACCUMULATE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT APPROX_COUNT_DISTINCT((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HLL_ACCUMULATE': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT APPROX_COUNT_DISTINCT((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [ROW(NUMBER(1,0), NUMBER(1,0))] and [NUMBER(9,"
                + "0)]",
            "SELECT MEDIAN((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | incompatible types: [ROW(NUMBER(38,0), NUMBER(38,0))] and [NUMBER(9,"
                + "0)]",
            "SELECT MEDIAN((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT STDDEV((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT STDDEV((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_BOOLEAN'",
            "SELECT BOOLAND_AGG((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G LIMIT 1 OFFSET 0))] for parameter 'TO_BOOLEAN'",
            "SELECT BOOLAND_AGG((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT BITAND_AGG((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND_AGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT BITAND_AGG((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_UNIQUE_AGG': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ARRAY_UNIQUE_AGG((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_UNIQUE_AGG': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT ARRAY_UNIQUE_AGG((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MODE does not support ROW(NUMBER(1,0), NUMBER(1,0)) "
                + "argument type",
            "SELECT MODE((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MODE does not support ROW(NUMBER(38,0), NUMBER(38,0)) "
                + "argument type",
            "SELECT MODE((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT_IF((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (ROW(NUMBER(38,0), NUMBER(38,0)), NUMBER(1,0), NUMBER(1,0))",
            "SELECT COUNT_IF((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT VARIANCE((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT VARIANCE((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'APPROX_PERCENTILE_ACCUMULATE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT APPROX_PERCENTILE((SELECT 1, 2), 0.5) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Invalid argument types for function "
                + "'APPROX_PERCENTILE_ACCUMULATE': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT APPROX_PERCENTILE((SELECT g.id, g.v FROM g LIMIT 1), 0.5) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_AGG': (VARCHAR(1), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT OBJECT_AGG('k', (SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'OBJECT_AGG': (VARCHAR(1), ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT OBJECT_AGG('k', (SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT COUNT((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(1,0), NUMBER(1,0)) "
                + "argument type",
            "SELECT MAX((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_AGG': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ARRAY_AGG((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT COUNT(DISTINCT (SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT COUNT(DISTINCT (SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(1,0), NUMBER(1,0)) "
                + "argument type",
            "SELECT MAX((SELECT 1, 2)) OVER () FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT COUNT((SELECT 1, 2)) OVER () FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(1,0), NUMBER(1,0)) "
                + "argument type",
            "SELECT id FROM g GROUP BY id HAVING MAX((SELECT 1, 2)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT id FROM g GROUP BY id HAVING COUNT((SELECT 1, 2)) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MIN_BY': (NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT MIN_BY(id, (SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MAX_BY': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(38,0))",
            "SELECT MAX_BY((SELECT 1, 2), id) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LISTAGG': (VARCHAR(1), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT LISTAGG('a', (SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT COUNT((SELECT 1, 2), 1) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for function "
                + "'+': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT SUM((SELECT 1, 2) + 1) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'ABS': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT MAX(ABS((SELECT 1, 2))) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(1,0), NUMBER(1,0), "
                + "NUMBER(1,0)) argument type",
            "SELECT MAX((SELECT 1, 2, 3)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'COUNT': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT COUNT((SELECT * FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(38,0), NUMBER(38,0)) "
                + "argument type",
            "SELECT MAX((SELECT * FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(VARCHAR(1), NUMBER(1,0)) argument "
                + "type",
            "SELECT MAX((SELECT 'a', 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NULL, NUMBER(1,0)) argument type",
            "SELECT MAX((SELECT NULL, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function MAX does not support ROW(NUMBER(2,1), BOOLEAN) argument "
                + "type",
            "SELECT MAX((SELECT 1.5, TRUE)) FROM g");
    }

    @Test
    public void aScalarFunctionsArgument() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ABS': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ABS((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT HASH((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT ARRAY_CONSTRUCT((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARIANT((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_VARIANT'",
            "SELECT TO_VARIANT((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT SYSTEM$TYPEOF((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_NUMBER((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL))] for parameter 'TO_NUMBER'",
            "SELECT TO_NUMBER((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL))] for parameter 'TO_DATE'",
            "SELECT TO_DATE((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_BOOLEAN'",
            "SELECT TO_BOOLEAN((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TO_JSON': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT TO_JSON((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'UPPER': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT UPPER((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'LENGTH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT LENGTH((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_SIZE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ARRAY_SIZE((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'TYPEOF': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT TYPEOF((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT IS_NULL_VALUE((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_ARRAY((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL))] for parameter 'TO_ARRAY'",
            "SELECT TO_ARRAY((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_OBJECT((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL))] for parameter 'TO_OBJECT'",
            "SELECT TO_OBJECT((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DOUBLE((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL))] for parameter 'TO_DOUBLE'",
            "SELECT TO_DOUBLE((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_CHAR((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL))] for parameter 'TO_CHAR'",
            "SELECT TO_CHAR((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_TIMESTAMP_NTZ'",
            "SELECT TO_TIMESTAMP((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TRY_TO_NUMBER((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_NUMBER'",
            "SELECT TRY_TO_NUMBER((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BINARY((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL))] for parameter 'TO_BINARY'",
            "SELECT TO_BINARY((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_TIME((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL))] for parameter 'TO_TIME'",
            "SELECT TO_TIME((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'PARSE_JSON': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT PARSE_JSON((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SQRT': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT SQRT((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function OBJECT_CONSTRUCT_KEEP_NULL does not support ROW(NUMBER(1,"
                + "0), NUMBER(1,0)) argument type",
            "SELECT OBJECT_CONSTRUCT_KEEP_NULL('k', (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT_COMPACT does not support ROW(NUMBER(1,0), "
                + "NUMBER(1,0)) argument type",
            "SELECT ARRAY_CONSTRUCT_COMPACT((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT COALESCE((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT COALESCE(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NVL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT NVL((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NVL': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT NVL(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GREATEST': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT GREATEST((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'GREATEST': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT GREATEST(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONCAT': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT CONCAT((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'CONCAT': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT CONCAT(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NULLIF': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT NULLIF((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'NULLIF': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT NULLIF(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'EQUAL_NULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT EQUAL_NULL((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'EQUAL_NULL': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT EQUAL_NULL(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT IFNULL((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFNULL': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT IFNULL(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[DECODE((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES (null)) DUAL), 1)], expected 3, got 2",
            "SELECT DECODE((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[DECODE(1, (SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES (null)) DUAL))], expected 3, got 2",
            "SELECT DECODE(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ZEROIFNULL': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ZEROIFNULL((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'MD5': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT MD5((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'SHA2': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT SHA2((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT BITAND((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'BITAND': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT BITAND(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ROUND': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT ROUND((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ROUND': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ROUND(1, (SELECT 1, 2))");
    }

    @Test
    public void inEveryClause() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT id FROM fz WHERE HASH((SELECT g.id, g.v FROM g LIMIT 1)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT id FROM fz WHERE HASH((SELECT 1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'ABS': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT id FROM fz WHERE ABS((SELECT 1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'ABS': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT id FROM fz WHERE ABS((SELECT g.id, g.v FROM g LIMIT 1)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARIANT((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_VARIANT'",
            "SELECT id FROM g WHERE TO_VARIANT((SELECT 1, 2)) IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G LIMIT 1 OFFSET 0))] for parameter 'TO_VARCHAR'",
            "SELECT id FROM fz WHERE TO_VARCHAR((SELECT g.id, g.v FROM g LIMIT 1)) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_VARCHAR'",
            "SELECT id FROM fz WHERE TO_VARCHAR((SELECT 1, 2)) = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT id FROM g ORDER BY HASH((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT id FROM g ORDER BY HASH((SELECT g.id, g.v FROM g LIMIT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_VARCHAR'",
            "SELECT id FROM g ORDER BY TO_VARCHAR((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT id FROM g ORDER BY ARRAY_CONSTRUCT((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT id FROM g GROUP BY id, HASH((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 36 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT id FROM g GROUP BY id HAVING HASH((SELECT 1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT id FROM g QUALIFY HASH((SELECT 1, 2)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT HASH((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ABS': (ROW(NUMBER(38,0), NUMBER(38,0)))",
            "SELECT ABS((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G LIMIT 1 OFFSET 0))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARIANT((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G LIMIT 1 OFFSET 0))] for parameter 'TO_VARIANT'",
            "SELECT TO_VARIANT((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(38,0), "
                + "NUMBER(38,0)) argument type",
            "SELECT ARRAY_CONSTRUCT((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT SYSTEM$TYPEOF((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT ARRAY_CONSTRUCT(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT ARRAY_CONSTRUCT((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function OBJECT_CONSTRUCT does not support ROW(NUMBER(1,0), "
                + "NUMBER(1,0)) argument type",
            "SELECT OBJECT_CONSTRUCT('a', (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT HASH(1, (SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT HASH((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'NEGATE': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ABS(-(SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | Invalid argument types for function "
                + "'+': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT ABS((SELECT 1, 2) + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ABS': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ABS(((SELECT 1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 11 | Invalid argument types for function "
                + "'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ABS(HASH((SELECT 1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 12 | Invalid argument types for function "
                + "'ABS': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT HASH(ABS((SELECT 1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT (SELECT 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT (SELECT g.id, g.v FROM g LIMIT 1) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for function "
                + "'+': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (SELECT 1, 2) + 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT (SELECT 1, 2) = (SELECT 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Invalid argument types for function 'IS "
                + "NULL': (ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT (SELECT 1, 2) IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL) AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT (SELECT 1, 2)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [CAST((SELECT 1 AS \"1\", 2 AS \"2\" FROM (VALUES "
                + "(NULL)) DUAL) AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'",
            "SELECT CAST((SELECT 1, 2) AS VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (BOOLEAN, ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT IFF(TRUE, (SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter '(SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (null)) DUAL)' of type [ROW(NUMBER(1,0), NUMBER(1,0))] into expected type [ANY]",
            "SELECT CASE WHEN TRUE THEN (SELECT 1, 2) END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT SYSTEM$TYPEOF((SELECT 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT SYSTEM$TYPEOF((SELECT 1, 2, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT 1 AS \"1\", 2 AS \"2\", 3 AS \"3\" "
                + "FROM (VALUES (NULL)) DUAL))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT 1, 2, 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G LIMIT 1 OFFSET 0))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT * FROM g LIMIT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT 'a' AS \"'A'\", TRUE AS \"TRUE\" "
                + "FROM (VALUES (NULL)) DUAL))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT 'a', TRUE))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARIANT((SELECT G.ID AS \"X\", G.V AS \"Y\" FROM G "
                + "AS G LIMIT 1 OFFSET 0))] for parameter 'TO_VARIANT'",
            "SELECT TO_VARIANT((SELECT g.id AS x, g.v AS y FROM g LIMIT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G  ORDER BY ID ASC NULLS LAST LIMIT 1 OFFSET 0))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT id, v FROM g ORDER BY id LIMIT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G WHERE G.ID = 5))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT id, v FROM g WHERE id = 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT MAX(G.ID) AS \"MAX(ID)\", MIN(G.V) "
                + "AS \"MIN(V)\" FROM G AS G))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT MAX(id), MIN(v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G WHERE G.ID = CORRELATION(FZ.ID)))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT g.id, g.v FROM g WHERE g.id = fz.id)) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT DISTINCT G.ID AS \"ID\", G.V AS "
                + "\"V\" FROM G AS G LIMIT 1 OFFSET 0))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT DISTINCT id, v FROM g LIMIT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT G.ID + 1 AS \"ID + 1\", G.V AS "
                + "\"V\" FROM G AS G LIMIT 1 OFFSET 0))] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT id + 1, v FROM g LIMIT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARIANT((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL))] for parameter 'TO_VARIANT'",
            "SELECT TO_VARIANT((SELECT 1, 2)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_NUMBER((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM G "
                + "AS G LIMIT 1 OFFSET 0))] for parameter 'TO_NUMBER'",
            "SELECT TO_NUMBER((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DATE((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM G "
                + "AS G LIMIT 1 OFFSET 0))] for parameter 'TO_DATE'",
            "SELECT TO_DATE((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_CHAR((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM G "
                + "AS G LIMIT 1 OFFSET 0))] for parameter 'TO_CHAR'",
            "SELECT TO_CHAR((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_DOUBLE((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM G "
                + "AS G LIMIT 1 OFFSET 0))] for parameter 'TO_DOUBLE'",
            "SELECT TO_DOUBLE((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_BOOLEAN((SELECT G.ID AS \"ID\", G.V AS \"V\" FROM "
                + "G AS G LIMIT 1 OFFSET 0))] for parameter 'TO_BOOLEAN'",
            "SELECT TO_BOOLEAN((SELECT g.id, g.v FROM g LIMIT 1)) FROM g");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TO_VARCHAR((SELECT 1 AS \"1\", 2 AS \"2\" FROM "
                + "(VALUES (NULL)) DUAL), 1)] for parameter 'TO_VARCHAR'",
            "SELECT TO_VARCHAR((SELECT 1, 2), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | Format argument for function 'TO_VARCHAR' needs to be a string",
            "SELECT TO_VARCHAR(1, (SELECT 1, 2))");
    }
}
