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
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for IDENTITY column functionality (auto-increment with custom start/increment)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IdentityTest {
    private static final Logger logger = LoggerFactory.getLogger(IdentityTest.class);

    private DatabaseEngine engine;

    @BeforeAll
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for IDENTITY tests");
    }

    @AfterAll
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    @Order(1)
    public void testSimpleIdentity() {
        logger.info("Testing simple IDENTITY");
        engine.execute("CREATE TABLE test_identity (id INTEGER IDENTITY, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test_identity");
        assertNotNull(table);

        TableColumn idCol = table.getColumn("id");
        assertTrue(idCol.isAutoIncrement());
        assertEquals(1, idCol.getIdentityStart());
        assertEquals(1, idCol.getIdentityIncrement());
    }

    @Test
    @Order(2)
    public void testIdentityWithStartAndIncrement() {
        logger.info("Testing IDENTITY with custom start and increment");
        engine.execute("CREATE TABLE test_identity_custom (id INTEGER IDENTITY(100, 5), name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test_identity_custom");
        assertNotNull(table);

        TableColumn idCol = table.getColumn("id");
        assertTrue(idCol.isAutoIncrement());
        assertEquals(100, idCol.getIdentityStart());
        assertEquals(5, idCol.getIdentityIncrement());
    }

    @Test
    @Order(3)
    public void testIdentityAutoInsert() {
        logger.info("Testing IDENTITY auto-generates values on INSERT");
        engine.execute("INSERT INTO test_identity (name) VALUES ('Alice')");
        engine.execute("INSERT INTO test_identity (name) VALUES ('Bob')");
        engine.execute("INSERT INTO test_identity (name) VALUES ('Charlie')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_identity ORDER BY id");
        assertNotNull(rs);
        assertEquals(3, rs.getRowCount());

        // Check that IDs are 1, 2, 3
        assertEquals(1L, rs.getRows().get(0).getValue(0));
        assertEquals("Alice", rs.getRows().get(0).getValue(1));

        assertEquals(2L, rs.getRows().get(1).getValue(0));
        assertEquals("Bob", rs.getRows().get(1).getValue(1));

        assertEquals(3L, rs.getRows().get(2).getValue(0));
        assertEquals("Charlie", rs.getRows().get(2).getValue(1));
    }

    @Test
    @Order(4)
    public void testIdentityCustomAutoInsert() {
        logger.info("Testing IDENTITY with custom start/increment auto-generates values");
        engine.execute("INSERT INTO test_identity_custom (name) VALUES ('Alice')");
        engine.execute("INSERT INTO test_identity_custom (name) VALUES ('Bob')");
        engine.execute("INSERT INTO test_identity_custom (name) VALUES ('Charlie')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_custom ORDER BY id");
        assertNotNull(rs);
        assertEquals(3, rs.getRowCount());

        // Check that IDs are 100, 105, 110
        assertEquals(100L, rs.getRows().get(0).getValue(0));
        assertEquals("Alice", rs.getRows().get(0).getValue(1));

        assertEquals(105L, rs.getRows().get(1).getValue(0));
        assertEquals("Bob", rs.getRows().get(1).getValue(1));

        assertEquals(110L, rs.getRows().get(2).getValue(0));
        assertEquals("Charlie", rs.getRows().get(2).getValue(1));
    }

    @Test
    @Order(5)
    public void testIdentityWithExplicitValue() {
        logger.info("Testing IDENTITY with explicit value insertion");
        engine.execute("CREATE TABLE test_identity_explicit (id INTEGER IDENTITY, name VARCHAR)");
        engine.execute("INSERT INTO test_identity_explicit (id, name) VALUES (50, 'Explicit')");
        engine.execute("INSERT INTO test_identity_explicit (name) VALUES ('Auto')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_explicit ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        // ORDER BY id sorts 1 before 50
        // Auto-increment should continue from 1 (not affected by explicit value)
        assertEquals(1L, rs.getRows().get(0).getValue(0));
        assertEquals("Auto", rs.getRows().get(0).getValue(1));

        assertEquals(50L, rs.getRows().get(1).getValue(0));
        assertEquals("Explicit", rs.getRows().get(1).getValue(1));
    }

    @Test
    @Order(6)
    public void testIdentityLargeIncrement() {
        logger.info("Testing IDENTITY with large increment");
        engine.execute("CREATE TABLE test_identity_large (id INTEGER IDENTITY(1000, 1000), name VARCHAR)");

        engine.execute("INSERT INTO test_identity_large (name) VALUES ('A')");
        engine.execute("INSERT INTO test_identity_large (name) VALUES ('B')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_large ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        assertEquals(1000L, rs.getRows().get(0).getValue(0));
        assertEquals(2000L, rs.getRows().get(1).getValue(0));
    }

    @Test
    @Order(7)
    public void testAutoIncrementBackwardCompatibility() {
        logger.info("Testing AUTOINCREMENT still works (backward compatibility)");
        engine.execute("CREATE TABLE test_autoincrement (id INTEGER AUTOINCREMENT, name VARCHAR)");

        engine.execute("INSERT INTO test_autoincrement (name) VALUES ('Test1')");
        engine.execute("INSERT INTO test_autoincrement (name) VALUES ('Test2')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_autoincrement ORDER BY id");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        assertEquals(1L, rs.getRows().get(0).getValue(0));
        assertEquals(2L, rs.getRows().get(1).getValue(0));
    }

    @Test
    @Order(8)
    public void testIdentityWithMultipleInserts() {
        logger.info("Testing IDENTITY with multiple VALUES in single INSERT");
        engine.execute("CREATE TABLE test_identity_multi (id INTEGER IDENTITY(10, 2), name VARCHAR)");

        engine.execute("INSERT INTO test_identity_multi (name) VALUES ('A'), ('B'), ('C')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_multi ORDER BY id");
        assertNotNull(rs);
        assertEquals(3, rs.getRowCount());

        assertEquals(10L, rs.getRows().get(0).getValue(0));
        assertEquals(12L, rs.getRows().get(1).getValue(0));
        assertEquals(14L, rs.getRows().get(2).getValue(0));
    }

    @Test
    @Order(9)
    public void testIdentityPersistsAcrossQueries() {
        logger.info("Testing IDENTITY counter persists across queries");
        engine.execute("CREATE TABLE test_identity_persist (id INTEGER IDENTITY(1, 1), value INTEGER)");

        engine.execute("INSERT INTO test_identity_persist (value) VALUES (100)");
        engine.execute("INSERT INTO test_identity_persist (value) VALUES (200)");

        // Execute different query in between
        engine.executeQuery("SELECT * FROM test_identity");

        // Continue inserting
        engine.execute("INSERT INTO test_identity_persist (value) VALUES (300)");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_identity_persist ORDER BY id");
        assertEquals(3, rs.getRowCount());

        assertEquals(1L, rs.getRows().get(0).getValue(0));
        assertEquals(2L, rs.getRows().get(1).getValue(0));
        assertEquals(3L, rs.getRows().get(2).getValue(0));
    }
}
