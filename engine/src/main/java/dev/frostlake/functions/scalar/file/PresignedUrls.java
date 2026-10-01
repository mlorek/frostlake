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

package dev.frostlake.functions.scalar.file;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * The opaque tokens of the presigned URLs GET_PRESIGNED_URL hands out. A token carries the staged file's local
 * path and the instant the URL expires, signed with a key the process draws at start-up, so the HTTP server can
 * serve the file without a session: it checks the signature and the expiry and streams the file. A URL is
 * valid only in the process that issued it.
 */
public final class PresignedUrls {

    /** The HTTP path the presigned URLs are served under. */
    public static final String CONTEXT = "/presigned/";

    private static final String ALGORITHM = "HmacSHA256";
    private static final byte[] KEY = newKey();

    /** Static helpers only. */
    private PresignedUrls() {
    }

    private static byte[] newKey() {
        final byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    /** The token for a file, valid until the given epoch second. */
    public static String sign(final Path file, final long expiresAtEpochSecond) {
        final String payload = expiresAtEpochSecond + "\n" + file.toAbsolutePath().normalize();
        final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "."
            + encoder.encodeToString(mac(payload));
    }

    /**
     * The file a token names, or null when the token is malformed or its signature does not match.
     *
     * @param token the token
     * @param expiry receives the token's expiry epoch second in its first cell
     */
    public static Path verify(final String token, final long[] expiry) {
        final int dot = token.indexOf('.');
        if (dot <= 0) {
            return null;
        }
        final String payload;
        final byte[] signature;
        try {
            payload = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
            signature = Base64.getUrlDecoder().decode(token.substring(dot + 1));
        } catch (final IllegalArgumentException malformed) {
            return null;
        }
        if (!MessageDigest.isEqual(mac(payload), signature)) {
            return null;
        }
        final int newline = payload.indexOf('\n');
        if (newline <= 0) {
            return null;
        }
        try {
            expiry[0] = Long.parseLong(payload.substring(0, newline));
        } catch (final NumberFormatException malformed) {
            return null;
        }
        return Paths.get(payload.substring(newline + 1));
    }

    private static byte[] mac(final String payload) {
        try {
            final Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(KEY, ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (final GeneralSecurityException unavailable) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", unavailable);
        }
    }
}
