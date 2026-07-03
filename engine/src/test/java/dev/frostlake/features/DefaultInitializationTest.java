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
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for default database and schema initialization
 */
public class DefaultInitializationTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DatabaseEngine();
    }

    @AfterEach
    public void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testDefaultSnowflakeDatabaseExists() {
        // SNOWFLAKE database should exist by default
        ResultSet result = engine.executeQuery("SHOW DATABASES");
        boolean found = false;
        while (result.next()) {
            if ("SNOWFLAKE".equals(result.getValue("name"))) {
                found = true;
                break;
            }
        }
        assertTrue(found, "SNOWFLAKE database should exist by default");
    }

    @Test
    public void testCurrentDatabaseIsSnowflake() {
        // Default current database should be SNOWFLAKE
        assertEquals("SNOWFLAKE", engine.getCurrentDatabase());
    }

    @Test
    public void testDefaultPublicSchemaExists() {
        // PUBLIC schema should exist in SNOWFLAKE database
        Database db = engine.getCatalog().getDatabase("SNOWFLAKE");
        Schema publicSchema = db.getSchema("PUBLIC");
        assertNotNull(publicSchema);
        assertEquals("PUBLIC", publicSchema.getName());
    }

    @Test
    public void testDefaultInformationSchemaExists() {
        // INFORMATION_SCHEMA should exist in SNOWFLAKE database
        Database db = engine.getCatalog().getDatabase("SNOWFLAKE");
        Schema infoSchema = db.getSchema("INFORMATION_SCHEMA");
        assertNotNull(infoSchema);
        assertEquals("INFORMATION_SCHEMA", infoSchema.getName());
    }

    @Test
    public void testCurrentSchemaIsPublic() {
        // Default current schema should be PUBLIC
        assertEquals("PUBLIC", engine.getCurrentSchema());
    }

    @Test
    public void testShowSchemasIncludesBothDefaultSchemas() {
        // SHOW SCHEMAS should return both PUBLIC and INFORMATION_SCHEMA
        ResultSet result = engine.executeQuery("SHOW SCHEMAS");

        boolean foundPublic = false;
        boolean foundInfoSchema = false;

        while (result.next()) {
            String schemaName = (String) result.getValue("name");
            if ("PUBLIC".equals(schemaName)) {
                foundPublic = true;
            }
            if ("INFORMATION_SCHEMA".equals(schemaName)) {
                foundInfoSchema = true;
            }
        }

        assertTrue(foundPublic, "PUBLIC schema should exist");
        assertTrue(foundInfoSchema, "INFORMATION_SCHEMA should exist");
    }

    @Test
    public void testNewDatabaseGetsDefaultSchemas() {
        // Create a new database
        engine.execute("CREATE DATABASE test_db");

        // Verify it has PUBLIC schema
        Database db = engine.getCatalog().getDatabase("TEST_DB");
        Schema publicSchema = db.getSchema("PUBLIC");
        assertNotNull(publicSchema, "New database should have PUBLIC schema");

        // Verify it has INFORMATION_SCHEMA
        Schema infoSchema = db.getSchema("INFORMATION_SCHEMA");
        assertNotNull(infoSchema, "New database should have INFORMATION_SCHEMA");
    }

    @Test
    public void testCannotDropPublicSchema() {
        // Attempt to drop PUBLIC schema should fail
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP SCHEMA PUBLIC");
        });
        assertTrue(exception.getMessage().contains("Cannot drop PUBLIC schema"));
    }

    @Test
    public void testCannotDropInformationSchema() {
        // Attempt to drop INFORMATION_SCHEMA should fail
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.execute("DROP SCHEMA INFORMATION_SCHEMA");
        });
        assertTrue(exception.getMessage().contains("Cannot drop INFORMATION_SCHEMA schema"));
    }

    @Test
    public void testInformationSchemaHasSystemViews() {
        // Use INFORMATION_SCHEMA
        engine.execute("USE SCHEMA INFORMATION_SCHEMA");

        // Query system views
        ResultSet tables = engine.executeQuery("SHOW VIEWS");

        // Should have at least DATABASES, SCHEMATA, TABLES, COLUMNS, VIEWS
        assertTrue(tables.getRowCount() >= 5,
            "INFORMATION_SCHEMA should have at least 5 system views");
    }
}
