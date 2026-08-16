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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DEFAULT constraint with CURRENT_TIMESTAMP / CURRENT_DATE (with and without parentheses),
 * asserted through the SQL surface — the {@code DESCRIBE TABLE} default cell plus the populated
 * values themselves, read back with server-side predicates ({@code IS NOT NULL},
 * {@code = CURRENT_DATE}) so the clock and time zone are the executing engine's own — so every
 * check runs against whichever engine executed the DDL/DML, embedded or live.
 */
public class DefaultCurrentTimestampTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(DefaultCurrentTimestampTest.class);

    private long countOf(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    /** Asserts the column's DESCRIBE default cell carries this fragment of the declared expression. */
    private void assertDefaultContains(final String table, final String column, final String fragment) {
        final String cellText = describeCell(table, column, "default");
        assertNotNull(cellText, column + " should carry a default");
        assertTrue(cellText.toUpperCase().contains(fragment.toUpperCase()),
            column + "'s default cell reads: " + cellText);
    }

    private void createTimestampsTable() {
        engine.execute("CREATE TABLE test_timestamps (id INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, name VARCHAR)");
    }

    @Test
    public void testDefaultCurrentTimestamp() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP");
        createTimestampsTable();

        assertDefaultContains("test_timestamps", "CREATED_AT", "CURRENT_TIMESTAMP");
    }

    @Test
    public void testDefaultCurrentTimestampInsertion() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP auto-populates on INSERT");
        createTimestampsTable();

        engine.execute("INSERT INTO test_timestamps (id, name) VALUES (1, 'Alice')");

        assertEquals(1L, countOf(
            "SELECT COUNT(*) FROM test_timestamps WHERE id = 1 AND created_at IS NOT NULL"));
    }

    @Test
    public void testDefaultCurrentTimestampMultipleInserts() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP populates every inserted row");
        createTimestampsTable();

        engine.execute("INSERT INTO test_timestamps (id, name) VALUES (2, 'Bob')");
        engine.execute("INSERT INTO test_timestamps (id, name) VALUES (3, 'Charlie')");

        assertEquals(2L, countOf(
            "SELECT COUNT(*) FROM test_timestamps WHERE id IN (2, 3) AND created_at IS NOT NULL"));
    }

    @Test
    public void testDefaultCurrentTimestampWithExplicitValue() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP can be overridden with NULL");
        createTimestampsTable();

        engine.execute("INSERT INTO test_timestamps (id, created_at, name) VALUES (4, NULL, 'Explicit')");

        // Explicit NULL overrides the default.
        assertEquals(1L, countOf(
            "SELECT COUNT(*) FROM test_timestamps WHERE id = 4 AND created_at IS NULL"));
    }

    @Test
    public void testDefaultCurrentDate() {
        logger.info("Testing DEFAULT CURRENT_DATE");
        engine.execute("CREATE TABLE test_dates (id INTEGER, date_col DATE DEFAULT CURRENT_DATE, name VARCHAR)");

        assertDefaultContains("test_dates", "DATE_COL", "CURRENT_DATE");

        engine.execute("INSERT INTO test_dates (id, name) VALUES (1, 'Test')");

        // Compared server-side, so the clock and time zone are the executing engine's own.
        assertEquals(1L, countOf(
            "SELECT COUNT(*) FROM test_dates WHERE id = 1 AND date_col = CURRENT_DATE"));
    }

    @Test
    public void testDefaultLiteralValue() {
        logger.info("Testing DEFAULT with literal values still works");
        engine.execute("CREATE TABLE test_literals (id INTEGER, status VARCHAR DEFAULT 'ACTIVE', count INTEGER DEFAULT 0)");

        engine.execute("INSERT INTO test_literals (id) VALUES (1)");

        final ResultSet rs = engine.executeQuery("SELECT * FROM test_literals WHERE id = 1");
        assertEquals(1, rs.getRowCount());
        final Row row = rs.getRows().get(0);
        assertEquals("ACTIVE", cell(rs, row, "STATUS"));
        assertEquals("0", cell(rs, row, "COUNT"));
    }

    @Test
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

        assertEquals(1L, countOf("""
            SELECT COUNT(*) FROM test_multi_defaults
            WHERE id = 1 AND created_at IS NOT NULL AND updated_at IS NOT NULL AND status = 'NEW'
            """));
    }

    @Test
    public void testDefaultWithColumnList() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP with explicit column list");
        createTimestampsTable();

        engine.execute("INSERT INTO test_timestamps (name, id) VALUES ('ColumnTest', 10)");

        assertEquals(1L, countOf(
            "SELECT COUNT(*) FROM test_timestamps WHERE id = 10 AND created_at IS NOT NULL"));
    }

    @Test
    public void testDefaultWithMultipleValues() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP with multiple VALUES");
        createTimestampsTable();

        engine.execute("INSERT INTO test_timestamps (id, name) VALUES (20, 'Multi1'), (21, 'Multi2'), (22, 'Multi3')");

        assertEquals(3L, countOf(
            "SELECT COUNT(*) FROM test_timestamps WHERE id >= 20 AND created_at IS NOT NULL"));
    }

    @Test
    public void testDefaultCurrentTimestampWithParentheses() {
        logger.info("Testing DEFAULT CURRENT_TIMESTAMP()");
        engine.execute("CREATE TABLE test_timestamps_paren (id INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP(), name VARCHAR)");

        assertDefaultContains("test_timestamps_paren", "CREATED_AT", "CURRENT_TIMESTAMP");

        engine.execute("INSERT INTO test_timestamps_paren (id, name) VALUES (1, 'ParenTest')");

        assertEquals(1L, countOf(
            "SELECT COUNT(*) FROM test_timestamps_paren WHERE id = 1 AND created_at IS NOT NULL"));
    }

    @Test
    public void testDefaultCurrentDateWithParentheses() {
        logger.info("Testing DEFAULT CURRENT_DATE()");
        engine.execute("CREATE TABLE test_dates_paren (id INTEGER, date_col DATE DEFAULT CURRENT_DATE(), name VARCHAR)");

        assertDefaultContains("test_dates_paren", "DATE_COL", "CURRENT_DATE");

        engine.execute("INSERT INTO test_dates_paren (id, name) VALUES (1, 'Test')");

        assertEquals(1L, countOf(
            "SELECT COUNT(*) FROM test_dates_paren WHERE id = 1 AND date_col = CURRENT_DATE"));
    }

    @Test
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

        assertDefaultContains("test_mixed_syntax", "TS_NO_PAREN", "CURRENT_TIMESTAMP");
        assertDefaultContains("test_mixed_syntax", "TS_WITH_PAREN", "CURRENT_TIMESTAMP");
        assertDefaultContains("test_mixed_syntax", "DATE_NO_PAREN", "CURRENT_DATE");
        assertDefaultContains("test_mixed_syntax", "DATE_WITH_PAREN", "CURRENT_DATE");

        engine.execute("INSERT INTO test_mixed_syntax (id) VALUES (1)");

        assertEquals(1L, countOf("""
            SELECT COUNT(*) FROM test_mixed_syntax
            WHERE id = 1 AND ts_no_paren IS NOT NULL AND ts_with_paren IS NOT NULL
              AND date_no_paren IS NOT NULL AND date_with_paren IS NOT NULL
            """));

        logger.info("Both syntax forms work correctly");
    }
}
