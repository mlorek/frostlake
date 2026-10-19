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
 * A scan its own filter prunes folds a correlated subquery away only while the outer row is read nowhere
 * else. A scalar subquery reading it in the select list is refused as it is with no filter; an IN selecting
 * an aggregate of its own beside the outer row is refused over a scan pruned by FALSE, NULL or a column's
 * statistics — not by {@code 1 = 0}, which reads no column, a filter reading the outer row, a HAVING or an
 * empty table — and an EXISTS over such an aggregate under any filter that never holds. Every cell is
 * live-verified.
 */
public class CorrelatedPrunedScanAggregateTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        engine.execute("INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        engine.execute("CREATE OR REPLACE TABLE em (id INT, v INT, a INT)");
    }

    @Test
    public void anInAggregatingBesideTheOuterRowOverAPrunedScan() {
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE FALSE)", unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE 1 = 0)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE g.id > 100)", unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE NULL)", unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE g.id = 5 AND FALSE)",
                unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT v + fz.id FROM g WHERE FALSE)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT fz.id FROM g WHERE FALSE)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT COUNT(*) + fz.id FROM g WHERE FALSE)", unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) FROM g WHERE FALSE)", ""},
            {"SELECT id, id IN (SELECT MAX(v) + fz.id FROM g WHERE FALSE) FROM fz ORDER BY id", unsupportedAt(18)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM em)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g LIMIT 0)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE FALSE GROUP BY g.id)",
                unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id NOT IN (SELECT MAX(v) + fz.id FROM g WHERE FALSE) ORDER BY id",
                unsupportedAt(35)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE FALSE LIMIT 0)", unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE g.id > 100 LIMIT 1)",
                unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE g.id > fz.id + 100)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE g.id = 5)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE g.id = 9)", unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE TRUE)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v + fz.id) FROM g WHERE FALSE)", unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g HAVING FALSE)", ""},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE FALSE OR g.id > 100)",
                unsupportedAt(31)},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) FROM g WHERE g.id = fz.id AND FALSE)", ""},
        });
    }

    @Test
    public void anExistsAggregatingBesideTheOuterRowUnderAFilterThatNeverHolds() {
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) + fz.id FROM g WHERE FALSE) ORDER BY id",
                unsupportedAt(24)},
            {"SELECT id FROM fz WHERE EXISTS (SELECT v + fz.id FROM g WHERE FALSE) ORDER BY id", ""},
            {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) + fz.id FROM g WHERE 1 = 0) ORDER BY id",
                unsupportedAt(24)},
            {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) + fz.id FROM g WHERE g.id > 100) ORDER BY id",
                unsupportedAt(24)},
            {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) FROM g WHERE g.id = fz.id AND FALSE) ORDER BY id",
                "5 ;; 7"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT COUNT(*) FROM g WHERE FALSE) ORDER BY id", "5 ;; 7"},
        });
    }

    @Test
    public void aScalarSubqueryReadingTheOuterRowBesideASettledFilter() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT MAX(v) + fz.id FROM g WHERE FALSE) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(v) + fz.id FROM g) FROM fz ORDER BY id", "5|65 ;; 7|67"},
            {"SELECT id, (SELECT MAX(v) + fz.id FROM g WHERE 1 = 0) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(v) + fz.id FROM g WHERE g.id > 100) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT v + fz.id FROM g WHERE FALSE) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT v + fz.id FROM g WHERE 1 = 0) AS x FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT v + fz.id FROM g WHERE g.id > 100) AS x FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT value || fz.id FROM tt WHERE tt.id = fz.id) AS x FROM fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT fz.id FROM g WHERE FALSE) AS x FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT fz.id FROM tt WHERE tt.id = fz.id) AS x FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT fz.id FROM g WHERE g.id = 5) AS x FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(v) + fz.id FROM g WHERE g.id = 5) AS x FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(v) + fz.id FROM g WHERE TRUE) AS x FROM fz ORDER BY id", unsupportedAt(12)},
        });
    }
}
