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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ALTER TABLE ADD/DROP constraint commands — PRIMARY KEY, UNIQUE and FOREIGN KEY — asserted
 * through the SQL surface: {@code DESCRIBE TABLE}'s "primary key"/"unique key" cells,
 * {@code SHOW PRIMARY KEYS}, {@code SHOW UNIQUE KEYS} and {@code SHOW IMPORTED KEYS}. A foreign
 * key declaring any referential action other than NO ACTION is dropped whole, silently, exactly
 * as on the CREATE TABLE paths.
 */
public class AlterTableConstraintsTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterTableConstraintsTest.class);

    private ResultSet importedKeys(final String childTable) {
        return engine.executeQuery("SHOW IMPORTED KEYS IN TABLE " + childTable);
    }

    @Test
    public void testAlterTableAddPrimaryKey() {
        logger.info("Testing ALTER TABLE ADD PRIMARY KEY");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR, email VARCHAR)");
        engine.execute("ALTER TABLE users ADD PRIMARY KEY (id)");

        assertEquals("Y", describeCell("users", "ID", "primary key"));
        assertEquals("N", describeCell("users", "NAME", "primary key"));

        logger.info("ALTER TABLE ADD PRIMARY KEY works correctly");
    }

    @Test
    public void testAlterTableAddCompositePrimaryKey() {
        logger.info("Testing ALTER TABLE ADD composite PRIMARY KEY");

        engine.execute("CREATE TABLE order_items (order_id INTEGER, item_id INTEGER, quantity INTEGER)");
        engine.execute("ALTER TABLE order_items ADD PRIMARY KEY (order_id, item_id)");

        assertEquals("Y", describeCell("order_items", "ORDER_ID", "primary key"));
        assertEquals("Y", describeCell("order_items", "ITEM_ID", "primary key"));
        assertEquals("N", describeCell("order_items", "QUANTITY", "primary key"));

        logger.info("ALTER TABLE ADD composite PRIMARY KEY works correctly");
    }

    @Test
    public void testAlterTableAddNamedPrimaryKey() {
        logger.info("Testing ALTER TABLE ADD named PRIMARY KEY");

        engine.execute("CREATE TABLE products (product_id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE products ADD CONSTRAINT pk_products PRIMARY KEY (product_id)");

        final ResultSet keys = engine.executeQuery("SHOW PRIMARY KEYS IN TABLE products");
        final Row key = soleRowWhere(keys, "column_name", "PRODUCT_ID");
        assertEquals("PK_PRODUCTS", cell(keys, key, "constraint_name"));

        logger.info("ALTER TABLE ADD named PRIMARY KEY works correctly");
    }

    @Test
    public void testAlterTableAddUnique() {
        logger.info("Testing ALTER TABLE ADD UNIQUE");

        engine.execute("CREATE TABLE users (id INTEGER, email VARCHAR, phone VARCHAR)");
        engine.execute("ALTER TABLE users ADD UNIQUE (email)");

        assertEquals("Y", describeCell("users", "EMAIL", "unique key"));
        assertEquals("N", describeCell("users", "PHONE", "unique key"));

        logger.info("ALTER TABLE ADD UNIQUE works correctly");
    }

    @Test
    public void testAlterTableAddCompositeUnique() {
        logger.info("Testing ALTER TABLE ADD composite UNIQUE");

        engine.execute("CREATE TABLE registrations (user_id INTEGER, event_id INTEGER, timestamp VARCHAR)");
        engine.execute("ALTER TABLE registrations ADD UNIQUE (user_id, event_id)");

        assertEquals("Y", describeCell("registrations", "USER_ID", "unique key"));
        assertEquals("Y", describeCell("registrations", "EVENT_ID", "unique key"));
        assertEquals("N", describeCell("registrations", "TIMESTAMP", "unique key"));

        logger.info("ALTER TABLE ADD composite UNIQUE works correctly");
    }

    @Test
    public void testAlterTableAddNamedUnique() {
        logger.info("Testing ALTER TABLE ADD named UNIQUE");

        engine.execute("CREATE TABLE employees (id INTEGER, email VARCHAR)");
        engine.execute("ALTER TABLE employees ADD CONSTRAINT uk_email UNIQUE (email)");

        final ResultSet keys = engine.executeQuery("SHOW UNIQUE KEYS IN TABLE employees");
        final Row key = soleRowWhere(keys, "column_name", "EMAIL");
        assertEquals("UK_EMAIL", cell(keys, key, "constraint_name"));

        logger.info("ALTER TABLE ADD named UNIQUE works correctly");
    }

    @Test
    public void testAlterTableAddForeignKey() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY");

        engine.execute("CREATE TABLE departments (dept_id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE employees (emp_id INTEGER, name VARCHAR, dept_id INTEGER)");
        engine.execute("ALTER TABLE employees ADD FOREIGN KEY (dept_id) REFERENCES departments (dept_id)");

        final ResultSet keys = importedKeys("employees");
        final Row fk = soleRowWhere(keys, "fk_column_name", "DEPT_ID");
        assertEquals("DEPARTMENTS", cell(keys, fk, "pk_table_name"));
        assertEquals("DEPT_ID", cell(keys, fk, "pk_column_name"));

        logger.info("ALTER TABLE ADD FOREIGN KEY works correctly");
    }

    @Test
    public void testAlterTableAddNamedForeignKey() {
        logger.info("Testing ALTER TABLE ADD named FOREIGN KEY");

        engine.execute("CREATE TABLE countries (country_id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE cities (city_id INTEGER, name VARCHAR, country_id INTEGER)");
        engine.execute("ALTER TABLE cities ADD CONSTRAINT fk_cities_countries FOREIGN KEY (country_id) REFERENCES countries (country_id)");

        final ResultSet keys = importedKeys("cities");
        final Row fk = soleRowWhere(keys, "fk_column_name", "COUNTRY_ID");
        assertEquals("FK_CITIES_COUNTRIES", cell(keys, fk, "fk_name"));

        logger.info("ALTER TABLE ADD named FOREIGN KEY works correctly");
    }

    @Test
    public void testAlterTableAddForeignKeyWithOnDelete() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON DELETE");

        engine.execute("CREATE TABLE users (user_id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE posts (post_id INTEGER, user_id INTEGER)");
        engine.execute("ALTER TABLE posts ADD FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE");

        // A referential action other than NO ACTION silently drops the whole constraint.
        assertEquals(0, importedKeys("posts").getRowCount());

        logger.info("ALTER TABLE ADD FOREIGN KEY with ON DELETE dropped the constraint, as live does");
    }

    @Test
    public void testAlterTableAddForeignKeyWithOnUpdate() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON UPDATE");

        engine.execute("CREATE TABLE categories (cat_id INTEGER PRIMARY KEY, name VARCHAR)");
        engine.execute("CREATE TABLE products (prod_id INTEGER, cat_id INTEGER)");
        engine.execute("ALTER TABLE products ADD FOREIGN KEY (cat_id) REFERENCES categories (cat_id) ON UPDATE CASCADE");

        assertEquals(0, importedKeys("products").getRowCount());

        logger.info("ALTER TABLE ADD FOREIGN KEY with ON UPDATE dropped the constraint, as live does");
    }

    @Test
    public void testAlterTableAddForeignKeyWithBothActions() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with both ON DELETE and ON UPDATE");

        engine.execute("CREATE TABLE orders (order_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE order_items (item_id INTEGER, order_id INTEGER)");
        engine.execute("ALTER TABLE order_items ADD FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE ON UPDATE CASCADE");

        assertEquals(0, importedKeys("order_items").getRowCount());

        logger.info("ALTER TABLE ADD FOREIGN KEY with both actions dropped the constraint, as live does");
    }

    @Test
    public void testAlterTableAddCompositeForeignKey() {
        logger.info("Testing ALTER TABLE ADD composite FOREIGN KEY");

        engine.execute("CREATE TABLE order_headers (order_id INTEGER, customer_id INTEGER, PRIMARY KEY (order_id, customer_id))");
        engine.execute("CREATE TABLE shipments (ship_id INTEGER, order_id INTEGER, customer_id INTEGER)");
        engine.execute("ALTER TABLE shipments ADD FOREIGN KEY (order_id, customer_id) REFERENCES order_headers (order_id, customer_id)");

        // One SHOW IMPORTED KEYS row per key column.
        final ResultSet keys = importedKeys("shipments");
        assertEquals(2, keys.getRowCount());
        final Row first = soleRowWhere(keys, "fk_column_name", "ORDER_ID");
        assertEquals("1", cell(keys, first, "key_sequence"));
        final Row second = soleRowWhere(keys, "fk_column_name", "CUSTOMER_ID");
        assertEquals("2", cell(keys, second, "key_sequence"));

        logger.info("ALTER TABLE ADD composite FOREIGN KEY works correctly");
    }

    @Test
    public void testAlterTableDropConstraint() {
        logger.info("Testing ALTER TABLE DROP CONSTRAINT");

        engine.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE child (id INTEGER, parent_id INTEGER)");
        engine.execute("ALTER TABLE child ADD CONSTRAINT fk_child_parent FOREIGN KEY (parent_id) REFERENCES parent (id)");

        assertEquals(1, importedKeys("child").getRowCount());

        engine.execute("ALTER TABLE child DROP CONSTRAINT fk_child_parent");

        assertEquals(0, importedKeys("child").getRowCount());

        logger.info("ALTER TABLE DROP CONSTRAINT works correctly");
    }

    @Test
    public void testAlterTableAddMultipleConstraints() {
        logger.info("Testing ALTER TABLE with multiple constraint operations");

        engine.execute("CREATE TABLE users (user_id INTEGER, email VARCHAR, phone VARCHAR)");
        engine.execute("ALTER TABLE users ADD PRIMARY KEY (user_id)");
        engine.execute("ALTER TABLE users ADD UNIQUE (email)");
        engine.execute("ALTER TABLE users ADD UNIQUE (phone)");

        assertEquals("Y", describeCell("users", "USER_ID", "primary key"));
        assertEquals("Y", describeCell("users", "EMAIL", "unique key"));
        assertEquals("Y", describeCell("users", "PHONE", "unique key"));

        logger.info("ALTER TABLE with multiple constraint operations works correctly");
    }

    @Test
    public void testAlterTableConstraintWithQualifiedName() {
        logger.info("Testing ALTER TABLE ADD constraint with qualified table name");

        engine.execute("CREATE SCHEMA analytics");
        engine.execute("CREATE TABLE analytics.users (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE analytics.users ADD PRIMARY KEY (id)");

        assertEquals("Y", describeCell("analytics.users", "ID", "primary key"));

        logger.info("ALTER TABLE ADD constraint with qualified name works correctly");
    }

    @Test
    public void testAlterTableConstraintWithIfExists() {
        logger.info("Testing ALTER TABLE IF EXISTS ADD constraint");

        engine.execute("CREATE TABLE test1 (id INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS test1 ADD PRIMARY KEY (id)");

        assertEquals("Y", describeCell("test1", "ID", "primary key"));

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

        assertEquals(3, engine.executeQuery("DESCRIBE TABLE teams").getRowCount());
        assertEquals("Y", describeCell("teams", "TEAM_ID", "primary key"));
        assertEquals("Y", describeCell("teams", "NAME", "unique key"));

        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'teams'");
        assertEquals("LINEAR(city)", cell(tables, soleRowWhere(tables, "name", "TEAMS"), "cluster_by"));

        logger.info("Mixed ALTER TABLE operations with constraints work correctly");
    }

    @Test
    public void testAlterTableAddForeignKeyWithSetNull() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON DELETE SET NULL");

        engine.execute("CREATE TABLE managers (mgr_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE employees (emp_id INTEGER, mgr_id INTEGER)");
        engine.execute("ALTER TABLE employees ADD FOREIGN KEY (mgr_id) REFERENCES managers (mgr_id) ON DELETE SET NULL");

        assertEquals(0, importedKeys("employees").getRowCount());

        logger.info("ALTER TABLE ADD FOREIGN KEY with SET NULL dropped the constraint, as live does");
    }

    @Test
    public void testAlterTableAddForeignKeyWithRestrict() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON DELETE RESTRICT");

        engine.execute("CREATE TABLE wallets (wallet_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE transactions (txn_id INTEGER, wallet_id INTEGER)");
        engine.execute("ALTER TABLE transactions ADD FOREIGN KEY (wallet_id) REFERENCES wallets (wallet_id) ON DELETE RESTRICT");

        assertEquals(0, importedKeys("transactions").getRowCount());

        logger.info("ALTER TABLE ADD FOREIGN KEY with RESTRICT dropped the constraint, as live does");
    }

    @Test
    public void testAlterTableAddForeignKeyWithNoAction() {
        logger.info("Testing ALTER TABLE ADD FOREIGN KEY with ON UPDATE NO ACTION");

        engine.execute("CREATE TABLE vendors (vendor_id INTEGER PRIMARY KEY)");
        engine.execute("CREATE TABLE products (prod_id INTEGER, vendor_id INTEGER)");
        engine.execute("ALTER TABLE products ADD FOREIGN KEY (vendor_id) REFERENCES vendors (vendor_id) ON UPDATE NO ACTION");

        // NO ACTION is the one supported referential action: the constraint is kept.
        final ResultSet keys = importedKeys("products");
        final Row fk = soleRowWhere(keys, "fk_column_name", "VENDOR_ID");
        assertEquals("NO ACTION", cell(keys, fk, "update_rule"));
        assertEquals("NO ACTION", cell(keys, fk, "delete_rule"));

        logger.info("ALTER TABLE ADD FOREIGN KEY with NO ACTION works correctly");
    }
}
