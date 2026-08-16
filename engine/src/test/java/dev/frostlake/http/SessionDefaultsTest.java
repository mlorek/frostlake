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

package dev.frostlake.http;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code database.default} / {@code schema.default} point every fresh session — the engine's own
 * bootstrap session and each HTTP session — at a configured (and writable) landing database,
 * created on first boot. Unset, the built-in read-only SNOWFLAKE.PUBLIC stays the default.
 */
public class SessionDefaultsTest {

    private EngineConfig configWith(final String database, final String schema) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_DATABASE_DEFAULT, database);
        config.setProperty(EngineConfig.PROP_SCHEMA_DEFAULT, schema);
        return config;
    }

    @Test
    public void engineBootstrapsIntoTheConfiguredDefaults() {
        final DatabaseEngine engine = new DatabaseEngine(configWith("boot_db", "app"));
        try {
            final ResultSet rs = engine.executeQuery(
                "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
            assertEquals("BOOT_DB", rs.getRows().get(0).getValue(0));
            assertEquals("APP", rs.getRows().get(0).getValue(1));
            // The landing database is writable — the whole point of not defaulting into the
            // read-only SNOWFLAKE database.
            engine.execute("CREATE TABLE landing_t (id INTEGER)");
            engine.execute("INSERT INTO landing_t VALUES (1)");
            assertEquals(1, engine.executeQuery("SELECT * FROM landing_t").getRowCount());
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void unsetDefaultsKeepSnowflakePublic() {
        final DatabaseEngine engine = new DatabaseEngine();
        try {
            final ResultSet rs = engine.executeQuery(
                "SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()");
            assertEquals("SNOWFLAKE", rs.getRows().get(0).getValue(0));
            assertEquals("PUBLIC", rs.getRows().get(0).getValue(1));
        } finally {
            engine.shutdown();
        }
    }

    @Test
    public void httpSessionsStartInTheConfiguredDefaults() {
        final ConcurrentDatabaseEngine concurrent =
            new ConcurrentDatabaseEngine(configWith("boot_db", "app"));
        try {
            final SessionContext session = concurrent.getSessionManager().createSession();
            assertEquals("BOOT_DB", session.getCurrentDatabase());
            assertEquals("APP", session.getCurrentSchema());

            final ResultSet rs = concurrent
                .execute("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()", session)
                .getResultSets().get(0);
            assertEquals("BOOT_DB", rs.getRows().get(0).getValue(0));
            assertEquals("APP", rs.getRows().get(0).getValue(1));

            concurrent.execute("CREATE TABLE http_landing (id INTEGER)", session);
            assertEquals(1, concurrent
                .execute("INSERT INTO http_landing VALUES (7)", session)
                .getResultSets().size());
        } finally {
            concurrent.shutdown();
        }
    }

    @Test
    public void httpSessionsDefaultToSnowflakePublicWhenUnset() {
        final ConcurrentDatabaseEngine concurrent = new ConcurrentDatabaseEngine();
        try {
            final SessionContext session = concurrent.getSessionManager().createSession();
            assertEquals("SNOWFLAKE", session.getCurrentDatabase());
            assertEquals("PUBLIC", session.getCurrentSchema());
        } finally {
            concurrent.shutdown();
        }
    }
}
