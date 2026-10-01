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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a refusal raised inside a subquery is placed: at its place in the STATEMENT, however the subquery is
 * compiled or run (live-verified).
 *
 * <p>A subquery runs from its own text, so a place read off its own parse tree counts from where that text
 * begins. The QUALIFY-without-a-window refusal and the grouped select-list refusal both anchor on a token, and
 * both now add the place the subquery's text starts at — in a select item, a WHERE, an IN, an EXISTS, a HAVING,
 * nested one inside another, and in a view's body — while a query read straight from the statement's own tree,
 * a derived table or a CTE, keeps the place it already had. A HAVING's condition, a join's ON condition and an
 * ORDER BY key place what they raise while rows are read, as a WHERE's does.
 */
public class SubqueryRefusalPlacementTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (1, 10), (5, 50)");
        engine.execute("CREATE OR REPLACE TABLE q (v INT)");
        engine.execute("INSERT INTO q VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        engine.execute("CREATE OR REPLACE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2), (3, 4)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED " + rs.getRowCount();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String noWindow(final int line, final int position) {
        return "SQL compilation error: error line " + line + " at position " + position
            + "|found QUALIFY clause but no window function.";
    }

    private static String ungrouped(final int line, final int position) {
        return "SQL compilation error: error line " + line + " at position " + position
            + "|'FULL_T.B' in select clause is neither an aggregate nor in the group by clause.";
    }

    /** A subquery's QUALIFY is placed in the statement wherever the subquery stands. */
    @Test
    public void aSubquerysQualifyIsPlacedInTheStatement() {
        assertEquals(noWindow(1, 54), answer(
            "SELECT id, (SELECT COUNT(*) FROM g WHERE g.id = fz.id QUALIFY TRUE) AS x FROM fz ORDER BY id"));
        assertEquals(noWindow(1, 47), answer("SELECT id FROM fz WHERE id = (SELECT id FROM g QUALIFY TRUE)"));
        assertEquals(noWindow(1, 48), answer("SELECT id FROM fz WHERE id IN (SELECT id FROM g QUALIFY TRUE)"));
        assertEquals(noWindow(1, 47), answer("SELECT * FROM fz WHERE EXISTS (SELECT 1 FROM g QUALIFY TRUE)"));
        assertEquals(noWindow(1, 53), answer(
            "SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE q.v = 1 QUALIFY TRUE) ORDER BY v"));
        assertEquals(noWindow(1, 32), answer("SELECT 1 WHERE EXISTS (SELECT 1 QUALIFY TRUE)"));
        assertEquals(noWindow(1, 17), answer("SELECT (SELECT 1 QUALIFY TRUE) AS x"));
        assertEquals(noWindow(1, 21), answer("SELECT 1 + (SELECT 1 QUALIFY TRUE) AS x FROM fz"));
    }

    /** Nested subqueries, a derived table inside one, a HAVING, a join condition, and a view's body. */
    @Test
    public void nestedAndClauseShapesArePlacedToo() {
        assertEquals(noWindow(1, 25), answer("SELECT (SELECT (SELECT 1 QUALIFY TRUE)) AS x"));
        assertEquals(noWindow(1, 63), answer(
            "SELECT id FROM fz WHERE id = (SELECT id FROM (SELECT id FROM g QUALIFY TRUE))"));
        assertEquals(noWindow(1, 52), answer("SELECT COUNT(*) FROM fz HAVING COUNT(*) > (SELECT 1 QUALIFY TRUE)"));
        assertEquals(noWindow(1, 45), answer("SELECT * FROM fz JOIN g ON fz.id = (SELECT 1 QUALIFY TRUE)"));
        assertEquals(noWindow(1, 36), answer("CREATE VIEW vq AS SELECT id FROM fz QUALIFY TRUE"));
        assertEquals(noWindow(1, 40), answer("CREATE VIEW vq2 AS SELECT id, (SELECT 1 QUALIFY TRUE) AS x FROM fz"));
    }

    /** A place on a later line keeps its line, counted in the statement. */
    @Test
    public void aLaterLineIsCountedInTheStatement() {
        assertEquals(noWindow(3, 3), answer("SELECT id,\n  (SELECT COUNT(*) FROM g\n   QUALIFY TRUE) AS x FROM fz"));
        assertEquals(noWindow(2, 1), answer("SELECT id, (SELECT COUNT(*) FROM g\n QUALIFY TRUE) AS x FROM fz"));
    }

    /** A derived table, a CTE and a set operation's branch are read from the statement and keep their places. */
    @Test
    public void queriesReadFromTheStatementKeepTheirPlaces() {
        assertEquals(noWindow(1, 18), answer("SELECT id FROM fz QUALIFY TRUE"));
        assertEquals(noWindow(1, 33), answer("SELECT * FROM (SELECT id FROM fz QUALIFY TRUE)"));
        assertEquals(noWindow(1, 29), answer("WITH c AS (SELECT id FROM fz QUALIFY TRUE) SELECT * FROM c"));
        assertEquals(noWindow(1, 37), answer("SELECT 1 UNION ALL SELECT id FROM fz QUALIFY TRUE"));
        assertEquals(noWindow(1, 40), answer("SELECT * FROM fz, LATERAL (SELECT fz.id QUALIFY TRUE)"));
    }

    /** The grouped select-list refusal inside a subquery is placed in the statement too. */
    @Test
    public void aSubquerysUngroupedColumnIsPlacedInTheStatement() {
        assertEquals(ungrouped(1, 7), answer("SELECT b FROM FULL_T GROUP BY a"));
        assertEquals(ungrouped(1, 34), answer("SELECT a FROM T WHERE a = (SELECT b FROM FULL_T GROUP BY a)"));
        assertEquals(ungrouped(1, 15), answer("SELECT (SELECT b FROM FULL_T GROUP BY a) FROM T"));
        assertEquals(ungrouped(1, 37), answer("SELECT a FROM T WHERE EXISTS (SELECT b FROM FULL_T GROUP BY a)"));
        assertEquals(ungrouped(1, 40), answer("SELECT a FROM FULL_T WHERE a IN (SELECT b FROM FULL_T GROUP BY a)"));
        assertEquals(ungrouped(1, 15), answer("SELECT (SELECT b FROM FULL_T GROUP BY a) FROM FULL_T"));
        assertEquals(ungrouped(2, 9), answer("SELECT a FROM T WHERE a = (\n  SELECT b FROM FULL_T GROUP BY a)"));
        assertEquals(ungrouped(1, 25), answer("CREATE VIEW vg AS SELECT b FROM FULL_T GROUP BY a"));
        assertEquals(ungrouped(1, 22), answer("SELECT * FROM (SELECT b FROM FULL_T GROUP BY a)"));
    }

    /** An ORDER BY key's subquery that live cannot evaluate is refused at its SELECT. */
    @Test
    public void anOrderByKeysSubqueryIsPlaced() {
        assertEquals("SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 28",
            answer("SELECT id FROM fz ORDER BY (SELECT v FROM g WHERE g.id = fz.id)"));
    }
}
