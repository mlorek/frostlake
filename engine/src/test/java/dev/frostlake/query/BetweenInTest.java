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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test BETWEEN and IN operators in WHERE clause
 */
public class BetweenInTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @BeforeEach
    public void setupData() {
        // Clean up and recreate tables for each test
        try {
            engine.execute("DROP TABLE IF EXISTS products");
            engine.execute("DROP TABLE IF EXISTS categories");
        } catch (final Exception e) {
            // Ignore errors
        }

        // Create test data
        engine.execute("CREATE TABLE products (id INT, name VARCHAR, price INT, category VARCHAR)");
        engine.execute("INSERT INTO products VALUES (1, 'Laptop', 1200, 'Electronics')");
        engine.execute("INSERT INTO products VALUES (2, 'Mouse', 25, 'Electronics')");
        engine.execute("INSERT INTO products VALUES (3, 'Desk', 300, 'Furniture')");
        engine.execute("INSERT INTO products VALUES (4, 'Chair', 150, 'Furniture')");
        engine.execute("INSERT INTO products VALUES (5, 'Monitor', 400, 'Electronics')");
        engine.execute("INSERT INTO products VALUES (6, 'Keyboard', 75, 'Electronics')");
        engine.execute("INSERT INTO products VALUES (7, 'Lamp', 50, 'Furniture')");

        engine.execute("CREATE TABLE categories (name VARCHAR)");
        engine.execute("INSERT INTO categories VALUES ('Electronics')");
        engine.execute("INSERT INTO categories VALUES ('Furniture')");
    }

    // BETWEEN tests
    @Test
    public void testBetweenBasic() {
        ResultSet result = engine.executeQuery(
            "SELECT name, price FROM products WHERE price BETWEEN 50 AND 300 ORDER BY price"
        );

        // Should return: Lamp (50), Keyboard (75), Chair (150), Desk (300)
        assertEquals(4, result.getRows().size());
        assertEquals("Lamp", result.getRows().get(0).getValue(0));
        assertEquals(50L, result.getRows().get(0).getValue(1));
        assertEquals("Keyboard", result.getRows().get(1).getValue(0));
        assertEquals("Chair", result.getRows().get(2).getValue(0));
        assertEquals("Desk", result.getRows().get(3).getValue(0));
        assertEquals(300L, result.getRows().get(3).getValue(1));
    }

    @Test
    public void testBetweenInclusive() {
        // BETWEEN is inclusive on both ends
        ResultSet result = engine.executeQuery(
            "SELECT name FROM products WHERE price BETWEEN 150 AND 150"
        );

        assertEquals(1, result.getRows().size());
        assertEquals("Chair", result.getRows().get(0).getValue(0));
    }

    @Test
    public void testNotBetween() {
        ResultSet result = engine.executeQuery(
            "SELECT name, price FROM products WHERE price NOT BETWEEN 100 AND 500 ORDER BY price"
        );

        // Should return items with price < 100 or > 500: Mouse (25), Lamp (50), Keyboard (75), Laptop (1200)
        assertEquals(4, result.getRows().size());
        assertEquals("Mouse", result.getRows().get(0).getValue(0));
        assertEquals(25L, result.getRows().get(0).getValue(1));
        assertEquals("Lamp", result.getRows().get(1).getValue(0));
        assertEquals(50L, result.getRows().get(1).getValue(1));
        assertEquals("Keyboard", result.getRows().get(2).getValue(0));
        assertEquals(75L, result.getRows().get(2).getValue(1));
        assertEquals("Laptop", result.getRows().get(3).getValue(0));
        assertEquals(1200L, result.getRows().get(3).getValue(1));
    }

    @Test
    public void testBetweenWithLiterals() {
        ResultSet result = engine.executeQuery(
            "SELECT id, name FROM products WHERE id BETWEEN 2 AND 4 ORDER BY id"
        );

        assertEquals(3, result.getRows().size());
        assertEquals("Mouse", result.getRows().get(0).getValue(1));
        assertEquals("Desk", result.getRows().get(1).getValue(1));
        assertEquals("Chair", result.getRows().get(2).getValue(1));
    }

    // IN tests
    @Test
    public void testInWithList() {
        ResultSet result = engine.executeQuery(
            "SELECT name, category FROM products WHERE category IN ('Electronics', 'Furniture') ORDER BY name"
        );

        // Should return all products
        assertEquals(7, result.getRows().size());
    }

    @Test
    public void testInWithNumbers() {
        ResultSet result = engine.executeQuery(
            "SELECT name, price FROM products WHERE price IN (25, 75, 150) ORDER BY price"
        );

        // Should return: Mouse (25), Keyboard (75), Chair (150)
        assertEquals(3, result.getRows().size());
        assertEquals("Mouse", result.getRows().get(0).getValue(0));
        assertEquals(25L, result.getRows().get(0).getValue(1));
        assertEquals("Keyboard", result.getRows().get(1).getValue(0));
        assertEquals("Chair", result.getRows().get(2).getValue(0));
    }

    @Test
    public void testNotIn() {
        ResultSet result = engine.executeQuery(
            "SELECT name, category FROM products WHERE category NOT IN ('Electronics') ORDER BY name"
        );

        // Should return only Furniture items
        assertEquals(3, result.getRows().size());
        assertEquals("Chair", result.getRows().get(0).getValue(0));
        assertEquals("Desk", result.getRows().get(1).getValue(0));
        assertEquals("Lamp", result.getRows().get(2).getValue(0));
    }

    @Test
    public void testInWithSubquery() {
        ResultSet result = engine.executeQuery("""
            SELECT name, category FROM products
            WHERE category IN (SELECT name FROM categories)
            ORDER BY name
            """);

        // Should return all products that match categories in the categories table
        assertEquals(7, result.getRows().size());
    }

    @Test
    public void testNotInWithSubquery() {
        // Remove Furniture from categories
        engine.execute("DELETE FROM categories WHERE name = 'Furniture'");

        ResultSet result = engine.executeQuery("""
            SELECT name, category FROM products
            WHERE category NOT IN (SELECT name FROM categories)
            ORDER BY name
            """);

        // Should return only Furniture items (not in categories)
        assertEquals(3, result.getRows().size());
        assertEquals("Chair", result.getRows().get(0).getValue(0));
        assertEquals("Desk", result.getRows().get(1).getValue(0));
        assertEquals("Lamp", result.getRows().get(2).getValue(0));
    }

    @Test
    public void testInWithSingleValue() {
        ResultSet result = engine.executeQuery(
            "SELECT name FROM products WHERE category IN ('Electronics') ORDER BY name"
        );

        assertEquals(4, result.getRows().size());
        assertEquals("Keyboard", result.getRows().get(0).getValue(0));
        assertEquals("Laptop", result.getRows().get(1).getValue(0));
        assertEquals("Monitor", result.getRows().get(2).getValue(0));
        assertEquals("Mouse", result.getRows().get(3).getValue(0));
    }

    @Test
    public void testInEmptyList() {
        // Create a query with empty result - NOT IN empty set should return all rows
        engine.execute("DELETE FROM categories");

        ResultSet result = engine.executeQuery("""
            SELECT name FROM products
            WHERE category NOT IN (SELECT name FROM categories)
            ORDER BY name
            """);

        // All products should be returned
        assertEquals(7, result.getRows().size());
    }

    // Combined tests
    @Test
    public void testBetweenAndIn() {
        ResultSet result = engine.executeQuery("""
            SELECT name, price FROM products
            WHERE price BETWEEN 50 AND 400
            AND category IN ('Electronics')
            ORDER BY price
            """);

        // Electronics items with price between 50 and 400: Keyboard (75), Monitor (400)
        assertEquals(2, result.getRows().size());
        assertEquals("Keyboard", result.getRows().get(0).getValue(0));
        assertEquals(75L, result.getRows().get(0).getValue(1));
        assertEquals("Monitor", result.getRows().get(1).getValue(0));
        assertEquals(400L, result.getRows().get(1).getValue(1));
    }

    @Test
    public void testNotBetweenAndNotIn() {
        ResultSet result = engine.executeQuery("""
            SELECT name, price FROM products
            WHERE price NOT BETWEEN 100 AND 500
            AND category NOT IN ('Furniture')
            ORDER BY price
            """);

        // Electronics items with price < 100 or > 500: Mouse (25), Keyboard (75), Laptop (1200)
        assertEquals(3, result.getRows().size());
        assertEquals("Mouse", result.getRows().get(0).getValue(0));
        assertEquals("Keyboard", result.getRows().get(1).getValue(0));
        assertEquals("Laptop", result.getRows().get(2).getValue(0));
    }

    @Test
    public void testBetweenOrIn() {
        ResultSet result = engine.executeQuery("""
            SELECT name, price FROM products
            WHERE price BETWEEN 300 AND 500
            OR category IN ('Furniture')
            ORDER BY price
            """);

        // Furniture items OR items with price 300-500
        // Lamp (50), Chair (150), Desk (300), Monitor (400)
        assertEquals(4, result.getRows().size());
        assertEquals("Lamp", result.getRows().get(0).getValue(0));
        assertEquals("Chair", result.getRows().get(1).getValue(0));
        assertEquals("Desk", result.getRows().get(2).getValue(0));
        assertEquals("Monitor", result.getRows().get(3).getValue(0));
    }
}
