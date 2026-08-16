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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Three calls Frostlake used to answer where the account refuses.
 *
 * <p>The first is a ROW-time refusal: a {@code _STRING} decoder hands back a VARCHAR, so bytes that
 * spell no UTF-8 string are an error rather than a lossy conversion — Java's decoder substitutes the
 * replacement character, and a one-byte 0xAB came back as a one-character string. Live answers
 * {@code Invalid UTF8 detected while decoding 'qw=='}, quoting the INPUT as written, and the TRY_
 * spellings answer NULL.
 *
 * <p>The other two are COMPILE-time argument-type refusals, each keyed to a family the function will
 * not read: JSON_EXTRACT_PATH_TEXT takes JSON text or a VARIANT and refuses an OBJECT,
 * TRY_VALIDATE_UTF8 reads its argument as text and refuses a BINARY. Both name the offending types
 * with the argument list's own widths.
 */
public class DecodedTextAndArgumentRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE eb (bn BINARY(5), v VARCHAR(20), vt VARIANT, i INT)");
        engine.execute("INSERT INTO eb SELECT TO_BINARY('AB'), 'hello', PARSE_JSON('{\"a\": 1}'), 7");
    }

    /** The one-column answer, or the refusal's message. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " AS x FROM eb");
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Bytes that are not UTF-8 cannot come back as a string. */
    @Test
    public void aStringDecoderRefusesBytesThatAreNotText() {
        assertEquals("Invalid UTF8 detected while decoding 'qw=='",
            outcome("BASE64_DECODE_STRING('qw==')"));
        assertEquals("Invalid UTF8 detected while decoding 'qw=='",
            outcome("BASE64_DECODE_STRING(BASE64_ENCODE(bn))"));
        assertEquals("Invalid UTF8 detected while decoding 'AB'",
            outcome("HEX_DECODE_STRING('AB')"));
    }

    /** The TRY_ spellings answer NULL for the same input. */
    @Test
    public void theTryFormsAnswerNull() {
        assertNull(engine.executeQuery("SELECT TRY_BASE64_DECODE_STRING('qw==') AS x FROM eb")
            .getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT TRY_HEX_DECODE_STRING('AB') AS x FROM eb")
            .getRows().get(0).getValue(0));
    }

    /** Text that IS valid still decodes, and the _BINARY decoders never read the bytes at all. */
    @Test
    public void validTextStillDecodes() {
        assertEquals("hello", outcome("BASE64_DECODE_STRING('aGVsbG8=')"));
        assertEquals("hello", outcome("BASE64_DECODE_STRING(BASE64_ENCODE(v))"));
        assertEquals("AB", outcome("BASE64_DECODE_BINARY(BASE64_ENCODE(bn))"));
        assertEquals("hello", outcome("TRY_BASE64_DECODE_STRING('aGVsbG8=')"));
    }

    /** JSON_EXTRACT_PATH_TEXT will not read an OBJECT, and says so at compile time. */
    @Test
    public void jsonExtractPathTextRefusesAnObject() {
        assertEquals("SQL compilation error: error line 1 at position 7 Invalid argument types for"
            + " function 'JSON_EXTRACT_PATH_TEXT': (OBJECT, VARCHAR(1))",
            outcome("JSON_EXTRACT_PATH_TEXT(OBJECT_CONSTRUCT('a', 1), 'a')"));
    }

    /** The spellings it DOES read still answer — a VARIANT, a JSON string, even a number. */
    @Test
    public void jsonExtractPathTextStillReadsWhatItTakes() {
        assertEquals("1", outcome("JSON_EXTRACT_PATH_TEXT(vt, 'a')"));
        assertEquals("1", outcome("JSON_EXTRACT_PATH_TEXT('{\"a\": 1}', 'a')"));
        assertEquals("null", outcome("JSON_EXTRACT_PATH_TEXT(i, 'a')"));
    }

    /** TRY_VALIDATE_UTF8 reads text, so a BINARY is an argument-type error — with its own width. */
    @Test
    public void tryValidateUtf8RefusesABinary() {
        assertEquals("SQL compilation error: error line 1 at position 7 Invalid argument types for"
            + " function 'TRY_VALIDATE_UTF8': (BINARY(5))",
            outcome("TRY_VALIDATE_UTF8(bn)"));
        assertEquals("hello", outcome("TRY_VALIDATE_UTF8(v)"));
        assertEquals("7", outcome("TRY_VALIDATE_UTF8(i)"));
    }
}
