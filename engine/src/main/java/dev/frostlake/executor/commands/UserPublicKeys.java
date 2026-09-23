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

package dev.frostlake.executor.commands;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Reads the public key a user's {@code RSA_PUBLIC_KEY} or {@code RSA_PUBLIC_KEY_2} is set to, and answers its
 * fingerprint. The key is the Base64 body of an X.509 {@code SubjectPublicKeyInfo}, with or without its PEM armour
 * and line breaks; the fingerprint is {@code SHA256:} and the Base64 SHA-256 digest of that structure — what an
 * account computes for the same key.
 *
 * <p>An RSA key must be at least 2048 bits long; an EC key is taken as well. A readable key of any other algorithm
 * is refused as unsupported, and anything that reads as no key at all as invalid, each with the account's own
 * sentence.
 */
final class UserPublicKeys {

    /** The shortest RSA modulus a key may have. */
    private static final int MINIMUM_RSA_BITS = 2048;

    /** The algorithms a readable key is tried as; the first two are the ones a user may hold. */
    private static final String[] ALGORITHMS = {"RSA", "EC", "EdDSA", "XDH", "DSA", "DiffieHellman"};

    private UserPublicKeys() {
    }

    /**
     * The fingerprint of a key, refusing a key an account refuses.
     *
     * @param key the key as written
     * @return {@code SHA256:<base64 digest>}
     */
    static String fingerprint(final String key) {
        final byte[] encoded = decode(key);
        for (int i = 0; i < ALGORITHMS.length; i++) {
            final PublicKey parsed = parse(ALGORITHMS[i], encoded);
            if (parsed == null) {
                continue;
            }
            if (i > 1) {
                throw new RuntimeException("SQL execution error: The provided public key uses an unsupported key "
                    + "algorithm.");
            }
            if (parsed instanceof RSAPublicKey) {
                final int bits = ((RSAPublicKey) parsed).getModulus().bitLength();
                if (bits < MINIMUM_RSA_BITS) {
                    throw rejected("Key length " + bits + " is smaller than minimal requirement of "
                        + MINIMUM_RSA_BITS + ".");
                }
            }
            return "SHA256:" + Base64.getEncoder().encodeToString(sha256(parsed.getEncoded()));
        }
        throw rejected("Invalid Public key");
    }

    /**
     * The key's bytes: its Base64 body with any {@code -----BEGIN …-----} / {@code -----END …-----} armour and every
     * blank taken out.
     */
    private static byte[] decode(final String key) {
        final StringBuilder body = new StringBuilder();
        int i = 0;
        while (i < key.length()) {
            if (key.startsWith("-----", i)) {
                final int close = key.indexOf("-----", i + 5);
                if (close < 0) {
                    throw rejected("Invalid Public key");
                }
                i = close + 5;
                continue;
            }
            final char c = key.charAt(i);
            if (!Character.isWhitespace(c)) {
                body.append(c);
            }
            i++;
        }
        try {
            return Base64.getDecoder().decode(body.toString().getBytes(StandardCharsets.US_ASCII));
        } catch (final IllegalArgumentException notBase64) {
            throw rejected("Invalid Public key");
        }
    }

    /** The key read as one algorithm's X.509 structure, or null when it is not one. */
    private static PublicKey parse(final String algorithm, final byte[] encoded) {
        try {
            return KeyFactory.getInstance(algorithm).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (final GeneralSecurityException | RuntimeException notThisAlgorithm) {
            return null;
        }
    }

    private static byte[] sha256(final byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (final GeneralSecurityException noDigest) {
            throw new IllegalStateException("SHA-256 is not available", noDigest);
        }
    }

    /** The refusal a key the account's key policy turns down answers with. */
    private static RuntimeException rejected(final String reason) {
        return new RuntimeException("SQL execution error:\nNew public key rejected by current policy. Reason: '"
            + reason + "'");
    }
}
