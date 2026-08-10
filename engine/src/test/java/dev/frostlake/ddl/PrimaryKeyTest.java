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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRIMARY KEY metadata, asserted through the SQL surface — {@code SHOW PRIMARY KEYS IN TABLE},
 * {@code DESCRIBE TABLE}'s {@code primary key}/{@code null?} cells and {@code GET_DDL} — so every
 * check runs against whichever engine executed the DDL, embedded or live.
 */
public class PrimaryKeyTest extends BaseDatabaseTest {

    private ResultSet primaryKeys(final String table) {
        return engine.executeQuery("SHOW PRIMARY KEYS IN TABLE " + table);
    }

    @Test
    public void testSingleColumnPrimaryKey() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            name VARCHAR,
            email VARCHAR
            )
            """);

        assertEquals(3, engine.executeQuery("DESCRIBE TABLE users").getRowCount());
        assertEquals("Y", describeCell("users", "ID", "primary key"));
        assertEquals("N", describeCell("users", "NAME", "primary key"));
        assertEquals("N", describeCell("users", "EMAIL", "primary key"));

        final ResultSet pk = primaryKeys("users");
        assertEquals(1, pk.getRowCount());
        soleRowWhere(pk, "column_name", "ID");
    }

    @Test
    public void testPrimaryKeyWithNotNull() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY NOT NULL,
            name VARCHAR
            )
            """);

        assertEquals("Y", describeCell("users", "ID", "primary key"));
        assertEquals("N", describeCell("users", "ID", "null?"), "Primary key should be NOT NULL");
    }

    @Test
    public void testMultipleSingleColumnPrimaryKeys() {
        // This should work - each table has one primary key
        engine.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE products (product_id INTEGER PRIMARY KEY, name VARCHAR)");

        assertEquals(1, primaryKeys("users").getRowCount());
        assertEquals(1, primaryKeys("products").getRowCount());
    }

    @Test
    public void testPrimaryKeyWithAutoIncrement() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name VARCHAR
            )
            """);

        assertEquals("Y", describeCell("users", "ID", "primary key"));
        // AUTOINCREMENT is visible in the reconstructed DDL (rendered lower-case with its start
        // and increment, the GET_DDL spelling).
        final String ddl = engine.executeQuery("SELECT GET_DDL('TABLE', 'users')")
            .getRows().get(0).getValue(0).toString();
        assertTrue(ddl.toLowerCase().contains("autoincrement"), ddl);
    }

    @Test
    public void testPrimaryKeyWithDefault() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY DEFAULT 1,
            name VARCHAR
            )
            """);

        assertEquals("Y", describeCell("users", "ID", "primary key"));
        assertEquals("1", describeCell("users", "ID", "default"));
    }

    @Test
    public void testInsertWithPrimaryKey() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            name VARCHAR
            )
            """);

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("INSERT INTO users VALUES (2, 'Bob')");

        final ResultSet result = engine.executeQuery("SELECT * FROM users ORDER BY id");
        assertEquals(2, result.getRowCount());

        result.next();
        assertEquals(1L, result.getValue("id"));
        assertEquals("Alice", result.getValue("name"));

        result.next();
        assertEquals(2L, result.getValue("id"));
        assertEquals("Bob", result.getValue("name"));
    }

    @Test
    public void testPrimaryKeyInDifferentDataTypes() {
        // INTEGER primary key
        engine.execute("CREATE TABLE t1 (id INTEGER PRIMARY KEY, name VARCHAR)");

        // BIGINT primary key
        engine.execute("CREATE TABLE t2 (id BIGINT PRIMARY KEY, name VARCHAR)");

        // VARCHAR primary key
        engine.execute("CREATE TABLE t3 (id VARCHAR PRIMARY KEY, name VARCHAR)");

        assertEquals("Y", describeCell("t1", "ID", "primary key"));
        assertEquals("Y", describeCell("t2", "ID", "primary key"));
        assertEquals("Y", describeCell("t3", "ID", "primary key"));
    }

    @Test
    public void testGetPrimaryKeysList() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            username VARCHAR,
            email VARCHAR
            )
            """);

        final ResultSet pk = primaryKeys("users");
        assertEquals(1, pk.getRowCount());
        assertEquals("ID", cell(pk, pk.getRows().get(0), "column_name"));
        assertEquals("1", cell(pk, pk.getRows().get(0), "key_sequence"));
    }

    @Test
    public void testTableWithNoPrimaryKey() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");

        assertEquals(0, primaryKeys("users").getRowCount(), "Table should have no primary keys");
        assertEquals("N", describeCell("users", "ID", "primary key"));
        assertEquals("N", describeCell("users", "NAME", "primary key"));
        assertEquals("N", describeCell("users", "EMAIL", "primary key"));
    }

    @Test
    public void testPrimaryKeyWithComplexTable() {
        engine.execute("""
            CREATE TABLE orders (
            order_id INTEGER PRIMARY KEY,
            customer_id INTEGER NOT NULL,
            order_date DATE,
            total_amount DECIMAL,
            status VARCHAR DEFAULT 'pending'
            )
            """);

        assertEquals(5, engine.executeQuery("DESCRIBE TABLE orders").getRowCount());
        assertEquals("Y", describeCell("orders", "ORDER_ID", "primary key"));
        assertEquals("N", describeCell("orders", "CUSTOMER_ID", "primary key"));
        assertEquals("N", describeCell("orders", "CUSTOMER_ID", "null?"));
    }

    @Test
    public void testShowPrimaryKeyInSystemViews() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            name VARCHAR
            )
            """);

        final ResultSet columns = engine.executeQuery("SHOW COLUMNS IN TABLE users");
        assertEquals(1, rowsWhere(columns, "column_name", "ID").size(),
            "Primary key column should be visible in SHOW COLUMNS");
    }
}
