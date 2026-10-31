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
 * A subquery whose one select item is a ROW is judged by what reads it: an EXISTS over a body that reads no
 * relation and filters nothing answers TRUE, a comparison, IN, ANY and an operator refuse the ROW in their
 * argument-type words, a scalar subquery is refused at its own SELECT, an INSERT judges the source's width
 * and the ROW's type first, and a view's unnamed ROW is a missing column specification (all live-verified).
 */
public class RowItemSubqueryReadersTest extends MeasuredCellsTestSupport {

    @Test
    public void rowItemReaders() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-01'), (2, 'y', '2024-02-01')");
        engine.execute("CREATE OR REPLACE TABLE t1c (a INT)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT, b VARCHAR)");
        assertAnswer("true", "SELECT EXISTS (SELECT (1, 2))");
        assertAnswer("true", "SELECT EXISTS (SELECT (1, 2), 3)");
        assertAnswer("true", "SELECT EXISTS (SELECT (1, 2) AS r)");
        assertAnswer("1 ;; 2", "SELECT a FROM t WHERE EXISTS (SELECT (1, 2)) ORDER BY a");
        assertAnswer("1 ;; 2", "SELECT a FROM t WHERE EXISTS (SELECT (t.a, 2)) ORDER BY a");
        assertAnswer("y", "SELECT CASE WHEN EXISTS (SELECT (1, 2)) THEN 'y' END");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 22 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT EXISTS (SELECT (1, 2) FROM t)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 22 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT EXISTS (SELECT (1, 2) WHERE 1 = 0)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for "
                + "function '=': (NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT a FROM t WHERE a = (SELECT (1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for "
                + "function '>': (NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT a FROM t WHERE a > (SELECT (1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for "
                + "function '=': (NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT a FROM t WHERE a = ANY (SELECT (1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 24 | Invalid argument types for "
                + "function '=': (NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT a FROM t WHERE a IN (SELECT (1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 9 | Invalid argument types for function "
                + "'+': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))",
            "SELECT 1 + (SELECT (1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 8 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT (SELECT (1, 2))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 8 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT (SELECT (1, 2) AS r)");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column A",
            "INSERT INTO t1c SELECT (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column A",
            "INSERT INTO t1c (a) SELECT (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(NUMBER(38,0), NUMBER(38,0)) for column A",
            "INSERT INTO t1c SELECT (a, a) FROM t");
        assertAnswer("REFUSED SQL compilation error: | Insert value list does not match column list expecting 2 but "
                + "got 1",
            "INSERT INTO t2 SELECT (1, 2)");
        assertAnswer("REFUSED SQL compilation error: | Missing column specification",
            "CREATE OR REPLACE VIEW vr2 AS SELECT (1, 2)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 32 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT * FROM (SELECT (1, 2) AS r)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | Unsupported: SELECT list element "
                + "with multiple columns.",
            "SELECT (1, 2) UNION ALL SELECT (3, 4)");
    }
}
