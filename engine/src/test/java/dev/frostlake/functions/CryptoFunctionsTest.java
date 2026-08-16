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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CryptoFunctionsTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    // MD5 / MD5_HEX
    @Test
    public void testMd5() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", q("SELECT MD5('hello')"));
    }
    @Test
    public void testMd5Hex() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", q("SELECT MD5_HEX('hello')"));
    }
    @Test
    public void testMd5AndMd5HexAreIdentical() {
        assertEquals(q("SELECT MD5('snowflake')"), q("SELECT MD5_HEX('snowflake')"));
    }

    // MD5_NUMBER_LOWER64 / MD5_NUMBER_UPPER64
    @Test
    public void testMd5NumberLower64IsUnsigned() {
        // MD5_NUMBER is an unsigned NUMBER(38,0); the old signed-long impl could return a negative value.
        final Object v = q("SELECT MD5_NUMBER_LOWER64('hello')");
        assertNotNull(v);
        assertFalse(v.toString().startsWith("-"), "MD5_NUMBER_LOWER64 must be unsigned, got " + v);
    }
    @Test
    public void testMd5NumberUpper64MatchesSnowflakeExample() {
        // Snowflake's documented worked example: MD5_NUMBER_UPPER64('Snowflake') = 17145559544104499780
        // (exceeds Long.MAX_VALUE — the high bit of the digest is set).
        assertEquals("17145559544104499780", q("SELECT MD5_NUMBER_UPPER64('Snowflake')").toString());
    }
    @Test
    public void testMd5NumberLowerAndUpperAreDifferent() {
        assertNotEquals(q("SELECT MD5_NUMBER_LOWER64('hello')"),
                        q("SELECT MD5_NUMBER_UPPER64('hello')"));
    }

    // SHA1 / SHA1_HEX
    @Test
    public void testSha1() {
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", q("SELECT SHA1('hello')"));
    }
    @Test
    public void testSha1Hex() {
        assertEquals("aaf4c61ddcc5e8a2dabede0f3b482cd9aea9434d", q("SELECT SHA1_HEX('hello')"));
    }

    // SHA2 / SHA2_HEX
    @Test
    public void testSha2Default256() {
        final String h = q("SELECT SHA2('hello')").toString();
        assertEquals(64, h.length());
    }
    @Test
    public void testSha2Hex256() {
        assertEquals(q("SELECT SHA2('hello', 256)"), q("SELECT SHA2_HEX('hello', 256)"));
    }
    @Test
    public void testSha2512() {
        final String h = q("SELECT SHA2('hello', 512)").toString();
        assertEquals(128, h.length());
    }

    // HASH
    @Test
    public void testHashReturnsLong() {
        final Object v = q("SELECT HASH('hello')");
        assertTrue(v instanceof Long);
    }
    @Test
    public void testHashIsDeterministic() {
        assertEquals(q("SELECT HASH('same')"), q("SELECT HASH('same')"));
    }
    @Test
    public void testHashDifferentInputs() {
        assertNotEquals(q("SELECT HASH('a')"), q("SELECT HASH('b')"));
    }
    @Test
    public void testHashMultipleArgs() {
        assertNotNull(q("SELECT HASH('a', 'b', 'c')"));
    }

    // HMAC / HMAC_HEX

    // ENCRYPT / DECRYPT round-trip (both return BINARY, as in Snowflake)
    @Test
    public void testEncryptDecryptRoundTrip() {
        engine.execute("CREATE TABLE secrets (plain VARCHAR, cipher BINARY)");
        // ENCRYPT may not appear in a VALUES clause — live-measured, the same rule that refuses
        // RANDOM, UUID_STRING, UNIFORM, SEQn, ENCRYPT_RAW, NORMAL and ZIPF there. INSERT ... SELECT
        // is the supported route for all of them.
        engine.execute("INSERT INTO secrets SELECT 'hello world', ENCRYPT('hello world', 'mypassword')");

        final ResultSet rs = engine.executeQuery(
            "SELECT plain, DECRYPT(cipher, 'mypassword') = TO_BINARY(plain, 'UTF-8') FROM secrets");
        assertEquals(1, rs.getRowCount());
        assertEquals("hello world", rs.getRows().get(0).getValue(0).toString());
        assertEquals(Boolean.TRUE, rs.getRows().get(0).getValue(1),
            "DECRYPT returns the plaintext bytes as BINARY");
    }
    @Test
    public void testEncryptProducesNonPlaintext() {
        final String cipher = q("SELECT ENCRYPT('secret', 'key')").toString();
        assertFalse(cipher.contains("secret"), "Ciphertext should not contain plaintext");
        assertNotEquals("secret", cipher);
    }
    @Test
    public void testEncryptNonDeterministic() {
        // Each call uses random IV so ciphertexts differ
        final String c1 = q("SELECT ENCRYPT('msg', 'key')").toString();
        final String c2 = q("SELECT ENCRYPT('msg', 'key')").toString();
        assertNotEquals(c1, c2, "ENCRYPT should use random IV");
    }
    @Test
    public void testDecryptWrongKeyThrows() {
        final String cipher = q("SELECT ENCRYPT('secret', 'rightkey')").toString();
        engine.execute("CREATE TABLE enc_test (c VARCHAR)");
        engine.execute("INSERT INTO enc_test VALUES ('" + cipher + "')");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT DECRYPT(c, 'wrongkey') FROM enc_test");
            }
        });
    }

    /**
     * HMAC and HMAC_HEX do not exist on Snowflake — live answers {@code Unknown function HMAC.} for
     * both, at every arity probed. Frostlake used to implement them, which let a script run here and
     * fail on the account; they were removed, and this pins the removal so they cannot drift back.
     */
    @Test
    public void hmacIsNotASnowflakeFunction() {
        for (final String sql : new String[] {
                "SELECT HMAC('msg', 'key')", "SELECT HMAC_HEX('msg', 'key')" }) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(sql);
                }
            }, sql);
        }
    }
}
