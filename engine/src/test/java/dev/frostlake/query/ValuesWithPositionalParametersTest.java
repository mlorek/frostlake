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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for VALUES with positional parameters ($1, $2, etc.)
 */
public class ValuesWithPositionalParametersTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ValuesWithPositionalParametersTest.class);

    @Override
    protected void setupTest() {
        // No setup needed for these tests
    }

    @Test
    public void testSelectPositionalParametersFromValues() {
        logger.info("Testing SELECT $1, $2 FROM VALUES");

        final ResultSet result = engine.executeQuery("SELECT $1, $2 FROM VALUES(1, 2)");

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(0).getValue(1));
    }

    @Test
    public void testSelectPositionalParametersMultipleRows() {
        logger.info("Testing SELECT $1, $2 FROM VALUES with multiple rows");

        final ResultSet result = engine.executeQuery("""
            SELECT $1, $2
            FROM VALUES(1, 2), (3, 4), (5, 6)
            """);

        assertEquals(3, result.getRowCount());
        assertEquals(2, result.getColumnCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(0).getValue(1));

        assertEquals(3L, result.getRows().get(1).getValue(0));
        assertEquals(4L, result.getRows().get(1).getValue(1));

        assertEquals(5L, result.getRows().get(2).getValue(0));
        assertEquals(6L, result.getRows().get(2).getValue(1));
    }

    @Test
    public void testSelectPositionalParametersWithStrings() {
        logger.info("Testing SELECT $1, $2 FROM VALUES with strings");

        final ResultSet result = engine.executeQuery("""
            SELECT $1, $2
            FROM VALUES('Alice', 'Engineer'), ('Bob', 'Manager')
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());

        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals("Engineer", result.getRows().get(0).getValue(1));

        assertEquals("Bob", result.getRows().get(1).getValue(0));
        assertEquals("Manager", result.getRows().get(1).getValue(1));
    }

    @Test
    public void testSelectPositionalParametersOutOfOrder() {
        logger.info("Testing SELECT $2, $1 FROM VALUES (reverse order)");

        final ResultSet result = engine.executeQuery("""
            SELECT $2, $1
            FROM VALUES(1, 2)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(0).getValue(1));
    }

    @Test
    public void testSelectPositionalParametersSameColumnTwice() {
        logger.info("Testing SELECT $1, $1 FROM VALUES (same column twice)");

        final ResultSet result = engine.executeQuery("""
            SELECT $1, $1
            FROM VALUES(42)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(42L, result.getRows().get(0).getValue(0));
        assertEquals(42L, result.getRows().get(0).getValue(1));
    }

    @Test
    public void testSelectPositionalParametersWithExpressions() {
        logger.info("Testing SELECT with expressions on positional parameters");

        final ResultSet result = engine.executeQuery("""
            SELECT $1 + $2 AS sum, $1 * $2 AS product
            FROM VALUES(3, 4)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(7L, result.getRows().get(0).getValue(0));
        assertEquals(12L, result.getRows().get(0).getValue(1));
    }

    @Test
    public void testSelectPositionalParametersWithWhere() {
        logger.info("Testing SELECT with WHERE clause on positional parameters");

        final ResultSet result = engine.executeQuery("""
            SELECT $1, $2
            FROM VALUES(1, 10), (2, 20), (3, 30)
            WHERE $1 > 1
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());

        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals(20L, result.getRows().get(0).getValue(1));

        assertEquals(3L, result.getRows().get(1).getValue(0));
        assertEquals(30L, result.getRows().get(1).getValue(1));
    }

    @Test
    public void testSelectPositionalParametersWithOrderBy() {
        logger.info("Testing SELECT with ORDER BY on positional parameters");

        final ResultSet result = engine.executeQuery("""
            SELECT $1, $2
            FROM VALUES(3, 'C'), (1, 'A'), (2, 'B')
            ORDER BY COLUMN1
            """);

        assertEquals(3, result.getRowCount());
        assertEquals(2, result.getColumnCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("A", result.getRows().get(0).getValue(1));

        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals("B", result.getRows().get(1).getValue(1));

        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals("C", result.getRows().get(2).getValue(1));
    }

    @Test
    public void testSelectPositionalParametersThreeColumns() {
        logger.info("Testing SELECT $1, $2, $3 FROM VALUES");

        final ResultSet result = engine.executeQuery("""
            SELECT $1, $2, $3
            FROM VALUES(1, 2, 3)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(3, result.getColumnCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(0).getValue(1));
        assertEquals(3L, result.getRows().get(0).getValue(2));
    }

    @Test
    public void testSelectStarFromValues() {
        logger.info("Testing SELECT * FROM VALUES");

        final ResultSet result = engine.executeQuery("""
            SELECT *
            FROM VALUES(1, 2, 3)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectMixedPositionalAndStar() {
        logger.info("Testing SELECT $1, * FROM VALUES");

        final ResultSet result = engine.executeQuery("""
            SELECT $1, *
            FROM VALUES(10, 20)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(3, result.getColumnCount());
        assertEquals(10L, result.getRows().get(0).getValue(0));
        assertEquals(10L, result.getRows().get(0).getValue(1));
        assertEquals(20L, result.getRows().get(0).getValue(2));
    }

    @Test
    public void testSelectPositionalParametersWithGroupBy() {
        logger.info("Testing SELECT with GROUP BY on positional parameters");

        final ResultSet result = engine.executeQuery("""
            SELECT COLUMN1, COUNT(*)
            FROM VALUES(1, 10), (1, 20), (2, 30)
            GROUP BY COLUMN1
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectPositionalParametersWithJoin() {
        logger.info("Testing SELECT with JOIN using positional parameters");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'Alice'), (2, 'Bob')");

        final ResultSet result = engine.executeQuery("""
            SELECT t.name, v.value
            FROM test_table t
            JOIN (SELECT $1 AS id, $2 AS value FROM VALUES(1, 100), (2, 200)) v
            ON t.id = v.id
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectPositionalParametersWithAlias() {
        logger.info("Testing SELECT positional parameters with table alias");

        final ResultSet result = engine.executeQuery("""
            SELECT v.$1, v.$2
            FROM VALUES(5, 10) AS v
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(5L, result.getRows().get(0).getValue(0));
        assertEquals(10L, result.getRows().get(0).getValue(1));
    }
}
