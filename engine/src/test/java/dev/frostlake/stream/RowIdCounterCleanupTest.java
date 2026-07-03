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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.metastore.model.StreamRecord;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Verifies that {@link StreamManager}'s per-table change-tracking row-id counters are forgotten when a
 * table (or its schema / database) is dropped, so {@code rowIdCounters} cannot grow without bound as
 * objects are created and dropped. The cleanup is observable: a counter that is removed restarts at 1,
 * so a table recreated after a drop tracks from 1 again instead of inheriting the stale counter.
 */
public class RowIdCounterCleanupTest extends BaseDatabaseTest {

    private static final Logger log = LoggerFactory.getLogger(RowIdCounterCleanupTest.class);

    /** End-to-end: a real DROP TABLE must clear the counter, so a recreated table restarts at row-id 1. */
    @Test
    public void dropTableResetsRowIdCounter() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("INSERT INTO t VALUES (2)");
        assertEquals(2L, newestRowId("S"), "counter should advance to 2 over two inserts");

        engine.execute("DROP TABLE t");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s2 ON TABLE t");
        engine.execute("INSERT INTO t VALUES (3)");
        assertEquals(1L, newestRowId("S2"),
            "a table recreated after DROP must restart row-id tracking at 1, not inherit the old counter");
    }

    /** The schema-level (prefix) cleanup removes the counter for tables in that schema. */
    @Test
    public void onSchemaDroppedResetsRowIdCounter() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("INSERT INTO t VALUES (2)");
        assertEquals(2L, newestRowId("S"), "counter should advance to 2 over two inserts");

        engine.getStreamManager().onSchemaDropped("TEST_DB", "TEST_SCHEMA");

        engine.execute("INSERT INTO t VALUES (3)");
        assertEquals(1L, newestRowId("S"), "schema-prefix cleanup should have reset the counter");
    }

    /** The database-level (prefix) cleanup removes the counter for tables in that database. */
    @Test
    public void onDatabaseDroppedResetsRowIdCounter() {
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE STREAM s ON TABLE t");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("INSERT INTO t VALUES (2)");
        assertEquals(2L, newestRowId("S"), "counter should advance to 2 over two inserts");

        engine.getStreamManager().onDatabaseDropped("TEST_DB");

        engine.execute("INSERT INTO t VALUES (3)");
        assertEquals(1L, newestRowId("S"), "database-prefix cleanup should have reset the counter");
    }

    /** Row id of the most recently tracked change on the named stream (read straight from the model). */
    private long newestRowId(final String streamName) {
        final List<Stream> streams = engine.getCatalog()
            .getDatabase("TEST_DB").getSchema("TEST_SCHEMA").getStreams();
        for (final Stream stream : streams) {
            if (stream.getName().equalsIgnoreCase(streamName)) {
                final List<StreamRecord> records = stream.getRecords();
                if (records.isEmpty()) {
                    throw new IllegalStateException("stream has no records: " + streamName);
                }
                final long rowId = records.get(records.size() - 1).getRowId();
                log.debug("stream {} newest rowId = {}", streamName, rowId);
                return rowId;
            }
        }
        throw new IllegalStateException("stream not found: " + streamName);
    }
}
