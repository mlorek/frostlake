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
 * ALTER TABLE … ALTER COLUMN … SET DATA TYPE, asserted through the SQL surface — the
 * {@code DESCRIBE TABLE} type/comment/primary-key cells — so every check runs against whichever
 * engine executed the DDL, embedded or live. Snowflake permits only the widening retypes: a
 * VARCHAR may grow its length, a NUMBER may grow its precision at an unchanged scale, and a
 * column may restate a synonymous spelling of its own type; every cross-family or narrowing
 * change is refused with "cannot change column …". Type cells are matched by their family prefix,
 * tolerant of the parameter suffixes the engines spell differently.
 */
public class AlterColumnTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterColumnTest.class);

    /** The DESCRIBE TABLE type cell for one column. */
    private String columnType(final String table, final String column) {
        return describeCell(table, column, "type");
    }

    private void assertAlterColumnRefused(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(e.getMessage().contains("cannot change column"), e.getMessage());
    }

    @Test
    public void testCrossFamilyChangeToVarcharIsRejected() {
        logger.info("Testing that INTEGER -> VARCHAR is rejected (Snowflake restriction)");

        engine.execute("CREATE TABLE users (id INTEGER, age INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO users VALUES (1, 25, 'Alice')");

        assertAlterColumnRefused("ALTER TABLE users ALTER COLUMN age SET DATA TYPE VARCHAR");
    }

    @Test
    public void testCrossFamilyChangeToIntegerIsRejected() {
        logger.info("Testing that VARCHAR -> INTEGER is rejected (Snowflake restriction)");

        engine.execute("CREATE TABLE products (id INTEGER, price VARCHAR, name VARCHAR)");

        assertAlterColumnRefused("ALTER TABLE products ALTER COLUMN price SET DATA TYPE INTEGER");
    }

    @Test
    public void testAlterColumnWithTypeParameters() {
        logger.info("Testing ALTER COLUMN with type parameters (VARCHAR length increase)");

        engine.execute("CREATE TABLE documents (id INTEGER, content VARCHAR(100))");

        engine.execute("ALTER TABLE documents ALTER COLUMN content SET DATA TYPE VARCHAR(1000)");

        assertTrue(columnType("documents", "CONTENT").startsWith("VARCHAR"));

        logger.info("Column type altered with type parameters successfully");
    }

    @Test
    public void testAlterColumnToDecimal() {
        logger.info("Testing ALTER COLUMN to DECIMAL with grown precision at unchanged scale");

        engine.execute("CREATE TABLE finances (id INTEGER, amount DECIMAL(10, 2))");

        engine.execute("ALTER TABLE finances ALTER COLUMN amount SET DATA TYPE DECIMAL(12, 2)");

        assertTrue(columnType("finances", "AMOUNT").startsWith("NUMBER"));

        logger.info("Column type altered to DECIMAL successfully");
    }

    @Test
    public void testAlterColumnToTimestamp() {
        logger.info("Testing ALTER COLUMN restating TIMESTAMP_NTZ as its TIMESTAMP synonym");

        engine.execute("CREATE TABLE events (id INTEGER, event_time TIMESTAMP_NTZ)");

        engine.execute("ALTER TABLE events ALTER COLUMN event_time SET DATA TYPE TIMESTAMP");

        assertTrue(columnType("events", "EVENT_TIME").startsWith("TIMESTAMP"));

        logger.info("Column type restated as TIMESTAMP successfully");
    }

    @Test
    public void testChangeTimestampToDateIsRejected() {
        logger.info("Testing that TIMESTAMP -> DATE is rejected (Snowflake restriction)");

        engine.execute("CREATE TABLE orders (id INTEGER, order_date TIMESTAMP)");

        assertAlterColumnRefused("ALTER TABLE orders ALTER COLUMN order_date SET DATA TYPE DATE");
    }

    @Test
    public void testVarcharShrinkIsRejected() {
        logger.info("Testing that shrinking a VARCHAR length is rejected (Snowflake restriction)");

        engine.execute("CREATE TABLE notes (id INTEGER, body VARCHAR(1000))");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE notes ALTER COLUMN body SET DATA TYPE VARCHAR(100)");
            }
        });
        // The one retype refusal that spells its own reason, and names both parameterized types.
        assertTrue(e.getMessage().contains(
            "cannot change column BODY from type VARCHAR(1000) to VARCHAR(100)"), e.getMessage());
        assertTrue(e.getMessage().contains(
            "because reducing the byte-length of a varchar is not supported."), e.getMessage());
    }

    @Test
    public void testNumberScaleChangeIsRejected() {
        logger.info("Testing that changing a NUMBER scale is rejected (Snowflake restriction)");

        engine.execute("CREATE TABLE prices (id INTEGER, amount NUMBER(10, 2))");

        assertAlterColumnRefused("ALTER TABLE prices ALTER COLUMN amount SET DATA TYPE NUMBER(10, 4)");
    }

    @Test
    public void testNumberPrecisionDecreaseIsAllowed() {
        logger.info("Testing that decreasing a NUMBER precision at an unchanged scale is allowed");

        engine.execute("CREATE TABLE ledgers (id INTEGER, amount NUMBER(20, 2))");
        engine.execute("INSERT INTO ledgers VALUES (1, 1.25)");

        // Precision may move either way as long as the SCALE is unchanged — even with rows present.
        engine.execute("ALTER TABLE ledgers ALTER COLUMN amount SET DATA TYPE NUMBER(10, 2)");

        assertTrue(columnType("ledgers", "AMOUNT").startsWith("NUMBER"));
    }

    @Test
    public void testAlterColumnNonExistentColumn() {
        logger.info("Testing ALTER COLUMN on non-existent column");

        engine.execute("CREATE TABLE test_table (id INTEGER)");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE test_table ALTER COLUMN non_existent SET DATA TYPE VARCHAR");
            }
        });

        logger.info("ALTER COLUMN on non-existent column correctly throws exception");
    }

    @Test
    public void testAlterColumnNonExistentTable() {
        logger.info("Testing ALTER COLUMN on non-existent table");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE non_existent ALTER COLUMN col SET DATA TYPE VARCHAR");
            }
        });

        logger.info("ALTER COLUMN on non-existent table correctly throws exception");
    }

    @Test
    public void testAlterColumnWithIfExists() {
        logger.info("Testing ALTER COLUMN with IF EXISTS on non-existent table");

        engine.execute("ALTER TABLE IF EXISTS non_existent ALTER COLUMN col SET DATA TYPE VARCHAR");

        logger.info("ALTER COLUMN with IF EXISTS succeeds without error");
    }

    @Test
    public void testAlterColumnPreservesComment() {
        logger.info("Testing that ALTER COLUMN preserves column comment");

        engine.execute("CREATE TABLE test_table (id INTEGER, value VARCHAR(10) COMMENT 'Important value')");

        assertEquals("Important value", describeCell("test_table", "VALUE", "comment"));

        engine.execute("ALTER TABLE test_table ALTER COLUMN value SET DATA TYPE VARCHAR(200)");

        assertEquals("Important value", describeCell("test_table", "VALUE", "comment"));

        logger.info("ALTER COLUMN preserved column comment");
    }

    @Test
    public void testAlterColumnPreservesPrimaryKey() {
        logger.info("Testing that ALTER COLUMN preserves primary key constraint");

        engine.execute("CREATE TABLE test_table (id INTEGER PRIMARY KEY, name VARCHAR)");

        assertEquals("Y", describeCell("test_table", "ID", "primary key"));

        engine.execute("ALTER TABLE test_table ALTER COLUMN id SET DATA TYPE BIGINT");

        assertEquals("Y", describeCell("test_table", "ID", "primary key"));

        logger.info("ALTER COLUMN preserved primary key constraint");
    }

    @Test
    public void testAlterMultipleColumns() {
        logger.info("Testing ALTER COLUMN on multiple columns");

        engine.execute("CREATE TABLE test_table (col1 VARCHAR(5), col2 INTEGER, col3 INTEGER)");

        engine.execute("ALTER TABLE test_table ALTER COLUMN col1 SET DATA TYPE VARCHAR");
        engine.execute("ALTER TABLE test_table ALTER COLUMN col2 SET DATA TYPE DECIMAL");
        engine.execute("ALTER TABLE test_table ALTER COLUMN col3 SET DATA TYPE BIGINT");

        assertTrue(columnType("test_table", "COL1").startsWith("VARCHAR"));
        assertTrue(columnType("test_table", "COL2").startsWith("NUMBER"));
        // BIGINT is NUMBER(38,0)'s synonym; either spelling of the one type is the pass.
        final String col3Type = columnType("test_table", "COL3");
        assertTrue(col3Type.startsWith("NUMBER") || col3Type.startsWith("BIGINT"), col3Type);

        logger.info("Multiple columns altered successfully");
    }

    @Test
    public void testAlterColumnWithQualifiedName() {
        logger.info("Testing ALTER COLUMN with schema-qualified table name");

        engine.execute("CREATE SCHEMA alter_col_schema");
        engine.execute("CREATE TABLE alter_col_schema.products (id INTEGER, price INTEGER)");

        engine.execute("ALTER TABLE alter_col_schema.products ALTER COLUMN price SET DATA TYPE DECIMAL");

        assertTrue(columnType("alter_col_schema.products", "PRICE").startsWith("NUMBER"));

        logger.info("ALTER COLUMN with qualified name works correctly");
    }

    @Test
    public void testAlterColumnWithData() {
        logger.info("Testing ALTER COLUMN on table with existing data");

        engine.execute("CREATE TABLE customers (id INTEGER, age INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO customers VALUES (1, 25, 'Alice')");
        engine.execute("INSERT INTO customers VALUES (2, 30, 'Bob')");

        engine.execute("ALTER TABLE customers ALTER COLUMN age SET DATA TYPE BIGINT");

        final ResultSet rs = engine.executeQuery("SELECT * FROM customers");
        assertNotNull(rs);
        assertEquals(2, rs.getRowCount());

        // BIGINT is NUMBER(38,0)'s synonym; either spelling of the one type is the pass.
        final String ageType = columnType("customers", "AGE");
        assertTrue(ageType.startsWith("NUMBER") || ageType.startsWith("BIGINT"), ageType);

        logger.info("ALTER COLUMN on table with data works correctly");
    }

    @Test
    public void testAlterColumnCombinedWithOtherAlterOperations() {
        logger.info("Testing ALTER COLUMN combined with other ALTER TABLE operations");

        engine.execute("CREATE TABLE test_table (id INTEGER, col1 VARCHAR(5), col2 VARCHAR)");

        engine.execute("ALTER TABLE test_table ADD COLUMN col3 INTEGER");
        engine.execute("ALTER TABLE test_table ALTER COLUMN col1 SET DATA TYPE VARCHAR");
        engine.execute("ALTER TABLE test_table RENAME COLUMN col2 TO col2_renamed");
        engine.execute("ALTER TABLE test_table ALTER COLUMN col3 SET DATA TYPE DECIMAL");

        assertEquals(4, engine.executeQuery("DESCRIBE TABLE test_table").getRowCount());
        assertTrue(columnType("test_table", "COL1").startsWith("VARCHAR"));
        assertTrue(columnType("test_table", "COL3").startsWith("NUMBER"));

        logger.info("ALTER COLUMN combined with other operations works correctly");
    }

    @Test
    public void testChangeToBooleanIsRejected() {
        logger.info("Testing that INTEGER -> BOOLEAN is rejected (Snowflake restriction)");

        engine.execute("CREATE TABLE flags (id INTEGER, is_active INTEGER)");

        assertAlterColumnRefused("ALTER TABLE flags ALTER COLUMN is_active SET DATA TYPE BOOLEAN");
    }
}
