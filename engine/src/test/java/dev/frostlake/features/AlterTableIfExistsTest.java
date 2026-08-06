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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for IF EXISTS clause in ALTER TABLE statement
 */
public class AlterTableIfExistsTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterTableIfExistsTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for ALTER TABLE IF EXISTS tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAlterTableRenameWithoutIfExists() {
        logger.info("Testing ALTER TABLE RENAME TO without IF EXISTS on non-existent table");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
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
            public void execute() throws Throwable {
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

        Table table = engine.getCatalog().resolveTable("EXISTING_TABLE");
        assertNotNull(table);
        assertEquals(3, table.getColumns().size());
        assertTrue(table.hasColumn("age"));

        logger.info("ALTER TABLE IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableRenameIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE RENAME TO IF EXISTS on existing table");

        engine.execute("CREATE TABLE old_name (id INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS old_name RENAME TO new_name");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.getCatalog().resolveTable("OLD_NAME");
            }
        });

        Table table = engine.getCatalog().resolveTable("NEW_NAME");
        assertNotNull(table);

        logger.info("ALTER TABLE RENAME TO IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableDropColumnIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE DROP COLUMN IF EXISTS on existing table");

        engine.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR, age INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS test_table DROP COLUMN age");

        Table table = engine.getCatalog().resolveTable("TEST_TABLE");
        assertNotNull(table);
        assertEquals(2, table.getColumns().size());

        logger.info("ALTER TABLE DROP COLUMN IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableRenameColumnIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE RENAME COLUMN IF EXISTS on existing table");

        engine.execute("CREATE TABLE test_table (id INTEGER, old_name VARCHAR)");
        engine.execute("ALTER TABLE IF EXISTS test_table RENAME COLUMN old_name TO new_name");

        Table table = engine.getCatalog().resolveTable("TEST_TABLE");
        assertNotNull(table);
        assertTrue(table.hasColumn("new_name"));

        logger.info("ALTER TABLE RENAME COLUMN IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableCommentIfExistsOnExistingTable() {
        logger.info("Testing ALTER TABLE COMMENT IF EXISTS on existing table");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS test_table SET COMMENT = 'Test comment'");

        Table table = engine.getCatalog().resolveTable("TEST_TABLE");
        assertNotNull(table);
        assertEquals("Test comment", table.getComment());

        logger.info("ALTER TABLE COMMENT IF EXISTS on existing table works correctly");
    }

    @Test
    public void testAlterTableIfExistsWithQualifiedName() {
        logger.info("Testing ALTER TABLE IF EXISTS with schema-qualified name");

        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("ALTER TABLE IF EXISTS test_schema.non_existent RENAME TO new_table");

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

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.getCatalog().resolveTable("PHANTOM_TABLE");
            }
        });

        logger.info("ALTER TABLE IF EXISTS correctly does not create non-existent table");
    }

    @Test
    public void testAlterTableIfExistsWithDataInTable() {
        logger.info("Testing ALTER TABLE IF EXISTS with data in table");

        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 'Alice')");
        engine.execute("INSERT INTO customers VALUES (2, 'Bob')");

        engine.execute("ALTER TABLE IF EXISTS customers ADD COLUMN email VARCHAR");

        ResultSet rs = engine.executeQuery("SELECT * FROM customers");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        Table table = engine.getCatalog().resolveTable("CUSTOMERS");
        assertEquals(3, table.getColumns().size());

        logger.info("ALTER TABLE IF EXISTS on table with data works correctly");
    }
}
