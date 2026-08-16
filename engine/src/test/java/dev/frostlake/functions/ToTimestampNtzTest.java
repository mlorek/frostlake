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

package dev.frostlake.functions;

import dev.frostlake.BaseJdbcTest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ToTimestampNtzTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(ToTimestampNtzTest.class);

    @Test
    public void testToTimestampNtzFromIsoString() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ with ISO format string");

        final ResultSet rs = statement.executeQuery("SELECT TO_TIMESTAMP_NTZ('2024-01-15T10:30:45') AS ts");
        assertTrue(rs.next());

        final Object result = rs.getObject("ts");
        assertNotNull(result);
        logger.info("Result type: {}, value: {}", result.getClass().getName(), result);

        final LocalDateTime expected = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
        if (result instanceof Timestamp) {
            assertEquals(expected, ((Timestamp) result).toLocalDateTime());
        } else if (result instanceof LocalDateTime) {
            assertEquals(expected, result);
        } else {
            fail("Unexpected result type: " + result.getClass().getName());
        }

        rs.close();
    }

    @Test
    public void testToTimestampNtzFromSpaceSeparatedString() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ with space-separated format");

        final ResultSet rs = statement.executeQuery("SELECT TO_TIMESTAMP_NTZ('2024-01-15 10:30:45') AS ts");
        assertTrue(rs.next());

        final Object result = rs.getObject("ts");
        assertNotNull(result);

        final LocalDateTime expected = LocalDateTime.of(2024, 1, 15, 10, 30, 45);
        if (result instanceof Timestamp) {
            assertEquals(expected, ((Timestamp) result).toLocalDateTime());
        } else if (result instanceof LocalDateTime) {
            assertEquals(expected, result);
        }

        rs.close();
    }

    @Test
    public void testToTimestampNtzFromDateString() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ with date-only string");

        final ResultSet rs = statement.executeQuery("SELECT TO_TIMESTAMP_NTZ('2024-01-15') AS ts");
        assertTrue(rs.next());

        final Object result = rs.getObject("ts");
        assertNotNull(result);

        final LocalDateTime expected = LocalDateTime.of(2024, 1, 15, 0, 0, 0);
        if (result instanceof Timestamp) {
            assertEquals(expected, ((Timestamp) result).toLocalDateTime());
        } else if (result instanceof LocalDateTime) {
            assertEquals(expected, result);
        }

        rs.close();
    }

    @Test
    public void testToTimestampNtzFromEpochSeconds() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ with epoch seconds");

        // 1705315845 = 2024-01-15 10:30:45 UTC
        final ResultSet rs = statement.executeQuery("SELECT TO_TIMESTAMP_NTZ(1705315845) AS ts");
        assertTrue(rs.next());

        final Object result = rs.getObject("ts");
        assertNotNull(result);
        logger.info("Result: {}", result);

        rs.close();
    }

    @Test
    public void testToTimestampNtzWithNull() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ with NULL");

        final ResultSet rs = statement.executeQuery("SELECT TO_TIMESTAMP_NTZ(NULL) AS ts");
        assertTrue(rs.next());

        final Object result = rs.getObject("ts");
        assertNull(result);

        rs.close();
    }

    @Test
    public void testToTimestampNtzInWhereClause() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ in WHERE clause");

        statement.execute("CREATE TABLE events (id INTEGER, event_time VARCHAR)");
        statement.execute("INSERT INTO events VALUES (1, '2024-01-15 10:30:45')");
        statement.execute("INSERT INTO events VALUES (2, '2024-01-16 11:45:30')");
        statement.execute("INSERT INTO events VALUES (3, '2024-01-14 09:15:20')");

        final ResultSet rs = statement.executeQuery(
            "SELECT id FROM events WHERE TO_TIMESTAMP_NTZ(event_time) > TO_TIMESTAMP_NTZ('2024-01-15 00:00:00') ORDER BY id"
        );

        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));

        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));

        assertFalse(rs.next());
        rs.close();
    }

    @Test
    public void testToTimestampNtzInInsert() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ in INSERT statement");

        statement.execute("CREATE TABLE logs (id INTEGER, log_time TIMESTAMP_NTZ)");
        statement.execute("INSERT INTO logs VALUES (1, TO_TIMESTAMP_NTZ('2024-01-15 10:30:45'))");

        final ResultSet rs = statement.executeQuery("SELECT log_time FROM logs WHERE id = 1");
        assertTrue(rs.next());

        final Object result = rs.getObject("log_time");
        assertNotNull(result);

        rs.close();
    }

    @Test
    public void testToTimestampNtzWithColumnValue() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ with column values");

        statement.execute("CREATE TABLE raw_data (id INTEGER, ts_string VARCHAR)");
        statement.execute("INSERT INTO raw_data VALUES (1, '2024-01-15T10:30:45')");
        statement.execute("INSERT INTO raw_data VALUES (2, '2024-01-16 11:45:30')");

        final ResultSet rs = statement.executeQuery("SELECT id, TO_TIMESTAMP_NTZ(ts_string) AS parsed_ts FROM raw_data ORDER BY id");

        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertNotNull(rs.getObject("parsed_ts"));

        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));
        assertNotNull(rs.getObject("parsed_ts"));

        assertFalse(rs.next());
        rs.close();
    }

    @Test
    public void testToTimestampNtzComparison() throws SQLException {
        logger.info("Testing TO_TIMESTAMP_NTZ in comparison operations");

        final ResultSet rs = statement.executeQuery(
            "SELECT TO_TIMESTAMP_NTZ('2024-01-15 10:30:45') < TO_TIMESTAMP_NTZ('2024-01-16 10:30:45') AS result"
        );

        assertTrue(rs.next());
        assertTrue(rs.getBoolean("result"));

        rs.close();
    }

    @Test
    public void testToTimestampNtzInvalid() {
        logger.info("Testing TO_TIMESTAMP_NTZ with invalid input");

        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.executeQuery("SELECT TO_TIMESTAMP_NTZ('invalid-timestamp')");
                
            }
        });
    }
}
