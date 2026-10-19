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

package dev.frostlake.executor.udf;

import com.snowflake.snowpark_java.types.Variant;
import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.expressions.ValueCaster;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * What a Java or a Scala handler hands back, as the value of the routine's declared RETURNS type
 * (live-verified). A {@code BigDecimal} is rounded half to even to the declared scale and refused when the
 * declared width cannot hold it; a {@code long} or an {@code int} is refused likewise; a text returned for a
 * NUMBER is read as the cast reads it. A {@code java.sql.Date} is the date of its instant at UTC, a
 * {@code java.sql.Time} the time of day it counts - refused when it counts none, {@code Time value out of range:
 * 86401000 ms} - a {@code java.sql.Timestamp} its wall clock at UTC with its
 * nanoseconds for a timestamp without a zone and its instant for a zoned one. A {@code String[]} is an ARRAY of
 * texts, a null element one of its SQL NULLs; a {@code Map} an OBJECT; a {@code byte[]} a BINARY, refused past
 * a declared width; a text returned for a VARIANT, an ARRAY or an OBJECT is read as JSON, and refused when it
 * is none or, for an ARRAY and an OBJECT, of another kind; a text returned for a TIME is read as the cast reads
 * it.
 */
public final class HandlerResults {

    /** How many of a value's bytes the refusal of a BINARY past its declared width quotes. */
    private static final int QUOTED_BYTES = 20;

    private HandlerResults() {
    }

    /**
     * Converts a handler's value to the declared type.
     *
     * @param value    what the handler handed back
     * @param declared the routine's RETURNS type
     * @param routine  the routine's name, as the refusals spell it
     * @param handler  the handler as declared
     * @return the value the CALL answers
     */
    public static Object fromJava(final Object value, final DataType declared, final String routine,
                                  final String handler) {
        if (value == null || declared == null) {
            return value;
        }
        final String where = " in function " + routine + " with handler " + handler;
        if (declared instanceof NumericType) {
            return number(value, (NumericType) declared, where);
        }
        if (declared instanceof StringType) {
            return String.valueOf(value);
        }
        final String name = declared.getName() == null ? "" : declared.getName().toUpperCase(Locale.ROOT);
        if ("DATE".equals(name) && value instanceof Date) {
            return Instant.ofEpochMilli(((Date) value).getTime()).atZone(ZoneOffset.UTC).toLocalDate();
        }
        if ("TIME".equals(name) && value instanceof Time) {
            final long millis = ((Time) value).getTime();
            if (millis < 0 || millis >= 86_400_000L) {
                throw new RuntimeException("Time value out of range: " + millis + " ms" + where);
            }
            return LocalTime.ofNanoOfDay(millis * 1_000_000L);
        }
        if ("TIME".equals(name) && value instanceof String) {
            return ValueCaster.castValue(value, "TIME");
        }
        if (name.startsWith("TIMESTAMP") && value instanceof Timestamp) {
            final Timestamp timestamp = (Timestamp) value;
            final Instant instant = Instant.ofEpochSecond(Math.floorDiv(timestamp.getTime(), 1000L),
                timestamp.getNanos());
            return name.endsWith("NTZ") || "TIMESTAMP".equals(name) ? (Object) LocalDateTime.ofInstant(instant,
                ZoneOffset.UTC) : OffsetDateTime.ofInstant(instant, SessionZone.current());
        }
        if (declared instanceof BinaryType && value instanceof byte[]) {
            return binary(BinaryValue.of((byte[]) value), (BinaryType) declared, where);
        }
        if ("ARRAY".equals(name) && value instanceof Object[]) {
            return array((Object[]) value);
        }
        if (declared instanceof ObjectType && value instanceof Map) {
            return object((Map<?, ?>) value);
        }
        if (declared instanceof VariantType && value instanceof String) {
            return variant((String) value, where);
        }
        if (declared instanceof VariantType && value instanceof Variant) {
            return SnowparkVariants.toSql((Variant) value, where);
        }
        if (("ARRAY".equals(name) || declared instanceof ObjectType) && value instanceof String) {
            return container((String) value, "ARRAY".equals(name), where);
        }
        return value;
    }

    /**
     * A text for an ARRAY or an OBJECT: read as JSON, and refused when it is none or is of another kind -
     * {@code Error parsing JSON: output JSON is not an array type: {"a":1}}.
     */
    private static Object container(final String text, final boolean array, final String where) {
        final Object parsed = variant(text, where);
        final VariantValue held = parsed instanceof VariantValue ? (VariantValue) parsed : null;
        if (held == null || (array ? !held.isJsonArray() : !held.isJsonObject())) {
            throw new RuntimeException("Error parsing JSON: output JSON is not an " + (array ? "array" : "object")
                + " type: " + text + where);
        }
        return held;
    }

