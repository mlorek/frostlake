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
 * In a scalar subquery of a select list or a HAVING, MIN or MAX of one bare column of a table the outer
 * query reads is that table's own least or greatest value — whatever the outer WHERE keeps and whichever group
 * the row is in — and COUNT of such a column counts the subquery's rows. The subquery reads one stored table
 * with no filter, join or grouping and writes an aggregate of its own first. SUM, AVG, DISTINCT, LISTAGG, an
 * expression argument, a view or a derived table as the outer relation, an outer aggregate standing alone and
 * a subquery in a WHERE are refused. Every cell is live-verified.
 */
public class OuterAggregateStatisticsTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE fz2 (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz2 VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE h (id INT, v INT)");
        engine.execute("INSERT INTO h VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute("CREATE OR REPLACE VIEW fzv AS SELECT * FROM fz");
    }

    @Test
    public void anOuterMinMaxOrCountBesideAnAggregateOfItsOwnIsAnswered() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT MAX(g.v) + MIN(fz.id) FROM g) FROM fz GROUP BY id ORDER BY id", "5|65 ;; 7|65"},
            {"SELECT id, (SELECT MAX(g.v) + MIN(fz.id) FROM g) FROM fz ORDER BY id", "5|65 ;; 7|65"},
            {"SELECT id, (SELECT COUNT(*) + MIN(fz.id) FROM h) FROM fz ORDER BY id", "5|8 ;; 7|8"},
            {"SELECT id, (SELECT MAX(h.v) + COUNT(fz.id) FROM h) FROM fz ORDER BY id", "5|33 ;; 7|33"},
            {"SELECT id, (SELECT MIN(h.v) + MAX(fz.id) FROM h) FROM fz ORDER BY id", "5|17 ;; 7|17"},
            {"SELECT id, (SELECT MAX(f2.id) + COUNT(fz.id) FROM fz f2) FROM fz ORDER BY id", "5|9 ;; 7|9"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) + 1 FROM fz f2) FROM fz ORDER BY id", "5|13 ;; 7|13"},
            {"SELECT id, (SELECT MAX(f2.id) || MIN(fz.b)::VARCHAR FROM fz f2) FROM fz ORDER BY id",
                "5|7false ;; 7|7false"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz2 f2) FROM fz ORDER BY id", "5|12 ;; 7|12"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2 LIMIT 1) FROM fz ORDER BY id", "5|12 ;; 7|12"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(f2.id) + MIN(fz.id) FROM fz f2) FROM fz ORDER BY id",
                "5|17 ;; 7|17"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(o.id) FROM fz f2) FROM fz o ORDER BY id", "5|12 ;; 7|12"},
            {"SELECT id, (SELECT MAX(g.v) + MIN(h.id) FROM g) FROM h ORDER BY id", "1|61 ;; 2|61 ;; 3|61"},
            {"SELECT id, (SELECT MAX(g.v) + MIN(h.id) FROM g) FROM h WHERE id > 1 ORDER BY id", "2|61 ;; 3|61"},
            {"SELECT id, (SELECT MAX(g.v) + MIN(h.id) + MAX(h.id) FROM g) FROM h ORDER BY id",
                "1|64 ;; 2|64 ;; 3|64"},
            {"SELECT id, (SELECT COUNT(*) + MIN(h.id) FROM g) FROM h ORDER BY id", "1|3 ;; 2|3 ;; 3|3"},
            {"SELECT COUNT(*) FROM (SELECT id, (SELECT MAX(g.v) + MIN(h.id) FROM g) x FROM h WHERE FALSE)", "0"},
            {"SELECT id FROM h GROUP BY id HAVING (SELECT MAX(g.v) + MIN(h.id) FROM g) > 0 ORDER BY id",
                "1 ;; 2 ;; 3"},
            {"SELECT id, (SELECT MAX(g.v) + MIN(h.id) + h.id FROM g) FROM h ORDER BY id", "1|62 ;; 2|63 ;; 3|64"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2) FROM fz GROUP BY id ORDER BY id",
                "5|12 ;; 7|12"},
            {"SELECT b, (SELECT MAX(f2.id) - MAX(fz.id) FROM fz f2) FROM fz GROUP BY b ORDER BY b",
                "false|0 ;; true|0"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2) FROM fz ORDER BY id", "5|12 ;; 7|12"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2) FROM fz WHERE id > 5 GROUP BY id ORDER BY id",
                "7|12"},
            {"SELECT fz.id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2) FROM fz JOIN g ON fz.id = g.id GROUP BY "
                + "fz.id ORDER BY 1",
                "5|12"},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2) > 0 ORDER BY b",
                "false ;; true"},
            {"SELECT (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2) FROM fz", "12 ;; 12"},
            {"SELECT MAX(id), (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2) FROM fz", "7|12"},
            {"SELECT id, (SELECT COUNT(*) + MIN(fz.id) FROM fz f2) FROM fz GROUP BY id ORDER BY id", "5|7 ;; 7|7"},
        });
    }

    @Test
    public void everyOtherOuterAggregateIsRefused() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT COUNT(*) + SUM(fz.id) FROM h) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(h.v) + AVG(fz.id) FROM h) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MIN(fz.id) FROM fz f2) FROM fz GROUP BY id ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(f2.id) + SUM(fz.id) FROM fz f2) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id + 1) FROM fz f2) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fzv.id) FROM fz f2) FROM fzv ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(d.id) FROM fz f2) FROM (SELECT * FROM fz) d ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id FROM fz WHERE id = (SELECT MAX(f2.id) + MIN(fz.id) - 7 FROM fz f2) ORDER BY id",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function "
                    + "[MIN(FZ.ID)] can only appear in having or select clause"},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2, g) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(g.v) + SUM(h.v) FROM g) FROM h ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(g.v) + SUM(h.v) FROM g) FROM h GROUP BY id ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MIN(h.id) FROM g) FROM h ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MIN(h.id) + 1 FROM g) FROM h ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(g.v) + MIN(h.id) FROM g WHERE g.id > 5) FROM h ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(g.v) + MIN(h.id) FROM g WHERE FALSE) FROM h ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(g.v) + COUNT(DISTINCT h.id) FROM g) FROM h ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(g.v + h.id) FROM g) FROM h ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(g.v) || '-' || LISTAGG(h.id, ',') FROM g) FROM h ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT MIN(fz.id) + MAX(fz.id) + COUNT(f2.id) FROM fz f2) FROM fz GROUP BY id ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT MIN(fz.id) FROM g) FROM fz GROUP BY id ORDER BY id", unsupportedAt(12)},
            {"SELECT b, (SELECT MIN(fz.id) FROM g) FROM fz GROUP BY b ORDER BY b", unsupportedAt(11)},
            {"SELECT b, (SELECT COUNT(*) + SUM(fz.id) FROM g) FROM fz GROUP BY b ORDER BY b", unsupportedAt(11)},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2 WHERE f2.id > 5) FROM fz GROUP BY id ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(f2.id) + MIN(fz.id) FROM fz f2 GROUP BY f2.b ORDER BY 1 LIMIT 1) FROM fz "
                + "GROUP BY id ORDER BY id",
                unsupportedAt(12)},
            {"SELECT id, (SELECT MAX(f2.id + fz.id) FROM fz f2) FROM fz GROUP BY id ORDER BY id", unsupportedAt(12)},
            {"SELECT id, (SELECT COUNT(DISTINCT fz.id) + MAX(f2.id) FROM fz f2) FROM fz GROUP BY id ORDER BY id",
                unsupportedAt(12)},
        });
    }
}
