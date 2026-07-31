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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for SELECT DISTINCT
 */
public class SelectDistinctTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(SelectDistinctTest.class);

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE TABLE test_data (
                id INTEGER,
                category VARCHAR,
                value INTEGER
            )
            """);

        engine.execute("INSERT INTO test_data VALUES (1, 'A', 10)");
        engine.execute("INSERT INTO test_data VALUES (2, 'B', 20)");
        engine.execute("INSERT INTO test_data VALUES (3, 'A', 10)");
        engine.execute("INSERT INTO test_data VALUES (4, 'B', 20)");
        engine.execute("INSERT INTO test_data VALUES (5, 'C', 30)");
    }

    @Test
    public void testSelectDistinctSingleColumn() {
        logger.info("Testing SELECT DISTINCT on single column");

        ResultSet result = engine.executeQuery("SELECT DISTINCT category FROM test_data");

        assertEquals(3, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctMultipleColumns() {
        logger.info("Testing SELECT DISTINCT on multiple columns");

        ResultSet result = engine.executeQuery("SELECT DISTINCT category, value FROM test_data");

        assertEquals(3, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctFromValues() {
        logger.info("Testing SELECT DISTINCT from VALUES");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT $1, $2
            FROM VALUES(1, 2), (1, 2), (3, 4)
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(2L, result.getRows().get(0).getValue(1));
        assertEquals(3L, result.getRows().get(1).getValue(0));
        assertEquals(4L, result.getRows().get(1).getValue(1));
    }

    @Test
    public void testSelectDistinctFromValuesWithAlias() {
        logger.info("Testing SELECT DISTINCT from VALUES with alias");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT a, b
            FROM VALUES(1, 2), (1, 2) AS t(a, b)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctWithWhere() {
        logger.info("Testing SELECT DISTINCT with WHERE clause");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT category
            FROM test_data
            WHERE value >= 20
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctWithOrderBy() {
        logger.info("Testing SELECT DISTINCT with ORDER BY");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT category
            FROM test_data
            ORDER BY category DESC
            """);

        assertEquals(3, result.getRowCount());
        assertEquals(1, result.getColumnCount());
        assertEquals("C", result.getRows().get(0).getValue(0));
        assertEquals("B", result.getRows().get(1).getValue(0));
        assertEquals("A", result.getRows().get(2).getValue(0));
    }

    @Test
    public void testSelectDistinctWithLimit() {
        logger.info("Testing SELECT DISTINCT with LIMIT");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT category
            FROM test_data
            LIMIT 2
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctAllSame() {
        logger.info("Testing SELECT DISTINCT when all rows are identical");

        engine.execute("CREATE TABLE same_values (col VARCHAR)");
        engine.execute("INSERT INTO same_values VALUES ('A'), ('A'), ('A')");

        ResultSet result = engine.executeQuery("SELECT DISTINCT col FROM same_values");

        assertEquals(1, result.getRowCount());
        assertEquals(1, result.getColumnCount());
        assertEquals("A", result.getRows().get(0).getValue(0));
    }

    @Test
    public void testSelectDistinctAllUnique() {
        logger.info("Testing SELECT DISTINCT when all rows are unique");

        engine.execute("CREATE TABLE unique_values (col VARCHAR)");
        engine.execute("INSERT INTO unique_values VALUES ('A'), ('B'), ('C')");

        ResultSet result = engine.executeQuery("SELECT DISTINCT col FROM unique_values");

        assertEquals(3, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctWithNulls() {
        logger.info("Testing SELECT DISTINCT with NULL values");

        engine.execute("CREATE TABLE with_nulls (col VARCHAR)");
        engine.execute("INSERT INTO with_nulls VALUES ('A'), (NULL), ('A'), (NULL), ('B')");

        ResultSet result = engine.executeQuery("SELECT DISTINCT col FROM with_nulls");

        assertEquals(3, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctVsSelectAll() {
        logger.info("Testing SELECT DISTINCT vs SELECT all");

        ResultSet allResult = engine.executeQuery("SELECT category FROM test_data");
        ResultSet distinctResult = engine.executeQuery("SELECT DISTINCT category FROM test_data");

        assertEquals(5, allResult.getRowCount());
        assertEquals(3, distinctResult.getRowCount());
    }

    @Test
    public void testSelectDistinctWithExpressions() {
        logger.info("Testing SELECT DISTINCT with expressions");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT value * 2 AS doubled
            FROM test_data
            """);

        assertEquals(3, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctWithFunctions() {
        logger.info("Testing SELECT DISTINCT with functions");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT UPPER(category) AS upper_cat
            FROM test_data
            """);

        assertEquals(3, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctEmptyTable() {
        logger.info("Testing SELECT DISTINCT from empty table");

        engine.execute("CREATE TABLE empty (col VARCHAR)");

        ResultSet result = engine.executeQuery("SELECT DISTINCT col FROM empty");

        assertEquals(0, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctWithJoin() {
        logger.info("Testing SELECT DISTINCT with JOIN");

        engine.execute("CREATE TABLE categories (name VARCHAR)");
        engine.execute("INSERT INTO categories VALUES ('A'), ('B'), ('C')");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT c.name
            FROM categories c
            JOIN test_data t ON c.name = t.category
            """);

        assertEquals(3, result.getRowCount());
        assertEquals(1, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctStar() {
        logger.info("Testing SELECT DISTINCT *");

        ResultSet result = engine.executeQuery("SELECT DISTINCT * FROM test_data");

        assertEquals(5, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctThreeColumns() {
        logger.info("Testing SELECT DISTINCT on three columns");

        ResultSet result = engine.executeQuery("SELECT DISTINCT id, category, value FROM test_data");

        assertEquals(5, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctWithPositionalParameters() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "asserts the ROW ORDER of a DISTINCT result that carries no ORDER BY; a real account is free "
            + "to hand the distinct rows back in any order");
        logger.info("Testing SELECT DISTINCT with positional parameters");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT $1, $2
            FROM VALUES(1, 'A'), (2, 'B'), (1, 'A'), (2, 'B'), (3, 'C')
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
    public void testSelectDistinctWithPositionalParametersAndExpressions() {
        logger.info("Testing SELECT DISTINCT with positional parameters and expressions");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT ABS($1), UPPER($2)
            FROM VALUES(-1, 'a'), (-1, 'a'), (2, 'b'), (2, 'b')
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }

    @Test
    public void testSelectDistinctWithTrailingComma() {
        logger.info("Testing SELECT DISTINCT with trailing comma");

        ResultSet result = engine.executeQuery("""
            SELECT DISTINCT
                category,
                value,
            FROM test_data
            """);

        assertEquals(3, result.getRowCount());
        assertEquals(2, result.getColumnCount());
    }
}
