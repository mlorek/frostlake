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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE TABLE, asserted through the SQL surface — {@code SHOW TABLES}, {@code DESCRIBE TABLE}
 * cells and {@code GET_DDL} — so every check runs against whichever engine executed the DDL,
 * embedded or live.
 */
public class CreateTableTest extends BaseDatabaseTest {

    @Test
    public void testCreateSimpleTable() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");

        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'users'");
        soleRowWhere(tables, "name", "USERS");
        assertEquals(3, engine.executeQuery("DESCRIBE TABLE users").getRowCount());
    }

    @Test
    public void testCreateTableWithPrimaryKey() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            name VARCHAR,
            email VARCHAR
            )
            """);

        assertEquals("Y", describeCell("users", "ID", "primary key"), "First column should be primary key");
    }

    @Test
    public void testCreateTableWithNotNull() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER NOT NULL,
            name VARCHAR NOT NULL,
            age INTEGER
            )
            """);

        assertEquals("N", describeCell("users", "ID", "null?"), "id should be NOT NULL");
        assertEquals("N", describeCell("users", "NAME", "null?"), "name should be NOT NULL");
        assertEquals("Y", describeCell("users", "AGE", "null?"), "age should be nullable");
    }

    @Test
    public void testCreateTableWithDefault() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER,
            name VARCHAR,
            status VARCHAR DEFAULT 'active'
            )
            """);

        // The default cell shows the EXPRESSION as written — quotes included (live-verified).
        assertEquals("'active'", describeCell("users", "STATUS", "default"));
    }

    @Test
    public void testCreateTableWithAutoIncrement() {
        engine.execute("""
            CREATE TABLE users (
            id INTEGER AUTOINCREMENT,
            name VARCHAR
            )
            """);

        // AUTOINCREMENT is visible in the reconstructed DDL (the GET_DDL spelling).
        final String ddl = engine.executeQuery("SELECT GET_DDL('TABLE', 'users')")
            .getRows().get(0).getValue(0).toString();
        assertTrue(ddl.toLowerCase().contains("autoincrement"), ddl);
    }

    @Test
    public void testCreateTableWithAllDataTypes() {
        engine.execute("""
            CREATE TABLE test_types (
            col_int INTEGER,
            col_bigint BIGINT,
            col_smallint SMALLINT,
            col_number NUMBER,
            col_decimal DECIMAL,
            col_float FLOAT,
            col_double DOUBLE,
            col_varchar VARCHAR,
            col_string STRING,
            col_text TEXT,
            col_char CHAR,
            col_boolean BOOLEAN,
            col_date DATE,
            col_timestamp TIMESTAMP,
            col_variant VARIANT,
            col_array ARRAY,
            col_object OBJECT
            )
            """);

        assertEquals(17, engine.executeQuery("DESCRIBE TABLE test_types").getRowCount());
    }

    @Test
    public void testCreateTableAndInsertData() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");
        engine.execute("INSERT INTO users VALUES (2, 'Bob', 25)");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");

        assertEquals(2, result.getRowCount());
        assertEquals(3, result.getColumnCount());
    }

    @Test
    public void testCreateTableInDifferentSchema() {
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("CREATE TABLE other_schema.users (id INTEGER, name VARCHAR)");

        final ResultSet tables = engine.executeQuery("SHOW TABLES IN SCHEMA other_schema");
        final Row row = soleRowWhere(tables, "name", "USERS");
        assertEquals("OTHER_SCHEMA", cell(tables, row, "schema_name"));
    }

    @Test
    public void testCreateTableWithQualifiedName() {
        engine.execute("CREATE TABLE test_db.test_schema.products (id INTEGER, name VARCHAR)");

        soleRowWhere(engine.executeQuery("SHOW TABLES LIKE 'products'"), "name", "PRODUCTS");
    }

    @Test
    public void testCreateTableIfNotExists() {
        // Create table first time
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        // Create with IF NOT EXISTS should not throw
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER, email VARCHAR)");
            }
        }, "Creating table with IF NOT EXISTS should not fail");

        // Verify original table structure is preserved (the second column list was ignored).
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE users");
        assertEquals(2, described.getRowCount());
        assertEquals("NAME", cell(described, described.getRows().get(1), "name"));
    }
}
