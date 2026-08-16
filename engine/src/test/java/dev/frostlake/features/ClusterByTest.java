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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CLUSTER BY on CREATE TABLE, asserted through the SQL surface — the {@code cluster_by} cell of
 * {@code SHOW TABLES} ({@code LINEAR(keys)} exactly as written, empty when unclustered) — so every
 * check runs against whichever engine executed the DDL, embedded or live.
 */
public class ClusterByTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ClusterByTest.class);

    @Override
    protected void teardownTest() {
        engine.execute("DROP DATABASE IF EXISTS test_db_clone");
    }

    /** One SHOW TABLES cell for the given table in the current schema. */
    private String tableCell(final String table, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        return cell(rs, soleRowWhere(rs, "name", table.toUpperCase()), column);
    }

    private String clusterBy(final String table) {
        return tableCell(table, "cluster_by");
    }

    @Test
    public void testCreateTableWithSingleClusterKey() {
        logger.info("Testing CREATE TABLE with single CLUSTER BY column");

        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR, region VARCHAR) CLUSTER BY (region)");

        assertEquals("LINEAR(region)", clusterBy("customers"));

        logger.info("Single cluster key created successfully");
    }

    @Test
    public void testCreateTableWithMultipleClusterKeys() {
        logger.info("Testing CREATE TABLE with multiple CLUSTER BY columns");

        engine.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, order_date VARCHAR, status VARCHAR) CLUSTER BY (order_date, status)");

        assertEquals("LINEAR(order_date, status)", clusterBy("orders"));

        logger.info("Multiple cluster keys created successfully");
    }

    @Test
    public void testCreateTableWithoutClusterBy() {
        logger.info("Testing CREATE TABLE without CLUSTER BY clause");

        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");

        assertEquals("", clusterBy("products"));

        logger.info("Table without cluster keys created successfully");
    }

    @Test
    public void testCreateTableWithClusterByAndComment() {
        logger.info("Testing CREATE TABLE with both CLUSTER BY and COMMENT");

        engine.execute("CREATE TABLE sales (id INTEGER, product VARCHAR, amount INTEGER) CLUSTER BY (product) COMMENT = 'Sales data clustered by product'");

        assertEquals("LINEAR(product)", clusterBy("sales"));
        assertEquals("Sales data clustered by product", tableCell("sales", "comment"));

        logger.info("Table with cluster keys and comment created successfully");
    }

    @Test
    public void testInsertAndSelectWithClusterBy() {
        logger.info("Testing INSERT and SELECT on table with CLUSTER BY");

        engine.execute("CREATE TABLE inventory (item_id INTEGER, category VARCHAR, quantity INTEGER) CLUSTER BY (category)");
        engine.execute("INSERT INTO inventory VALUES (1, 'Electronics', 100)");
        engine.execute("INSERT INTO inventory VALUES (2, 'Furniture', 50)");
        engine.execute("INSERT INTO inventory VALUES (3, 'Electronics', 75)");

        final ResultSet rs = engine.executeQuery("SELECT * FROM inventory WHERE category = 'Electronics'");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        logger.info("INSERT and SELECT on clustered table works correctly");
    }

    @Test
    public void testCloneTableWithClusterBy() {
        logger.info("Testing CLONE table with CLUSTER BY");

        engine.execute("CREATE TABLE original_table (id INTEGER, region VARCHAR, data VARCHAR) CLUSTER BY (region)");
        engine.execute("INSERT INTO original_table VALUES (1, 'US', 'data1')");

        engine.execute("CREATE TABLE cloned_table CLONE original_table");

        assertEquals("LINEAR(region)", clusterBy("cloned_table"));

        final ResultSet rs = engine.executeQuery("SELECT * FROM cloned_table");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());

        logger.info("Cloned table preserved cluster keys");
    }

    @Test
    public void testCloneDatabaseWithClusteredTables() {
        logger.info("Testing CLONE database with clustered tables");

        engine.execute("CREATE TABLE users (user_id INTEGER, country VARCHAR, name VARCHAR) CLUSTER BY (country)");
        engine.execute("INSERT INTO users VALUES (1, 'USA', 'Alice')");

        engine.execute("CREATE DATABASE test_db_clone CLONE test_db");

        final ResultSet rs = engine.executeQuery(
            "SHOW TABLES LIKE 'users' IN SCHEMA test_db_clone.test_schema");
        assertEquals("LINEAR(country)", cell(rs, soleRowWhere(rs, "name", "USERS"), "cluster_by"));

        logger.info("Database clone preserved cluster keys");
    }

    @Test
    public void testClusterByWithQualifiedName() {
        logger.info("Testing CLUSTER BY with schema-qualified table name");

        engine.execute("CREATE SCHEMA analytics");
        engine.execute("CREATE TABLE analytics.metrics (metric_id INTEGER, date VARCHAR, value INTEGER) CLUSTER BY (date)");

        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE 'metrics' IN SCHEMA analytics");
        assertEquals("LINEAR(date)", cell(rs, soleRowWhere(rs, "name", "METRICS"), "cluster_by"));

        logger.info("CLUSTER BY with qualified name works correctly");
    }

    @Test
    public void testMultipleTablesWithDifferentClustering() {
        logger.info("Testing multiple tables with different clustering");

        engine.execute("CREATE TABLE t1 (id INTEGER, col1 VARCHAR) CLUSTER BY (col1)");
        engine.execute("CREATE TABLE t2 (id INTEGER, col2 VARCHAR, col3 VARCHAR) CLUSTER BY (col2, col3)");
        engine.execute("CREATE TABLE t3 (id INTEGER, col4 VARCHAR)");

        assertEquals("LINEAR(col1)", clusterBy("t1"));
        assertEquals("LINEAR(col2, col3)", clusterBy("t2"));
        assertEquals("", clusterBy("t3"));

        logger.info("Multiple tables with different clustering configurations work correctly");
    }

    @Test
    public void testClusterByWithFunctionExpression() {
        logger.info("Testing CLUSTER BY with function expression");

        engine.execute("CREATE TABLE users (id INTEGER, email VARCHAR, created_at VARCHAR) CLUSTER BY (UPPER(email))");

        assertEquals("LINEAR(UPPER(email))", clusterBy("users"));

        logger.info("CLUSTER BY with function expression created successfully");
    }

    @Test
    public void testClusterByWithMultipleFunctionExpressions() {
        logger.info("Testing CLUSTER BY with multiple function expressions");

        engine.execute("CREATE TABLE events (id INTEGER, name VARCHAR, timestamp VARCHAR) CLUSTER BY (UPPER(name), DATE(timestamp))");

        final String clusterBy = clusterBy("events");
        assertTrue(clusterBy.contains("UPPER(name)"));
        assertTrue(clusterBy.contains("DATE"));
        assertTrue(clusterBy.contains("timestamp"));

        logger.info("CLUSTER BY with multiple function expressions created successfully");
    }

    @Test
    public void testClusterByWithCastExpression() {
        logger.info("Testing CLUSTER BY with CAST expression");

        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR, amount DECIMAL) CLUSTER BY (CAST(value AS INTEGER))");

        final String clusterBy = clusterBy("data");
        assertTrue(clusterBy.contains("CAST"));
        assertTrue(clusterBy.contains("value"));
        assertTrue(clusterBy.contains("INTEGER"));

        logger.info("CLUSTER BY with CAST expression created successfully");
    }

    @Test
    public void testClusterByWithArithmeticExpression() {
        logger.info("Testing CLUSTER BY with arithmetic expression");

        engine.execute("CREATE TABLE metrics (id INTEGER, value INTEGER, multiplier INTEGER) CLUSTER BY (value * multiplier)");

        final String clusterBy = clusterBy("metrics");
        assertTrue(clusterBy.contains("value"));
        assertTrue(clusterBy.contains("multiplier"));

        logger.info("CLUSTER BY with arithmetic expression created successfully");
    }

    @Test
    public void testClusterByWithComplexExpression() {
        logger.info("Testing CLUSTER BY with complex expression");

        engine.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER, order_date VARCHAR, amount DECIMAL) CLUSTER BY (UPPER(order_date), customer_id + 1000)");

        final String clusterBy = clusterBy("orders");
        assertTrue(clusterBy.contains("UPPER"));
        assertTrue(clusterBy.contains("order_date"));
        assertTrue(clusterBy.contains("customer_id"));

        logger.info("CLUSTER BY with complex expression created successfully");
    }

    @Test
    public void testClusterByMixedColumnsAndExpressions() {
        logger.info("Testing CLUSTER BY with mixed columns and expressions");

        engine.execute("CREATE TABLE sales (id INTEGER, region VARCHAR, amount INTEGER, date VARCHAR) CLUSTER BY (region, UPPER(date), amount * 2)");

        final String clusterBy = clusterBy("sales");
        assertTrue(clusterBy.startsWith("LINEAR(region,"));
        assertTrue(clusterBy.contains("UPPER"));
        assertTrue(clusterBy.contains("amount"));

        logger.info("CLUSTER BY with mixed columns and expressions created successfully");
    }

    @Test
    public void testClusterByWithSubstringExpression() {
        logger.info("Testing CLUSTER BY with SUBSTRING expression");

        engine.execute("CREATE TABLE logs (id INTEGER, message VARCHAR, timestamp VARCHAR) CLUSTER BY (SUBSTRING(message, 1, 10))");

        final String clusterBy = clusterBy("logs");
        assertTrue(clusterBy.contains("SUBSTRING"));
        assertTrue(clusterBy.contains("message"));

        logger.info("CLUSTER BY with SUBSTRING expression created successfully");
    }

    @Test
    public void testClusterByExpressionWithClone() {
        logger.info("Testing CLONE table with expression-based CLUSTER BY");

        engine.execute("CREATE TABLE original (id INTEGER, name VARCHAR, value INTEGER) CLUSTER BY (UPPER(name), value * 10)");
        engine.execute("INSERT INTO original VALUES (1, 'test', 5)");

        engine.execute("CREATE TABLE cloned CLONE original");

        final String clusterBy = clusterBy("cloned");
        assertTrue(clusterBy.contains("UPPER"));
        assertTrue(clusterBy.contains("value"));

        logger.info("Cloned table preserved expression-based cluster keys");
    }

    @Test
    public void testClusterByWithConcatExpression() {
        logger.info("Testing CLUSTER BY with concatenation expression");

        engine.execute("CREATE TABLE people (id INTEGER, first_name VARCHAR, last_name VARCHAR) CLUSTER BY (first_name || ' ' || last_name)");

        final String clusterBy = clusterBy("people");
        assertTrue(clusterBy.contains("first_name"));
        assertTrue(clusterBy.contains("last_name"));

        logger.info("CLUSTER BY with concatenation expression created successfully");
    }

    @Test
    public void testClusterByUnknownColumnIsRefused() {
        logger.info("Testing CREATE TABLE with an unknown clustering column is refused");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE cbad (id INTEGER) CLUSTER BY (nosuchcol)");
            }
        });
        assertTrue(e.getMessage().contains("invalid identifier 'NOSUCHCOL'"), e.getMessage());

        assertEquals(0, engine.executeQuery("SHOW TABLES LIKE 'cbad'").getRowCount());

        logger.info("Unknown clustering column refused correctly");
    }

    @Test
    public void testClusterByBeforeColumnList() {
        logger.info("Testing CREATE TABLE with CLUSTER BY before the column list");

        // Snowflake accepts CLUSTER BY immediately after the table name, before the column definitions.
        engine.execute("CREATE TABLE lead_single CLUSTER BY (i) (i INTEGER, name VARCHAR)");

        assertEquals("LINEAR(i)", clusterBy("lead_single"));

        logger.info("CLUSTER BY before column list created successfully");
    }

    @Test
    public void testClusterByBeforeColumnListMultipleKeys() {
        logger.info("Testing CREATE TABLE with multiple CLUSTER BY keys before the column list");

        engine.execute("CREATE TABLE lead_multi CLUSTER BY (region, status) (id INTEGER, region VARCHAR, status VARCHAR)");

        assertEquals("LINEAR(region, status)", clusterBy("lead_multi"));

        logger.info("Multiple CLUSTER BY keys before column list created successfully");
    }

    @Test
    public void testClusterByBeforeColumnListWithExpression() {
        logger.info("Testing CREATE TABLE with an expression CLUSTER BY before the column list");

        engine.execute("CREATE TABLE lead_expr CLUSTER BY (UPPER(name)) (id INTEGER, name VARCHAR)");

        assertEquals("LINEAR(UPPER(name))", clusterBy("lead_expr"));

        logger.info("Expression CLUSTER BY before column list created successfully");
    }

    @Test
    public void testClusterByBeforeColumnListInsertAndSelect() {
        logger.info("Testing INSERT and SELECT on a table declared with CLUSTER BY before the column list");

        engine.execute("CREATE TABLE lead_data CLUSTER BY (category) (item_id INTEGER, category VARCHAR, quantity INTEGER)");
        engine.execute("INSERT INTO lead_data VALUES (1, 'Electronics', 100)");
        engine.execute("INSERT INTO lead_data VALUES (2, 'Furniture', 50)");
        engine.execute("INSERT INTO lead_data VALUES (3, 'Electronics', 75)");

        final ResultSet rs = engine.executeQuery("SELECT * FROM lead_data WHERE category = 'Electronics'");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        logger.info("INSERT and SELECT on leading-CLUSTER-BY table works correctly");
    }
}
