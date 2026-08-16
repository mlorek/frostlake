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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The IF EXISTS clause of ALTER TABLE, asserted through the SQL surface — a forgiven statement
 * leaves no trace ({@code SHOW TABLES} finds nothing), an applied one shows its effect in
 * {@code DESCRIBE TABLE} rows and {@code SHOW TABLES} cells — so every check runs against
 * whichever engine executed the DDL, embedded or live.
 */
public class AlterTableIfExistsTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterTableIfExistsTest.class);

    private int tableCount(final String table) {
        return engine.executeQuery("SHOW TABLES LIKE '" + table + "'").getRowCount();
    }

    private ResultSet describe(final String table) {
        return engine.executeQuery("DESCRIBE TABLE " + table);
    }

    private boolean hasColumn(final String table, final String column) {
        return !rowsWhere(describe(table), "name", column).isEmpty();
    }

    @Test
    public void testAlterTableRenameWithoutIfExists() {
        logger.info("Testing ALTER TABLE RENAME TO without IF EXISTS on non-existent table");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE non_existent_table RENAME TO new_table");
            }
        });

        logger.info("ALTER TABLE without IF EXISTS correctly throws exception");
    }

    @Test
    public void testAlterTableRenameWithIfExists() {
        logger.info("Testing ALTER TABLE RENAME TO with IF EXISTS on non-existent table");

        engine.execute("ALTER TABLE IF EXISTS non_existent_table RENAME TO new_table");

        logger.info("ALTER TABLE IF EXISTS on non-existent table succeeds without error");
    }

    @Test
    public void testAlterTableAddColumnWithoutIfExists() {
        logger.info("Testing ALTER TABLE ADD COLUMN without IF EXISTS on non-existent table");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE non_existent_table ADD COLUMN new_col VARCHAR");
            }
        });

        logger.info("ALTER TABLE ADD COLUMN without IF EXISTS correctly throws exception");
    }

    @Test
    public void testAlterTableAddColumnWithIfExists() {
        logger.info("Testing ALTER TABLE ADD COLUMN with IF EXISTS on non-existent table");

        engine.execute("ALTER TABLE IF EXISTS non_existent_table ADD COLUMN new_col VARCHAR");

        logger.info("ALTER TABLE IF EXISTS ADD COLUMN on non-existent table succeeds without error");
    }

    @Test
    public void testAlterTableDropColumnWithIfExists() {
        logger.info("Testing ALTER TABLE DROP COLUMN with IF EXISTS on non-existent table");

        engine.execute("ALTER TABLE IF EXISTS non_existent_table DROP COLUMN old_col");

        logger.info("ALTER TABLE IF EXISTS DROP COLUMN on non-existent table succeeds without error");
    }

    @Test
    public void testAlterTableRenameColumnWithIfExists() {
        logger.info("Testing ALTER TABLE RENAME COLUMN with IF EXISTS on non-existent table");

        engine.execute("ALTER TABLE IF EXISTS non_existent_table RENAME COLUMN old_col TO new_col");

        logger.info("ALTER TABLE IF EXISTS RENAME COLUMN on non-existent table succeeds without error");
    }

    @Test
    public void testAlterTableCommentWithIfExists() {
        logger.info("Testing ALTER TABLE COMMENT with IF EXISTS on non-existent table");

        engine.execute("ALTER TABLE IF EXISTS non_existent_table SET COMMENT = 'Some comment'");

        logger.info("ALTER TABLE IF EXISTS COMMENT on non-existent table succeeds without error");
    }

    @Test
    public void testAlterTableIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE IF EXISTS on existing table");

        engine.execute("CREATE TABLE existing_table (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE IF EXISTS existing_table ADD COLUMN age INTEGER");

        assertEquals(3, describe("existing_table").getRowCount());
        assertTrue(hasColumn("existing_table", "AGE"));

        logger.info("ALTER TABLE IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableRenameIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE RENAME TO IF EXISTS on existing table");

        engine.execute("CREATE TABLE old_name (id INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS old_name RENAME TO new_name");

        assertEquals(0, tableCount("old_name"));
        assertEquals(1, tableCount("new_name"));

        logger.info("ALTER TABLE RENAME TO IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableDropColumnIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE DROP COLUMN IF EXISTS on existing table");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS test_table DROP COLUMN age");

        assertEquals(2, describe("test_table").getRowCount());

        logger.info("ALTER TABLE DROP COLUMN IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableRenameColumnIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE RENAME COLUMN IF EXISTS on existing table");

        engine.execute("CREATE TABLE test_table (id INTEGER, old_name VARCHAR)");
        engine.execute("ALTER TABLE IF EXISTS test_table RENAME COLUMN old_name TO new_name");

        assertTrue(hasColumn("test_table", "NEW_NAME"));

        logger.info("ALTER TABLE RENAME COLUMN IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableCommentIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE COMMENT IF EXISTS on existing table");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS test_table SET COMMENT = 'Test comment'");

        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'test_table'");
        assertEquals("Test comment",
            cell(tables, soleRowWhere(tables, "name", "TEST_TABLE"), "comment"));

        logger.info("ALTER TABLE COMMENT IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableIfExistsWithQualifiedName() {
        logger.info("Testing ALTER TABLE IF EXISTS with schema-qualified name");

        engine.execute("CREATE SCHEMA alter_ie_schema");
        engine.execute("ALTER TABLE IF EXISTS alter_ie_schema.non_existent RENAME TO new_table");

        logger.info("ALTER TABLE IF EXISTS with qualified name on non-existent table succeeds");
    }

    @Test
    public void testAlterTableIfExistsMultipleOperations() {
        logger.info("Testing multiple ALTER TABLE IF EXISTS operations");

        engine.execute("ALTER TABLE IF EXISTS table1 ADD COLUMN col1 VARCHAR");
        engine.execute("ALTER TABLE IF EXISTS table2 DROP COLUMN col2");
        engine.execute("ALTER TABLE IF EXISTS table3 RENAME TO table4");
        engine.execute("ALTER TABLE IF EXISTS table5 RENAME COLUMN old TO new");
        engine.execute("ALTER TABLE IF EXISTS table6 SET COMMENT = 'Comment'");

        logger.info("Multiple ALTER TABLE IF EXISTS operations on non-existent tables succeed");
    }

    @Test
    public void testAlterTableIfExistsDoesNotCreateTable() {
        logger.info("Testing that ALTER TABLE IF EXISTS does not create table");

        engine.execute("ALTER TABLE IF EXISTS phantom_table ADD COLUMN col1 VARCHAR");

        assertEquals(0, tableCount("phantom_table"));

        logger.info("ALTER TABLE IF EXISTS correctly does not create non-existent table");
    }

    @Test
    public void testAlterTableIfExistsWithDataInTable() {
        logger.info("Testing ALTER TABLE IF EXISTS with data in table");

        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 'Alice')");
        engine.execute("INSERT INTO customers VALUES (2, 'Bob')");

        engine.execute("ALTER TABLE IF EXISTS customers ADD COLUMN email VARCHAR");

        final ResultSet rs = engine.executeQuery("SELECT * FROM customers");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        assertEquals(3, describe("customers").getRowCount());

        logger.info("ALTER TABLE IF EXISTS on table with data works correctly");
    }
}
