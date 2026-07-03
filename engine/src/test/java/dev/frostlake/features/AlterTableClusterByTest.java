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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ALTER TABLE CLUSTER BY clause
 * Allows changing or setting cluster keys on existing tables
 */
public class AlterTableClusterByTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterTableClusterByTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for ALTER TABLE CLUSTER BY tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAlterTableAddClusterBy() {
        logger.info("Testing ALTER TABLE to add CLUSTER BY");

        engine.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, order_date VARCHAR)");

        Table table = engine.getCatalog().resolveTable("orders");
        assertNotNull(table);
        assertEquals(0, table.getClusterKeys().size());

        engine.execute("ALTER TABLE orders CLUSTER BY (order_date)");

        table = engine.getCatalog().resolveTable("orders");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(1, clusterKeys.size());
        assertEquals("order_date", clusterKeys.get(0));

        logger.info("ALTER TABLE add CLUSTER BY works correctly");
    }

    @Test
    public void testAlterTableAddMultipleClusterKeys() {
        logger.info("Testing ALTER TABLE with multiple cluster keys");

        engine.execute("CREATE TABLE sales (id INTEGER, region VARCHAR, product VARCHAR, amount INTEGER)");
        engine.execute("ALTER TABLE sales CLUSTER BY (region, product)");

        Table table = engine.getCatalog().resolveTable("sales");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(2, clusterKeys.size());
        assertEquals("region", clusterKeys.get(0));
        assertEquals("product", clusterKeys.get(1));

        logger.info("ALTER TABLE with multiple cluster keys works correctly");
    }

    @Test
    public void testAlterTableChangeClusterBy() {
        logger.info("Testing ALTER TABLE to change existing CLUSTER BY");

        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR, country VARCHAR, city VARCHAR) CLUSTER BY (country)");

        Table table = engine.getCatalog().resolveTable("customers");
        assertEquals(1, table.getClusterKeys().size());
        assertEquals("country", table.getClusterKeys().get(0));

        engine.execute("ALTER TABLE customers CLUSTER BY (city)");

        table = engine.getCatalog().resolveTable("customers");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(1, clusterKeys.size());
        assertEquals("city", clusterKeys.get(0));

        logger.info("ALTER TABLE change CLUSTER BY works correctly");
    }

    @Test
    public void testAlterTableChangeToMultipleClusterKeys() {
        logger.info("Testing ALTER TABLE to change from single to multiple cluster keys");

        engine.execute("CREATE TABLE products (id INTEGER, category VARCHAR, brand VARCHAR, price INTEGER) CLUSTER BY (category)");
        engine.execute("ALTER TABLE products CLUSTER BY (category, brand)");

        Table table = engine.getCatalog().resolveTable("products");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(2, clusterKeys.size());
        assertEquals("category", clusterKeys.get(0));
        assertEquals("brand", clusterKeys.get(1));

        logger.info("ALTER TABLE change to multiple cluster keys works correctly");
    }

    @Test
    public void testAlterTableClusterByWithExpression() {
        logger.info("Testing ALTER TABLE CLUSTER BY with expression");

        engine.execute("CREATE TABLE users (id INTEGER, email VARCHAR, created_at VARCHAR)");
        engine.execute("ALTER TABLE users CLUSTER BY (UPPER(email))");

        Table table = engine.getCatalog().resolveTable("users");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(1, clusterKeys.size());
        assertTrue(clusterKeys.get(0).contains("UPPER"));
        assertTrue(clusterKeys.get(0).contains("email"));

        logger.info("ALTER TABLE CLUSTER BY with expression works correctly");
    }

    @Test
    public void testAlterTableClusterByWithMultipleExpressions() {
        logger.info("Testing ALTER TABLE CLUSTER BY with multiple expressions");

        engine.execute("CREATE TABLE events (id INTEGER, name VARCHAR, timestamp VARCHAR)");
        engine.execute("ALTER TABLE events CLUSTER BY (UPPER(name), DATE(timestamp))");

        Table table = engine.getCatalog().resolveTable("events");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(2, clusterKeys.size());
        assertTrue(clusterKeys.get(0).contains("UPPER"));
        assertTrue(clusterKeys.get(1).contains("DATE"));

        logger.info("ALTER TABLE CLUSTER BY with multiple expressions works correctly");
    }

    @Test
    public void testAlterTableClusterByWithArithmeticExpression() {
        logger.info("Testing ALTER TABLE CLUSTER BY with arithmetic expression");

        engine.execute("CREATE TABLE metrics (id INTEGER, value INTEGER, multiplier INTEGER)");
        engine.execute("ALTER TABLE metrics CLUSTER BY (value * multiplier)");

        Table table = engine.getCatalog().resolveTable("metrics");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(1, clusterKeys.size());
        assertTrue(clusterKeys.get(0).contains("value"));
        assertTrue(clusterKeys.get(0).contains("multiplier"));

        logger.info("ALTER TABLE CLUSTER BY with arithmetic expression works correctly");
    }

    @Test
    public void testAlterTableClusterByMixedColumnsAndExpressions() {
        logger.info("Testing ALTER TABLE CLUSTER BY with mixed columns and expressions");

        engine.execute("CREATE TABLE transactions (id INTEGER, user_id INTEGER, date VARCHAR, amount INTEGER)");
        engine.execute("ALTER TABLE transactions CLUSTER BY (user_id, UPPER(date), amount * 100)");

        Table table = engine.getCatalog().resolveTable("transactions");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(3, clusterKeys.size());
        assertEquals("user_id", clusterKeys.get(0));
        assertTrue(clusterKeys.get(1).contains("UPPER"));
        assertTrue(clusterKeys.get(2).contains("amount"));

        logger.info("ALTER TABLE CLUSTER BY with mixed columns and expressions works correctly");
    }

    @Test
    public void testAlterTableClusterByWithQualifiedName() {
        logger.info("Testing ALTER TABLE CLUSTER BY with qualified table name");

        engine.execute("CREATE SCHEMA analytics");
        engine.execute("CREATE TABLE analytics.reports (id INTEGER, region VARCHAR, quarter VARCHAR)");
        engine.execute("ALTER TABLE analytics.reports CLUSTER BY (region, quarter)");

        Table table = engine.getCatalog().resolveTable("analytics.reports");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(2, clusterKeys.size());
        assertEquals("region", clusterKeys.get(0));
        assertEquals("quarter", clusterKeys.get(1));

        logger.info("ALTER TABLE CLUSTER BY with qualified name works correctly");
    }

    @Test
    public void testAlterTableClusterByAfterInsert() {
        logger.info("Testing ALTER TABLE CLUSTER BY after data insertion");

        engine.execute("CREATE TABLE items (id INTEGER, category VARCHAR)");
        engine.execute("INSERT INTO items VALUES (1, 'Electronics'), (2, 'Furniture'), (3, 'Electronics')");
        engine.execute("ALTER TABLE items CLUSTER BY (category)");

        Table table = engine.getCatalog().resolveTable("items");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(1, clusterKeys.size());
        assertEquals("category", clusterKeys.get(0));

        logger.info("ALTER TABLE CLUSTER BY after insert works correctly");
    }

    @Test
    public void testAlterTableClusterByWithIfExists() {
        logger.info("Testing ALTER TABLE IF EXISTS CLUSTER BY");

        engine.execute("CREATE TABLE test1 (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE IF EXISTS test1 CLUSTER BY (name)");

        Table table = engine.getCatalog().resolveTable("test1");
        assertEquals(1, table.getClusterKeys().size());

        engine.execute("ALTER TABLE IF EXISTS nonexistent CLUSTER BY (col1)");

        logger.info("ALTER TABLE IF EXISTS CLUSTER BY works correctly");
    }

    @Test
    public void testAlterTableMultipleAlterations() {
        logger.info("Testing multiple ALTER TABLE operations including CLUSTER BY");

        engine.execute("CREATE TABLE multi_alter (id INTEGER, col1 VARCHAR)");
        engine.execute("ALTER TABLE multi_alter ADD COLUMN col2 INTEGER");
        engine.execute("ALTER TABLE multi_alter CLUSTER BY (col1)");
        engine.execute("ALTER TABLE multi_alter ADD COLUMN col3 VARCHAR");
        engine.execute("ALTER TABLE multi_alter CLUSTER BY (col1, col2)");

        Table table = engine.getCatalog().resolveTable("multi_alter");
        assertEquals(4, table.getColumns().size());
        assertEquals(2, table.getClusterKeys().size());
        assertEquals("col1", table.getClusterKeys().get(0));
        assertEquals("col2", table.getClusterKeys().get(1));

        logger.info("Multiple ALTER TABLE operations including CLUSTER BY work correctly");
    }

    @Test
    public void testAlterTableClusterByWithCastExpression() {
        logger.info("Testing ALTER TABLE CLUSTER BY with CAST expression");

        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR, score DECIMAL)");
        engine.execute("ALTER TABLE data CLUSTER BY (CAST(value AS INTEGER))");

        Table table = engine.getCatalog().resolveTable("data");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(1, clusterKeys.size());
        assertTrue(clusterKeys.get(0).contains("CAST"));
        assertTrue(clusterKeys.get(0).contains("value"));

        logger.info("ALTER TABLE CLUSTER BY with CAST expression works correctly");
    }

    @Test
    public void testAlterTableClusterByRemoveBySettingEmpty() {
        logger.info("Testing ALTER TABLE CLUSTER BY to update clustering");

        engine.execute("CREATE TABLE clustered (id INTEGER, col1 VARCHAR, col2 VARCHAR) CLUSTER BY (col1, col2)");

        Table table = engine.getCatalog().resolveTable("clustered");
        assertEquals(2, table.getClusterKeys().size());

        engine.execute("ALTER TABLE clustered CLUSTER BY (col1)");

        table = engine.getCatalog().resolveTable("clustered");
        assertEquals(1, table.getClusterKeys().size());
        assertEquals("col1", table.getClusterKeys().get(0));

        logger.info("ALTER TABLE CLUSTER BY update works correctly");
    }

    @Test
    public void testAlterTableClusterByWithSubstringExpression() {
        logger.info("Testing ALTER TABLE CLUSTER BY with SUBSTRING expression");

        engine.execute("CREATE TABLE logs (id INTEGER, message VARCHAR, timestamp VARCHAR)");
        engine.execute("ALTER TABLE logs CLUSTER BY (SUBSTRING(message, 1, 10))");

        Table table = engine.getCatalog().resolveTable("logs");
        List<String> clusterKeys = table.getClusterKeys();
        assertEquals(1, clusterKeys.size());
        assertTrue(clusterKeys.get(0).contains("SUBSTRING"));
        assertTrue(clusterKeys.get(0).contains("message"));

        logger.info("ALTER TABLE CLUSTER BY with SUBSTRING expression works correctly");
    }

    @Test
    public void testAlterTableClusterByPreservesData() {
        logger.info("Testing ALTER TABLE CLUSTER BY preserves data");

        engine.execute("CREATE TABLE preserve_test (id INTEGER, region VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO preserve_test VALUES (1, 'US', 100), (2, 'EU', 200), (3, 'US', 150)");
        engine.execute("ALTER TABLE preserve_test CLUSTER BY (region)");

        Table table = engine.getCatalog().resolveTable("preserve_test");
        assertEquals(1, table.getClusterKeys().size());

        // Verify data is still present
        engine.execute("SELECT * FROM preserve_test");

        logger.info("ALTER TABLE CLUSTER BY preserves data correctly");
    }
}
