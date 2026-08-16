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

package dev.frostlake.functions.scalar.hash;

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * The bytes HASH and HASH_AGG hash for one argument: a TYPE-CLASS tag followed by a canonical rendering
 * of the value. The tag and the rendering together decide which arguments hash ALIKE, which is the only
 * part of Snowflake's HASH that can be reproduced from outside — the 64-bit values themselves come from a
 * proprietary algorithm and are deliberately not imitated.
 *
 * <p>The classes are live-verified, and two of them are counter-intuitive:
 *
 * <ul>
 *   <li><strong>Numbers hash by VALUE, never by declared type or scale.</strong> 1, 1.00, 1.0000,
 *       {@code 1::NUMBER(38,10)} and {@code 1.0::FLOAT} are one hash; so are 2.5 and 2.50. Trailing zeros
 *       are stripped, and {@code -0.0} renders as 0.</li>
 *   <li><strong>BOOLEAN is in the NUMBER class</strong> — TRUE hashes bit-identically to 1 and FALSE to 0,
 *       though neither equals the string 'true'.</li>
 *   <li><strong>Every temporal is in the NUMBER class too</strong>, as its epoch offset in its own natural
 *       unit: a DATE by DAYS, a TIME by seconds since midnight, a TIMESTAMP by epoch seconds, fractions
 *       included. So {@code DATE '1970-01-02'} and {@code TIME '00:00:01'} both hash as the number 1, while
 *       a DATE and the TIMESTAMP at its midnight do NOT collide — their unit differs.</li>
 *   <li><strong>A BINARY hashes as the STRING of its bytes</strong>: {@code X'31'} and {@code '1'} are one
 *       hash. The raw bytes are used, not a decode, so two binaries that are both invalid UTF-8 stay
 *       distinct.</li>
 *   <li><strong>A VARIANT hashes as the value it holds</strong> — a VARIANT number equals the bare number
 *       and a VARIANT string the bare string. The two exceptions are a VARIANT BOOLEAN and a VARIANT JSON
 *       null, which each keep their own class: neither collides with the SQL boolean nor with SQL NULL.</li>
 * </ul>
 *
 * <p>A container hashes as its canonical JSON, which is why key order does not matter for an object and
 * element order does for an array, and why an array never collides with the string of its own text.
 */
public final class HashCanonicalValue {

    /** SQL NULL — distinct from a VARIANT JSON null. */
    private static final byte TAG_NULL = 'N';
    /** Numbers, booleans and every temporal. */
    private static final byte TAG_NUMBER = '#';
    /** Strings and binaries alike. */
    private static final byte TAG_TEXT = 'S';
    /** An OBJECT or ARRAY, rendered as canonical JSON. */
    private static final byte TAG_CONTAINER = 'V';
    /** A boolean INSIDE a variant, which does not join the number class. */
    private static final byte TAG_VARIANT_BOOLEAN = 'b';
    /** A JSON null inside a variant, which is not SQL NULL. */
    private static final byte TAG_VARIANT_NULL = 'n';

    /** Nanoseconds are a scale-9 fraction of a second, never a division — a division can be inexact. */
    private static final int NANO_SCALE = 9;

    private HashCanonicalValue() {
    }

    /** The tagged bytes for one HASH argument. */
    public static byte[] encode(final Object value) {
        if (value == null) {
            return tagged(TAG_NULL, new byte[0]);
        }
        if (value instanceof VariantValue) {
            return encodeNode(((VariantValue) value).node());
        }
        if (value instanceof BinaryValue) {
            return tagged(TAG_TEXT, ((BinaryValue) value).bytes());
        }
        if (value instanceof String) {
            return tagged(TAG_TEXT, ((String) value).getBytes(StandardCharsets.UTF_8));
        }
        final String number = numberText(value);
        if (number != null) {
            return tagged(TAG_NUMBER, number.getBytes(StandardCharsets.UTF_8));
        }
        return tagged(TAG_TEXT, SharedFunctionHelpers.textOf(value).getBytes(StandardCharsets.UTF_8));
    }

