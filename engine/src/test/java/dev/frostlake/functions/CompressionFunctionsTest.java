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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CompressionFunctionsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    // ── deflate round-trip ────────────────────────────────────────────────────

    @Test
    public void testCompressDecompressDeflateRoundTrip() {
        String original = "Hello, Snowflake! This is a test string for compression.";
        engine.execute("CREATE TABLE comp_test (compressed VARCHAR)");
        engine.execute("INSERT INTO comp_test VALUES (COMPRESS('" + original + "', 'deflate'))");
        String result = (String) engine.executeQuery(
            "SELECT DECOMPRESS_STRING(compressed, 'deflate') FROM comp_test")
            .getRows().get(0).getValue(0);
        assertEquals(original, result);
    }

    @Test
    public void testCompressProducesSmallerOutput() {
        String text = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String compressed = (String) q("SELECT COMPRESS('" + text + "', 'deflate')");
        assertNotNull(compressed);
        assertTrue(compressed.length() < text.length(),
            "Compressed Base64 should be shorter than 50 'a's");
    }

    // ── raw_deflate round-trip ────────────────────────────────────────────────

    @Test
    public void testRawDeflateRoundTrip() {
        String original = "raw deflate test data";
        engine.execute("CREATE TABLE raw_test (c VARCHAR)");
        engine.execute("INSERT INTO raw_test VALUES (COMPRESS('" + original + "', 'raw_deflate'))");
        String result = (String) engine.executeQuery(
            "SELECT DECOMPRESS_STRING(c, 'raw_deflate') FROM raw_test")
            .getRows().get(0).getValue(0);
        assertEquals(original, result);
    }

    // ── zlib round-trip ───────────────────────────────────────────────────────

    @Test
    public void testZlibRoundTrip() {
        String original = "zlib compression test";
        engine.execute("CREATE TABLE zlib_test (c VARCHAR)");
        engine.execute("INSERT INTO zlib_test VALUES (COMPRESS('" + original + "', 'zlib'))");
        String result = (String) engine.executeQuery(
            "SELECT DECOMPRESS_STRING(c, 'zlib') FROM zlib_test")
            .getRows().get(0).getValue(0);
        assertEquals(original, result);
    }

    // ── gzip round-trip ───────────────────────────────────────────────────────

    @Test
    public void testGzipRoundTrip() {
        String original = "gzip compression test string";
        engine.execute("CREATE TABLE gz_test (c VARCHAR)");
        engine.execute("INSERT INTO gz_test VALUES (COMPRESS('" + original + "', 'gzip'))");
        String result = (String) engine.executeQuery(
            "SELECT DECOMPRESS_STRING(c, 'gzip') FROM gz_test")
            .getRows().get(0).getValue(0);
        assertEquals(original, result);
    }

    // ── default method (deflate) ──────────────────────────────────────────────

    @Test
    public void testCompressDefaultMethod() {
        String original = "default method test";
        engine.execute("CREATE TABLE def_test (c VARCHAR)");
        engine.execute("INSERT INTO def_test VALUES (COMPRESS('" + original + "'))");
        String result = (String) engine.executeQuery(
            "SELECT DECOMPRESS_STRING(c) FROM def_test")
            .getRows().get(0).getValue(0);
        assertEquals(original, result);
    }

    // ── DECOMPRESS_BINARY returns hex ─────────────────────────────────────────

    @Test
    public void testDecompressBinaryReturnsHex() {
        engine.execute("CREATE TABLE bin_test (c VARCHAR)");
        engine.execute("INSERT INTO bin_test VALUES (COMPRESS('AB', 'deflate'))");
        String hex = (String) engine.executeQuery(
            "SELECT DECOMPRESS_BINARY(c, 'deflate') FROM bin_test")
            .getRows().get(0).getValue(0);
        // 'AB' in UTF-8 is 0x41 0x42
        assertEquals("4142", hex);
    }

    // ── NULL passthrough ──────────────────────────────────────────────────────

    @Test
    public void testCompressNull() {
        assertNull(q("SELECT COMPRESS(NULL, 'deflate')"));
    }

    @Test
    public void testDecompressStringNull() {
        assertNull(q("SELECT DECOMPRESS_STRING(NULL, 'deflate')"));
    }

    // ── unsupported method throws ─────────────────────────────────────────────

    @Test
    public void testUnsupportedMethodThrows() {
        // snappy is supported (Snowflake's default method); a genuinely unknown method still throws.
        assertThrows(RuntimeException.class, () ->
            q("SELECT COMPRESS('hello', 'lz77-nonsense')"));
    }

    // ── different methods produce different output ────────────────────────────

    @Test
    public void testDifferentMethodsDifferentOutput() {
        String deflate = (String) q("SELECT COMPRESS('hello world', 'deflate')");
        String gzip    = (String) q("SELECT COMPRESS('hello world', 'gzip')");
        assertNotEquals(deflate, gzip);
    }
}
