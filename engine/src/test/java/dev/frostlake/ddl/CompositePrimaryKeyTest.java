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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for composite (multi-column) PRIMARY KEY constraints
 */
public class CompositePrimaryKeyTest extends BaseDatabaseTest {

    private static final String CATALOG_ASSERTIONS =
        "asserts through engine.getCatalog(), which under SF_LIVE still reads the embedded engine — "
        + "the CREATE TABLE went to Snowflake, so the embedded catalog never saw the table; the DDL "
        + "itself is still submitted to the account";

    @Test
    public void testCompositePrimaryKeyTwoColumns() {
        engine.execute("""
            CREATE TABLE order_items (
            order_id INTEGER,
            item_id INTEGER,
            quantity INTEGER,
            PRIMARY KEY (order_id, item_id)
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("order_items");

        assertNotNull(table);
        assertEquals(3, table.getColumns().size());

        // Check that both columns are marked as primary keys
        TableColumn orderIdCol = table.getColumn("order_id");
        TableColumn itemIdCol = table.getColumn("item_id");
        TableColumn quantityCol = table.getColumn("quantity");

        assertTrue(orderIdCol.isPrimaryKey(), "order_id should be part of primary key");
        assertTrue(itemIdCol.isPrimaryKey(), "item_id should be part of primary key");
        assertFalse(quantityCol.isPrimaryKey(), "quantity should NOT be part of primary key");

        // Check primary keys list
        List<String> primaryKeys = table.getPrimaryKeys();
        assertEquals(2, primaryKeys.size(), "Should have 2 primary key columns");

        // Verify both keys are in the list (case-insensitive)
        boolean hasOrderId = primaryKeys.stream()
            .anyMatch((final var pk) -> pk.equalsIgnoreCase("order_id"));
        boolean hasItemId = primaryKeys.stream()
            .anyMatch((final var pk) -> pk.equalsIgnoreCase("item_id"));

        assertTrue(hasOrderId, "Primary keys should include order_id");
        assertTrue(hasItemId, "Primary keys should include item_id");
    }

    @Test
    public void testCompositePrimaryKeyThreeColumns() {
        engine.execute("""
            CREATE TABLE inventory (
            warehouse_id INTEGER,
            product_id INTEGER,
            location VARCHAR,
            quantity INTEGER,
            PRIMARY KEY (warehouse_id, product_id, location)
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("inventory");

        assertEquals(4, table.getColumns().size());

        // Verify all three columns are primary keys
        assertTrue(table.getColumn("warehouse_id").isPrimaryKey());
        assertTrue(table.getColumn("product_id").isPrimaryKey());
        assertTrue(table.getColumn("location").isPrimaryKey());
        assertFalse(table.getColumn("quantity").isPrimaryKey());

        // Check count
        List<String> primaryKeys = table.getPrimaryKeys();
        assertEquals(3, primaryKeys.size(), "Should have 3 primary key columns");
    }

    @Test
    public void testCompositePrimaryKeyWithOtherConstraints() {
        engine.execute("""
            CREATE TABLE user_roles (
            user_id INTEGER NOT NULL,
            role_id INTEGER NOT NULL,
            granted_date DATE,
            granted_by VARCHAR DEFAULT 'system',
            PRIMARY KEY (user_id, role_id)
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("user_roles");

        TableColumn userIdCol = table.getColumn("user_id");
        TableColumn roleIdCol = table.getColumn("role_id");

        assertTrue(userIdCol.isPrimaryKey());
        assertTrue(roleIdCol.isPrimaryKey());
        assertFalse(userIdCol.isNullable());
        assertFalse(roleIdCol.isNullable());

        assertEquals("system", table.getColumn("granted_by").getDefaultValue());
    }

    @Test
    public void testCompositePrimaryKeyInsertData() {
        engine.execute("""
            CREATE TABLE order_items (
            order_id INTEGER,
            item_id INTEGER,
            quantity INTEGER,
            PRIMARY KEY (order_id, item_id)
            )
            """);

        // Insert some data
        engine.execute("INSERT INTO order_items VALUES (1, 101, 5)");
        engine.execute("INSERT INTO order_items VALUES (1, 102, 3)");
        engine.execute("INSERT INTO order_items VALUES (2, 101, 2)");

        ResultSet result = engine.executeQuery("SELECT * FROM order_items ORDER BY order_id, item_id");
        assertEquals(3, result.getRowCount());

        result.next();
        assertEquals(1L, result.getValue("order_id"));
        assertEquals(101L, result.getValue("item_id"));
        assertEquals(5L, result.getValue("quantity"));

        result.next();
        assertEquals(1L, result.getValue("order_id"));
        assertEquals(102L, result.getValue("item_id"));
        assertEquals(3L, result.getValue("quantity"));

        result.next();
        assertEquals(2L, result.getValue("order_id"));
        assertEquals(101L, result.getValue("item_id"));
        assertEquals(2L, result.getValue("quantity"));
    }

    @Test
    public void testMixedPrimaryKeys() {
        // Test table with one column-level PK and one table-level composite PK
        // Should combine both
        engine.execute("""
            CREATE TABLE test_mixed (
            id INTEGER PRIMARY KEY,
            code VARCHAR,
            name VARCHAR
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("test_mixed");

        assertTrue(table.getColumn("id").isPrimaryKey());
        assertEquals(1, table.getPrimaryKeys().size());
    }

    @Test
    public void testCompositePrimaryKeyDifferentOrder() {
        engine.execute("""
            CREATE TABLE test (
            col_a VARCHAR,
            col_b INTEGER,
            col_c DATE,
            col_d BOOLEAN,
            PRIMARY KEY (col_c, col_a)
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("test");

        assertTrue(table.getColumn("col_a").isPrimaryKey());
        assertFalse(table.getColumn("col_b").isPrimaryKey());
        assertTrue(table.getColumn("col_c").isPrimaryKey());
        assertFalse(table.getColumn("col_d").isPrimaryKey());

        assertEquals(2, table.getPrimaryKeys().size());
    }

    @Test
    public void testSingleColumnTableLevelPrimaryKey() {
        // Test that table-level PRIMARY KEY works for single column too
        engine.execute("""
            CREATE TABLE users (
            id INTEGER,
            name VARCHAR,
            PRIMARY KEY (id)
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("users");

        assertTrue(table.getColumn("id").isPrimaryKey());
        assertFalse(table.getColumn("name").isPrimaryKey());

        List<String> primaryKeys = table.getPrimaryKeys();
        assertEquals(1, primaryKeys.size());
    }

    @Test
    public void testCompositePrimaryKeyAllDataTypes() {
        engine.execute("""
            CREATE TABLE multi_key (
            int_key INTEGER,
            varchar_key VARCHAR,
            date_key DATE,
            data VARCHAR,
            PRIMARY KEY (int_key, varchar_key, date_key)
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("multi_key");

        assertTrue(table.getColumn("int_key").isPrimaryKey());
        assertTrue(table.getColumn("varchar_key").isPrimaryKey());
        assertTrue(table.getColumn("date_key").isPrimaryKey());
        assertFalse(table.getColumn("data").isPrimaryKey());
    }

    @Test
    public void testTableWithOnlyPrimaryKeyColumns() {
        engine.execute("""
            CREATE TABLE lookup (
            key1 INTEGER,
            key2 VARCHAR,
            PRIMARY KEY (key1, key2)
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("lookup");

        // All columns should be primary keys
        for (final TableColumn col : table.getColumns()) {
            assertTrue(col.isPrimaryKey(), col.getName() + " should be primary key");
        }

        assertEquals(2, table.getPrimaryKeys().size());
        assertEquals(2, table.getColumns().size());
    }

    @Test
    public void testCompositePrimaryKeyWithQualifiedTableName() {
        engine.execute("""
            CREATE TABLE test_db.test_schema.composite_test (
            id1 INTEGER,
            id2 INTEGER,
            value VARCHAR,
            PRIMARY KEY (id1, id2)
            )
            """);

        Assumptions.assumeFalse(isLiveSnowflake(), CATALOG_ASSERTIONS);
        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Table table = schema.getTable("composite_test");

        assertTrue(table.getColumn("id1").isPrimaryKey());
        assertTrue(table.getColumn("id2").isPrimaryKey());
        assertFalse(table.getColumn("value").isPrimaryKey());
    }
}
