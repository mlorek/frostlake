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
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'USERS' AND CONSTRAINT_TYPE = 'PRIMARY KEY'"
        );

        assertTrue(rs.next(), "Should have PRIMARY KEY constraint");
        assertEquals("TEST_DB", rs.getString("CONSTRAINT_CATALOG"));
        assertEquals("PUBLIC", rs.getString("CONSTRAINT_SCHEMA"));
        assertTrue(rs.getString("CONSTRAINT_NAME").contains("PK"), "Constraint name should contain PK");
        assertEquals("TEST_DB", rs.getString("TABLE_CATALOG"));
        assertEquals("PUBLIC", rs.getString("TABLE_SCHEMA"));
        assertEquals("USERS", rs.getString("TABLE_NAME"));
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
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'PRODUCTS' AND CONSTRAINT_TYPE = 'UNIQUE'"
        );

        assertTrue(rs.next(), "Should have UNIQUE constraint");
        assertEquals("PUBLIC", rs.getString("CONSTRAINT_SCHEMA"));
        assertTrue(rs.getString("CONSTRAINT_NAME").contains("UNIQUE"), "Constraint name should contain UNIQUE");
        assertEquals("PRODUCTS", rs.getString("TABLE_NAME"));
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
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'EMPLOYEES' AND CONSTRAINT_TYPE = 'FOREIGN KEY'"
        );

        assertTrue(rs.next(), "Should have FOREIGN KEY constraint");
        assertEquals("PUBLIC", rs.getString("CONSTRAINT_SCHEMA"));
        assertTrue(rs.getString("CONSTRAINT_NAME").contains("FK"), "Constraint name should contain FK");
        assertEquals("EMPLOYEES", rs.getString("TABLE_NAME"));
        assertEquals("FOREIGN KEY", rs.getString("CONSTRAINT_TYPE"));

        rs.close();
    }

    @Test
    public void testMultipleConstraintsOnSameTable() throws SQLException {
        logger.info("Testing multiple constraints on same table");

        statement.execute("CREATE TABLE orders (id INTEGER PRIMARY KEY, order_number VARCHAR UNIQUE, customer_id INTEGER)");

        ResultSet rs = statement.executeQuery(
            "SELECT CONSTRAINT_TYPE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'ORDERS' ORDER BY CONSTRAINT_TYPE"
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
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'LOGS'"
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
            WHERE t.TABLE_NAME = 'ITEMS'
            """
        );

        assertTrue(rs.next(), "Should have constraint via join");
        assertEquals("ITEMS", rs.getString("TABLE_NAME"));
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
            "SELECT COUNT(*) AS cnt FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_TYPE = 'PRIMARY KEY' AND TABLE_NAME IN ('T1', 'T2', 'T3')"
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
            "SELECT * FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_NAME = 'TEST_TABLE'"
        );

        assertTrue(rs.next());
        assertEquals(rs.getString("CONSTRAINT_CATALOG"), rs.getString("TABLE_CATALOG"),
                     "Constraint catalog should match table catalog");
        assertEquals(rs.getString("CONSTRAINT_SCHEMA"), rs.getString("TABLE_SCHEMA"),
                     "Constraint schema should match table schema");
        rs.close();
    }

    @Test
    public void testAlterTableDropPrimaryKey() throws SQLException {
        logger.info("Testing ALTER TABLE ... DROP PRIMARY KEY");

        statement.execute("CREATE TABLE dp (id INTEGER PRIMARY KEY, name VARCHAR)");
        assertEquals(1, primaryKeyConstraintCount("dp"), "PRIMARY KEY should exist initially");

        statement.execute("ALTER TABLE dp DROP PRIMARY KEY");
        assertEquals(0, primaryKeyConstraintCount("dp"), "PRIMARY KEY should be gone after DROP PRIMARY KEY");
    }

    @Test
    public void testDropAndReAddPrimaryKey() throws SQLException {
        logger.info("Testing DROP PRIMARY KEY on a composite key, then ADD PRIMARY KEY");

        statement.execute("CREATE TABLE dp2 (a INTEGER, b INTEGER, PRIMARY KEY (a, b))");
        // TABLE_CONSTRAINTS reports one row per PRIMARY KEY column, so a composite key is two rows.
        assertEquals(2, primaryKeyConstraintCount("dp2"));

        statement.execute("ALTER TABLE dp2 DROP PRIMARY KEY");
        assertEquals(0, primaryKeyConstraintCount("dp2"), "DROP PRIMARY KEY clears every column of a composite key");

        statement.execute("ALTER TABLE dp2 ADD PRIMARY KEY (a)");
        assertEquals(1, primaryKeyConstraintCount("dp2"), "PRIMARY KEY should be re-addable after a drop");
    }

    @Test
    public void testDropUnique() throws SQLException {
        logger.info("Testing ALTER TABLE ... DROP UNIQUE (col)");

        statement.execute("CREATE TABLE du (a INTEGER UNIQUE, b INTEGER)");
        assertEquals(1, constraintCount("du", "UNIQUE"), "UNIQUE should exist initially");

        statement.execute("ALTER TABLE du DROP UNIQUE (a)");
        assertEquals(0, constraintCount("du", "UNIQUE"), "UNIQUE should be gone after DROP UNIQUE");
    }

    @Test
    public void testDropForeignKeyInline() throws SQLException {
        logger.info("Testing ALTER TABLE ... DROP FOREIGN KEY (col) for an inline reference");

        statement.execute("CREATE TABLE fk_parent (id INTEGER PRIMARY KEY)");
        statement.execute("CREATE TABLE fk_child (x INTEGER REFERENCES fk_parent(id))");
        assertEquals(1, constraintCount("fk_child", "FOREIGN KEY"), "FOREIGN KEY should exist initially");

        statement.execute("ALTER TABLE fk_child DROP FOREIGN KEY (x)");
        assertEquals(0, constraintCount("fk_child", "FOREIGN KEY"), "FOREIGN KEY should be gone after drop");
    }

    @Test
    public void testTableLevelForeignKeyVisibleAndDroppable() throws SQLException {
        logger.info("Testing that a table-level FOREIGN KEY (…) appears in TABLE_CONSTRAINTS and drops");

        statement.execute("CREATE TABLE fk_parent2 (id INTEGER PRIMARY KEY)");
        statement.execute("CREATE TABLE fk_child2 (y INTEGER, FOREIGN KEY (y) REFERENCES fk_parent2(id))");
        // Table-level FKs surface in TABLE_CONSTRAINTS just like inline REFERENCES (Snowflake-aligned).
        assertEquals(1, constraintCount("fk_child2", "FOREIGN KEY"), "table-level FK should be visible");

        statement.execute("ALTER TABLE fk_child2 DROP FOREIGN KEY (y)");
        assertEquals(0, constraintCount("fk_child2", "FOREIGN KEY"), "and removed after DROP FOREIGN KEY");
    }

    @Test
    public void testTableLevelForeignKeyInReferentialConstraints() throws SQLException {
        logger.info("Testing that a table-level FOREIGN KEY appears in REFERENTIAL_CONSTRAINTS");

        statement.execute("CREATE TABLE rc_parent (id INTEGER PRIMARY KEY)");
        statement.execute("CREATE TABLE rc_child (a INTEGER, FOREIGN KEY (a) REFERENCES rc_parent(id))");

        final ResultSet rs = statement.executeQuery(
            "SELECT UNIQUE_CONSTRAINT_NAME FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS "
            + "WHERE CONSTRAINT_NAME = 'RC_CHILD_A_FK'");
        assertTrue(rs.next(), "table-level FK should appear in REFERENTIAL_CONSTRAINTS");
        assertEquals("RC_PARENT_PK", rs.getString("UNIQUE_CONSTRAINT_NAME"));
        assertFalse(rs.next(), "exactly one referential row for the FK");
        rs.close();
    }

    @Test
    public void testDropUniqueKeepsForeignKey() throws SQLException {
        logger.info("Testing that DROP UNIQUE preserves a FOREIGN KEY on the same column");

        statement.execute("CREATE TABLE ufp (id INTEGER PRIMARY KEY)");
        statement.execute("CREATE TABLE uf (z INTEGER UNIQUE REFERENCES ufp(id))");
        assertEquals(1, constraintCount("uf", "UNIQUE"));
        assertEquals(1, constraintCount("uf", "FOREIGN KEY"));

        statement.execute("ALTER TABLE uf DROP UNIQUE (z)");
        assertEquals(0, constraintCount("uf", "UNIQUE"), "UNIQUE dropped");
        assertEquals(1, constraintCount("uf", "FOREIGN KEY"), "FOREIGN KEY on the same column must remain");
    }

    private int constraintCount(final String tableName, final String type) throws SQLException {
        final ResultSet rs = statement.executeQuery(
            "SELECT COUNT(*) AS c FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
            + "WHERE TABLE_NAME = '" + tableName.toUpperCase() + "' AND CONSTRAINT_TYPE = '" + type + "'");
        assertTrue(rs.next());
        final int count = rs.getInt("c");
        rs.close();
        return count;
    }

    private int primaryKeyConstraintCount(final String tableName) throws SQLException {
        return constraintCount(tableName, "PRIMARY KEY");
    }
}
