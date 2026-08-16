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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for CAST expressions with type parameters
 * Format: CAST(expr AS datatype(params)) and expr::datatype(params)
 */
public class CastWithTypeParametersTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(CastWithTypeParametersTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE test_data (id INTEGER, name VARCHAR, value NUMBER)");
        engine.execute("INSERT INTO test_data VALUES (1, 'Alice', 123.456)");
        engine.execute("INSERT INTO test_data VALUES (2, 'Bob', 789.012)");
        logger.info("DatabaseEngine initialized for CAST with type parameters tests");
    }

    @Test
    public void testCastToVarcharWithLength() {
        logger.info("Testing CAST to VARCHAR with length parameter");

        final ResultSet result = engine.executeQuery("SELECT CAST(id AS VARCHAR(10)) as id_str FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("1", result.getRows().get(0).getValue(0).toString());

        logger.info("CAST to VARCHAR(10) works correctly");
    }

    @Test
    public void testCastToVarcharWithLargeLength() {
        logger.info("Testing CAST to VARCHAR with large length parameter");

        final ResultSet result = engine.executeQuery("SELECT CAST(name AS VARCHAR(16777216)) as name_str FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("Alice", result.getRows().get(0).getValue(0).toString());

        logger.info("CAST to VARCHAR(16777216) works correctly");
    }

    @Test
    public void testCastToDecimalWithPrecision() {
        logger.info("Testing CAST to DECIMAL with precision parameter");

        final ResultSet result = engine.executeQuery("SELECT CAST(value AS DECIMAL(10)) as decimal_val FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());

        logger.info("CAST to DECIMAL(10) works correctly");
    }

    @Test
    public void testCastToDecimalWithPrecisionAndScale() {
        logger.info("Testing CAST to DECIMAL with precision and scale parameters");

        final ResultSet result = engine.executeQuery("SELECT CAST(value AS DECIMAL(10, 2)) as decimal_val FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());

        logger.info("CAST to DECIMAL(10, 2) works correctly");
    }

    @Test
    public void testCastToNumberWithPrecision() {
        logger.info("Testing CAST to NUMBER with precision parameter");

        final ResultSet result = engine.executeQuery("SELECT CAST(value AS NUMBER(15)) as num_val FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());

        logger.info("CAST to NUMBER(15) works correctly");
    }

    @Test
    public void testCastToNumberWithPrecisionAndScale() {
        logger.info("Testing CAST to NUMBER with precision and scale parameters");

        final ResultSet result = engine.executeQuery("SELECT CAST(value AS NUMBER(15, 3)) as num_val FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());

        logger.info("CAST to NUMBER(15, 3) works correctly");
    }

    @Test
    public void testDoubleColonCastWithVarcharLength() {
        logger.info("Testing :: cast operator to VARCHAR with length parameter");

        final ResultSet result = engine.executeQuery("SELECT id::VARCHAR(50) as id_str FROM test_data WHERE id = 2");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("2", result.getRows().get(0).getValue(0).toString());

        logger.info(":: cast to VARCHAR(50) works correctly");
    }

    @Test
    public void testDoubleColonCastWithDecimal() {
        logger.info("Testing :: cast operator to DECIMAL with parameters");

        final ResultSet result = engine.executeQuery("SELECT value::DECIMAL(20, 5) as decimal_val FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());

        logger.info(":: cast to DECIMAL(20, 5) works correctly");
    }

    @Test
    public void testCastWithTypeParametersInExpression() {
        logger.info("Testing CAST with type parameters in complex expression");

        final ResultSet result = engine.executeQuery("SELECT CAST((id + 100) AS VARCHAR(100)) as result FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("101", result.getRows().get(0).getValue(0).toString());

        logger.info("CAST with type parameters in expression works correctly");
    }

    @Test
    public void testMultipleCastsWithTypeParameters() {
        logger.info("Testing multiple CAST operations with type parameters in one query");

        final ResultSet result = engine.executeQuery("""
            SELECT
                CAST(id AS VARCHAR(20)) as id_str,
                CAST(value AS DECIMAL(10, 2)) as value_decimal
            FROM test_data
            WHERE id = 1
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("1", result.getRows().get(0).getValue(0).toString());

        logger.info("Multiple CAST operations with type parameters work correctly");
    }

    @Test
    public void testCastInConcatenationWithTypeParameters() {
        logger.info("Testing CAST with type parameters in concatenation");

        final ResultSet result = engine.executeQuery("""
            SELECT 'ID: ' || CAST(id AS VARCHAR(10)) as result
            FROM test_data
            WHERE id = 1
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("ID: 1", result.getRows().get(0).getValue(0).toString());

        logger.info("CAST with type parameters in concatenation works correctly");
    }

    @Test
    public void testNestedCastWithTypeParameters() {
        logger.info("Testing nested CAST with type parameters");

        final ResultSet result = engine.executeQuery("""
            SELECT CAST(CAST(id AS VARCHAR(50)) AS INTEGER) as result
            FROM test_data
            WHERE id = 1
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));

        logger.info("Nested CAST with type parameters works correctly");
    }

    @Test
    public void testCastToTimestampWithPrecision() {
        logger.info("Testing CAST to TIMESTAMP with precision parameter");

        final ResultSet result = engine.executeQuery("SELECT CAST('2024-01-01 12:00:00' AS TIMESTAMP(6)) as ts_val FROM test_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());

        logger.info("CAST to TIMESTAMP(6) works correctly");
    }

    @Test
    public void testCastMixedWithAndWithoutTypeParameters() {
        logger.info("Testing mix of CAST with and without type parameters");

        final ResultSet result = engine.executeQuery("""
            SELECT
                CAST(id AS INTEGER) as int_val,
                CAST(name AS VARCHAR(100)) as name_str
            FROM test_data
            WHERE id = 1
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("Alice", result.getRows().get(0).getValue(1).toString());

        logger.info("Mix of CAST with and without type parameters works correctly");
    }

    @Test
    public void testDoubleColonCastWithNumber() {
        logger.info("Testing :: cast operator to NUMBER with parameters");

        final ResultSet result = engine.executeQuery("SELECT value::NUMBER(18, 4) as num_val FROM test_data WHERE id = 2");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());

        logger.info(":: cast to NUMBER(18, 4) works correctly");
    }
}
