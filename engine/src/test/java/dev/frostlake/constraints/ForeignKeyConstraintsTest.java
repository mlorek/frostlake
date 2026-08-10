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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FOREIGN KEY / PRIMARY KEY / UNIQUE constraint metadata (parsed, not enforced), asserted through
 * the SQL surface — {@code SHOW IMPORTED KEYS}, {@code SHOW PRIMARY KEYS}, {@code SHOW UNIQUE KEYS}
 * and {@code DESCRIBE TABLE} — so every check runs against whichever engine executed the DDL,
 * embedded or live. SHOW … KEYS reports one row per key COLUMN; an unspecified referential action
 * reads {@code NO ACTION} and an unset RELY reads {@code false}.
 */
public class ForeignKeyConstraintsTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(ForeignKeyConstraintsTest.class);

    private ResultSet importedKeys(final String childTable) {
        return engine.executeQuery("SHOW IMPORTED KEYS IN TABLE " + childTable);
    }

    // ---- foreign-key metadata --------------------------------------------------

    @Test
    public void testColumnLevelForeignKey() {
        logger.info("Testing column-level FOREIGN KEY constraint");

        engine.execute("CREATE TABLE parent_table (id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE child_table (id INTEGER, parent_id INTEGER REFERENCES parent_table(id))");

        final ResultSet keys = importedKeys("child_table");
        final Row fk = soleRowWhere(keys, "fk_column_name", "PARENT_ID");
        assertEquals("PARENT_TABLE", cell(keys, fk, "pk_table_name"));
        assertEquals("ID", cell(keys, fk, "pk_column_name"));
        assertEquals("NO ACTION", cell(keys, fk, "delete_rule"));
        assertEquals("NO ACTION", cell(keys, fk, "update_rule"));

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

        // A referential action other than NO ACTION silently drops the WHOLE constraint
        // (live-verified: the CREATE succeeds, but the key appears in neither SHOW IMPORTED
        // KEYS nor GET_DDL).
        assertEquals(0, importedKeys("employees").getRowCount());

        logger.info("Foreign key with referential actions dropped, as on a real account");
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

        final ResultSet keys = importedKeys("orders");
        assertEquals(1, keys.getRowCount());
        final Row fk = keys.getRows().get(0);
        // Declared without CONSTRAINT <name>, so Snowflake auto-names it SYS_CONSTRAINT_<uuid>.
        final String fkName = cell(keys, fk, "fk_name");
        assertTrue(fkName.startsWith("SYS_CONSTRAINT_"), fkName);
        assertEquals("CUSTOMER_ID", cell(keys, fk, "fk_column_name"));
        assertEquals("CUSTOMERS", cell(keys, fk, "pk_table_name"));
        assertEquals("CUSTOMER_ID", cell(keys, fk, "pk_column_name"));
        assertEquals("1", cell(keys, fk, "key_sequence"));

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

        final ResultSet keys = importedKeys("inventory");
        final Row fk = soleRowWhere(keys, "fk_name", "FK_PRODUCT");
        assertEquals("PRODUCT_ID", cell(keys, fk, "fk_column_name"));
        assertEquals("PRODUCTS", cell(keys, fk, "pk_table_name"));
        assertEquals("PRODUCT_ID", cell(keys, fk, "pk_column_name"));

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

        // One row per key column, sharing the constraint's fk_name, numbered by key_sequence.
        final ResultSet keys = importedKeys("contact");
        assertEquals(2, keys.getRowCount());
        final Row first = soleRowWhere(keys, "key_sequence", "1");
        final Row second = soleRowWhere(keys, "key_sequence", "2");
        assertEquals("FIRST_NAME", cell(keys, first, "fk_column_name"));
        assertEquals("FIRST_NAME", cell(keys, first, "pk_column_name"));
        assertEquals("LAST_NAME", cell(keys, second, "fk_column_name"));
        assertEquals("LAST_NAME", cell(keys, second, "pk_column_name"));
        assertEquals("PERSON", cell(keys, first, "pk_table_name"));
        assertEquals("PERSON", cell(keys, second, "pk_table_name"));
        assertEquals(cell(keys, first, "fk_name"), cell(keys, second, "fk_name"));

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

        // SET NULL is not a supported action, so the whole constraint is silently dropped.
        assertEquals(0, importedKeys("transactions").getRowCount());

        logger.info("ON DELETE SET NULL drops the constraint, as on a real account");
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

        // RESTRICT is not a supported action, so the whole constraint is silently dropped.
        assertEquals(0, importedKeys("items").getRowCount());

        logger.info("ON UPDATE RESTRICT drops the constraint, as on a real account");
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

        final ResultSet keys = importedKeys("purchases");
        final Row fk = soleRowWhere(keys, "fk_column_name", "VENDOR_ID");
        assertEquals("NO ACTION", cell(keys, fk, "delete_rule"));

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

        // SET DEFAULT is not a supported action, so the whole constraint is silently dropped.
        assertEquals(0, importedKeys("members").getRowCount());

        logger.info("ON DELETE SET DEFAULT drops the constraint, as on a real account");
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

        // The plain key survives; the ON DELETE CASCADE one is silently dropped whole.
        final ResultSet keys = importedKeys("employees");
        assertEquals(1, keys.getRowCount());

        final Row managerFk = soleRowWhere(keys, "fk_column_name", "MANAGER_ID");
        assertEquals("USERS", cell(keys, managerFk, "pk_table_name"));
        assertEquals("USER_ID", cell(keys, managerFk, "pk_column_name"));
        assertEquals("NO ACTION", cell(keys, managerFk, "delete_rule"));

        logger.info("Only the action-free foreign key is kept, as on a real account");
    }

    // ---- non-enforcement (already SQL-visible) ---------------------------------

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

        final ResultSet result = engine.executeQuery("SELECT * FROM child ORDER BY id");
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

        final ResultSet parentResult = engine.executeQuery("SELECT * FROM parent");
        assertEquals(0, parentResult.getRowCount());

        // Child row should still exist (cascade not enforced)
        final ResultSet childResult = engine.executeQuery("SELECT * FROM child");
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

        final ResultSet parentResult = engine.executeQuery("SELECT * FROM parent");
        assertEquals(2L, ((Number) parentResult.getRows().get(0).getValues().get(0)).longValue());

        // Child row should still have old parent_id (cascade not enforced)
        final ResultSet childResult = engine.executeQuery("SELECT * FROM child");
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

        final ResultSet keys = importedKeys("main_table");
        final Row fk = soleRowWhere(keys, "fk_column_name", "LOOKUP_ID");
        assertEquals("OTHER_SCHEMA", cell(keys, fk, "pk_schema_name"));
        assertEquals("LOOKUP", cell(keys, fk, "pk_table_name"));
        assertEquals("LOOKUP_ID", cell(keys, fk, "pk_column_name"));

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

        final ResultSet unique = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE child");
        soleRowWhere(unique, "column_name", "PARENT_ID");

        final ResultSet keys = importedKeys("child");
        final Row fk = soleRowWhere(keys, "fk_column_name", "PARENT_ID");
        assertEquals("PARENT", cell(keys, fk, "pk_table_name"));

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

        assertEquals("N", describeCell("child", "PARENT_ID", "null?"));
        final ResultSet keys = importedKeys("child");
        soleRowWhere(keys, "fk_column_name", "PARENT_ID");

        logger.info("NOT NULL with FOREIGN KEY works correctly");
    }

    @Test
    public void testShowColumnsDisplaysForeignKey() {
        logger.info("Testing SHOW COLUMNS includes foreign key information");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child (id INTEGER, parent_id INTEGER REFERENCES parent(id))");

        final ResultSet columns = engine.executeQuery("SHOW COLUMNS IN TABLE child");
        assertNotNull(columns);
        assertTrue(columns.getRowCount() >= 2);

        logger.info("SHOW COLUMNS works with foreign key columns");
    }

    // ---- RELY / NORELY metadata -------------------------------------------------

    @Test
    public void testPrimaryKeyWithRely() {
        logger.info("Testing PRIMARY KEY with RELY");

        engine.execute("CREATE TABLE test_rely (id INTEGER PRIMARY KEY RELY, name VARCHAR)");

        final ResultSet pk = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE test_rely");
        final Row idRow = soleRowWhere(pk, "column_name", "ID");
        assertEquals("true", cell(pk, idRow, "rely"));

        logger.info("PRIMARY KEY RELY parsed correctly");
    }

    @Test
    public void testPrimaryKeyWithNorely() {
        logger.info("Testing PRIMARY KEY with NORELY");

        engine.execute("CREATE TABLE test_norely (id INTEGER PRIMARY KEY NORELY, name VARCHAR)");

        final ResultSet pk = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE test_norely");
        final Row idRow = soleRowWhere(pk, "column_name", "ID");
        assertEquals("false", cell(pk, idRow, "rely"));

        logger.info("PRIMARY KEY NORELY parsed correctly");
    }

    @Test
    public void testUniqueWithRely() {
        logger.info("Testing UNIQUE with RELY");

        engine.execute("CREATE TABLE test_unique_rely (id INTEGER, email VARCHAR UNIQUE RELY)");

        final ResultSet unique = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE test_unique_rely");
        final Row emailRow = soleRowWhere(unique, "column_name", "EMAIL");
        assertEquals("true", cell(unique, emailRow, "rely"));

        logger.info("UNIQUE RELY parsed correctly");
    }

    @Test
    public void testNotNullRejectsNorely() {
        logger.info("Testing NOT NULL rejects NORELY");

        // Live-verified: NORELY is not accepted on a column NOT NULL constraint.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE test_notnull (id INTEGER, name VARCHAR NOT NULL NORELY)");
            }
        });

        // NOT NULL alone still works.
        engine.execute("CREATE TABLE test_notnull_ok (id INTEGER, name VARCHAR NOT NULL)");
        assertEquals("N", describeCell("test_notnull_ok", "NAME", "null?"));

        logger.info("NOT NULL NORELY correctly rejected");
    }

    @Test
    public void testForeignKeyWithRely() {
        logger.info("Testing FOREIGN KEY with RELY");

        engine.execute("CREATE TABLE parent_rely (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child_rely (id INTEGER, parent_id INTEGER REFERENCES parent_rely(id) RELY)");

        final ResultSet keys = importedKeys("child_rely");
        final Row fk = soleRowWhere(keys, "fk_column_name", "PARENT_ID");
        assertEquals("PARENT_RELY", cell(keys, fk, "pk_table_name"));
        assertEquals("true", cell(keys, fk, "rely"));

        logger.info("FOREIGN KEY RELY parsed correctly");
    }

    @Test
    public void testForeignKeyWithNorely() {
        logger.info("Testing FOREIGN KEY with NORELY");

        engine.execute("CREATE TABLE parent_norely (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child_norely (id INTEGER, parent_id INTEGER REFERENCES parent_norely(id) NORELY)");

        final ResultSet keys = importedKeys("child_norely");
        final Row fk = soleRowWhere(keys, "fk_column_name", "PARENT_ID");
        assertEquals("false", cell(keys, fk, "rely"));

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

        final ResultSet keys = importedKeys("child_tbl");
        assertEquals(1, keys.getRowCount());
        assertEquals("true", cell(keys, keys.getRows().get(0), "rely"));

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

        // ON DELETE CASCADE drops the whole constraint, its NORELY with it.
        assertEquals(0, importedKeys("transactions_tbl").getRowCount());

        logger.info("Table-level FOREIGN KEY with ON DELETE CASCADE dropped, as on a real account");
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

        // A composite key is one row per column, numbered by key_sequence, sharing one constraint.
        final ResultSet pk = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE compound_key");
        assertEquals(2, pk.getRowCount());
        final Row first = soleRowWhere(pk, "key_sequence", "1");
        final Row second = soleRowWhere(pk, "key_sequence", "2");
        assertEquals("FIRST_NAME", cell(pk, first, "column_name"));
        assertEquals("LAST_NAME", cell(pk, second, "column_name"));
        assertEquals(cell(pk, first, "constraint_name"), cell(pk, second, "constraint_name"));

        logger.info("Table-level PRIMARY KEY RELY parsed correctly");
    }

    @Test
    public void testConstraintWithoutRelyOption() {
        logger.info("Testing constraints without RELY/NORELY (report false)");

        engine.execute("CREATE TABLE no_rely_option (id INTEGER PRIMARY KEY, name VARCHAR UNIQUE)");

        final ResultSet pk = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE no_rely_option");
        assertEquals("false", cell(pk, soleRowWhere(pk, "column_name", "ID"), "rely"));

        final ResultSet unique = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE no_rely_option");
        assertEquals("false", cell(unique, soleRowWhere(unique, "column_name", "NAME"), "rely"));

        logger.info("Constraints without RELY/NORELY report rely=false");
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

        final ResultSet pk = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE mixed_rely");
        assertEquals("true", cell(pk, soleRowWhere(pk, "column_name", "ID"), "rely"));

        final ResultSet unique = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE mixed_rely");
        assertEquals("false", cell(unique, soleRowWhere(unique, "column_name", "CODE"), "rely"));

        final ResultSet keys = importedKeys("mixed_rely");
        assertEquals("true", cell(keys, soleRowWhere(keys, "fk_column_name", "PARENT_ID"), "rely"));

        assertEquals("N", describeCell("mixed_rely", "STATUS", "null?"));

        logger.info("Mixed RELY/NORELY options work correctly");
    }

    @Test
    public void testRelyNotEnforced() {
        logger.info("Testing that RELY/NORELY is not enforced (metadata only)");

        engine.execute("CREATE TABLE parent_enforce (id INTEGER PRIMARY KEY RELY)");
        engine.execute("CREATE TABLE child_enforce (id INTEGER, parent_id INTEGER REFERENCES parent_enforce(id) RELY)");

        // Even with RELY, foreign key is not enforced
        engine.execute("INSERT INTO child_enforce VALUES (1, 999)");

        final ResultSet result = engine.executeQuery("SELECT * FROM child_enforce");
        assertEquals(1, result.getRowCount());

        logger.info("RELY/NORELY is metadata only and not enforced (as expected)");
    }
}
