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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for LAG and LEAD window functions
 */
public class LagLeadTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sales (id INTEGER, product VARCHAR, sale_date VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES (1, 'Widget', '2024-01-01', 100)");
        engine.execute("INSERT INTO sales VALUES (2, 'Widget', '2024-01-02', 150)");
        engine.execute("INSERT INTO sales VALUES (3, 'Widget', '2024-01-03', 200)");
        engine.execute("INSERT INTO sales VALUES (4, 'Widget', '2024-01-04', 180)");
        engine.execute("INSERT INTO sales VALUES (5, 'Gadget', '2024-01-01', 300)");
        engine.execute("INSERT INTO sales VALUES (6, 'Gadget', '2024-01-02', 350)");
    }

    @Test
    public void testLagBasic() {
        // LAG with default offset of 1
        final ResultSet result = engine.executeQuery("""
            SELECT product, sale_date, amount,
            LAG(amount) OVER (ORDER BY id) as prev_amount
            FROM sales
            """);

        assertEquals(6, result.getRowCount());

        // First row should have NULL for LAG (no previous row)
        final Row row1 = result.getRows().get(0);
        assertEquals("Widget", row1.getValue(0));
        assertEquals(100L, row1.getValue(2));
        assertNull(row1.getValue(3), "First row LAG should be NULL");

        // Second row LAG should be 100 (previous amount)
        final Row row2 = result.getRows().get(1);
        assertEquals("Widget", row2.getValue(0));
        assertEquals(150L, row2.getValue(2));
        assertEquals(100L, row2.getValue(3), "Second row LAG should be 100");

        // Third row LAG should be 150
        final Row row3 = result.getRows().get(2);
        assertEquals(200L, row3.getValue(2));
        assertEquals(150L, row3.getValue(3), "Third row LAG should be 150");
    }

    @Test
    public void testLeadBasic() {
        // LEAD with default offset of 1
        final ResultSet result = engine.executeQuery("""
            SELECT product, sale_date, amount,
            LEAD(amount) OVER (ORDER BY id) as next_amount
            FROM sales
            """);

        assertEquals(6, result.getRowCount());

        // First row LEAD should be 150 (next amount)
        final Row row1 = result.getRows().get(0);
        assertEquals("Widget", row1.getValue(0));
        assertEquals(100L, row1.getValue(2));
        assertEquals(150L, row1.getValue(3), "First row LEAD should be 150");

        // Second row LEAD should be 200
        final Row row2 = result.getRows().get(1);
        assertEquals(150L, row2.getValue(2));
        assertEquals(200L, row2.getValue(3), "Second row LEAD should be 200");

        // Last row should have NULL for LEAD (no next row)
        final Row row6 = result.getRows().get(5);
        assertEquals("Gadget", row6.getValue(0));
        assertEquals(350L, row6.getValue(2));
        assertNull(row6.getValue(3), "Last row LEAD should be NULL");
    }

    @Test
    public void testLagWithOffset() {
        // LAG with offset of 2
        final ResultSet result = engine.executeQuery("""
            SELECT id, amount,
            LAG(amount, 2) OVER (ORDER BY id) as lag2_amount
            FROM sales
            """);

        assertEquals(6, result.getRowCount());

        // First two rows should have NULL (not enough previous rows)
        final Row row1 = result.getRows().get(0);
        assertNull(row1.getValue(2), "First row LAG(2) should be NULL");

        final Row row2 = result.getRows().get(1);
        assertNull(row2.getValue(2), "Second row LAG(2) should be NULL");

        // Third row LAG(2) should be 100 (two rows back)
        final Row row3 = result.getRows().get(2);
        assertEquals(200L, row3.getValue(1));
        assertEquals(100L, row3.getValue(2), "Third row LAG(2) should be 100");

        // Fourth row LAG(2) should be 150
        final Row row4 = result.getRows().get(3);
        assertEquals(180L, row4.getValue(1));
        assertEquals(150L, row4.getValue(2), "Fourth row LAG(2) should be 150");
    }

    @Test
    public void testLeadWithOffset() {
        // LEAD with offset of 2
        final ResultSet result = engine.executeQuery("""
            SELECT id, amount,
            LEAD(amount, 2) OVER (ORDER BY id) as lead2_amount
            FROM sales
            """);

        assertEquals(6, result.getRowCount());

        // First row LEAD(2) should be 200 (two rows ahead)
        final Row row1 = result.getRows().get(0);
        assertEquals(100L, row1.getValue(1));
        assertEquals(200L, row1.getValue(2), "First row LEAD(2) should be 200");

        // Second row LEAD(2) should be 180
        final Row row2 = result.getRows().get(1);
        assertEquals(150L, row2.getValue(1));
        assertEquals(180L, row2.getValue(2), "Second row LEAD(2) should be 180");

        // Last two rows should have NULL (not enough following rows)
        final Row row5 = result.getRows().get(4);
        assertNull(row5.getValue(2), "Fifth row LEAD(2) should be NULL");

        final Row row6 = result.getRows().get(5);
        assertNull(row6.getValue(2), "Sixth row LEAD(2) should be NULL");
    }

    @Test
    public void testLagWithDefault() {
        // LAG with default value of 0
        final ResultSet result = engine.executeQuery("""
            SELECT id, amount,
            LAG(amount, 1, 0) OVER (ORDER BY id) as prev_amount
            FROM sales
            """);

        assertEquals(6, result.getRowCount());

        // First row should have default value of 0 (not NULL)
        final Row row1 = result.getRows().get(0);
        assertEquals(100L, row1.getValue(1));
        assertEquals(0L, row1.getValue(2), "First row LAG with default should be 0");

        // Second row should have 100
        final Row row2 = result.getRows().get(1);
        assertEquals(150L, row2.getValue(1));
        assertEquals(100L, row2.getValue(2), "Second row LAG should be 100");
    }

    @Test
    public void testLeadWithDefault() {
        // LEAD with default value of -1
        final ResultSet result = engine.executeQuery("""
            SELECT id, amount,
            LEAD(amount, 1, -1) OVER (ORDER BY id) as next_amount
            FROM sales
            """);

        assertEquals(6, result.getRowCount());

        // Last row should have default value of -1 (not NULL)
        final Row row6 = result.getRows().get(5);
        assertEquals(350L, row6.getValue(1));
        assertEquals(-1L, row6.getValue(2), "Last row LEAD with default should be -1");

        // Second-to-last row should have 350
        final Row row5 = result.getRows().get(4);
        assertEquals(300L, row5.getValue(1));
        assertEquals(350L, row5.getValue(2), "Fifth row LEAD should be 350");
    }

    @Test
    public void testLagLeadTogether() {
        // Use both LAG and LEAD in the same query
        final ResultSet result = engine.executeQuery("""
            SELECT id, amount,
            LAG(amount) OVER (ORDER BY id) as prev_amount,
            LEAD(amount) OVER (ORDER BY id) as next_amount
            FROM sales
            """);

        assertEquals(6, result.getRowCount());

        // Check middle row (id=3)
        final Row row3 = result.getRows().get(2);
        assertEquals(3L, row3.getValue(0));
        assertEquals(200L, row3.getValue(1));
        assertEquals(150L, row3.getValue(2), "Row 3 LAG should be 150");
        assertEquals(180L, row3.getValue(3), "Row 3 LEAD should be 180");
    }

    @Test
    public void testLagWithColumnName() {
        // LAG referencing column by name
        final ResultSet result = engine.executeQuery("""
            SELECT sale_date, amount,
            LAG(sale_date) OVER (ORDER BY id) as prev_date
            FROM sales
            """);

        assertEquals(6, result.getRowCount());

        // First row LAG should be NULL
        final Row row1 = result.getRows().get(0);
        assertEquals("2024-01-01", row1.getValue(0));
        assertNull(row1.getValue(2), "First row LAG(date) should be NULL");

        // Second row LAG should be previous date
        final Row row2 = result.getRows().get(1);
        assertEquals("2024-01-02", row2.getValue(0));
        assertEquals("2024-01-01", row2.getValue(2), "Second row LAG(date) should be 2024-01-01");
    }
}
