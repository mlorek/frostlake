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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.VariantText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;

/**
 * A VARIANT as the {@code Variant} a Java handler takes, and a handler's {@code Variant} as the VARIANT it
 * answers (live-verified).
 *
 * <p>An argument is made the way its value's SQL type makes one: an exact number from a decimal, a FLOAT from a
 * double, a BOOLEAN from a boolean, a BINARY from bytes, a DATE, a TIME and a timestamp from their
 * {@code java.sql} values, and a text, an OBJECT, an ARRAY and a JSON null from their text - so a text that
 * spells JSON arrives as that JSON, and an array holding a SQL NULL, whose text spells none, as the text.
 *
 * <p>A result answers by what it was made as: a number, a boolean, bytes, a DATE, a TIME or a TIMESTAMP_NTZ;
 * one made from a text is an OBJECT, an ARRAY or a JSON null where it holds one and a text otherwise, a number
 * it holds included.
 */
public final class SnowparkVariants {

    private static final long DAY_MILLIS = 86_400_000L;
    private static final long MILLI_NANOS = 1_000_000L;

    /** Static helpers only — never instantiated. */
    private SnowparkVariants() {
    }

    /**
     * @param value a VARIANT parameter's argument, not null
     * @param where the end of a refusal's sentence
     * @return the handler's value
     */
    public static Variant ofSql(final Object value, final String where) {
        if (value instanceof VariantValue) {
            return ofNode(((VariantValue) value).node(), where);
        }
        if (value instanceof Boolean) {
            return new Variant(((Boolean) value).booleanValue());
        }
        if (value instanceof Double || value instanceof Float) {
            return new Variant(((Number) value).doubleValue());
        }
        if (value instanceof Number) {
            return new Variant(exact(value instanceof BigDecimal ? (BigDecimal) value
                : new BigDecimal(value.toString())));
        }
        if (value instanceof String) {
            return new Variant((String) value);
        }
        if (value instanceof LocalDate) {
            return new Variant((Date) HandlerArguments.convert(Date.class, value, where));
        }
        if (value instanceof LocalTime) {
            return new Variant((Time) HandlerArguments.convert(Time.class, value, where));
        }
        if (value instanceof TemporalAccessor) {
            return new Variant((Timestamp) HandlerArguments.convert(Timestamp.class, value, where));
        }
        if (value instanceof BinaryValue) {
            return new Variant(((BinaryValue) value).bytes());
        }
        return new Variant(VariantText.standard(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value)));
    }

    private static Variant ofNode(final JsonNode node, final String where) {
        final Object typed = TypedScalarNode.typedValueOf(node);
        if (typed != null && !(typed instanceof VariantValue) && !(typed instanceof JsonNode)) {
            return ofSql(typed, where);
        }
        if (node == null || node.isNull()) {
            return new Variant("null");
        }
        if (node.isBoolean()) {
            return new Variant(node.booleanValue());
        }
        if (node.isDouble() || node.isFloat()) {
            return new Variant(node.doubleValue());
        }
        if (node.isNumber()) {
            return new Variant(exact(node.decimalValue()));
        }
        if (node.isString()) {
            return new Variant(node.stringValue());
        }
        if (node.isBinary()) {
            return new Variant(node.binaryValue());
        }
        return new Variant(VariantText.standard(node));
    }

    /** A number as a VARIANT holds it: no trailing zeros, and no exponent for a whole one. */
    private static BigDecimal exact(final BigDecimal number) {
        final BigDecimal stripped = number.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    /**
     * @param variant what the handler handed back, not null
     * @param where   the end of a refusal's sentence
     * @return the VARIANT's value
     */
    public static Object toSql(final Variant variant, final String where) {
        return variant.handedTo(new SnowparkVariantReader() {
            @Override
            public Object read(final SnowparkVariantKind kind, final JsonNode value) {
                return answer(variant, kind, value, where);
            }
        });
    }

    private static Object answer(final Variant variant, final SnowparkVariantKind kind, final JsonNode value,
                                 final String where) {
        if (value == null) {
            return null;
        }
        switch (kind) {
            case REAL_NUMBER:
                return ArrayFunctionHelper.toCanonicalVariant(JsonNodeFactory.instance.numberNode(
                    variant.asDouble()));
            case FIXED_NUMBER:
                return ArrayFunctionHelper.toCanonicalVariant(value.isBigDecimal()
                    ? JsonNodeFactory.instance.numberNode(exact(value.decimalValue()))
                    : value.isNumber() ? JsonNodeFactory.instance.numberNode(variant.asBigInteger()) : value);
            case BOOLEAN:
                return ArrayFunctionHelper.toCanonicalVariant(value);
            case BINARY:
                return BinaryValue.of(variant.asBinary());
            case DATE:
                return Instant.ofEpochMilli(variant.asDate().getTime()).atZone(ZoneOffset.UTC).toLocalDate();
            case TIME:
                return LocalTime.ofNanoOfDay(Math.floorMod(variant.asTime().getTime(), DAY_MILLIS) * MILLI_NANOS);
            case TIMESTAMP:
                return LocalDateTime.ofInstant(variant.asTimestamp().toInstant(), ZoneOffset.UTC);
            default:
                if (value.isContainer()) {
                    return HandlerResults.variant(variant.asJsonString(), where);
                }
                return value.isNull() ? VariantValue.of("null")
                    : ArrayFunctionHelper.toCanonicalVariant(JsonNodeFactory.instance.stringNode(
                        variant.asString()));
        }
    }
}
