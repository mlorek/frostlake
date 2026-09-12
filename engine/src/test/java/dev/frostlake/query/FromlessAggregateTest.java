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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * A query with NO FROM CLAUSE is one row of one group. Every aggregate answers over that row — SUM(1) is
 * 1, COUNT(*) is 1 — and Frostlake used to refuse the lot with "Unknown function SUM.", because a
 * FROM-less select list was evaluated item by item and an aggregate name means nothing to the scalar
 * dispatch. The name was never the problem: the SAME call answers as soon as a FROM is written.
 *
 * <p>★ THE ROW IS REAL, not a special case bolted onto the aggregate. Writing WHERE 1 = 0 filters it
 * away and the query STILL answers one row, holding NULL — the group survives its own empty input,
 * exactly as a grouped query over an empty table does. HAVING, by contrast, filters the GROUP, so a
 * false HAVING answers no rows at all. That pair is what says the whole ordinary pipeline is running.
 *
 * <p>★ A WINDOW WITH NO FROM is the same shape and now runs too: {@code SELECT SUM(1) OVER ()} is 1.
 */
public class FromlessAggregateTest extends BaseDatabaseTest {

    private String cellOf(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void everyAggregateAnswersOverTheSingleRow() {
        assertEquals("1", cellOf("SELECT SUM(1) AS a"));
        assertEquals("1", cellOf("SELECT COUNT(*) AS a"));
        assertEquals("1", cellOf("SELECT COUNT(1) AS a"));
        assertEquals("0", cellOf("SELECT COUNT(NULL) AS a"));
        assertEquals("1", cellOf("SELECT MIN(1) AS a"));
        assertEquals("b", cellOf("SELECT MAX('b') AS a"));
        assertEquals("1", cellOf("SELECT COUNT(DISTINCT 1) AS a"));
    }

    @Test
    public void includingTheOnesThatBuildOrInterpolate() {
        assertEquals("[1]", cellOf("SELECT ARRAY_AGG(1) AS a"));
        assertEquals("x", cellOf("SELECT LISTAGG('x', ',') AS a"));
        assertEquals("1.000", cellOf("SELECT MEDIAN(1) AS a"));
        assertEquals("1.000", cellOf("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY 1) AS a"));
        assertEquals("3.000000", cellOf("SELECT AVG(3) AS a"));
    }

    @Test
    public void anEmptyAggregateIsNullNotZero() {
        assertNull(engine.executeQuery("SELECT SUM(NULL) AS a").getRows().get(0).getValue(0));
        // One row is not enough for a SAMPLE standard deviation, so live answers NULL here too.
        assertNull(engine.executeQuery("SELECT STDDEV(1) AS a").getRows().get(0).getValue(0));
    }

    @Test
    public void anAggregateSitsBesideScalarsAndInsideExpressions() {
        final ResultSet pair = engine.executeQuery("SELECT SUM(1) AS a, COUNT(*) AS b");
        assertEquals("1", String.valueOf(pair.getRows().get(0).getValue(0)));
        assertEquals("1", String.valueOf(pair.getRows().get(0).getValue(1)));
        assertEquals("2", cellOf("SELECT SUM(1) + 1 AS a"));
        final ResultSet mixed = engine.executeQuery("SELECT SUM(1) AS a, 2 AS b");
        assertEquals("1", String.valueOf(mixed.getRows().get(0).getValue(0)));
        assertEquals("2", String.valueOf(mixed.getRows().get(0).getValue(1)));
    }

    @Test
    public void whereFiltersTheRowAndHavingFiltersTheGroup() {
        assertEquals("1", cellOf("SELECT SUM(1) AS a WHERE 1 = 1"));
        // The row is gone, the GROUP is not: one row, holding NULL.
        final ResultSet emptied = engine.executeQuery("SELECT SUM(1) AS a WHERE 1 = 0");
        assertEquals(1, emptied.getRows().size());
        assertNull(emptied.getRows().get(0).getValue(0));
        assertEquals("1", cellOf("SELECT SUM(1) AS a HAVING SUM(1) > 0"));
        assertEquals(0, engine.executeQuery("SELECT SUM(1) AS a HAVING SUM(1) > 99").getRows().size());
    }

    @Test
    public void theRestOfTheQueryStillApplies() {
        assertEquals("1", cellOf("SELECT SUM(1) AS a ORDER BY 1"));
        assertEquals("1", cellOf("SELECT SUM(1) AS a LIMIT 1"));
        assertEquals("1", cellOf("SELECT (SELECT SUM(1)) AS a"));
        assertEquals("1", cellOf("SELECT SUM(1) OVER () AS a"));
    }

    @Test
    public void aWrittenSourceIsUnchanged() {
        assertEquals("1", cellOf("SELECT SUM(1) AS a FROM (SELECT 1) x"));
        assertEquals("2", cellOf("SELECT SUM(1) AS a FROM (VALUES (1), (2)) v"));
        assertEquals("3", cellOf("SELECT SUM(1) AS a FROM TABLE(GENERATOR(ROWCOUNT => 3))"));
    }

    @Test
    public void aColumnStillHasNothingToResolveAgainst() {
        assertEquals("SQL compilation error: error line 1 at position 11\ninvalid identifier 'X'",
            assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT SUM(x) AS a");
                }
            }).getMessage());
    }
}
