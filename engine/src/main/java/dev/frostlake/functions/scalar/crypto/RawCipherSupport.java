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

package dev.frostlake.functions.scalar.crypto;

import java.security.SecureRandom;

/**
 * Shared helpers for the raw-key {@link EncryptRaw} / {@link DecryptRaw} functions: hex BINARY conversion
 * (the engine represents BINARY as an uppercase hex string), the key and IV size rules, and the IV a call
 * draws when it gives none.
 */
public final class RawCipherSupport {

    // Digit-table loop — String.format("%02X", b) parsed a format string PER BYTE.
    private static final char[] HEX_UPPER = "0123456789ABCDEF".toCharArray();

    /** GCM authentication-tag length used by ENCRYPT_RAW / DECRYPT_RAW, in bits and bytes. */
    public static final int GCM_TAG_BITS = 128;
    public static final int GCM_TAG_BYTES = GCM_TAG_BITS / 8;

    private RawCipherSupport() {
    }

    /**
     * Refuse a key AES has no size for: {@code Key size of 8 bits not found for encryption algorithm AES}
     * (live-verified), checked after the method and before the IV.
     *
     * @param key the key's bytes
     */
    public static void requireKeySize(final byte[] key) {
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new RuntimeException("Key size of " + key.length * Byte.SIZE
                + " bits not found for encryption algorithm AES");
        }
    }

    /**
     * Refuse an IV that is not the size its mode takes: {@code IV/Nonce of size 8 bits needs to be of size of
     * 96 bits for encryption mode GCM}, checked after the key and before the AAD. ECB takes NO IV, and an IV
     * given for it is refused against a size of 0 bits.
     *
     * @param method the method the call names
     * @param iv     the IV's bytes
     */
    public static void requireIvSize(final EncryptionMethod method, final byte[] iv) {
        if (iv.length != method.mode().ivBytes()) {
            throw new RuntimeException("IV/Nonce of size " + iv.length * Byte.SIZE
                + " bits needs to be of size of " + method.mode().ivBytes() * Byte.SIZE
                + " bits for encryption mode " + method.mode().name());
        }
    }

    /**
     * The IV a call uses: the one it gave, or one drawn at random when it gave none. A mode that takes no IV
     * draws nothing, and its answer carries no IV at all.
     *
     * @param function the function's name, for an argument that is no BINARY
     * @param method   the method the call names
     * @param given    the IV argument, which may be null
     * @return the IV's bytes, empty for a mode that takes none
     */
    public static byte[] ivOrDrawn(final String function, final EncryptionMethod method, final Object given) {
        if (given != null) {
            return binaryBytes(function, given);
        }
        final byte[] drawn = new byte[method.mode().ivBytes()];
        if (drawn.length > 0) {
            new SecureRandom().nextBytes(drawn);
        }
        return drawn;
    }

    /**
     * Read a BINARY argument's bytes. Snowflake types the crypto arguments (plaintext/ciphertext,
     * key, IV, AAD, tag) as BINARY and rejects a VARCHAR with "Invalid argument types" — a hex
     * string is NOT silently accepted. A {@code byte[]} is tolerated (an already-unwrapped BINARY).
     */
    public static byte[] binaryBytes(final String function, final Object arg) {
        if (arg instanceof dev.frostlake.values.BinaryValue) {
            return ((dev.frostlake.values.BinaryValue) arg).bytes();
        }
        if (arg instanceof byte[]) {
            return (byte[]) arg;
        }
        throw new RuntimeException("Invalid argument types for function '" + function
            + "': a BINARY value is required, not " + (arg == null ? "NULL" : "VARCHAR"));
    }

    /** Decode an uppercase-or-lowercase hex BINARY string to bytes. */
    public static byte[] hexToBytes(final String function, final String hex) {
        if (hex.length() % 2 != 0) {
            throw new RuntimeException(function + ": invalid hex BINARY length: " + hex.length());
        }
        final byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    /** Encode bytes as an uppercase hex BINARY string, matching the engine's BINARY rendering. */
    public static String bytesToHex(final byte[] bytes) {
        final StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            hex.append(HEX_UPPER[(b >> 4) & 0xF]).append(HEX_UPPER[b & 0xF]);
        }
        return hex.toString();
    }
}
