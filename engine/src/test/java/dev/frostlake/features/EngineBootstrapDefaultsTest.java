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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The state a brand-new engine boots into: the session starts in SNOWFLAKE.PUBLIC, and the
 * SNOWFLAKE database carries both default schemas.
 *
 * <p>Deliberately NOT on the live surface: these assertions are about a FRESH embedded engine's
 * bootstrap session state, which a shared live session (already placed in a test database by the
 * harness) cannot exhibit. The engine-agnostic default-schema semantics live in
 * {@code DefaultInitializationTest}.
 */
public class EngineBootstrapDefaultsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private String scalar(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    @Test
    public void testCurrentDatabaseIsSnowflake() {
        assertEquals("SNOWFLAKE", scalar("SELECT CURRENT_DATABASE()"));
    }

    @Test
    public void testCurrentSchemaIsPublic() {
        assertEquals("PUBLIC", scalar("SELECT CURRENT_SCHEMA()"));
    }

    @Test
    public void testDefaultPublicSchemaExists() {
        assertEquals(1,
            engine.executeQuery("SHOW SCHEMAS LIKE 'PUBLIC' IN DATABASE SNOWFLAKE").getRowCount());
    }

    @Test
    public void testDefaultInformationSchemaExists() {
        assertEquals(1,
            engine.executeQuery("SHOW SCHEMAS LIKE 'INFORMATION_SCHEMA' IN DATABASE SNOWFLAKE").getRowCount());
    }
}
