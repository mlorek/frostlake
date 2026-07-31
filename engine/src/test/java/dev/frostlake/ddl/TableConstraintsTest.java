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
        assertTrue(rs.getString("CONSTRAINT_NAME").startsWith("SYS_CONSTRAINT_"),
                   "an unnamed PRIMARY KEY is auto-named SYS_CONSTRAINT_<uuid>, as in Snowflake");
        assertEquals("TEST_DB", rs.getString("TABLE_CATALOG"));
        assertEquals("PUBLIC", rs.getString("TABLE_SCHEMA"));
        assertEquals("USERS", rs.getString("TABLE_NAME"));
        assertEquals("PRIMARY KEY", rs.getString("CONSTRAINT_TYPE"));
        assertEquals("NO", rs.getString("IS_DEFERRABLE"));
        // Live-verified on a real account: Snowflake reports INITIALLY_DEFERRED = YES for
        // PRIMARY KEY, UNIQUE and FOREIGN KEY alike, and ENFORCED is a constant NO (a PRIMARY KEY RELY
        // still shows ENFORCED = NO with RELY = YES).
        assertEquals("YES", rs.getString("INITIALLY_DEFERRED"));
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
        assertTrue(rs.getString("CONSTRAINT_NAME").startsWith("SYS_CONSTRAINT_"),
                   "an unnamed UNIQUE constraint is auto-named SYS_CONSTRAINT_<uuid>, as in Snowflake");
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
        assertTrue(rs.getString("CONSTRAINT_NAME").startsWith("SYS_CONSTRAINT_"),
                   "an unnamed FOREIGN KEY is auto-named SYS_CONSTRAINT_<uuid>, as in Snowflake");
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
        // TABLE_CONSTRAINTS reports one row per CONSTRAINT, so a composite key is a single row.
        assertEquals(1, primaryKeyConstraintCount("dp2"));

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

        // Constraint names are opaque SYS_CONSTRAINT_<uuid> values, so the FK is looked up through
        // TABLE_CONSTRAINTS and the referenced key is matched against the parent's own PK name.
        final String fkName = constraintName("rc_child", "FOREIGN KEY");
        final String parentPkName = constraintName("rc_parent", "PRIMARY KEY");

        final ResultSet rs = statement.executeQuery(
            "SELECT UNIQUE_CONSTRAINT_NAME FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS "
            + "WHERE CONSTRAINT_NAME = '" + fkName + "'");
        assertTrue(rs.next(), "table-level FK should appear in REFERENTIAL_CONSTRAINTS");
        assertEquals(parentPkName, rs.getString("UNIQUE_CONSTRAINT_NAME"),
                     "the referenced constraint is the parent table's PRIMARY KEY");
        assertFalse(rs.next(), "exactly one referential row for the FK");
        rs.close();
    }

    @Test
    public void testExplicitlyNamedConstraintKeepsItsName() throws SQLException {
        logger.info("Testing that CONSTRAINT <name> FOREIGN KEY keeps the given name");

        statement.execute("CREATE TABLE nc_parent (id INTEGER PRIMARY KEY)");
        statement.execute("CREATE TABLE nc_child (a INTEGER, "
            + "CONSTRAINT my_fk FOREIGN KEY (a) REFERENCES nc_parent(id))");

        assertEquals("MY_FK", constraintName("nc_child", "FOREIGN KEY"),
                     "an explicitly named constraint is never auto-named");
    }

    @Test
    public void testCompositePrimaryKeyIsOneConstraint() throws SQLException {
        logger.info("Testing that a composite PRIMARY KEY is ONE row in TABLE_CONSTRAINTS");

        // The live-Snowflake shape: one row per CONSTRAINT, so the two-column key is a single row and the
        // single-column UNIQUE is another — each under the name its CONSTRAINT clause gave it.
        statement.execute("""
            CREATE TABLE ck (a INTEGER, b INTEGER, c VARCHAR,
                             CONSTRAINT my_pk PRIMARY KEY (a, b), CONSTRAINT my_uq UNIQUE (c))
            """);

        assertEquals(1, primaryKeyConstraintCount("ck"),
                     "a PRIMARY KEY over (a, b) is one constraint, not one per column");
        assertEquals("MY_PK", constraintName("ck", "PRIMARY KEY"));
        assertEquals(1, constraintCount("ck", "UNIQUE"));
        assertEquals("MY_UQ", constraintName("ck", "UNIQUE"));
    }

    @Test
    public void testMultiColumnUniqueIsOneConstraint() throws SQLException {
        logger.info("Testing that a multi-column UNIQUE is ONE constraint with ONE name");

        statement.execute("CREATE TABLE mu (a INTEGER, b INTEGER, CONSTRAINT uq_ab UNIQUE (a, b))");

        assertEquals(1, constraintCount("mu", "UNIQUE"),
                     "UNIQUE (a, b) is a single constraint, not one per column");
        assertEquals("UQ_AB", constraintName("mu", "UNIQUE"));
    }

    @Test
    public void testUnnamedTableLevelConstraintsAreAutoNamed() throws SQLException {
        logger.info("Testing that constraints declared without CONSTRAINT <name> keep auto-naming");

        statement.execute("CREATE TABLE ck2 (a INTEGER, b VARCHAR, PRIMARY KEY (a), UNIQUE (b))");

        assertTrue(constraintName("ck2", "PRIMARY KEY").startsWith("SYS_CONSTRAINT_"),
                   "an unnamed table-level PRIMARY KEY is still auto-named SYS_CONSTRAINT_<uuid>");
        assertTrue(constraintName("ck2", "UNIQUE").startsWith("SYS_CONSTRAINT_"),
                   "an unnamed table-level UNIQUE is still auto-named SYS_CONSTRAINT_<uuid>");
    }

    @Test
    public void testAlterTableAddNamedConstraints() throws SQLException {
        logger.info("Testing that ALTER TABLE ADD CONSTRAINT <name> keeps the given name");

        statement.execute("CREATE TABLE anc (a INTEGER, b INTEGER, c VARCHAR)");
        statement.execute("ALTER TABLE anc ADD CONSTRAINT anc_pk PRIMARY KEY (a, b)");
        statement.execute("ALTER TABLE anc ADD CONSTRAINT anc_uq UNIQUE (c)");

        assertEquals(1, primaryKeyConstraintCount("anc"), "the added composite key is one constraint");
        assertEquals("ANC_PK", constraintName("anc", "PRIMARY KEY"));
        assertEquals("ANC_UQ", constraintName("anc", "UNIQUE"));
    }

    @Test
    public void testReferentialConstraintsUseExplicitNames() throws SQLException {
        logger.info("Testing that REFERENTIAL_CONSTRAINTS reports explicit constraint names");

        statement.execute("CREATE TABLE erc_parent (id INTEGER, CONSTRAINT erc_pk PRIMARY KEY (id))");
        statement.execute("CREATE TABLE erc_child (a INTEGER, "
            + "CONSTRAINT erc_fk FOREIGN KEY (a) REFERENCES erc_parent(id))");

        final ResultSet rs = statement.executeQuery(
            "SELECT CONSTRAINT_NAME, UNIQUE_CONSTRAINT_NAME FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS "
            + "WHERE CONSTRAINT_NAME = 'ERC_FK'");
        assertTrue(rs.next(), "the named FK should appear in REFERENTIAL_CONSTRAINTS");
        assertEquals("ERC_FK", rs.getString("CONSTRAINT_NAME"));
        assertEquals("ERC_PK", rs.getString("UNIQUE_CONSTRAINT_NAME"),
                     "the referenced constraint is the parent's explicitly named PRIMARY KEY");
        assertFalse(rs.next(), "exactly one referential row for the FK");
        rs.close();
    }

    @Test
    public void testDroppedPrimaryKeyLosesItsExplicitName() throws SQLException {
        logger.info("Testing that a re-added PRIMARY KEY is a new constraint with a new name");

        statement.execute("CREATE TABLE dpn (a INTEGER, CONSTRAINT dpn_pk PRIMARY KEY (a))");
        assertEquals("DPN_PK", constraintName("dpn", "PRIMARY KEY"));

        statement.execute("ALTER TABLE dpn DROP PRIMARY KEY");
        statement.execute("ALTER TABLE dpn ADD PRIMARY KEY (a)");
        assertTrue(constraintName("dpn", "PRIMARY KEY").startsWith("SYS_CONSTRAINT_"),
                   "the dropped constraint's name goes with it; the new unnamed one auto-names itself");
    }

    private String constraintName(final String tableName, final String type) throws SQLException {
        final ResultSet rs = statement.executeQuery(
            "SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
            + "WHERE TABLE_NAME = '" + tableName.toUpperCase() + "' AND CONSTRAINT_TYPE = '" + type + "'");
        assertTrue(rs.next(), "expected a " + type + " constraint on " + tableName);
        final String name = rs.getString("CONSTRAINT_NAME");
        rs.close();
        return name;
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
