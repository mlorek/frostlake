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
 * ORDER BY and GROUP BY read a ROW as its values, one by one: ORDER BY (b, a) DESC orders by b DESC, a DESC,
 * a number among the values is an ordinal as it is on its own, and a row inside a row is read the same way. A
 * condition that is a row is refused by its type, in WHERE, HAVING, QUALIFY and ON; a VALUES clause holds none;
 * an array or an object literal refuses one as its constructor does. A row compares with the rows of a query
 * under ANY and ALL, a NULL on either side leaving the pair unequal. Every answer is live-verified.
 */
public class RowValueClauseTest extends MeasuredCellsTestSupport {

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
    public void aRowInAClause() {
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t ORDER BY (a, b)");
        assertAnswer(
            "null|z ;; 3|x ;; 2|null ;; 2|x ;; 1|y ;; 1|x",
            "SELECT a, b FROM t ORDER BY (a, b) DESC");
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, a)");
        assertAnswer(
            "2|null ;; null|z ;; 1|y ;; 3|x ;; 2|x ;; 1|x",
            "SELECT a, b FROM t ORDER BY (b, a) DESC");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t ORDER BY (a), (b)");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t ORDER BY (a, b), d");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t ORDER BY (1, 2)");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t ORDER BY ((a, b))");
        assertAnswer(
            "2|null ;; 1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z",
            "SELECT a, b FROM t ORDER BY (b, a) NULLS FIRST");
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, a) ASC NULLS LAST");
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY b, (a, b)");
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, a, d)");
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, (a, d))");
        assertAnswer(
            "1",
            "SELECT a FROM t ORDER BY (a, b) LIMIT 1");
        assertAnswer(
            "3|x ;; 2|x ;; 1|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, -a)");
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (2, 1)");
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, 1)");
        assertAnswer(
            "2|x ;; 3|x ;; 1|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, NULL)");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (a, b) ORDER BY 1, 2");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (a, b), d ORDER BY 1, 2");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY ((a, b)) ORDER BY 1, 2");
        assertAnswer(
            "1|2 ;; 2|2 ;; 3|1 ;; null|1",
            "SELECT a, COUNT(*) FROM t GROUP BY (a) ORDER BY 1");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY a, (b) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | [2] is not a valid group by expression",
            "SELECT COUNT(*) FROM t GROUP BY (1, 2)");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY GROUPING SETS ((a, b)) ORDER BY 1, 2");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (a, b, d) ORDER BY 1, 2");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (b, a) ORDER BY 1, 2");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (a, b) HAVING COUNT(*) > 0 ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(38,0), VARCHAR(16777216))] for "
                + "predicate [ROW(T.A, T.B)]",
            "SELECT a FROM t WHERE (a, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] for predicate "
                + "[ROW(1, 2)]",
            "SELECT a FROM t WHERE (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] for predicate "
                + "[ROW(1, 2)]",
            "SELECT COUNT(*) FROM t HAVING (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] for predicate "
                + "[ROW(1, 2)]",
            "SELECT a FROM t QUALIFY (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] for predicate "
                + "[ROW(1, 2)]",
            "SELECT t.a FROM t JOIN t u ON (1, 2)");
        assertAnswer(
            "1",
            "SELECT a FROM t WHERE (a, b) = (1, 'x') ORDER BY a");
        assertAnswer(
            "1",
            "SELECT a FROM t WHERE (a, b) IN ((1, 'x')) ORDER BY a");
        assertAnswer(
            "1 ;; 2 ;; 2 ;; 3",
            "SELECT a FROM t WHERE (a, b) > (1, 'x') ORDER BY a, b");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | Invalid argument types for function "
                + "'NOT': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT a FROM t WHERE NOT (a, b)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function "
                + "'AND': (ROW(NUMBER(38,0), VARCHAR(16777216)), BOOLEAN)",
            "SELECT a FROM t WHERE (a, b) AND a > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'IN': (NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT a FROM t WHERE a IN ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function 'IS "
                + "NOT NULL': (ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT a FROM t WHERE (a, b) IS NOT NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [ANY]",
            "SELECT CASE WHEN TRUE THEN (1, 2) END");
        assertAnswer(
            "1",
            "SELECT CASE (1, 2) WHEN (1, 2) THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [BOOLEAN]",
            "SELECT CASE WHEN (1, 2) THEN 1 END");
        assertAnswer(
            "1",
            "SELECT CASE WHEN (1, 2) = (1, 2) THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [NUMBER(1,0)]",
            "SELECT CASE WHEN TRUE THEN 1 ELSE (1, 2) END");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0))] into expected type [NUMBER(1,0)]",
            "SELECT CASE 1 WHEN (1, 2) THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function '=': "
                + "(ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT CASE (1, 2) WHEN 1 THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT * FROM (VALUES ((1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT * FROM (VALUES ((1, 2), 3))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] in VALUES clause",
            "SELECT * FROM (VALUES (1, 2), ((3, 4), 5))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT * FROM VALUES ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 8 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT (SELECT 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT * FROM (SELECT (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT * FROM (SELECT (1, 2) AS r)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "WITH c AS (SELECT (1, 2)) SELECT * FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator input "
                + "branches, expected ROW(NUMBER(1,0), NUMBER(1,0)), got NUMBER(38,0)",
            "SELECT a FROM t UNION SELECT (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Function ARRAY_CONSTRUCT does not support ROW(NUMBER(1,0), NUMBER(1,"
                + "0)) argument type",
            "SELECT [(1, 2)]");
        assertAnswer(
            "REFUSED SQL compilation error: | Function OBJECT_CONSTRUCT does not support ROW(NUMBER(1,0), "
                + "NUMBER(1,0)) argument type",
            "SELECT {'k': (1, 2)}");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | Invalid argument types for function "
                + "'GET': (ARRAY, ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT ARRAY_CONSTRUCT(1, 2)[(0, 1)]");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '('. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT IDENTIFIER((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [ROW(NUMBER(1,0), NUMBER(1,0))] for parameter 'INPUT'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => (1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (1, 2) FROM t GROUP BY 1");
        assertAnswer(
            "1 ;; 1 ;; 2 ;; 2 ;; 3 ;; null",
            "SELECT a FROM t ORDER BY (a, b), (b, a)");
        assertAnswer(
            "1 ;; 2 ;; 3 ;; null",
            "SELECT a FROM t GROUP BY a ORDER BY (a, a)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 43 unexpected '<EOF>'.",
            "SELECT a, b FROM t ORDER BY (a, b) OFFSET 1");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM (SELECT a, b FROM t ORDER BY (b, a)) ORDER BY (a, b)");
    }

    @Test
    public void theClausesMeasuredSecond() {
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY ((b), a)");
        assertAnswer(
            "null|z ;; 1|y ;; 3|x ;; 2|x ;; 1|x ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, a) DESC NULLS LAST");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t ORDER BY 1, (2, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | [5] is not a valid order by expression",
            "SELECT a, b FROM t ORDER BY (1, 5)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'X'",
            "SELECT a, b FROM t ORDER BY (a, x)");
        assertAnswer(
            "REFUSED SQL compilation error: | [0] is not a valid order by expression",
            "SELECT a, b FROM t ORDER BY (0, 1)");
        assertAnswer(
            "1|x|4 ;; 1|y|1 ;; 2|x|2 ;; 2|null|6 ;; 3|x|3 ;; null|z|5",
            "SELECT a, b, ROW_NUMBER() OVER (ORDER BY (1, 2)) FROM t ORDER BY a, b");
        assertAnswer(
            "2|null|1 ;; null|z|2 ;; 1|y|3 ;; 3|x|4 ;; 2|x|5 ;; 1|x|6",
            "SELECT a, b, ROW_NUMBER() OVER (ORDER BY (b, a) DESC) r FROM t ORDER BY r");
        assertAnswer(
            "2|null|1 ;; 1|x|2 ;; 2|x|3 ;; 3|x|4 ;; 1|y|5 ;; null|z|6",
            "SELECT a, b, ROW_NUMBER() OVER (ORDER BY (b, a) NULLS FIRST) r FROM t ORDER BY r");
        assertAnswer(
            "1|x|1 ;; 2|x|2 ;; 3|x|3 ;; 1|y|1 ;; null|z|1 ;; 2|null|1",
            "SELECT a, b, ROW_NUMBER() OVER (PARTITION BY (b) ORDER BY (a, d)) r FROM t ORDER BY b, r");
        assertAnswer(
            "xyxxz",
            "SELECT LISTAGG(b) WITHIN GROUP (ORDER BY (a, b)) FROM t");
        assertAnswer(
            "[1,2,3,1,2]",
            "SELECT ARRAY_AGG(a) WITHIN GROUP (ORDER BY (b, a)) FROM t");
        assertAnswer(
            "1 ;; 2 ;; 3 ;; null",
            "SELECT DISTINCT a FROM t ORDER BY (a, a)");
        assertAnswer(
            "1 ;; 1 ;; 1 ;; 1 ;; 2 ;; 2 ;; 2 ;; 2 ;; 3 ;; 3 ;; null ;; null",
            "SELECT a FROM t UNION ALL SELECT a FROM t ORDER BY (1, 1)");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t ORDER BY (a + 1, b || 'q')");
        assertAnswer(
            "1|x ;; 2|x",
            "SELECT a, b FROM t ORDER BY (b, a) LIMIT 2");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a AS z, b FROM t ORDER BY (z, b)");
        assertAnswer(
            "1|x ;; 2|x ;; 3|x ;; 1|y ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b, a), (a)");
        assertAnswer(
            "1|y ;; 1|x ;; 2|x ;; 3|x ;; null|z ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b IS NULL, a)");
        assertAnswer(
            "1|y ;; null|z ;; 1|x ;; 2|x ;; 3|x ;; 2|null",
            "SELECT a, b FROM t ORDER BY (b = 'x', a)");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (a, b), (a) ORDER BY 1, 2");
        assertAnswer(
            "1|x|2024-01-01|1 ;; 1|y|2024-01-04|1 ;; 2|x|2024-01-03|1 ;; 2|null|2024-01-05|1 ;; 3|x|2024-01-02|1 "
                + ";; null|z|null|1",
            "SELECT a, b, d, COUNT(*) FROM t GROUP BY (a, (b, d)) ORDER BY 1, 2");
        assertAnswer(
            "2|1 ;; 2|1 ;; 3|1 ;; 3|1 ;; 4|1 ;; null|1",
            "SELECT a + 1, COUNT(*) FROM t GROUP BY (a + 1, b) ORDER BY 1");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (1, 2) ORDER BY 1, 2");
        assertAnswer(
            "1|x|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (a, b) HAVING (a, b) = (1, 'x')");
        assertAnswer(
            "1|1 ;; 1|1 ;; 2|1 ;; 2|1 ;; 3|1 ;; null|1",
            "SELECT a, COUNT(*) FROM t GROUP BY (a, b) ORDER BY 1");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (a, 2) ORDER BY 1, 2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 42 | invalid identifier 'X'",
            "SELECT a, b, COUNT(*) FROM t GROUP BY (a, x)");
        assertAnswer(
            "1|x|1 ;; 1|y|1 ;; 2|x|1 ;; 2|null|1 ;; 3|x|1 ;; null|z|1",
            "SELECT a, b, COUNT(*) FROM t GROUP BY a, (b, a) ORDER BY 1, 2");
        assertAnswer(
            "x|3 ;; y|1 ;; z|1 ;; null|1",
            "SELECT b, COUNT(*) FROM t GROUP BY ((b)) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function "
                + "'OR': (ROW(NUMBER(38,0), VARCHAR(16777216)), BOOLEAN)",
            "SELECT a FROM t WHERE (a, b) OR TRUE");
        assertAnswer(
            "1 ;; 2 ;; 2 ;; 3 ;; null",
            "SELECT a FROM t WHERE NOT ((a, b) = (1, 'x')) ORDER BY a, b");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(38,0), VARCHAR(16777216))] for "
                + "predicate [ROW(T.A, T.B)]",
            "SELECT a FROM t WHERE ((a, b))");
        assertAnswer(
            "1 ;; 1",
            "SELECT a FROM t WHERE (a, b) IN (SELECT a, b FROM t WHERE a = 1) ORDER BY a, b");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause.",
            "SELECT a FROM t WHERE (a, b) = (SELECT 1, 'x')");
        assertAnswer(
            "1 ;; 1 ;; 2 ;; 3",
            "SELECT t.a FROM t JOIN t u ON (t.a, t.b) = (u.a, u.b) ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for function "
                + "'IN': (NUMBER(38,0), NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT a FROM t WHERE a IN (1, (2, 3))");
        assertAnswer(
            "1 ;; 2",
            "SELECT a FROM t WHERE (a, b) IN ((1, 'x'), (2, 'x')) ORDER BY a");
        assertAnswer(
            "1 ;; 2 ;; 2 ;; 3 ;; null",
            "SELECT a FROM t WHERE (a, b) NOT IN ((1, 'x')) ORDER BY a, b");
        assertAnswer(
            "1 ;; 1 ;; 2 ;; 2",
            "SELECT a FROM t WHERE a IN ((1), (2)) ORDER BY a");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT (t.a, t.b) FROM t");
        assertAnswer(
            "3",
            "SELECT a FROM t WHERE (a, b) = ANY (SELECT a, b FROM t WHERE a = 3)");
        assertAnswer(
            "1 ;; 1 ;; 2",
            "SELECT a FROM t WHERE (a, b) < (2, 'y') ORDER BY a, b");
        assertAnswer(
            "1 ;; 2 ;; 2 ;; 3 ;; null",
            "SELECT a FROM t WHERE (a, b) <> (1, 'x') ORDER BY a, b");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM t WHERE ((a, b)) = ((1, 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] for predicate "
                + "[ROW(1, 2)]",
            "SELECT a FROM t QUALIFY (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(18,0), NUMBER(1,0))] for predicate "
                + "[ROW(ROW_NUMBER() OVER (ORDER BY T.A ASC NULLS LAST), 1)]",
            "SELECT a FROM t QUALIFY (ROW_NUMBER() OVER (ORDER BY a), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | Invalid argument types for function "
                + "'AND': (ROW(NUMBER(38,0), VARCHAR(16777216)), ROW(NUMBER(38,0), VARCHAR(16777216)))",
            "SELECT a FROM t WHERE (a, b) AND (a, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(38,0), VARCHAR(16777216))] for "
                + "predicate [ROW(T.A, T.B)]",
            "SELECT a FROM t HAVING (a, b)");
        assertAnswer(
            "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(ANY) for column A",
            "INSERT INTO t2 VALUES ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(ANY) for column A",
            "INSERT INTO t2 VALUES ((1, 2)), ((3, 4))");
        assertAnswer(
            "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(ANY) for column A",
            "INSERT INTO t2 (a) VALUES ((1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column A",
            "UPDATE t2 SET a = (1, 2) WHERE FALSE");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(38,0), NUMBER(38,0))] for predicate "
                + "[ROW(T2.A, T2.A)]",
            "UPDATE t2 SET a = 1 WHERE (a, a)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(38,0), NUMBER(38,0))] for predicate "
                + "[ROW(T2.A, T2.A)]",
            "DELETE FROM t2 WHERE (a, a)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 47 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "CREATE OR REPLACE TABLE tr AS SELECT (1, 2) AS r");
        assertAnswer(
            "0",
            "SELECT COUNT(*) FROM t2");
        assertAnswer(
            "REFUSED SQL compilation error: error line 0 at position -1 | Unsupported: SELECT list element with "
                + "multiple columns.",
            "SELECT * FROM (VALUES (1, (2, 3)))");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] in VALUES clause",
            "SELECT * FROM (VALUES (1), ((2, 3)))");
        assertAnswer(
            "[true]",
            "SELECT ARRAY_CONSTRUCT((1, 2) = (1, 2))");
        assertAnswer(
            "[true]",
            "SELECT [(1, 2) = (1, 2)]");
        assertAnswer(
            "1",
            "SELECT a FROM t ORDER BY (a, b) FETCH FIRST 1 ROWS ONLY");
        assertAnswer(
            "2|null ;; null|z ;; 1|y ;; 3|x ;; 2|x ;; 1|x",
            "SELECT a, b FROM t ORDER BY (b, a) DESC, (a, b) ASC");
        assertAnswer(
            "1|x ;; 1|y ;; 2|x ;; 2|null ;; 3|x ;; null|z",
            "SELECT a, b FROM t GROUP BY (a, b) ORDER BY (a, b)");
    }

    @Test
    public void aRowAgainstTheRowsOfAQuery() {
        assertAnswer(
            "true",
            "SELECT (1, 2) = ALL (SELECT 1, 2)");
        assertAnswer(
            "false",
            "SELECT (1, 2) = ALL (SELECT 1, 3)");
        assertAnswer(
            "true",
            "SELECT (1, 2) <> ALL (SELECT 1, 3)");
        assertAnswer(
            "false",
            "SELECT (1, 2) <> ANY (SELECT 1, 2)");
        assertAnswer(
            "true",
            "SELECT (1, 2) < ANY (SELECT 1, 3)");
        assertAnswer(
            "false",
            "SELECT (1, 2) < ALL (SELECT a, 1 FROM t WHERE a IS NOT NULL)");
        assertAnswer(
            "true",
            "SELECT (1, 2) >= ALL (SELECT 1, 2)");
        assertAnswer(
            "false",
            "SELECT (1, NULL) = ANY (SELECT 1, 2)");
        assertAnswer(
            "false",
            "SELECT (1, NULL) = ALL (SELECT 2, 2)");
        assertAnswer(
            "false",
            "SELECT (1, 2) = ANY (SELECT 1, 2 WHERE FALSE)");
        assertAnswer(
            "true",
            "SELECT (1, 2) = ALL (SELECT 1, 2 WHERE FALSE)");
        assertAnswer(
            "true",
            "SELECT (1, 2) = SOME (SELECT 1, 2)");
        assertAnswer(
            "1 ;; 2 ;; 3",
            "SELECT a FROM t WHERE (a, b) <> ALL (SELECT 1, 'x') ORDER BY a, b");
        assertAnswer(
            "1 ;; 2 ;; 3",
            "SELECT a FROM t WHERE (a, b) > ALL (SELECT 1, 'x') ORDER BY a, b");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ANY(SELECT 1 AS \"1\", 2 AS \"2\", 3 AS "
                + "\"3\" FROM (VALUES (null)) DUAL)' of type [ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))] into "
                + "expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            "SELECT (1, 2) = ANY (SELECT 1, 2, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ALL(SELECT 1 AS \"1\", 2 AS \"2\", 3 AS "
                + "\"3\" FROM (VALUES (null)) DUAL)' of type [ROW(NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))] into "
                + "expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            "SELECT (1, 2) = ALL (SELECT 1, 2, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'<': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) < ALL (SELECT 1)");
        assertAnswer(
            "true",
            "SELECT (1, 2) BETWEEN (1, 1) AND (1, 3)");
        assertAnswer(
            "false",
            "SELECT (1, 2) NOT BETWEEN (1, 1) AND (1, 3)");
        assertAnswer(
            "false",
            "SELECT (1, 2) BETWEEN (1, 3) AND (1, 1)");
        assertAnswer(
            "null",
            "SELECT (1, 2) BETWEEN (1, NULL) AND (1, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 14 | Invalid argument types for function "
                + "'<=': (ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT (1, 2) BETWEEN (1, 1) AND 3");
        assertAnswer(
            "1 ;; 1 ;; 2",
            "SELECT a FROM t WHERE (a, b) BETWEEN (1, 'x') AND (2, 'x') ORDER BY a, b");
        assertAnswer(
            "true",
            "SELECT ((1, 2)) IN (((1, 2)), ((3, 4)))");
        assertAnswer(
            "false",
            "SELECT ((1, 2)) NOT IN (((1, 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 16 | Invalid argument types for function "
                + "'IN': (ROW(NUMBER(1,0), NUMBER(1,0)), ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))",
            "SELECT ((1, 2)) IN (((1, 2)), 3)");
        assertAnswer(
            "true",
            "SELECT (1, 2) IS NOT DISTINCT FROM (1, 2)");
        assertAnswer(
            "false",
            "SELECT (1, NULL) IS DISTINCT FROM (1, NULL)");
        assertAnswer(
            "true",
            "SELECT EQUAL_NULL((1, NULL), (1, NULL))");
        assertAnswer(
            "x",
            "SELECT DECODE((1, 2), (1, 2), 'x', (3, 4), 'y', 'z')");
        assertAnswer(
            "y",
            "SELECT DECODE((3, 4), (1, 2), 'x', (3, 4), 'y', 'z')");
        assertAnswer(
            "z",
            "SELECT DECODE((5, 6), (1, 2), 'x', 'z')");
        assertAnswer(
            "REFUSED SQL compilation error: | Can not convert parameter 'ROW(1, 2, 3)' of type [ROW(NUMBER(1,0), "
                + "NUMBER(1,0), NUMBER(1,0))] into expected type [ROW(NUMBER(1,0), NUMBER(1,0))]",
            "SELECT DECODE((1, 2), (1, 2, 3), 'x')");
    }
}
