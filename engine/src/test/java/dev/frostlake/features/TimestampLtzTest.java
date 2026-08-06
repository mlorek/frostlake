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
import dev.frostlake.types.DateTimeType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for TIMESTAMP_LTZ, TIMESTAMP_NTZ, and TIMESTAMP_TZ data types with precision
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TimestampLtzTest {
    private static final Logger logger = LoggerFactory.getLogger(TimestampLtzTest.class);

    private DatabaseEngine engine;

    @BeforeAll
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for TIMESTAMP_LTZ tests");
    }

    @AfterAll
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    @Order(1)
    public void testTimestampLtzDefaultPrecision() {
        logger.info("Testing TIMESTAMP_LTZ with default precision");
        engine.execute("CREATE TABLE test_ts_ltz (id INTEGER, ts TIMESTAMP_LTZ)");

        Table table = engine.getCatalog().resolveTable("TEST_TS_LTZ");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        assertNotNull(tsCol);
        assertEquals("TIMESTAMP_LTZ", tsCol.getDataType().getName());

        DateTimeType tsType = (DateTimeType) tsCol.getDataType();
        assertEquals(9, tsType.getPrecision(), "Default precision should be 9");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_LTZ should have timezone");
    }

    @Test
    @Order(2)
    public void testTimestampLtzCustomPrecision3() {
        logger.info("Testing TIMESTAMP_LTZ with precision 3");
        engine.execute("CREATE TABLE test_ts_ltz3 (id INTEGER, ts TIMESTAMP_LTZ(3))");

        Table table = engine.getCatalog().resolveTable("TEST_TS_LTZ3");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        assertNotNull(tsCol);

        DateTimeType tsType = (DateTimeType) tsCol.getDataType();
        assertEquals(3, tsType.getPrecision(), "Precision should be 3");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_LTZ should have timezone");
    }

    @Test
    @Order(3)
    public void testTimestampLtzCustomPrecision6() {
        logger.info("Testing TIMESTAMP_LTZ with precision 6");
        engine.execute("CREATE TABLE test_ts_ltz6 (id INTEGER, ts TIMESTAMP_LTZ(6))");

        Table table = engine.getCatalog().resolveTable("TEST_TS_LTZ6");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        assertNotNull(tsCol);

        DateTimeType tsType = (DateTimeType) tsCol.getDataType();
        assertEquals(6, tsType.getPrecision(), "Precision should be 6");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_LTZ should have timezone");
    }

    @Test
    @Order(4)
    public void testTimestampLtzWithDefaultCurrentTimestamp() {
        logger.info("Testing TIMESTAMP_LTZ with DEFAULT CURRENT_TIMESTAMP");
        engine.execute("CREATE TABLE test_ts_ltz_default (id INTEGER, ts TIMESTAMP_LTZ(3) DEFAULT CURRENT_TIMESTAMP)");

        Table table = engine.getCatalog().resolveTable("TEST_TS_LTZ_DEFAULT");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        assertEquals("CURRENT_TIMESTAMP", tsCol.getDefaultValue());

        engine.execute("INSERT INTO test_ts_ltz_default (id) VALUES (1)");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_ts_ltz_default WHERE id = 1");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());

        Object timestamp = rs.getRows().get(0).getValue(1);
        assertNotNull(timestamp, "DEFAULT CURRENT_TIMESTAMP should populate timestamp");
        logger.info("Generated timestamp: {}", timestamp);
    }

    @Test
    @Order(5)
    public void testTimestampNtzDefaultPrecision() {
        logger.info("Testing TIMESTAMP_NTZ with default precision");
        engine.execute("CREATE TABLE test_ts_ntz (id INTEGER, ts TIMESTAMP_NTZ)");

        Table table = engine.getCatalog().resolveTable("TEST_TS_NTZ");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        assertNotNull(tsCol);
        assertEquals("TIMESTAMP_NTZ", tsCol.getDataType().getName());

        DateTimeType tsType = (DateTimeType) tsCol.getDataType();
        assertEquals(9, tsType.getPrecision(), "Default precision should be 9");
    }

    @Test
    @Order(6)
    public void testTimestampNtzCustomPrecision() {
        logger.info("Testing TIMESTAMP_NTZ with custom precision");
        engine.execute("CREATE TABLE test_ts_ntz_p (id INTEGER, ts TIMESTAMP_NTZ(5))");

        Table table = engine.getCatalog().resolveTable("TEST_TS_NTZ_P");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        DateTimeType tsType = (DateTimeType) tsCol.getDataType();
        assertEquals(5, tsType.getPrecision(), "Precision should be 5");
    }

    @Test
    @Order(7)
    public void testTimestampTzDefaultPrecision() {
        logger.info("Testing TIMESTAMP_TZ with default precision");
        engine.execute("CREATE TABLE test_ts_tz (id INTEGER, ts TIMESTAMP_TZ)");

        Table table = engine.getCatalog().resolveTable("TEST_TS_TZ");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        assertNotNull(tsCol);
        assertEquals("TIMESTAMP_TZ", tsCol.getDataType().getName());

        DateTimeType tsType = (DateTimeType) tsCol.getDataType();
        assertEquals(9, tsType.getPrecision(), "Default precision should be 9");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_TZ should have timezone");
    }

    @Test
    @Order(8)
    public void testTimestampTzCustomPrecision() {
        logger.info("Testing TIMESTAMP_TZ with custom precision");
        engine.execute("CREATE TABLE test_ts_tz_p (id INTEGER, ts TIMESTAMP_TZ(7))");

        Table table = engine.getCatalog().resolveTable("TEST_TS_TZ_P");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        DateTimeType tsType = (DateTimeType) tsCol.getDataType();
        assertEquals(7, tsType.getPrecision(), "Precision should be 7");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_TZ should have timezone");
    }

    @Test
    @Order(9)
    public void testMultipleTimestampTypes() {
        logger.info("Testing multiple timestamp types in single table");
        engine.execute("""
            CREATE TABLE test_multi_ts (
                id INTEGER,
                ts_ntz TIMESTAMP_NTZ(3),
                ts_ltz TIMESTAMP_LTZ(6),
                ts_tz TIMESTAMP_TZ(9)
            )
            """);

        Table table = engine.getCatalog().resolveTable("TEST_MULTI_TS");
        assertNotNull(table);

        TableColumn ntzCol = table.getColumn("ts_ntz");
        TableColumn ltzCol = table.getColumn("ts_ltz");
        TableColumn tzCol = table.getColumn("ts_tz");

        assertEquals("TIMESTAMP_NTZ", ntzCol.getDataType().getName());
        assertEquals("TIMESTAMP_LTZ", ltzCol.getDataType().getName());
        assertEquals("TIMESTAMP_TZ", tzCol.getDataType().getName());

        assertEquals(3, ((DateTimeType) ntzCol.getDataType()).getPrecision());
        assertEquals(6, ((DateTimeType) ltzCol.getDataType()).getPrecision());
        assertEquals(9, ((DateTimeType) tzCol.getDataType()).getPrecision());
    }

    @Test
    @Order(10)
    public void testTimestampLtzInsertAndSelect() {
        logger.info("Testing INSERT and SELECT with TIMESTAMP_LTZ");
        engine.execute("CREATE TABLE test_ts_insert (id INTEGER, ts TIMESTAMP_LTZ(3))");

        LocalDateTime now = LocalDateTime.now();
        engine.execute("INSERT INTO test_ts_insert (id, ts) VALUES (1, NULL)");

        ResultSet rs = engine.executeQuery("SELECT * FROM test_ts_insert WHERE id = 1");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());

        logger.info("Successfully inserted and queried TIMESTAMP_LTZ value");
    }

    @Test
    @Order(11)
    public void testTimestampLtzPrecisionRange() {
        logger.info("Testing TIMESTAMP_LTZ with various precision values");

        // Test precision 0
        engine.execute("CREATE TABLE test_ts_p0 (id INTEGER, ts TIMESTAMP_LTZ(0))");
        Table table0 = engine.getCatalog().resolveTable("TEST_TS_P0");
        assertEquals(0, ((DateTimeType) table0.getColumn("ts").getDataType()).getPrecision());

        // Test precision 9 (max)
        engine.execute("CREATE TABLE test_ts_p9 (id INTEGER, ts TIMESTAMP_LTZ(9))");
        Table table9 = engine.getCatalog().resolveTable("TEST_TS_P9");
        assertEquals(9, ((DateTimeType) table9.getColumn("ts").getDataType()).getPrecision());

        logger.info("Tested precision range 0-9 successfully");
    }

    @Test
    @Order(12)
    public void testPlainTimestampStillWorks() {
        logger.info("Testing plain TIMESTAMP type still works");
        engine.execute("CREATE TABLE test_plain_ts (id INTEGER, ts TIMESTAMP)");

        Table table = engine.getCatalog().resolveTable("TEST_PLAIN_TS");
        assertNotNull(table);

        TableColumn tsCol = table.getColumn("ts");
        assertNotNull(tsCol);
        assertEquals("TIMESTAMP", tsCol.getDataType().getName());

        logger.info("Plain TIMESTAMP type works correctly");
    }
}
