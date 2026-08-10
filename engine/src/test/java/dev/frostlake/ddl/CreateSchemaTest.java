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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CREATE SCHEMA command
 */
public class CreateSchemaTest extends BaseDatabaseTest {

    @Test
    public void testCreateSchema() {
        engine.execute("CREATE SCHEMA new_schema");

        final ResultSet schemas = engine.showSchemas();
        assertTrue(schemas.getRowCount() > 0, "Should have at least one schema");
    }

    @Test
    public void testCreateSchemaInCurrentDatabase() {
        engine.execute("CREATE SCHEMA another_schema");
        engine.execute("USE SCHEMA another_schema");

        final ResultSet schemas = engine.showSchemas();
        assertTrue(schemas.getRowCount() >= 2, "Should have at least 2 schemas");
    }

    @Test
    public void testCreateTableInNewSchema() {
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("CREATE TABLE other_schema.users (id INTEGER, name VARCHAR)");

        // Switch to new schema to verify table exists
        engine.execute("USE SCHEMA other_schema");
        Assumptions.assumeFalse(isLiveSnowflake(),
            "counts through engine.showTables(), an engine accessor that under SF_LIVE still reads the "
            + "embedded catalog — only execute()/executeQuery() are rerouted, so the CREATE TABLE went "
            + "to Snowflake and the embedded schema is empty");
        final ResultSet tables = engine.showTables();
        assertEquals(1, tables.getRowCount(), "Should have one table in new schema");
    }
}
