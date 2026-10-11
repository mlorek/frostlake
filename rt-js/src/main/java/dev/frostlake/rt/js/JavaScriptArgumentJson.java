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

package dev.frostlake.rt.js;

import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.udf.HandlerJson;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.VariantText;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Map;

/**
 * A semi-structured argument as the text a handler's invoker reads (live-verified). It is the value's JSON, in
 * which what JSON cannot spell is marked for the invoker to make: an array's SQL NULL element, which the handler
 * finds as a hole of its array — {@code 1 in A} is false and {@code A.length} still counts it — a DATE and a
 * TIMESTAMP, which arrive as a {@code Date}, a BINARY, a {@code Uint8Array}, and a NaN or an infinity, a number. A
 * JSON null stays {@code null}. A TIME has no counterpart in the language, so a value that holds one is refused.
 *
 * <p>The marks are {@link HandlerJson}'s, the ones a handler's answer is written with. The text leads with
 * {@link #PLAIN} when it holds no mark and with {@link #MARKED} when it does, so the invoker parses engine-made
 * JSON strictly and pays for a reviver only where one is needed.
 */
final class JavaScriptArgumentJson {

    /** Leads JSON text that holds no mark. */
    static final String PLAIN = "\u0002J:";

    /** Leads JSON text the invoker reads with its reviver. */
    static final String MARKED = "\u0002M:";

    /** Leads a text of another origin, which the invoker reads as JSON where it is JSON. */
    static final String LENIENT = "\u0002T:";

    /** Stands for an array's SQL NULL element. */
    static final String HOLE = "\u0000undefined\u0000";

    private static final DateTimeFormatter COLUMN_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS");

    private boolean marked;
    private final String routine;

    private JavaScriptArgumentJson(final String routine) {
        this.routine = routine;
    }

    /**
     * Writes an argument for the invoker.
     *
     * @param value   a semi-structured parameter's argument: a variant, or the typed scalar a VARIANT holds
     * @param routine the routine's name, for the refusal of a TIME, or null to write a TIME as its text
     * @return the led text, or null for a value this writer does not know
     */
    static String of(final Object value, final String routine) {
        final JsonNode node;
        if (value instanceof VariantValue) {
            node = ((VariantValue) value).node();
        } else if (value instanceof Boolean || value instanceof Number || value instanceof TemporalAccessor
                || value instanceof BinaryValue || value instanceof VectorValue) {
            node = ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value);
        } else {
            return null;
        }
        final JavaScriptArgumentJson writer = new JavaScriptArgumentJson(routine);
        final JsonNode written = writer.written(node);
        return (writer.marked ? MARKED : PLAIN) + VariantText.standard(written);
    }

    /**
     * Writes a column's value for the script-side result set (live-verified): a NULL is {@code null}, a text a
     * string, a TIME its text to the nanosecond — {@code 10:11:12.000000000} — and any other value as an
     * argument is written, with a TIME inside a VARIANT as its text.
     *
     * @param value the column's value
     * @return the led text
     */
    static String ofColumn(final Object value) {
        if (value == null) {
            return PLAIN + "null";
        }
        if (value instanceof LocalTime) {
            return PLAIN + VariantText.standard(StringNode.valueOf(COLUMN_TIME.format((LocalTime) value)));
        }
        final Object written = value instanceof String ? VariantValue.ofNode(StringNode.valueOf((String) value))
            : value;
        final String led = of(written, null);
        return led != null ? led : PLAIN + VariantText.standard(StringNode.valueOf(String.valueOf(value)));
    }

    /** The tree as it is written: itself where it holds nothing to mark, otherwise a copy that holds the marks. */
    private JsonNode written(final JsonNode node) {
        if (VariantUndefined.isUndefined(node)) {
            return mark(HOLE);
        }
        if (node == null || node.isNull()) {
            return NullNode.getInstance();
        }
        if (node.isArray()) {
            ArrayNode copy = null;
            int at = 0;
            for (final JsonNode element : node) {
                final JsonNode out = written(element);
                if (out != element && copy == null) {
                    copy = ((ArrayNode) node).arrayNode();
                    for (int i = 0; i < at; i++) {
                        copy.add(node.get(i));
                    }
                }
                if (copy != null) {
                    copy.add(out);
                }
                at++;
            }
            return copy != null ? copy : node;
        }
        if (node.isObject()) {
            ObjectNode copy = null;
            for (final Map.Entry<String, JsonNode> member : node.properties()) {
                final JsonNode out = written(member.getValue());
                if (out != member.getValue()) {
                    if (copy == null) {
                        copy = ((ObjectNode) node).objectNode();
                        copy.setAll((ObjectNode) node);
                    }
                    copy.set(member.getKey(), out);
                }
            }
            return copy != null ? copy : node;
        }
        if (node instanceof TypedScalarNode) {
            return typed((TypedScalarNode) node);
        }
        if (node.isString()) {
            final String text = node.stringValue();
            final boolean led = !text.isEmpty() && (text.charAt(0) == '\u0002' || text.charAt(0) == '\u0000');
            return led ? mark(HandlerJson.TEXT + text) : node;
        }
        if (node.isFloatingPointNumber() && !node.isBigDecimal()) {
            final double number = node.doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                return mark(HandlerJson.DOUBLE + (Double.isNaN(number) ? "NaN" : number > 0 ? "Infinity" : "-Infinity"));
            }
        }
        return node;
    }

    /** A member that kept its SQL type: an instant, bytes, or the refusal of a TIME. */
    private JsonNode typed(final TypedScalarNode node) {
        final Object value = node.typedValue();
        if (value instanceof LocalTime) {
            if (routine == null) {
                return node;
            }
            throw new RuntimeException("JavaScript execution error: TIME inside VARIANT value - not supported"
                + " in function " + routine + " with handler " + routine);
        }
        final Long instant = value instanceof TemporalAccessor ? epochMillis((TemporalAccessor) value) : null;
        if (instant != null) {
            return mark(HandlerJson.INSTANT + instant);
        }
        if (value instanceof BinaryValue) {
            return mark(HandlerJson.BINARY + ((BinaryValue) value).toHex());
        }
        return node;
    }

    private JsonNode mark(final String text) {
        marked = true;
        return StringNode.valueOf(text);
    }

    /**
     * The instant a DATE or a TIMESTAMP names, in milliseconds: a date at its midnight and a timestamp without
     * a zone at its wall clock, both in the session's zone, and a zoned timestamp at its own instant
     * (live-verified). Null for a value that names none, a TIME among them.
     */
    static Long epochMillis(final TemporalAccessor value) {
        if (value instanceof LocalDate) {
            return Long.valueOf(((LocalDate) value).atStartOfDay(SessionZone.current()).toInstant()
                .toEpochMilli());
        }
        if (value instanceof LocalDateTime) {
            return Long.valueOf(((LocalDateTime) value).atZone(SessionZone.current()).toInstant()
                .toEpochMilli());
        }
        if (value instanceof OffsetDateTime || value instanceof ZonedDateTime || value instanceof Instant) {
            return Long.valueOf(Instant.from(value).toEpochMilli());
        }
        return null;
    }
}
