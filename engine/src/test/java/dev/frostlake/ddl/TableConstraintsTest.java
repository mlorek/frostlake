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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

public class TableConstraintsTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(TableConstraintsTest.class);

    @Test
    public void testPrimaryKeyConstraint() throws SQLException {
        logger.info("Testing PRIMARY KEY constraint in INFORMATION_SCHEMA.TABLE_CONSTRAINTS");

        statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name VARCHAR)");

        ResultSet rs = statement.executeQuery(
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'users' AND CONSTRAINT_TYPE = 'PRIMARY KEY'"
        );

        assertTrue(rs.next(), "Should have PRIMARY KEY constraint");
        assertEquals("TEST_DB", rs.getString("CONSTRAINT_CATALOG"));
        assertEquals("PUBLIC", rs.getString("CONSTRAINT_SCHEMA"));
        assertTrue(rs.getString("CONSTRAINT_NAME").contains("PK"), "Constraint name should contain PK");
        assertEquals("TEST_DB", rs.getString("TABLE_CATALOG"));
        assertEquals("PUBLIC", rs.getString("TABLE_SCHEMA"));
        assertEquals("users", rs.getString("TABLE_NAME"));
        assertEquals("PRIMARY KEY", rs.getString("CONSTRAINT_TYPE"));
        assertEquals("NO", rs.getString("IS_DEFERRABLE"));
        assertEquals("NO", rs.getString("INITIALLY_DEFERRED"));
        assertEquals("NO", rs.getString("ENFORCED"));

        assertFalse(rs.next(), "Should have only one PRIMARY KEY constraint");
        rs.close();
    }

    @Test
    public void testUniqueConstraint() throws SQLException {
        logger.info("Testing UNIQUE constraint in INFORMATION_SCHEMA.TABLE_CONSTRAINTS");

        statement.execute("CREATE TABLE products (id INTEGER, code VARCHAR UNIQUE, name VARCHAR)");

        ResultSet rs = statement.executeQuery(
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'products' AND CONSTRAINT_TYPE = 'UNIQUE'"
        );

        assertTrue(rs.next(), "Should have UNIQUE constraint");
        assertEquals("PUBLIC", rs.getString("CONSTRAINT_SCHEMA"));
        assertTrue(rs.getString("CONSTRAINT_NAME").contains("UNIQUE"), "Constraint name should contain UNIQUE");
        assertEquals("products", rs.getString("TABLE_NAME"));
        assertEquals("UNIQUE", rs.getString("CONSTRAINT_TYPE"));

        assertFalse(rs.next(), "Should have only one UNIQUE constraint");
        rs.close();
    }

    @Test
    public void testForeignKeyConstraint() throws SQLException {
        logger.info("Testing FOREIGN KEY constraint in INFORMATION_SCHEMA.TABLE_CONSTRAINTS");

        statement.execute("CREATE TABLE departments (id INTEGER PRIMARY KEY, name VARCHAR)");
        statement.execute("CREATE TABLE employees (id INTEGER, dept_id INTEGER REFERENCES departments(id), name VARCHAR)");

        ResultSet rs = statement.executeQuery(
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'employees' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"
        );

        assertTrue(rs.next(), "Should have FOREIGN KEY constraint");
        assertEquals("PUBLIC", rs.getString("CONSTRAINT_SCHEMA"));
        assertTrue(rs.getString("CONSTRAINT_NAME").contains("FK"), "Constraint name should contain FK");
        assertEquals("employees", rs.getString("TABLE_NAME"));
        assertEquals("FOREIGN KEY", rs.getString("CONSTRAINT_TYPE"));

        rs.close();
    }

    @Test
    public void testMultipleConstraintsOnSameTable() throws SQLException {
        logger.info("Testing multiple constraints on same table");

        statement.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, order_number VARCHAR UNIQUE, customer_id INTEGER)");

        ResultSet rs = statement.executeQuery(
            "SELECT CONSTRAINT_TYPE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'orders' ORDER BY CONSTRAINT_TYPE"
        );

        assertTrue(rs.next(), "Should have first constraint");
        assertEquals("PRIMARY KEY", rs.getString("CONSTRAINT_TYPE"));

        assertTrue(rs.next(), "Should have second constraint");
        assertEquals("UNIQUE", rs.getString("CONSTRAINT_TYPE"));

        assertFalse(rs.next(), "Should have exactly two constraints");
        rs.close();
    }

    @Test
    public void testNoConstraints() throws SQLException {
        logger.info("Testing table with no constraints");

        statement.execute("CREATE TABLE logs (timestamp INTEGER, message VARCHAR)");

        ResultSet rs = statement.executeQuery(
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'logs'"
        );

        assertFalse(rs.next(), "Should have no constraints");
        rs.close();
    }

    @Test
    public void testTableConstraintsAcrossSchemas() throws SQLException {
        logger.info("Testing TABLE_CONSTRAINTS across schemas");

        statement.execute("CREATE SCHEMA schema1");
        statement.execute("CREATE TABLE schema1.table1 (id INTEGER PRIMARY KEY)");

        statement.execute("CREATE SCHEMA schema2");
        statement.execute("CREATE TABLE schema2.table2 (id INTEGER PRIMARY KEY)");

        ResultSet rs = statement.executeQuery(
            "SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'PRIMARY KEY' ORDER BY TABLE_SCHEMA, TABLE_NAME"
        );

        boolean foundSchema1 = false;
        boolean foundSchema2 = false;

        while (rs.next()) {
            String schema = rs.getString("TABLE_SCHEMA");
            String table = rs.getString("TABLE_NAME");
            if ("schema1".equalsIgnoreCase(schema) && "table1".equalsIgnoreCase(table)) {
                foundSchema1 = true;
            }
            if ("schema2".equalsIgnoreCase(schema) && "table2".equalsIgnoreCase(table)) {
                foundSchema2 = true;
            }
        }

        assertTrue(foundSchema1, "Should find constraint in schema1");
        assertTrue(foundSchema2, "Should find constraint in schema2");
        rs.close();
    }

    @Test
    public void testTableConstraintsWithJoin() throws SQLException {
        logger.info("Testing TABLE_CONSTRAINTS joined with TABLES");

        statement.execute("CREATE TABLE items (id INTEGER PRIMARY KEY, name VARCHAR)");

        ResultSet rs = statement.executeQuery("""
            SELECT t.TABLE_NAME, tc.CONSTRAINT_TYPE
            FROM INFORMATION_SCHEMA.TABLES t
            INNER JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
              ON t.TABLE_NAME = tc.TABLE_NAME AND t.TABLE_SCHEMA = tc.TABLE_SCHEMA
            WHERE t.TABLE_NAME = 'items'
            """
        );

        assertTrue(rs.next(), "Should have constraint via join");
        assertEquals("items", rs.getString("TABLE_NAME"));
        assertEquals("PRIMARY KEY", rs.getString("CONSTRAINT_TYPE"));

        rs.close();
    }

    @Test
    public void testTableConstraintsFiltering() throws SQLException {
        logger.info("Testing TABLE_CONSTRAINTS with WHERE filtering");

        statement.execute("CREATE TABLE t1 (id INTEGER PRIMARY KEY)");
        statement.execute("CREATE TABLE t2 (id INTEGER PRIMARY KEY)");
        statement.execute("CREATE TABLE t3 (id INTEGER, code VARCHAR UNIQUE)");

        ResultSet rs = statement.executeQuery(
            "SELECT COUNT(*) AS cnt FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'PRIMARY KEY' AND TABLE_NAME IN ('t1', 't2', 't3')"
        );

        assertTrue(rs.next());
        assertEquals(2, rs.getInt("cnt"), "Should have 2 PRIMARY KEY constraints");
        rs.close();
    }

    @Test
    public void testConstraintCatalogAndSchemaMatch() throws SQLException {
        logger.info("Testing constraint catalog and schema match table catalog and schema");

        statement.execute("CREATE TABLE test_table (id INTEGER PRIMARY KEY)");

        ResultSet rs = statement.executeQuery(
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'test_table'"
        );

        assertTrue(rs.next());
        assertEquals(rs.getString("CONSTRAINT_CATALOG"), rs.getString("TABLE_CATALOG"),
                     "Constraint catalog should match table catalog");
        assertEquals(rs.getString("CONSTRAINT_SCHEMA"), rs.getString("TABLE_SCHEMA"),
                     "Constraint schema should match table schema");
        rs.close();
    }
}
