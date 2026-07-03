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

package dev.frostlake.stream;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StreamShowInitialRowsTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(StreamShowInitialRowsTest.class);

    /**
     * Runs the given SHOW STREAMS variant and reports whether a stream with the given
     * (uppercased) name is listed. Stream names are normalized to upper case by the engine.
     */
    private boolean streamListed(final String showStreamsSql, final String upperName) throws SQLException {
        final ResultSet rs = statement.executeQuery(showStreamsSql);
        try {
            while (rs.next()) {
                final String name = rs.getString("name");
                if (upperName.equals(name)) {
                    return true;
                }
            }
            return false;
        } finally {
            rs.close();
        }
    }

    @Test
    public void testCreateStreamWithShowInitialRowsTrue() throws SQLException {
        logger.info("Testing CREATE STREAM with SHOW_INITIAL_ROWS = TRUE");

        statement.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");
        statement.execute("INSERT INTO products VALUES (1, 'Apple', 100)");
        statement.execute("INSERT INTO products VALUES (2, 'Banana', 50)");

        statement.execute("CREATE STREAM products_stream ON TABLE products SHOW_INITIAL_ROWS = TRUE");

        assertTrue(streamListed("SHOW STREAMS", "PRODUCTS_STREAM"),
                "Stream PRODUCTS_STREAM should be created");
        statement.execute("DROP STREAM products_stream");
    }

    @Test
    public void testCreateStreamWithShowInitialRowsFalse() throws SQLException {
        logger.info("Testing CREATE STREAM with SHOW_INITIAL_ROWS = FALSE");

        statement.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO customers VALUES (1, 'John')");

        statement.execute("CREATE STREAM customers_stream ON TABLE customers SHOW_INITIAL_ROWS = FALSE");

        assertTrue(streamListed("SHOW STREAMS", "CUSTOMERS_STREAM"),
                "Stream CUSTOMERS_STREAM should be created");
        statement.execute("DROP STREAM customers_stream");
    }

    @Test
    public void testCreateStreamWithAppendOnlyAndShowInitialRows() throws SQLException {
        logger.info("Testing CREATE STREAM with both APPEND_ONLY and SHOW_INITIAL_ROWS");

        statement.execute("CREATE TABLE orders (id INTEGER, amount INTEGER)");
        statement.execute("INSERT INTO orders VALUES (1, 500)");

        statement.execute("CREATE STREAM orders_stream ON TABLE orders APPEND_ONLY = TRUE SHOW_INITIAL_ROWS = TRUE");

        assertTrue(streamListed("SHOW STREAMS", "ORDERS_STREAM"),
                "Stream ORDERS_STREAM should be created");
        statement.execute("DROP STREAM orders_stream");
    }

    @Test
    public void testCreateStreamWithShowInitialRowsOnly() throws SQLException {
        logger.info("Testing CREATE STREAM with only SHOW_INITIAL_ROWS option");

        statement.execute("CREATE TABLE inventory (item VARCHAR, quantity INTEGER)");
        statement.execute("INSERT INTO inventory VALUES ('Widget', 100)");

        statement.execute("CREATE STREAM inventory_stream ON TABLE inventory SHOW_INITIAL_ROWS = TRUE");

        assertTrue(streamListed("SHOW STREAMS", "INVENTORY_STREAM"),
                "Stream INVENTORY_STREAM should be created");
        statement.execute("DROP STREAM inventory_stream");
    }

    @Test
    public void testCreateStreamWithBothOptionsReversedOrder() throws SQLException {
        logger.info("Testing CREATE STREAM with options in reversed order");

        statement.execute("CREATE TABLE events (id INTEGER, event_type VARCHAR)");
        statement.execute("INSERT INTO events VALUES (1, 'LOGIN')");

        statement.execute("CREATE STREAM events_stream ON TABLE events SHOW_INITIAL_ROWS = TRUE APPEND_ONLY = TRUE");

        assertTrue(streamListed("SHOW STREAMS", "EVENTS_STREAM"),
                "Stream EVENTS_STREAM should be created");
        statement.execute("DROP STREAM events_stream");
    }

    @Test
    public void testCreateStreamWithoutShowInitialRows() throws SQLException {
        logger.info("Testing CREATE STREAM without SHOW_INITIAL_ROWS (backward compatible)");

        statement.execute("CREATE TABLE logs (id INTEGER, message VARCHAR)");
        statement.execute("INSERT INTO logs VALUES (1, 'Test')");

        // Old syntax should still work
        statement.execute("CREATE STREAM logs_stream ON TABLE logs APPEND_ONLY = TRUE");

        assertTrue(streamListed("SHOW STREAMS", "LOGS_STREAM"),
                "Stream LOGS_STREAM should be created");
        statement.execute("DROP STREAM logs_stream");
    }

    @Test
    public void testCreateStreamDefaultNoOptions() throws SQLException {
        logger.info("Testing CREATE STREAM with no options");

        statement.execute("CREATE TABLE activity (id INTEGER, action VARCHAR)");
        statement.execute("INSERT INTO activity VALUES (1, 'CREATE')");

        // No options specified
        statement.execute("CREATE STREAM activity_stream ON TABLE activity");

        assertTrue(streamListed("SHOW STREAMS", "ACTIVITY_STREAM"),
                "Stream ACTIVITY_STREAM should be created");
        statement.execute("DROP STREAM activity_stream");
    }

    @Test
    public void testCreateStreamShowInitialRowsWithComment() throws SQLException {
        logger.info("Testing CREATE STREAM with SHOW_INITIAL_ROWS and COMMENT");

        statement.execute("CREATE TABLE transactions (id INTEGER, amount INTEGER)");
        statement.execute("INSERT INTO transactions VALUES (1, 250)");

        statement.execute("CREATE STREAM transactions_stream ON TABLE transactions SHOW_INITIAL_ROWS = TRUE COMMENT = 'Transaction change stream'");

        assertTrue(streamListed("SHOW STREAMS", "TRANSACTIONS_STREAM"),
                "Stream TRANSACTIONS_STREAM should be created");
        statement.execute("DROP STREAM transactions_stream");
    }

    @Test
    public void testCreateStreamIfNotExistsWithShowInitialRows() throws SQLException {
        logger.info("Testing CREATE STREAM IF NOT EXISTS with SHOW_INITIAL_ROWS");

        statement.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");
        statement.execute("INSERT INTO users VALUES (1, 'alice')");

        statement.execute("CREATE STREAM IF NOT EXISTS users_stream ON TABLE users SHOW_INITIAL_ROWS = TRUE");

        // Try to create again - should not fail
        statement.execute("CREATE STREAM IF NOT EXISTS users_stream ON TABLE users SHOW_INITIAL_ROWS = TRUE");

        // IF NOT EXISTS is idempotent: exactly one USERS_STREAM should exist.
        final ResultSet rs = statement.executeQuery("SHOW STREAMS");
        int count = 0;
        while (rs.next()) {
            if ("USERS_STREAM".equals(rs.getString("name"))) {
                count++;
            }
        }
        rs.close();
        assertEquals(1, count, "IF NOT EXISTS should leave exactly one USERS_STREAM");

        statement.execute("DROP STREAM users_stream");
    }

    @Test
    public void testCreateMultipleStreamsWithDifferentShowInitialRows() throws SQLException {
        logger.info("Testing multiple streams with different SHOW_INITIAL_ROWS values");

        statement.execute("CREATE TABLE sales (id INTEGER, product VARCHAR, amount INTEGER)");
        statement.execute("INSERT INTO sales VALUES (1, 'Widget', 100)");

        statement.execute("CREATE STREAM sales_stream_with_initial ON TABLE sales SHOW_INITIAL_ROWS = TRUE");
        statement.execute("CREATE STREAM sales_stream_without_initial ON TABLE sales SHOW_INITIAL_ROWS = FALSE");

        assertTrue(streamListed("SHOW STREAMS", "SALES_STREAM_WITH_INITIAL"),
                "Stream SALES_STREAM_WITH_INITIAL should be created");
        assertTrue(streamListed("SHOW STREAMS", "SALES_STREAM_WITHOUT_INITIAL"),
                "Stream SALES_STREAM_WITHOUT_INITIAL should be created");
        statement.execute("DROP STREAM sales_stream_with_initial");
        statement.execute("DROP STREAM sales_stream_without_initial");
    }

    @Test
    public void testStreamOptionsAllCombinations() throws SQLException {
        logger.info("Testing all combinations of APPEND_ONLY and SHOW_INITIAL_ROWS");

        statement.execute("CREATE TABLE test_table (id INTEGER, data VARCHAR)");
        statement.execute("INSERT INTO test_table VALUES (1, 'test')");

        // APPEND_ONLY = TRUE, SHOW_INITIAL_ROWS = TRUE
        statement.execute("CREATE STREAM stream1 ON TABLE test_table APPEND_ONLY = TRUE SHOW_INITIAL_ROWS = TRUE");

        // APPEND_ONLY = TRUE, SHOW_INITIAL_ROWS = FALSE
        statement.execute("CREATE STREAM stream2 ON TABLE test_table APPEND_ONLY = TRUE SHOW_INITIAL_ROWS = FALSE");

        // APPEND_ONLY = FALSE, SHOW_INITIAL_ROWS = TRUE
        statement.execute("CREATE STREAM stream3 ON TABLE test_table APPEND_ONLY = FALSE SHOW_INITIAL_ROWS = TRUE");

        // APPEND_ONLY = FALSE, SHOW_INITIAL_ROWS = FALSE
        statement.execute("CREATE STREAM stream4 ON TABLE test_table APPEND_ONLY = FALSE SHOW_INITIAL_ROWS = FALSE");

        assertTrue(streamListed("SHOW STREAMS", "STREAM1"), "STREAM1 should be created");
        assertTrue(streamListed("SHOW STREAMS", "STREAM2"), "STREAM2 should be created");
        assertTrue(streamListed("SHOW STREAMS", "STREAM3"), "STREAM3 should be created");
        assertTrue(streamListed("SHOW STREAMS", "STREAM4"), "STREAM4 should be created");
        statement.execute("DROP STREAM stream1");
        statement.execute("DROP STREAM stream2");
        statement.execute("DROP STREAM stream3");
        statement.execute("DROP STREAM stream4");
    }

    @Test
    public void testStreamWithSchemaQualifiedAndShowInitialRows() throws SQLException {
        logger.info("Testing schema-qualified stream with SHOW_INITIAL_ROWS");

        statement.execute("CREATE SCHEMA stream_schema");
        statement.execute("CREATE TABLE stream_schema.data_table (id INTEGER, value VARCHAR)");
        statement.execute("INSERT INTO stream_schema.data_table VALUES (1, 'data')");

        statement.execute("CREATE STREAM stream_schema.data_stream ON TABLE stream_schema.data_table SHOW_INITIAL_ROWS = TRUE");

        // The stream lives in stream_schema; SHOW STREAMS must be scoped to that schema
        // because the session's current schema is PUBLIC.
        assertTrue(streamListed("SHOW STREAMS IN SCHEMA stream_schema", "DATA_STREAM"),
                "Stream DATA_STREAM should be created in stream_schema");
        statement.execute("DROP STREAM stream_schema.data_stream");
    }
}
