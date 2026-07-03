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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CREATE DATABASE command
 */
public class CreateDatabaseTest extends BaseDatabaseTest {

    @Test
    public void testCreateDatabase() {
        engine.execute("CREATE DATABASE new_db");

        ResultSet databases = engine.showDatabases();
        assertTrue(databases.getRowCount() > 0, "Should have at least one database");
    }

    @Test
    public void testMultipleDatabases() {
        engine.execute("CREATE DATABASE db1");
        engine.execute("CREATE DATABASE db2");

        ResultSet databases = engine.showDatabases();
        assertTrue(databases.getRowCount() >= 2, "Should have at least 2 databases");
    }

    @Test
    public void testUseDatabaseAfterCreate() {
        engine.execute("CREATE DATABASE use_test_db");
        ExecutionResult result = engine.execute("USE DATABASE use_test_db");
        assertTrue(result.isSuccess(), "Should be able to use newly created database");
    }
}
