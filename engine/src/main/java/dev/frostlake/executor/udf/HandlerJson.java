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

import dev.frostlake.executor.SessionZone;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * The JSON text a handler runtime writes for a semi-structured answer, read into the variant it stands for
 * (live-verified). JSON cannot spell everything a handler hands back the way an account keeps it, so a runtime's
 * serialiser leaves marks in its text. One leads a number that is a DOUBLE — a JavaScript number with a
 * fraction or from 1e21 on, every Python float, a NaN and an infinity — which a variant keeps apart from a
 * whole or a decimal number: inside an array or an object it reads {@code 1.500000000000000e+00}. Another
 * leads an instant, a JavaScript {@code Date}, a third the hex of bytes, a JavaScript {@code Uint8Array}, and
 * a fourth a text that would itself read as a mark. An array element the serialiser wrote as {@code undefined}
 * stays one of the array's SQL NULLs, and an object's members come in key order, as any variant's do.
 *
 * <p>What an instant and bytes become depends on whose answer it is. A procedure's answer keeps an instant as
 * its text in the session's zone, {@code 2024-01-01 19:04:05.678 -0800}, and bytes as their hex — see
 * {@link #toVariant}. A function's answer keeps them typed, a TIMESTAMP_LTZ and a BINARY, inside a container
 * and as the whole answer alike — see {@link #toValue}.
 */
public final class HandlerJson {

    /** Leads the text written for a DOUBLE; the number follows as its language prints it. */
    public static final String DOUBLE = "\u0002F:";

    /** Leads the text written for an instant; its milliseconds since the epoch follow. */
    public static final String INSTANT = "\u0002D:";

    /** Leads the text written for bytes; their hex follows. */
    public static final String BINARY = "\u0002B:";

    /** Leads a text that would otherwise read as a mark; the text follows as it is. */
    public static final String TEXT = "\u0002S:";

    /** {@link #DOUBLE} as a JavaScript or a Python source spells it inside a string literal. */
    public static final String DOUBLE_IN_SOURCE = "\\u0002F:";

    /** {@link #INSTANT} as a JavaScript or a Python source spells it inside a string literal. */
    public static final String INSTANT_IN_SOURCE = "\\u0002D:";

    /** {@link #TEXT} as a JavaScript source spells it inside a string literal. */
    public static final String TEXT_IN_SOURCE = "\\u0002S:";

    /** {@link #BINARY} as a JavaScript source spells it inside a string literal. */
    public static final String BINARY_IN_SOURCE = "\\u0002B:";

    private static final DateTimeFormatter INSTANT_TEXT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS xx", Locale.ROOT);

    private HandlerJson() {
    }

    /**
     * Reads a serialiser's text.
     *
     * @param json the text, valid JSON but for the marks
     * @return the variant: a JSON null for the text {@code null}
     */
    public static VariantValue toVariant(final String json) {
        final JsonNode tree = VariantUndefined.restore(ArrayFunctionHelper.MAPPER.readTree(json));
        return ArrayFunctionHelper.toCanonicalVariant(marked(tree, false));
    }

    /**
     * Reads a serialiser's text as a function's answer, which keeps an instant and bytes typed.
     *
     * @param json the text, valid JSON but for the marks
     * @return the variant, or the TIMESTAMP_LTZ or the BINARY that is the whole answer
     */
    public static Object toValue(final String json) {
        final JsonNode tree = marked(VariantUndefined.restore(ArrayFunctionHelper.MAPPER.readTree(json)), true);
        final Object whole = TypedScalarNode.typedValueOf(tree);
        return whole != null ? whole : ArrayFunctionHelper.toCanonicalVariant(tree);
    }

    private static JsonNode marked(final JsonNode node, final boolean typed) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            final ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, marked(array.get(i), typed));
            }
            return array;
        }
        if (node.isObject()) {
            final ObjectNode object = (ObjectNode) node;
            final List<String> keys = new ArrayList<>();
            final Iterator<String> names = object.propertyNames().iterator();
            while (names.hasNext()) {
                keys.add(names.next());
            }
            for (final String key : keys) {
                object.set(key, marked(object.get(key), typed));
            }
            return object;
        }
        if (!node.isString()) {
            return node;
        }
        final String text = node.stringValue();
        if (text.startsWith(DOUBLE)) {
            return new DoubleNode(number(text.substring(DOUBLE.length())));
        }
        if (text.startsWith(INSTANT)) {
            final Instant instant = Instant.ofEpochMilli(Long.parseLong(text.substring(INSTANT.length())));
            if (typed) {
                return ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER,
                    OffsetDateTime.ofInstant(instant, SessionZone.current()));
            }
            return StringNode.valueOf(INSTANT_TEXT.format(instant.atZone(SessionZone.current())));
        }
        if (text.startsWith(TEXT)) {
            return StringNode.valueOf(text.substring(TEXT.length()));
        }
        if (text.startsWith(BINARY)) {
            final String hex = text.substring(BINARY.length());
            return typed ? ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, BinaryValue.fromHex(hex))
                : StringNode.valueOf(hex);
        }
        return node;
    }

    /** A DOUBLE as JavaScript ({@code NaN}, {@code -Infinity}) or Python ({@code nan}, {@code -inf}) prints it. */
    private static double number(final String text) {
        final String lower = text.toLowerCase(Locale.ROOT);
        if ("nan".equals(lower)) {
            return Double.NaN;
        }
        if ("inf".equals(lower) || "infinity".equals(lower)) {
            return Double.POSITIVE_INFINITY;
        }
        if ("-inf".equals(lower) || "-infinity".equals(lower)) {
            return Double.NEGATIVE_INFINITY;
        }
        return Double.parseDouble(text);
    }
}
