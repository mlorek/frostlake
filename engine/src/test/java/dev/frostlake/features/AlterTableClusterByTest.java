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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER TABLE … CLUSTER BY — setting, changing and narrowing cluster keys on existing tables —
 * asserted through the SQL surface: the {@code cluster_by} cell of {@code SHOW TABLES}
 * ({@code LINEAR(keys)} exactly as written, empty when unclustered), so every check runs against
 * whichever engine executed the DDL, embedded or live.
 */
public class AlterTableClusterByTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(AlterTableClusterByTest.class);

    /** The SHOW TABLES cluster_by cell for the given table, optionally schema-qualified. */
    private String clusterBy(final String table) {
        final String bareName = table.contains(".")
            ? table.substring(table.lastIndexOf('.') + 1) : table;
        final String scope = table.contains(".")
            ? " IN SCHEMA " + table.substring(0, table.lastIndexOf('.')) : "";
        final ResultSet rs = engine.executeQuery("SHOW TABLES LIKE '" + bareName + "'" + scope);
        return cell(rs, soleRowWhere(rs, "name", bareName.toUpperCase()), "cluster_by");
    }

    @Test
    public void testAlterTableAddClusterBy() {
        logger.info("Testing ALTER TABLE to add CLUSTER BY");

        engine.execute("CREATE TABLE orders (order_id INTEGER, customer_id INTEGER, order_date VARCHAR)");

        assertEquals("", clusterBy("orders"));

        engine.execute("ALTER TABLE orders CLUSTER BY (order_date)");

        assertEquals("LINEAR(order_date)", clusterBy("orders"));

        logger.info("ALTER TABLE add CLUSTER BY works correctly");
    }

    @Test
    public void testAlterTableAddMultipleClusterKeys() {
        logger.info("Testing ALTER TABLE with multiple cluster keys");

        engine.execute("CREATE TABLE sales (id INTEGER, region VARCHAR, product VARCHAR, amount INTEGER)");
        engine.execute("ALTER TABLE sales CLUSTER BY (region, product)");

        assertEquals("LINEAR(region, product)", clusterBy("sales"));

        logger.info("ALTER TABLE with multiple cluster keys works correctly");
    }

    @Test
    public void testAlterTableChangeClusterBy() {
        logger.info("Testing ALTER TABLE to change existing CLUSTER BY");

        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR, country VARCHAR, city VARCHAR) CLUSTER BY (country)");

        assertEquals("LINEAR(country)", clusterBy("customers"));

        engine.execute("ALTER TABLE customers CLUSTER BY (city)");

        assertEquals("LINEAR(city)", clusterBy("customers"));

        logger.info("ALTER TABLE change CLUSTER BY works correctly");
    }

    @Test
    public void testAlterTableChangeToMultipleClusterKeys() {
        logger.info("Testing ALTER TABLE to change from single to multiple cluster keys");

        engine.execute("CREATE TABLE products (id INTEGER, category VARCHAR, brand VARCHAR, price INTEGER) CLUSTER BY (category)");
        engine.execute("ALTER TABLE products CLUSTER BY (category, brand)");

        assertEquals("LINEAR(category, brand)", clusterBy("products"));

        logger.info("ALTER TABLE change to multiple cluster keys works correctly");
    }

    @Test
    public void testAlterTableClusterByWithExpression() {
        logger.info("Testing ALTER TABLE CLUSTER BY with expression");

        engine.execute("CREATE TABLE users (id INTEGER, email VARCHAR, created_at VARCHAR)");
        engine.execute("ALTER TABLE users CLUSTER BY (UPPER(email))");

        final String clusterBy = clusterBy("users");
        assertTrue(clusterBy.contains("UPPER"));
        assertTrue(clusterBy.contains("email"));

        logger.info("ALTER TABLE CLUSTER BY with expression works correctly");
    }

    @Test
    public void testAlterTableClusterByWithMultipleExpressions() {
        logger.info("Testing ALTER TABLE CLUSTER BY with multiple expressions");

        engine.execute("CREATE TABLE events (id INTEGER, name VARCHAR, timestamp VARCHAR)");
        engine.execute("ALTER TABLE events CLUSTER BY (UPPER(name), DATE(timestamp))");

        final String clusterBy = clusterBy("events");
        assertTrue(clusterBy.contains("UPPER"));
        assertTrue(clusterBy.contains("DATE"));

        logger.info("ALTER TABLE CLUSTER BY with multiple expressions works correctly");
    }

    @Test
    public void testAlterTableClusterByWithArithmeticExpression() {
        logger.info("Testing ALTER TABLE CLUSTER BY with arithmetic expression");

        engine.execute("CREATE TABLE metrics (id INTEGER, value INTEGER, multiplier INTEGER)");
        engine.execute("ALTER TABLE metrics CLUSTER BY (value * multiplier)");

        final String clusterBy = clusterBy("metrics");
        assertTrue(clusterBy.contains("value"));
        assertTrue(clusterBy.contains("multiplier"));

        logger.info("ALTER TABLE CLUSTER BY with arithmetic expression works correctly");
    }

    @Test
    public void testAlterTableClusterByMixedColumnsAndExpressions() {
        logger.info("Testing ALTER TABLE CLUSTER BY with mixed columns and expressions");

        engine.execute("CREATE TABLE transactions (id INTEGER, user_id INTEGER, date VARCHAR, amount INTEGER)");
        engine.execute("ALTER TABLE transactions CLUSTER BY (user_id, UPPER(date), amount * 100)");

        final String clusterBy = clusterBy("transactions");
        assertTrue(clusterBy.startsWith("LINEAR(user_id,"));
        assertTrue(clusterBy.contains("UPPER"));
        assertTrue(clusterBy.contains("amount"));

        logger.info("ALTER TABLE CLUSTER BY with mixed columns and expressions works correctly");
    }

    @Test
    public void testAlterTableClusterByWithQualifiedName() {
        logger.info("Testing ALTER TABLE CLUSTER BY with qualified table name");

        engine.execute("CREATE SCHEMA analytics");
        engine.execute("CREATE TABLE analytics.reports (id INTEGER, region VARCHAR, quarter VARCHAR)");
        engine.execute("ALTER TABLE analytics.reports CLUSTER BY (region, quarter)");

        assertEquals("LINEAR(region, quarter)", clusterBy("analytics.reports"));

        logger.info("ALTER TABLE CLUSTER BY with qualified name works correctly");
    }

    @Test
    public void testAlterTableClusterByAfterInsert() {
        logger.info("Testing ALTER TABLE CLUSTER BY after data insertion");

        engine.execute("CREATE TABLE items (id INTEGER, category VARCHAR)");
        engine.execute("INSERT INTO items VALUES (1, 'Electronics'), (2, 'Furniture'), (3, 'Electronics')");
        engine.execute("ALTER TABLE items CLUSTER BY (category)");

        assertEquals("LINEAR(category)", clusterBy("items"));

        logger.info("ALTER TABLE CLUSTER BY after insert works correctly");
    }

    @Test
    public void testAlterTableClusterByWithIfExists() {
        logger.info("Testing ALTER TABLE IF EXISTS CLUSTER BY");

        engine.execute("CREATE TABLE test1 (id INTEGER, name VARCHAR)");
        engine.execute("ALTER TABLE IF EXISTS test1 CLUSTER BY (name)");

        assertEquals("LINEAR(name)", clusterBy("test1"));

        // The clustering expression is compiled even though IF EXISTS forgave the missing
        // table — with no table, no column reference can resolve (live-verified, positioned).
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE IF EXISTS nonexistent CLUSTER BY (col1)");
            }
        });
        assertTrue(e.getMessage().contains("invalid identifier 'COL1'"), e.getMessage());

        logger.info("ALTER TABLE IF EXISTS CLUSTER BY works correctly");
    }

    @Test
    public void testClusterByUnknownColumnIsRefused() {
        engine.execute("CREATE TABLE known_cols (id INTEGER, name VARCHAR)");

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE known_cols CLUSTER BY (nosuchcol)");
            }
        });
        assertTrue(e.getMessage().contains("invalid identifier 'NOSUCHCOL'"), e.getMessage());

        // The table's clustering is untouched.
        assertEquals("", clusterBy("known_cols"));
    }

    @Test
    public void testAlterTableMultipleAlterations() {
        logger.info("Testing multiple ALTER TABLE operations including CLUSTER BY");

        engine.execute("CREATE TABLE multi_alter (id INTEGER, col1 VARCHAR)");
        engine.execute("ALTER TABLE multi_alter ADD COLUMN col2 INTEGER");
        engine.execute("ALTER TABLE multi_alter CLUSTER BY (col1)");
        engine.execute("ALTER TABLE multi_alter ADD COLUMN col3 VARCHAR");
        engine.execute("ALTER TABLE multi_alter CLUSTER BY (col1, col2)");

        assertEquals(4, engine.executeQuery("DESCRIBE TABLE multi_alter").getRowCount());
        assertEquals("LINEAR(col1, col2)", clusterBy("multi_alter"));

        logger.info("Multiple ALTER TABLE operations including CLUSTER BY work correctly");
    }

    @Test
    public void testAlterTableClusterByWithCastExpression() {
        logger.info("Testing ALTER TABLE CLUSTER BY with CAST expression");

        engine.execute("CREATE TABLE data (id INTEGER, value VARCHAR, score DECIMAL)");
        engine.execute("ALTER TABLE data CLUSTER BY (CAST(value AS INTEGER))");

        final String clusterBy = clusterBy("data");
        assertTrue(clusterBy.contains("CAST"));
        assertTrue(clusterBy.contains("value"));

        logger.info("ALTER TABLE CLUSTER BY with CAST expression works correctly");
    }

    @Test
    public void testAlterTableClusterByRemoveBySettingEmpty() {
        logger.info("Testing ALTER TABLE CLUSTER BY to update clustering");

        engine.execute("CREATE TABLE clustered (id INTEGER, col1 VARCHAR, col2 VARCHAR) CLUSTER BY (col1, col2)");

        assertEquals("LINEAR(col1, col2)", clusterBy("clustered"));

        engine.execute("ALTER TABLE clustered CLUSTER BY (col1)");

        assertEquals("LINEAR(col1)", clusterBy("clustered"));

        logger.info("ALTER TABLE CLUSTER BY update works correctly");
    }

    @Test
    public void testAlterTableClusterByWithSubstringExpression() {
        logger.info("Testing ALTER TABLE CLUSTER BY with SUBSTRING expression");

        engine.execute("CREATE TABLE logs (id INTEGER, message VARCHAR, timestamp VARCHAR)");
        engine.execute("ALTER TABLE logs CLUSTER BY (SUBSTRING(message, 1, 10))");

        final String clusterBy = clusterBy("logs");
        assertTrue(clusterBy.contains("SUBSTRING"));
        assertTrue(clusterBy.contains("message"));

        logger.info("ALTER TABLE CLUSTER BY with SUBSTRING expression works correctly");
    }

    @Test
    public void testAlterTableClusterByPreservesData() {
        logger.info("Testing ALTER TABLE CLUSTER BY preserves data");

        engine.execute("CREATE TABLE preserve_test (id INTEGER, region VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO preserve_test VALUES (1, 'US', 100), (2, 'EU', 200), (3, 'US', 150)");
        engine.execute("ALTER TABLE preserve_test CLUSTER BY (region)");

        assertEquals("LINEAR(region)", clusterBy("preserve_test"));

        assertEquals(3, engine.executeQuery("SELECT * FROM preserve_test").getRowCount());

        logger.info("ALTER TABLE CLUSTER BY preserves data correctly");
    }
}
