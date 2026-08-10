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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SHOW and DESCRIBE commands
 */
public class ShowDescribeTest extends BaseDatabaseTest {

    @Test
    public void testShowDatabases() {
        engine.execute("CREATE DATABASE test_db1");
        engine.execute("CREATE DATABASE test_db2");

        final ResultSet result = engine.executeQuery("SHOW DATABASES");
        assertTrue(result.getRowCount() >= 3); // SNOWFLAKE + test_db1 + test_db2
    }

    @Test
    public void testShowTables() {
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR)");
        engine.execute("CREATE TABLE departments (id INTEGER, name VARCHAR)");

        final ResultSet result = engine.executeQuery("SHOW TABLES");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testShowColumns() {
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR, active BOOLEAN)");

        final ResultSet result = engine.executeQuery("SHOW COLUMNS IN test_table");
        assertEquals(3, result.getRowCount());
    }

    @Test
    public void testDescribeTable() {
        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");

        final ResultSet result = engine.executeQuery("DESCRIBE TABLE test_table");
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testShowViews() {
        engine.execute("CREATE TABLE base_table (id INTEGER, value INTEGER)");
        engine.execute("CREATE VIEW test_view AS SELECT * FROM base_table");

        final ResultSet result = engine.executeQuery("SHOW VIEWS");
        assertEquals(1, result.getRowCount());
    }
}
