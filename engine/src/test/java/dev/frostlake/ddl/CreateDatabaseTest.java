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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for CREATE DATABASE command
 */
public class CreateDatabaseTest extends BaseDatabaseTest {

    @Test
    public void testCreateDatabase() {
        engine.execute("CREATE DATABASE new_db");

        final ResultSet databases = engine.showDatabases();
        assertTrue(databases.getRowCount() > 0, "Should have at least one database");
    }

    @Test
    public void testMultipleDatabases() {
        // Count only the databases this test creates, through SQL. The old form counted whatever
        // engine.showDatabases() reported, which (a) assumed other databases were already there — on
        // a clean account "Should have at least 2 databases" failed — and (b) reads the in-memory
        // catalog, so under SF_LIVE it never saw the two CREATEs at all. A prefixed pair counted with
        // SHOW DATABASES LIKE is deterministic on both backends.
        engine.execute("CREATE DATABASE cdt_multi_1");
        engine.execute("CREATE DATABASE cdt_multi_2");

        final ResultSet databases = engine.executeQuery("SHOW DATABASES LIKE 'CDT_MULTI_%'");
        assertEquals(2, databases.getRowCount(), "Both created databases should be listed");

        // CREATE DATABASE activates the new database, so step back before dropping them.
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("DROP DATABASE IF EXISTS cdt_multi_1");
        engine.execute("DROP DATABASE IF EXISTS cdt_multi_2");
    }

    @Test
    public void testUseDatabaseAfterCreate() {
        engine.execute("CREATE DATABASE use_test_db");
        final ExecutionResult result = engine.execute("USE DATABASE use_test_db");
        assertTrue(result.isSuccess(), "Should be able to use newly created database");
    }
}
