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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A subquery written as an ORDER BY key raises its own refusal, placed where it stands in the statement — its
 * QUALIFY without a window, its grouped select list, its argument types, a second row it returns — never an
 * invalid identifier naming the key's whole text. A FROM-less select's ORDER BY is judged too, though its one
 * row needs no sort: its names, its ordinals, its function names, its types and its subqueries, while a key that
 * would fault only on a row is answered. A set operation's key reads none of the output's names inside its
 * subquery. All live-verified.
 */
public class OrderBySubqueryKeyRefusalTest extends BaseDatabaseTest {

    private static final String NO_WINDOW = "found QUALIFY clause but no window function.";
    private static final String UNGROUPED = "'FULL_T.B' in select clause is neither an aggregate nor in the group by clause.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE TABLE full_t (a INT, b INT)");
        engine.execute("INSERT INTO full_t VALUES (1, 2), (3, 4)");
    }

    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "");
            for (int c = 0; c < result.getColumnCount(); c++) {
                text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
        }
        return text.toString();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String at(final int position, final String sentence) {
        return "SQL compilation error: error line 1 at position " + position + "\n" + sentence;
    }

    @Test
    public void aSortedKeyRaisesItsSubquerysOwnRefusal() {
        assertEquals(at(37, NO_WINDOW), refusal("SELECT id FROM fz ORDER BY (SELECT 1 QUALIFY TRUE)"));
        assertEquals(at(38, UNGROUPED), refusal("SELECT a FROM FULL_T ORDER BY (SELECT b FROM FULL_T GROUP BY a)"));
        assertEquals(at(39, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT id FROM fz ORDER BY (SELECT 'x' + TRUE)"));
        assertEquals(at(28, "Unsupported: Scalar subquery with multi-column SELECT clause."),
            refusal("SELECT id FROM fz ORDER BY (SELECT 1, 2)"));
        assertEquals("SQL compilation error:\nInvalid aggregate function in where clause [SUM(G.V)]",
            refusal("SELECT id FROM fz ORDER BY (SELECT id FROM g WHERE SUM(v) > 1)"));
        assertEquals("SQL compilation error:\n[9] is not a valid order by expression",
            refusal("SELECT id FROM fz ORDER BY (SELECT 1 FROM g ORDER BY 9)"));
        // Held inside a larger key, or behind another key.
        assertEquals(at(39, UNGROUPED), refusal("SELECT id FROM fz ORDER BY 1 + (SELECT b FROM FULL_T GROUP BY a)"));
        assertEquals(at(41, NO_WINDOW), refusal("SELECT id FROM fz ORDER BY id, (SELECT 1 QUALIFY TRUE)"));
        assertEquals(at(44, NO_WINDOW), refusal("SELECT id FROM fz ORDER BY EXISTS (SELECT 1 QUALIFY TRUE)"));
        assertEquals(at(41, UNGROUPED), refusal("SELECT id FROM fz ORDER BY id IN (SELECT b FROM FULL_T GROUP BY a)"));
    }

    @Test
    public void aSecondRowIsTheSubquerysRowTimeFault() {
        assertEquals("Single-row subquery returns more than one row.",
            refusal("SELECT id FROM fz ORDER BY (SELECT v FROM g)"));
        assertEquals("Single-row subquery returns more than one row.",
            refusal("SELECT id, (SELECT v FROM g) AS s FROM fz ORDER BY s"));
    }

    @Test
    public void aFromlessOrderByIsJudged() {
        assertEquals(at(31, UNGROUPED), refusal("SELECT 1 AS x ORDER BY (SELECT b FROM FULL_T GROUP BY a)"));
        assertEquals(at(33, NO_WINDOW), refusal("SELECT 1 AS x ORDER BY (SELECT 1 QUALIFY TRUE)"));
        assertEquals(at(31, "invalid identifier 'NOSUCH'"), refusal("SELECT 1 AS x ORDER BY (SELECT nosuch FROM g)"));
        assertEquals(at(35, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT 1 AS x ORDER BY (SELECT 'x' + TRUE)"));
        assertEquals(at(25, "Invalid argument types for function '+': (NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))"),
            refusal("SELECT 1 AS x ORDER BY 1 + (SELECT 1, 2)"));
        // The published names are no relation of a subquery in a key.
        assertEquals(at(53, "invalid identifier 'X'"), refusal("SELECT 1 AS x ORDER BY (SELECT v FROM g WHERE g.id = x)"));
        // A filter or a limit that leaves no row spares nothing.
        assertEquals(at(31, UNGROUPED), refusal("SELECT 1 AS x ORDER BY (SELECT b FROM FULL_T GROUP BY a) LIMIT 0"));
        assertEquals(at(43, UNGROUPED), refusal("SELECT 1 AS x WHERE 1 = 0 ORDER BY (SELECT b FROM FULL_T GROUP BY a)"));
        // The key's own names, ordinals, function names and types.
        assertEquals(at(23, "invalid identifier 'NOSUCH2'"), refusal("SELECT 1 AS x ORDER BY nosuch2"));
        assertEquals(at(47, "invalid identifier 'NOSUCH2'"), refusal("SELECT 1 AS x ORDER BY (SELECT nosuch FROM g), nosuch2"));
        assertEquals("SQL compilation error:\n[9] is not a valid order by expression", refusal("SELECT 1 AS x ORDER BY 9"));
        assertEquals("SQL compilation error:\n[9] is not a valid order by expression",
            refusal("SELECT 1 AS x ORDER BY (SELECT b FROM FULL_T GROUP BY a), 9"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.", refusal("SELECT 1 AS x ORDER BY NOSUCHFN(x)"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCHFN.", refusal("SELECT 1 AS x ORDER BY (SELECT NOSUCHFN(1))"));
        assertEquals(at(23, "too many arguments for function [ABS(1, 2)] expected 1, got 2"),
            refusal("SELECT 1 AS x ORDER BY ABS(1, 2)"));
        assertEquals(at(27, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT 1 AS x ORDER BY 'a' + TRUE"));
    }

    @Test
    public void whatNeedsARowIsAnswered() {
        assertEquals("1", rows("SELECT 1 AS x ORDER BY (SELECT v FROM g)"));
        assertEquals("1", rows("SELECT 1 AS x ORDER BY (SELECT 1 / 0)"));
        assertEquals("1", rows("SELECT 1 AS x ORDER BY UPPER(x)"));
        assertEquals("1", rows("SELECT 1 AS x ORDER BY x"));
    }

    @Test
    public void aSetOperationsKeyIsPlacedInTheStatement() {
        assertEquals(at(52, NO_WINDOW), refusal("SELECT 1 AS x UNION ALL SELECT 2 ORDER BY (SELECT 1 QUALIFY TRUE)"));
        assertEquals(at(50, "invalid identifier 'NOSUCH'"),
            refusal("SELECT 1 AS x UNION ALL SELECT 2 ORDER BY (SELECT nosuch FROM g)"));
        assertEquals(at(54, "Invalid argument types for function '+': (VARCHAR(1), BOOLEAN)"),
            refusal("SELECT 1 AS x UNION ALL SELECT 2 ORDER BY (SELECT 'x' + TRUE)"));
        assertEquals(at(72, "invalid identifier 'X'"),
            refusal("SELECT 1 AS x UNION ALL SELECT 2 ORDER BY (SELECT v FROM g WHERE g.id = x)"));
        assertEquals(at(43, "Unsupported: Scalar subquery with multi-column SELECT clause."),
            refusal("SELECT 1 AS x UNION ALL SELECT 2 ORDER BY (SELECT 1, 2)"));
        assertEquals("1 | 2", rows("SELECT 1 AS x UNION ALL SELECT 2 ORDER BY x + (SELECT MAX(v) FROM g)"));
        assertEquals("Single-row subquery returns more than one row.",
            refusal("SELECT 1 AS x UNION ALL SELECT 2 ORDER BY (SELECT v FROM g)"));
    }
}
