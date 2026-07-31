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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ENCRYPT_RAW / DECRYPT_RAW — raw-key AES-GCM. ENCRYPT_RAW returns a VARIANT OBJECT
 * {@code {ciphertext, iv, tag}}; DECRYPT_RAW consumes those and returns the decrypted BINARY. Its
 * optionals nest from the left (live-measured): the 4th argument is always the AAD, the
 * 5th the method, and the AEAD tag sits strictly 6th — so a no-AAD round trip passes an EMPTY BINARY
 * AAD (cryptographically identical to none) to reach the tag position. BINARY values are the
 * engine's uppercase-hex representation.
 */
public class EncryptRawTest extends BaseDatabaseTest {

    // 32-byte key → AES-256; 12-byte IV → GCM.
    private static final String KEY = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
    private static final String IV = "0102030405060708090A0B0C";

    /**
     * A hex payload as a BINARY argument: Snowflake's raw-crypto functions take BINARY and reject a
     * hex VARCHAR outright (live: "Invalid argument types for function 'ENCRYPT_RAW':
     * (VARCHAR(10), VARCHAR(8), VARCHAR(6))"), so every argument goes through TO_BINARY.
     */
    private String binary(final String hex) {
        return "TO_BINARY('" + hex + "', 'HEX')";
    }

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private JsonNode encrypt(final String argsSql) {
        return ArrayFunctionHelper.parseNode(q("SELECT ENCRYPT_RAW(" + argsSql + ")"));
    }

    @Test
    public void objectHasCiphertextIvTagAndRoundTrips() {
        final String value = q("SELECT TO_BINARY('hello world', 'UTF-8')").toString();
        final JsonNode obj = encrypt(binary(value) + "," + binary(KEY) + "," + binary(IV));
        assertNotNull(obj.get("ciphertext"));
        assertNotNull(obj.get("tag"));
        assertEquals(IV, obj.get("iv").asText());   // IV is echoed back, uppercase hex

        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        final Object decrypted = q("SELECT DECRYPT_RAW(" + binary(ct) + "," + binary(KEY) + "," + binary(IV)
            + "," + binary("") + ",'AES-GCM'," + binary(tag) + ")");
        assertEquals(value, decrypted.toString());
    }

    /**
     * Live: the tag is NOT the fourth argument — a call that stops before the sixth position never
     * has one, whatever it passed fourth, and fails with exactly this message.
     */
    @Test
    public void tagInTheFourthPositionIsAMissingTag() {
        final String value = q("SELECT TO_BINARY('data', 'UTF-8')").toString();
        final JsonNode obj = encrypt(binary(value) + "," + binary(KEY) + "," + binary(IV));
        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT DECRYPT_RAW(" + binary(ct) + "," + binary(KEY) + "," + binary(IV)
                    + "," + binary(tag) + ")");
            }
        });
        assertEquals("Decryption mode requires an AEAD tag as parameter", error.getMessage());
    }

    /** Live: a sixth argument of the wrong length reports the byte counts verbatim. */
    @Test
    public void wrongTagSizeIsReported() {
        final String value = q("SELECT TO_BINARY('data', 'UTF-8')").toString();
        final JsonNode obj = encrypt(binary(value) + "," + binary(KEY) + "," + binary(IV));
        final String ct = obj.get("ciphertext").asText();
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT DECRYPT_RAW(" + binary(ct) + "," + binary(KEY) + "," + binary(IV)
                    + "," + binary("") + ",'AES-GCM'," + binary("AB") + ")");
            }
        });
        assertEquals("Wrong AEAD tag size. Expected 16, but got 1", error.getMessage());
    }

    @Test
    public void roundTripsWithAdditionalAuthenticatedData() {
        final String value = q("SELECT TO_BINARY('top secret', 'UTF-8')").toString();
        final String aad = q("SELECT TO_BINARY('header-v1', 'UTF-8')").toString();
        // 5-arg ENCRYPT_RAW: value, key, iv, aad, method
        final JsonNode obj = encrypt(binary(value) + "," + binary(KEY) + "," + binary(IV)
            + "," + binary(aad) + ",'AES-GCM'");
        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        // 6-arg DECRYPT_RAW: value, key, iv, aad, method, tag
        final Object decrypted = q("SELECT DECRYPT_RAW(" + binary(ct) + "," + binary(KEY) + "," + binary(IV)
            + "," + binary(aad) + ",'AES-GCM'," + binary(tag) + ")");
        assertEquals(value, decrypted.toString());
    }

    @Test
    public void wrongTagFailsAuthentication() {
        final String value = q("SELECT TO_BINARY('data', 'UTF-8')").toString();
        final JsonNode obj = encrypt(binary(value) + "," + binary(KEY) + "," + binary(IV));
        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        final String badTag = tag.substring(0, tag.length() - 1) + (tag.endsWith("0") ? "1" : "0");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT DECRYPT_RAW(" + binary(ct) + "," + binary(KEY) + "," + binary(IV)
                    + "," + binary("") + ",'AES-GCM'," + binary(badTag) + ")");
            }
        });
    }

    @Test
    public void wrongKeyFailsAuthentication() {
        final String value = q("SELECT TO_BINARY('data', 'UTF-8')").toString();
        final JsonNode obj = encrypt(binary(value) + "," + binary(KEY) + "," + binary(IV));
        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        final String wrongKey = "FF" + KEY.substring(2);
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT DECRYPT_RAW(" + binary(ct) + "," + binary(wrongKey) + "," + binary(IV)
                    + "," + binary("") + ",'AES-GCM'," + binary(tag) + ")");
            }
        });
    }

    @Test
    public void nonGcmMethodIsRejected() {
        final String value = q("SELECT TO_BINARY('data', 'UTF-8')").toString();
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT ENCRYPT_RAW('" + value + "','" + KEY + "','" + IV + "','','AES-CBC')");
            }
        });
    }

    @Test
    public void nullValueYieldsNull() {
        assertNull(q("SELECT ENCRYPT_RAW(NULL, " + binary(KEY) + ", " + binary(IV) + ")"));
    }
}
