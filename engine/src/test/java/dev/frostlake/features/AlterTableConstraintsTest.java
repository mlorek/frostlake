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
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for ALTER TABLE ADD/DROP constraint commands
 * Allows adding and dropping PRIMARY KEY, UNIQUE, and FOREIGN KEY constraints
 */
public class AlterTableConstraintsTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterTableConstraintsTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for ALTER TABLE constraints tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testAlterTableAddPrimaryKey() {
        logger.info("Testing ALTER TABLE ADD PRIMARY KEY");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("ALTER TABLE users ADD PRIMARY KEY (id)");

        Table table = engine.getCatalog().resolveTable("users");
        TableColumn idColumn = table.getColumn("id");
        assertTrue(idColumn.isPrimaryKey());

        logger.info("ALTER TABLE ADD PRIMARY KEY works correctly");
    }

    @Test
    public void testAlterTableAddCompositePrimaryKey() {
        logger.info("Testing ALTER TABLE ADD composite PRIMARY KEY");

        engine.execute("CREATE TABLE order_items (order_id INTEGER, item_id INTEGER, quantity INTEGER)");
        engine.execute("ALTER TABLE order_items ADD PRIMARY KEY (order_id, item_id)");

        Table table = engine.getCatalog().resolveTable("order_items");
        assertTrue(table.getColumn("order_id").isPrimaryKey());
        assertTrue(table.getColumn("item_id").isPrimaryKey());
        assertFalse(table.getColumn("quantity").isPrimaryKey());

        logger.info("ALTER TABLE ADD composite PRIMARY KEY works correctly");
    }

    @Test
    public void testAlterTableAddNamedPrimaryKey() {
        logger.info("Testing ALTER TABLE ADD named PRIMARY KEY");

        engine.execute("CREATE TABLE products (product_id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE products ADD CONSTRAINT pk_products PRIMARY KEY (product_id)");

        Table table = engine.getCatalog().resolveTable("products");
        assertTrue(table.getColumn("product_id").isPrimaryKey());

        logger.info("ALTER TABLE ADD named PRIMARY KEY works correctly");
    }

    @Test
    public void testAlterTableAddUnique() {
        logger.info("Testing ALTER TABLE ADD UNIQUE");

        engine.execute("CREATE TABLE users (id INTEGER, email VARCHAR, phone VARCHAR)");
        engine.execute("ALTER TABLE users ADD UNIQUE (email)");

        Table table = engine.getCatalog().resolveTable("users");
        assertTrue(table.getColumn("email").isUnique());
        assertFalse(table.getColumn("phone").isUnique());

        logger.info("ALTER TABLE ADD UNIQUE works correctly");
    }

    @Test
    public void testAlterTableAddCompositeUnique() {
        logger.info("Testing ALTER TABLE ADD composite UNIQUE");

        engine.execute("CREATE TABLE registrations (user_id INTEGER, event_id INTEGER, timestamp VARCHAR)");
        engine.execute("ALTER TABLE registrations ADD UNIQUE (user_id, event_id)");

        Table table = engine.getCatalog().resolveTable("registrations");
        assertTrue(table.getColumn("user_id").isUnique());
        assertTrue(table.getColumn("event_id").isUnique());
        assertFalse(table.getColumn("timestamp").isUnique());

        logger.info("ALTER TABLE ADD composite UNIQUE works correctly");
    }

    @Test
    public void testAlterTableAddNamedUnique() {
        logger.info("Testing ALTER TABLE ADD named UNIQUE");

        engine.execute("CREATE TABLE employees (id INTEGER, email VARCHAR)");
        engine.execute("ALTER TABLE employees ADD CONSTRAINT uk_email UNIQUE (email)");

        Table table = engine.getCatalog().resolveTable("employees");
        assertTrue(table.getColumn("email").isUnique());

        logger.info("ALTER TABLE ADD named UNIQUE works correctly");
    }

    @Test
    public void testAlterTableAddForeignKey() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY");

        engine.execute("CREATE TABLE departments (dept_id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE employees (emp_id INTEGER, name VARCHAR, dept_id INTEGER)");
        engine.execute("ALTER TABLE employees ADD FOREIGN KEY (dept_id) REFERENCES departments (dept_id)");

        Table table = engine.getCatalog().resolveTable("employees");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertEquals(1, fk.getColumnNames().size());
        assertTrue(fk.getColumnNames().contains("dept_id"));
        assertTrue(fk.getReferencedTable().equalsIgnoreCase("departments"));

        logger.info("ALTER TABLE ADD FOREIGN KEY works correctly");
    }

    @Test
    public void testAlterTableAddNamedForeignKey() {
        logger.info("Testing ALTER TABLE ADD named FOREIGN KEY");

        engine.execute("CREATE TABLE countries (country_id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE cities (city_id INTEGER, name VARCHAR, country_id INTEGER)");
        engine.execute("ALTER TABLE cities ADD CONSTRAINT fk_cities_countries FOREIGN KEY (country_id) REFERENCES countries (country_id)");

        Table table = engine.getCatalog().resolveTable("cities");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertEquals("fk_cities_countries", fk.getConstraintName());

        logger.info("ALTER TABLE ADD named FOREIGN KEY works correctly");
    }

    @Test
    public void testAlterTableAddForeignKeyWithOnDelete() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON DELETE");

        engine.execute("CREATE TABLE users (user_id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE posts (post_id INTEGER, user_id INTEGER)");
        engine.execute("ALTER TABLE posts ADD FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE");

        Table table = engine.getCatalog().resolveTable("posts");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertEquals("CASCADE", fk.getOnDelete());

        logger.info("ALTER TABLE ADD FOREIGN KEY with ON DELETE works correctly");
    }

    @Test
    public void testAlterTableAddForeignKeyWithOnUpdate() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON UPDATE");

        engine.execute("CREATE TABLE categories (cat_id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE products (prod_id INTEGER, cat_id INTEGER)");
        engine.execute("ALTER TABLE products ADD FOREIGN KEY (cat_id) REFERENCES categories (cat_id) ON UPDATE CASCADE");

        Table table = engine.getCatalog().resolveTable("products");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertEquals("CASCADE", fk.getOnUpdate());

        logger.info("ALTER TABLE ADD FOREIGN KEY with ON UPDATE works correctly");
    }

    @Test
    public void testAlterTableAddForeignKeyWithBothActions() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with both ON DELETE and ON UPDATE");

        engine.execute("CREATE TABLE orders (order_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE order_items (item_id INTEGER, order_id INTEGER)");
        engine.execute("ALTER TABLE order_items ADD FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE ON UPDATE CASCADE");

        Table table = engine.getCatalog().resolveTable("order_items");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertEquals("CASCADE", fk.getOnDelete());
        assertEquals("CASCADE", fk.getOnUpdate());

        logger.info("ALTER TABLE ADD FOREIGN KEY with both actions works correctly");
    }

    @Test
    public void testAlterTableAddCompositeForeignKey() {
        logger.info("Testing ALTER TABLE ADD composite FOREIGN KEY");

        engine.execute("CREATE TABLE order_headers (order_id INTEGER, customer_id INTEGER, PRIMARY KEY (order_id, customer_id))");
        engine.execute("CREATE TABLE shipments (ship_id INTEGER, order_id INTEGER, customer_id INTEGER)");
        engine.execute("ALTER TABLE shipments ADD FOREIGN KEY (order_id, customer_id) REFERENCES order_headers (order_id, customer_id)");

        Table table = engine.getCatalog().resolveTable("shipments");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertEquals(2, fk.getColumnNames().size());
        assertTrue(fk.getColumnNames().contains("order_id"));
        assertTrue(fk.getColumnNames().contains("customer_id"));

        logger.info("ALTER TABLE ADD composite FOREIGN KEY works correctly");
    }

    @Test
    public void testAlterTableDropConstraint() {
        logger.info("Testing ALTER TABLE DROP CONSTRAINT");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child (id INTEGER, parent_id INTEGER)");
        engine.execute("ALTER TABLE child ADD CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent (id)");

        Table table = engine.getCatalog().resolveTable("child");
        assertEquals(1, table.getForeignKeys().size());

        engine.execute("ALTER TABLE child DROP CONSTRAINT fk_child_parent");

        table = engine.getCatalog().resolveTable("child");
        assertEquals(0, table.getForeignKeys().size());

        logger.info("ALTER TABLE DROP CONSTRAINT works correctly");
    }

    @Test
    public void testAlterTableAddMultipleConstraints() {
        logger.info("Testing ALTER TABLE with multiple constraint operations");

        engine.execute("CREATE TABLE users (user_id INTEGER, email VARCHAR, phone VARCHAR)");
        engine.execute("ALTER TABLE users ADD PRIMARY KEY (user_id)");
        engine.execute("ALTER TABLE users ADD UNIQUE (email)");
        engine.execute("ALTER TABLE users ADD UNIQUE (phone)");

        Table table = engine.getCatalog().resolveTable("users");
        assertTrue(table.getColumn("user_id").isPrimaryKey());
        assertTrue(table.getColumn("email").isUnique());
        assertTrue(table.getColumn("phone").isUnique());

        logger.info("ALTER TABLE with multiple constraint operations works correctly");
    }

    @Test
    public void testAlterTableConstraintWithQualifiedName() {
        logger.info("Testing ALTER TABLE ADD constraint with qualified table name");

        engine.execute("CREATE SCHEMA analytics");
        engine.execute("CREATE TABLE analytics.users (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE analytics.users ADD PRIMARY KEY (id)");

        Table table = engine.getCatalog().resolveTable("analytics.users");
        assertTrue(table.getColumn("id").isPrimaryKey());

        logger.info("ALTER TABLE ADD constraint with qualified name works correctly");
    }

    @Test
    public void testAlterTableConstraintWithIfExists() {
        logger.info("Testing ALTER TABLE IF EXISTS ADD constraint");

        engine.execute("CREATE TABLE test1 (id INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS test1 ADD PRIMARY KEY (id)");

        Table table = engine.getCatalog().resolveTable("test1");
        assertTrue(table.getColumn("id").isPrimaryKey());

        engine.execute("ALTER TABLE IF EXISTS nonexistent ADD PRIMARY KEY (id)");

        logger.info("ALTER TABLE IF EXISTS ADD constraint works correctly");
    }

    @Test
    public void testAlterTableMixedConstraintOperations() {
        logger.info("Testing mixed ALTER TABLE operations with constraints");

        engine.execute("CREATE TABLE teams (team_id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE teams ADD COLUMN city VARCHAR");
        engine.execute("ALTER TABLE teams ADD PRIMARY KEY (team_id)");
        engine.execute("ALTER TABLE teams ADD UNIQUE (name)");
        engine.execute("ALTER TABLE teams CLUSTER BY (city)");

        Table table = engine.getCatalog().resolveTable("teams");
        assertEquals(3, table.getColumns().size());
        assertTrue(table.getColumn("team_id").isPrimaryKey());
        assertTrue(table.getColumn("name").isUnique());
        assertEquals(1, table.getClusterKeys().size());

        logger.info("Mixed ALTER TABLE operations with constraints work correctly");
    }

    @Test
    public void testAlterTableAddForeignKeyWithSetNull() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON DELETE SET NULL");

        engine.execute("CREATE TABLE managers (mgr_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE employees (emp_id INTEGER, mgr_id INTEGER)");
        engine.execute("ALTER TABLE employees ADD FOREIGN KEY (mgr_id) REFERENCES managers (mgr_id) ON DELETE SET NULL");

        Table table = engine.getCatalog().resolveTable("employees");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertTrue(fk.getOnDelete().contains("NULL"));

        logger.info("ALTER TABLE ADD FOREIGN KEY with SET NULL works correctly");
    }

    @Test
    public void testAlterTableAddForeignKeyWithRestrict() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON DELETE RESTRICT");

        engine.execute("CREATE TABLE accounts (account_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE transactions (txn_id INTEGER, account_id INTEGER)");
        engine.execute("ALTER TABLE transactions ADD FOREIGN KEY (account_id) REFERENCES accounts (account_id) ON DELETE RESTRICT");

        Table table = engine.getCatalog().resolveTable("transactions");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertEquals("RESTRICT", fk.getOnDelete());

        logger.info("ALTER TABLE ADD FOREIGN KEY with RESTRICT works correctly");
    }

    @Test
    public void testAlterTableAddForeignKeyWithNoAction() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON UPDATE NO ACTION");

        engine.execute("CREATE TABLE vendors (vendor_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE products (prod_id INTEGER, vendor_id INTEGER)");
        engine.execute("ALTER TABLE products ADD FOREIGN KEY (vendor_id) REFERENCES vendors (vendor_id) ON UPDATE NO ACTION");

        Table table = engine.getCatalog().resolveTable("products");
        List<ForeignKeyConstraint> foreignKeys = table.getForeignKeys();
        assertEquals(1, foreignKeys.size());

        ForeignKeyConstraint fk = foreignKeys.get(0);
        assertTrue(fk.getOnUpdate().contains("ACTION"));

        logger.info("ALTER TABLE ADD FOREIGN KEY with NO ACTION works correctly");
    }
}
