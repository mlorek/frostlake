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

package dev.frostlake.constraints;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for FOREIGN KEY constraint syntax support (not enforced)
 */
public class ForeignKeyConstraintsTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(ForeignKeyConstraintsTest.class);

    @Test
    public void testColumnLevelForeignKey() {
        logger.info("Testing column-level FOREIGN KEY constraint");

        engine.execute("CREATE TABLE parent_table (id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE child_table (id INTEGER, parent_id INTEGER REFERENCES parent_table(id))");

        Table childTable = engine.getCatalog().resolveTable("child_table");
        TableColumn parentIdCol = childTable.getColumn("parent_id");

        assertTrue(parentIdCol.hasForeignKey());
        assertEquals("PARENT_TABLE", parentIdCol.getReferencedTable());
        assertEquals("ID", parentIdCol.getReferencedColumn());
        assertNull(parentIdCol.getOnDelete());
        assertNull(parentIdCol.getOnUpdate());

        logger.info("Column-level foreign key parsed correctly");
    }

    @Test
    public void testColumnLevelForeignKeyWithActions() {
        logger.info("Testing column-level FOREIGN KEY with ON DELETE/UPDATE");

        engine.execute("CREATE TABLE departments (dept_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE employees (
                emp_id INTEGER PRIMARY KEY,
                dept_id INTEGER REFERENCES departments(dept_id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """);

        Table empTable = engine.getCatalog().resolveTable("employees");
        TableColumn deptIdCol = empTable.getColumn("dept_id");

        assertTrue(deptIdCol.hasForeignKey());
        assertEquals("DEPARTMENTS", deptIdCol.getReferencedTable());
        assertEquals("DEPT_ID", deptIdCol.getReferencedColumn());
        assertEquals("CASCADE", deptIdCol.getOnDelete());
        assertEquals("CASCADE", deptIdCol.getOnUpdate());

        logger.info("Foreign key with referential actions parsed correctly");
    }

    @Test
    public void testTableLevelForeignKey() {
        logger.info("Testing table-level FOREIGN KEY constraint");

        engine.execute("CREATE TABLE customers (customer_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE orders (
                order_id INTEGER PRIMARY KEY,
                customer_id INTEGER,
                FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
            )
            """);

        Table ordersTable = engine.getCatalog().resolveTable("orders");
        assertEquals(1, ordersTable.getForeignKeys().size());

        ForeignKeyConstraint fk = ordersTable.getForeignKeys().get(0);
        assertNull(fk.getConstraintName());
        assertEquals(1, fk.getColumnNames().size());
        assertEquals("CUSTOMER_ID", fk.getColumnNames().get(0));
        assertEquals("CUSTOMERS", fk.getReferencedTable());
        assertEquals(1, fk.getReferencedColumns().size());
        assertEquals("CUSTOMER_ID", fk.getReferencedColumns().get(0));

        logger.info("Table-level foreign key parsed correctly");
    }

    @Test
    public void testTableLevelForeignKeyWithConstraintName() {
        logger.info("Testing table-level FOREIGN KEY with constraint name");

        engine.execute("CREATE TABLE products (product_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE inventory (
                inv_id INTEGER,
                product_id INTEGER,
                CONSTRAINT fk_product FOREIGN KEY (product_id) REFERENCES products(product_id)
            )
            """);

        Table invTable = engine.getCatalog().resolveTable("inventory");
        assertEquals(1, invTable.getForeignKeys().size());

        ForeignKeyConstraint fk = invTable.getForeignKeys().get(0);
        assertEquals("FK_PRODUCT", fk.getConstraintName());
        assertEquals("PRODUCT_ID", fk.getColumnNames().get(0));
        assertEquals("PRODUCTS", fk.getReferencedTable());
        assertEquals("PRODUCT_ID", fk.getReferencedColumns().get(0));

        logger.info("Named foreign key constraint parsed correctly");
    }

    @Test
    public void testMultiColumnForeignKey() {
        logger.info("Testing multi-column FOREIGN KEY constraint");

        engine.execute("CREATE TABLE person (first_name VARCHAR, last_name VARCHAR, PRIMARY KEY (first_name, last_name))");
        engine.execute("""
            CREATE TABLE contact (
                contact_id INTEGER,
                first_name VARCHAR,
                last_name VARCHAR,
                FOREIGN KEY (first_name, last_name) REFERENCES person(first_name, last_name)
            )
            """);

        Table contactTable = engine.getCatalog().resolveTable("contact");
        assertEquals(1, contactTable.getForeignKeys().size());

        ForeignKeyConstraint fk = contactTable.getForeignKeys().get(0);
        assertEquals(2, fk.getColumnNames().size());
        assertEquals("FIRST_NAME", fk.getColumnNames().get(0));
        assertEquals("LAST_NAME", fk.getColumnNames().get(1));
        assertEquals("PERSON", fk.getReferencedTable());
        assertEquals(2, fk.getReferencedColumns().size());
        assertEquals("FIRST_NAME", fk.getReferencedColumns().get(0));
        assertEquals("LAST_NAME", fk.getReferencedColumns().get(1));

        logger.info("Multi-column foreign key parsed correctly");
    }

    @Test
    public void testForeignKeyOnDeleteSetNull() {
        logger.info("Testing FOREIGN KEY with ON DELETE SET NULL");

        engine.execute("CREATE TABLE wallets (wallet_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE transactions (
                txn_id INTEGER,
                wallet_id INTEGER,
                FOREIGN KEY (wallet_id) REFERENCES wallets(wallet_id) ON DELETE SET NULL
            )
            """);

        Table txnTable = engine.getCatalog().resolveTable("transactions");
        ForeignKeyConstraint fk = txnTable.getForeignKeys().get(0);

        assertEquals("SET NULL", fk.getOnDelete());
        assertNull(fk.getOnUpdate());

        logger.info("ON DELETE SET NULL parsed correctly");
    }

    @Test
    public void testForeignKeyOnUpdateRestrict() {
        logger.info("Testing FOREIGN KEY with ON UPDATE RESTRICT");

        engine.execute("CREATE TABLE categories (category_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE items (
                item_id INTEGER,
                category_id INTEGER,
                FOREIGN KEY (category_id) REFERENCES categories(category_id) ON UPDATE RESTRICT
            )
            """);

        Table itemsTable = engine.getCatalog().resolveTable("items");
        ForeignKeyConstraint fk = itemsTable.getForeignKeys().get(0);

        assertNull(fk.getOnDelete());
        assertEquals("RESTRICT", fk.getOnUpdate());

        logger.info("ON UPDATE RESTRICT parsed correctly");
    }

    @Test
    public void testForeignKeyOnDeleteNoAction() {
        logger.info("Testing FOREIGN KEY with ON DELETE NO ACTION");

        engine.execute("CREATE TABLE vendors (vendor_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE purchases (
                purchase_id INTEGER,
                vendor_id INTEGER,
                FOREIGN KEY (vendor_id) REFERENCES vendors(vendor_id) ON DELETE NO ACTION
            )
            """);

        Table purchasesTable = engine.getCatalog().resolveTable("purchases");
        ForeignKeyConstraint fk = purchasesTable.getForeignKeys().get(0);

        assertEquals("NO ACTION", fk.getOnDelete());

        logger.info("ON DELETE NO ACTION parsed correctly");
    }

    @Test
    public void testForeignKeyOnDeleteSetDefault() {
        logger.info("Testing FOREIGN KEY with ON DELETE SET DEFAULT");

        engine.execute("CREATE TABLE groups (group_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE members (
                member_id INTEGER,
                group_id INTEGER DEFAULT 1,
                FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE SET DEFAULT
            )
            """);

        Table membersTable = engine.getCatalog().resolveTable("members");
        ForeignKeyConstraint fk = membersTable.getForeignKeys().get(0);

        assertEquals("SET DEFAULT", fk.getOnDelete());

        logger.info("ON DELETE SET DEFAULT parsed correctly");
    }

    @Test
    public void testMultipleForeignKeysInOneTable() {
        logger.info("Testing multiple FOREIGN KEY constraints in one table");

        engine.execute("CREATE TABLE users (user_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE departments (dept_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE employees (
                emp_id INTEGER PRIMARY KEY,
                manager_id INTEGER,
                dept_id INTEGER,
                FOREIGN KEY (manager_id) REFERENCES users(user_id),
                FOREIGN KEY (dept_id) REFERENCES departments(dept_id) ON DELETE CASCADE
            )
            """);

        Table empTable = engine.getCatalog().resolveTable("employees");
        assertEquals(2, empTable.getForeignKeys().size());

        ForeignKeyConstraint fk1 = empTable.getForeignKeys().get(0);
        assertEquals("MANAGER_ID", fk1.getColumnNames().get(0));
        assertEquals("USERS", fk1.getReferencedTable());
        assertEquals("USER_ID", fk1.getReferencedColumns().get(0));
        assertNull(fk1.getOnDelete());

        ForeignKeyConstraint fk2 = empTable.getForeignKeys().get(1);
        assertEquals("DEPT_ID", fk2.getColumnNames().get(0));
        assertEquals("DEPARTMENTS", fk2.getReferencedTable());
        assertEquals("DEPT_ID", fk2.getReferencedColumns().get(0));
        assertEquals("CASCADE", fk2.getOnDelete());

        logger.info("Multiple foreign keys parsed correctly");
    }

    @Test
    public void testForeignKeyNotEnforcedOnInsert() {
        logger.info("Testing that FOREIGN KEY constraints are not enforced");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child (id INTEGER, parent_id INTEGER REFERENCES parent(id))");

        // Insert into parent
        engine.execute("INSERT INTO parent VALUES (1)");

        // Insert into child with valid foreign key - should work
        engine.execute("INSERT INTO child VALUES (100, 1)");

        // Insert into child with INVALID foreign key - should still work (not enforced)
        engine.execute("INSERT INTO child VALUES (200, 999)");

        ResultSet result = engine.executeQuery("SELECT * FROM child ORDER BY id");
        assertEquals(2, result.getRowCount());

        logger.info("Foreign key constraints are not enforced (as expected)");
    }

    @Test
    public void testForeignKeyNotEnforcedOnDelete() {
        logger.info("Testing that ON DELETE CASCADE is not enforced");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE child (
                id INTEGER,
                parent_id INTEGER,
                FOREIGN KEY (parent_id) REFERENCES parent(id) ON DELETE CASCADE
            )
            """);

        engine.execute("INSERT INTO parent VALUES (1)");
        engine.execute("INSERT INTO child VALUES (100, 1)");

        // Delete parent - ON DELETE CASCADE should NOT actually cascade (not enforced)
        engine.execute("DELETE FROM parent WHERE id = 1");

        ResultSet parentResult = engine.executeQuery("SELECT * FROM parent");
        assertEquals(0, parentResult.getRowCount());

        // Child row should still exist (cascade not enforced)
        ResultSet childResult = engine.executeQuery("SELECT * FROM child");
        assertEquals(1, childResult.getRowCount());

        logger.info("ON DELETE CASCADE is not enforced (as expected)");
    }

    @Test
    public void testForeignKeyNotEnforcedOnUpdate() {
        logger.info("Testing that ON UPDATE CASCADE is not enforced");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE child (
                id INTEGER,
                parent_id INTEGER,
                FOREIGN KEY (parent_id) REFERENCES parent(id) ON UPDATE CASCADE
            )
            """);

        engine.execute("INSERT INTO parent VALUES (1)");
        engine.execute("INSERT INTO child VALUES (100, 1)");

        // Update parent - ON UPDATE CASCADE should NOT actually cascade (not enforced)
        engine.execute("UPDATE parent SET id = 2 WHERE id = 1");

        ResultSet parentResult = engine.executeQuery("SELECT * FROM parent");
        assertEquals(2L, ((Number) parentResult.getRows().get(0).getValues().get(0)).longValue());

        // Child row should still have old parent_id (cascade not enforced)
        ResultSet childResult = engine.executeQuery("SELECT * FROM child");
        assertEquals(1L, ((Number) childResult.getRows().get(0).getValues().get(1)).longValue());

        logger.info("ON UPDATE CASCADE is not enforced (as expected)");
    }

    @Test
    public void testQualifiedTableNameInForeignKey() {
        logger.info("Testing qualified table name in FOREIGN KEY");

        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("CREATE TABLE other_schema.lookup (lookup_id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE main_table (
                id INTEGER,
                lookup_id INTEGER REFERENCES other_schema.lookup(lookup_id)
            )
            """);

        Table mainTable = engine.getCatalog().resolveTable("main_table");
        TableColumn lookupCol = mainTable.getColumn("lookup_id");

        assertTrue(lookupCol.hasForeignKey());
        assertEquals("OTHER_SCHEMA.LOOKUP", lookupCol.getReferencedTable());
        assertEquals("LOOKUP_ID", lookupCol.getReferencedColumn());

        logger.info("Qualified table name in foreign key works correctly");
    }

    @Test
    public void testUniqueConstraintWithForeignKey() {
        logger.info("Testing UNIQUE constraint combined with FOREIGN KEY");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE child (
                id INTEGER PRIMARY KEY,
                parent_id INTEGER UNIQUE REFERENCES parent(id)
            )
            """);

        Table childTable = engine.getCatalog().resolveTable("child");
        TableColumn parentIdCol = childTable.getColumn("parent_id");

        assertTrue(parentIdCol.isUnique());
        assertTrue(parentIdCol.hasForeignKey());
        assertEquals("PARENT", parentIdCol.getReferencedTable());

        logger.info("UNIQUE and FOREIGN KEY combination works correctly");
    }

    @Test
    public void testForeignKeyWithNotNull() {
        logger.info("Testing FOREIGN KEY with NOT NULL constraint");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE child (
                id INTEGER PRIMARY KEY,
                parent_id INTEGER NOT NULL REFERENCES parent(id)
            )
            """);

        Table childTable = engine.getCatalog().resolveTable("child");
        TableColumn parentIdCol = childTable.getColumn("parent_id");

        assertFalse(parentIdCol.isNullable());
        assertTrue(parentIdCol.hasForeignKey());

        logger.info("NOT NULL with FOREIGN KEY works correctly");
    }

    @Test
    public void testShowColumnsDisplaysForeignKey() {
        logger.info("Testing SHOW COLUMNS includes foreign key information");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child (id INTEGER, parent_id INTEGER REFERENCES parent(id))");

        ResultSet columns = engine.executeQuery("SHOW COLUMNS IN TABLE child");
        assertNotNull(columns);
        assertTrue(columns.getRowCount() >= 2);

        logger.info("SHOW COLUMNS works with foreign key columns");
    }

    @Test
    public void testPrimaryKeyWithRely() {
        logger.info("Testing PRIMARY KEY with RELY");

        engine.execute("CREATE TABLE test_rely (id INTEGER PRIMARY KEY RELY, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test_rely");
        TableColumn idCol = table.getColumn("id");

        assertTrue(idCol.isPrimaryKey());
        assertNotNull(idCol.getRely());
        assertTrue(idCol.getRely());

        logger.info("PRIMARY KEY RELY parsed correctly");
    }

    @Test
    public void testPrimaryKeyWithNorely() {
        logger.info("Testing PRIMARY KEY with NORELY");

        engine.execute("CREATE TABLE test_norely (id INTEGER PRIMARY KEY NORELY, name VARCHAR)");

        Table table = engine.getCatalog().resolveTable("test_norely");
        TableColumn idCol = table.getColumn("id");

        assertTrue(idCol.isPrimaryKey());
        assertNotNull(idCol.getRely());
        assertFalse(idCol.getRely());

        logger.info("PRIMARY KEY NORELY parsed correctly");
    }

    @Test
    public void testUniqueWithRely() {
        logger.info("Testing UNIQUE with RELY");

        engine.execute("CREATE TABLE test_unique_rely (id INTEGER, email VARCHAR UNIQUE RELY)");

        Table table = engine.getCatalog().resolveTable("test_unique_rely");
        TableColumn emailCol = table.getColumn("email");

        assertTrue(emailCol.isUnique());
        assertNotNull(emailCol.getRely());
        assertTrue(emailCol.getRely());

        logger.info("UNIQUE RELY parsed correctly");
    }

    @Test
    public void testNotNullWithNorely() {
        logger.info("Testing NOT NULL with NORELY");

        engine.execute("CREATE TABLE test_notnull (id INTEGER, name VARCHAR NOT NULL NORELY)");

        Table table = engine.getCatalog().resolveTable("test_notnull");
        TableColumn nameCol = table.getColumn("name");

        assertFalse(nameCol.isNullable());
        assertNotNull(nameCol.getRely());
        assertFalse(nameCol.getRely());

        logger.info("NOT NULL NORELY parsed correctly");
    }

    @Test
    public void testForeignKeyWithRely() {
        logger.info("Testing FOREIGN KEY with RELY");

        engine.execute("CREATE TABLE parent_rely (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child_rely (id INTEGER, parent_id INTEGER REFERENCES parent_rely(id) RELY)");

        Table childTable = engine.getCatalog().resolveTable("child_rely");
        TableColumn parentIdCol = childTable.getColumn("parent_id");

        assertTrue(parentIdCol.hasForeignKey());
        assertEquals("PARENT_RELY", parentIdCol.getReferencedTable());
        assertNotNull(parentIdCol.getRely());
        assertTrue(parentIdCol.getRely());

        logger.info("FOREIGN KEY RELY parsed correctly");
    }

    @Test
    public void testForeignKeyWithNorely() {
        logger.info("Testing FOREIGN KEY with NORELY");

        engine.execute("CREATE TABLE parent_norely (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child_norely (id INTEGER, parent_id INTEGER REFERENCES parent_norely(id) NORELY)");

        Table childTable = engine.getCatalog().resolveTable("child_norely");
        TableColumn parentIdCol = childTable.getColumn("parent_id");

        assertTrue(parentIdCol.hasForeignKey());
        assertNotNull(parentIdCol.getRely());
        assertFalse(parentIdCol.getRely());

        logger.info("FOREIGN KEY NORELY parsed correctly");
    }

    @Test
    public void testTableLevelForeignKeyWithRely() {
        logger.info("Testing table-level FOREIGN KEY with RELY");

        engine.execute("CREATE TABLE parent_tbl (id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE child_tbl (
                id INTEGER,
                parent_id INTEGER,
                FOREIGN KEY (parent_id) REFERENCES parent_tbl(id) RELY
            )
            """);

        Table childTable = engine.getCatalog().resolveTable("child_tbl");
        assertEquals(1, childTable.getForeignKeys().size());

        ForeignKeyConstraint fk = childTable.getForeignKeys().get(0);
        assertNotNull(fk.getRely());
        assertTrue(fk.getRely());

        logger.info("Table-level FOREIGN KEY RELY parsed correctly");
    }

    @Test
    public void testTableLevelForeignKeyWithNorely() {
        logger.info("Testing table-level FOREIGN KEY with NORELY");

        engine.execute("CREATE TABLE accounts_tbl (id INTEGER PRIMARY KEY)");
        engine.execute("""
            CREATE TABLE transactions_tbl (
                id INTEGER,
                wallet_id INTEGER,
                CONSTRAINT fk_account FOREIGN KEY (wallet_id) REFERENCES accounts_tbl(id) ON DELETE CASCADE NORELY
            )
            """);

        Table txnTable = engine.getCatalog().resolveTable("transactions_tbl");
        ForeignKeyConstraint fk = txnTable.getForeignKeys().get(0);

        assertEquals("FK_ACCOUNT", fk.getConstraintName());
        assertEquals("CASCADE", fk.getOnDelete());
        assertNotNull(fk.getRely());
        assertFalse(fk.getRely());

        logger.info("Table-level FOREIGN KEY NORELY with ON DELETE CASCADE parsed correctly");
    }

    @Test
    public void testTableLevelPrimaryKeyWithRely() {
        logger.info("Testing table-level PRIMARY KEY with RELY");

        engine.execute("""
            CREATE TABLE compound_key (
                first_name VARCHAR,
                last_name VARCHAR,
                PRIMARY KEY (first_name, last_name) RELY
            )
            """);

        Table table = engine.getCatalog().resolveTable("compound_key");
        assertEquals(2, table.getPrimaryKeys().size());
        assertTrue(table.getPrimaryKeys().contains("FIRST_NAME"));
        assertTrue(table.getPrimaryKeys().contains("LAST_NAME"));

        logger.info("Table-level PRIMARY KEY RELY parsed correctly");
    }

    @Test
    public void testConstraintWithoutRelyOption() {
        logger.info("Testing constraints without RELY/NORELY (should be null)");

        engine.execute("CREATE TABLE no_rely_option (id INTEGER PRIMARY KEY, name VARCHAR UNIQUE)");

        Table table = engine.getCatalog().resolveTable("no_rely_option");
        TableColumn idCol = table.getColumn("id");
        TableColumn nameCol = table.getColumn("name");

        assertTrue(idCol.isPrimaryKey());
        assertNull(idCol.getRely());

        assertTrue(nameCol.isUnique());
        assertNull(nameCol.getRely());

        logger.info("Constraints without RELY/NORELY have null rely value");
    }

    @Test
    public void testMixedRelyOptions() {
        logger.info("Testing multiple constraints with mixed RELY/NORELY");

        engine.execute("CREATE TABLE parent_mixed (id INTEGER PRIMARY KEY RELY)");
        engine.execute("""
            CREATE TABLE mixed_rely (
                id INTEGER PRIMARY KEY RELY,
                code VARCHAR UNIQUE NORELY,
                parent_id INTEGER REFERENCES parent_mixed(id) RELY,
                status VARCHAR NOT NULL
            )
            """);

        Table table = engine.getCatalog().resolveTable("mixed_rely");

        TableColumn idCol = table.getColumn("id");
        assertTrue(idCol.isPrimaryKey());
        assertTrue(idCol.getRely());

        TableColumn codeCol = table.getColumn("code");
        assertTrue(codeCol.isUnique());
        assertFalse(codeCol.getRely());

        TableColumn parentIdCol = table.getColumn("parent_id");
        assertTrue(parentIdCol.hasForeignKey());
        assertTrue(parentIdCol.getRely());

        TableColumn statusCol = table.getColumn("status");
        assertFalse(statusCol.isNullable());
        assertNull(statusCol.getRely());

        logger.info("Mixed RELY/NORELY options work correctly");
    }

    @Test
    public void testRelyNotEnforced() {
        logger.info("Testing that RELY/NORELY is not enforced (metadata only)");

        engine.execute("CREATE TABLE parent_enforce (id INTEGER PRIMARY KEY RELY)");
        engine.execute("CREATE TABLE child_enforce (id INTEGER, parent_id INTEGER REFERENCES parent_enforce(id) RELY)");

        // Even with RELY, foreign key is not enforced
        engine.execute("INSERT INTO child_enforce VALUES (1, 999)");

        ResultSet result = engine.executeQuery("SELECT * FROM child_enforce");
        assertEquals(1, result.getRowCount());

        logger.info("RELY/NORELY is metadata only and not enforced (as expected)");
    }
}
