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
 * An aggregate written inside a subquery of a GROUPED query, over that query's own columns, is the grouped
 * query's aggregate: computed over the group and read by the subquery as an outer value, in its HAVING and
 * in its select list alike. A query that groups only implicitly offers none, and an aggregate whose argument
 * also reads the subquery's columns is not one of these. Frostlake refused every such subquery as an
 * unsupported correlation (live-verified).
 */
public class GroupedSubqueryAggregateTest extends BaseDatabaseTest {

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
    public void aHavingSubqueryReadsTheGroupsAggregate() {
        assertEquals("false", rows("SELECT b FROM fz GROUP BY b "
            + "HAVING (SELECT MAX(v) FROM g WHERE g.id < MAX(fz.id)) > 55 ORDER BY b"));
        assertEquals("true", rows("SELECT b FROM fz GROUP BY b "
            + "HAVING (SELECT COUNT(*) FROM g WHERE g.id = MAX(fz.id)) > 0 ORDER BY b"));
        assertEquals("true", rows("SELECT b FROM fz GROUP BY b "
            + "HAVING (SELECT COUNT(*) FROM g WHERE g.id = MIN(fz.id)) > 0 ORDER BY b"));
        assertEquals("false", rows("SELECT b FROM fz GROUP BY b "
            + "HAVING (SELECT MAX(v) FROM g WHERE g.id < MAX(fz.id) AND g.v > 0) > 55 ORDER BY b"));
        // Beside an aggregate of the HAVING's own.
        assertEquals("true", rows("SELECT b FROM fz GROUP BY b "
            + "HAVING (SELECT COUNT(*) FROM g WHERE g.id = MAX(fz.id)) > 0 AND MAX(fz.id) > 0 ORDER BY b"));
    }

    @Test
    public void aGroupedSelectItemsSubqueryReadsItToo() {
        assertEquals("false, 2 | true, 1", rows("SELECT b, (SELECT COUNT(*) FROM g WHERE g.id <= MAX(fz.id)) "
            + "FROM fz GROUP BY b ORDER BY b"));
        assertEquals("false, null | true, 60", rows("SELECT b, (SELECT SUM(v) FROM g WHERE g.id > MIN(fz.id)) "
            + "FROM fz GROUP BY b ORDER BY b"));
        assertEquals("false, 2 | true, 2", rows("SELECT b, (SELECT COUNT(*) FROM g WHERE g.id <= MAX(fz.id) + 1) "
            + "FROM fz GROUP BY b ORDER BY b"));
        assertEquals("false, 2 | true, 1", rows("SELECT b, (SELECT COUNT(*) FROM g WHERE g.id <= SUM(fz.id)) "
            + "FROM fz GROUP BY b ORDER BY b"));
    }

    @Test
    public void onlyAGroupedQueryOffersItsAggregate() {
        // No GROUP BY: the subquery's aggregate over the outer row is a correlation live cannot plan.
        assertEquals("SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position 8",
            refusal("SELECT (SELECT COUNT(*) FROM g WHERE g.id <= MAX(fz.id)) FROM fz"));
        assertEquals("SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position 11",
            refusal("SELECT b, (SELECT COUNT(*) FROM g WHERE g.id <= MAX(fz.id)) FROM fz ORDER BY b"));
        // The subquery's own aggregate in its own WHERE is refused as any aggregate there is.
        assertEquals("SQL compilation error:\nInvalid aggregate function in where clause [COUNT(*)]",
            refusal("SELECT b, (SELECT COUNT(*) FROM g WHERE g.id <= COUNT(*)) FROM fz GROUP BY b ORDER BY b"));
        assertEquals("SQL compilation error:\nInvalid aggregate function in where clause [MAX(G.ID)]",
            refusal("SELECT b FROM fz GROUP BY b HAVING (SELECT COUNT(*) FROM g WHERE g.id = MAX(g.id)) > 0 ORDER BY b"));
    }
}
