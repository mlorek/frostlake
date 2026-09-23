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

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * How two VARIANT values order and compare, live-verified member by member.
 *
 * <p>The KIND decides first: BOOLEAN &lt; NUMBER &lt; STRING &lt; OBJECT &lt; ARRAY &lt; JSON null
 * (with an array's {@code undefined} hole after the null, so {@code [1,undefined]} and {@code [1,null]}
 * stay unequal). Within a kind: {@code false} before {@code true}; numbers by VALUE whatever their
 * notation ({@code 10} after {@code 9}, {@code 1} equal to {@code 1.0} and {@code 1e0}), with
 * {@code -Infinity} below every finite number and {@code NaN} above {@code Infinity}; strings by their
 * code points ({@code "10"} before {@code "9"}, {@code "Z"} before {@code "a"}); arrays element by
 * element in this same order, a prefix first ({@code []} &lt; {@code [0,5]} &lt; {@code [1]} &lt;
 * {@code [1,2]} &lt; {@code ["a"]}); objects member by member over their keys in sorted order, where at
 * each position the LARGER key sorts first and equal keys compare their values, a prefix first
 * ({@code {}} &lt; {@code {"b":0}} &lt; {@code {"a":0}} &lt; {@code {"a":0,"c":0}} &lt;
 * {@code {"a":0,"b":0}} &lt; {@code {"a":0,"b":1}} &lt; {@code {"a":1,"b":0}}); a JSON null equals a
 * JSON null. Equality is this comparison answering zero, so {@code {"a":1,"b":2}} equals
 * {@code {"b":2,"a":1}} and {@code [1]} equals {@code [1.0]}, while a variant STRING never equals the
 * number or object it spells.
 */
public final class VariantOrder {

    private static final int BOOLEAN = 0;
    private static final int NUMBER = 1;
    private static final int STRING = 2;
    private static final int OBJECT = 3;
    private static final int ARRAY = 4;
    private static final int NULL = 5;
    private static final int UNDEFINED = 6;

    private VariantOrder() {
    }

    /**
     * Orders two members.
     *
     * @param left one member, null read as a JSON null
     * @param right the other
     * @return negative, zero or positive
     */
    public static int compare(final JsonNode left, final JsonNode right) {
        final int leftKind = kind(left);
        final int rightKind = kind(right);
        if (leftKind != rightKind) {
            return Integer.compare(leftKind, rightKind);
        }
        switch (leftKind) {
            case BOOLEAN:
                return Boolean.compare(left.booleanValue(), right.booleanValue());
            case NUMBER:
                return compareNumbers(left, right);
            case STRING:
                // A UUID never equals the string of its text, so the tie between the two is broken.
                final int byText = left.asText().compareTo(right.asText());
                return byText != 0 ? byText
                    : Boolean.compare(UuidTextNode.holds(left), UuidTextNode.holds(right));
            case OBJECT:
                return compareObjects(left, right);
            case ARRAY:
                return compareArrays(left, right);
            default:
                return 0;
        }
    }

    /**
     * A text that is the same for two members exactly when {@link #compare} answers zero — the key
     * equality and hashing use, so a DISTINCT, a GROUP BY or a set operation sees {@code 1} and
     * {@code 1.0} as one value and {@code {"a":1,"b":2}} and {@code {"b":2,"a":1}} as one object.
     *
     * @param node the member
     * @return its key
     */
    public static String comparisonKey(final JsonNode node) {
        final StringBuilder key = new StringBuilder();
        appendKey(key, node);
        return key.toString();
    }

    private static void appendKey(final StringBuilder key, final JsonNode node) {
        switch (kind(node)) {
            case BOOLEAN:
                key.append(node.booleanValue() ? "true" : "false");
                return;
            case NUMBER:
                if (isFinite(node)) {
                    key.append('N').append(node.decimalValue().stripTrailingZeros().toPlainString());
                } else {
                    key.append('N').append(node.doubleValue());
                }
                return;
            case STRING:
                final String text = node.asText();
                // A UUID never equals the string of the same text (live-verified), so it keys apart.
                key.append(UuidTextNode.holds(node) ? 'U' : 'S').append(text.length()).append(':').append(text);
                return;
            case OBJECT:
                key.append('{');
                for (final String name : sortedKeys(node)) {
                    key.append(name.length()).append(':').append(name).append('=');
                    appendKey(key, node.get(name));
                    key.append(',');
                }
                key.append('}');
                return;
            case ARRAY:
                key.append('[');
                for (int i = 0; i < node.size(); i++) {
                    appendKey(key, node.get(i));
                    key.append(',');
                }
                key.append(']');
                return;
            case NULL:
                key.append("null");
                return;
            default:
                key.append("undefined");
        }
    }

    private static int kind(final JsonNode node) {
        // The hole is read before the null it is modelled on, or it would rank as one.
        if (VariantUndefined.isUndefined(node)) {
            return UNDEFINED;
        }
        if (node == null || node.isNull()) {
            return NULL;
        }
        if (node.isBoolean()) {
            return BOOLEAN;
        }
        if (node.isNumber()) {
            return NUMBER;
        }
        if (node.isObject()) {
            return OBJECT;
        }
        if (node.isArray()) {
            return ARRAY;
        }
        return STRING;
    }

    private static boolean isFinite(final JsonNode node) {
        return !(node.isDouble() || node.isFloat()) || Double.isFinite(node.doubleValue());
    }

    private static int compareNumbers(final JsonNode left, final JsonNode right) {
        if (isFinite(left) && isFinite(right)) {
            final BigDecimal leftValue = left.decimalValue();
            final BigDecimal rightValue = right.decimalValue();
            return leftValue.compareTo(rightValue);
        }
        return Double.compare(left.doubleValue(), right.doubleValue());
    }

    private static int compareArrays(final JsonNode left, final JsonNode right) {
        final int shared = Math.min(left.size(), right.size());
        for (int i = 0; i < shared; i++) {
            final int element = compare(left.get(i), right.get(i));
            if (element != 0) {
                return element;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private static int compareObjects(final JsonNode left, final JsonNode right) {
        final List<String> leftKeys = sortedKeys(left);
        final List<String> rightKeys = sortedKeys(right);
        final int shared = Math.min(leftKeys.size(), rightKeys.size());
        for (int i = 0; i < shared; i++) {
            // The larger key sorts FIRST at a position (live-verified: {"c":0} < {"b":0} < {"a":0}).
            final int byKey = rightKeys.get(i).compareTo(leftKeys.get(i));
            if (byKey != 0) {
                return byKey;
            }
            final int byValue = compare(left.get(leftKeys.get(i)), right.get(rightKeys.get(i)));
            if (byValue != 0) {
                return byValue;
            }
        }
        return Integer.compare(leftKeys.size(), rightKeys.size());
    }

    private static List<String> sortedKeys(final JsonNode object) {
        final List<String> keys = new ArrayList<>();
        for (final String name : object.propertyNames()) {
            keys.add(name);
        }
        Collections.sort(keys);
        return keys;
    }
}
