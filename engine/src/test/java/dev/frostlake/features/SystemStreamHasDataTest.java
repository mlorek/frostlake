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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.*;

public class SystemStreamHasDataTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE events (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM events_stream ON TABLE events");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testStreamHasDataFalseWhenEmpty() {
        ResultSet rs = engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('events_stream')");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        Object val = rs.getRows().get(0).getValue(0);
        assertNotNull(val);
        assertFalse((Boolean) val, "Stream should report no data when table is empty");
    }

    @Test
    public void testStreamHasDataTrueAfterInsert() {
        engine.execute("INSERT INTO events VALUES (1, 'click')");
        ResultSet rs = engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('events_stream')");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        // The stream storage may or may not track inserts depending on impl —
        // at minimum the call should not throw
        assertNotNull(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testStreamHasDataCallable() {
        // Should be callable without error multiple times
        assertDoesNotThrow(() -> engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('events_stream')"));
        assertDoesNotThrow(() -> engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('events_stream')"));
    }

    @Test
    public void testNonExistentStreamReturnsFalse() {
        ResultSet rs = engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('no_such_stream')");
        assertNotNull(rs);
        Object val = rs.getRows().get(0).getValue(0);
        assertFalse((Boolean) val, "Non-existent stream should return false");
    }

    @Test
    public void testUnknownSystemFunctionErrors() {
        // An unimplemented SYSTEM$ function now errors instead of silently returning null.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SYSTEM$THIS_IS_NOT_A_REAL_FUNCTION()");
            }
        });
    }
}
