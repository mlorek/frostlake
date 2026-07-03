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
 * {@code {ciphertext, iv, tag}}; DECRYPT_RAW consumes those (tag last) and returns the decrypted BINARY.
 * BINARY values are the engine's uppercase-hex representation.
 */
public class EncryptRawTest extends BaseDatabaseTest {

    // 32-byte key → AES-256; 12-byte IV → GCM.
    private static final String KEY = "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
    private static final String IV = "0102030405060708090A0B0C";

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private JsonNode encrypt(final String argsSql) {
        return ArrayFunctionHelper.parseNode(q("SELECT ENCRYPT_RAW(" + argsSql + ")"));
    }

    @Test
    public void objectHasCiphertextIvTagAndRoundTrips() {
        final String value = q("SELECT TO_BINARY('hello world', 'UTF-8')").toString();
        final JsonNode obj = encrypt("'" + value + "','" + KEY + "','" + IV + "'");
        assertNotNull(obj.get("ciphertext"));
        assertNotNull(obj.get("tag"));
        assertEquals(IV, obj.get("iv").asText());   // IV is echoed back, uppercase hex

        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        final Object decrypted = q("SELECT DECRYPT_RAW('" + ct + "','" + KEY + "','" + IV + "','" + tag + "')");
        assertEquals(value, decrypted.toString());
    }

    @Test
    public void roundTripsWithAdditionalAuthenticatedData() {
        final String value = q("SELECT TO_BINARY('top secret', 'UTF-8')").toString();
        final String aad = q("SELECT TO_BINARY('header-v1', 'UTF-8')").toString();
        // 5-arg ENCRYPT_RAW: value, key, iv, aad, method
        final JsonNode obj = encrypt("'" + value + "','" + KEY + "','" + IV + "','" + aad + "','AES-GCM'");
        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        // 6-arg DECRYPT_RAW: value, key, iv, aad, method, tag
        final Object decrypted = q("SELECT DECRYPT_RAW('" + ct + "','" + KEY + "','" + IV
            + "','" + aad + "','AES-GCM','" + tag + "')");
        assertEquals(value, decrypted.toString());
    }

    @Test
    public void wrongTagFailsAuthentication() {
        final String value = q("SELECT TO_BINARY('data', 'UTF-8')").toString();
        final JsonNode obj = encrypt("'" + value + "','" + KEY + "','" + IV + "'");
        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        final String badTag = tag.substring(0, tag.length() - 1) + (tag.endsWith("0") ? "1" : "0");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT DECRYPT_RAW('" + ct + "','" + KEY + "','" + IV + "','" + badTag + "')");
            }
        });
    }

    @Test
    public void wrongKeyFailsAuthentication() {
        final String value = q("SELECT TO_BINARY('data', 'UTF-8')").toString();
        final JsonNode obj = encrypt("'" + value + "','" + KEY + "','" + IV + "'");
        final String ct = obj.get("ciphertext").asText();
        final String tag = obj.get("tag").asText();
        final String wrongKey = "FF" + KEY.substring(2);
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT DECRYPT_RAW('" + ct + "','" + wrongKey + "','" + IV + "','" + tag + "')");
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
        assertNull(q("SELECT ENCRYPT_RAW(NULL, '" + KEY + "', '" + IV + "')"));
    }
}
