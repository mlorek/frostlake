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
 * A derived relation — a subquery, a common table expression or a view — that passes the columns of one
 * table through is planned as the query over that table, so a correlated subquery over it is judged before
 * any row exists: {@code … FROM (SELECT * FROM fz WHERE 1 = 0) fz} and {@code … FROM (SELECT * FROM fz LIMIT
 * 0) fz} are refused. The relation's own WHERE prunes as a statement's does — FALSE and a filter the
 * statistics rule out, not {@code 1 = 0} — and over the relation the statement's {@code WHERE 1 = 0} and
 * {@code LIMIT 0} prune too, the latter while the relation has no limit of its own. A relation over a table
 * of one row at most is read row by row. Every cell is live-verified.
 */
public class DerivedOuterRelationPlanJudgementTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        engine.execute("INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        engine.execute("CREATE OR REPLACE TABLE em (id INT, v INT, a INT)");
        engine.execute("CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO one VALUES (5, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE k (id INT, n INT)");
        engine.execute("INSERT INTO k VALUES (5, 1), (5, 2)");
        engine.execute("CREATE OR REPLACE VIEW fzv AS SELECT * FROM fz");
        engine.execute("CREATE OR REPLACE VIEW fzv0 AS SELECT * FROM fz WHERE 1 = 0");
    }

    @Test
    public void aRelationPassingATableThroughIsJudgedAsTheTable() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz LIMIT 0) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE id = 6) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz LIMIT 1) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE id = 5) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz) fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz ORDER BY id LIMIT 0) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fzv fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fzv0 fz", unsupportedAt(12)},
            {"WITH c AS (SELECT * FROM fz WHERE 1 = 0) SELECT id, (SELECT v FROM g WHERE g.id = c.id) AS x FROM c",
                unsupportedAt(53)},
            {"WITH c AS (SELECT * FROM fz LIMIT 0) SELECT id, (SELECT v FROM g WHERE g.id = c.id) AS x FROM c",
                unsupportedAt(49)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE 1 = 0", unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz LIMIT 0", unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE FALSE", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT id FROM fz WHERE 1 = 0) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT DISTINCT id FROM fz WHERE 1 = 0) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0 AND id = 5) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0 ORDER BY "
                + "id) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT TOP 0 * FROM fz) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz LIMIT 5 OFFSET 5) fz",
                unsupportedAt(12)},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0) fz", ""},
            {"SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0) fz", ""},
        });
    }

    @Test
    public void whatPrunesTheRelationOrTheStatementSparesTheSubquery() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE FALSE) fz", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE id > 100) fz", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz) fz WHERE 1 = 0", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz) fz WHERE FALSE", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz) fz LIMIT 0", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fzv fz WHERE 1 = 0", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0) fz WHERE "
                + "id > 100",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0) fz WHERE "
                + "id = 6",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0) fz WHERE "
                + "1 = 0",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM fz LIMIT 0) fz LIMIT 0",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM (SELECT * FROM fz) WHERE "
                + "1 = 0) fz",
                ""},
        });
    }

    @Test
    public void aRelationOfOneRowAtMostIsReadRowByRow() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM em) fz", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM em LIMIT 0) fz", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM one LIMIT 0) fz", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM one WHERE 1 = 0) fz", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT * FROM one) fz", "5|50"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT 5 AS id WHERE 1 = 0) fz", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT 5 AS id) fz", "5|50"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT 5 AS id FROM fz WHERE 1 = 0) fz", ""},
        });
    }

    @Test
    public void aSubqueryInAnotherClauseFollowsThatClausesRule() {
        assertCells(new String[][] {
            {"SELECT id FROM (SELECT * FROM fz WHERE 1 = 0) fz WHERE (SELECT v FROM g WHERE g.id = fz.id) > 0", ""},
            {"SELECT id FROM (SELECT * FROM fz LIMIT 0) fz WHERE (SELECT v FROM g WHERE g.id = fz.id) > 0",
                unsupportedAt(52)},
            {"SELECT id FROM (SELECT * FROM fz LIMIT 0) fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = fz.id LIMIT 1)",
                unsupportedAt(51)},
            {"SELECT id, EXISTS (SELECT 1 FROM g WHERE g.id = fz.id LIMIT 1) FROM (SELECT * FROM fz LIMIT 0) fz",
                unsupportedAt(11)},
            {"SELECT id FROM (SELECT * FROM fz WHERE 1 = 0) fz GROUP BY id HAVING (SELECT v FROM g WHERE g.id = "
                + "fz.id) > 0",
                unsupportedAt(69)},
        });
    }

    @Test
    public void aRelationOfSeveralRowsOfItsOwnIsRefusedByTheRowThatReachesIt() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (SELECT 5 AS id UNION ALL SELECT 7) fz "
                + "ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM (VALUES (5), (7)) fz (id) ORDER BY id",
                unsupportedAt(12)},
        });
    }

    @Test
    public void theRelationKeepsTheTablesStatisticsBesideColumnsOfItsOwn() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT MAX(v) FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz) d ORDER BY id",
                "5|50 ;; 7|null"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM (SELECT * FROM k) d ORDER BY 1",
                "5|50 ;; 5|50"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM k d ORDER BY 1", "5|50 ;; 5|50"},
            {"SELECT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM (SELECT * FROM k WHERE 1 = 0) d", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM (SELECT f.id FROM fz f WHERE 1 = 0) d",
                unsupportedAt(12)},
            {"SELECT id FROM (SELECT * FROM fz WHERE 1 = 0) d WHERE EXISTS (SELECT 1 FROM g WHERE g.id = d.id)", ""},
            {"SELECT id, (SELECT MAX(v) FROM g WHERE g.id = d.id LIMIT 1) FROM (SELECT * FROM fz WHERE 1 = 0) d",
                unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(v) FROM g WHERE g.id = d.id LIMIT 1) FROM fz d WHERE 1 = 0", unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM (SELECT *, id + 1 AS n FROM fz WHERE 1 = 0) d",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM (SELECT * FROM fz WHERE b) d",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM (SELECT * FROM fz WHERE id = 5 AND 1 = 0) d",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0) d ORDER BY id",
                unsupportedAt(12)},
            {"SELECT DISTINCT id, (SELECT v FROM g WHERE g.id = d.id) AS x FROM (SELECT * FROM fz WHERE 1 = 0) d",
                unsupportedAt(21)},
            {"SELECT id, MAX((SELECT v FROM g WHERE g.id = d.id)) AS x FROM (SELECT * FROM fz WHERE 1 = 0) d "
                + "GROUP BY id",
                unsupportedAt(15)},
            {"SELECT COUNT(*) FROM (SELECT id, (SELECT MAX(v) FROM g WHERE g.id = d.id) AS x FROM (SELECT * "
                + "FROM fz) d)",
                "2"},
        });
    }
}
