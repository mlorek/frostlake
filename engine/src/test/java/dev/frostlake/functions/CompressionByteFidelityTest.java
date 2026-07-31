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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COMPRESS byte fidelity: every expected value below is the hex a LIVE Snowflake account returned
 * on pinned as a literal so the compressed bytes cannot drift away from the account's
 * without a test failing.
 *
 * <p>This is the gap that let the family diverge in the first place — every earlier test
 * round-tripped within Frostlake and so passed no matter what bytes came out.
 *
 * <p>Where Frostlake is NOT byte-identical, the account's value is recorded in a comment and the
 * test asserts the property that still has to hold: Frostlake decompresses Snowflake's bytes and
 * Snowflake decompresses Frostlake's (the latter verified live, see the notes on each method).
 *
 * <p>Assertions read the BINARY value directly: a BINARY cell renders as uppercase hex, which is
 * exactly what the account's JDBC string for a BINARY column is, so the two sides are directly
 * comparable. {@code HEX_ENCODE} now produces the same text — it used to hex-encode the value's hex
 * TEXT — and {@link #encodersSeeTheCompressedBytes()} pins that composition.
 */
public class CompressionByteFidelityTest extends BaseDatabaseTest {

    /** Long enough to exercise a real compressed block rather than a stored one. */
    private static final String FOX = "The quick brown fox jumps over the lazy dog. "
        + "The quick brown fox jumps over the lazy dog.";

    private String hex(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    // ── snappy: byte-identical on every probed input ─────────────────────────

    @Test
    public void snappyMatchesSnowflakeExactly() {
        assertEquals("051068656C6C6F", hex("SELECT COMPRESS('hello', 'snappy')"));
        assertEquals("00", hex("SELECT COMPRESS('', 'snappy')"));
        assertEquals("253068656C6C6F20776F726C642C205E0D00",
            hex("SELECT COMPRESS('hello world, hello world, hello world', 'snappy')"));
        assertEquals("232C68656C6C6F20776F726C64205A0C00",
            hex("SELECT COMPRESS('hello world hello world hello world', 'snappy')"));
        assertEquals("59B054686520717569636B2062726F776E20666F78206A756D7073206F76657220746865206C617A79"
                + "20646F672E20AE2D00",
            hex("SELECT COMPRESS('" + FOX + "', 'snappy')"));
    }

    @Test
    public void snappyCompressesBinaryAndNumericInputExactly() {
        assertEquals("051048454C4C4F",
            hex("SELECT COMPRESS(TO_BINARY('48454C4C4F', 'HEX'), 'snappy')"));
        // A non-string argument compresses its text form: 123 -> the three bytes '1','2','3'.
        assertEquals("0308313233", hex("SELECT COMPRESS(123, 'snappy')"));
    }

    /** snappy takes no level; Snowflake accepts one and ignores it, producing the same bytes. */
    @Test
    public void snappyIgnoresTheLevelExactly() {
        assertEquals("232C68656C6C6F20776F726C64205A0C00",
            hex("SELECT COMPRESS('hello world hello world hello world', 'snappy(1)')"));
        assertEquals("232C68656C6C6F20776F726C64205A0C00",
            hex("SELECT COMPRESS('hello world hello world hello world', 'snappy(99)')"));
    }

    // ── zlib: byte-identical at the default level and at levels 2-9 ──────────

    @Test
    public void zlibMatchesSnowflakeExactly() {
        assertEquals("789CCB48CDC9C90700062C0215", hex("SELECT COMPRESS('hello', 'zlib')"));
        assertEquals("789C030000000001", hex("SELECT COMPRESS('', 'zlib')"));
        assertEquals("789CCB48CDC9C95728CF2FCA49D151C8C0C10100038C0DAD",
            hex("SELECT COMPRESS('hello world, hello world, hello world', 'zlib')"));
        assertEquals("789CCB48CDC9C95728CF2FCA4951C8C0CE0600EF930D55",
            hex("SELECT COMPRESS('hello world hello world hello world', 'zlib')"));
    }

    /** {@code zlib(0)} and {@code zlib(6)} are the default level; the 0x789C header proves it. */
    @Test
    public void zlibLevelsMatchSnowflakeExactly() {
        final String defaultLevel = "789CCB48CDC9C95728CF2FCA4951C8C0CE0600EF930D55";
        assertEquals(defaultLevel,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zlib(0)')"));
        assertEquals(defaultLevel,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zlib(6)')"));
        assertEquals("785ECB48CDC9C95728CF2FCA4951C8C0CE0600EF930D55",
            hex("SELECT COMPRESS('hello world hello world hello world', 'zlib(2)')"));

        final String levelNine = "78DACB48CDC9C95728CF2FCA4951C8C0CE0600EF930D55";
        assertEquals(levelNine,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zlib(9)')"));
        // An oversized level clamps to 9 rather than erroring.
        assertEquals(levelNine,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zlib(10)')"));
        assertEquals(levelNine,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zlib(2147483647)')"));
        // Leading zeros are part of the level, not a different method.
        assertEquals(levelNine,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zlib(00000000009)')"));

        assertEquals("7801CB48CDC9C90700062C0215", hex("SELECT COMPRESS('hello', 'zlib(1)')"));
        assertEquals("78DACB48CDC9C90700062C0215", hex("SELECT COMPRESS('hello', 'zlib(9)')"));
    }

    /**
     * Two zlib inputs where Snowflake's deflate makes different match choices than the JDK's, so
     * the bytes differ while both stay well-formed zlib streams:
     * <ul>
     *   <li>{@code COMPRESS('hello world hello world hello world', 'zlib(1)')} →
     *       {@code 7801CB48CDC9C95728CF2FCA4951C0C10600EF930D55} live, one byte shorter than ours;</li>
     *   <li>{@code COMPRESS(FOX, 'zlib')} →
     *       {@code 789C0BC94855282CCD4CCE56482ACA2FCF5348CBAF50C82ACD2D2856C82F4B2D5228C94855C849AC
     *       AA5448C94FD7530821413100AED1202F} live, where we find a long back-reference it does not.</li>
     * </ul>
     * Both of the account's payloads decompress here, which is the property that has to hold.
     */
    @Test
    public void zlibStaysInteroperableWhereItIsNotByteIdentical() {
        assertEquals("hello world hello world hello world",
            hex("SELECT DECOMPRESS_STRING("
                + "TO_BINARY('7801CB48CDC9C95728CF2FCA4951C0C10600EF930D55', 'HEX'), 'zlib')"));
        assertEquals(FOX, hex("SELECT DECOMPRESS_STRING(TO_BINARY("
            + "'789C0BC94855282CCD4CCE56482ACA2FCF5348CBAF50C82ACD2D2856C82F4B2D5228C94855C849ACAA5448"
            + "C94FD7530821413100AED1202F', 'HEX'), 'zlib')"));
    }

    // ── zstd: byte-identical at the default level ────────────────────────────

    /**
     * Snowflake emits no frame content checksum, so neither do we — see
     * {@code CompressionCodec.stripZstdContentChecksum}. Without that these would all be five bytes
     * longer (flag bit 0x04 set in the frame header plus a trailing xxhash).
     */
    @Test
    public void zstdMatchesSnowflakeExactly() {
        assertEquals("28B52FFD200529000068656C6C6F", hex("SELECT COMPRESS('hello', 'zstd')"));
        assertEquals("28B52FFD2000010000", hex("SELECT COMPRESS('', 'zstd')"));
        assertEquals("28B52FFD20259D00006868656C6C6F20776F726C642C20010000CE2F",
            hex("SELECT COMPRESS('hello world, hello world, hello world', 'zstd')"));
        assertEquals("28B52FFD20239500006068656C6C6F20776F726C64200100AF4B12",
            hex("SELECT COMPRESS('hello world hello world hello world', 'zstd')"));
        assertEquals("28B52FFD200529000048454C4C4F",
            hex("SELECT COMPRESS(TO_BINARY('48454C4C4F', 'HEX'), 'zstd')"));
        assertEquals("28B52FFD2059AD0100D40254686520717569636B2062726F776E20666F78206A756D7073206F7665"
                + "7220746865206C617A7920646F672E2001000D9AAA0C",
            hex("SELECT COMPRESS('" + FOX + "', 'zstd')"));
    }

    /** 206 'a's — a compressed block rather than a raw or stored one. */
    @Test
    public void zstdMatchesSnowflakeOnALongRun() {
        final StringBuilder runOfA = new StringBuilder();
        for (int i = 0; i < 206; i++) {
            runOfA.append('a');
        }
        assertEquals("28B52FFD20CE4D00001061610100490A6001",
            hex("SELECT COMPRESS('" + runOfA + "', 'zstd')"));
    }

    /** Levels 0-3 are the default level, which we match byte for byte. */
    @Test
    public void zstdDefaultLevelSpellingsMatchSnowflakeExactly() {
        final String defaultLevel = "28B52FFD20239500006068656C6C6F20776F726C64200100AF4B12";
        assertEquals(defaultLevel,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zstd(0)')"));
        assertEquals(defaultLevel,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zstd(1)')"));
        assertEquals(defaultLevel,
            hex("SELECT COMPRESS('hello world hello world hello world', 'zstd(3)')"));
    }

    /**
     * A high explicit level is accepted but cannot be honoured — aircompressor exposes no level
     * knob — so we emit the default-level frame where the account emits
     * {@code 28B52FFD20239D00006868656C6C6F20776F726C642068010057CA17} for {@code zstd(19)}. Its
     * frame decompresses here, and ours decompresses there (verified live).
     */
    @Test
    public void zstdStaysInteroperableAtLevelsItCannotHonour() {
        assertEquals("hello world hello world hello world",
            hex("SELECT DECOMPRESS_STRING("
                + "TO_BINARY('28B52FFD20239D00006868656C6C6F20776F726C642068010057CA17', 'HEX'), 'zstd')"));
    }

    // ── bz2: round-trip compatible, never byte-identical ─────────────────────

    /**
     * aircompressor's block size is fixed at 9 and its Huffman table selection differs from
     * Snowflake's, whose default block size is 5:
     * <pre>
     *   COMPRESS('hello', 'bz2')  live 425A6835 3141592653591931653D00000081000244A000
     *                                            219A68334D07338BB9229C28480C98B29E80
     *                             here 425A6839 3141592653591931653D00000081000244A000
     *                                            30CD3419A68399C5DC914E1424064C594F40
     * </pre>
     * Only the header (BZh5 vs BZh9) and the entropy-coded tail differ; for some inputs even the
     * tail agrees. Both directions were verified live on the account decompresses our
     * BZh9 output to {@code hello}, and the assertions below decompress its BZh5 output.
     */
    @Test
    public void bz2ProducesAWellFormedBzip2Stream() {
        // "BZh" — the bzip2 magic — followed by the block-size digit.
        final String compressed = hex("SELECT COMPRESS('hello', 'bz2')");
        assertTrue(compressed.startsWith("425A68"), "expected a BZh header, got " + compressed);
    }

    @Test
    public void bz2DecompressesSnowflakesBytes() {
        assertEquals("hello", hex("SELECT DECOMPRESS_STRING(TO_BINARY("
            + "'425A68353141592653591931653D00000081000244A000219A68334D07338BB9229C28480C98B29E80',"
            + " 'HEX'), 'bz2')"));
        assertEquals("hello world hello world hello world", hex("SELECT DECOMPRESS_STRING(TO_BINARY("
            + "'425A68353141592653592C41D3C00000059180400006449080200021B5467A810C08F4444AB9860C34D13"
            + "85DC914E14240B1074F00', 'HEX'), 'bz2')"));
        assertEquals(FOX, hex("SELECT DECOMPRESS_STRING(TO_BINARY("
            + "'425A6835314159265359D93CF9430000099380400104003FFFFFF020005425534F42346234681B481554F"
            + "504C9846691B536891A3A6CE992684DED879DD0921228ADC396AA2D7C43114EC426787C2143D2C20A29B18"
            + "5CB9F8BB9229C28486C9E7CA180', 'HEX'), 'bz2')"));
    }

    /** A level only changes the block-size digit for Snowflake; its streams still read here. */
    @Test
    public void bz2DecompressesSnowflakesLevelledBytes() {
        assertEquals("hello", hex("SELECT DECOMPRESS_STRING(TO_BINARY("
            + "'425A68313141592653591931653D00000081000244A000219A68334D07338BB9229C28480C98B29E80',"
            + " 'HEX'), 'bz2')"));
        assertEquals("hello", hex("SELECT DECOMPRESS_STRING(TO_BINARY("
            + "'425A68393141592653591931653D00000081000244A000219A68334D07338BB9229C28480C98B29E80',"
            + " 'HEX'), 'bz2')"));
    }

    // ── the account's bytes read here for every method ───────────────────────

    @Test
    public void everyMethodDecompressesSnowflakesBytes() {
        assertEquals("hello",
            hex("SELECT DECOMPRESS_STRING(TO_BINARY('051068656C6C6F', 'HEX'), 'snappy')"));
        assertEquals("hello",
            hex("SELECT DECOMPRESS_STRING(TO_BINARY('789CCB48CDC9C90700062C0215', 'HEX'), 'zlib')"));
        assertEquals("hello",
            hex("SELECT DECOMPRESS_STRING(TO_BINARY('28B52FFD200529000068656C6C6F', 'HEX'), 'zstd')"));
        assertEquals("hello", hex("SELECT DECOMPRESS_STRING(TO_BINARY("
            + "'425A68353141592653591931653D00000081000244A000219A68334D07338BB9229C28480C98B29E80',"
            + " 'HEX'), 'bz2')"));
    }

    /**
     * The natural form of the assertions above, now that the encoders read a BINARY argument's
     * bytes: both values are the account's, live.
     */
    @Test
    public void encodersSeeTheCompressedBytes() {
        assertEquals("051068656C6C6F", hex("SELECT HEX_ENCODE(COMPRESS('hello', 'snappy'))"));
        assertEquals("BRBoZWxsbw==", hex("SELECT BASE64_ENCODE(COMPRESS('hello', 'snappy'))"));
    }
}
