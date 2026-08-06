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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CREATE TABLE AS SELECT (CTAS) with OR REPLACE and TEMP/TEMPORARY
 */
public class CreateTableAsSelectTest {
    private static final Logger logger = LoggerFactory.getLogger(CreateTableAsSelectTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for CTAS tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testBasicCreateTableAsSelect() {
        logger.info("Testing basic CREATE TABLE AS SELECT");

        engine.execute("CREATE TABLE source (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'Alice')");
        engine.execute("INSERT INTO source VALUES (2, 'Bob')");

        engine.execute("CREATE TABLE target AS SELECT id, name FROM source");

        Table table = engine.getCatalog().resolveTable("TARGET");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertEquals("ID", table.getColumn("id").getName());
        assertEquals("NAME", table.getColumn("name").getName());

        ResultSet rs = engine.executeQuery("SELECT * FROM target");
        assertEquals(2, rs.getRowCount());

        logger.info("Basic CTAS works correctly");
    }

    @Test
    public void testCreateTableAsSelectWithLiterals() {
        logger.info("Testing CREATE TABLE AS SELECT with literals");

        engine.execute("CREATE TABLE tbl AS SELECT 1 as c, 2 as d");

        Table table = engine.getCatalog().resolveTable("TBL");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertEquals("C", table.getColumn("c").getName());
        assertEquals("D", table.getColumn("d").getName());

        ResultSet rs = engine.executeQuery("SELECT * FROM tbl");
        assertEquals(1, rs.getRowCount());
        rs.next();
        assertEquals(1L, rs.getValue(0));
        assertEquals(2L, rs.getValue(1));

        logger.info("CTAS with literals works correctly");
    }

    @Test
    public void testCreateOrReplaceTable() {
        logger.info("Testing CREATE OR REPLACE TABLE");

        engine.execute("CREATE TABLE test (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO test VALUES (1, 'First')");

        ResultSet rs1 = engine.executeQuery("SELECT * FROM test");
        assertEquals(1, rs1.getRowCount());

        engine.execute("CREATE OR REPLACE TABLE test (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO test VALUES (2, 'Second')");

        Table table = engine.getCatalog().resolveTable("TEST");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());
        assertEquals("VALUE", table.getColumn("value").getName());

        ResultSet rs2 = engine.executeQuery("SELECT * FROM test");
        assertEquals(1, rs2.getRowCount());
        rs2.next();
        assertEquals(2L, rs2.getValue(0));

        logger.info("CREATE OR REPLACE TABLE works correctly");
    }

    @Test
    public void testCreateOrReplaceTempTableAsSelect() {
        logger.info("Testing CREATE OR REPLACE TEMP TABLE AS SELECT");

        engine.execute("CREATE OR REPLACE TEMP TABLE tbl AS SELECT 1 as c, 2 as d");

        Table table = engine.getCatalog().resolveTable("TBL");
        assertNotNull(table);
        assertTrue(table.isTemporary());
        assertEquals(2, table.getColumns().size());

        ResultSet rs = engine.executeQuery("SELECT * FROM tbl");
        assertEquals(1, rs.getRowCount());

        logger.info("CREATE OR REPLACE TEMP TABLE AS SELECT works correctly");
    }

    @Test
    public void testCreateTempTable() {
        logger.info("Testing CREATE TEMP TABLE");

        engine.execute("CREATE TEMP TABLE temp_tbl (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEMP_TBL");
        assertNotNull(table);
        assertTrue(table.isTemporary());
        assertFalse(table.isTransient());

        logger.info("CREATE TEMP TABLE works correctly");
    }

    @Test
    public void testCreateTemporaryTable() {
        logger.info("Testing CREATE TEMPORARY TABLE");

        engine.execute("CREATE TEMPORARY TABLE temp_tbl (id INTEGER, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEMP_TBL");
        assertNotNull(table);
        assertTrue(table.isTemporary());
        assertFalse(table.isTransient());

        logger.info("CREATE TEMPORARY TABLE works correctly");
    }

    @Test
    public void testCreateTempTableAsSelect() {
        logger.info("Testing CREATE TEMP TABLE AS SELECT");

        engine.execute("CREATE TABLE source (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'test')");

        engine.execute("CREATE TEMP TABLE temp_target AS SELECT * FROM source");

        Table table = engine.getCatalog().resolveTable("TEMP_TARGET");
        assertNotNull(table);
        assertTrue(table.isTemporary());

        ResultSet rs = engine.executeQuery("SELECT * FROM temp_target");
        assertEquals(1, rs.getRowCount());

        logger.info("CREATE TEMP TABLE AS SELECT works correctly");
    }

    @Test
    public void testCreateTemporaryTableAsSelect() {
        logger.info("Testing CREATE TEMPORARY TABLE AS SELECT");

        engine.execute("CREATE TEMPORARY TABLE temp_tbl AS SELECT 10 as x, 20 as y");

        Table table = engine.getCatalog().resolveTable("TEMP_TBL");
        assertNotNull(table);
        assertTrue(table.isTemporary());

        ResultSet rs = engine.executeQuery("SELECT * FROM temp_tbl");
        assertEquals(1, rs.getRowCount());
        rs.next();
        assertEquals(10L, rs.getValue(0));
        assertEquals(20L, rs.getValue(1));

        logger.info("CREATE TEMPORARY TABLE AS SELECT works correctly");
    }

    @Test
    public void testCreateOrReplaceTableAsSelect() {
        logger.info("Testing CREATE OR REPLACE TABLE AS SELECT");

        engine.execute("CREATE TABLE tbl AS SELECT 1 as a");
        ResultSet rs1 = engine.executeQuery("SELECT * FROM tbl");
        assertEquals(1, rs1.getRowCount());

        engine.execute("CREATE OR REPLACE TABLE tbl AS SELECT 2 as b, 3 as c");

        Table table = engine.getCatalog().resolveTable("TBL");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());

