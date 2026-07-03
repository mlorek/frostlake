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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for positional parameters in expressions
 */
public class PositionalParameterExpressionsTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(PositionalParameterExpressionsTest.class);

    @Override
    protected void setupTest() {
        // No setup needed
    }

    @Test
    public void testPositionalParameterWithAbsFunction() {
        logger.info("Testing SELECT $1, ABS($2) FROM VALUES");

        ResultSet result = engine.executeQuery("SELECT $1, ABS($2) FROM VALUES(1, -2)");

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(new BigDecimal("2"), result.getRows().get(0).getValue(1));
    }

    @Test
    public void testPositionalParameterWithMultipleFunctions() {
        logger.info("Testing positional parameters with multiple functions");

        ResultSet result = engine.executeQuery("""
            SELECT ABS($1), UPPER($2), LENGTH($3)
            FROM VALUES(-5, 'hello', 'world')
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(3, result.getColumnCount());
        assertEquals(new BigDecimal("5"), result.getRows().get(0).getValue(0));
        assertEquals("HELLO", result.getRows().get(0).getValue(1));
        assertEquals(5, result.getRows().get(0).getValue(2));
    }

    @Test
    public void testPositionalParameterInNestedFunctions() {
        logger.info("Testing positional parameters in nested functions");

        ResultSet result = engine.executeQuery("""
            SELECT UPPER(LOWER($1)), ABS(ABS($2))
            FROM VALUES('TeSt', -10)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals("TEST", result.getRows().get(0).getValue(0));
        assertEquals(new BigDecimal("10"), result.getRows().get(0).getValue(1));
    }

    @Test
    public void testPositionalParameterInArithmeticWithFunctions() {
        logger.info("Testing positional parameters in arithmetic with functions");

        ResultSet result = engine.executeQuery("""
            SELECT ABS($1) + ABS($2) AS sum
            FROM VALUES(-3, -4)
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(1, result.getColumnCount());
        assertEquals(new BigDecimal("7"), result.getRows().get(0).getValue(0));
    }

    @Test
    public void testPositionalParameterWithCast() {
        logger.info("Testing positional parameters with CAST");

        ResultSet result = engine.executeQuery("""
            SELECT CAST($1 AS VARCHAR), CAST($2 AS INTEGER)
            FROM VALUES(123, '456')
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumnCount());
        assertEquals("123", result.getRows().get(0).getValue(0));
        assertEquals(456L, result.getRows().get(0).getValue(1));
    }

    @Test
    public void testPositionalParameterWithConcat() {
        logger.info("Testing positional parameters with string concatenation");

        ResultSet result = engine.executeQuery("""
            SELECT $1 || ' ' || $2 AS full_name
            FROM VALUES('John', 'Doe')
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(1, result.getColumnCount());
        assertEquals("John Doe", result.getRows().get(0).getValue(0));
    }

    @Test
    public void testPositionalParameterWithCase() {
        logger.info("Testing positional parameters in CASE expression");

        ResultSet result = engine.executeQuery("""
            SELECT CASE WHEN $1 > 10 THEN 'High' ELSE 'Low' END
            FROM VALUES(5), (15)
            """);

        assertEquals(2, result.getRowCount());
        assertEquals(1, result.getColumnCount());
        assertEquals("Low", result.getRows().get(0).getValue(0));
        assertEquals("High", result.getRows().get(1).getValue(0));
    }

    @Test
    public void testPositionalParameterWithCoalesce() {
        logger.info("Testing positional parameters with COALESCE");

        ResultSet result = engine.executeQuery("""
            SELECT COALESCE($1, $2, $3)
            FROM VALUES(NULL, NULL, 'default')
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(1, result.getColumnCount());
        assertEquals("default", result.getRows().get(0).getValue(0));
    }

    @Test
    public void testPositionalParameterWithSubstring() {
        logger.info("Testing positional parameters with SUBSTRING");

        ResultSet result = engine.executeQuery("""
            SELECT SUBSTRING($1, 1, 3)
            FROM VALUES('Hello World')
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(1, result.getColumnCount());
        assertEquals("Hel", result.getRows().get(0).getValue(0));
    }

    @Test
    public void testMixedPositionalParametersAndFunctions() {
        logger.info("Testing mixed positional parameters and functions");

        ResultSet result = engine.executeQuery("""
            SELECT $1, ABS($2), $3 * 2, UPPER($4)
            FROM VALUES(10, -20, 5, 'test')
            """);

        assertEquals(1, result.getRowCount());
        assertEquals(4, result.getColumnCount());
        assertEquals(10L, result.getRows().get(0).getValue(0));
        assertEquals(new BigDecimal("20"), result.getRows().get(0).getValue(1));
        assertEquals(10L, result.getRows().get(0).getValue(2));
        assertEquals("TEST", result.getRows().get(0).getValue(3));
    }
}
