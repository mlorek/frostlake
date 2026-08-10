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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.values.BinaryValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BinaryTypeTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BinaryTypeTest.class);

    @Test
    public void testCreateTableWithBinaryColumn() {
        logger.info("Testing CREATE TABLE with BINARY column");

        engine.execute("CREATE TABLE binary_test (id INTEGER, data BINARY)");

        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE binary_test");
        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should have 2 columns");
    }

    @Test
    public void testCreateTableWithVarBinaryColumn() {
        logger.info("Testing CREATE TABLE with VARBINARY column");

        engine.execute("CREATE TABLE varbinary_test (id INTEGER, data VARBINARY)");

        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN TABLE varbinary_test");
        assertNotNull(rs, "Result set should not be null");
        assertEquals(2, rs.getRowCount(), "Should have 2 columns");
    }

    @Test
    public void testDeclareVariableWithBinaryType() {
        logger.info("Testing DECLARE variable with BINARY type");

        engine.execute("CREATE TABLE results (data BINARY)");

        engine.execute("""
            DECLARE binary_var BINARY := '48656C6C6F';
            BEGIN
                INSERT INTO results VALUES (:binary_var);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount(), "Should have 1 row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Binary value: {}", value);
        assertNotNull(value, "Binary value should not be null");
    }

    @Test
    public void testDeclareVariableWithVarBinaryType() {
        logger.info("Testing DECLARE variable with VARBINARY type");

        engine.execute("CREATE TABLE results (data VARBINARY)");

        engine.execute("""
            DECLARE varbinary_var VARBINARY := '576F726C64';
            BEGIN
                INSERT INTO results VALUES (:varbinary_var);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount(), "Should have 1 row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("VarBinary value: {}", value);
        assertNotNull(value, "VarBinary value should not be null");
    }

    @Test
    public void testCastToBinary() {
        logger.info("Testing CAST to BINARY");

        // Snowflake's VARCHAR-to-BINARY cast interprets the string as hex; non-hex text errors.
        final ResultSet rs = engine.executeQuery("SELECT CAST('48656C6C6F' AS BINARY) as result");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Cast result: {}", value);

        assertTrue(value instanceof BinaryValue, "Value should be a BINARY runtime value");
        assertEquals("48656C6C6F", value.toString());

        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CAST('Hello' AS BINARY)");
            }
        });
        assertTrue(e.getMessage().contains("not a legal hex-encoded value"),
            "unexpected: " + e.getMessage());
    }

    @Test
    public void testCastToVarBinary() {
        logger.info("Testing CAST to VARBINARY");

        final ResultSet rs = engine.executeQuery("SELECT CAST('576F726C64' AS VARBINARY) as result");

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Cast result: {}", value);

        assertTrue(value instanceof BinaryValue, "Value should be a BINARY runtime value");
        assertEquals("576F726C64", value.toString());
    }

    @Test
    public void testBinaryTypeCompatibility() {
        logger.info("Testing BinaryType compatibility");

        final BinaryType binary1 = new BinaryType("BINARY", 100);
        final BinaryType binary2 = new BinaryType("VARBINARY", 200);

        assertTrue(binary1.isCompatible(binary2),
                   "BINARY should be compatible with VARBINARY");
    }

    @Test
    public void testBinaryTypeCommonType() {
        logger.info("Testing BinaryType common type");

        final BinaryType binary1 = new BinaryType("BINARY", 100);
        final BinaryType binary2 = new BinaryType("VARBINARY", 200);

        final DataType commonType = binary1.getCommonType(binary2);
        assertNotNull(commonType, "Common type should not be null");
        // Both spellings are NAMED binary — the fold's answer shows in FIXEDNESS instead, and a width
        // widened by a side that is not fixed cannot be a declared width.
        assertEquals("BINARY", commonType.getName(),
                     "Every binary reports the type BINARY");
        assertTrue(commonType instanceof BinaryType,
                   "Common type should be BinaryType");
        assertFalse(((BinaryType) commonType).isFixed(),
                    "Widened by a VARBINARY side, so not fixed");
        assertEquals(200, ((BinaryType) commonType).getMaxLength(),
                     "The wider of the two widths");
    }

    @Test
    public void testBinaryTypeParseHexString() {
        logger.info("Testing BinaryType parse hex string");

        final BinaryType binaryType = BinaryType.BINARY;
        final BinaryValue result = (BinaryValue) binaryType.parseValue("48656C6C6F");

        assertNotNull(result, "Parsed value should not be null");
        assertEquals(5, result.length(), "Should have 5 bytes");
        assertArrayEquals("Hello".getBytes(), result.bytes(),
                          "Parsed bytes should match 'Hello'");
    }

    /**
     * BINARY text is BARE hex. Snowflake's decoder knows no {@code 0x} prefix and refuses one exactly
     * as it refuses any other non-hex text — from a cast, from TO_BINARY and from HEX_DECODE_BINARY
     * alike, in either letter case. Frostlake used to strip it, so these four tests were written in a
     * spelling no account accepts.
     */
    @Test
    public void aHexStringHasNoZeroXPrefix() {
        for (final String written : new String[] {"'0x48656C6C6F'::BINARY",
                                                  "'0X48656C6C6F'::BINARY",
                                                  "TO_BINARY('0x48656C6C6F')",
                                                  "HEX_DECODE_BINARY('0x48656C6C6F')"}) {
            final RuntimeException thrown = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT " + written);
                }
            }, written);
            Throwable root = thrown;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertEquals("The following string is not a legal hex-encoded value: '"
                + (written.contains("0X") ? "0X" : "0x") + "48656C6C6F'", root.getMessage(), written);
        }

        // The TRY_ forms answer NULL rather than raising, and the bare spelling still decodes.
        assertNull(engine.executeQuery("SELECT TRY_TO_BINARY('0x48656C6C6F')")
            .getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_HEX_DECODE_BINARY('0x48656C6C6F')")
            .getRows().get(0).getValue(0));
        assertNotNull(engine.executeQuery("SELECT '48656C6C6F'::BINARY")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void testBinaryTypeFormatValue() {
        logger.info("Testing BinaryType format value");

        final BinaryType binaryType = BinaryType.BINARY;
        final String formatted = binaryType.formatValue("Hello".getBytes());

        assertNotNull(formatted, "Formatted value should not be null");
        assertEquals("48656C6C6F", formatted,
                     "BINARY formats as bare uppercase hex (the Snowflake display form)");
        assertEquals("48656C6C6F", binaryType.formatValue(BinaryValue.fromHex("48656C6C6F")));
    }

    @Test
    public void testLetStatementWithBinaryType() {
        logger.info("Testing LET statement with BINARY type");

        engine.execute("CREATE TABLE results (data BINARY)");

        engine.execute("""
            BEGIN
                LET bin_var BINARY := '414243';
                INSERT INTO results VALUES (:bin_var);
            END;
            """);

        final ResultSet rs = engine.executeQuery("SELECT * FROM results");
        assertEquals(1, rs.getRowCount(), "Should have 1 row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Binary value from LET: {}", value);
        assertNotNull(value, "Binary value should not be null");
    }

    @Test
    public void testReturnBinaryFromBeginEndBlock() {
        logger.info("Testing RETURN BINARY from BEGIN...END block");

        final ResultSet rs = engine.executeQuery("""
            BEGIN
                LET bin_data BINARY := '54657374';
                RETURN bin_data;
            END;
            """);

        assertNotNull(rs, "Result set should not be null");
        assertEquals(1, rs.getRowCount(), "Should return one row");

        final Object value = rs.getRows().get(0).getValue(0);
        logger.info("Returned binary value: {}", value);
        assertNotNull(value, "Returned binary should not be null");
    }
}
