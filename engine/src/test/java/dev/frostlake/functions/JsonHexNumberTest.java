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

package dev.frostlake.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Snowflake's JSON reader reads a HEXADECIMAL number: {@code 0x} or {@code 0X} and hex digits is an exact
 * integer up to the signed 128-bit range, and a hex fraction or a binary exponent makes it a DOUBLE. A
 * leading plus belongs to the token, a leading minus never does, and {@code 0x} with no digits and an
 * integer past the range each have a sentence of their own. Frostlake refused every hex number as garbage
 * in the numeric literal (live-verified).
 */
public class JsonHexNumberTest extends BaseDatabaseTest {

    private static final String PREFIX = "Error parsing JSON: ";

    /** The one cell of a one-row query, as text. */
    private String cell(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        return String.valueOf(result.getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aHexIntegerIsReadExactly() {
        assertEquals("16", cell("SELECT TO_JSON(PARSE_JSON('0x10'))"));
        assertEquals("INTEGER", cell("SELECT TYPEOF(PARSE_JSON('0x10'))"));
        assertEquals("26", cell("SELECT TO_JSON(PARSE_JSON('0X1A'))"));
        assertEquals("16", cell("SELECT TO_JSON(PARSE_JSON('+0x10'))"));
        assertEquals("0", cell("SELECT TO_JSON(PARSE_JSON('0x0'))"));
        assertEquals("11259375", cell("SELECT TO_JSON(PARSE_JSON('0xABCDEF'))"));
        assertEquals("4325", cell("SELECT TO_JSON(PARSE_JSON('0x10e5'))"));
        assertEquals("[31,16]", cell("SELECT TO_JSON(PARSE_JSON('[0x1F, 0X10]'))"));
        assertEquals("{\"a\":255}", cell("SELECT TO_JSON(PARSE_JSON('{\"a\":0xff}'))"));
        assertEquals("{\"a\":[1,{\"b\":2}]}", cell("SELECT TO_JSON(PARSE_JSON('{\"a\":[0x1,{\"b\":0x2}]}'))"));
        assertEquals("16", cell("SELECT PARSE_JSON('0x10')::INT::VARCHAR"));
        // Past 64 bits, exactly, up to the signed 128-bit range.
        assertEquals("9223372036854775807", cell("SELECT TO_JSON(PARSE_JSON('0x7FFFFFFFFFFFFFFF'))"));
        assertEquals("18446744073709551616", cell("SELECT TO_JSON(PARSE_JSON('0x10000000000000000'))"));
        assertEquals("170141183460469231731687303715884105727",
            cell("SELECT TO_JSON(PARSE_JSON('0x7FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF'))"));
        assertEquals("INTEGER", cell("SELECT TYPEOF(PARSE_JSON('0x10000000000000000'))"));
        // A hex number inside a string is text, as it always was.
        assertEquals("{\"a\":\"0x1\"}", cell("SELECT TO_JSON(PARSE_JSON('{\"a\":\"0x1\"}'))"));
    }

    @Test
    public void aHexFractionOrExponentIsADouble() {
        assertEquals("DOUBLE", cell("SELECT TYPEOF(PARSE_JSON('0x1.5'))"));
        assertEquals("1.312500000000000e+00", cell("SELECT TO_JSON(PARSE_JSON('0x1.5'))"));
        assertEquals("1.000000000000000e+00", cell("SELECT TO_JSON(PARSE_JSON('0x1.'))"));
        assertEquals("3.125000000000000e-01", cell("SELECT TO_JSON(PARSE_JSON('0x.5'))"));
        assertEquals("1.600000000000000e+01", cell("SELECT TO_JSON(PARSE_JSON('0x10.'))"));
        assertEquals("8.000000000000000e+00", cell("SELECT TO_JSON(PARSE_JSON('0x1p3'))"));
        assertEquals("2.500000000000000e-01", cell("SELECT TO_JSON(PARSE_JSON('0x1p-2'))"));
        assertEquals("6.000000000000000e+00", cell("SELECT TO_JSON(PARSE_JSON('0x1.8p+2'))"));
        assertEquals("1.024000000000000e+03", cell("SELECT TO_JSON(PARSE_JSON('0x1.0p10'))"));
        assertEquals("1.797693134862316e+308", cell("SELECT TO_JSON(PARSE_JSON('0x1.fffffffffffffp1023'))"));
        assertEquals("1.3125", cell("SELECT PARSE_JSON('0x1.5')::FLOAT::VARCHAR"));
        assertEquals("{\"a\":1.312500000000000e+00}", cell("SELECT TO_JSON(PARSE_JSON('{\"a\":0x1.5}'))"));
    }

    @Test
    public void whatTheReaderWillNotConvertKeepsItsOwnSentence() {
        assertEquals(PREFIX + "hexadecimal integer number conversion error: 0x, pos 3", refusal("SELECT PARSE_JSON('0x')"));
        assertEquals("hexadecimal integer number conversion error: 0x, pos 3", cell("SELECT CHECK_JSON('0x')"));
        assertEquals(PREFIX + "hexadecimal integer number conversion error: "
            + "0x80000000000000000000000000000000, pos 35",
            refusal("SELECT PARSE_JSON('0x80000000000000000000000000000000')"));
        assertEquals(PREFIX + "missing hexadecimal exponent digits: '0x1p', pos 5", refusal("SELECT PARSE_JSON('0x1p')"));
        assertEquals(PREFIX + "garbage in the numeric literal: -0x10 , pos 6", refusal("SELECT PARSE_JSON('-0x10')"));
        assertEquals(PREFIX + "garbage in the numeric literal: 0xg , pos 4", refusal("SELECT PARSE_JSON('0xg')"));
        assertEquals(PREFIX + "garbage in the numeric literal: 0x10x , pos 6", refusal("SELECT PARSE_JSON('0x10x')"));
        assertEquals(PREFIX + "garbage in the numeric literal: 00x10 , pos 6", refusal("SELECT PARSE_JSON('00x10')"));
        assertEquals(PREFIX + "garbage in the numeric literal: 0x1.5.5 , pos 8", refusal("SELECT PARSE_JSON('0x1.5.5')"));
        assertEquals(PREFIX + "missing comma, pos 6", refusal("SELECT PARSE_JSON('[0x1 0x2]')"));
        assertEquals("null", String.valueOf(cell("SELECT TRY_PARSE_JSON('0xg')")));
        assertEquals("null", String.valueOf(cell("SELECT CHECK_JSON('0x10')")));
    }
}
