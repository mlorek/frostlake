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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A write's value from an aggregate over no table - UPDATE's SET, MERGE's UPDATE, INSERT ... SELECT - is folded
 * with its conversion to the column's type, so a value out of range is refused without the DML envelope; a
 * query of constants alone, or one reading a table, keeps it. In VALUES, a subquery that is no constant is
 * refused with its plan: an aggregate's, a one-row table's folded over a GENERATOR's row, a table's of other
 * sizes as read; a query of no table that does not aggregate is its value, and a MIN or a MAX over a table of
 * one row folds (all live-verified).
 */
public class FoldedSubqueryWritesTest extends MeasuredCellsTestSupport {

    @Test
    public void foldedWrites() {
        engine.execute("CREATE OR REPLACE TABLE nn681 (n NUMBER(3,0) NOT NULL)");
        engine.execute("INSERT INTO nn681 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE nm681 (n NUMBER(3,0))");
        engine.execute("INSERT INTO nm681 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE src681 (v INT)");
        engine.execute("INSERT INTO src681 VALUES (12345)");
        engine.execute("CREATE OR REPLACE TABLE src681b (v INT)");
        engine.execute("INSERT INTO src681b VALUES (12345), (7)");
        engine.execute("CREATE OR REPLACE TABLE src681e (v INT)");
        engine.execute("CREATE OR REPLACE TABLE src681s (v VARCHAR)");
        engine.execute("INSERT INTO src681s VALUES ('x')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn681 SET n = (SELECT MAX(12345))");
        assertAnswer("REFUSED DML operation to table NN681 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn681 SET n = (SELECT 12345)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn681 SET n = (SELECT MAX(12345) + 0)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn681 SET n = (SELECT COUNT(*) * 12345)");
        assertAnswer("REFUSED DML operation to table NN681 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn681 SET n = (SELECT MIN(v) FROM src681)");
        assertAnswer("REFUSED DML operation to table NN681 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn681 SET n = 12345");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nm681 SET n = (SELECT MAX(12345))");
        assertAnswer("REFUSED DML operation to table NM681 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nm681 SET n = (SELECT 12345)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn681 SELECT MAX(12345)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT MAX(12345) AS \"MAX(12345)\" FROM "
                + "(VALUES (null)) DUAL)] in VALUES clause",
            "INSERT INTO nn681 VALUES ((SELECT MAX(12345)))");
        assertAnswer("REFUSED DML operation to table NN681 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn681 VALUES ((SELECT 12345))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn681 VALUES ((SELECT v FROM src681))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nm681 VALUES ((SELECT v FROM src681))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT SRC681B.V AS \"V\" FROM SRC681B AS "
                + "SRC681B)] in VALUES clause",
            "INSERT INTO nn681 VALUES ((SELECT v FROM src681b))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT SRC681E.V AS \"V\" FROM SRC681E AS "
                + "SRC681E)] in VALUES clause",
            "INSERT INTO nn681 VALUES ((SELECT v FROM src681e))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12346 AS \"V + 1\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn681 VALUES ((SELECT v + 1 FROM src681))");
        assertAnswer("REFUSED DML operation to table NN681 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn681 VALUES ((SELECT MAX(v) FROM src681))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn681 VALUES ((SELECT v FROM src681 WHERE v > 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 'x' AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn681 VALUES ((SELECT v FROM src681s))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 5 AS \"5\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn681 VALUES ((SELECT 5 FROM src681))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn681 VALUES (1), ((SELECT v FROM src681))");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO nn681 t USING (SELECT 1 AS k) s ON TRUE WHEN MATCHED THEN UPDATE SET n = (SELECT MAX(12345))");
        assertAnswer("1", "SELECT n FROM nn681 ORDER BY 1");
    }

