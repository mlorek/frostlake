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
 * A GROUPED or DISTINCT query's ORDER BY holds no subquery at all — correlated or not — and the refusal
 * re-prints the subquery from its plan, with an outer reference as {@code CORRELATION(…)}. The same key over
 * an ungrouped query, an ordinal, and a select item's alias all stay legal. Frostlake answered every one of
 * them (live-verified).
 */
public class GroupedOrderBySubqueryTest extends BaseDatabaseTest {

    private static final String TAIL = "] is not a valid order by expression";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    /** Each row's cells joined by commas, rows by bars. */
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

    @Test
    public void aGroupedOrderByHoldsNoSubquery() {
        final String maxV = "SQL compilation error:\n[(SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G)" + TAIL;
        assertEquals("SQL compilation error:\n[(SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G WHERE G.ID = "
            + "CORRELATION(FZ.ID))" + TAIL,
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g WHERE g.id = fz.id) NULLS LAST, id"));
        assertEquals("SQL compilation error:\n[(SELECT COUNT(*) AS \"COUNT(*)\" FROM G AS G WHERE G.ID = "
            + "CORRELATION(FZ.ID))" + TAIL,
            refusal("SELECT b FROM fz GROUP BY b ORDER BY (SELECT COUNT(*) FROM g WHERE g.id = fz.id)"));
        // An uncorrelated one is refused just the same, whatever holds it.
        assertEquals(maxV, refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g), id"));
        assertEquals(maxV, refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g) + 1, id"));
        assertEquals(maxV, refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g) DESC"));
        assertEquals(maxV, refusal("SELECT id FROM fz GROUP BY id "
            + "ORDER BY CASE WHEN (SELECT MAX(v) FROM g) > 0 THEN 1 ELSE 2 END, id"));
        assertEquals(maxV, refusal("SELECT id FROM fz GROUP BY id HAVING COUNT(*) > 0 ORDER BY (SELECT MAX(v) FROM g), id"));
        assertEquals(maxV, refusal("SELECT id FROM fz GROUP BY ALL ORDER BY (SELECT MAX(v) FROM g), id"));
        assertEquals(maxV, refusal("SELECT DISTINCT id FROM fz ORDER BY (SELECT MAX(v) FROM g), id"));
        assertEquals("SQL compilation error:\n[(SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL)" + TAIL,
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT 1), id"));
    }

    @Test
    public void theEchoIsThePlansOwnPrint() {
        assertEquals("SQL compilation error:\n[(SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G WHERE G.ID = 5)" + TAIL,
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g WHERE g.id = 5), id"));
        assertEquals("SQL compilation error:\n[(SELECT COUNT(*) AS \"COUNT(*)\" FROM G AS G WHERE G.ID = "
            + "CORRELATION(MAX(FZ.ID)))" + TAIL,
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT COUNT(*) FROM g WHERE g.id = MAX(fz.id)), id"));
        assertEquals("SQL compilation error:\n[(SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G WHERE EXISTS(SELECT 1 "
            + "AS \"1\" FROM G AS G2 WHERE G2.ID = CORRELATION(G.ID)))" + TAIL,
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(g.v) FROM g "
                + "WHERE EXISTS (SELECT 1 FROM g g2 WHERE g2.id = g.id)), id"));
        assertEquals("SQL compilation error:\n[(SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G LIMIT 1 OFFSET 0)" + TAIL,
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g LIMIT 1)"));
        assertEquals("SQL compilation error:\n[(SELECT G.V AS \"V\" FROM G AS G  ORDER BY V ASC NULLS LAST "
            + "LIMIT 1 OFFSET 0)" + TAIL,
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT v FROM g ORDER BY v LIMIT 1)"));
        assertEquals("SQL compilation error:\n[(SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G WHERE G.ID = "
            + "ANY(SELECT FZ.ID AS \"ID\" FROM FZ AS FZ))" + TAIL,
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g WHERE g.id IN (SELECT id FROM fz))"));
    }

    @Test
    public void whatIsStillLegalStaysLegal() {
        assertEquals("5, 60 | 7, 60", rows("SELECT id, (SELECT MAX(v) FROM g) AS m FROM fz GROUP BY id ORDER BY m, id"));
        assertEquals("5 | 7", rows("SELECT id FROM fz ORDER BY (SELECT MAX(v) FROM g), id"));
        assertEquals("7", rows("SELECT MAX(id) FROM fz ORDER BY (SELECT MAX(v) FROM g)"));
        assertEquals("5, 1 | 7, 1", rows("SELECT id, COUNT(*) FROM fz GROUP BY id ORDER BY 2, id"));
        // A name the query cannot resolve, and an ordinal past the list, are still refused first.
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'NOSUCH'",
            refusal("SELECT nosuch FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g)"));
        assertEquals("SQL compilation error: error line 1 at position 47\ninvalid identifier 'NOSUCH'",
            refusal("SELECT id FROM fz GROUP BY id ORDER BY (SELECT nosuch FROM g)"));
        assertEquals("SQL compilation error:\n[9] is not a valid order by expression",
            refusal("SELECT id FROM fz GROUP BY id ORDER BY 9, (SELECT MAX(v) FROM g)"));
        assertEquals("SQL compilation error: error line 1 at position 7\n'FZ.B' in select clause is neither an "
            + "aggregate nor in the group by clause.",
            refusal("SELECT b FROM fz GROUP BY id ORDER BY (SELECT MAX(v) FROM g)"));
    }
}
