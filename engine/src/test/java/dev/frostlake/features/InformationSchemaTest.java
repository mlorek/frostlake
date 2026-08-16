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

import dev.frostlake.BaseJdbcTest;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for INFORMATION_SCHEMA system views
 */
public class InformationSchemaTest extends BaseJdbcTest {

    @Test
    public void testInformationSchemaDatabasesView() throws SQLException {
        // Create a test database
        statement.execute("CREATE DATABASE sample_db");

        // Query INFORMATION_SCHEMA.DATABASES (INFORMATION_SCHEMA is a schema in current database)
        final ResultSet rs = statement.executeQuery("SELECT * FROM INFORMATION_SCHEMA.DATABASES");

        boolean foundTestDb = false;
        boolean foundSampleDb = false;
        while (rs.next()) {
            final String dbName = rs.getString("DATABASE_NAME");
            if ("TEST_DB".equals(dbName)) {
                foundTestDb = true;
            }
            if ("SAMPLE_DB".equals(dbName)) {
                foundSampleDb = true;
            }
        }

        assertTrue(foundTestDb, "Should find TEST_DB");
        assertTrue(foundSampleDb, "Should find SAMPLE_DB");
        rs.close();
    }

    @Test
    public void testInformationSchemaSchemataView() throws SQLException {
        // Create a test schema
        statement.execute("CREATE SCHEMA test_schema");

        // Query INFORMATION_SCHEMA.SCHEMATA
        final ResultSet rs = statement.executeQuery("SELECT * FROM INFORMATION_SCHEMA.SCHEMATA");

        boolean foundPublicSchema = false;
        boolean foundTestSchema = false;
        while (rs.next()) {
            final String schemaName = rs.getString("SCHEMA_NAME");
            final String catalogName = rs.getString("CATALOG_NAME");
            if ("PUBLIC".equalsIgnoreCase(schemaName) && "TEST_DB".equalsIgnoreCase(catalogName)) {
                foundPublicSchema = true;
            }
            if ("test_schema".equalsIgnoreCase(schemaName) && "TEST_DB".equalsIgnoreCase(catalogName)) {
                foundTestSchema = true;
            }
        }

        assertTrue(foundPublicSchema, "Should find PUBLIC schema");
        assertTrue(foundTestSchema, "Should find test_schema");
        rs.close();
    }

    @Test
    public void testInformationSchemaTablesView() throws SQLException {
        // Create test tables
        statement.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        statement.execute("CREATE TABLE products (id INTEGER, price DECIMAL)");

        // Query INFORMATION_SCHEMA.TABLES. Filter to the PUBLIC schema: on real Snowflake the
        // catalog filter alone also surfaces the INFORMATION_SCHEMA views, which are not BASE TABLEs.
        final ResultSet rs = statement.executeQuery("""
            SELECT * FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_CATALOG = 'TEST_DB' AND TABLE_SCHEMA = 'PUBLIC'
            """);

        boolean foundUsers = false;
        boolean foundProducts = false;
        while (rs.next()) {
            final String tableName = rs.getString("TABLE_NAME");
            final String tableType = rs.getString("TABLE_TYPE");
            assertEquals("BASE TABLE", tableType, "Table type should be BASE TABLE");
            if ("users".equalsIgnoreCase(tableName)) {
                foundUsers = true;
            }
            if ("products".equalsIgnoreCase(tableName)) {
                foundProducts = true;
            }
        }

        assertTrue(foundUsers, "Should find users table");
        assertTrue(foundProducts, "Should find products table");
        rs.close();
    }

    @Test
    public void testInformationSchemaColumnsView() throws SQLException {
        // Create a test table
        statement.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, salary DECIMAL)");

        // Query INFORMATION_SCHEMA.COLUMNS
        final ResultSet rs = statement.executeQuery(
            "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'EMPLOYEES' ORDER BY ORDINAL_POSITION"
        );

        // DATA_TYPE reports Snowflake's canonical family names (live-verified):
        // integer/decimal flavors -> NUMBER, character flavors -> TEXT.

        // Check column 1: id
        assertTrue(rs.next(), "Should have first column");
        assertEquals("EMPLOYEES", rs.getString("TABLE_NAME"));
        assertEquals("ID", rs.getString("COLUMN_NAME"));
        assertEquals(1, rs.getInt("ORDINAL_POSITION"));
        assertEquals("NUMBER", rs.getString("DATA_TYPE"));

