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
 * A GENERATOR bound over a set operation echoed as the plan holds it: an INTERSECT as a SEMI JOIN grouped by its
 * columns, a side of another type cast to the type both take; an EXCEPT as its left side grouped; a UNION's
 * columns combined by SET_COMBINE, one value they all hold standing for it; a column holding one value read as it;
 * nested operations under OB$ names, the operations beneath numbered first (all live-verified).
 */
public class GeneratorBoundSetOperationPlanTest extends MeasuredCellsTestSupport {

    @Test
    public void setOperations() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"1\" = WB$1.\"1\") GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT SELECT 1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"1\" = WB$1.\"2\") GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT SELECT 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 EXCEPT SELECT 1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 EXCEPT SELECT 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 2 AS \"2\" FROM (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"2\" = WB$1.\"2\") GROUP BY 2) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 2 INTERSECT SELECT 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"X\" FROM (SELECT 1 AS \"X\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.X = WB$1.\"1\") GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 AS x INTERSECT SELECT "
                + "1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(1) AS \"MAX(X)\" FROM (SELECT 1 AS \"X\" FROM (SELECT 1 AS \"X\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.X = WB$1.\"1\") GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 1 AS x INTERSECT SELECT "
                + "1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 'a' AS \"'A'\" FROM (SELECT 'a' AS \"'A'\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 'a' AS \"'A'\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"'A'\" = WB$1.\"'A'\") GROUP BY 'a') "
                + "values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 'a' INTERSECT SELECT "
                + "'a'))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\", 2 AS \"2\" FROM (SELECT 1 AS \"1\", 2 "
                + "AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1 AS \"1\", "
                + "2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON ((WB$0.\"1\" = "
                + "WB$1.\"1\") AND (WB$0.\"2\" = WB$1.\"2\")) GROUP BY 1, 2) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1, 2 INTERSECT SELECT 1, "
                + "2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 "
                + "AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI "
                + "JOIN (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"1\" "
                + "= WB$1.\"1\") GROUP BY 1) OB$2) WB$2 SEMI JOIN (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT "
                + "=> 1, rowCount => 1))) WB$3 ON (WB$2.\"1\" = WB$3.\"1\") GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT SELECT 1 "
                + "INTERSECT SELECT 1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT OB$2.\"1\" AS \"1\" FROM (SELECT SET_COMBINE(1, 2) "
                + "AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION "
                + "ALL (SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) OB$2 GROUP BY "
                + "1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION ALL SELECT 2 "
                + "EXCEPT SELECT 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 "
                + "AS T1) WB$0 SEMI JOIN (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 ON (WB$0.A = WB$1.A) GROUP BY A) "
                + "values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 INTERSECT "
                + "SELECT a FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 "
                + "AS T1) WB$0 GROUP BY A) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 EXCEPT SELECT "
                + "a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(WB$0.A, 4) AS \"A\" FROM (SELECT T1.A "
                + "AS \"A\" FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$1 GROUP BY A) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 UNION SELECT a "
                + "FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 4 AS \"A\" FROM (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 4 AS \"4\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.A = WB$1.\"4\") GROUP BY 4) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t2 INTERSECT "
                + "SELECT 4))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT CAST(T1.A AS NUMBER(38,0)) AS "
                + "\"A\" FROM T1 AS T1) WB$1 ON (WB$0.\"1\" = WB$1.A) GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT SELECT a "
                + "FROM t1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, 4) AS \"X\" FROM (SELECT "
                + "T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1,"
                + " rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 UNION ALL "
                + "SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(4, WB$1.A) AS \"X\" FROM (SELECT "
                + "4 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT T1.A AS "
                + "\"A\" FROM T1 AS T1) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t2 UNION ALL "
                + "SELECT a FROM t1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MIN(\"values\".X) AS \"MIN(X)\" FROM (SELECT SET_COMBINE(WB$0.X, 4) AS \"X\" FROM (SELECT "
                + "T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1,"
                + " rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MIN(x) FROM (SELECT a AS x FROM t1 UNION ALL "
                + "SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(\"values\".X) AS \"COUNT(X)\" FROM (SELECT SET_COMBINE(WB$0.X, 4) AS \"X\" FROM "
                + "(SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(x) FROM (SELECT a AS x FROM t1 UNION ALL "
                + "SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, WB$1.A) AS \"X\" FROM "
                + "(SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1) "
                + "values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 UNION ALL "
                + "SELECT a FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(4) AS \"MAX(X)\" FROM (SELECT 4 AS \"X\" FROM (SELECT 4 AS \"X\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t2 UNION ALL "
                + "SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, 4) AS \"X\" FROM (SELECT "
                + "T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1,"
                + " rowCount => 1))) WB$1 GROUP BY X) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 UNION "
                + "SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, 5) AS \"X\" FROM (SELECT "
                + "T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT 5 AS \"5\" FROM TABLE (GENERATOR(ROWCOUNT => 1,"
                + " rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 UNION ALL "
                + "SELECT 5))))");
    }

    @Test
    public void moreSetOperations() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        engine.execute("CREATE OR REPLACE TABLE t3 (a INT)");
        engine.execute("INSERT INTO t3 VALUES (2), (3)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(1) AS \"MAX(X)\" FROM (SELECT 1 AS \"X\" FROM (SELECT 1 AS \"X\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 1 AS x UNION ALL SELECT "
                + "1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 "
                + "AS T1) WB$0 GROUP BY A) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 EXCEPT SELECT "
                + "5))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => "
                + "1))) WB$0 SEMI JOIN (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 "
                + "ON (WB$0.\"1\" = WB$1.\"1\") GROUP BY 1) OB$2) WB$2 UNION ALL (SELECT null AS \"CNULL\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$3) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT SELECT 1 "
                + "UNION ALL SELECT 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 "
                + "AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 GROUP "
                + "BY 1) OB$2) WB$2 GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 EXCEPT SELECT 2 EXCEPT "
                + "SELECT 3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 "
                + "AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI "
                + "JOIN (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"1\" "
                + "= WB$1.\"1\") GROUP BY 1) OB$2) WB$2 GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT SELECT 1 "
                + "EXCEPT SELECT 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 EXCEPT SELECT 2 "
                + "INTERSECT SELECT 1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT WB$0.X AS \"X\" FROM (SELECT T1.A AS \"X\" "
                + "FROM T1 AS T1) WB$0 SEMI JOIN (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 ON (WB$0.X = WB$1.A) GROUP "
                + "BY X) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 INTERSECT "
                + "SELECT a FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT SUM(CAST(\"values\".X AS NUMBER(38,0))) AS \"SUM(X)\" FROM (SELECT SET_COMBINE(WB$0.X, 4) "
                + "AS \"X\" FROM (SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT 4 AS \"A\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(x) FROM (SELECT a AS x FROM t1 UNION ALL "
                + "SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 "
                + "AS T1 WHERE T1.A > 1) WB$0 SEMI JOIN (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 ON (WB$0.A = "
                + "WB$1.A) GROUP BY A) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 WHERE a > 1 "
                + "INTERSECT SELECT a FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 "
                + "AS T1) WB$0 SEMI JOIN (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) "
                + "WB$1 ON (WB$0.A = WB$1.A) GROUP BY A) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 INTERSECT "
                + "SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\", WB$0.A AS \"A\" FROM (SELECT T1.A "
                + "AS \"A\", T1.A AS \"A\" FROM T1 AS T1) WB$0 SEMI JOIN (SELECT T3.A AS \"A\", T3.A AS \"A\" FROM T3 "
                + "AS T3) WB$1 ON ((WB$0.A = WB$1.A) AND (WB$0.A = WB$1.A)) GROUP BY A, A) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a, a FROM t1 INTERSECT "
                + "SELECT a, a FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"X\" FROM (SELECT 1 AS \"X\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 2 AS \"Y\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.X = WB$1.Y) GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 x INTERSECT SELECT 2 "
                + "y))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1.5 AS \"1.5\" FROM (SELECT 1.5 AS \"1.5\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1.5 AS \"1.5\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"1.5\" = WB$1.\"1.5\") GROUP BY 1.5) "
                + "values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1.5 INTERSECT SELECT "
                + "1.5))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$0.A AS \"A\" FROM (SELECT T1.A AS \"A\" FROM T1 "
                + "AS T1) WB$0 GROUP BY A) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 MINUS SELECT a "
                + "FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 'a' AS \"'A'\" FROM (SELECT 'a' AS \"'A'\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 GROUP BY 'a') values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 'a' EXCEPT SELECT 'b'))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MIN(\"values\".X) AS \"MIN(X)\" FROM (SELECT SET_COMBINE(WB$0.X, WB$1.A) AS \"X\" FROM "
                + "(SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 "
                + "GROUP BY X) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MIN(x) FROM (SELECT a AS x FROM t1 UNION "
                + "SELECT a FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(WB$0.X, WB$1.A, 4) AS \"X\" FROM "
                + "(SELECT T1.A AS \"X\" FROM T1 AS T1) WB$0 UNION ALL (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$1 "
                + "UNION ALL (SELECT 4 AS \"A\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 UNION ALL "
                + "SELECT a FROM t3 UNION ALL SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION SELECT 1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.\"1\" = WB$1.\"1\") GROUP BY 1) S)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT SELECT 1) "
                + "s)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION ALL SELECT a "
                + "FROM t1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(1) AS \"COUNT(X)\" FROM (SELECT 1 AS \"X\" FROM (SELECT 1 AS \"X\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 SEMI JOIN (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 ON (WB$0.X = WB$1.\"1\") GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(x) FROM (SELECT 1 AS x INTERSECT SELECT "
                + "1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT WB$0.X AS \"X\" FROM (SELECT T1.A AS \"X\" "
                + "FROM T1 AS T1) WB$0 GROUP BY X) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT a AS x FROM t1 EXCEPT "
                + "SELECT a FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT WB$2.A AS \"A\" FROM (SELECT OB$2.A AS \"A\" FROM "
                + "(SELECT WB$0.A AS \"A\" FROM (SELECT T3.A AS \"A\" FROM T3 AS T3) WB$0 SEMI JOIN (SELECT T1.A AS "
                + "\"A\" FROM T1 AS T1) WB$1 ON (WB$0.A = WB$1.A) GROUP BY A) OB$2) WB$2 SEMI JOIN (SELECT T3.A AS "
                + "\"A\" FROM T3 AS T3) WB$3 ON (WB$2.A = WB$3.A) GROUP BY A) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t3 INTERSECT "
                + "SELECT a FROM t1 INTERSECT SELECT a FROM t3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT 1 AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2 SEMI JOIN (SELECT SET_COMBINE(1, 2) AS \"1\" FROM "
                + "(SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 "
                + "AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) OB$3 ON (WB$2.\"1\" = "
                + "OB$3.\"1\") GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 INTERSECT (SELECT 1 "
                + "UNION ALL SELECT 2)))))");
    }
}
