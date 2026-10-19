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
 * An aggregate a subquery writes over its own row and the outer one is the subquery's, so the outer name it
 * reads is held to the enclosing query's grouping, and in the subquery's WHERE it is refused with the outer
 * name echoed as a correlation: {@code MAX(CORRELATION(FZ.ID) + G.V)}. That refusal is the subquery's own,
 * reported ahead of a name an aggregate of its select list reads; a QUALIFY with no window function and an
 * operand of the wrong type are reported ahead of both. Every cell is live-verified.
 */
public class CorrelatedAggregateEchoTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    @Test
    public void theRefusalEchoesTheOuterNameAsACorrelation() {
        assertCells(new String[][] {
            {"SELECT id FROM fz GROUP BY id HAVING (SELECT MAX(g.v) FROM g WHERE g.id < MAX(fz.id + g.v)) > 55",
                "REFUSED SQL compilation error: | Invalid aggregate function in where clause "
                    + "[MAX(CORRELATION(FZ.ID) + G.V)]"},
            {"SELECT b, (SELECT MAX(g.v) FROM g WHERE g.id < MAX(fz.id + g.v)) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: | Invalid aggregate function in where clause "
                    + "[MAX(CORRELATION(FZ.ID) + G.V)]"},
            {"SELECT b FROM fz GROUP BY b QUALIFY (SELECT MAX(g.v) FROM g WHERE g.id < MAX(fz.id + g.v)) > 55",
                "REFUSED SQL compilation error: error line 1 at position 28 | found QUALIFY clause but no "
                    + "window function."},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(g.v) FROM g WHERE g.id < MAX(g.v + fz.b)) > 55",
                "REFUSED SQL compilation error: error line 1 at position 80 | Invalid argument types for "
                    + "function '+': (NUMBER(38,0), BOOLEAN)"},
            {"SELECT b, (SELECT MAX(fz.id + g.v) FROM g WHERE g.id < MAX(fz.id + g.v)) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: | Invalid aggregate function in where clause "
                    + "[MAX(CORRELATION(FZ.ID) + G.V)]"},
            {"SELECT b FROM fz GROUP BY b ORDER BY (SELECT MAX(fz.id + g.v) FROM g)",
                "REFUSED SQL compilation error: | [(SELECT MAX(CORRELATION(FZ.ID) + G.V) AS \"MAX(FZ.ID + G.V)\" "
                    + "FROM G AS G)] is not a valid order by expression"},
        });
    }

    @Test
    public void theOuterNameIsHeldToTheEnclosingGrouping() {
        assertCells(new String[][] {
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(g.v) FROM g WHERE g.id < MAX(fz.id + g.v)) > 55",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b, (SELECT MAX(fz.id + g.v) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 22 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, (SELECT SUM(fz.id + g.v) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 22 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, (SELECT COUNT(fz.id + g.v) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 24 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, (SELECT MAX(g.v + fz.id) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 28 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, (SELECT MAX(fz.id + g.v) FROM g), id FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 22 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, id, (SELECT MAX(fz.id + g.v) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 10 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(fz.id + g.v) FROM g) > 55",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(g.v) FROM g HAVING MAX(fz.id + g.v) > 0) > 55",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT MAX(id), (SELECT MAX(fz.id + g.v) FROM g) FROM fz",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT id, (SELECT MAX(fz.id + g.v) FROM g) FROM fz GROUP BY id ORDER BY id", unsupportedAt(12)},
            {"SELECT id FROM fz GROUP BY id HAVING (SELECT MAX(fz.id + g.v) FROM g) > 55 ORDER BY id",
                unsupportedAt(38)},
            {"SELECT id, (SELECT MAX(fz.id + g.v) FROM g) FROM fz ORDER BY id", unsupportedAt(12)},
            {"SELECT b FROM fz GROUP BY b HAVING EXISTS (SELECT MAX(fz.id + g.v) FROM g)",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b FROM fz GROUP BY b HAVING 55 IN (SELECT MAX(fz.id + g.v) FROM g)",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT b, EXISTS (SELECT MAX(fz.id + g.v) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 29 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, 55 IN (SELECT MAX(fz.id + g.v) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 28 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, (SELECT MAX(g.v + fz.b::INT + fz.id) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 40 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, (SELECT MAX(fz.id + g.v) FROM g WHERE g.id > 0) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 22 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, COUNT(*), (SELECT SUM(g.v + fz.id) FROM g) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 38 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT id + 1, (SELECT MAX(fz.id + g.v) FROM g) FROM fz GROUP BY id + 1",
                "REFUSED SQL compilation error: error line 1 at position 27 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, (SELECT MAX(id + v) FROM g g2) FROM fz GROUP BY b", "true|66 ;; false|66"},
            {"SELECT b, (SELECT MAX(b::INT + v) FROM g) FROM fz GROUP BY b ORDER BY b", unsupportedAt(11)},
            {"SELECT b FROM fz GROUP BY b HAVING (SELECT MAX(g.v + nope) FROM g) > 55",
                "REFUSED SQL compilation error: error line 1 at position 53 | invalid identifier 'NOPE'"},
            {"SELECT b FROM fz GROUP BY b QUALIFY (SELECT MAX(fz.id + g.v) FROM g) > 55",
                "REFUSED SQL compilation error: error line 1 at position 28 | found QUALIFY clause but no "
                    + "window function."},
            {"SELECT b, (SELECT g.v + fz.id FROM g LIMIT 1) FROM fz GROUP BY b",
                "REFUSED SQL compilation error: error line 1 at position 24 | 'FZ.ID' in select clause is "
                    + "neither an aggregate nor in the group by clause."},
            {"SELECT b, (SELECT MAX(fz.id) + MAX(g.v) FROM g) FROM fz GROUP BY b ORDER BY b", unsupportedAt(11)},
        });
    }
}
