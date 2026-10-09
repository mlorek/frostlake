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
 * A subquery reading the outer row through a table function is read row by row only over a relation of
 * exactly one row whose scan the planner does not prune: over an empty table, and under {@code WHERE FALSE},
 * {@code WHERE NULL} or a filter the statistics rule out over a one-row table, it is refused, where
 * {@code WHERE 1 = 0} and {@code LIMIT 0} over the one-row table leave it read. A WHERE the planner prunes
 * drops the subqueries it holds. Every other correlated shape is read row by row over an empty table. Every
 * cell is live-verified.
 */
public class TableFunctionOuterRowCountTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE em (id INT, v INT, a INT)");
        engine.execute("CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO one VALUES (5, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE ev (id INT, arr ARRAY)");
        engine.execute("INSERT INTO ev SELECT 1, ARRAY_CONSTRUCT(1, 2) UNION ALL SELECT 2, ARRAY_CONSTRUCT(3)");
    }

    @Test
    public void aTableFunctionReadingTheOuterRowNeedsExactlyOneUnprunedRow() {
        assertCells(new String[][] {
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a, 5)))) FROM em E",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E", "2"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a, 5)))) FROM em E WHERE FALSE",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E WHERE FALSE",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E WHERE 1 = 0",
                ""},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM fz E WHERE FALSE",
                unsupportedAt(8)},
            {"SELECT id FROM em E WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))))",
                unsupportedAt(26)},
            {"SELECT id FROM em E WHERE 5 IN (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a, 5))))",
                unsupportedAt(32)},
            {"SELECT (SELECT MAX(value) FROM TABLE(SPLIT_TO_TABLE(E.id::VARCHAR, ','))) FROM em E", unsupportedAt(8)},
            {"SELECT (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a)))) FROM em E", unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM LATERAL FLATTEN(input => ARRAY_CONSTRUCT(E.a, 5))) FROM em E",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E WHERE "
                + "id > 100",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E WHERE "
                + "id = 6",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM fz E WHERE 1 = 0",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM em E WHERE 1 = 0",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E LIMIT 0",
                ""},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM em E LIMIT 0",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E WHERE NULL",
                unsupportedAt(8)},
            {"SELECT id FROM one E WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id))))", "5"},
            {"SELECT id FROM one E WHERE FALSE AND EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => "
                + "ARRAY_CONSTRUCT(E.id))))",
                ""},
            {"SELECT id FROM em E WHERE FALSE AND EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => "
                + "ARRAY_CONSTRUCT(E.id))))",
                ""},
            {"SELECT id FROM em E WHERE (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) > 0",
                unsupportedAt(27)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E JOIN "
                + "one F ON TRUE",
                "2"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM one E JOIN "
                + "em F ON TRUE",
                ""},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM one) E",
                "2"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM em) E",
                unsupportedAt(8)},
            {"SELECT id, x.c FROM ev E, LATERAL (SELECT COUNT(*) c FROM TABLE(FLATTEN(E.arr))) x ORDER BY id",
                UNSUPPORTED},
        });
    }

    @Test
    public void everyOtherShapeIsReadRowByRowOverAnEmptyTable() {
        assertCells(new String[][] {
            {"SELECT id, (SELECT v FROM g WHERE g.id = E.id) FROM em E", ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id > E.id) FROM em E", ""},
            {"SELECT id, (SELECT g.v + E.id FROM g) FROM em E", ""},
            {"SELECT id, (SELECT MAX(v) FROM g WHERE g.id = E.id LIMIT 1) FROM em E", ""},
            {"SELECT id FROM em E WHERE EXISTS (SELECT id FROM g WHERE g.id = E.id LIMIT 1)", ""},
            {"SELECT id FROM em E WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM g g2 WHERE g2.id = "
                + "E.id))",
                ""},
            {"SELECT id, (SELECT v FROM g WHERE g.id = E.id) FROM one E", "5|50"},
            {"SELECT id FROM one E WHERE EXISTS (SELECT 1 FROM g WHERE EXISTS (SELECT 1 FROM g g2 WHERE g2.id = "
                + "E.id))",
                "5"},
        });
    }

    @Test
    public void overADerivedRelationOnlyItsOwnPruningCounts() {
        assertCells(new String[][] {
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM fz WHERE 1 = 0) E",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM one WHERE FALSE) E",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM one WHERE 1 = 0) E",
                ""},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM one) E LIMIT 0",
                ""},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM one) E WHERE 1 = 0",
                ""},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM one) E WHERE FALSE",
                ""},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM one LIMIT 0) E",
                ""},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM one LIMIT 1) E",
                "2"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM em) E WHERE 1 = 0",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM fz) E",
                unsupportedAt(8)},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.id, 5)))) FROM (SELECT * "
                + "FROM fz LIMIT 1) E",
                unsupportedAt(8)},
        });
    }
}
