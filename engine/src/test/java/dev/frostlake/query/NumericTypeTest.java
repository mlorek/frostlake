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
import dev.frostlake.ExecutionResult;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for numeric type handling
 */
public class NumericTypeTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(NumericTypeTest.class);

    @Override
    protected void setupTest() {
        // No setup needed
    }

    @Test
    public void testIntegerLiteralReturnsLong() {
        logger.info("Testing SELECT 2 returns Long");

        ExecutionResult result = engine.execute("SELECT 2");
        ResultSet rs = result.getResultSets().get(0);

        assertEquals(1, rs.getRows().size());
        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        assertTrue(value instanceof Long, "Expected Long but got " + value.getClass().getName());
        assertEquals(2L, value);
    }

    @Test
    public void testDecimalLiteralReturnsDecimal() {
        logger.info("Testing SELECT 2.5 returns BigDecimal");

        ExecutionResult result = engine.execute("SELECT 2.5");
        ResultSet rs = result.getResultSets().get(0);

        assertEquals(1, rs.getRows().size());
        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        assertTrue(value instanceof BigDecimal,
            "Expected BigDecimal but got " + value.getClass().getName());
    }

    @Test
    public void testNegativeIntegerReturnsLong() {
        logger.info("Testing SELECT -42 returns Long");

        ExecutionResult result = engine.execute("SELECT -42");
        ResultSet rs = result.getResultSets().get(0);

        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        assertTrue(value instanceof Long, "Expected Long but got " + value.getClass().getName());
        assertEquals(-42L, value);
    }

    @Test
    public void testArithmeticPreservesTypes() {
        logger.info("Testing 2 + 3 returns Long");

        ExecutionResult result = engine.execute("SELECT 2 + 3");
        ResultSet rs = result.getResultSets().get(0);

        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        assertTrue(value instanceof Long, "Expected Long but got " + value.getClass().getName());
        assertEquals(5L, value);
    }

    @Test
    public void testIntegerMultiplication() {
        logger.info("Testing 3 * 4 returns Long");

        ExecutionResult result = engine.execute("SELECT 3 * 4");
        ResultSet rs = result.getResultSets().get(0);

        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        assertTrue(value instanceof Long, "Expected Long but got " + value.getClass().getName());
        assertEquals(12L, value);
    }

    @Test
    public void testIntegerSubtraction() {
        logger.info("Testing 10 - 3 returns Long");

        ExecutionResult result = engine.execute("SELECT 10 - 3");
        ResultSet rs = result.getResultSets().get(0);

        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        assertTrue(value instanceof Long, "Expected Long but got " + value.getClass().getName());
        assertEquals(7L, value);
    }

    @Test
    public void testDivisionReturnsDouble() {
        logger.info("Testing 10 / 3 returns Double (for precision)");

        ExecutionResult result = engine.execute("SELECT 10 / 3");
        ResultSet rs = result.getResultSets().get(0);

        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        assertTrue(value instanceof Double, "Expected Double but got " + value.getClass().getName());
        assertEquals(3.3333333333333335, (Double) value, 0.0001);
    }

    @Test
    public void testMixedArithmeticReturnsBigDecimal() {
        logger.info("Testing 2 + 3.5 returns BigDecimal");

        ExecutionResult result = engine.execute("SELECT 2 + 3.5");
        ResultSet rs = result.getResultSets().get(0);

        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        // When mixing integer and decimal literal, result should be BigDecimal
        assertTrue(value instanceof BigDecimal, "Expected BigDecimal but got " + value.getClass().getName());
        assertEquals(new BigDecimal("5.5"), value);
    }

    @Test
    public void testZeroReturnsLong() {
        logger.info("Testing SELECT 0 returns Long");

        ExecutionResult result = engine.execute("SELECT 0");
        ResultSet rs = result.getResultSets().get(0);

        Row row = rs.getRows().get(0);
        Object value = row.getValues().get(0);

        logger.info("Value: {} (type: {})", value, value.getClass().getName());

        assertTrue(value instanceof Long, "Expected Long but got " + value.getClass().getName());
        assertEquals(0L, value);
    }
}
