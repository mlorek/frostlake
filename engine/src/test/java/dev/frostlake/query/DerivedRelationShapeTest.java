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
 * A correlated subquery over a derived relation of a shape of its own, judged before any row (live-verified): one
 * that computes over, groups, unites or joins its table, or stands beside another relation, is refused unless the
 * column it reads is a constant, the statistics of what the column computes settle the correlation, or something
 * prunes the relation - its own FALSE or a condition its statistics rule out, its own constant WHERE over a derived
 * relation, or the statement's constant WHERE over one that does not group. Inside an aggregate's argument an IN
 * list's element the subject never equals and a COALESCE's argument after one that holds no NULL correlate nothing,
 * in a value's subquery and an IN's alike.
 */
public class DerivedRelationShapeTest extends MeasuredCellsTestSupport {

    /** Each derived shape, its exceptions and the agreeing ones. */
    @Test
    public void aDerivedRelationOfItsOwnShape() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        engine.execute("INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        engine.execute("CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO one VALUES (5, TRUE)");
        assertCells(new String[][] {
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz GROUP BY id HAVING 1 = 0) "
                + "fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT fz.id FROM fz JOIN one ON FALSE) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz WHERE 1 = 0) d, (SELECT * FROM "
                + "one) o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id AS id FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id::INT AS id FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT ABS(id) AS id FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz WHERE 1 = 0 UNION ALL "
                + "SELECT id FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz GROUP BY id) fz WHERE 1 = 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"WITH fz AS (SELECT id + 0 AS id FROM test_schema.fz WHERE 1 = 0) SELECT id, (SELECT v FROM g WHERE g.id "
                + "= fz.id) AS x FROM fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "77"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id AS id, id + 1 AS k FROM fz WHERE 1 "
                + "= 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz WHERE 1 = 0) d JOIN one o ON "
                + "TRUE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
            {"SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz WHERE 1 = 0) d, one o",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
            {"SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM one o, (SELECT * FROM fz WHERE 1 = 0) d",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "14"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT 5 AS id FROM fz WHERE 1 = 0) fz",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT -id AS id FROM fz WHERE 1 = 0) fz",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT MAX(id) AS id FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT DISTINCT id FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz WHERE 1 = 0 ORDER BY id) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz) fz WHERE 1 = 0",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT fz.id FROM fz JOIN one ON fz.id = "
                + "one.id) fz WHERE 1 = 0",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id, b FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM (SELECT id FROM fz) WHERE 1 = "
                + "0) fz",
                ""},
            {"SELECT fz.id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz, one WHERE 1 = 0",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "15"},
            {"SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz) d, (SELECT * FROM one) o WHERE "
                + "1 = 0",
                ""},
            {"SELECT d.id, (SELECT v FROM g WHERE g.id = d.id + o.id) FROM (SELECT * FROM fz WHERE 1 = 0) d, (SELECT "
                + "* FROM one) o",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz WHERE FALSE) fz",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 100 AS id FROM fz WHERE 1 = 0) fz",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id * 10 AS id FROM fz WHERE 1 = 0) fz",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz WHERE 1 = 0 UNION SELECT "
                + "id FROM fz WHERE 1 = 0) fz",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz) fz WHERE FALSE",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz GROUP BY id) fz WHERE FALSE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz WHERE id > 100) "
                + "fz",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT NULL::INT AS id FROM fz WHERE 1 = 0) "
                + "fz",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz) fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
        });
    }

    /** The folds of an IN list, a COALESCE and a settled condition. */
    @Test
    public void foldsInsideAnAggregatesArgument() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        engine.execute("INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        engine.execute("CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO one VALUES (5, TRUE)");
        assertCells(new String[][] {
            {"SELECT id, (SELECT MAX(IFF(tt.id IN (fz.id, 1), 'x', value)) FROM tt) AS x FROM fz ORDER BY id",
                "5|x ;; 7|x"},
            {"SELECT id, (SELECT MAX(COALESCE(tt.value, fz.id::VARCHAR)) FROM tt) AS x FROM fz ORDER BY id",
                "5|B ;; 7|B"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(IFF(tt.id = fz.id, 5, 7)) FROM tt) ORDER BY id",
                "7"},
            {"SELECT id, (SELECT MAX(IFF(tt.id IN (fz.id, 2), 'x', value)) FROM tt) AS x FROM fz ORDER BY id",
                "5|x ;; 7|x"},
            {"SELECT id, (SELECT MAX(COALESCE(fz.id::VARCHAR, tt.value)) FROM tt) AS x FROM fz ORDER BY id",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position "
                + "12"},
            {"SELECT id, (SELECT MAX(IFF(tt.id = fz.id, 'x', value)) FROM tt) AS x FROM fz ORDER BY id",
                "5|B ;; 7|B"},
            {"SELECT id, (SELECT MAX(IFF(tt.id NOT IN (fz.id, 1), 'x', value)) FROM tt) AS x FROM fz ORDER BY id",
                "5|x ;; 7|x"},
            {"SELECT id, (SELECT MAX(NVL(tt.value, fz.id::VARCHAR)) FROM tt) AS x FROM fz ORDER BY id",
                "5|B ;; 7|B"},
        });
    }
}
