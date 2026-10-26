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
 * A GENERATOR bound over a set operation, a FROM-less select or a VALUES list is refused with the plan the
 * account holds for it: a branch no column is read of is a CNULL column of nulls over its rows, a whole
 * table's rows a GENERATOR of their count, a column read the branches' constants combined by SET_COMBINE,
 * a UNION without ALL grouped; a derived table passing one table on whole folds by its statistics (all
 * live-verified).
 */
public class GeneratorBoundSetOperationTest extends MeasuredCellsTestSupport {

    @Test
    public void setOperationBounds() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        engine.execute("INSERT INTO t1 VALUES (1), (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE t2 (a INT)");
        engine.execute("INSERT INTO t2 VALUES (4)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION ALL SELECT 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT SET_COMBINE(1, 2) AS \"1\" FROM (SELECT 1 AS \"1\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 GROUP BY 1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION SELECT 2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$2) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION ALL SELECT 2 "
                + "UNION ALL SELECT 3))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 AS x UNION ALL SELECT "
                + "2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(\"values\".X) AS \"COUNT(X)\" FROM (SELECT SET_COMBINE(1, 2) AS \"X\" FROM (SELECT "
                + "1 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(x) FROM (SELECT 1 AS x UNION ALL SELECT "
                + "2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT MAX(\"values\".X) AS \"MAX(X)\" FROM (SELECT SET_COMBINE(1, 2) AS \"X\" FROM (SELECT 1 AS "
                + "\"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM "
                + "TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(x) FROM (SELECT 1 AS x UNION ALL SELECT "
                + "2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT SUM(CAST(\"values\".X AS NUMBER(1,0))) AS \"SUM(X)\" FROM (SELECT SET_COMBINE(1, 2) AS "
                + "\"X\" FROM (SELECT 1 AS \"X\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL "
                + "(SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT SUM(x) FROM (SELECT 1 AS x UNION ALL SELECT "
                + "2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (VALUES (null)) DUAL)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (VALUES (null)) DUAL)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 AS x))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 UNION ALL "
                + "SELECT a FROM t2))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 3, rowCount => 3))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 UNION ALL "
                + "SELECT 5))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) S)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1 UNION ALL SELECT 2) "
                + "s)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 'a' UNION ALL SELECT "
                + "'b'))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$0 UNION ALL (SELECT null AS \"CNULL\" "
                + "FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT 1, 2 UNION ALL SELECT 3, "
                + "4))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (SELECT null AS \"CNULL\" FROM (SELECT null AS \"CNULL\" "
                + "FROM T1 AS T1 WHERE T1.A > 1) WB$0 UNION ALL (SELECT null AS \"CNULL\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1))) WB$1) values)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT a FROM t1 WHERE a > 1 "
                + "UNION ALL SELECT a FROM t2))))");
        assertAnswer("3", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM t1)))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (SELECT * FROM t1))))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT SET_COMBINE(1, 2) AS \"1\" FROM (SELECT 1 AS \"1\" FROM TABLE (GENERATOR(ROWCOUNT => 1, "
                + "rowCount => 1))) WB$0 UNION ALL (SELECT 2 AS \"2\" FROM TABLE (GENERATOR(ROWCOUNT => 1, rowCount "
                + "=> 1))) WB$1)'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT 1 UNION ALL SELECT 2)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'(SELECT COUNT(*) AS \"COUNT(*)\" FROM (VALUES (1), (2)) \"VALUES\")'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT COUNT(*) FROM (VALUES (1), (2)))))");
    }
}
