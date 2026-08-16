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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live evaluates a correlated subquery only in the shapes its planner can turn into a join, and refuses the
 * rest as "Unsupported subquery type cannot be evaluated", positioned at the subquery's SELECT (an EXISTS at
 * its keyword), when a row first needs it. A value subquery must promise one row by its shape: an aggregate
 * with no GROUP BY or LIMIT, or a FROM-less select with no filter. Its filters, and an EXISTS or IN
 * subquery's, may read the outer row only through comparisons that keep each operand to one side. Live
 * answers from metadata first, so a subquery over an empty table, or over an outer table of one row, is
 * never refused. Every cell is live-verified.
 */
public class CorrelatedSubqueryShapeTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** A correlated subquery read as a value: an aggregate with no GROUP BY or LIMIT, or a FROM-less select with no filter. */
    @Test
    public void aValueSubqueryMustPromiseOneRowByItsShape() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P452_DB");
            engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
            engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
            engine.execute("CREATE OR REPLACE TABLE h (k INT, w INT)");
            engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
            assertRefused("SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT w FROM h WHERE k = id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM g WHERE g.id = fz.id LIMIT 1) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM g WHERE g.id = 5 AND fz.id = 5) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT MIN(v) FROM g WHERE g.id = fz.id GROUP BY g.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT b FROM fz i WHERE i.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT x FROM (SELECT id AS x FROM g) WHERE x = id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT (SELECT w FROM h WHERE k = id) FROM g WHERE g.id = 5) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated");
            assertRefused("SELECT id FROM fz WHERE (SELECT v FROM g WHERE g.id = fz.id) = 50",
                "Unsupported subquery type cannot be evaluated at line 1, position 25");
            assertEquals("",
                rows("SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE FALSE"));
            assertRefused("SELECT id FROM fz ORDER BY (SELECT v FROM g WHERE g.id = fz.id)",
                "Unsupported subquery type cannot be evaluated");
            assertRefused("SELECT id, (SELECT g.v + fz.id FROM g WHERE g.id = 5) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, null | 7, 60",
                rows("SELECT id, (SELECT MAX(v) FROM g WHERE g.id < fz.id) AS x FROM fz ORDER BY id"));
            assertEquals("5, 1 | 7, 0",
                rows("SELECT id, (SELECT COUNT(*) FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT ANY_VALUE(v) FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT SUM(v + fz.id) FROM g) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 110 | 7, 110",
                rows("SELECT id, (SELECT SUM(v) FROM g HAVING SUM(v) > fz.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT SUM(v) FROM g HAVING fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT SUM(v) FROM g WHERE fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT COUNT(*) FROM g WHERE g.id = fz.id OR fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 1 | 7, 2",
                rows("SELECT id, (SELECT COUNT(DISTINCT v) FROM g WHERE g.id <= fz.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id LIMIT 1) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id ORDER BY 1) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT DISTINCT MAX(v) FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id"));
            assertEquals("5, 500 | 7, null",
                rows("SELECT id, (SELECT MAX(w) FROM g JOIN h ON h.k = g.id WHERE g.id = fz.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id UNION ALL SELECT 1) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id HAVING COUNT(*) > 0) AS x FROM fz ORDER BY id"));
            assertEquals("5, 110 | 7, 110",
                rows("SELECT id, (SELECT SUM(v) FROM g HAVING SUM(v) > fz.id AND COUNT(*) > 1) AS x FROM fz ORDER BY id"));
            assertEquals("5, 1 | 7, 0",
                rows("SELECT id, (SELECT COUNT(*) FROM g WHERE g.id = fz.id AND g.v > fz.id) AS x FROM fz ORDER BY id"));
            assertEquals("5, 6 | 7, 8",
                rows("SELECT id, (SELECT fz.id + 1) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT fz.id WHERE fz.id > 5) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 0 | 7, 1",
                rows("SELECT id, (SELECT COUNT(*) WHERE fz.id > 5) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT fz.id WHERE TRUE) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT 1 WHERE fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 5 | 7, 7",
                rows("SELECT id, (SELECT fz.id LIMIT 1) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT fz.id ORDER BY 1) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 5 | 7, 7",
                rows("SELECT id, (SELECT DISTINCT fz.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT fz.id + 1 UNION ALL SELECT 2) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT MAX(fz.id)) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT (SELECT fz.id WHERE fz.id > 5)) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated");
            assertRefused("SELECT id, (SELECT 1 HAVING fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("UPDATE fz SET b = (SELECT v FROM g WHERE g.id = fz.id) > 0",
                "Unsupported subquery type cannot be evaluated at line 1, position 19");
            engine.execute("UPDATE fz SET b = (SELECT MAX(v) FROM g WHERE g.id = fz.id) > 0");
            assertEquals("5, true | 7, null",
                rows("SELECT id, b FROM fz ORDER BY id"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P452_DB");
        }
    }

    /** A correlated EXISTS or IN subquery: any shape but a LIMIT or a set operation, its filters held to the same rule. */
    @Test
    public void existsAndInTakeAnyShapeButALimitOrASetOperation() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P452_DB");
            engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
            engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
            engine.execute("CREATE OR REPLACE TABLE h (k INT, w INT)");
            engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
            assertEquals("5",
                rows("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = fz.id) ORDER BY id"));
            assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 WHERE fz.b) ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 24");
            assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE fz.b) ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 24");
            assertEquals("5",
                rows("SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.v > fz.id) ORDER BY id"));
            assertEquals("5 | 7",
                rows("SELECT id FROM fz WHERE id IN (SELECT fz.id) ORDER BY id"));
            assertEquals("5, true | 7, false",
                rows("SELECT id, EXISTS (SELECT 1 FROM g WHERE g.id = fz.id) AS e FROM fz ORDER BY id"));
            assertEquals("7",
                rows("SELECT id FROM fz WHERE NOT EXISTS (SELECT 1 FROM g WHERE g.id = fz.id) ORDER BY id"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P452_DB");
        }
    }

    /** A filter may read the outer row only through comparisons whose operands keep to one side; an empty outer query never meets the refusal. */
    @Test
    public void aFilterReadsTheOuterRowOnlyThroughOneSidedComparisons() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P452B_DB");
            engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
            engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
            engine.execute("CREATE OR REPLACE TABLE e (id INT)");
            engine.execute("CREATE OR REPLACE TABLE gu (id INT PRIMARY KEY, v INT)");
            engine.execute("INSERT INTO gu VALUES (5, 50), (6, 60)");
            assertEquals("5, null | 7, 110",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE fz.id > 5) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id = fz.id + 0) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT SUM(v) FROM g WHERE g.id + fz.id = 10) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT SUM(v) FROM g WHERE NOT (g.id = fz.id)) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 110 | 7, 60",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id IN (fz.id, 6)) AS x FROM fz ORDER BY id"));
            assertEquals("5, 110 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id BETWEEN fz.id AND 10) AS x FROM fz ORDER BY id"));
            assertEquals("5, 110 | 7, 60",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id = fz.id OR g.id = 6) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id LIMIT 5) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 100 | 7, null",
                rows("SELECT id, (SELECT MAX(v) * 2 FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id"));
            assertEquals("5, 60 | 7, 60",
                rows("SELECT id, (SELECT COALESCE(MAX(v), fz.id) FROM g) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT COUNT(*) WHERE fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT COUNT(*) HAVING fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 110 | 7, 110",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.v > 0 HAVING SUM(v) > fz.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id AND fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id AND 1 = 1) AS x FROM fz ORDER BY id"));
            assertEquals("",
                rows("SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE NULL"));
            assertEquals("",
                rows("SELECT id, (SELECT v FROM g WHERE g.id = e.id) AS x FROM e"));
            assertEquals("",
                rows("SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE id > 100"));
            assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = fz.id OR fz.b) ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 24");
            assertRefused("SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE fz.b) ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 31");
            engine.execute("CREATE OR REPLACE VIEW vv AS SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz");
            assertRefused("SELECT id, (SELECT v FROM g WHERE g.id = fz.id AND g.id = 5) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM gu WHERE gu.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT COUNT(*) FROM g WHERE g.id = fz.id QUALIFY TRUE) AS x FROM fz ORDER BY id",
                "found QUALIFY clause but no window function.");
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT (SELECT MAX(v) FROM g WHERE g.id = fz.id)) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = (SELECT fz.id)) AS x FROM fz ORDER BY id"));
            assertEquals("",
                rows("SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE FALSE AND id > 0"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = fz.id AND g.v = fz.id * 10) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT ANY_VALUE(v) FROM g WHERE g.id = fz.id ORDER BY 1 LIMIT 1) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT fz.id + 1 LIMIT 0) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P452B_DB");
        }
    }

    /** More filter shapes, EXISTS and IN over them, and a correlation that reaches the outer row through a nested subquery. */
    @Test
    public void filtersExistsAndNestingMoreShapes() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P452C_DB");
            engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
            engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
            engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
            engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
            engine.execute("CREATE OR REPLACE TABLE h (k INT, w INT)");
            engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
            assertEquals("5, 110 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE fz.b = TRUE) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT SUM(v) FROM g WHERE ABS(g.id - fz.id) < 2) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id = ABS(fz.id)) AS x FROM fz ORDER BY id"));
            assertEquals("5, null | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE fz.id IS NULL) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.s LIKE fz.s) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id = fz.id AND g.s = fz.s) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE (g.id = fz.id)) AS x FROM fz ORDER BY id"));
            assertEquals("5, 60 | 7, 110",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id <> fz.id) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE fz.id = g.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT SUM(v) FROM g WHERE NOT fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 50 | 7, 50",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id IN (SELECT h.k FROM h WHERE h.w > fz.id)) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT SUM(v) FROM g WHERE CASE WHEN fz.b THEN g.id = 5 ELSE g.id = 6 END) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 110 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id = fz.id OR g.id = fz.id + 1) AS x FROM fz ORDER BY id"));
            assertEquals("5, 110 | 7, 110",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE g.id > 0 OR fz.id > 6) AS x FROM fz ORDER BY id"));
            assertEquals("5, null | 7, 110",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE fz.id > g.id + 0) AS x FROM fz ORDER BY id"));
            assertEquals("5, 50 | 7, null",
                rows("SELECT id, (SELECT SUM(v) FROM g WHERE COALESCE(g.id, 0) = fz.id) AS x FROM fz ORDER BY id"));
            assertEquals("7",
                rows("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE fz.id > 5) ORDER BY id"));
            assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id + fz.id = 10) ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 24");
            assertEquals("5",
                rows("SELECT id FROM fz WHERE EXISTS (SELECT g.id FROM g WHERE g.id = fz.id GROUP BY g.id) ORDER BY id"));
            assertRefused("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 24");
            assertRefused("SELECT id FROM fz WHERE id IN (SELECT g.id FROM g WHERE g.id = fz.id UNION SELECT 7) ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 31");
            assertRefused("SELECT id FROM fz WHERE NOT EXISTS (SELECT 1 FROM g WHERE fz.b) ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 28");
            assertRefused("SELECT id, (SELECT (SELECT MAX(w) FROM h WHERE h.k = fz.id) FROM g WHERE g.id = 5) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, 500 | 7, 500",
                rows("SELECT id, (SELECT (SELECT MAX(w) FROM h WHERE h.k = g.id) FROM g WHERE g.id = 5) AS x FROM fz ORDER BY id"));
            assertEquals("5, null | 7, null",
                rows("SELECT id, (SELECT MAX(v) FROM g WHERE g.id = (SELECT MAX(k) FROM h WHERE h.w > fz.id)) AS x FROM fz ORDER BY id"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P452C_DB");
        }
    }

    /** The refusal where the subquery sits: a function argument, a WHERE, an UPDATE or DELETE, a LATERAL or a LIMITed query. */
    @Test
    public void theRefusalReachesEveryPlacement() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P452C_DB");
            engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
            engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
            engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT, s VARCHAR)");
            engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
            engine.execute("CREATE OR REPLACE TABLE h (k INT, w INT)");
            engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
            assertEquals("5, 51 | 7, null",
                rows("SELECT id, (SELECT SUM(v) + 1 FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, COALESCE((SELECT v FROM g WHERE g.id = fz.id), 0) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 21");
            assertRefused("SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz WHERE id = 5",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("DELETE FROM fz WHERE (SELECT v FROM g WHERE g.id = fz.id) = 50",
                "Unsupported subquery type cannot be evaluated at line 1, position 22");
            assertEquals("5, 50",
                rows("SELECT fz.id, l.v FROM fz, LATERAL (SELECT v FROM g WHERE g.id = fz.id) l ORDER BY fz.id"));
            assertRefused("SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz LIMIT 1",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P452C_DB");
        }
    }

    /** Metadata first: a subquery over an empty table is pruned, and one over an outer table of one row is read row by row. */
    @Test
    public void liveAnswersFromMetadataBeforeTheShape() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P452D_DB");
            engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
            engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
            engine.execute("CREATE OR REPLACE TABLE g1 (id INT, v INT)");
            engine.execute("INSERT INTO g1 VALUES (5, 50)");
            engine.execute("CREATE OR REPLACE TABLE g0 (id INT, v INT)");
            engine.execute("CREATE OR REPLACE TABLE g2 (id INT, v INT)");
            engine.execute("INSERT INTO g2 VALUES (5, 50), (6, 60)");
            engine.execute("CREATE OR REPLACE TABLE h1 (k INT, w INT)");
            engine.execute("INSERT INTO h1 VALUES (5, 500)");
            engine.execute("CREATE OR REPLACE TABLE test_table (id INT, value VARCHAR)");
            engine.execute("INSERT INTO test_table VALUES (1, 'A'), (2, 'B')");
            engine.execute("CREATE OR REPLACE TABLE target (id INT, value VARCHAR)");
            engine.execute("INSERT INTO target VALUES (1, 'X')");
            engine.execute("CREATE OR REPLACE TABLE raw_events (id INT, src VARIANT)");
            engine.execute("INSERT INTO raw_events SELECT 1, PARSE_JSON('[{\"grp\":{\"guid\":\"g1\"},\"inst\":{\"iid\":\"i1\"}},{\"grp\":{\"guid\":\"g2\"},\"inst\":{\"iid\":\"i2\"}}]')");
            engine.execute("CREATE OR REPLACE TABLE parents (pid VARCHAR, pkey VARCHAR)");
            engine.execute("INSERT INTO parents VALUES ('PFX:G1', 'K1')");
            engine.execute("CREATE OR REPLACE VIEW vg1 AS SELECT * FROM g1");
            assertRefused("SELECT id, (SELECT v FROM g1 WHERE g1.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("5, null | 7, null",
                rows("SELECT id, (SELECT v FROM g0 WHERE g0.id = fz.id) AS x FROM fz ORDER BY id"));
            assertRefused("SELECT id, (SELECT v FROM g2 WHERE g2.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (WITH c AS (SELECT id, v FROM g2 WHERE id = 5) SELECT v FROM c WHERE c.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM (SELECT id, v FROM g2 WHERE id = 5) d WHERE d.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            engine.execute("UPDATE target SET value = (WITH cte AS (SELECT id, value FROM test_table WHERE id = 1) SELECT value FROM cte WHERE cte.id = target.id)");
            assertEquals("1, A",
                rows("SELECT id, value FROM target"));
            assertRefused("SELECT id, (SELECT v FROM g1 WHERE g1.id <> fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM g1, h1 WHERE g1.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM g1 WHERE g1.id = fz.id UNION ALL SELECT 1) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM vg1 WHERE vg1.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("K1 | K1",
                rows("SELECT (SELECT pkey FROM parents WHERE pkey <> s.id::VARCHAR) k FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))"));
            assertRefused("SELECT id, (SELECT v FROM g1 WHERE g1.id = fz.id LIMIT 1) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM g1 WHERE fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT MAX(v) FROM g1 WHERE fz.b) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v + fz.id FROM g1) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertRefused("SELECT id, (SELECT v FROM g1 WHERE g1.id = fz.id ORDER BY v) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            assertEquals("PFX:G1, K1",
                rows("SELECT p.pid, (SELECT pkey FROM parents q WHERE q.pid = p.pid) k FROM parents p"));
            engine.execute("INSERT INTO g1 VALUES (6, 60)");
            assertRefused("SELECT id, (SELECT v FROM g1 WHERE g1.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            engine.execute("DELETE FROM g2 WHERE id = 6");
            assertRefused("SELECT id, (SELECT v FROM g2 WHERE g2.id = fz.id) AS x FROM fz ORDER BY id",
                "Unsupported subquery type cannot be evaluated at line 1, position 12");
            engine.execute("INSERT INTO parents VALUES ('PFX:G2', 'K2')");
            assertRefused("SELECT (SELECT pkey FROM parents WHERE pkey <> s.id::VARCHAR) k FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))",
                "Single-row subquery returns more than one row.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P452D_DB");
        }
    }
}
