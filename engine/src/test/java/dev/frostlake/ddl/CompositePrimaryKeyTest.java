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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Composite (multi-column) PRIMARY KEY metadata, asserted through the SQL surface —
 * {@code SHOW PRIMARY KEYS IN TABLE} (one row per key column, numbered by {@code key_sequence},
 * sharing one constraint) and {@code DESCRIBE TABLE}'s {@code primary key} cells — so every check
 * runs against whichever engine executed the DDL, embedded or live.
 */
public class CompositePrimaryKeyTest extends BaseDatabaseTest {

    private ResultSet primaryKeys(final String table) {
        return engine.executeQuery("SHOW PRIMARY KEYS IN TABLE " + table);
    }

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

        assertEquals(3, engine.executeQuery("DESCRIBE TABLE order_items").getRowCount());
        assertEquals("Y", describeCell("order_items", "ORDER_ID", "primary key"),
            "order_id should be part of primary key");
        assertEquals("Y", describeCell("order_items", "ITEM_ID", "primary key"),
            "item_id should be part of primary key");
        assertEquals("N", describeCell("order_items", "QUANTITY", "primary key"),
            "quantity should NOT be part of primary key");

        final ResultSet pk = primaryKeys("order_items");
        assertEquals(2, pk.getRowCount(), "Should have 2 primary key columns");
        soleRowWhere(pk, "column_name", "ORDER_ID");
        soleRowWhere(pk, "column_name", "ITEM_ID");
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

        assertEquals(4, engine.executeQuery("DESCRIBE TABLE inventory").getRowCount());
        assertEquals("Y", describeCell("inventory", "WAREHOUSE_ID", "primary key"));
        assertEquals("Y", describeCell("inventory", "PRODUCT_ID", "primary key"));
        assertEquals("Y", describeCell("inventory", "LOCATION", "primary key"));
        assertEquals("N", describeCell("inventory", "QUANTITY", "primary key"));

        assertEquals(3, primaryKeys("inventory").getRowCount(), "Should have 3 primary key columns");
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

        assertEquals("Y", describeCell("user_roles", "USER_ID", "primary key"));
        assertEquals("Y", describeCell("user_roles", "ROLE_ID", "primary key"));
        assertEquals("N", describeCell("user_roles", "USER_ID", "null?"));
        assertEquals("N", describeCell("user_roles", "ROLE_ID", "null?"));

        // The default cell shows the EXPRESSION as written — quotes included (live-verified).
        assertEquals("'system'", describeCell("user_roles", "GRANTED_BY", "default"));
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

        final ResultSet result = engine.executeQuery("SELECT * FROM order_items ORDER BY order_id, item_id");
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
        // A single column-level PRIMARY KEY reads back as a one-column constraint.
        engine.execute("""
            CREATE TABLE test_mixed (
            id INTEGER PRIMARY KEY,
            code VARCHAR,
            name VARCHAR
            )
            """);

        assertEquals("Y", describeCell("test_mixed", "ID", "primary key"));
        assertEquals(1, primaryKeys("test_mixed").getRowCount());
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

        assertEquals("Y", describeCell("test", "COL_A", "primary key"));
        assertEquals("N", describeCell("test", "COL_B", "primary key"));
        assertEquals("Y", describeCell("test", "COL_C", "primary key"));
        assertEquals("N", describeCell("test", "COL_D", "primary key"));

        assertEquals(2, primaryKeys("test").getRowCount());
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

        assertEquals("Y", describeCell("users", "ID", "primary key"));
        assertEquals("N", describeCell("users", "NAME", "primary key"));

        assertEquals(1, primaryKeys("users").getRowCount());
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

        assertEquals("Y", describeCell("multi_key", "INT_KEY", "primary key"));
        assertEquals("Y", describeCell("multi_key", "VARCHAR_KEY", "primary key"));
        assertEquals("Y", describeCell("multi_key", "DATE_KEY", "primary key"));
        assertEquals("N", describeCell("multi_key", "DATA", "primary key"));
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

        // All columns should be primary keys
        assertEquals("Y", describeCell("lookup", "KEY1", "primary key"));
        assertEquals("Y", describeCell("lookup", "KEY2", "primary key"));

        assertEquals(2, primaryKeys("lookup").getRowCount());
        assertEquals(2, engine.executeQuery("DESCRIBE TABLE lookup").getRowCount());
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

        assertEquals("Y", describeCell("composite_test", "ID1", "primary key"));
        assertEquals("Y", describeCell("composite_test", "ID2", "primary key"));
        assertEquals("N", describeCell("composite_test", "VALUE", "primary key"));
    }
}
