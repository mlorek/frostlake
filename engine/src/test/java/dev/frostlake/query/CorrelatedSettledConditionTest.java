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
 * A condition the relations' statistics settle is folded to its answer wherever it stands, so the outer name
 * inside it correlates nothing: a disjunct that never holds, a conjunct that always does, the element of an
 * IN list the subject never equals, the condition of an IFF or a CASE, a comparison in the select list. Over
 * tt.id holding 1 and 2 and fz.id holding 5 and 7, {@code tt.id = fz.id OR tt.id = 1} reads tt alone. A
 * condition the statistics leave open, or the outer row read beside it, keeps the refusal. Every cell is
 * live-verified.
 */
public class CorrelatedSettledConditionTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        engine.execute("INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
    }

    @Test
    public void aSettledConditionCorrelatesNothing() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id OR tt.id = 1) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = 1 OR tt.id = fz.id) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE (tt.id = fz.id OR tt.id = 1) AND tt.value = 'A') AS x FROM "
                + "fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id < fz.id AND tt.id = 1) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id > fz.id OR tt.id = fz.id OR tt.id = 1) AS x FROM fz "
                + "ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE NOT (tt.id = fz.id) AND tt.id = 1) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE (tt.id = fz.id AND tt.value = 'A') OR tt.id = 1) AS x FROM "
                + "fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE IFF(tt.id = fz.id, TRUE, tt.id = 1)) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT MAX(value) FROM tt WHERE tt.id = fz.id OR tt.id = 1) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT COUNT(*) FROM tt WHERE tt.id = fz.id OR tt.id = 1) AS x FROM fz ORDER BY id",
                "5|1 ;; 7|1"},
            {"SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id OR g.id = 5) AS x FROM fz ORDER BY id",
                "5|50 ;; 7|50"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = 1 AND (tt.id > fz.id OR tt.value = 'A')) AS x FROM "
                + "fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id IN (fz.id, 1)) AS x FROM fz ORDER BY id", "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id OR FALSE) AS x FROM fz ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM tt WHERE tt.id = fz.id OR tt.id = 1) ORDER BY id",
                "5 ;; 7"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = fz.id OR g.id = 5) ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE 'A' IN (SELECT value FROM tt WHERE tt.id = fz.id OR tt.id = 1) ORDER BY id",
                "5 ;; 7"},
            {"SELECT id FROM fz WHERE (SELECT value FROM tt WHERE tt.id = fz.id OR tt.id = 1) = 'A' ORDER BY id",
                "5 ;; 7"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id NOT IN (fz.id, 2)) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id BETWEEN fz.id AND 9 OR tt.id = 1) AS x FROM fz ORDER "
                + "BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE CASE WHEN tt.id = fz.id THEN TRUE ELSE tt.id = 1 END) AS x "
                + "FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = 1 AND tt.id + fz.id > 0) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = 1 AND fz.id > 0) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = 1 AND fz.id > 100) AS x FROM fz ORDER BY id",
                "5|null ;; 7|null"},
            {"SELECT id, (SELECT IFF(tt.id = fz.id, 'x', value) FROM tt WHERE tt.id = 1) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT tt.id = fz.id FROM tt WHERE tt.id = 1) AS x FROM fz ORDER BY id",
                "5|false ;; 7|false"},
            {"SELECT id, (SELECT value FROM tt WHERE (tt.id = fz.id) = FALSE AND tt.id = 1) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM tt WHERE tt.id = fz.id OR tt.value = "
                + "fz.id::VARCHAR) ORDER BY id",
                ""},
            {"SELECT id, (SELECT MAX(value) FROM tt WHERE tt.id IN (fz.id, 1)) AS x FROM fz ORDER BY id",
                "5|A ;; 7|A"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id < fz.id OR tt.id = 1) AS x FROM fz ORDER BY id",
                "REFUSED Single-row subquery returns more than one row."},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id OR tt.id = 1 OR tt.id = 2) AS x FROM fz "
                + "ORDER BY id",
                "REFUSED Single-row subquery returns more than one row."},
        });
    }

    @Test
    public void aConditionLeftOpenKeepsTheRefusal() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id OR tt.value = fz.id::VARCHAR) AS x FROM fz "
                + "ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id OR g.id = 5) AS x FROM fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT value || fz.id FROM tt WHERE tt.id = fz.id OR tt.id = 1) AS x FROM fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = 1 AND fz.id > 5) AS x FROM fz ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT v FROM g WHERE g.id IN (fz.id, 5)) AS x FROM fz ORDER BY id", unsupportedAt(12)},
        });
    }
}
