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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The DEFAULT constraint with expressions, asserted through the SQL surface — the
 * {@code DESCRIBE TABLE} default cell, which carries the declared expression verbatim — so every
 * check runs against whichever engine executed the DDL, embedded or live.
 */
public class DefaultExpressionTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(DefaultExpressionTest.class);

    /** Asserts the column's DESCRIBE default cell carries this fragment of the declared expression. */
    private void assertDefaultContains(final String table, final String column, final String fragment) {
        final String cellText = describeCell(table, column, "default");
        assertNotNull(cellText, column + " should carry a default");
        assertTrue(cellText.toUpperCase().contains(fragment.toUpperCase()),
            column + "'s default cell reads: " + cellText);
    }

    @Test
    public void testDefaultWithLiteral() {
        logger.info("Testing DEFAULT with literal value");

        engine.execute("CREATE TABLE test1 (id INTEGER, status VARCHAR DEFAULT 'active')");

        assertEquals("'active'", describeCell("test1", "STATUS", "default"));

        logger.info("DEFAULT with literal works correctly");
    }

    @Test
    public void testDefaultWithCurrentTimestamp() {
        logger.info("Testing DEFAULT with CURRENT_TIMESTAMP");

        engine.execute("CREATE TABLE test2 (id INTEGER, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");

        assertDefaultContains("test2", "CREATED_AT", "CURRENT_TIMESTAMP");

        logger.info("DEFAULT with CURRENT_TIMESTAMP works correctly");
    }

    @Test
    public void testDefaultWithCurrentTimestampParens() {
        logger.info("Testing DEFAULT with CURRENT_TIMESTAMP()");

        engine.execute("CREATE TABLE test3 (id INTEGER, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP())");

        assertDefaultContains("test3", "UPDATED_AT", "CURRENT_TIMESTAMP");

        logger.info("DEFAULT with CURRENT_TIMESTAMP() works correctly");
    }

    @Test
    public void testDefaultWithCurrentDate() {
        logger.info("Testing DEFAULT with CURRENT_DATE");

        engine.execute("CREATE TABLE test4 (id INTEGER, date_col DATE DEFAULT CURRENT_DATE)");

        assertDefaultContains("test4", "DATE_COL", "CURRENT_DATE");

        logger.info("DEFAULT with CURRENT_DATE works correctly");
    }

    @Test
    public void testDefaultWithArithmeticExpression() {
        logger.info("Testing DEFAULT with arithmetic expression");

        engine.execute("CREATE TABLE test5 (id INTEGER, quantity INTEGER DEFAULT 10 + 5)");

        assertDefaultContains("test5", "QUANTITY", "10 + 5");

        logger.info("DEFAULT with arithmetic expression works correctly");
    }

    @Test
    public void testDefaultWithFunctionCall() {
        logger.info("Testing DEFAULT with function call");

        engine.execute("CREATE TABLE test6 (id INTEGER, upper_name VARCHAR DEFAULT UPPER('test'))");

        assertDefaultContains("test6", "UPPER_NAME", "UPPER");

        logger.info("DEFAULT with function call works correctly");
    }

    @Test
    public void testDefaultWithConcatenation() {
        logger.info("Testing DEFAULT with string concatenation");

        engine.execute("CREATE TABLE test7 (id INTEGER, full_name VARCHAR DEFAULT 'Mr. ' || 'Unknown')");

        assertDefaultContains("test7", "FULL_NAME", "||");

        logger.info("DEFAULT with concatenation works correctly");
    }

    @Test
    public void testDefaultWithCaseExpression() {
        logger.info("Testing DEFAULT with CASE expression");

        engine.execute("CREATE TABLE test8 (id INTEGER, priority VARCHAR DEFAULT CASE WHEN 1 = 1 THEN 'high' ELSE 'low' END)");

        assertDefaultContains("test8", "PRIORITY", "CASE");

        logger.info("DEFAULT with CASE expression works correctly");
    }

    @Test
    public void testDefaultWithNegativeNumber() {
        logger.info("Testing DEFAULT with negative number");

        engine.execute("CREATE TABLE test9 (id INTEGER, balance INTEGER DEFAULT -100)");

        assertDefaultContains("test9", "BALANCE", "-100");

        logger.info("DEFAULT with negative number works correctly");
    }

    @Test
    public void testDefaultWithComplexExpression() {
        logger.info("Testing DEFAULT with complex expression");

        engine.execute("CREATE TABLE test10 (id INTEGER, computed INTEGER DEFAULT (10 * 5) + (20 - 5))");

        assertDefaultContains("test10", "COMPUTED", "(10 * 5)");

        logger.info("DEFAULT with complex expression works correctly");
    }

    @Test
    public void testDefaultWithParenthesizedExpression() {
        logger.info("Testing DEFAULT with parenthesized expression");

        engine.execute("CREATE TABLE test11 (id INTEGER, value INTEGER DEFAULT (100))");

        assertDefaultContains("test11", "VALUE", "100");

        logger.info("DEFAULT with parenthesized expression works correctly");
    }

    @Test
    public void testMultipleColumnsWithDefaultExpressions() {
        logger.info("Testing multiple columns with different DEFAULT expressions");

        engine.execute("CREATE TABLE test12 (id INTEGER DEFAULT 0, name VARCHAR DEFAULT 'unnamed', created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, count INTEGER DEFAULT 5 * 2)");

        assertEquals(4, engine.executeQuery("DESCRIBE TABLE test12").getRowCount());

        assertDefaultContains("test12", "ID", "0");
        assertEquals("'unnamed'", describeCell("test12", "NAME", "default"));
        assertDefaultContains("test12", "CREATED_AT", "CURRENT_TIMESTAMP");
        assertDefaultContains("test12", "COUNT", "5 * 2");

        logger.info("Multiple columns with DEFAULT expressions work correctly");
    }

    @Test
    public void testDefaultWithNull() {
        logger.info("Testing DEFAULT with NULL");

        engine.execute("CREATE TABLE test13 (id INTEGER, nullable_col VARCHAR DEFAULT NULL)");

        // A NULL default leaves the column nullable; the declaration itself must parse.
        assertEquals("Y", describeCell("test13", "NULLABLE_COL", "null?"));

        logger.info("DEFAULT with NULL works correctly");
    }

    @Test
    public void testDefaultWithBoolean() {
        logger.info("Testing DEFAULT with boolean expression");

        engine.execute("CREATE TABLE test14 (id INTEGER, is_active BOOLEAN DEFAULT TRUE)");

        assertDefaultContains("test14", "IS_ACTIVE", "TRUE");

        logger.info("DEFAULT with boolean works correctly");
    }

    @Test
    public void testDefaultWithDecimalExpression() {
        logger.info("Testing DEFAULT with decimal expression");

        engine.execute("CREATE TABLE test15 (id INTEGER, rate DECIMAL DEFAULT 3.14 * 2)");

        assertDefaultContains("test15", "RATE", "3.14");

        logger.info("DEFAULT with decimal expression works correctly");
    }
}
