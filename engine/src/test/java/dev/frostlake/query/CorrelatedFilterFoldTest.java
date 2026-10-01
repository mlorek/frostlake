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
 * A correlated filter whose operand mixes the inner and the outer row is answered where the relations'
 * statistics settle it — true on every row, or false on every row — and refused where they leave it open, so
 * the same shape turns on the data: over these tables {@code g.id + fz.id > 0} answers and {@code > 10} is
 * refused. Frostlake refused every mixed operand, whatever the statistics said (live-verified).
 */
public class CorrelatedFilterFoldTest extends BaseDatabaseTest {

    private static final String REFUSAL =
        "SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position 12";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE TABLE h (k INT, w INT)");
        engine.execute("INSERT INTO h VALUES (5, 500), (7, 700)");
    }

    /** The subquery's value for each row of FZ, as {@code SELECT id, <subquery> FROM fz ORDER BY id} reads it. */
    private String perRow(final String subquery) {
        final ResultSet result = engine.executeQuery("SELECT id, " + subquery + " FROM fz ORDER BY id");
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "").append(result.getRows().get(r).getValue(0))
                .append(", ").append(result.getRows().get(r).getValue(1));
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

    /** The refusal of the same statement {@link #perRow} runs. */
    private String refusedPerRow(final String subquery) {
        return refusal("SELECT id, " + subquery + " FROM fz ORDER BY id");
    }

    @Test
    public void aSettledFilterIsAnswered() {
        // The sum lies in [10, 13], which no value of h.k, g.v or the bounds below can meet.
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id + fz.id)"));
        assertEquals("5, null | 7, null",
            perRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id WHERE h.k = g.id + fz.id)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g JOIN h ON h.k + fz.id = g.id)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g, h WHERE h.k = g.id + fz.id)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.v = g.id + fz.id)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g JOIN h ON NOT (h.w > fz.id))"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g JOIN h ON h.k * 1 = g.id + fz.id)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id >= g.v)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id = g.v + fz.id)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id IN (g.v))"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id IS NULL)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id = 10 + fz.id)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id * fz.id = g.v)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE ABS(g.id - fz.id) = g.v)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id IN (g.v, 5))"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id BETWEEN g.v AND 20)"));
        // Settled the other way, so every row of the inner relation survives.
        assertEquals("5, 220 | 7, 220", perRow("(SELECT SUM(v) FROM g JOIN h ON g.id + fz.id > 0)"));
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id > 0)"));
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id BETWEEN 1 AND 20)"));
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE NOT (g.id + fz.id IN (5)))"));
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id <> g.v)"));
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id IS NOT NULL)"));
        // A settled conjunct or disjunct settles the whole filter, and an EXISTS reads the same rule.
        assertEquals("5, null | 7, null",
            perRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id + fz.id AND h.w > 0)"));
        assertEquals("5, null | 7, null",
            perRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id + fz.id OR h.w = 0)"));
        assertEquals("5 | 7", rowsOf("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id + fz.id > 0) ORDER BY id"));
    }

    /** Each row's first cell, rows joined by bars. */
    private String rowsOf(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "").append(result.getRows().get(r).getValue(0));
        }
        return text.toString();
    }

    @Test
    public void anOpenFilterIsRefused() {
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id = 10)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g JOIN h ON g.id + fz.id = 10)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id <> 10)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id IN (5, 10))"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id NOT IN (5, 10))"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE ABS(g.id - fz.id) = 0)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE GREATEST(g.id, fz.id) = 5)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id - fz.id = 0)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id LIKE '1%')"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE NOT (g.id = fz.id))"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE NOT (g.id + fz.id = 10))"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id = 10 AND g.id = 5)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id = 10 OR g.id = 5)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id = fz.id AND g.v + fz.id = 55)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g HAVING SUM(g.id + fz.id) = 10)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id = 10 GROUP BY g.id)"));
        // A bare outer boolean and a derived table's outer filter are open whatever the statistics say.
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g JOIN h ON fz.b)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g JOIN h ON h.k = g.id AND fz.b)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM (SELECT v FROM g WHERE fz.b))"));
        assertEquals("SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position 24",
            refusal("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE g.id + fz.id = 10) ORDER BY id"));
        assertEquals("SQL compilation error:\nUnsupported subquery type cannot be evaluated at line 1, position 24",
            refusal("SELECT id FROM fz WHERE EXISTS (SELECT 1 FROM g WHERE NOT (g.id = fz.id)) ORDER BY id"));
    }

    @Test
    public void theSameShapeTurnsOnTheStatistics() {
        // g.id + fz.id lies in [10, 13]: a bound outside it settles the filter, one inside leaves it open.
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id > 0)"));
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id >= 10)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id < 10)"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id = 0)"));
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id <> 0)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id > 10)"));
        assertEquals(REFUSAL, refusedPerRow("(SELECT SUM(v) FROM g WHERE g.id + fz.id <= 10)"));
    }

    @Test
    public void aPlainCorrelationStillAnswers() {
        assertEquals("5, 50 | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id = fz.id)"));
        assertEquals("5, 50 | 7, null", perRow("(SELECT SUM(v) FROM g WHERE g.id = fz.id + 0)"));
        assertEquals("5, 110 | 7, null", perRow("(SELECT SUM(v) FROM g WHERE fz.id = 5)"));
        assertEquals("5, 110 | 7, 110", perRow("(SELECT SUM(v) FROM g WHERE fz.id IN (5, 7))"));
        assertEquals("5, null | 7, null", perRow("(SELECT SUM(v) FROM g WHERE fz.id + 1 = 10)"));
    }
}
