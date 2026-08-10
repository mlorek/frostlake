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

package dev.frostlake.values;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Base64;

/**
 * Runtime value of the SQL BINARY type: an immutable byte sequence.
 *
 * <p>Engine cells historically carried BINARY as encoded text (uppercase hex or base64 depending on
 * the producer). This class replaces that with real bytes. {@link #toString()} is the canonical
 * uppercase-hex display form with no {@code 0x} prefix — the Snowflake result rendering — so any
 * boundary that stringifies a cell keeps producing the documented output. Equality is byte
 * equality, and ordering is unsigned lexicographic over the bytes, which coincides with the
 * lexicographic order of the uppercase-hex form.
 */
public final class BinaryValue implements Comparable<BinaryValue>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final byte[] bytes;

    private BinaryValue(final byte[] bytes) {
        this.bytes = bytes;
    }

    /** Wraps {@code bytes} as a BINARY value; the caller hands over ownership of the array. */
    public static BinaryValue of(final byte[] bytes) {
        return new BinaryValue(bytes);
    }

    /**
     * Decodes a hex string. <b>There is no {@code 0x} prefix</b>: Snowflake's decoder does not know
     * one, and refuses {@code '0x48656C6C6F'} with the same sentence it gives any other non-hex text —
     * from a {@code ::BINARY} cast, from {@code TO_BINARY} and from {@code HEX_DECODE_BINARY} alike.
     * Frostlake used to strip it, so it read a spelling no account accepts. ({@code X'48656C6C6F'} is
     * unaffected: the literal's own syntax carries the marker, and the text inside it is bare hex.)
     */
    public static BinaryValue fromHex(final String hex) {
        if (hex.length() % 2 != 0) {
            throw new RuntimeException(
                "The following string is not a legal hex-encoded value: '" + hex + "'");
        }
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            final int hi = Character.digit(hex.charAt(2 * i), 16);
            final int lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new RuntimeException(
                    "The following string is not a legal hex-encoded value: '" + hex + "'");
            }
            out[i] = (byte) ((hi << 4) + lo);
        }
        return new BinaryValue(out);
    }

    public static BinaryValue fromBase64(final String base64) {
        try {
            return new BinaryValue(Base64.getDecoder().decode(base64));
        } catch (final IllegalArgumentException e) {
            throw new RuntimeException(
                "The following string is not a legal base64-encoded value: '" + base64 + "'");
        }
    }

    /** The underlying bytes. Callers must not mutate the returned array. */
    public byte[] bytes() {
        return bytes;
    }

    public int length() {
        return bytes.length;
    }

    public String toHex() {
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
        }
        return sb.toString();
    }

    public String toBase64() {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Override
    public String toString() {
        return toHex();
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BinaryValue)) {
            return false;
        }
        return Arrays.equals(bytes, ((BinaryValue) other).bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public int compareTo(final BinaryValue other) {
        return Arrays.compareUnsigned(bytes, other.bytes);
    }
}