    /** The tagged bytes for a value that arrived as a VARIANT node. */
    private static byte[] encodeNode(final JsonNode node) {
        if (node == null) {
            return tagged(TAG_NULL, new byte[0]);
        }
        // A DATE/TIME/TIMESTAMP/BINARY kept its type inside the container, so it hashes in the class its
        // bare counterpart would. This is checked before isTextual() because a typed scalar IS a string
        // node — that is how its JSON text stays byte-identical.
        final Object typed = TypedScalarNode.typedValueOf(node);
        if (typed != null) {
            return encode(typed);
        }
        if (node.isNull()) {
            return tagged(TAG_VARIANT_NULL, new byte[0]);
        }
        if (node.isObject() || node.isArray()) {
            return tagged(TAG_CONTAINER,
                ArrayFunctionHelper.toCanonicalJson(node).getBytes(StandardCharsets.UTF_8));
        }
        if (node.isBoolean()) {
            return tagged(TAG_VARIANT_BOOLEAN,
                String.valueOf(node.asBoolean()).getBytes(StandardCharsets.UTF_8));
        }
        if (node.isNumber()) {
            return tagged(TAG_NUMBER, plain(node.decimalValue()).getBytes(StandardCharsets.UTF_8));
        }
        return tagged(TAG_TEXT, node.asText().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The canonical NUMBER-class rendering of a value, or null when the value is not in that class.
     * Everything here is measured: a boolean is its 0/1, and a temporal its epoch offset.
     */
    private static String numberText(final Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "1" : "0";
        }
        if (value instanceof BigDecimal) {
            return plain((BigDecimal) value);
        }
        if (value instanceof BigInteger || value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof Double || value instanceof Float) {
            return doubleText(((Number) value).doubleValue());
        }
        if (value instanceof LocalDate) {
            return String.valueOf(((LocalDate) value).toEpochDay());
        }
        if (value instanceof LocalTime) {
            return plain(BigDecimal.valueOf(((LocalTime) value).toNanoOfDay(), NANO_SCALE));
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime moment = (LocalDateTime) value;
            return epochText(moment.toEpochSecond(ZoneOffset.UTC), moment.getNano());
        }
        if (value instanceof OffsetDateTime) {
            return instantText(((OffsetDateTime) value).toInstant());
        }
        if (value instanceof ZonedDateTime) {
            return instantText(((ZonedDateTime) value).toInstant());
        }
        if (value instanceof Instant) {
            return instantText((Instant) value);
        }
        if (value instanceof Number) {
            return doubleText(((Number) value).doubleValue());
        }
        return null;
    }

    private static String instantText(final Instant instant) {
        return epochText(instant.getEpochSecond(), instant.getNano());
    }

    /** Epoch seconds carrying their nanosecond fraction, which stays correct either side of the epoch. */
    private static String epochText(final long seconds, final int nanos) {
        return plain(BigDecimal.valueOf(seconds).add(BigDecimal.valueOf(nanos, NANO_SCALE)));
    }

    /**
     * A FLOAT joins the exact numbers by its SHORTEST round-trip decimal, which is why
     * {@code 0.3::FLOAT} hashes as the exact 0.3 and {@code 1e20::FLOAT} as the exact integer. The
     * ten-significant-digit VARCHAR rendering is a display rule and deliberately not used here.
     */
    private static String doubleText(final double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            return String.valueOf(d);
        }
        return plain(new BigDecimal(Double.toString(d)));
    }

    private static String plain(final BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static byte[] tagged(final byte tag, final byte[] payload) {
        final byte[] out = new byte[payload.length + 1];
        out[0] = tag;
        System.arraycopy(payload, 0, out, 1, payload.length);
        return out;
    }
}
