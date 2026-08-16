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

package dev.frostlake.expressions;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IlikeTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(IlikeTest.class);

    @Test
    public void testBasicIlike() throws SQLException {
        logger.info("Testing basic ILIKE case-insensitive matching");

        statement.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO products VALUES (1, 'Apple')");
        statement.execute("INSERT INTO products VALUES (2, 'BANANA')");
        statement.execute("INSERT INTO products VALUES (3, 'cherry')");
        statement.execute("INSERT INTO products VALUES (4, 'Grape')");

        final ResultSet rs = statement.executeQuery("SELECT name FROM products WHERE name ILIKE 'apple' ORDER BY id");
        assertTrue(rs.next());
        assertEquals("Apple", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testIlikeWithWildcards() throws SQLException {
        logger.info("Testing ILIKE with % wildcard");

        statement.execute("CREATE TABLE items (id INTEGER, description VARCHAR)");
        statement.execute("INSERT INTO items VALUES (1, 'Red Apple')");
        statement.execute("INSERT INTO items VALUES (2, 'GREEN BANANA')");
        statement.execute("INSERT INTO items VALUES (3, 'yellow lemon')");
        statement.execute("INSERT INTO items VALUES (4, 'Blue Grape')");

        ResultSet rs = statement.executeQuery("SELECT description FROM items WHERE description ILIKE '%apple%' ORDER BY id");
        assertTrue(rs.next());
        assertEquals("Red Apple", rs.getString(1));
        assertFalse(rs.next());

        rs = statement.executeQuery("SELECT description FROM items WHERE description ILIKE 'green%' ORDER BY id");
        assertTrue(rs.next());
        assertEquals("GREEN BANANA", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testIlikeWithUnderscore() throws SQLException {
        logger.info("Testing ILIKE with _ wildcard");

        statement.execute("CREATE TABLE codes (id INTEGER, code VARCHAR)");
        statement.execute("INSERT INTO codes VALUES (1, 'A123')");
        statement.execute("INSERT INTO codes VALUES (2, 'a456')");
        statement.execute("INSERT INTO codes VALUES (3, 'B789')");

        final ResultSet rs = statement.executeQuery("SELECT code FROM codes WHERE code ILIKE 'a___' ORDER BY id");
        assertTrue(rs.next());
        assertEquals("A123", rs.getString(1));
        assertTrue(rs.next());
        assertEquals("a456", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testIlikeVsLike() throws SQLException {
        logger.info("Testing ILIKE vs LIKE case sensitivity");

        statement.execute("CREATE TABLE words (id INTEGER, word VARCHAR)");
        statement.execute("INSERT INTO words VALUES (1, 'Hello')");
        statement.execute("INSERT INTO words VALUES (2, 'HELLO')");
        statement.execute("INSERT INTO words VALUES (3, 'hello')");

        // LIKE is case-sensitive - only matches exact case
        ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM words WHERE word LIKE 'Hello'");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));

        // ILIKE is case-insensitive - matches all
        rs = statement.executeQuery("SELECT COUNT(*) FROM words WHERE word ILIKE 'Hello'");
        assertTrue(rs.next());
        assertEquals(3, rs.getInt(1));
    }

    @Test
    public void testNotIlike() throws SQLException {
        logger.info("Testing NOT ILIKE");

        statement.execute("CREATE TABLE status_table (id INTEGER, status VARCHAR)");
        statement.execute("INSERT INTO status_table VALUES (1, 'Active')");
        statement.execute("INSERT INTO status_table VALUES (2, 'INACTIVE')");
        statement.execute("INSERT INTO status_table VALUES (3, 'Pending')");

        final ResultSet rs = statement.executeQuery("SELECT status FROM status_table WHERE status NOT ILIKE 'active' ORDER BY id");
        assertTrue(rs.next());
        assertEquals("INACTIVE", rs.getString(1));
        assertTrue(rs.next());
        assertEquals("Pending", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testIlikeWithNullValues() throws SQLException {
        logger.info("Testing ILIKE with NULL values");

        statement.execute("CREATE TABLE nullable_data (id INTEGER, value VARCHAR)");
        statement.execute("INSERT INTO nullable_data VALUES (1, 'Test')");
        statement.execute("INSERT INTO nullable_data VALUES (2, NULL)");
        statement.execute("INSERT INTO nullable_data VALUES (3, 'DATA')");

        final ResultSet rs = statement.executeQuery("SELECT id FROM nullable_data WHERE value ILIKE '%test%' ORDER BY id");
        assertTrue(rs.next());
        assertEquals(1, rs.getInt(1));
        assertFalse(rs.next());
    }

    @Test
    public void testIlikeComplexPattern() throws SQLException {
        logger.info("Testing ILIKE with complex patterns");

        statement.execute("CREATE TABLE emails (id INTEGER, email VARCHAR)");
        statement.execute("INSERT INTO emails VALUES (1, 'user@EXAMPLE.com')");
        statement.execute("INSERT INTO emails VALUES (2, 'admin@TEST.ORG')");
        statement.execute("INSERT INTO emails VALUES (3, 'info@example.com')");

        final ResultSet rs = statement.executeQuery("SELECT email FROM emails WHERE email ILIKE '%@example.com' ORDER BY id");
        assertTrue(rs.next());
        assertEquals("user@EXAMPLE.com", rs.getString(1));
        assertTrue(rs.next());
        assertEquals("info@example.com", rs.getString(1));
        assertFalse(rs.next());
    }

    @Test
    public void testIlikeInWhereClause() throws SQLException {
        logger.info("Testing ILIKE with multiple conditions");

        statement.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, department VARCHAR)");
        statement.execute("INSERT INTO employees VALUES (1, 'John DOE', 'Engineering')");
        statement.execute("INSERT INTO employees VALUES (2, 'Jane Smith', 'ENGINEERING')");
        statement.execute("INSERT INTO employees VALUES (3, 'Bob Wilson', 'Sales')");

        final ResultSet rs = statement.executeQuery(
            "SELECT name FROM employees " +
            "WHERE name ILIKE '%doe%' OR department ILIKE 'engineering' " +
            "ORDER BY id"
        );

        assertTrue(rs.next());
        assertEquals("John DOE", rs.getString(1));
        assertTrue(rs.next());
        assertEquals("Jane Smith", rs.getString(1));
        assertFalse(rs.next());
    }
}
