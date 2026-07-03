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

package dev.frostlake.types;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BinaryTypeTest {

    private static final Logger logger = LoggerFactory.getLogger(BinaryTypeTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testCreateTableWithBinaryColumn() {
        logger.info("Testing CREATE TABLE with BINARY column");

        engine.execute("CREATE TABLE binary_test (id INTEGER, data BINARY)");

        ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE binary_test");
        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should have 2 columns");
    }

    @Test
    public void testCreateTableWithVarBinaryColumn() {
        logger.info("Testing CREATE TABLE with VARBINARY column");

        engine.execute("CREATE TABLE varbinary_test (id INTEGER, data VARBINARY)");

        ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE varbinary_test");
        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should have 2 columns");
    }

    @Test
    public void testDeclareVariableWithBinaryType() {
        logger.info("Testing DECLARE variable with BINARY type");

        engine.execute("CREATE TABLE results (data BINARY)");

        engine.execute("""
            DECLARE binary_var BINARY := '0x48656C6C6F';
            INSERT INTO results VALUES (binary_var);
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount(), "Should have 1 row");

        Object value = rs.getRows().get(0).getValue(0);
        logger.info("Binary value: {}", value);
        assertNotNull(value, "Binary value should not be null");
    }

    @Test
    public void testDeclareVariableWithVarBinaryType() {
        logger.info("Testing DECLARE variable with VARBINARY type");

        engine.execute("CREATE TABLE results (data VARBINARY)");

        engine.execute("""
            DECLARE varbinary_var VARBINARY := '0x576F726C64';
            INSERT INTO results VALUES (varbinary_var);
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount(), "Should have 1 row");

        Object value = rs.getRows().get(0).getValue(0);
        logger.info("VarBinary value: {}", value);
        assertNotNull(value, "VarBinary value should not be null");
    }

    @Test
    public void testCastToBinary() {
        logger.info("Testing CAST to BINARY");

        ResultSet rs = engine.executeQuery("SELECT CAST('Hello' AS BINARY) as result");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        Object value = rs.getRows().get(0).getValue(0);
        logger.info("Cast result: {}", value);

        assertTrue(value instanceof byte[] || value instanceof String,
                   "Value should be byte array or string");
    }

    @Test
    public void testCastToVarBinary() {
        logger.info("Testing CAST to VARBINARY");

        ResultSet rs = engine.executeQuery("SELECT CAST('World' AS VARBINARY) as result");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        Object value = rs.getRows().get(0).getValue(0);
        logger.info("Cast result: {}", value);

        assertTrue(value instanceof byte[] || value instanceof String,
                   "Value should be byte array or string");
    }

    @Test
    public void testBinaryTypeCompatibility() {
        logger.info("Testing BinaryType compatibility");

        BinaryType binary1 = new BinaryType("BINARY", 100);
        BinaryType binary2 = new BinaryType("VARBINARY", 200);

        assertTrue(binary1.isCompatible(binary2),
                   "BINARY should be compatible with VARBINARY");
    }

    @Test
    public void testBinaryTypeCommonType() {
        logger.info("Testing BinaryType common type");

        BinaryType binary1 = new BinaryType("BINARY", 100);
        BinaryType binary2 = new BinaryType("VARBINARY", 200);

        DataType commonType = binary1.getCommonType(binary2);
        assertNotNull(commonType, "Common type should not be null");
        assertEquals("VARBINARY", commonType.getName(),
                     "Common type should be VARBINARY");
        assertTrue(commonType instanceof BinaryType,
                   "Common type should be BinaryType");
    }

    @Test
    public void testBinaryTypeParseHexString() {
        logger.info("Testing BinaryType parse hex string");

        BinaryType binaryType = BinaryType.BINARY;
        byte[] result = (byte[]) binaryType.parseValue("0x48656C6C6F");

        assertNotNull(result, "Parsed value should not be null");
        assertEquals(5, result.length, "Should have 5 bytes");
        assertArrayEquals("Hello".getBytes(), result,
                          "Parsed bytes should match 'Hello'");
    }

    @Test
    public void testBinaryTypeFormatValue() {
        logger.info("Testing BinaryType format value");

        BinaryType binaryType = BinaryType.BINARY;
        String formatted = binaryType.formatValue("Hello".getBytes());

        assertNotNull(formatted, "Formatted value should not be null");
        assertTrue(formatted.startsWith("0x"),
                   "Formatted value should start with 0x");
        assertEquals("0x48656C6C6F", formatted,
                     "Formatted value should be hex string");
    }

    @Test
    public void testLetStatementWithBinaryType() {
        logger.info("Testing LET statement with BINARY type");

        engine.execute("CREATE TABLE results (data BINARY)");

        engine.execute("""
            BEGIN
                LET bin_var BINARY := '0x414243';
                INSERT INTO results VALUES (bin_var);
            END;
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount(), "Should have 1 row");

        Object value = rs.getRows().get(0).getValue(0);
        logger.info("Binary value from LET: {}", value);
        assertNotNull(value, "Binary value should not be null");
    }

    @Test
    public void testReturnBinaryFromBeginEndBlock() {
        logger.info("Testing RETURN BINARY from BEGIN...END block");

        ResultSet rs = engine.executeQuery("""
            BEGIN
                LET bin_data BINARY := '0x54657374';
                RETURN bin_data;
            END;
            """);

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned binary value: {}", value);
        assertNotNull(value, "Returned binary should not be null");
    }
}
