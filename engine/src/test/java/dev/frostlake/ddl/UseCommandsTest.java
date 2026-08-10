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
import dev.frostlake.ExecutionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for USE DATABASE and USE SCHEMA commands
 */
public class UseCommandsTest extends BaseDatabaseTest {

    @Test
    public void testUseDatabase() {
        engine.execute("CREATE DATABASE use_test_db");
        final ExecutionResult result = engine.execute("USE DATABASE use_test_db");

        assertTrue(result.isSuccess(), "USE DATABASE should succeed");
    }

    @Test
    public void testUseSchema() {
        engine.execute("CREATE SCHEMA use_test_schema");
        final ExecutionResult result = engine.execute("USE SCHEMA use_test_schema");

        assertTrue(result.isSuccess(), "USE SCHEMA should succeed");
    }

    @Test
    public void testUseDatabaseThenSchema() {
        engine.execute("CREATE DATABASE another_db");
        engine.execute("USE DATABASE another_db");
        engine.execute("CREATE SCHEMA another_schema");
        engine.execute("USE SCHEMA another_schema");

        // Create table in new context
        engine.execute("CREATE TABLE test_table (id INTEGER)");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("SELECT * FROM test_table");
            }
        }, "Should be able to query table in current schema");
    }

    @Test
    public void testSwitchBetweenSchemas() {
        engine.execute("CREATE SCHEMA schema1");
        engine.execute("CREATE SCHEMA schema2");

        engine.execute("USE SCHEMA schema1");
        engine.execute("CREATE TABLE users (id INTEGER)");

        engine.execute("USE SCHEMA schema2");
        engine.execute("CREATE TABLE products (id INTEGER)");

        // Verify each schema has its own table
        engine.execute("USE SCHEMA schema1");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM users");
            }
        }, "Should find users in schema1");

        engine.execute("USE SCHEMA schema2");
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT * FROM products");
            }
        }, "Should find products in schema2");
    }
}
