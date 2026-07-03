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
import dev.frostlake.metastore.model.TableColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for DEFAULT constraint with expressions
 */
public class DefaultExpressionTest {
    private static final Logger logger = LoggerFactory.getLogger(DefaultExpressionTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        logger.info("DatabaseEngine initialized for DEFAULT expression tests");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testDefaultWithLiteral() {
        logger.info("Testing DEFAULT with literal value");

        engine.execute("CREATE TABLE test1 (id INTEGER, status VARCHAR DEFAULT 'active')");

        Table table = engine.getCatalog().resolveTable("test1");
        assertNotNull(table);

        TableColumn statusColumn = table.getColumn("status");
        assertNotNull(statusColumn.getDefaultValue());

        logger.info("DEFAULT with literal works correctly");
    }

    @Test
    public void testDefaultWithCurrentTimestamp() {
        logger.info("Testing DEFAULT with CURRENT_TIMESTAMP");

        engine.execute("CREATE TABLE test2 (id INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        Table table = engine.getCatalog().resolveTable("test2");
        assertNotNull(table);

        TableColumn createdAtColumn = table.getColumn("created_at");
        assertNotNull(createdAtColumn.getDefaultValue());

        logger.info("DEFAULT with CURRENT_TIMESTAMP works correctly");
    }

    @Test
    public void testDefaultWithCurrentTimestampParens() {
        logger.info("Testing DEFAULT with CURRENT_TIMESTAMP()");

        engine.execute("CREATE TABLE test3 (id INTEGER, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP())");

        Table table = engine.getCatalog().resolveTable("test3");
        assertNotNull(table);

        TableColumn updatedAtColumn = table.getColumn("updated_at");
        assertNotNull(updatedAtColumn.getDefaultValue());

        logger.info("DEFAULT with CURRENT_TIMESTAMP() works correctly");
    }

    @Test
    public void testDefaultWithCurrentDate() {
        logger.info("Testing DEFAULT with CURRENT_DATE");

        engine.execute("CREATE TABLE test4 (id INTEGER, date_col DATE DEFAULT CURRENT_DATE)");

        Table table = engine.getCatalog().resolveTable("test4");
        assertNotNull(table);

        TableColumn dateColumn = table.getColumn("date_col");
        assertNotNull(dateColumn.getDefaultValue());

        logger.info("DEFAULT with CURRENT_DATE works correctly");
    }

    @Test
    public void testDefaultWithArithmeticExpression() {
        logger.info("Testing DEFAULT with arithmetic expression");

        engine.execute("CREATE TABLE test5 (id INTEGER, quantity INTEGER DEFAULT 10 + 5)");

        Table table = engine.getCatalog().resolveTable("test5");
        assertNotNull(table);

        TableColumn quantityColumn = table.getColumn("quantity");
        assertNotNull(quantityColumn.getDefaultValue());

        logger.info("DEFAULT with arithmetic expression works correctly");
    }

    @Test
    public void testDefaultWithFunctionCall() {
        logger.info("Testing DEFAULT with function call");

        engine.execute("CREATE TABLE test6 (id INTEGER, upper_name VARCHAR DEFAULT UPPER('test'))");

        Table table = engine.getCatalog().resolveTable("test6");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("upper_name");
        assertNotNull(nameColumn.getDefaultValue());

        logger.info("DEFAULT with function call works correctly");
    }

    @Test
    public void testDefaultWithConcatenation() {
        logger.info("Testing DEFAULT with string concatenation");

        engine.execute("CREATE TABLE test7 (id INTEGER, full_name VARCHAR DEFAULT 'Mr. ' || 'Unknown')");

        Table table = engine.getCatalog().resolveTable("test7");
        assertNotNull(table);

        TableColumn nameColumn = table.getColumn("full_name");
        assertNotNull(nameColumn.getDefaultValue());

        logger.info("DEFAULT with concatenation works correctly");
    }

    @Test
    public void testDefaultWithCaseExpression() {
        logger.info("Testing DEFAULT with CASE expression");

        engine.execute("CREATE TABLE test8 (id INTEGER, priority VARCHAR DEFAULT CASE WHEN 1 = 1 THEN 'high' ELSE 'low' END)");

        Table table = engine.getCatalog().resolveTable("test8");
        assertNotNull(table);

        TableColumn priorityColumn = table.getColumn("priority");
        assertNotNull(priorityColumn.getDefaultValue());

        logger.info("DEFAULT with CASE expression works correctly");
    }

    @Test
    public void testDefaultWithNegativeNumber() {
        logger.info("Testing DEFAULT with negative number");

        engine.execute("CREATE TABLE test9 (id INTEGER, balance INTEGER DEFAULT -100)");

        Table table = engine.getCatalog().resolveTable("test9");
        assertNotNull(table);

        TableColumn balanceColumn = table.getColumn("balance");
        assertNotNull(balanceColumn.getDefaultValue());

        logger.info("DEFAULT with negative number works correctly");
    }

    @Test
    public void testDefaultWithComplexExpression() {
        logger.info("Testing DEFAULT with complex expression");

        engine.execute("CREATE TABLE test10 (id INTEGER, computed INTEGER DEFAULT (10 * 5) + (20 - 5))");

        Table table = engine.getCatalog().resolveTable("test10");
        assertNotNull(table);

        TableColumn computedColumn = table.getColumn("computed");
        assertNotNull(computedColumn.getDefaultValue());

        logger.info("DEFAULT with complex expression works correctly");
    }

    @Test
    public void testDefaultWithParenthesizedExpression() {
        logger.info("Testing DEFAULT with parenthesized expression");

        engine.execute("CREATE TABLE test11 (id INTEGER, value INTEGER DEFAULT (100))");

        Table table = engine.getCatalog().resolveTable("test11");
        assertNotNull(table);

        TableColumn valueColumn = table.getColumn("value");
        assertNotNull(valueColumn.getDefaultValue());

        logger.info("DEFAULT with parenthesized expression works correctly");
    }

    @Test
    public void testMultipleColumnsWithDefaultExpressions() {
        logger.info("Testing multiple columns with different DEFAULT expressions");

        engine.execute("CREATE TABLE test12 (id INTEGER DEFAULT 0, name VARCHAR DEFAULT 'unnamed', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, count INTEGER DEFAULT 5 * 2)");

        Table table = engine.getCatalog().resolveTable("test12");
        assertNotNull(table);
        assertEquals(4, table.getColumns().size());

        assertNotNull(table.getColumn("id").getDefaultValue());
        assertNotNull(table.getColumn("name").getDefaultValue());
        assertNotNull(table.getColumn("created_at").getDefaultValue());
        assertNotNull(table.getColumn("count").getDefaultValue());

        logger.info("Multiple columns with DEFAULT expressions work correctly");
    }

    @Test
    public void testDefaultWithNull() {
        logger.info("Testing DEFAULT with NULL");

        engine.execute("CREATE TABLE test13 (id INTEGER, nullable_col VARCHAR DEFAULT NULL)");

        Table table = engine.getCatalog().resolveTable("test13");
        assertNotNull(table);

        TableColumn nullableColumn = table.getColumn("nullable_col");
        assertNotNull(nullableColumn.getDefaultValue());

        logger.info("DEFAULT with NULL works correctly");
    }

    @Test
    public void testDefaultWithBoolean() {
        logger.info("Testing DEFAULT with boolean expression");

        engine.execute("CREATE TABLE test14 (id INTEGER, is_active BOOLEAN DEFAULT TRUE)");

        Table table = engine.getCatalog().resolveTable("test14");
        assertNotNull(table);

        TableColumn activeColumn = table.getColumn("is_active");
        assertNotNull(activeColumn.getDefaultValue());

        logger.info("DEFAULT with boolean works correctly");
    }

    @Test
    public void testDefaultWithDecimalExpression() {
        logger.info("Testing DEFAULT with decimal expression");

        engine.execute("CREATE TABLE test15 (id INTEGER, rate DECIMAL DEFAULT 3.14 * 2)");

        Table table = engine.getCatalog().resolveTable("test15");
        assertNotNull(table);

        TableColumn rateColumn = table.getColumn("rate");
        assertNotNull(rateColumn.getDefaultValue());

        logger.info("DEFAULT with decimal expression works correctly");
    }
}
