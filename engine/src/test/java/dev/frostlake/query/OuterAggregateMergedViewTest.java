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
 * A derived table's outer aggregate over a view's expression column, under a filter, over a join and over a view
 * or a derived table that passes a stored table through (all live-verified).
 */
public class OuterAggregateMergedViewTest extends MeasuredCellsTestSupport {

    @Test
    public void mergedViewAggregates() {
        engine.execute("CREATE OR REPLACE TABLE fz3 (id INT)");
        engine.execute("INSERT INTO fz3 VALUES (5), (7), (9)");
        engine.execute("CREATE OR REPLACE TABLE fzd (id NUMBER(10,2))");
        engine.execute("INSERT INTO fzd VALUES (1.5), (2.5)");
        engine.execute("CREATE OR REPLACE TABLE g3 (id INT, v INT)");
        engine.execute("INSERT INTO g3 VALUES (5, 10), (6, 20)");
        engine.execute("CREATE OR REPLACE TABLE g4 (id INT, v INT)");
        engine.execute("INSERT INTO g4 VALUES (1, 1), (2, 2), (3, 3)");
        engine.execute("CREATE OR REPLACE VIEW vz3e AS SELECT id + 1 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3m AS SELECT id * 2 AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3a AS SELECT ABS(id) AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3x AS SELECT -id AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vz3c AS SELECT CAST(id AS VARCHAR) AS id FROM fz3");
        engine.execute("CREATE OR REPLACE VIEW vg3 AS SELECT * FROM g3");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID + 1)] "
                + "nested in [SUM(CORRELATION(CAST(MAX(FZ3.ID + 1) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3e.id) AS x FROM g3)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MIN(FZ3.ID + 1)] "
                + "nested in [SUM(CORRELATION(CAST(MIN(FZ3.ID + 1) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MIN(vz3e.id) AS x FROM g3)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ3.ID + 1)] "
                + "nested in [SUM(CORRELATION(CAST(COUNT(FZ3.ID + 1) AS NUMBER(18,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(vz3e.id) AS x FROM g3)) FROM vz3e ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID * 2)] "
                + "nested in [SUM(CORRELATION(CAST(MAX(FZ3.ID * 2) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3m.id) AS x FROM g3)) FROM vz3m ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(ABS(FZ3.ID))] "
                + "nested in [SUM(CORRELATION(CAST(MAX(ABS(FZ3.ID)) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3a.id) AS x FROM g3)) FROM vz3a ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(NEGATE(FZ3.ID))] "
                + "nested in [SUM(CORRELATION(CAST(MAX(NEGATE(FZ3.ID)) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3x.id) AS x FROM g3)) FROM vz3x ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CAST(FZ3.ID AS "
                + "VARCHAR(134217728)))] nested in [MAX(CORRELATION(MAX(CAST(FZ3.ID AS VARCHAR(134217728)))))]",
            "SELECT id, (SELECT MAX(x) FROM (SELECT MAX(vz3c.id) AS x FROM g3)) FROM vz3c ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ3.ID)] nested "
                + "in [SUM(CORRELATION(CAST(COUNT(FZ3.ID) AS NUMBER(18,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz3.id) AS x FROM g3 WHERE fz3.id > 6)) FROM fz3 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ3.ID)] nested "
                + "in [SUM(CORRELATION(CAST(COUNT(FZ3.ID) AS NUMBER(18,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz3.id) AS x FROM g3 WHERE g3.v > 10)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZD.ID AS "
                + "NUMBER(10,2)))] nested in [SUM(CORRELATION((CAST(SUM(CAST(FZD.ID AS NUMBER(10,2))) AS NUMBER(28,"
                + "8))) / (CAST(COUNT(FZD.ID) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fzd.id) AS x FROM g3 WHERE g3.v > 10)) FROM fzd ORDER BY id");
        assertAnswer("5|4 ;; 7|4 ;; 9|4",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz3.id) AS x FROM vg3)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [COUNT(FZ3.ID)] nested "
                + "in [SUM(CORRELATION(CAST(COUNT(FZ3.ID) AS NUMBER(18,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz3.id) AS x FROM g3 JOIN g4 ON TRUE)) FROM fz3 ORDER BY id");
        assertAnswer("5|4 ;; 7|4 ;; 9|4",
            "SELECT id, (SELECT SUM(x) FROM (SELECT COUNT(fz3.id) AS x FROM (SELECT * FROM g3))) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(CAST(SCALED_ROUND_INT_DIVIDE(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))), 2) AS NUMBER(38,6))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fz3.id) AS x FROM vg3)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0)))] nested in [SUM(CORRELATION(SCALED_ROUND_INT_DIVIDE(CAST(SUM(CAST(FZ3.ID AS "
                + "NUMBER(38,0))) AS NUMBER(38,0)), CAST(COUNT(FZ3.ID) AS NUMBER(18,0)))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT AVG(fz3.id) AS x FROM g3 JOIN g4 ON TRUE)) FROM fz3 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ3.ID + 1)] "
                + "nested in [SUM(CORRELATION(CAST(MAX(FZ3.ID + 1) AS NUMBER(38,0))))]",
            "SELECT id, (SELECT SUM(x) FROM (SELECT MAX(vz3e.id) AS x FROM g3)) FROM vz3e WHERE id > 6 ORDER BY id");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
