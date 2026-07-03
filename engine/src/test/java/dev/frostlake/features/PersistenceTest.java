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
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for data persistence functionality
 */
public class PersistenceTest {

    private static final String TEST_DATA_DIR = "./test_data_persistence";
    private EngineConfig config;

    @BeforeEach
    public void setup() throws IOException {
        // Clean up any existing test data
        cleanupTestData();

        // Create config with persistence enabled
        config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
        config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, TEST_DATA_DIR);
        config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false"); // Disable auto-save for tests
    }

    @AfterEach
    public void teardown() throws IOException {
        cleanupTestData();
    }

    private void cleanupTestData() throws IOException {
        Path dataDir = Paths.get(TEST_DATA_DIR);
        if (Files.exists(dataDir)) {
            Files.walk(dataDir)
                .sorted(Comparator.reverseOrder())
                .forEach((final var path) -> {
                    try {
                        Files.delete(path);
                    } catch (final IOException e) {
                        // Ignore
                    }
                });
        }
    }

    @Test
    public void testBasicPersistence() {
        // Create engine and add some data
        DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine1.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine1.execute("INSERT INTO users VALUES (2, 'Bob')");
        engine1.shutdown();

        // Create new engine instance and verify data is loaded
        DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        ResultSet result = engine2.executeQuery("SELECT * FROM users ORDER BY id");

        assertEquals(2, result.getRowCount());
        assertTrue(result.next());
        assertEquals(1L, result.getValue("id"));
        assertEquals("Alice", result.getValue("name"));
        assertTrue(result.next());
        assertEquals(2L, result.getValue("id"));
        assertEquals("Bob", result.getValue("name"));

        engine2.shutdown();
    }

    @Test
    public void testPersistMultipleTables() {
        DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");

        // Create multiple tables
        engine1.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine1.execute("CREATE TABLE orders (order_id INTEGER, user_id INTEGER, amount FLOAT)");

        engine1.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");
        engine1.execute("INSERT INTO orders VALUES (100, 1, 50.5), (101, 2, 75.25)");

        engine1.shutdown();

        // Reload and verify both tables
        DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");

        ResultSet users = engine2.executeQuery("SELECT * FROM users ORDER BY id");
        assertEquals(2, users.getRowCount());

        ResultSet orders = engine2.executeQuery("SELECT * FROM orders ORDER BY order_id");
        assertEquals(2, orders.getRowCount());

        engine2.shutdown();
    }

    @Test
    public void testPersistViews() {
        DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine1.execute("INSERT INTO users VALUES (1, 'Alice', 30), (2, 'Bob', 25)");
        engine1.execute("CREATE VIEW adult_users AS SELECT * FROM users WHERE age >= 18");
        engine1.shutdown();

        // Reload and verify view exists and works
        DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");

        ResultSet views = engine2.executeQuery("SHOW VIEWS");
        boolean foundView = false;
        while (views.next()) {
            String viewName = (String) views.getValue("name");
            if ("ADULT_USERS".equalsIgnoreCase(viewName)) {
                foundView = true;
                break;
            }
        }
        assertTrue(foundView, "View ADULT_USERS should be persisted");

        ResultSet result = engine2.executeQuery("SELECT * FROM adult_users ORDER BY id");
        assertEquals(2, result.getRowCount());

        engine2.shutdown();
    }

    @Test
    public void testPersistWarehouses() {
        DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE WAREHOUSE test_wh WITH WAREHOUSE_SIZE = 'MEDIUM'");

        engine1.shutdown();

        // Reload and verify warehouse exists
        DatabaseEngine engine2 = new DatabaseEngine(config);
        ResultSet warehouses = engine2.executeQuery("SHOW WAREHOUSES");

        boolean foundTestWh = false;
        boolean foundComputeWh = false;
        while (warehouses.next()) {
            String whName = (String) warehouses.getValue("name");
            if ("TEST_WH".equalsIgnoreCase(whName)) {
                foundTestWh = true;
                assertEquals("MEDIUM", warehouses.getValue("size"));
            }
            if ("COMPUTE_WH".equalsIgnoreCase(whName)) {
                foundComputeWh = true;
            }
        }
        assertTrue(foundComputeWh, "Default COMPUTE_WH warehouse should exist");
        assertTrue(foundTestWh, "TEST_WH warehouse should be persisted");

        engine2.shutdown();
    }

    @Test
    public void testPersistUsersAndRoles() {
        DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE USER alice PASSWORD = 'pass123'");
        engine1.execute("CREATE ROLE analyst");
        engine1.execute("GRANT ROLE analyst TO USER alice");
        engine1.shutdown();

        // Reload and verify user and role exist
        DatabaseEngine engine2 = new DatabaseEngine(config);

        ResultSet users = engine2.executeQuery("SHOW USERS");
        boolean foundAlice = false;
        while (users.next()) {
            if ("ALICE".equals(users.getValue("name"))) {
                foundAlice = true;
                break;
            }
        }
        assertTrue(foundAlice, "User alice should be persisted");

        ResultSet roles = engine2.executeQuery("SHOW ROLES");
        boolean foundAnalyst = false;
        while (roles.next()) {
            if ("ANALYST".equals(roles.getValue("name"))) {
                foundAnalyst = true;
                break;
            }
        }
        assertTrue(foundAnalyst, "Role analyst should be persisted");

        engine2.shutdown();
    }

    @Test
    public void testPersistenceDisabled() {
        // Create config with persistence disabled
        EngineConfig disabledConfig = new EngineConfig();
        disabledConfig.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "false");

        DatabaseEngine engine1 = new DatabaseEngine(disabledConfig);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine1.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine1.shutdown();

        // Create new engine - should not have the data
        DatabaseEngine engine2 = new DatabaseEngine(disabledConfig);
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine2.execute("USE DATABASE test_db");
        });
        assertTrue(exception.getMessage().contains("Database does not exist"));

        engine2.shutdown();
    }

    @Test
    public void testPersistEmptyDatabase() {
        DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE empty_db");
        engine1.shutdown();

        // Reload and verify empty database exists
        DatabaseEngine engine2 = new DatabaseEngine(config);
        ResultSet databases = engine2.executeQuery("SHOW DATABASES");

        boolean foundEmptyDb = false;
        while (databases.next()) {
            if ("EMPTY_DB".equals(databases.getValue("name"))) {
                foundEmptyDb = true;
                break;
            }
        }
        assertTrue(foundEmptyDb, "Empty database should be persisted");

        engine2.shutdown();
    }

    @Test
    public void testPersistComplexDataTypes() {
        DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("""
            CREATE TABLE test_types (
            col_int INTEGER,
            col_bigint BIGINT,
            col_float FLOAT,
            col_double DOUBLE,
            col_varchar VARCHAR,
            col_boolean BOOLEAN)
            """);
        engine1.execute("INSERT INTO test_types VALUES (42, 9999999999, 3.14, 2.71828, 'test', true)");
        engine1.shutdown();

        // Reload and verify all data types
        DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        ResultSet result = engine2.executeQuery("SELECT * FROM test_types");

        assertTrue(result.next());
        assertEquals(42L, result.getValue("col_int"));
        assertEquals(9999999999L, result.getValue("col_bigint"));
        // FLOAT and DOUBLE are stored as BigDecimal
        assertEquals(3.14, ((Number) result.getValue("col_float")).doubleValue(), 0.01);
        assertEquals(2.71828, ((Number) result.getValue("col_double")).doubleValue(), 0.00001);
        assertEquals("test", result.getValue("col_varchar"));
        assertEquals(true, result.getValue("col_boolean"));

        engine2.shutdown();
    }

    @Test
    public void testUpdatePersistedData() {
        DatabaseEngine engine1 = new DatabaseEngine(config);
        engine1.execute("CREATE DATABASE test_db");
        engine1.execute("USE DATABASE test_db");
        engine1.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine1.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine1.shutdown();

        // Reload, modify data, and save again
        DatabaseEngine engine2 = new DatabaseEngine(config);
        engine2.execute("USE DATABASE test_db");
        engine2.execute("INSERT INTO users VALUES (2, 'Bob')");
        engine2.execute("UPDATE users SET name = 'Alicia' WHERE id = 1");
        engine2.shutdown();

        // Reload again and verify modifications
        DatabaseEngine engine3 = new DatabaseEngine(config);
        engine3.execute("USE DATABASE test_db");
        ResultSet result = engine3.executeQuery("SELECT * FROM users ORDER BY id");

        assertEquals(2, result.getRowCount());
        assertTrue(result.next());
        assertEquals("Alicia", result.getValue("name"));
        assertTrue(result.next());
        assertEquals("Bob", result.getValue("name"));

        engine3.shutdown();
    }
}
