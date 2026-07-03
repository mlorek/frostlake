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

/**
 * Tests for DROP commands (TABLE, VIEW, SCHEMA, DATABASE)
 */
public class DropCommandsTest extends BaseDatabaseTest {

    @Test
    public void testDropTable() {
        engine.execute("CREATE TABLE users (id INTEGER, username VARCHAR)");

        ResultSet tablesBefore = engine.showTables();
        assertEquals(1, tablesBefore.getRowCount(), "Should have one table");

        engine.execute("DROP TABLE users");

        ResultSet tablesAfter = engine.showTables();
        assertEquals(0, tablesAfter.getRowCount(), "Should have no tables");
    }

    @Test
    public void testDropView() {
        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("CREATE VIEW user_view AS SELECT * FROM users");

        ResultSet viewsBefore = engine.showViews();
        assertEquals(1, viewsBefore.getRowCount(), "Should have one view");

        engine.execute("DROP VIEW user_view");

        ResultSet viewsAfter = engine.showViews();
        assertEquals(0, viewsAfter.getRowCount(), "Should have no views");
    }

    @Test
    public void testDropSchema() {
        engine.execute("CREATE SCHEMA drop_test_schema");

        ResultSet schemasBefore = engine.showSchemas();
        int countBefore = schemasBefore.getRowCount();

        engine.execute("DROP SCHEMA drop_test_schema");

        ResultSet schemasAfter = engine.showSchemas();
        assertEquals(countBefore - 1, schemasAfter.getRowCount(), "Should have one less schema");
    }

    @Test
    public void testDropDatabase() {
        engine.execute("CREATE DATABASE drop_test_db");

        ResultSet dbsBefore = engine.showDatabases();
        int countBefore = dbsBefore.getRowCount();

        engine.execute("DROP DATABASE drop_test_db");

        ResultSet dbsAfter = engine.showDatabases();
        assertEquals(countBefore - 1, dbsAfter.getRowCount(), "Should have one less database");
    }

    @Test
    public void testDropTableDoesNotAffectOtherTables() {
        engine.execute("CREATE TABLE users (id INTEGER)");
        engine.execute("CREATE TABLE products (id INTEGER)");

        engine.execute("DROP TABLE users");

        ResultSet tables = engine.showTables();
        assertEquals(1, tables.getRowCount(), "Should still have products table");
    }
}