    @Test
    public void queriesOfNoTableAndLimits() {
        engine.execute("CREATE OR REPLACE TABLE nn682 (n NUMBER(3,0) NOT NULL)");
        engine.execute("INSERT INTO nn682 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE src682 (v INT)");
        engine.execute("INSERT INTO src682 VALUES (12345)");
        engine.execute("CREATE OR REPLACE TABLE src682b (v INT)");
        engine.execute("INSERT INTO src682b VALUES (12345), (7)");
        engine.execute("CREATE OR REPLACE PROJECTION POLICY deny682 AS () RETURNS PROJECTION_CONSTRAINT -> "
            + "PROJECTION_CONSTRAINT(ALLOW => false)");
        engine.execute("CREATE OR REPLACE TABLE pt682 (id INT, p VARCHAR WITH PROJECTION POLICY deny682)");
        engine.execute("INSERT INTO pt682 VALUES (1, 'hidden')");
        engine.execute("CREATE OR REPLACE TABLE pt682b (id INT, p VARCHAR WITH PROJECTION POLICY deny682)");
        engine.execute("INSERT INTO pt682b VALUES (1, 'hidden'), (2, 'other')");
        engine.execute("CREATE OR REPLACE TABLE t682 (id INT, x VARCHAR)");
        assertAnswer("REFUSED DML operation to table NN682 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn682 VALUES ((SELECT NULLIF(12345, 0)))");
        assertAnswer("REFUSED DML operation to table NN682 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn682 VALUES ((SELECT ABS(-12345)))");
        assertAnswer("REFUSED DML operation to table NN682 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn682 VALUES ((SELECT 12345 + 0))");
        assertAnswer("REFUSED DML operation to table NN682 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn682 VALUES ((SELECT IFF(TRUE, 12345, 0)))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT (MAX(12345)) + 0 AS \"MAX(12345) + "
                + "0\" FROM (VALUES (null)) DUAL)] in VALUES clause",
            "INSERT INTO nn682 VALUES ((SELECT MAX(12345) + 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT COUNT(*) AS \"COUNT(*)\" FROM "
                + "(VALUES (null)) DUAL)] in VALUES clause",
            "INSERT INTO nn682 VALUES ((SELECT COUNT(*)))");
        assertAnswer("REFUSED DML operation to table NN682 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn682 VALUES ((SELECT 12345 WHERE TRUE))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn682 VALUES ((SELECT v FROM src682 LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT SRC682B.V AS \"V\" FROM SRC682B AS "
                + "SRC682B LIMIT 1 OFFSET 0)] in VALUES clause",
            "INSERT INTO nn682 VALUES ((SELECT v FROM src682b LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn682 VALUES ((SELECT v FROM src682 ORDER BY v))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM SRC682 AS "
                + "SRC682 GROUP BY 12345)] in VALUES clause",
            "INSERT INTO nn682 VALUES ((SELECT v FROM src682 GROUP BY v))");
        assertAnswer("REFUSED DML operation to table NN682 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn682 VALUES ((SELECT MIN(v) FROM src682))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 5 AS \"5\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 2, rowCount => 2)))] in VALUES clause",
            "INSERT INTO nn682 VALUES ((SELECT 5 FROM src682b))");
        assertAnswer("REFUSED SQL compilation error: | Insert value list does not match column list expecting 1 but "
                + "got 2",
            "INSERT INTO nn682 VALUES ((SELECT v FROM src682), 1)");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "NUMBER(38,0) but got ROW(ANY) for column ID",
            "INSERT INTO t682 (id) VALUES ((SELECT 1, 2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] in VALUES "
                + "clause",
            "INSERT INTO t682 (id) VALUES (1), ((SELECT 1, 2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 'hidden' AS \"P\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO t682 VALUES (3, (SELECT p FROM pt682 LIMIT 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 'hidden' AS \"P\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO t682 (x) VALUES ((SELECT p FROM pt682))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT PT682B.P AS \"P\" FROM PT682B AS "
                + "PT682B)] in VALUES clause",
            "INSERT INTO t682 (x) VALUES ((SELECT p FROM pt682b))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 1 AS \"ID\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO t682 (x) VALUES ((SELECT id FROM pt682))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "SELECT $1 FROM VALUES ((SELECT v FROM src682))");
        assertAnswer("1", "SELECT n FROM nn682 ORDER BY 1");
    }

