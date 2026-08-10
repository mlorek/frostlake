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

package dev.frostlake;

import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseEngineTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCreateDatabase() {
        final ExecutionResult result = engine.execute("CREATE DATABASE test_db");
        assertTrue(result.isSuccess(), "Database creation should succeed");

        final ResultSet databases = engine.showDatabases();
        assertTrue(databases.getRowCount() > 0, "Should have at least one database");
    }

    @Test
    public void testCreateSchema() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");

        final ResultSet schemas = engine.showSchemas();
        assertTrue(schemas.getRowCount() > 0, "Should have at least one schema");
    }

    @Test
    public void testCreateTable() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");

        engine.execute("""
            CREATE TABLE users (
            id INTEGER PRIMARY KEY,
            username VARCHAR,
            age INTEGER
            )
            """);

        final ResultSet tables = engine.showTables();
        assertEquals(1, tables.getRowCount(), "Should have one table");
    }

    @Test
    public void testInsertAndSelect() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");

        engine.execute("""
            CREATE TABLE users (
            id INTEGER,
            username VARCHAR,
            age INTEGER
            )
            """);

        engine.execute("INSERT INTO users VALUES (1, 'alice', 25)");
        engine.execute("INSERT INTO users VALUES (2, 'bob', 30)");
        engine.execute("INSERT INTO users VALUES (3, 'charlie', 35)");

        final ResultSet result = engine.executeQuery("SELECT * FROM users");
        assertEquals(3, result.getRowCount(), "Should have 3 rows");
        assertEquals(3, result.getColumnCount(), "Should have 3 columns");
    }

    @Test
    public void testCreateView() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");

        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");
        engine.execute("CREATE VIEW active_users AS SELECT * FROM users");

        final ResultSet views = engine.showViews();
        assertEquals(1, views.getRowCount(), "Should have one view");
    }

    @Test
    public void testTransaction() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");

        engine.setAutoCommit(false);
        engine.beginTransaction();

        engine.execute("INSERT INTO users VALUES (1, 'alice')");
        assertTrue(engine.getTransactionManager().hasActiveTransaction(),
            "Should have active transaction");

        engine.commit();
        assertFalse(engine.getTransactionManager().hasActiveTransaction(),
            "Transaction should be committed");
    }

    @Test
    public void testRollback() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");

        engine.setAutoCommit(false);
        engine.beginTransaction();

        engine.execute("INSERT INTO users VALUES (1, 'alice')");
        assertTrue(engine.getTransactionManager().hasActiveTransaction(),
            "Should have active transaction");

        engine.rollback();
        assertFalse(engine.getTransactionManager().hasActiveTransaction(),
            "Transaction should be rolled back");
    }

    @Test
    public void testDropTable() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");

        final ResultSet tablesBefore = engine.showTables();
        assertEquals(1, tablesBefore.getRowCount(), "Should have one table");

        engine.execute("DROP TABLE users");

        final ResultSet tablesAfter = engine.showTables();
        assertEquals(0, tablesAfter.getRowCount(), "Should have no tables");
    }

    @Test
    public void testMultipleDatabases() {
        engine.execute("CREATE DATABASE db1");
        engine.execute("CREATE DATABASE db2");

        final ResultSet databases = engine.showDatabases();
        assertTrue(databases.getRowCount() >= 2, "Should have at least 2 databases");
    }

    @Test
    public void testSystemViews() {
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        final ResultSet columns = engine.showColumns("users");
        assertEquals(2, columns.getRowCount(), "Should have 2 columns");
    }
}
