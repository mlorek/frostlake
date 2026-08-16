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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The IF NOT EXISTS clause of ALTER TABLE … ADD COLUMN, asserted through the SQL surface —
 * {@code DESCRIBE TABLE} rows for column presence, count and type — so every check runs against
 * whichever engine executed the DDL, embedded or live. Adding an already-present column is
 * forgiven and leaves the existing column (and its type) untouched.
 */
public class AddColumnIfNotExistsTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(AddColumnIfNotExistsTest.class);

    private ResultSet describe(final String table) {
        return engine.executeQuery("DESCRIBE TABLE " + table);
    }

    private int columnCount(final String table) {
        return describe(table).getRowCount();
    }

    private boolean hasColumn(final String table, final String column) {
        return !rowsWhere(describe(table), "name", column).isEmpty();
    }

    @Test
    public void testAddColumnWithoutIfNotExists() {
        logger.info("Testing ADD COLUMN without IF NOT EXISTS on new column");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE users ADD COLUMN age INTEGER");

        assertEquals(3, columnCount("users"));
        assertTrue(hasColumn("users", "AGE"));

        logger.info("ADD COLUMN without IF NOT EXISTS works correctly");
    }

    @Test
    public void testAddColumnWithoutIfNotExistsOnExistingColumn() {
        logger.info("Testing ADD COLUMN without IF NOT EXISTS on existing column");

        engine.execute("CREATE TABLE users (id INTEGER, name VARCHAR)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE users ADD COLUMN name VARCHAR");
            }
        });

        logger.info("ADD COLUMN without IF NOT EXISTS correctly throws exception on duplicate");
    }

    @Test
    public void testAddColumnIfNotExistsOnNewColumn() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS on new column");

        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS price INTEGER");

        assertEquals(3, columnCount("products"));
        assertTrue(hasColumn("products", "PRICE"));

        logger.info("ADD COLUMN IF NOT EXISTS on new column works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsOnExistingColumn() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS on existing column");

        engine.execute("CREATE TABLE products (id INTEGER, name VARCHAR, price INTEGER)");

        engine.execute("ALTER TABLE products ADD COLUMN IF NOT EXISTS price INTEGER");

        assertEquals(3, columnCount("products"));

        logger.info("ADD COLUMN IF NOT EXISTS on existing column succeeds without error");
    }

    @Test
    public void testAddColumnIfNotExistsWithDifferentType() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with different type on existing column");

        engine.execute("CREATE TABLE items (id INTEGER, name VARCHAR)");

        engine.execute("ALTER TABLE items ADD COLUMN IF NOT EXISTS name INTEGER");

        assertEquals(2, columnCount("items"));
        final String nameType = describeCell("items", "NAME", "type");
        assertTrue(nameType.startsWith("VARCHAR"),
            "the existing column's type must be preserved but reads: " + nameType);

        logger.info("ADD COLUMN IF NOT EXISTS preserves existing column type");
    }

    @Test
    public void testAddMultipleColumnsIfNotExists() {
        logger.info("Testing multiple ADD COLUMN IF NOT EXISTS operations");

        engine.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER)");

        engine.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS status VARCHAR");
        engine.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS status VARCHAR");
        engine.execute("ALTER TABLE orders ADD COLUMN IF NOT EXISTS total INTEGER");

        assertEquals(4, columnCount("orders"));
        assertTrue(hasColumn("orders", "STATUS"));
        assertTrue(hasColumn("orders", "TOTAL"));

        logger.info("Multiple ADD COLUMN IF NOT EXISTS operations work correctly");
    }

    @Test
    public void testAddColumnIfNotExistsWithQualifiedName() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with schema-qualified table name");

        engine.execute("CREATE SCHEMA add_col_schema");
        engine.execute("CREATE TABLE add_col_schema.customers (id INTEGER, name VARCHAR)");

        engine.execute("ALTER TABLE add_col_schema.customers ADD COLUMN IF NOT EXISTS email VARCHAR");

        assertEquals(3, columnCount("add_col_schema.customers"));
        assertTrue(hasColumn("add_col_schema.customers", "EMAIL"));

        logger.info("ADD COLUMN IF NOT EXISTS with qualified name works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsWithAlterTableIfExists() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with ALTER TABLE IF EXISTS");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("ALTER TABLE IF EXISTS test_table ADD COLUMN IF NOT EXISTS col1 VARCHAR");

        assertEquals(2, columnCount("test_table"));
        assertTrue(hasColumn("test_table", "COL1"));

        logger.info("ADD COLUMN IF NOT EXISTS with ALTER TABLE IF EXISTS works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsOnNonExistentTableWithTableIfExists() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS on non-existent table with ALTER TABLE IF EXISTS");

        engine.execute("ALTER TABLE IF EXISTS non_existent ADD COLUMN IF NOT EXISTS col1 VARCHAR");

        logger.info("ADD COLUMN IF NOT EXISTS with ALTER TABLE IF EXISTS on non-existent table succeeds");
    }

    @Test
    public void testAddColumnIfNotExistsWithData() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS on table with data");

        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice')");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob')");

        engine.execute("ALTER TABLE employees ADD COLUMN IF NOT EXISTS salary INTEGER");

        final ResultSet rs = engine.executeQuery("SELECT * FROM employees");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        assertEquals(3, columnCount("employees"));

        logger.info("ADD COLUMN IF NOT EXISTS on table with data works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsIdempotent() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS is idempotent");

        engine.execute("CREATE TABLE config (id INTEGER)");

        for (int i = 0; i < 5; i++) {
            engine.execute("ALTER TABLE config ADD COLUMN IF NOT EXISTS setting VARCHAR");
        }

        assertEquals(2, columnCount("config"));

        logger.info("ADD COLUMN IF NOT EXISTS is idempotent");
    }

    @Test
    public void testAddColumnIfNotExistsCaseInsensitive() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS is case-insensitive");

        engine.execute("CREATE TABLE test_table (id INTEGER, Name VARCHAR)");

        engine.execute("ALTER TABLE test_table ADD COLUMN IF NOT EXISTS name VARCHAR");

        assertEquals(2, columnCount("test_table"));

        logger.info("ADD COLUMN IF NOT EXISTS is case-insensitive");
    }

    @Test
    public void testAddColumnIfNotExistsWithComplexDataType() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with complex data type");

        engine.execute("CREATE TABLE events (id INTEGER)");
        engine.execute("ALTER TABLE events ADD COLUMN IF NOT EXISTS event_time TIMESTAMP_LTZ");

        assertEquals(2, columnCount("events"));
        assertTrue(hasColumn("events", "EVENT_TIME"));

        logger.info("ADD COLUMN IF NOT EXISTS with complex data type works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsWithTypeParameters() {
        logger.info("Testing ADD COLUMN IF NOT EXISTS with type parameters");

        engine.execute("CREATE TABLE documents (id INTEGER)");
        engine.execute("ALTER TABLE documents ADD COLUMN IF NOT EXISTS content VARCHAR(16777216)");

        assertEquals(2, columnCount("documents"));
        final String contentType = describeCell("documents", "CONTENT", "type");
        assertTrue(contentType.startsWith("VARCHAR"), contentType);

        logger.info("ADD COLUMN IF NOT EXISTS with type parameters works correctly");
    }

    @Test
    public void testMixedAddColumnWithAndWithoutIfNotExists() {
        logger.info("Testing mixed ADD COLUMN with and without IF NOT EXISTS");

        engine.execute("CREATE TABLE mixed (id INTEGER)");

        engine.execute("ALTER TABLE mixed ADD COLUMN col1 VARCHAR");
        engine.execute("ALTER TABLE mixed ADD COLUMN IF NOT EXISTS col2 INTEGER");
        engine.execute("ALTER TABLE mixed ADD COLUMN IF NOT EXISTS col1 VARCHAR");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE mixed ADD COLUMN col2 INTEGER");
            }
        });

        assertEquals(3, columnCount("mixed"));

        logger.info("Mixed ADD COLUMN with and without IF NOT EXISTS works correctly");
    }

    @Test
    public void testAddColumnIfNotExistsRealWorldExample() {
        logger.info("Testing real-world example from user");

        engine.execute("CREATE SCHEMA BASE");
        engine.execute("CREATE TABLE BASE.DIM_PRODUCT (PRODUCT_ID INTEGER, PRODUCT_NAME VARCHAR)");

        engine.execute("ALTER TABLE IF EXISTS BASE.DIM_PRODUCT ADD COLUMN IF NOT EXISTS PRODUCT_URL VARCHAR(16777216)");

        assertEquals(3, columnCount("BASE.DIM_PRODUCT"));
        assertTrue(hasColumn("BASE.DIM_PRODUCT", "PRODUCT_URL"));
        assertFalse(describeCell("BASE.DIM_PRODUCT", "PRODUCT_URL", "type").isEmpty());

        engine.execute("ALTER TABLE IF EXISTS BASE.DIM_PRODUCT ADD COLUMN IF NOT EXISTS PRODUCT_URL VARCHAR(16777216)");

        assertEquals(3, columnCount("BASE.DIM_PRODUCT"));

        logger.info("Real-world example works correctly");
    }
}
