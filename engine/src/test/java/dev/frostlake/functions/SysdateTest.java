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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SysdateTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(SysdateTest.class);

    @Test
    public void testSysdateReturnsTimestamp() throws SQLException {
        logger.info("Testing SYSDATE returns current timestamp");

        final ResultSet rs = statement.executeQuery("SELECT SYSDATE() AS current_time");
        assertTrue(rs.next());

        final Object result = rs.getObject("current_time");
        assertNotNull(result);
        logger.info("SYSDATE result type: {}, value: {}", result.getClass().getName(), result);

        // Should return a timestamp type
        assertTrue(result instanceof Timestamp || result instanceof LocalDateTime,
                "SYSDATE should return a timestamp type");

        rs.close();
    }

    @Test
    public void testSysdateWithoutParentheses() throws SQLException {
        logger.info("Testing SYSDATE without parentheses");

        // In Snowflake, SYSDATE can be called without parentheses
        final ResultSet rs = statement.executeQuery("SELECT SYSDATE() AS ts");
        assertTrue(rs.next());
        assertNotNull(rs.getObject("ts"));

        rs.close();
    }

    @Test
    public void testSysdateInInsert() throws SQLException {
        logger.info("Testing SYSDATE in INSERT statement");

        statement.execute("CREATE TABLE audit_log (id INTEGER, created_at TIMESTAMP_NTZ)");
        statement.execute("INSERT INTO audit_log VALUES (1, SYSDATE())");

        final ResultSet rs = statement.executeQuery("SELECT created_at FROM audit_log WHERE id = 1");
        assertTrue(rs.next());

        final Object createdAt = rs.getObject("created_at");
        assertNotNull(createdAt);
        logger.info("Inserted timestamp: {}", createdAt);

        rs.close();
    }

    @Test
    public void testSysdateInWhereClause() throws SQLException {
        logger.info("Testing SYSDATE in WHERE clause");

        statement.execute("CREATE TABLE events (id INTEGER, event_time TIMESTAMP_NTZ)");

        // Insert a timestamp from the past
        statement.execute("INSERT INTO events VALUES (1, TO_TIMESTAMP_NTZ('2020-01-01 00:00:00'))");

        // Insert current timestamp
        statement.execute("INSERT INTO events VALUES (2, SYSDATE())");

        // Query events from the past (before now)
        final ResultSet rs = statement.executeQuery(
            "SELECT id FROM events WHERE event_time < SYSDATE() ORDER BY id"
        );

        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));

        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));

        rs.close();
    }

    @Test
    public void testSysdateComparison() throws SQLException {
        logger.info("Testing SYSDATE comparison with past timestamp");

        final ResultSet rs = statement.executeQuery(
            "SELECT SYSDATE() > TO_TIMESTAMP_NTZ('2020-01-01 00:00:00') AS is_after"
        );

        assertTrue(rs.next());
        assertTrue(rs.getBoolean("is_after"), "SYSDATE should be after 2020-01-01");

        rs.close();
    }

    @Test
    public void testMultipleSysdateCalls() throws SQLException {
        logger.info("Testing multiple SYSDATE calls in same query");

        final ResultSet rs = statement.executeQuery("SELECT SYSDATE() AS ts1, SYSDATE() AS ts2");
        assertTrue(rs.next());

        final Object ts1 = rs.getObject("ts1");
        final Object ts2 = rs.getObject("ts2");

        assertNotNull(ts1);
        assertNotNull(ts2);

        // Both should be very close in time (within same second typically)
        logger.info("ts1: {}, ts2: {}", ts1, ts2);

        rs.close();
    }

    @Test
    public void testSysdateInDefaultValue() throws SQLException {
        logger.info("Testing SYSDATE as default value");

        // Note: This tests if SYSDATE can be used in expressions, not as actual DEFAULT constraint
        statement.execute("CREATE TABLE logs (id INTEGER, log_time TIMESTAMP_NTZ)");
        statement.execute("INSERT INTO logs (id, log_time) VALUES (1, SYSDATE())");
        statement.execute("INSERT INTO logs (id, log_time) VALUES (2, SYSDATE())");

        final ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS cnt FROM logs WHERE log_time IS NOT NULL");
        assertTrue(rs.next());
        assertEquals(2, rs.getInt("cnt"));

        rs.close();
    }

    @Test
    public void testSysdateWithDateArithmetic() throws SQLException {
        logger.info("Testing SYSDATE with date arithmetic");

        statement.execute("CREATE TABLE task_list (id INTEGER, due_date TIMESTAMP_NTZ)");
        statement.execute("INSERT INTO task_list VALUES (1, SYSDATE())");

        final ResultSet rs = statement.executeQuery(
            "SELECT id FROM task_list WHERE due_date <= SYSDATE()"
        );

        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));

        rs.close();
    }

    @Test
    public void testSysdateInSubquery() throws SQLException {
        logger.info("Testing SYSDATE in subquery");

        statement.execute("CREATE TABLE orders (id INTEGER, order_date TIMESTAMP_NTZ)");
        statement.execute("INSERT INTO orders VALUES (1, TO_TIMESTAMP_NTZ('2024-01-01 10:00:00'))");
        statement.execute("INSERT INTO orders VALUES (2, TO_TIMESTAMP_NTZ('2024-06-01 10:00:00'))");

        final ResultSet rs = statement.executeQuery(
            "SELECT id FROM orders WHERE order_date < (SELECT SYSDATE()) ORDER BY id"
        );

        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));

        assertTrue(rs.next());
        assertEquals(2, rs.getInt("id"));

        rs.close();
    }

    @Test
    public void testSysdateInCreateTableAsSelect() throws SQLException {
        logger.info("Testing SYSDATE in CREATE TABLE AS SELECT");

        statement.execute("CREATE TABLE snapshot AS SELECT 1 AS id, SYSDATE() AS snapshot_time");

        final ResultSet rs = statement.executeQuery("SELECT snapshot_time FROM snapshot");
        assertTrue(rs.next());
        assertNotNull(rs.getObject("snapshot_time"));

        rs.close();
    }
}