    @Test
    public void oneRowTablesAndAggregates() {
        engine.execute("CREATE OR REPLACE TABLE nn683 (n NUMBER(3,0) NOT NULL)");
        engine.execute("INSERT INTO nn683 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE w683 (s VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE src683 (v INT)");
        engine.execute("INSERT INTO src683 VALUES (12345)");
        engine.execute("CREATE OR REPLACE TABLE src683b (v INT)");
        engine.execute("INSERT INTO src683b VALUES (12345), (7)");
        engine.execute("CREATE OR REPLACE TABLE s683 (s VARCHAR)");
        engine.execute("INSERT INTO s683 VALUES ('a'), ('b')");
        engine.execute("CREATE OR REPLACE TABLE s683l (s VARCHAR)");
        engine.execute("INSERT INTO s683l VALUES ('xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx1'), "
            + "('xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx2')");
        engine.execute("CREATE OR REPLACE TABLE s683o (s VARCHAR)");
        engine.execute("INSERT INTO s683o VALUES ('only')");
        engine.execute("CREATE OR REPLACE TABLE f683 (f FLOAT)");
        engine.execute("INSERT INTO f683 VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE d683 (d DATE)");
        engine.execute("INSERT INTO d683 VALUES ('2024-01-01'), ('2024-02-01')");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT MAX(S683.S) AS \"MAX(S)\" FROM "
                + "S683 AS S683)] in VALUES clause",
            "INSERT INTO w683 VALUES ((SELECT MAX(s) FROM s683))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT MIN(S683L.S) AS \"MIN(S)\" FROM "
                + "S683L AS S683L)] in VALUES clause",
            "INSERT INTO w683 VALUES ((SELECT MIN(s) FROM s683l))");
        assertAnswer("1", "INSERT INTO w683 VALUES ((SELECT MAX(s) FROM s683o))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT MAX(F683.F) AS \"MAX(F)\" FROM "
                + "F683 AS F683)] in VALUES clause",
            "INSERT INTO w683 VALUES ((SELECT MAX(f) FROM f683))");
        assertAnswer("1", "INSERT INTO w683 VALUES ((SELECT MAX(d) FROM d683))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT SUM(12345) AS \"SUM(V)\" FROM "
                + "SRC683 AS SRC683)] in VALUES clause",
            "INSERT INTO nn683 VALUES ((SELECT SUM(v) FROM src683))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT SUM(CAST(SRC683B.V AS NUMBER(38,"
                + "0))) AS \"SUM(V)\" FROM SRC683B AS SRC683B)] in VALUES clause",
            "INSERT INTO nn683 VALUES ((SELECT SUM(v) FROM src683b))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT "
                + "SCALED_ROUND_INT_DIVIDE(CAST(SUM(12345) AS NUMBER(38,0)), 1) AS \"AVG(V)\" FROM SRC683 AS SRC683)] "
                + "in VALUES clause",
            "INSERT INTO nn683 VALUES ((SELECT AVG(v) FROM src683))");
        assertAnswer("REFUSED DML operation to table NN683 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn683 VALUES ((SELECT MAX(v) FROM src683b))");
        assertAnswer("1", "INSERT INTO nn683 VALUES ((SELECT COUNT(*) FROM src683b))");
        assertAnswer("REFUSED DML operation to table NN683 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12346",
            "INSERT INTO nn683 VALUES ((SELECT MAX(v) + 1 FROM src683))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 0, rowCount => 0)))] in VALUES clause",
            "INSERT INTO nn683 VALUES ((SELECT v FROM src683 LIMIT 1 OFFSET 1))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT SRC683.V AS \"V\" FROM SRC683 AS "
                + "SRC683 WHERE FALSE)] in VALUES clause",
            "INSERT INTO nn683 VALUES ((SELECT v FROM src683 WHERE v < 0))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT DISTINCT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn683 VALUES ((SELECT DISTINCT v FROM src683))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn683 VALUES ((SELECT v FROM src683 ORDER BY v LIMIT 5))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [(SELECT 12345 AS \"V\" FROM TABLE "
                + "(GENERATOR(ROWCOUNT => 1, rowCount => 1)))] in VALUES clause",
            "INSERT INTO nn683 VALUES ((SELECT v FROM src683 t WHERE t.v = 12345))");
        assertAnswer("2024-02-01 ;; only", "SELECT s FROM w683 ORDER BY 1");
        assertAnswer("1 ;; 2", "SELECT n FROM nn683 ORDER BY 1");
    }
}
