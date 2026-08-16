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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for HAVING clause with GROUP BY
 */
public class HavingTest extends BaseDatabaseTest {

    @Test
    public void testHavingWithCount() {
        engine.execute("CREATE TABLE orders (customer VARCHAR, amount INT)");
        engine.execute("INSERT INTO orders VALUES ('Alice', 100)");
        engine.execute("INSERT INTO orders VALUES ('Alice', 200)");
        engine.execute("INSERT INTO orders VALUES ('Bob', 150)");
        engine.execute("INSERT INTO orders VALUES ('Charlie', 300)");
        engine.execute("INSERT INTO orders VALUES ('Charlie', 250)");
        engine.execute("INSERT INTO orders VALUES ('Charlie', 180)");

        // Select customers with more than 2 orders
        final ResultSet rs = engine.executeQuery("""
            SELECT customer, COUNT(*) as order_count FROM orders GROUP BY customer HAVING COUNT(*) > 2
            """);

        assertNotNull(rs);
        assertEquals(1, rs.getRows().size());
        assertEquals("Charlie", rs.getRows().get(0).getValue(0));
        assertEquals(3L, rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testHavingWithSum() {
        engine.execute("CREATE TABLE sales (region VARCHAR, amount INT)");
        engine.execute("INSERT INTO sales VALUES ('East', 1000)");
        engine.execute("INSERT INTO sales VALUES ('East', 1500)");
        engine.execute("INSERT INTO sales VALUES ('West', 800)");
        engine.execute("INSERT INTO sales VALUES ('West', 600)");
        engine.execute("INSERT INTO sales VALUES ('North', 2000)");

        // Select regions with total sales > 2000
        // East = 1000 + 1500 = 2500, West = 800 + 600 = 1400, North = 2000
        // HAVING > 2000 means only East (2500)
        final ResultSet rs = engine.executeQuery("""
            SELECT region, SUM(amount) as total FROM sales GROUP BY region HAVING SUM(amount) > 2000
            """);

        assertNotNull(rs);
        assertEquals(1, rs.getRows().size());
        assertEquals("East", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testHavingWithAvg() {
        engine.execute("CREATE TABLE scores (student VARCHAR, score INT)");
        engine.execute("INSERT INTO scores VALUES ('Alice', 90)");
        engine.execute("INSERT INTO scores VALUES ('Alice', 85)");
        engine.execute("INSERT INTO scores VALUES ('Alice', 95)");
        engine.execute("INSERT INTO scores VALUES ('Bob', 70)");
        engine.execute("INSERT INTO scores VALUES ('Bob', 75)");
        engine.execute("INSERT INTO scores VALUES ('Charlie', 95)");
        engine.execute("INSERT INTO scores VALUES ('Charlie', 98)");

        // Select students with average score >= 90
        // ORDER BY pins the read order — grouped output alone guarantees none.
        final ResultSet rs = engine.executeQuery("""
            SELECT student, AVG(score) as avg_score FROM scores GROUP BY student HAVING AVG(score) >= 90
            ORDER BY student
            """);

        assertNotNull(rs);
        assertEquals(2, rs.getRows().size());

        // Alice: (90 + 85 + 95) / 3 = 90
        // Bob: (70 + 75) / 2 = 72.5
        // Charlie: (95 + 98) / 2 = 96.5
        // Should get Alice and Charlie
        assertEquals("Alice", rs.getRows().get(0).getValue(0));
        assertEquals("Charlie", rs.getRows().get(1).getValue(0));
    }

    @Test
    public void testHavingWithAlias() {
        engine.execute("CREATE TABLE transactions (acct_id VARCHAR, amount INT)");
        engine.execute("INSERT INTO transactions VALUES ('A', 100)");
        engine.execute("INSERT INTO transactions VALUES ('A', 200)");
        engine.execute("INSERT INTO transactions VALUES ('A', 150)");
        engine.execute("INSERT INTO transactions VALUES ('B', 50)");
        engine.execute("INSERT INTO transactions VALUES ('B', 75)");

        // Use alias in HAVING clause
        final ResultSet rs = engine.executeQuery("""
            SELECT acct_id, SUM(amount) as total_amount FROM transactions GROUP BY acct_id HAVING total_amount > 200
            """);

        assertNotNull(rs);
        assertEquals(1, rs.getRows().size());
        assertEquals("A", rs.getRows().get(0).getValue(0));
        // SUM can return Long or Double
        final Number total = (Number) rs.getRows().get(0).getValue(1);
        assertEquals(450.0, total.doubleValue(), 0.01);
    }

    @Test
    public void testHavingWithMultipleConditions() {
        engine.execute("CREATE TABLE sales_data (category VARCHAR, quantity INT, price INT)");
        engine.execute("INSERT INTO sales_data VALUES ('Electronics', 5, 100)");
        engine.execute("INSERT INTO sales_data VALUES ('Electronics', 3, 150)");
        engine.execute("INSERT INTO sales_data VALUES ('Books', 10, 20)");
        engine.execute("INSERT INTO sales_data VALUES ('Books', 15, 25)");
        engine.execute("INSERT INTO sales_data VALUES ('Clothing', 8, 50)");

        // Categories with total quantity > 5
        // Electronics: 5 + 3 = 8
        // Books: 10 + 15 = 25
        // Clothing: 8
        // All three match > 5
        final ResultSet rs = engine.executeQuery("""
            SELECT category, SUM(quantity) as total_qty, COUNT(*) as num_sales FROM sales_data GROUP BY category HAVING SUM(quantity) > 5
            """);

        assertNotNull(rs);
        assertEquals(3, rs.getRows().size());
    }

    @Test
    public void testHavingWithNoGroupBy() {
        engine.execute("CREATE TABLE numbers (value INT)");
        engine.execute("INSERT INTO numbers VALUES (10)");
        engine.execute("INSERT INTO numbers VALUES (20)");
        engine.execute("INSERT INTO numbers VALUES (30)");

        // Aggregate without GROUP BY, with HAVING
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) as total FROM numbers HAVING COUNT(*) > 2");

        assertNotNull(rs);
        assertEquals(1, rs.getRows().size());
        assertEquals(3L, rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testHavingFilterAll() {
        engine.execute("CREATE TABLE items (category VARCHAR, count INT)");
        engine.execute("INSERT INTO items VALUES ('A', 1)");
        engine.execute("INSERT INTO items VALUES ('B', 2)");
        engine.execute("INSERT INTO items VALUES ('C', 3)");

        // HAVING that filters everything
        final ResultSet rs = engine.executeQuery("""
            SELECT category, SUM(count) as total FROM items GROUP BY category HAVING SUM(count) > 100
            """);

        assertNotNull(rs);
        assertEquals(0, rs.getRows().size());
    }
}
