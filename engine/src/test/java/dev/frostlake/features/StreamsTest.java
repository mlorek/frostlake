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
import dev.frostlake.metastore.*;
import dev.frostlake.metastore.model.ChangeType;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;
import dev.frostlake.metastore.model.StreamType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for STREAM feature
 */
public class StreamsTest extends BaseDatabaseTest {

    @Test
    public void testCreateStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("user_stream");

        assertNotNull(stream);
        assertEquals("USER_STREAM", stream.getName().toUpperCase());
        assertEquals("USERS", stream.getSourceTableName().toUpperCase());
        assertEquals(StreamType.STANDARD, stream.getStreamType());
    }

    @Test
    public void testCreateAppendOnlyStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users APPEND_ONLY = TRUE");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("user_stream");

        assertEquals(StreamType.APPEND_ONLY, stream.getStreamType());
    }

    @Test
    public void testStreamTracksInserts() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("user_stream");

        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        List<StreamRecord> records = stream.getUnconsumedRecords();
        assertEquals(2, records.size());
        assertEquals(ChangeType.INSERT, records.get(0).getChangeType());
    }

    @Test
    public void testStreamTracksUpdates() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice', 30)");

        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("UPDATE users SET age = 31 WHERE id = 1");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("user_stream");

        List<StreamRecord> records = stream.getUnconsumedRecords();
        assertEquals(2, records.size());
        assertEquals(ChangeType.DELETE, records.get(0).getChangeType());
        assertEquals(ChangeType.INSERT, records.get(1).getChangeType());
        assertTrue(records.get(0).isUpdate());
        assertTrue(records.get(1).isUpdate());
    }

    @Test
    public void testStreamTracksDeletes() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')");

        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("DELETE FROM users WHERE id = 1");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("user_stream");

        List<StreamRecord> records = stream.getUnconsumedRecords();
        assertEquals(1, records.size());
        assertEquals(ChangeType.DELETE, records.get(0).getChangeType());
    }

    @Test
    public void testAppendOnlyStreamOnlyTracksInserts() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users APPEND_ONLY = TRUE");

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");
        engine.execute("UPDATE users SET name = 'Alicia' WHERE id = 1");
        engine.execute("DELETE FROM users WHERE id = 1");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("user_stream");

        List<StreamRecord> records = stream.getUnconsumedRecords();
        assertEquals(1, records.size());
        assertEquals(ChangeType.INSERT, records.get(0).getChangeType());
    }

    @Test
    public void testStreamConsume() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");

        engine.execute("INSERT INTO users VALUES (1, 'Alice')");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("user_stream");

        assertEquals(1, stream.getUnconsumedCount());

        stream.consume();

        assertEquals(0, stream.getUnconsumedCount());
    }

    @Test
    public void testDropStream() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM user_stream ON TABLE users");
        engine.execute("DROP STREAM user_stream");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");

        assertThrows(RuntimeException.class, () -> {
            schema.getStream("user_stream");
        });
    }

    @Test
    public void testStreamWithMultipleOperations() {
        engine.execute("CREATE TABLE orders (id INTEGER, amount INTEGER, status VARCHAR)");
        engine.execute("CREATE STREAM order_stream ON TABLE orders");

        engine.execute("INSERT INTO orders VALUES (1, 100, 'pending'), (2, 200, 'completed')");

        Schema schema = engine.getCatalog().getDatabase("test_db").getSchema("test_schema");
        Stream stream = schema.getStream("order_stream");

        assertEquals(2, stream.getUnconsumedCount());
    }
}