        ResultSet rs2 = engine.executeQuery("SELECT * FROM tbl");
        assertEquals(1, rs2.getRowCount());
        rs2.next();
        assertEquals(2L, rs2.getValue(0));
        assertEquals(3L, rs2.getValue(1));

        logger.info("CREATE OR REPLACE TABLE AS SELECT works correctly");
    }

    @Test
    public void testCreateTableAsSelectWithWhereClause() {
        logger.info("Testing CREATE TABLE AS SELECT with WHERE clause");

        engine.execute("CREATE TABLE source (id INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'active')");
        engine.execute("INSERT INTO source VALUES (2, 'inactive')");
        engine.execute("INSERT INTO source VALUES (3, 'active')");

        engine.execute("CREATE TABLE filtered AS SELECT id FROM source WHERE status = 'active'");

        ResultSet rs = engine.executeQuery("SELECT * FROM filtered");
        assertEquals(2, rs.getRowCount());

        logger.info("CTAS with WHERE clause works correctly");
    }

    @Test
    public void testCreateTableAsSelectWithJoin() {
        logger.info("Testing CREATE TABLE AS SELECT with JOIN");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE orders (order_id INTEGER, user_id INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO orders VALUES (100, 1)");

        engine.execute("CREATE TABLE joined AS SELECT u.name, o.order_id FROM users u JOIN orders o ON u.id = o.user_id");

        Table table = engine.getCatalog().resolveTable("JOINED");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());

        ResultSet rs = engine.executeQuery("SELECT * FROM joined");
        assertEquals(1, rs.getRowCount());

        logger.info("CTAS with JOIN works correctly");
    }

    @Test
    public void testCreateTableAsSelectWithAggregation() {
        logger.info("Testing CREATE TABLE AS SELECT with aggregation");

        engine.execute("CREATE TABLE sales (product VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('Widget', 100)");
        engine.execute("INSERT INTO sales VALUES ('Widget', 150)");
        engine.execute("INSERT INTO sales VALUES ('Gadget', 200)");

        engine.execute("CREATE TABLE summary AS SELECT product, SUM(amount) as total FROM sales GROUP BY product");

        Table table = engine.getCatalog().resolveTable("SUMMARY");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());

        ResultSet rs = engine.executeQuery("SELECT * FROM summary");
        assertEquals(2, rs.getRowCount());

        logger.info("CTAS with aggregation works correctly");
    }

    @Test
    public void testCreateOrReplaceTempTableMultipleTimes() {
        logger.info("Testing CREATE OR REPLACE TEMP TABLE multiple times");

        engine.execute("CREATE OR REPLACE TEMP TABLE tbl AS SELECT 1 as x");
        ResultSet rs1 = engine.executeQuery("SELECT * FROM tbl");
        assertEquals(1, rs1.getRowCount());

        engine.execute("CREATE OR REPLACE TEMP TABLE tbl AS SELECT 2 as y");
        ResultSet rs2 = engine.executeQuery("SELECT * FROM tbl");
        assertEquals(1, rs2.getRowCount());
        rs2.next();
        assertEquals(2L, rs2.getValue(0));

        Table table = engine.getCatalog().resolveTable("TBL");
        assertTrue(table.isTemporary());

        logger.info("Multiple CREATE OR REPLACE TEMP TABLE works correctly");
    }

    @Test
    public void testCreateTransientVsTempTable() {
        logger.info("Testing TRANSIENT vs TEMP table types");

        engine.execute("CREATE TRANSIENT TABLE trans_tbl (id INTEGER)");
        engine.execute("CREATE TEMP TABLE temp_tbl (id INTEGER)");

        Table transTable = engine.getCatalog().resolveTable("TRANS_TBL");
        Table tempTable = engine.getCatalog().resolveTable("TEMP_TBL");

        assertNotNull(transTable);
        assertNotNull(tempTable);

        assertFalse(transTable.isTemporary());
        assertTrue(transTable.isTransient());

        assertTrue(tempTable.isTemporary());
        assertFalse(tempTable.isTransient());

        logger.info("TRANSIENT vs TEMP table types work correctly");
    }

    @Test
    public void testCreateOrReplaceWithComment() {
        logger.info("Testing CREATE OR REPLACE TABLE with COMMENT");

        engine.execute("CREATE OR REPLACE TABLE tbl (id INTEGER) COMMENT = 'Test table'");

        Table table = engine.getCatalog().resolveTable("TBL");
        assertNotNull(table);
        assertEquals("Test table", table.getComment());

        logger.info("CREATE OR REPLACE with COMMENT works correctly");
    }

    @Test
    public void testCreateTempTableAsSelectWithClusterBy() {
        logger.info("Testing CREATE TEMP TABLE AS SELECT with CLUSTER BY");

        engine.execute("CREATE TABLE source (id INTEGER, region VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'US')");
        engine.execute("INSERT INTO source VALUES (2, 'EU')");

        engine.execute("CREATE TEMP TABLE clustered AS SELECT * FROM source CLUSTER BY (region)");

        Table table = engine.getCatalog().resolveTable("CLUSTERED");
        assertNotNull(table);
        assertTrue(table.isTemporary());
        assertEquals(1, table.getClusterKeys().size());
        assertEquals("region", table.getClusterKeys().get(0));

        logger.info("CREATE TEMP TABLE AS SELECT with CLUSTER BY works correctly");
    }
}
