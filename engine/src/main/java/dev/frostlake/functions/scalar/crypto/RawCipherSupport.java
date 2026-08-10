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

/**
 * Shared helpers for the raw-key {@link EncryptRaw} / {@link DecryptRaw} AES-GCM functions: hex BINARY
 * conversion (the engine represents BINARY as an uppercase hex string) and the encryption-method guard.
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
     * Reject anything but AES-GCM. A null method is the Snowflake default (AES-GCM); any explicit method that
     * does not name the GCM mode is refused so that an unsupported mode fails loudly rather than being
     * silently mis-encrypted.
     */
    public static void requireGcm(final String function, final Object method) {
        if (method == null) {
            return;
        }
        final String upper = method.toString().trim().toUpperCase();
        if (!upper.contains("GCM")) {
            throw new RuntimeException(function + ": only the AES-GCM encryption method is supported (got '"
                + method + "')");
        }
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