    private static Object number(final Object value, final NumericType type, final String where) {
        if (NumericType.isApproximate(type)) {
            if (value instanceof Number) {
                return Double.valueOf(((Number) value).doubleValue());
            }
            try {
                return Double.valueOf(Double.parseDouble(String.valueOf(value).trim()));
            } catch (final NumberFormatException notANumber) {
                throw new RuntimeException("Numeric value '" + value + "' is not recognized" + where);
            }
        }
        final BigDecimal held;
        final String refusal;
        if (value instanceof BigDecimal) {
            // A BigDecimal rounds half to even: 2.5 is 2 and 3.5 is 4, 1.385 is 1.38 at two decimals.
            held = ((BigDecimal) value).setScale(type.getScale(), RoundingMode.HALF_EVEN);
            refusal = "BigDecimal value cannot be losslessly represented as a Snowflake NUMBER with a precision of "
                + type.getPrecision() + " and a scale of " + type.getScale();
        } else if (value instanceof Number) {
            final BigDecimal exact = value instanceof BigInteger ? new BigDecimal((BigInteger) value)
                : value instanceof Double || value instanceof Float ? BigDecimal.valueOf(((Number) value).doubleValue())
                : BigDecimal.valueOf(((Number) value).longValue());
            held = exact.setScale(type.getScale(), RoundingMode.HALF_EVEN);
            refusal = "Java value cannot be losslessly represented as a Snowflake NUMBER with a precision of "
                + type.getPrecision() + ", value: " + exact.toPlainString();
        } else {
            // A text is read as the cast reads it, rounding half away from zero.
            final String text = String.valueOf(value);
            try {
                held = new BigDecimal(text.trim()).setScale(type.getScale(), RoundingMode.HALF_UP);
            } catch (final NumberFormatException notANumber) {
                throw new RuntimeException("Numeric value '" + text + "' is not recognized" + where);
            }
            refusal = "Java value cannot be losslessly represented as a Snowflake NUMBER with a precision of "
                + type.getPrecision() + ", value: " + text;
        }
        if (type.getPrecision() > 0 && NumericRangeRefusal.exceeds(held, type.getPrecision(), type.getScale())) {
            throw new RuntimeException(refusal + where);
        }
        if (type.getScale() == 0) {
            return held.unscaledValue().bitLength() < Long.SIZE ? (Object) Long.valueOf(held.longValue()) : held;
        }
        return held;
    }

    /** An array of texts; a null element is one of the array's SQL NULLs. */
    private static VariantValue array(final Object[] elements) {
        final ArrayNode array = ArrayFunctionHelper.MAPPER.createArrayNode();
        for (final Object element : elements) {
            if (element == null) {
                array.add(VariantUndefined.node());
            } else {
                array.add(String.valueOf(element));
            }
        }
        return VariantValue.ofNode(array);
    }

    private static VariantValue object(final Map<?, ?> members) {
        final ObjectNode object = ArrayFunctionHelper.MAPPER.createObjectNode();
        for (final Map.Entry<?, ?> member : members.entrySet()) {
            if (member.getValue() == null) {
                object.putNull(String.valueOf(member.getKey()));
            } else {
                object.put(String.valueOf(member.getKey()), String.valueOf(member.getValue()));
            }
        }
        return VariantValue.ofNode(object);
    }

    /**
     * Bytes for a BINARY, refused past a declared width in the words a Java and a Python handler's answer gets:
     * the limit, then the bytes themselves read as characters (live-verified for a value of three bytes; the
     * longest run the sentence quotes is a guess).
     *
     * @param bytes    the handler's bytes
     * @param declared the declared type
     * @param where    the routine and the handler, as a refusal names them
     * @return the bytes
     */
    public static BinaryValue binary(final BinaryValue bytes, final BinaryType declared, final String where) {
        if (declared.getMaxLength() > 0 && bytes.length() > declared.getMaxLength()) {
            final byte[] raw = bytes.bytes();
            final String quoted = new String(raw, 0, Math.min(raw.length, QUOTED_BYTES), StandardCharsets.ISO_8859_1);
            throw new RuntimeException("System byte length limit (" + declared.getMaxLength()
                + ") exceeded by string '" + quoted + "...'" + where);
        }
        return bytes;
    }

    /**
     * A text for a VARIANT, read as PARSE_JSON reads one: a number written with an exponent is a DOUBLE, a plain
     * fraction a decimal without its trailing zeros, {@code NaN} a number, an array's {@code undefined} one of
     * its SQL NULLs — and the text {@code null} a JSON null, where a Java {@code null} is a SQL NULL.
     */
    static Object variant(final String text, final String where) {
        final String document = text.trim();
        if ("null".equals(document)) {
            return VariantValue.of("null");
        }
        final JsonNode parsed = document.isEmpty() ? null : JsonTypeHelper.parseLenient(document);
        if (parsed == null) {
            throw new RuntimeException("Error parsing JSON: Document is not a valid JSON object: " + text + where);
        }
        return VariantUndefined.isUndefined(parsed) ? null : ArrayFunctionHelper.toCanonicalVariant(parsed);
    }
}
