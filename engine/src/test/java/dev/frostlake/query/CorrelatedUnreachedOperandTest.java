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
 * An outer name standing where the statistics prove no row reads it correlates nothing: an argument of
 * COALESCE, NVL or IFNULL after one that holds no NULL, the third argument of NVL2 beside a first that holds
 * none, and the branch of IFF or of a searched CASE that a settled condition leaves out. Over a g.id holding
 * no NULL, {@code COALESCE(g.id, fz.id) = 5} filters g alone, where GREATEST, the outer name written first
 * and a column holding a NULL keep the correlation and its refusal. Every cell is live-verified.
 */
public class CorrelatedUnreachedOperandTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE gn (id INT, v INT)");
        engine.execute("INSERT INTO gn VALUES (5, 50), (NULL, 60)");
    }

    @Test
    public void anOperandNoRowReachesCorrelatesNothing() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT SUM(v) FROM g WHERE COALESCE(g.id, fz.id) = 5) FROM fz ORDER BY id", "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE NVL(g.id, fz.id) = 5) FROM fz ORDER BY id", "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE IFNULL(g.id, fz.id) = 5) FROM fz ORDER BY id", "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE NVL2(g.id, g.id, fz.id) = 5) FROM fz ORDER BY id",
                "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE IFF(g.id IS NULL, fz.id, g.id) = 5) FROM fz ORDER BY id",
                "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE CASE WHEN g.id IS NULL THEN fz.id ELSE g.id END = 5) FROM "
                + "fz ORDER BY id",
                "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE COALESCE(g.id, fz.id, 0) = 5) FROM fz ORDER BY id",
                "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE COALESCE(g.id + 0, fz.id) = 5) FROM fz ORDER BY id",
                "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE COALESCE(g.id, fz.id) = 5 AND g.v > 0) FROM fz ORDER BY id",
                "5|50 ;; 7|50"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE COALESCE(g.id, fz.id) > fz.id - 100) FROM fz ORDER BY id",
                "5|110 ;; 7|110"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE COALESCE(g.id, fz.id) = 5) ORDER BY id",
                "5 ;; 7"},
            {"SELECT id FROM fz WHERE id IN (SELECT v / 10 FROM g WHERE COALESCE(g.id, fz.id) = 5) ORDER BY id", "5"},
            {"SELECT id, (SELECT MAX(COALESCE(g.id, fz.id)) FROM g) FROM fz ORDER BY id", "5|6 ;; 7|6"},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE g.id = fz.id) FROM fz ORDER BY id", "5|50 ;; 7|null"},
        });
    }

    @Test
    public void anOperandARowMayReachKeepsTheRefusal() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT SUM(v) FROM g WHERE GREATEST(g.id, fz.id) = 5) FROM fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT SUM(v) FROM g WHERE COALESCE(fz.id, g.id) = 5) FROM fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT SUM(v) FROM gn WHERE COALESCE(gn.id, fz.id) = 5) FROM fz ORDER BY id",
                unsupportedAt(12)},
        });
    }
}
