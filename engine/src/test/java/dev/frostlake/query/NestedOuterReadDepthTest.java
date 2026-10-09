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
 * A subquery whose own subquery reads the row of the query around both is refused, whatever its shape: no
 * query nested two levels down is turned into a join. A plain query's row and a grouped query's key or
 * aggregate are refused alike, at the first-level subquery in a select list or a HAVING and with no place in a
 * WHERE; read from three levels down the refusal is placed again. One level down the shape rules decide.
 * Every cell is live-verified.
 */
public class NestedOuterReadDepthTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    @Test
    public void aPlainQuerysRowReadTwoLevelsDownIsRefused() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 WHERE g2.id <= fz.id)) "
                + "FROM fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 WHERE g2.id "
                + "<= fz.id)) ORDER BY id",
                UNSUPPORTED},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM g g2 WHERE g2.id = "
                + "fz.id)) ORDER BY id",
                UNSUPPORTED},
            {"SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.v IN (SELECT g2.v FROM g g2 WHERE g2.id "
                + "= fz.id)) ORDER BY id",
                UNSUPPORTED},
            {"SELECT id, (SELECT MAX(v) FROM g WHERE g.id = (SELECT MAX(g2.id) FROM g g2 WHERE g2.id = fz.id)) "
                + "FROM fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM g g2 WHERE EXISTS "
                + "(SELECT 1 FROM g g3 WHERE g3.id = fz.id))) ORDER BY id",
                unsupportedAt(24)},
        });
    }

    @Test
    public void aGroupedQuerysRowReadTwoLevelsDownIsRefused() {
        assertCells(new String[][] {
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 "
                + "WHERE fz.b)) > 0 ORDER BY b",
                unsupportedAt(36)},
            {"SELECT b, (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 WHERE fz.b)) FROM fz GROUP "
                + "BY b ORDER BY b",
                unsupportedAt(11)},
            {"SELECT b, (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 WHERE g2.id <= "
                + "MAX(fz.id))) FROM fz GROUP BY b ORDER BY b",
                unsupportedAt(11)},
            {"SELECT b FROM fz GROUP BY b HAVING EXISTS (SELECT 1 FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 "
                + "WHERE g2.id <= MAX(fz.id))) ORDER BY b",
                unsupportedAt(35)},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.id <= MAX(fz.id) AND g.v > "
                + "(SELECT MIN(v) FROM g g2 WHERE g2.id <= MAX(fz.id))) > 0 ORDER BY b",
                unsupportedAt(36)},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT (SELECT MAX(fz.id))) > 0 ORDER BY b", unsupportedAt(44)},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 "
                + "WHERE g2.v > (SELECT MIN(v) FROM g g3 WHERE g3.id <= MAX(fz.id)))) > 0 ORDER BY b",
                unsupportedAt(36)},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g "
                + "WHERE g.id <= MAX(fz.id))) > 0 ORDER BY b",
                unsupportedAt(36)},
            {"SELECT b, (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 WHERE g2.id <= "
                + "MAX(fz.id))) FROM fz GROUP BY b ORDER BY b",
                unsupportedAt(11)},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.v > (SELECT MIN(v) FROM g g2 "
                + "WHERE fz.b)) > 0 ORDER BY b",
                unsupportedAt(36)},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT MAX((SELECT MAX(fz.id))) FROM g) > 0 ORDER BY b",
                unsupportedAt(36)},
            {"SELECT b FROM fz GROUP BY b HAVING EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM g g2 WHERE "
                + "g2.id <= MAX(fz.id))) ORDER BY b",
                unsupportedAt(35)},
        });
    }

    @Test
    public void oneLevelDownTheShapeRulesDecide() {
        assertCells(new String[][] {
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.id = MAX(fz.id)) > 0 ORDER BY b",
                "true"},
            {"SELECT b, (SELECT COUNT(*) FROM g WHERE g.id = MAX(fz.id)) FROM fz GROUP BY b ORDER BY b",
                "false|0 ;; true|1"},
            {"SELECT b, (SELECT COUNT(*) FROM g WHERE fz.b) FROM fz GROUP BY b ORDER BY b", unsupportedAt(11)},
        });
    }
}
