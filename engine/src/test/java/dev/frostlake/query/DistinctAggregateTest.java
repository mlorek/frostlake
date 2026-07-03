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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DistinctAggregateTest {

    private static final Logger logger = LoggerFactory.getLogger(DistinctAggregateTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCountDistinct() {
        logger.info("Testing COUNT(DISTINCT column)");

        ResultSet rs = engine.executeQuery(
            "SELECT COUNT(*), COUNT(DISTINCT i) FROM VALUES(1, 2), (1, 2), (2, 3) AS t(i, j)");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 row");
        assertEquals(2, rs.getColumns().size(), "Should have 2 columns");

        // COUNT(*) should be 3, COUNT(DISTINCT i) should be 2
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue(), "COUNT(*) should be 3");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue(), "COUNT(DISTINCT i) should be 2");
    }

    @Test
    public void testUserQueryExample() {
        logger.info("Testing user's exact query with COUNT(*) and COUNT(DISTINCT i)");

        // User's exact query: select i,j, count(*), count(distinct i) from values (1,2),(1,2) as t(i,j) group by i,j
        ResultSet rs = engine.executeQuery(
            "select i,j, count(*), count(distinct i) from values (1,2),(1,2) as t(i,j) group by i,j");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 grouped row");
        assertEquals(4, rs.getColumns().size(), "Should have 4 columns");

        // Verify values
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue(), "i should be 1");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue(), "j should be 2");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(2)).longValue(), "count(*) should be 2");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(3)).longValue(), "count(distinct i) should be 1");
    }

    @Test
    public void testSumDistinct() {
        logger.info("Testing SUM(DISTINCT column)");

        ResultSet rs = engine.executeQuery(
            "SELECT SUM(i), SUM(DISTINCT i) FROM VALUES(1), (1), (2) AS t(i)");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 row");

        // SUM(i) should be 4 (1+1+2), SUM(DISTINCT i) should be 3 (1+2)
        assertEquals(4.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.001, "SUM(i) should be 4");
        assertEquals(3.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.001, "SUM(DISTINCT i) should be 3");
    }

    @Test
    public void testAvgDistinct() {
        logger.info("Testing AVG(DISTINCT column)");

        ResultSet rs = engine.executeQuery(
            "SELECT AVG(i), AVG(DISTINCT i) FROM VALUES(1), (1), (2), (4) AS t(i)");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 row");

        // AVG(i) should be 2.0 ((1+1+2+4)/4), AVG(DISTINCT i) should be 2.333 ((1+2+4)/3)
        assertEquals(2.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.001, "AVG(i) should be 2.0");
        assertEquals(2.333, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.01, "AVG(DISTINCT i) should be ~2.33");
    }

    @Test
    public void testCountDistinctWithGroupBy() {
        logger.info("Testing COUNT(DISTINCT) with GROUP BY");

        engine.execute("CREATE TABLE sales (category VARCHAR, product VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('Electronics', 'Phone', 100)");
        engine.execute("INSERT INTO sales VALUES ('Electronics', 'Phone', 100)");
        engine.execute("INSERT INTO sales VALUES ('Electronics', 'Laptop', 200)");
        engine.execute("INSERT INTO sales VALUES ('Clothing', 'Shirt', 50)");
        engine.execute("INSERT INTO sales VALUES ('Clothing', 'Shirt', 50)");

        ResultSet rs = engine.executeQuery(
            "SELECT category, COUNT(*), COUNT(DISTINCT product) FROM sales GROUP BY category ORDER BY category");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should return 2 rows");

        // Clothing: 2 total, 1 distinct product
        assertEquals("Clothing", rs.getRows().get(0).getValue(0));
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(2)).longValue());

        // Electronics: 3 total, 2 distinct products
        assertEquals("Electronics", rs.getRows().get(1).getValue(0));
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void testCountDistinctWithNulls() {
        logger.info("Testing COUNT(DISTINCT) with NULL values");

        ResultSet rs = engine.executeQuery(
            "SELECT COUNT(i), COUNT(DISTINCT i) FROM VALUES(1), (1), (2), (NULL) AS t(i)");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 row");

        // COUNT(i) should be 3 (nulls excluded), COUNT(DISTINCT i) should be 2
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue(), "COUNT(i) should be 3");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue(), "COUNT(DISTINCT i) should be 2");
    }

    @Test
    public void testMultipleDistinctAggregates() {
        logger.info("Testing multiple DISTINCT aggregates in same query");

        ResultSet rs = engine.executeQuery(
            "SELECT COUNT(DISTINCT i), COUNT(DISTINCT j) FROM VALUES(1, 2), (1, 3), (2, 2) AS t(i, j)");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return 1 row");

        // COUNT(DISTINCT i) should be 2, COUNT(DISTINCT j) should be 2
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue(), "COUNT(DISTINCT i) should be 2");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue(), "COUNT(DISTINCT j) should be 2");
    }
}
