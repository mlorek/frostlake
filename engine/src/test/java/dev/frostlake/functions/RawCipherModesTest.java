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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ENCRYPT_RAW and DECRYPT_RAW take every mode ENCRYPT and DECRYPT take — GCM, CBC, ECB, CTR, OFB and
 * CFB — where they used to refuse anything but GCM in words of their own.
 *
 * <p>Each mode brings its own IV rule: 12 bytes for GCM, 16 for the other IV modes, and NONE at all for
 * ECB, whose answer carries a NULL {@code iv}. Only an authenticating mode answers with a {@code tag},
 * and only one accepts AAD. An IV of NULL is not a missing argument: one is drawn, and the answer
 * carries it.
 */
public class RawCipherModesTest extends BaseDatabaseTest {

    private static final String KEY = "X'00112233445566778899AABBCCDDEEFF'";
    private static final String IV16 = "X'000102030405060708090A0B0C0D0E0F'";
    private static final String IV12 = "X'000102030405060708090A0B'";
    private static final String DATA = "X'00112233445566778899AABBCCDDEEFF'";

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The message of the refusal a statement raises. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " ");
    }

    /** Encrypt then decrypt under one mode, answering the plaintext that came back. */
    private String roundTrip(final String method, final String iv) {
        return answer("WITH e AS (SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + iv
            + ", NULL, '" + method + "') AS o) "
            + "SELECT DECRYPT_RAW(TO_BINARY(o:ciphertext), " + KEY + ", " + iv
            + ", NULL, '" + method + "') FROM e");
    }

    /** Every IV mode round-trips, and the ciphertext is the mode's own. */
    @Test
    public void everyIvModeRoundTrips() {
        assertEquals("00112233445566778899AABBCCDDEEFF", roundTrip("AES-CBC", IV16));
        assertEquals("00112233445566778899AABBCCDDEEFF", roundTrip("AES-CTR", IV16));
        assertEquals("00112233445566778899AABBCCDDEEFF", roundTrip("AES-OFB", IV16));
        assertEquals("00112233445566778899AABBCCDDEEFF", roundTrip("AES-CFB", IV16));
        assertEquals("{\"ciphertext\":\"67423557CA0509243B9EE04A5DA3448AA397F6D29B5C8BCE065D9CDC936B7F9B\","
            + "\"iv\":\"000102030405060708090A0B0C0D0E0F\"}",
            answer("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV16 + ", NULL, 'AES-CBC')"),
            "CBC pads, so its ciphertext runs one block longer");
        assertEquals("{\"ciphertext\":\"278E9579312775290702244D1D330EFC\","
            + "\"iv\":\"000102030405060708090A0B0C0D0E0F\"}",
            answer("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV16 + ", NULL, 'AES-CTR')"),
            "the streaming modes answer as many bytes as the value");
    }

    /** ECB takes no IV at all, and its answer carries none. */
    @Test
    public void ecbTakesNoIv() {
        assertEquals("{\"ciphertext\":\"62F679BE2BF0D931641E039CA3401BB200657EA140655A44782747705D422FAD\","
            + "\"iv\":null}",
            answer("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", NULL, NULL, 'AES-ECB')"));
        assertEquals("00112233445566778899AABBCCDDEEFF", roundTrip("AES-ECB", "NULL"));
        assertEquals("IV/Nonce of size 128 bits needs to be of size of 0 bits for encryption mode ECB",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV16 + ", NULL, 'AES-ECB')"));
    }

    /** Only the authenticating mode answers with a tag, and it alone keeps the 96-bit nonce. */
    @Test
    public void onlyTheAuthenticatingModeHasATag() {
        assertEquals("ciphertext,iv,tag",
            answer("SELECT ARRAY_TO_STRING(OBJECT_KEYS(ENCRYPT_RAW(" + DATA + ", " + KEY
                + ", " + IV12 + ")), ',')"));
        assertEquals("ciphertext,iv",
            answer("SELECT ARRAY_TO_STRING(OBJECT_KEYS(ENCRYPT_RAW(" + DATA + ", " + KEY
                + ", " + IV16 + ", NULL, 'AES-CBC')), ',')"));
    }

    /** Each mode names its own IV size in the refusal. */
    @Test
    public void eachModeNamesItsOwnIvSize() {
        assertEquals("IV/Nonce of size 96 bits needs to be of size of 128 bits for encryption mode CBC",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV12 + ", NULL, 'AES-CBC')"));
        assertEquals("IV/Nonce of size 64 bits needs to be of size of 128 bits for encryption mode CTR",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", X'0001020304050607', NULL, 'AES-CTR')"));
        assertEquals("IV/Nonce of size 128 bits needs to be of size of 96 bits for encryption mode GCM",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV16 + ", NULL, 'AES-GCM')"));
    }

    /** The IV's size is judged before the AAD the mode cannot take. */
    @Test
    public void theIvIsJudgedBeforeTheAad() {
        assertEquals("Encryption mode CBC does not support AAD",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV16 + ", X'00', 'AES-CBC')"));
        assertEquals("Encryption mode ECB does not support AAD",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", NULL, X'00', 'AES-ECB')"));
        assertEquals("IV/Nonce of size 64 bits needs to be of size of 128 bits for encryption mode CBC",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", X'0001020304050607', X'00', 'AES-CBC')"),
            "with both faults the IV speaks first");
    }

    /** A padding of NONE insists on whole blocks; the default PKCS pads a short value. */
    @Test
    public void paddingReadsAsItDoesForEncrypt() {
        assertEquals("Data size (2 bytes) needs to be a multiple of block size (16 bytes) if padding is disabled",
            refusal("SELECT ENCRYPT_RAW(X'0011', " + KEY + ", " + IV16 + ", NULL, 'AES-CBC/pad:NONE')"));
        assertEquals("{\"ciphertext\":\"67423557CA0509243B9EE04A5DA3448A\","
            + "\"iv\":\"000102030405060708090A0B0C0D0E0F\"}",
            answer("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV16 + ", NULL, 'AES-CBC/pad:NONE')"));
        assertEquals("{\"ciphertext\":\"E184CB450FCA1EC8A269B2BD2EC4C990\","
            + "\"iv\":\"000102030405060708090A0B0C0D0E0F\"}",
            answer("SELECT ENCRYPT_RAW(X'0011', " + KEY + ", " + IV16 + ", NULL, 'AES-CBC')"));
    }

    /** A NULL IV draws one of the mode's size, and the answer carries it back. */
    @Test
    public void aNullIvIsDrawn() {
        assertEquals("16", answer("SELECT LENGTH(TO_BINARY(ENCRYPT_RAW(" + DATA + ", " + KEY
            + ", NULL, NULL, 'AES-CBC'):iv))"), "the bytes CBC takes");
        assertEquals("12", answer("SELECT LENGTH(TO_BINARY(ENCRYPT_RAW(" + DATA + ", " + KEY
            + ", NULL):iv))"), "the bytes GCM takes");
        assertEquals("00112233445566778899AABBCCDDEEFF",
            answer("WITH e AS (SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", NULL, NULL, 'AES-CBC') AS o) "
                + "SELECT DECRYPT_RAW(TO_BINARY(o:ciphertext), " + KEY + ", TO_BINARY(o:iv), NULL, 'AES-CBC') FROM e"),
            "so the call stays reversible");
    }

    /** Only the value and the key make the answer NULL. */
    @Test
    public void onlyTheValueAndKeyPropagateNull() {
        assertEquals("null", answer("SELECT ENCRYPT_RAW(NULL, " + KEY + ", " + IV16 + ", NULL, 'AES-CBC')"));
        assertEquals("null", answer("SELECT ENCRYPT_RAW(" + DATA + ", NULL, " + IV16 + ", NULL, 'AES-CBC')"));
    }

    /** Whatever the cipher refuses is one sentence, and TRY_DECRYPT_RAW answers NULL for it. */
    @Test
    public void everyCipherFailureIsOneSentence() {
        assertEquals("Decryption failed. Check encrypted data, key, AAD, or AEAD tag.",
            refusal("SELECT DECRYPT_RAW(X'00', " + KEY + ", " + IV16 + ", NULL, 'AES-CBC')"));
        assertEquals("Decryption failed. Check encrypted data, key, AAD, or AEAD tag.",
            refusal("SELECT DECRYPT_RAW(X'00', " + KEY + ", " + IV12
                + ", NULL, 'AES-GCM', X'000102030405060708090A0B0C0D0E0F')"));
        assertEquals("null", answer("SELECT TRY_DECRYPT_RAW(X'00', " + KEY + ", " + IV16 + ", NULL, 'AES-CBC')"));
    }

    /** The tag rules hold for the authenticating mode and do not reach the others. */
    @Test
    public void theTagRulesAreTheAuthenticatingModesAlone() {
        assertEquals("Decryption mode requires an AEAD tag as parameter",
            refusal("SELECT DECRYPT_RAW(" + DATA + ", " + KEY + ", " + IV12 + ")"));
        assertEquals("Wrong AEAD tag size. Expected 16, but got 2",
            refusal("SELECT DECRYPT_RAW(" + DATA + ", " + KEY + ", " + IV12 + ", NULL, 'AES-GCM', X'0001')"));
        assertEquals("00112233445566778899AABBCCDDEEFF", roundTrip("AES-CBC", IV16),
            "a mode that does not authenticate needs no tag at all");
    }

    /** The method itself is still read as ENCRYPT reads it. */
    @Test
    public void theMethodIsReadAsEncryptReadsIt() {
        assertEquals("Unsupported encryption mode: XYZ",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV16 + ", NULL, 'AES-XYZ')"));
        assertEquals("Unsupported encryption algorithm: DES",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", " + KEY + ", " + IV16 + ", NULL, 'DES-CBC')"));
        assertEquals("Key size of 8 bits not found for encryption algorithm AES",
            refusal("SELECT ENCRYPT_RAW(" + DATA + ", X'00', " + IV16 + ", NULL, 'AES-CBC')"));
    }
}
