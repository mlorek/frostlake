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
 * An ALL beside an INTERSECT, an EXCEPT or a MINUS is refused as an unsupported feature wherever the operation
 * stands — in a query, under EXPLAIN and inside a GENERATOR bound, before the bound is judged — and UNION takes a
 * DISTINCT, which INTERSECT and EXCEPT do not (live-verified).
 */
public class SetOperatorAllTest extends MeasuredCellsTestSupport {

    @Test
    public void anAllOnIntersectOrExceptIsUnsupportedAndUnionTakesDistinct() {
        engine.execute("CREATE OR REPLACE TABLE s905 (a INT)");
        engine.execute("INSERT INTO s905 VALUES (1), (1), (2)");
        assertCells(new String[][] {
            {"SELECT a FROM s905 INTERSECT ALL SELECT a FROM s905", "REFUSED Unsupported feature 'INTERSECT ALL'."},
            {"SELECT a FROM s905 EXCEPT ALL SELECT 2", "REFUSED Unsupported feature 'MINUS ALL'."},
            {"SELECT a FROM s905 MINUS ALL SELECT 2", "REFUSED Unsupported feature 'MINUS ALL'."},
            {"SELECT COUNT(*) FROM (SELECT a FROM s905 INTERSECT ALL SELECT 1)",
                "REFUSED Unsupported feature 'INTERSECT ALL'."},
            {"SELECT a FROM s905 INTERSECT DISTINCT SELECT a FROM s905",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'DISTINCT'."},
            {"SELECT a FROM s905 EXCEPT DISTINCT SELECT 2",
                "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'DISTINCT'."},
            {"SELECT a FROM s905 UNION DISTINCT SELECT 2", "1 ;; 2"},
            {"EXPLAIN SELECT a FROM s905 INTERSECT ALL SELECT 1", "REFUSED Unsupported feature 'INTERSECT ALL'."},
            {"SELECT 1 INTERSECT ALL SELECT 1", "REFUSED Unsupported feature 'INTERSECT ALL'."},
        });
    }

    @Test
    public void aGeneratorBoundOverSuchAnOperation() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t5 (a INT)");
        engine.execute("INSERT INTO t5 VALUES (7), (7), (7)");
        engine.execute("CREATE OR REPLACE TABLE tw (a INT, b INT)");
        engine.execute("INSERT INTO tw VALUES (1, 10), (2, 20)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 "
                + "INTERSECT ALL SELECT a FROM t3))))",
                "REFUSED Unsupported feature 'INTERSECT ALL'."},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 EXCEPT "
                + "ALL SELECT a FROM t3))))",
                "REFUSED Unsupported feature 'MINUS ALL'."},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 UNION "
                + "SELECT a FROM t3 UNION ALL SELECT a FROM t2))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 "
                + "UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 GROUP BY A) OB$2) WB$2 UNION ALL (SELECT null "
                + "AS \"CNULL\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$3) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1.5 INTERSECT "
                + "SELECT 1))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1.5 AS \"1.5\" FROM (SELECT 1.5 AS \"1.5\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"1.5\" = WB$1.\"1\") GROUP BY 1.5) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MIN(a) FROM (SELECT a FROM t1 UNION ALL "
                + "SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MIN(\"values\".A) AS \"MIN(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" FROM "
                + "(SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) "
                + "values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(a) FROM (SELECT a FROM t1 UNION "
                + "ALL SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(\"values\".A) AS \"COUNT(A)\" FROM (SELECT SET_COMBINE(WB$0.A, WB$1.A) AS \"A\" "
                + "FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) "
                + "WB$1) values)'"},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(a) FROM (SELECT a FROM t1 UNION "
                + "SELECT a FROM t3))))",
                "REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT SUM(CAST(\"values\".A AS NUMBER(38,0))) AS \"SUM(A)\" FROM (SELECT SET_COMBINE(WB$0.A, "
                + "WB$1.A) AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" "
                + "FROM T3 AS T3) WB$1 GROUP BY A) values)'"},
        });
    }
}
