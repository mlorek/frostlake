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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The base64 and hex encoders carry optional arguments Frostlake declared away, refusing calls a real
 * account runs. They are not the same shape, and neither is a flag on both:
 *
 * <pre>
 *   BASE64_ENCODE(expr [, max_line_length [, alphabet]])   up to THREE
 *   HEX_ENCODE(expr [, case])                              up to TWO
 *   BASE64_DECODE_STRING / _BINARY and their TRY_ forms    up to TWO — the alphabet, to read it back
 *   HEX_DECODE_STRING / _BINARY and their TRY_ forms       ONE, and a second is refused on both
 * </pre>
 *
 * <p>The alphabet's two characters stand in for the standard {@code +} and {@code /}, so a value
 * encoded with one only reads back through the same one — decoding it with the standard alphabet is
 * an error, because {@code $} and {@code %} are not base64 at all.
 */
public class EncodeOptionalArgumentsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE eb (bn BINARY(5), v VARCHAR(200))");
        engine.execute("INSERT INTO eb SELECT TO_BINARY('AB1234'), 'hello world'");
    }

    /** The answer with newlines made visible, or the message of the refusal it raised. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery(
                "SELECT COALESCE(TO_VARCHAR(" + expr + "), '<NULL>') AS x FROM eb");
            rs.next();
            return String.valueOf(rs.getValue(0)).replace("\n", "\\n");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** A max line length of 0 — the default — does not wrap. */
    @Test
    public void aZeroLineLengthIsTheUnwrappedDefault() {
        assertEquals("qxI0", outcome("BASE64_ENCODE(bn)"));
        assertEquals("qxI0", outcome("BASE64_ENCODE(bn, 0)"));
        assertEquals("aGVsbG8gd29ybGQ=", outcome("BASE64_ENCODE(v)"));
        assertEquals("aGVsbG8gd29ybGQ=", outcome("BASE64_ENCODE(v, 0)"));
    }

    /** A positive one breaks the output every N characters, with none left trailing. */
    @Test
    public void aPositiveLineLengthWraps() {
        assertEquals("aGVs\\nbG8g\\nd29y\\nbGQ=", outcome("BASE64_ENCODE(v, 4)"));
        assertEquals("aGVsbG8g\\nd29ybGQ=", outcome("BASE64_ENCODE(v, 8)"));
        assertEquals("aGVsbG8gd29ybGQ=", outcome("BASE64_ENCODE(v, 76)"),
            "wider than the value, so nothing to wrap");
    }

    /** A negative literal is refused while the statement COMPILES — see Base64ArgumentDecisionTest
     *  for when each of these arguments is judged, and for the literal-versus-value distinction. */
    @Test
    public void aNegativeLineLengthIsRefused() {
        assertTrue(outcome("BASE64_ENCODE(v, -1)").contains(
                "invalid argument for function [BASE64_ENCODE]"
                    + " unexpected argument [maximum line length] at position 2,"),
            "got: " + outcome("BASE64_ENCODE(v, -1)"));
    }

    /** The alphabet's two characters replace the standard + and /, in that order. */
    @Test
    public void theAlphabetReplacesPlusAndSlash() {
        assertEquals("+/8=", outcome("BASE64_ENCODE(TO_BINARY('FBFF', 'HEX'))"));
        assertEquals("$%8=", outcome("BASE64_ENCODE(TO_BINARY('FBFF', 'HEX'), 0, '$%')"));
        assertEquals("+/8=", outcome("BASE64_ENCODE(TO_BINARY('FBFF', 'HEX'), 0, '+/')"),
            "the standard alphabet, spelled out");
    }

    /** A custom alphabet only reads back through the same alphabet. */
    @Test
    public void aCustomAlphabetRoundTripsOnlyWithItself() {
        assertEquals("FBFF", outcome(
            "BASE64_DECODE_BINARY(BASE64_ENCODE(TO_BINARY('FBFF', 'HEX'), 0, '$%'), '$%')"));
        assertEquals("The following string is not a legal base64-encoded value: '$%8='", outcome(
            "BASE64_DECODE_BINARY(BASE64_ENCODE(TO_BINARY('FBFF', 'HEX'), 0, '$%'))"));
        assertEquals("hello world",
            outcome("BASE64_DECODE_STRING(BASE64_ENCODE(v), '+/')"));
        assertEquals("hello world",
            outcome("TRY_BASE64_DECODE_STRING(BASE64_ENCODE(v), '+/')"));
        assertEquals("AB1234",
            outcome("TRY_BASE64_DECODE_BINARY(BASE64_ENCODE(bn), '+/')"));
    }

    /** HEX_ENCODE's second argument is a case flag: 1 upper (the default), 0 lower. */
    @Test
    public void hexEncodeTakesACaseFlag() {
        assertEquals("AB1234", outcome("HEX_ENCODE(bn)"));
        assertEquals("AB1234", outcome("HEX_ENCODE(bn, 1)"));
        assertEquals("ab1234", outcome("HEX_ENCODE(bn, 0)"));
        assertEquals("68656c6c6f20776f726c64", outcome("HEX_ENCODE(v, 0)"));
    }

    /** Any other case value is out of range, and says so at row time rather than compile time. */
    @Test
    public void anyOtherCaseValueIsOutOfRange() {
        assertEquals("Numeric value is out of range, error: 2", outcome("HEX_ENCODE(bn, 2)"));
        assertEquals("Numeric value is out of range, error: -1", outcome("HEX_ENCODE(bn, -1)"));
    }

    /** The hex DECODERS take one argument only, and the second is refused on both engines. */
    @Test
    public void theHexDecodersStayOneArgument() {
        assertTrue(outcome("HEX_DECODE_STRING(HEX_ENCODE(v), 0)")
            .contains("too many arguments for function"), "got: "
            + outcome("HEX_DECODE_STRING(HEX_ENCODE(v), 0)"));
        assertTrue(outcome("HEX_DECODE_BINARY(HEX_ENCODE(bn), 0)")
            .contains("expected 1, got 2"));
        assertTrue(outcome("TRY_HEX_DECODE_STRING(HEX_ENCODE(v), 0)")
            .contains("expected 1, got 2"));
    }

    /** And one argument past each maximum is refused, with the account's own counts. */
    @Test
    public void oneArgumentPastTheMaximumIsRefused() {
        assertTrue(outcome("BASE64_ENCODE(bn, 0, '+/', 1)").contains("expected 3, got 4"),
            "got: " + outcome("BASE64_ENCODE(bn, 0, '+/', 1)"));
        assertTrue(outcome("HEX_ENCODE(bn, 1, 1)").contains("expected 2, got 3"),
            "got: " + outcome("HEX_ENCODE(bn, 1, 1)"));
    }
}
