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

public class CryptoFunctionsTest {

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
        ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    // MD5 / MD5_HEX
    @Test public void testMd5() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", q("SELECT MD5('hello')"));
    }
    @Test public void testMd5Hex() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", q("SELECT MD5_HEX('hello')"));
    }
    @Test public void testMd5AndMd5HexAreIdentical() {
        assertEquals(q("SELECT MD5('snowflake')"), q("SELECT MD5_HEX('snowflake')"));
    }

    // MD5_NUMBER_LOWER64 / MD5_NUMBER_UPPER64
    @Test public void testMd5NumberLower64IsUnsigned() {
        // MD5_NUMBER is an unsigned NUMBER(38,0); the old signed-long impl could return a negative value.
        Object v = q("SELECT MD5_NUMBER_LOWER64('hello')");
        assertNotNull(v);
        assertFalse(v.toString().startsWith("-"), "MD5_NUMBER_LOWER64 must be unsigned, got " + v);
    }
    @Test public void testMd5NumberUpper64MatchesSnowflakeExample() {
        // Snowflake's documented worked example: MD5_NUMBER_UPPER64('Snowflake') = 17145559544104499780
        // (exceeds Long.MAX_VALUE — the high bit of the digest is set).
        assertEquals("17145559544104499780", q("SELECT MD5_NUMBER_UPPER64('Snowflake')").toString());
    }
    @Test public void testMd5NumberLowerAndUpperAreDifferent() {
        assertNotEquals(q("SELECT MD5_NUMBER_LOWER64('hello')"),
                        q("SELECT MD5_NUMBER_UPPER64('hello')"));
    }

    // SHA1 / SHA1_HEX
    @Test public void testSha1() {
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", q("SELECT SHA1('hello')"));
    }
    @Test public void testSha1Hex() {
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", q("SELECT SHA1_HEX('hello')"));
    }

    // SHA2 / SHA2_HEX
    @Test public void testSha2Default256() {
        String h = q("SELECT SHA2('hello')").toString();
        assertEquals(64, h.length());
    }
    @Test public void testSha2Hex256() {
        assertEquals(q("SELECT SHA2('hello', 256)"), q("SELECT SHA2_HEX('hello', 256)"));
    }
    @Test public void testSha2512() {
        String h = q("SELECT SHA2('hello', 512)").toString();
        assertEquals(128, h.length());
    }

    // HASH
    @Test public void testHashReturnsLong() {
        Object v = q("SELECT HASH('hello')");
        assertTrue(v instanceof Long);
    }
    @Test public void testHashIsDeterministic() {
        assertEquals(q("SELECT HASH('same')"), q("SELECT HASH('same')"));
    }
    @Test public void testHashDifferentInputs() {
        assertNotEquals(q("SELECT HASH('a')"), q("SELECT HASH('b')"));
    }
    @Test public void testHashMultipleArgs() {
        assertNotNull(q("SELECT HASH('a', 'b', 'c')"));
    }

    // HMAC / HMAC_HEX
    @Test public void testHmacReturnsHex() {
        String h = q("SELECT HMAC('message', 'secret')").toString();
        assertTrue(h.matches("[0-9a-f]+"), "Expected hex string, got: " + h);
        assertEquals(64, h.length()); // SHA-256 = 32 bytes = 64 hex chars
    }
    @Test public void testHmacAndHmacHexAreIdentical() {
        assertEquals(q("SELECT HMAC('msg', 'key')"), q("SELECT HMAC_HEX('msg', 'key')"));
    }
    @Test public void testHmacDifferentKeys() {
        assertNotEquals(q("SELECT HMAC('msg', 'key1')"), q("SELECT HMAC('msg', 'key2')"));
    }

    // ENCRYPT / DECRYPT round-trip
    @Test public void testEncryptDecryptRoundTrip() {
        engine.execute("CREATE TABLE secrets (plain VARCHAR, cipher VARCHAR)");
        engine.execute("INSERT INTO secrets VALUES ('hello world', ENCRYPT('hello world', 'mypassword'))");

        ResultSet rs = engine.executeQuery("SELECT plain, DECRYPT(cipher, 'mypassword') FROM secrets");
        assertEquals(1, rs.getRowCount());
        assertEquals("hello world", rs.getRows().get(0).getValue(0).toString());
        assertEquals("hello world", rs.getRows().get(0).getValue(1).toString());
    }
    @Test public void testEncryptProducesNonPlaintext() {
        String cipher = q("SELECT ENCRYPT('secret', 'key')").toString();
        assertFalse(cipher.contains("secret"), "Ciphertext should not contain plaintext");
        assertNotEquals("secret", cipher);
    }
    @Test public void testEncryptNonDeterministic() {
        // Each call uses random IV so ciphertexts differ
        String c1 = q("SELECT ENCRYPT('msg', 'key')").toString();
        String c2 = q("SELECT ENCRYPT('msg', 'key')").toString();
        assertNotEquals(c1, c2, "ENCRYPT should use random IV");
    }
    @Test public void testDecryptWrongKeyThrows() {
        String cipher = q("SELECT ENCRYPT('secret', 'rightkey')").toString();
        engine.execute("CREATE TABLE enc_test (c VARCHAR)");
        engine.execute("INSERT INTO enc_test VALUES ('" + cipher + "')");
        assertThrows(RuntimeException.class, () ->
            engine.executeQuery("SELECT DECRYPT(c, 'wrongkey') FROM enc_test"));
    }
}
