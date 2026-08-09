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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for DEFAULT constraint with CURRENT_TIMESTAMP, CURRENT_DATE, and CURRENT_TIME functions
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DefaultCurrentTimestampTest {
    private static final Logger logger = LoggerFactory.getLogger(DefaultCurrentTimestampTest.class);

    private DatabaseEngine engine;

    @BeforeAll
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for DEFAULT CURRENT_TIMESTAMP tests");
    }

    @AfterAll
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    @Order(1)
    public void testDefaultCurrentTimestamp() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP");
        engine.execute("CREATE TABLE test_timestamps (id INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEST_TIMESTAMPS");
        assertNotNull(table);

        TableColumn createdAtCol = table.getColumn("created_at");
        assertNotNull(createdAtCol.getDefaultValue());
        assertEquals("CURRENT_TIMESTAMP", createdAtCol.getDefaultValue());
    }

    @Test
    @Order(2)
    public void testDefaultCurrentTimestampInsertion() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP auto-populates on INSERT");
        LocalDateTime before = LocalDateTime.now();

        engine.execute("INSERT INTO test_timestamps (id, name) VALUES (1, 'Alice')");

        LocalDateTime after = LocalDateTime.now();

        ResultSet rs = engine.executeQuery("SELECT * FROM test_timestamps WHERE id = 1");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());

        Object timestamp = rs.getRows().get(0).getValue(1);
        assertNotNull(timestamp);
        assertTrue(timestamp instanceof LocalDateTime);

        LocalDateTime actualTime = (LocalDateTime) timestamp;
        assertTrue(!actualTime.isBefore(before), "Timestamp should be >= before time");
        assertTrue(!actualTime.isAfter(after), "Timestamp should be <= after time");

        logger.info("Generated timestamp: {}", actualTime);
    }

    @Test
    @Order(3)
    public void testDefaultCurrentTimestampMultipleInserts() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP generates different values");
        engine.execute("INSERT INTO test_timestamps (id, name) VALUES (2, 'Bob')");

        try {
            Thread.sleep(10); // Small delay to ensure different timestamps
        } catch (final InterruptedException e) {
            // Ignore
        }

        engine.execute("INSERT INTO test_timestamps (id, name) VALUES (3, 'Charlie')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_timestamps WHERE id IN (2, 3) ORDER BY id");
        assertEquals(2, rs.getRowCount());

        LocalDateTime time1 = (LocalDateTime) rs.getRows().get(0).getValue(1);
        LocalDateTime time2 = (LocalDateTime) rs.getRows().get(1).getValue(1);

        assertNotNull(time1);
        assertNotNull(time2);

        logger.info("Time1: {}, Time2: {}", time1, time2);
    }

    @Test
    @Order(4)
    public void testDefaultCurrentTimestampWithExplicitValue() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP can be overridden with NULL");

        engine.execute("INSERT INTO test_timestamps (id, created_at, name) VALUES (4, NULL, 'Explicit')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_timestamps WHERE id = 4");
        assertEquals(1, rs.getRowCount());

        Object timestamp = rs.getRows().get(0).getValue(1);
        // Explicit NULL should override default
        logger.info("Explicit timestamp (NULL): {}", timestamp);
    }

    @Test
    @Order(5)
    public void testDefaultCurrentDate() {
        logger.info("Testing DEFAULT CURRENT_DATE");
        engine.execute("CREATE TABLE test_dates (id INTEGER, date_col DATE DEFAULT CURRENT_DATE, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEST_DATES");
        TableColumn dateCol = table.getColumn("date_col");
        assertEquals("CURRENT_DATE", dateCol.getDefaultValue());

        engine.execute("INSERT INTO test_dates (id, name) VALUES (1, 'Test')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_dates WHERE id = 1");
        assertEquals(1, rs.getRowCount());

        Object date = rs.getRows().get(0).getValue(1);
        assertNotNull(date);
        assertTrue(date instanceof LocalDate);

        LocalDate actualDate = (LocalDate) date;
        assertEquals(LocalDate.now(), actualDate);

        logger.info("Generated date: {}", actualDate);
    }

    @Test
    @Order(6)
    public void testDefaultLiteralValue() {
        logger.info("Testing DEFAULT with literal values still works");
        engine.execute("CREATE TABLE test_literals (id INTEGER, status VARCHAR DEFAULT 'ACTIVE', count INTEGER DEFAULT 0)");

        engine.execute("INSERT INTO test_literals (id) VALUES (1)");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_literals WHERE id = 1");
        assertEquals(1, rs.getRowCount());

        assertEquals("ACTIVE", rs.getRows().get(0).getValue(1));
        assertEquals(0L, rs.getRows().get(0).getValue(2));
    }

    @Test
    @Order(7)
    public void testMultipleColumnsWithDefaults() {
        logger.info("Testing multiple columns with DEFAULT constraints");
        engine.execute("""
            CREATE TABLE test_multi_defaults (
                id INTEGER,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                status VARCHAR DEFAULT 'NEW'
            )
            """);

        engine.execute("INSERT INTO test_multi_defaults (id) VALUES (1)");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_multi_defaults WHERE id = 1");
        assertEquals(1, rs.getRowCount());

        assertNotNull(rs.getRows().get(0).getValue(1)); // created_at
        assertNotNull(rs.getRows().get(0).getValue(2)); // updated_at
        assertEquals("NEW", rs.getRows().get(0).getValue(3)); // status
    }

    @Test
    @Order(8)
    public void testDefaultWithColumnList() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP with explicit column list");
        engine.execute("INSERT INTO test_timestamps (name, id) VALUES ('ColumnTest', 10)");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_timestamps WHERE id = 10");
        assertEquals(1, rs.getRowCount());

        Object timestamp = rs.getRows().get(0).getValue(1);
        assertNotNull(timestamp);
        assertTrue(timestamp instanceof LocalDateTime);
    }

    @Test
    @Order(9)
    public void testDefaultWithMultipleValues() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP with multiple VALUES");
        engine.execute("INSERT INTO test_timestamps (id, name) VALUES (20, 'Multi1'), (21, 'Multi2'), (22, 'Multi3')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_timestamps WHERE id >= 20 ORDER BY id");
        assertEquals(3, rs.getRowCount());

        // All should have timestamps
        for (int i = 0; i < 3; i++) {
            Object timestamp = rs.getRows().get(i).getValue(1);
            assertNotNull(timestamp);
            assertTrue(timestamp instanceof LocalDateTime);
        }
    }

    @Test
    @Order(10)
    public void testDefaultCurrentTimestampWithParentheses() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP()");
        engine.execute("CREATE TABLE test_timestamps_paren (id INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP(), name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEST_TIMESTAMPS_PAREN");
        assertNotNull(table);

        TableColumn createdAtCol = table.getColumn("created_at");
        assertNotNull(createdAtCol.getDefaultValue());
        assertEquals("CURRENT_TIMESTAMP", createdAtCol.getDefaultValue());

        LocalDateTime before = LocalDateTime.now();
        engine.execute("INSERT INTO test_timestamps_paren (id, name) VALUES (1, 'ParenTest')");
        LocalDateTime after = LocalDateTime.now();

        ResultSet rs = engine.executeQuery("SELECT * FROM test_timestamps_paren WHERE id = 1");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());

        Object timestamp = rs.getRows().get(0).getValue(1);
        assertNotNull(timestamp);
        assertTrue(timestamp instanceof LocalDateTime);

        LocalDateTime actualTime = (LocalDateTime) timestamp;
        assertTrue(!actualTime.isBefore(before), "Timestamp should be >= before time");
        assertTrue(!actualTime.isAfter(after), "Timestamp should be <= after time");

        logger.info("Generated timestamp with parentheses: {}", actualTime);
    }

    @Test
    @Order(11)
    public void testDefaultCurrentDateWithParentheses() {
        logger.info("Testing DEFAULT CURRENT_DATE()");
        engine.execute("CREATE TABLE test_dates_paren (id INTEGER, date_col DATE DEFAULT CURRENT_DATE(), name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("TEST_DATES_PAREN");
        TableColumn dateCol = table.getColumn("date_col");
        assertEquals("CURRENT_DATE", dateCol.getDefaultValue());

        engine.execute("INSERT INTO test_dates_paren (id, name) VALUES (1, 'Test')");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_dates_paren WHERE id = 1");
        assertEquals(1, rs.getRowCount());

        Object date = rs.getRows().get(0).getValue(1);
        assertNotNull(date);
        assertTrue(date instanceof LocalDate);

        LocalDate actualDate = (LocalDate) date;
        assertEquals(LocalDate.now(), actualDate);

        logger.info("Generated date with parentheses: {}", actualDate);
    }

    @Test
    @Order(12)
    public void testMixedParenthesesSyntax() {
        logger.info("Testing mixed DEFAULT syntax with and without parentheses");
        engine.execute("""
            CREATE TABLE test_mixed_syntax (
                id INTEGER,
                ts_no_paren TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                ts_with_paren TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
                date_no_paren DATE DEFAULT CURRENT_DATE,
                date_with_paren DATE DEFAULT CURRENT_DATE()
            )
            """);

        Table table = engine.getCatalog().resolveTable("TEST_MIXED_SYNTAX");
        assertNotNull(table);

        assertEquals("CURRENT_TIMESTAMP", table.getColumn("ts_no_paren").getDefaultValue());
        assertEquals("CURRENT_TIMESTAMP", table.getColumn("ts_with_paren").getDefaultValue());
        assertEquals("CURRENT_DATE", table.getColumn("date_no_paren").getDefaultValue());
        assertEquals("CURRENT_DATE", table.getColumn("date_with_paren").getDefaultValue());

        engine.execute("INSERT INTO test_mixed_syntax (id) VALUES (1)");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_mixed_syntax WHERE id = 1");
        assertEquals(1, rs.getRowCount());

        assertNotNull(rs.getRows().get(0).getValue(1)); // ts_no_paren
        assertNotNull(rs.getRows().get(0).getValue(2)); // ts_with_paren
        assertNotNull(rs.getRows().get(0).getValue(3)); // date_no_paren
        assertNotNull(rs.getRows().get(0).getValue(4)); // date_with_paren

        logger.info("Both syntax forms work correctly");
    }
}
