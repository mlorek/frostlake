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
 * {@code GROUP BY ALL} — grouping by every non-aggregate select item without naming them.
 */
public class GroupByAllTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sales (region VARCHAR, city VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('EMEA', 'Paris', 10), ('EMEA', 'Paris', 20),"
            + " ('EMEA', 'Berlin', 5), ('APAC', 'Tokyo', 40)");
    }

    private static long asLong(final Object value) {
        return ((Number) value).longValue();
    }

    @Test
    public void groupsByEveryNonAggregateItem() {
        final ResultSet rs = engine.executeQuery(
            "SELECT region, city, SUM(amount) FROM sales GROUP BY ALL ORDER BY region, city");
        assertEquals(3, rs.getRowCount());
        assertEquals("Tokyo", rs.getRows().get(0).getValue(1));
        assertEquals(40L, asLong(rs.getRows().get(0).getValue(2)));
        assertEquals("Berlin", rs.getRows().get(1).getValue(1));
        assertEquals(5L, asLong(rs.getRows().get(1).getValue(2)));
        assertEquals("Paris", rs.getRows().get(2).getValue(1));
        assertEquals(30L, asLong(rs.getRows().get(2).getValue(2)));
    }

    @Test
    public void expressionItemsGroupToo() {
        final ResultSet rs = engine.executeQuery(
            "SELECT LOWER(region), COUNT(*) FROM sales GROUP BY ALL ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals("apac", rs.getRows().get(0).getValue(0));
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(1)));
        assertEquals("emea", rs.getRows().get(1).getValue(0));
        assertEquals(3L, asLong(rs.getRows().get(1).getValue(1)));
    }

    @Test
    public void allAggregatesMeansOneGrandTotalRow() {
        final ResultSet rs = engine.executeQuery(
            "SELECT SUM(amount), COUNT(*) FROM sales GROUP BY ALL");
        assertEquals(1, rs.getRowCount());
        assertEquals(75L, asLong(rs.getRows().get(0).getValue(0)));
        assertEquals(4L, asLong(rs.getRows().get(0).getValue(1)));
    }

    @Test
    public void composesWithHavingAndJoins() {
        engine.execute("CREATE TABLE regions (region VARCHAR, mgr VARCHAR)");
        engine.execute("INSERT INTO regions VALUES ('EMEA', 'M1'), ('APAC', 'M2')");
        final ResultSet rs = engine.executeQuery("""
            SELECT r.mgr, s.region, SUM(s.amount) AS total
            FROM sales s JOIN regions r ON r.region = s.region
            GROUP BY ALL
            HAVING SUM(s.amount) > 30
            ORDER BY r.mgr
            """);
        assertEquals(2, rs.getRowCount());
        assertEquals("M1", rs.getRows().get(0).getValue(0));
        assertEquals(35L, asLong(rs.getRows().get(0).getValue(2)));
        assertEquals("M2", rs.getRows().get(1).getValue(0));
        assertEquals(40L, asLong(rs.getRows().get(1).getValue(2)));
    }
}