        // Check column 2: name
        assertTrue(rs.next(), "Should have second column");
        assertEquals("NAME", rs.getString("COLUMN_NAME"));
        assertEquals(2, rs.getInt("ORDINAL_POSITION"));
        assertEquals("TEXT", rs.getString("DATA_TYPE"));

        // Check column 3: salary
        assertTrue(rs.next(), "Should have third column");
        assertEquals("SALARY", rs.getString("COLUMN_NAME"));
        assertEquals(3, rs.getInt("ORDINAL_POSITION"));
        assertEquals("NUMBER", rs.getString("DATA_TYPE"));

        assertFalse(rs.next(), "Should have only three columns");
        rs.close();
    }

    @Test
    public void testInformationSchemaViewsView() throws SQLException {
        // Create a test view
        statement.execute("CREATE TABLE base_table (id INTEGER, value VARCHAR)");
        statement.execute("CREATE VIEW test_view AS SELECT * FROM base_table WHERE id > 10");

        // Query INFORMATION_SCHEMA.VIEWS
        final ResultSet rs = statement.executeQuery("SELECT * FROM INFORMATION_SCHEMA.VIEWS");

        boolean foundTestView = false;
        while (rs.next()) {
            final String viewName = rs.getString("TABLE_NAME");
            if ("test_view".equalsIgnoreCase(viewName)) {
                foundTestView = true;
                assertEquals("TEST_DB", rs.getString("TABLE_CATALOG"));
                assertEquals("PUBLIC", rs.getString("TABLE_SCHEMA"));
                assertNotNull(rs.getString("VIEW_DEFINITION"), "Should have view definition");
                break;
            }
        }

        assertTrue(foundTestView, "Should find test_view");
        rs.close();
    }

    @Test
    public void testInformationSchemaQualifiedAccess() throws SQLException {
        // Test schema.view qualification (INFORMATION_SCHEMA is a schema)
        final ResultSet rs1 = statement.executeQuery("SELECT * FROM INFORMATION_SCHEMA.DATABASES");
        assertTrue(rs1.next(), "Should be able to query INFORMATION_SCHEMA");
        rs1.close();

        // Test fully qualified database.schema.view
        final ResultSet rs2 = statement.executeQuery("SELECT * FROM TEST_DB.INFORMATION_SCHEMA.TABLES");
        assertNotNull(rs2, "Should be able to query with full qualification");
        rs2.close();

        // INFORMATION_SCHEMA exists in every database
        statement.execute("CREATE DATABASE other_db");
        statement.execute("USE DATABASE other_db");
        final ResultSet rs3 = statement.executeQuery("SELECT * FROM INFORMATION_SCHEMA.DATABASES");
        assertTrue(rs3.next(), "INFORMATION_SCHEMA should exist in other_db too");
        rs3.close();
    }

    @Test
    public void testInformationSchemaCannotBeDropped() throws SQLException {
        // Attempt to drop INFORMATION_SCHEMA schema
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP SCHEMA INFORMATION_SCHEMA");
            }
        }, "Should not be able to drop INFORMATION_SCHEMA schema");
    }

    @Test
    public void testSystemViewsCannotBeDropped() throws SQLException {
        // Attempt to drop system views
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP VIEW INFORMATION_SCHEMA.DATABASES");
            }
        }, "Should not be able to drop system view DATABASES");

        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                statement.execute("DROP VIEW INFORMATION_SCHEMA.COLUMNS");
            }
        }, "Should not be able to drop system view COLUMNS");
    }

    @Test
    public void testInformationSchemaWithJoins() throws SQLException {
        // Create tables
        statement.execute("CREATE TABLE orders (id INTEGER, product_id INTEGER)");

        // Query with joins between INFORMATION_SCHEMA views
        final ResultSet rs = statement.executeQuery("""
            SELECT t.TABLE_NAME, c.COLUMN_NAME, c.DATA_TYPE
            FROM INFORMATION_SCHEMA.TABLES t
            INNER JOIN INFORMATION_SCHEMA.COLUMNS c
              ON t.TABLE_NAME = c.TABLE_NAME
            WHERE t.TABLE_NAME = 'ORDERS'
            """);

        int count = 0;
        while (rs.next()) {
            count++;
            final String tableName = rs.getString("TABLE_NAME");
            assertTrue("orders".equalsIgnoreCase(tableName), "Table name should be orders");
        }

        assertEquals(2, count, "Should have 2 columns for orders table");
        rs.close();
    }
}
