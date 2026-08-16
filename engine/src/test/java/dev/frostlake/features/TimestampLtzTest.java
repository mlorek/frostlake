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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.types.DateTimeType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TIMESTAMP_LTZ / TIMESTAMP_NTZ / TIMESTAMP_TZ types with precision, asserted through the SQL
 * surface where one exists — the {@code DESCRIBE TABLE} type cell names the subtype — while the
 * parsed PRECISION is asserted off the model, embedded only, because the type cell does not yet
 * spell type parameters.
 */
public class TimestampLtzTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(TimestampLtzTest.class);

    private static final String NO_TYPE_PARAM_SURFACE =
        "the DESCRIBE type cell does not yet spell type parameters, so the parsed precision is "
        + "asserted off the model, embedded only";

    /** Asserts one column's DESCRIBE type cell starts with the expected subtype spelling. */
    private void assertColumnTypeStartsWith(final String table, final String column, final String prefix) {
        final String type = describeCell(table, column, "type");
        assertTrue(type.startsWith(prefix),
            column + " should be a " + prefix + " but its type reads: " + type);
    }

    /** The parsed datetime type of one column, off the model — callers gate on embedded first. */
    private DateTimeType parsedType(final String table, final String column) {
        final TableColumn col = engine.getCatalog().resolveTable(table).getColumn(column);
        assertNotNull(col);
        return (DateTimeType) col.getDataType();
    }

    private long countOf(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void testTimestampLtzDefaultPrecision() {
        logger.info("Testing TIMESTAMP_LTZ with default precision");
        engine.execute("CREATE TABLE test_ts_ltz (id INTEGER, ts TIMESTAMP_LTZ)");

        assertColumnTypeStartsWith("test_ts_ltz", "TS", "TIMESTAMP_LTZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        final DateTimeType tsType = parsedType("TEST_TS_LTZ", "ts");
        assertEquals(9, tsType.getPrecision(), "Default precision should be 9");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_LTZ should have timezone");
    }

    @Test
    public void testTimestampLtzCustomPrecision3() {
        logger.info("Testing TIMESTAMP_LTZ with precision 3");
        engine.execute("CREATE TABLE test_ts_ltz3 (id INTEGER, ts TIMESTAMP_LTZ(3))");

        assertColumnTypeStartsWith("test_ts_ltz3", "TS", "TIMESTAMP_LTZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        final DateTimeType tsType = parsedType("TEST_TS_LTZ3", "ts");
        assertEquals(3, tsType.getPrecision(), "Precision should be 3");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_LTZ should have timezone");
    }

    @Test
    public void testTimestampLtzCustomPrecision6() {
        logger.info("Testing TIMESTAMP_LTZ with precision 6");
        engine.execute("CREATE TABLE test_ts_ltz6 (id INTEGER, ts TIMESTAMP_LTZ(6))");

        assertColumnTypeStartsWith("test_ts_ltz6", "TS", "TIMESTAMP_LTZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        final DateTimeType tsType = parsedType("TEST_TS_LTZ6", "ts");
        assertEquals(6, tsType.getPrecision(), "Precision should be 6");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_LTZ should have timezone");
    }

    @Test
    public void testTimestampLtzWithDefaultCurrentTimestamp() {
        logger.info("Testing TIMESTAMP_LTZ with DEFAULT CURRENT_TIMESTAMP");
        engine.execute("CREATE TABLE test_ts_ltz_default (id INTEGER, ts TIMESTAMP_LTZ DEFAULT CURRENT_TIMESTAMP)");

        final String defaultCell = describeCell("test_ts_ltz_default", "TS", "default");
        assertNotNull(defaultCell);
        assertTrue(defaultCell.toUpperCase().contains("CURRENT_TIMESTAMP"), defaultCell);

        engine.execute("INSERT INTO test_ts_ltz_default (id) VALUES (1)");

        assertEquals(1L, countOf(
            "SELECT COUNT(*) FROM test_ts_ltz_default WHERE id = 1 AND ts IS NOT NULL"));
    }

    @Test
    public void testNarrowedPrecisionRefusesTheCurrentTimestampDefault() {
        logger.info("Testing a declared precision below CURRENT_TIMESTAMP's is refused as a default");

        // CURRENT_TIMESTAMP carries the maximum fractional-second precision, so a column that
        // declares a smaller one cannot take it as a default (live-verified). The unparameterized
        // spelling and the full precision both work, as the tests above show.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE test_ts_narrow (id INTEGER, ts TIMESTAMP_LTZ(3) DEFAULT CURRENT_TIMESTAMP)");
            }
        });
        assertTrue(e.getMessage().contains(
            "Default value data type does not match data type for column TS"), e.getMessage());

        engine.execute("CREATE TABLE test_ts_full (id INTEGER, ts TIMESTAMP_LTZ(9) DEFAULT CURRENT_TIMESTAMP)");
        assertNotNull(describeCell("test_ts_full", "TS", "default"));
    }

    @Test
    public void testTimestampNtzDefaultPrecision() {
        logger.info("Testing TIMESTAMP_NTZ with default precision");
        engine.execute("CREATE TABLE test_ts_ntz (id INTEGER, ts TIMESTAMP_NTZ)");

        assertColumnTypeStartsWith("test_ts_ntz", "TS", "TIMESTAMP_NTZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        assertEquals(9, parsedType("TEST_TS_NTZ", "ts").getPrecision(), "Default precision should be 9");
    }

    @Test
    public void testTimestampNtzCustomPrecision() {
        logger.info("Testing TIMESTAMP_NTZ with custom precision");
        engine.execute("CREATE TABLE test_ts_ntz_p (id INTEGER, ts TIMESTAMP_NTZ(5))");

        assertColumnTypeStartsWith("test_ts_ntz_p", "TS", "TIMESTAMP_NTZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        assertEquals(5, parsedType("TEST_TS_NTZ_P", "ts").getPrecision(), "Precision should be 5");
    }

    @Test
    public void testTimestampTzDefaultPrecision() {
        logger.info("Testing TIMESTAMP_TZ with default precision");
        engine.execute("CREATE TABLE test_ts_tz (id INTEGER, ts TIMESTAMP_TZ)");

        assertColumnTypeStartsWith("test_ts_tz", "TS", "TIMESTAMP_TZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        final DateTimeType tsType = parsedType("TEST_TS_TZ", "ts");
        assertEquals(9, tsType.getPrecision(), "Default precision should be 9");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_TZ should have timezone");
    }

    @Test
    public void testTimestampTzCustomPrecision() {
        logger.info("Testing TIMESTAMP_TZ with custom precision");
        engine.execute("CREATE TABLE test_ts_tz_p (id INTEGER, ts TIMESTAMP_TZ(7))");

        assertColumnTypeStartsWith("test_ts_tz_p", "TS", "TIMESTAMP_TZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        final DateTimeType tsType = parsedType("TEST_TS_TZ_P", "ts");
        assertEquals(7, tsType.getPrecision(), "Precision should be 7");
        assertTrue(tsType.hasTimeZone(), "TIMESTAMP_TZ should have timezone");
    }

    @Test
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

        assertColumnTypeStartsWith("test_multi_ts", "TS_NTZ", "TIMESTAMP_NTZ");
        assertColumnTypeStartsWith("test_multi_ts", "TS_LTZ", "TIMESTAMP_LTZ");
        assertColumnTypeStartsWith("test_multi_ts", "TS_TZ", "TIMESTAMP_TZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        assertEquals(3, parsedType("TEST_MULTI_TS", "ts_ntz").getPrecision());
        assertEquals(6, parsedType("TEST_MULTI_TS", "ts_ltz").getPrecision());
        assertEquals(9, parsedType("TEST_MULTI_TS", "ts_tz").getPrecision());
    }

    @Test
    public void testTimestampLtzInsertAndSelect() {
        logger.info("Testing INSERT and SELECT with TIMESTAMP_LTZ");
        engine.execute("CREATE TABLE test_ts_insert (id INTEGER, ts TIMESTAMP_LTZ(3))");

        engine.execute("INSERT INTO test_ts_insert (id, ts) VALUES (1, NULL)");

        assertEquals(1, engine.executeQuery("SELECT * FROM test_ts_insert WHERE id = 1").getRowCount());

        logger.info("Successfully inserted and queried TIMESTAMP_LTZ value");
    }

    @Test
    public void testTimestampLtzPrecisionRange() {
        logger.info("Testing TIMESTAMP_LTZ with various precision values");

        engine.execute("CREATE TABLE test_ts_p0 (id INTEGER, ts TIMESTAMP_LTZ(0))");
        engine.execute("CREATE TABLE test_ts_p9 (id INTEGER, ts TIMESTAMP_LTZ(9))");

        assertColumnTypeStartsWith("test_ts_p0", "TS", "TIMESTAMP_LTZ");
        assertColumnTypeStartsWith("test_ts_p9", "TS", "TIMESTAMP_LTZ");

        Assumptions.assumeFalse(isLiveSnowflake(), NO_TYPE_PARAM_SURFACE);
        assertEquals(0, parsedType("TEST_TS_P0", "ts").getPrecision());
        assertEquals(9, parsedType("TEST_TS_P9", "ts").getPrecision());

        logger.info("Tested precision range 0-9 successfully");
    }

    @Test
    public void testPlainTimestampStillWorks() {
        logger.info("Testing plain TIMESTAMP type still works");
        engine.execute("CREATE TABLE test_plain_ts (id INTEGER, ts TIMESTAMP)");

        assertColumnTypeStartsWith("test_plain_ts", "TS", "TIMESTAMP");

        logger.info("Plain TIMESTAMP type works correctly");
    }
}
