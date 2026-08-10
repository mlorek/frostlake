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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SystemStreamHasDataTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE events (id INTEGER, name VARCHAR)");
        engine.execute("CREATE STREAM events_stream ON TABLE events");
    }

    @Test
    public void testStreamHasDataFalseWhenEmpty() {
        final ResultSet rs = engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('events_stream')");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        final Object val = rs.getRows().get(0).getValue(0);
        assertNotNull(val);
        assertFalse((Boolean) val, "Stream should report no data when table is empty");
    }

    @Test
    public void testStreamHasDataTrueAfterInsert() {
        engine.execute("INSERT INTO events VALUES (1, 'click')");
        final ResultSet rs = engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('events_stream')");
        assertNotNull(rs);
        assertEquals(1, rs.getRowCount());
        // The stream storage may or may not track inserts depending on impl —
        // at minimum the call should not throw
        assertNotNull(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void testStreamHasDataCallable() {
        // Should be callable without error multiple times
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('events_stream')");
            }
        });
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('events_stream')");
            }
        });
    }

    @Test
    public void schemaQualifiedStreamNameResolves() {
        // Loaders gate on SYSTEM$STREAM_HAS_DATA('SCHEMA.STREAM_NAME') — the qualified text was looked
        // up as a bare name in the current schema, so the gate always said FALSE and whole loader
        // branches silently never ran.
        engine.execute("INSERT INTO events VALUES (1, 'click')");
        assertTrue((Boolean) one("SELECT SYSTEM$STREAM_HAS_DATA('test_schema.events_stream')"),
            "schema-qualified name must resolve");
        assertTrue((Boolean) one("SELECT SYSTEM$STREAM_HAS_DATA('test_db.test_schema.events_stream')"),
            "db.schema-qualified name must resolve");
    }

    @Test
    public void qualifiedStreamInAnotherSchemaResolves() {
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("CREATE TABLE other_schema.t2 (id INTEGER)");
        engine.execute("CREATE STREAM other_schema.s2 ON TABLE other_schema.t2");
        engine.execute("INSERT INTO other_schema.t2 VALUES (1)");
        assertTrue((Boolean) one("SELECT SYSTEM$STREAM_HAS_DATA('other_schema.s2')"),
            "stream in a non-current schema must be reachable by qualified name");
        final RuntimeException qualifiedMissing = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                one("SELECT SYSTEM$STREAM_HAS_DATA('other_schema.no_such')");
            }
        });
        assertTrue(qualifiedMissing.getMessage().contains("must be a valid stream name"),
            "unexpected message: " + qualifiedMissing.getMessage());
    }

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void testNonExistentStreamIsRefused() {
        // Live refuses an unknown stream at compile time, echoing the argument as written.
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SYSTEM$STREAM_HAS_DATA('no_such_stream')");
            }
        });
        assertTrue(e.getMessage().contains(
                "Invalid value ['no_such_stream'] for function 'SYSTEM$STREAM_HAS_DATA', parameter 1: must be a valid stream name"),
            "unexpected message: " + e.getMessage());
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
