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

package dev.frostlake.http.rest;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The user body's countdowns and public keys: {@code days_to_expiry}, {@code mins_to_unlock},
 * {@code mins_to_bypass_mfa}, {@code rsa_public_key} and {@code rsa_public_key_2}, applied as the USER statements'
 * properties and read back from SHOW USERS and DESCRIBE USER. The keys are generated for the run.
 */
public class RestUserKeysAndCountdownsTest extends BaseRestTest {

    private static PublicKey rsaKey() throws Exception {
        final KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair().getPublic();
    }

    private static String body(final PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static String fingerprint(final PublicKey key) throws Exception {
        return "SHA256:" + Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));
    }

    @Test
    public void theCountdownsAndKeysAreAppliedAndReadBack() throws Exception {
        final PublicKey first = rsaKey();
        final PublicKey second = rsaKey();
        ok(post("/api/v2/users", "{\"name\":\"u_keys\",\"days_to_expiry\":30,\"mins_to_unlock\":10,"
            + "\"mins_to_bypass_mfa\":5,\"rsa_public_key\":\"" + body(first) + "\",\"rsa_public_key_2\":\""
            + body(second) + "\"}"));
        final JsonNode user = ok(get("/api/v2/users/u_keys"));
        // The whole days and minutes left.
        final long days = user.path("days_to_expiry").asLong();
        assertTrue(days == 29L || days == 30L, user.toString());
        final long unlock = user.path("mins_to_unlock").asLong();
        assertTrue(unlock == 9L || unlock == 10L, user.toString());
        final long bypass = user.path("mins_to_bypass_mfa").asLong();
        assertTrue(bypass == 4L || bypass == 5L, user.toString());
        assertTrue(user.path("expires_at").asString().contains("T"), user.toString());
        assertTrue(user.path("locked_until").asString().contains("T"), user.toString());
        assertTrue(user.path("has_rsa_public_key").asBoolean());
        assertEquals(body(first), user.path("rsa_public_key").asString());
        assertEquals(body(second), user.path("rsa_public_key_2").asString());
        assertEquals(fingerprint(first), user.path("rsa_public_key_fp").asString());
        assertEquals(fingerprint(second), user.path("rsa_public_key_2_fp").asString());

        // A refused key answers the statement's refusal and changes nothing.
        error(400, put("/api/v2/users/u_keys", "{\"name\":\"u_keys\",\"rsa_public_key\":\"garbage\"}"));
        assertEquals(fingerprint(first), ok(get("/api/v2/users/u_keys")).path("rsa_public_key_fp").asString());
    }

    /** A fetched user sent back keeps its keys; a body that leaves them out takes them away. */
    @Test
    public void putKeepsWhatTheBodyCarriesAndUnsetsTheRest() throws Exception {
        final PublicKey first = rsaKey();
        ok(post("/api/v2/users", "{\"name\":\"u_put_keys\",\"days_to_expiry\":5,\"rsa_public_key\":\""
            + body(first) + "\"}"));
        final JsonNode fetched = ok(get("/api/v2/users/u_put_keys"));
        ok(put("/api/v2/users/u_put_keys", "{\"name\":\"u_put_keys\",\"rsa_public_key\":\""
            + fetched.path("rsa_public_key").asString() + "\"}"));
        JsonNode user = ok(get("/api/v2/users/u_put_keys"));
        assertEquals(fingerprint(first), user.path("rsa_public_key_fp").asString());
        assertTrue(user.get("days_to_expiry").isNull(), "an omitted countdown is unset: " + user);
        ok(put("/api/v2/users/u_put_keys", "{\"name\":\"u_put_keys\"}"));
        user = ok(get("/api/v2/users/u_put_keys"));
        assertTrue(user.get("rsa_public_key").isNull(), user.toString());
        assertTrue(user.get("rsa_public_key_fp").isNull(), user.toString());
        assertEquals(false, user.path("has_rsa_public_key").asBoolean());
    }
}
