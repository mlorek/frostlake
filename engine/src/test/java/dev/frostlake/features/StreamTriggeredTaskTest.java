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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests stream-triggered tasks — {@code CREATE TASK … WHEN SYSTEM$STREAM_HAS_DATA('s') AS …}, the canonical
 * Snowflake pattern for incremental processing. The task body runs only when the named stream has unconsumed
 * data; otherwise the WHEN condition is false and the run is skipped. {@code EXECUTE TASK} triggers a run
 * deterministically (no scheduler timing), and the WHEN condition is re-evaluated on every execution.
 */
public class StreamTriggeredTaskTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("CREATE TABLE src (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM src_stream ON TABLE src");
        engine.execute("CREATE TABLE tgt (id INTEGER, name VARCHAR)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private long count(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void taskSkippedWhenStreamEmpty() {
        engine.execute("""
            CREATE TASK load_tgt
            WAREHOUSE = 'compute_wh'
            WHEN SYSTEM$STREAM_HAS_DATA('src_stream')
            AS INSERT INTO tgt SELECT id, name FROM src
            """);

        // Stream has no unconsumed data → WHEN is false → the body must not run.
        engine.execute("EXECUTE TASK load_tgt");

        assertEquals(0, count("tgt"));
    }

    @Test
    public void taskRunsWhenStreamHasData() {
        engine.execute("""
            CREATE TASK load_tgt
            WAREHOUSE = 'compute_wh'
            WHEN SYSTEM$STREAM_HAS_DATA('src_stream')
            AS INSERT INTO tgt SELECT id, name FROM src
            """);

        engine.execute("INSERT INTO src VALUES (1, 'a'), (2, 'b')");   // stream now has data

        engine.execute("EXECUTE TASK load_tgt");

        assertEquals(2, count("tgt"));
    }

    @Test
    public void whenConditionReevaluatedEachExecution() {
        engine.execute("""
            CREATE TASK load_tgt
            WAREHOUSE = 'compute_wh'
            WHEN SYSTEM$STREAM_HAS_DATA('src_stream')
            AS INSERT INTO tgt SELECT id, name FROM src
            """);

        // First execution with an empty stream → skipped.
        engine.execute("EXECUTE TASK load_tgt");
        assertEquals(0, count("tgt"));

        // After data arrives, the same task re-evaluates WHEN as true and runs.
        engine.execute("INSERT INTO src VALUES (1, 'a')");
        engine.execute("EXECUTE TASK load_tgt");
        assertEquals(1, count("tgt"));
    }

    @Test
    public void streamConsumingTaskBodyLoadsChangedRows() {
        // The canonical idiom: the body consumes the stream itself.
        engine.execute("""
            CREATE TASK load_tgt
            WAREHOUSE = 'compute_wh'
            WHEN SYSTEM$STREAM_HAS_DATA('src_stream')
            AS INSERT INTO tgt SELECT id, name FROM src_stream
            """);

        engine.execute("INSERT INTO src VALUES (1, 'a'), (2, 'b')");

        engine.execute("EXECUTE TASK load_tgt");

        assertEquals(2, count("tgt"));
    }
}
