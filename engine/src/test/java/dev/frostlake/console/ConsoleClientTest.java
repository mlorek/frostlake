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

package dev.frostlake.console;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Basic tests for ConsoleClient
 */
public class ConsoleClientTest {

    @Test
    public void testConsoleClientCreation() {
        // Just verify we can instantiate the console client
        final ConsoleClient client = new ConsoleClient();
        assertNotNull(client, "ConsoleClient should be created");
    }

    @Test
    public void testContextPromptWithDatabase() {
        final DatabaseEngine engine = new DatabaseEngine();

        // Create and use a database
        engine.execute("DROP DATABASE IF EXISTS test_console_db");
        engine.execute("CREATE DATABASE test_console_db");
        engine.execute("USE DATABASE test_console_db");

        final String db = engine.getCatalog().getCurrentDatabase();
        String schema = engine.getCatalog().getCurrentSchema();

        assertNotNull(db, "Database should be selected");
        assertEquals("TEST_CONSOLE_DB", db);
        // Note: Schema might be auto-selected (PUBLIC) depending on implementation
        // This is acceptable behavior

        // Explicitly use a schema
        engine.execute("USE SCHEMA PUBLIC");
        schema = engine.getCatalog().getCurrentSchema();

        assertNotNull(schema, "Schema should be selected");
        assertEquals("TEST_CONSOLE_DB", db);
        assertEquals("PUBLIC", schema);

        engine.shutdown();
    }
}
