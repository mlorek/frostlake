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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COMPRESS / DECOMPRESS_STRING / DECOMPRESS_BINARY — the contract.
 *
 * <p>Everything asserted here was probed against a live Snowflake account on. Two
 * long-standing divergences are fixed by these tests: the method argument is MANDATORY (Frostlake
 * used to default to {@code deflate}), and the supported set is exactly
 * {@code snappy | zlib | zstd | bz2} — {@code deflate}, {@code raw_deflate} and {@code gzip} used
 * to be accepted here and are rejected by the account.
 *
 * <p>Byte-level agreement with Snowflake lives in {@code CompressionByteFidelityTest}.
 */
public class CompressionFunctionsTest extends BaseDatabaseTest {

    private static final String SAMPLE = "Frostlake compression round-trip test string, "
        + "long enough that the compressed form is genuinely shorter.";

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    private void assertFails(final String sql, final String expectedMessage) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                scalar(sql);
            }
        });
        assertTrue(String.valueOf(e.getMessage()).contains(expectedMessage),
            "expected message containing <" + expectedMessage + "> but was <" + e.getMessage() + ">");
    }

    // ── round-trips, one per supported method ────────────────────────────────

    @Test
    public void snappyRoundTrips() {
        assertEquals(SAMPLE, scalar("SELECT DECOMPRESS_STRING(COMPRESS('" + SAMPLE + "', 'snappy'), 'snappy')"));
    }

    @Test
    public void zlibRoundTrips() {
        assertEquals(SAMPLE, scalar("SELECT DECOMPRESS_STRING(COMPRESS('" + SAMPLE + "', 'zlib'), 'zlib')"));
    }

    @Test
    public void zstdRoundTrips() {
        assertEquals(SAMPLE, scalar("SELECT DECOMPRESS_STRING(COMPRESS('" + SAMPLE + "', 'zstd'), 'zstd')"));
    }

    @Test
    public void bz2RoundTrips() {
        assertEquals(SAMPLE, scalar("SELECT DECOMPRESS_STRING(COMPRESS('" + SAMPLE + "', 'bz2'), 'bz2')"));
    }

    @Test
    public void emptyInputRoundTripsForEveryMethod() {
        assertEquals("", scalar("SELECT DECOMPRESS_STRING(COMPRESS('', 'snappy'), 'snappy')"));
        assertEquals("", scalar("SELECT DECOMPRESS_STRING(COMPRESS('', 'zlib'), 'zlib')"));
        assertEquals("", scalar("SELECT DECOMPRESS_STRING(COMPRESS('', 'zstd'), 'zstd')"));
        assertEquals("", scalar("SELECT DECOMPRESS_STRING(COMPRESS('', 'bz2'), 'bz2')"));
    }

    @Test
    public void roundTripsThroughATableColumn() {
        // COMPRESS is one of the functions live refuses inside a VALUES clause (,
        // "Invalid expression [COMPRESS(…)] in VALUES clause") — INSERT … SELECT is the
        // supported route, and the rejection itself is pinned below.
        engine.execute("CREATE TABLE comp_test (compressed BINARY)");
        engine.execute("INSERT INTO comp_test SELECT COMPRESS('" + SAMPLE + "', 'zlib')");
        assertEquals(SAMPLE, scalar("SELECT DECOMPRESS_STRING(compressed, 'zlib') FROM comp_test"));
        final RuntimeException rejected = org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
                @Override
                public void execute() {
                    engine.execute("INSERT INTO comp_test VALUES (COMPRESS('x', 'zlib'))");
                }
            });
        org.junit.jupiter.api.Assertions.assertTrue(
            rejected.getMessage().contains("Invalid expression [COMPRESS('x', 'zlib')] in VALUES clause"));
    }

    @Test
    public void decompressBinaryReturnsTheOriginalBytes() {
        // 'AB' is 0x41 0x42 in UTF-8, and a BINARY value renders as uppercase hex.
        assertEquals("4142", scalar("SELECT DECOMPRESS_BINARY(COMPRESS('AB', 'zstd'), 'zstd')"));
    }

    @Test
    public void binaryInputRoundTrips() {
        assertEquals("48454C4C4F",
            scalar("SELECT DECOMPRESS_BINARY(COMPRESS(TO_BINARY('48454C4C4F', 'HEX'), 'snappy'), 'snappy')"));
    }

    @Test
    public void compressShrinksRepetitiveInput() {
        final String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        final Object bytes = engine.executeQuery(
            "SELECT LENGTH(COMPRESS('" + text + "', 'zlib'))").getRows().get(0).getValue(0);
        assertTrue(((Number) bytes).intValue() < text.length(),
            "compressed byte count should be smaller than 49 'a's, was " + bytes);
    }

    @Test
    public void differentMethodsProduceDifferentOutput() {
        assertNotEquals(scalar("SELECT COMPRESS('hello world', 'zlib')"),
            scalar("SELECT COMPRESS('hello world', 'zstd')"));
    }

    // ── the method argument is mandatory ─────────────────────────────────────

    /**
     * Live: {@code SELECT COMPRESS('hello')} fails with SQLSTATE 22023, error 938, "SQL compilation
     * error: … not enough arguments for function [COMPRESS('hello')], expected 2, got 1". Frostlake
     * used to accept it and silently compress with deflate.
     */
    @Test
    public void compressRequiresTheMethodArgument() {
        assertFails("SELECT COMPRESS('hello')",
            "not enough arguments for function [COMPRESS('hello')], expected 2, got 1");
    }

    @Test
    public void decompressStringRequiresTheMethodArgument() {
        assertFails("SELECT DECOMPRESS_STRING(TO_BINARY('051068656C6C6F', 'HEX'))",
            "not enough arguments for function [DECOMPRESS_STRING(TO_BINARY('051068656C6C6F', 'HEX'))], "
                + "expected 2, got 1");
    }

    @Test
    public void decompressBinaryRequiresTheMethodArgument() {
        assertFails("SELECT DECOMPRESS_BINARY(TO_BINARY('051068656C6C6F', 'HEX'))",
            "not enough arguments for function [DECOMPRESS_BINARY(TO_BINARY('051068656C6C6F', 'HEX'))], "
                + "expected 2, got 1");
    }

    /** Live: SQLSTATE 22023, error 939 — and no comma after the bracket in this one. */
    @Test
    public void aThirdArgumentIsRejected() {
        assertFails("SELECT COMPRESS('hello', 'snappy', 'extra')",
            "too many arguments for function [COMPRESS('hello', 'snappy', 'extra')] expected 2, got 3");
    }

    // ── the methods Snowflake does NOT have ──────────────────────────────────

    /**
     * Frostlake used to implement these three and default to {@code deflate}. The account rejects
     * every one of them with SQLSTATE 42P19, error 100194.
     */
    @Test
    public void deflateIsNotACompressionMethod() {
        assertFails("SELECT COMPRESS('hello', 'deflate')", "Unknown compression method 'deflate'");
        assertFails("SELECT DECOMPRESS_STRING(TO_BINARY('051068656C6C6F', 'HEX'), 'deflate')",
            "Unknown compression method 'deflate'");
    }

    @Test
    public void rawDeflateIsNotACompressionMethod() {
        assertFails("SELECT COMPRESS('hello', 'raw_deflate')",
            "Unknown compression method 'raw_deflate'");
        assertFails("SELECT DECOMPRESS_BINARY(TO_BINARY('051068656C6C6F', 'HEX'), 'raw_deflate')",
            "Unknown compression method 'raw_deflate'");
    }

    @Test
    public void gzipIsNotACompressionMethod() {
        assertFails("SELECT COMPRESS('hello', 'gzip')", "Unknown compression method 'gzip'");
        assertFails("SELECT DECOMPRESS_STRING(TO_BINARY('051068656C6C6F', 'HEX'), 'GZIP')",
            "Unknown compression method 'gzip'");
    }

    /** Neither the obvious near-misses nor an empty string are accepted. */
    @Test
    public void otherUnknownMethodsAreRejected() {
        assertFails("SELECT COMPRESS('hello', 'bzip2')", "Unknown compression method 'bzip2'");
        assertFails("SELECT COMPRESS('hello', 'zstandard')", "Unknown compression method 'zstandard'");
        assertFails("SELECT COMPRESS('hello', 'lz4')", "Unknown compression method 'lz4'");
        assertFails("SELECT COMPRESS('hello', '')", "Unknown compression method ''");
    }

    // ── method-name normalisation ────────────────────────────────────────────

    @Test
    public void methodNamesAreCaseInsensitive() {
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'SNAPPY'), 'snappy')"));
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'ZLib'), 'zLIB')"));
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'ZSTD'), 'Zstd')"));
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'Bz2'), 'BZ2')"));
    }

    /** Surrounding whitespace is ignored, but whitespace inside the name is not. */
    @Test
    public void surroundingWhitespaceIsIgnored() {
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', '  snappy  '), ' snappy ')"));
        assertFails("SELECT COMPRESS('hello', 'sn appy')", "Unknown compression method 'sn appy'");
    }

    /** The unknown-method message quotes the argument lowercased but NOT trimmed. */
    @Test
    public void theUnknownMethodMessageQuotesTheOriginalUntrimmed() {
        assertFails("SELECT COMPRESS('hello', '  DEFLATE  ')",
            "Unknown compression method '  deflate  '");
    }

    // ── compression levels ───────────────────────────────────────────────────

    @Test
    public void aLevelSuffixIsAccepted() {
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'zlib(1)'), 'zlib')"));
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'zstd(19)'), 'zstd')"));
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'bz2(1)'), 'bz2')"));
        assertEquals("hello", scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'snappy(1)'), 'snappy')"));
    }

    /** Level 0 means "the method's default", i.e. exactly the same bytes as omitting it. */
    @Test
    public void levelZeroIsTheDefaultLevel() {
        assertEquals(scalar("SELECT COMPRESS('hello world hello world', 'zlib')"),
            scalar("SELECT COMPRESS('hello world hello world', 'zlib(0)')"));
    }

    /** A level above the algorithm's maximum clamps instead of erroring. */
    @Test
    public void anOversizedLevelClamps() {
        assertEquals(scalar("SELECT COMPRESS('hello world hello world', 'zlib(9)')"),
            scalar("SELECT COMPRESS('hello world hello world', 'zlib(10)')"));
        assertEquals(scalar("SELECT COMPRESS('hello world hello world', 'zlib(9)')"),
            scalar("SELECT COMPRESS('hello world hello world', 'zlib(2147483647)')"));
    }

    @Test
    public void theLevelToleratesWhitespaceAndLeadingZeros() {
        assertEquals(scalar("SELECT COMPRESS('hello world hello world', 'zlib(1)')"),
            scalar("SELECT COMPRESS('hello world hello world', 'zlib( 1 )')"));
        assertEquals(scalar("SELECT COMPRESS('hello world hello world', 'zlib(1)')"),
            scalar("SELECT COMPRESS('hello world hello world', 'zlib(01)')"));
    }

    /** A malformed level is reported as an unknown METHOD, quoting the whole string. */
    @Test
    public void aMalformedLevelIsAnUnknownMethod() {
        assertFails("SELECT COMPRESS('hello', 'zlib(-1)')", "Unknown compression method 'zlib(-1)'");
        assertFails("SELECT COMPRESS('hello', 'zlib( -1 )')", "Unknown compression method 'zlib( -1 )'");
        assertFails("SELECT COMPRESS('hello', 'ZLIB(-2)')", "Unknown compression method 'zlib(-2)'");
        assertFails("SELECT COMPRESS('hello', 'zlib(+1)')", "Unknown compression method 'zlib(+1)'");
        assertFails("SELECT COMPRESS('hello', 'zlib(1.5)')", "Unknown compression method 'zlib(1.5)'");
        assertFails("SELECT COMPRESS('hello', 'zlib(x)')", "Unknown compression method 'zlib(x)'");
        assertFails("SELECT COMPRESS('hello', 'zlib()')", "Unknown compression method 'zlib()'");
        assertFails("SELECT COMPRESS('hello', 'zlib(1')", "Unknown compression method 'zlib(1'");
        assertFails("SELECT COMPRESS('hello', 'zlib(1)(2)')", "Unknown compression method 'zlib(1)(2)'");
    }

    /** The level must fit in a signed 32-bit int — 2147483647 is a level, 2147483648 is not. */
    @Test
    public void aLevelBeyondIntRangeIsAnUnknownMethod() {
        assertFails("SELECT COMPRESS('hello', 'zlib(2147483648)')",
            "Unknown compression method 'zlib(2147483648)'");
        assertFails("SELECT COMPRESS('hello', 'zlib(999999999999999999999)')",
            "Unknown compression method 'zlib(999999999999999999999)'");
    }

    /** Decompression is level-independent: the suffix is accepted and ignored. */
    @Test
    public void decompressIgnoresTheLevel() {
        assertEquals("hello world hello world",
            scalar("SELECT DECOMPRESS_STRING(COMPRESS('hello world hello world', 'zlib(1)'), 'zlib(9)')"));
        assertFails("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'zlib'), 'zlib(-1)')",
            "Unknown compression method 'zlib(-1)'");
    }

    // ── NULL handling ────────────────────────────────────────────────────────

    /** Either argument being NULL yields NULL — the other one is not even validated. */
    @Test
    public void nullArgumentsYieldNull() {
        assertNull(scalar("SELECT COMPRESS(NULL, 'snappy')"));
        assertNull(scalar("SELECT COMPRESS('hello', NULL)"));
        assertNull(scalar("SELECT COMPRESS(NULL, NULL)"));
        assertNull(scalar("SELECT COMPRESS(NULL, 'deflate')"));
        assertNull(scalar("SELECT DECOMPRESS_STRING(NULL, 'snappy')"));
        assertNull(scalar("SELECT DECOMPRESS_STRING(TO_BINARY('051068656C6C6F', 'HEX'), NULL)"));
        assertNull(scalar("SELECT DECOMPRESS_STRING(NULL, 'zlib(-1)')"));
        assertNull(scalar("SELECT DECOMPRESS_BINARY(NULL, 'snappy')"));
        assertNull(scalar("SELECT DECOMPRESS_BINARY(TO_BINARY('051068656C6C6F', 'HEX'), NULL)"));
    }

    // ── failure modes ────────────────────────────────────────────────────────

    /**
     * A payload the method cannot decode. Snowflake's message says "compress" in both directions
     * and echoes the method argument EXACTLY as written — neither lowercased nor trimmed, unlike
     * the unknown-method message (SQLSTATE 22000, error 100195).
     */
    @Test
    public void anUndecodablePayloadFails() {
        assertFails("SELECT DECOMPRESS_STRING(TO_BINARY('DEADBEEF', 'HEX'), 'ZLIB(9)')",
            "Can't compress data (too large output or invalid), method: 'ZLIB(9)'");
        assertFails("SELECT DECOMPRESS_STRING(TO_BINARY('DEADBEEF', 'HEX'), '  Zstd  ')",
            "Can't compress data (too large output or invalid), method: '  Zstd  '");
        assertFails("SELECT DECOMPRESS_STRING(TO_BINARY('DEADBEEF', 'HEX'), 'bz2')",
            "Can't compress data (too large output or invalid), method: 'bz2'");
    }

    /** Decompressing with the wrong method fails rather than returning garbage. */
    @Test
    public void theWrongMethodFails() {
        assertFails("SELECT DECOMPRESS_STRING(COMPRESS('hello', 'snappy'), 'zlib')",
            "Can't compress data (too large output or invalid), method: 'zlib'");
    }

    /** Snowflake has no implicit text-to-binary coercion for these functions. */
    @Test
    public void decompressRejectsVarcharInput() {
        assertFails("SELECT DECOMPRESS_STRING('eJzLSM3JyVcozy/KSVEEAB0JBF4=', 'zlib')",
            "Invalid argument types for function 'DECOMPRESS_STRING'");
    }
}
