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

package dev.frostlake.functions.scalar;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A JSON number a VARIANT cannot hold exactly is read as a DOUBLE. A plainly written number keeps its exact value,
 * but numbers that "cannot be stored as fixed-point decimals due to range or scale limitations, are stored as DOUBLE
 * values" (documented, PARSE_JSON). The range is the signed 128-bit one: a 39-digit integer within it stays an
 * INTEGER with every digit, and a hexadecimal integer is exact up to its end (both live-verified); the scale is
 * NUMBER's, at most 37. Trailing zeros are dropped first, as the exact families drop them.
 */
public final class JsonNumberRange {

    /** The digits of the longest number text that can still be held exactly whatever it spells. */
    private static final int MAX_DIGITS = 38;

    /** The largest scale a NUMBER takes. */
    private static final int MAX_SCALE = 37;

    /** The largest value held exactly: 2^127 - 1. */
    private static final BigInteger MAX_EXACT = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

    /** The smallest value held exactly: -2^127. */
    private static final BigInteger MIN_EXACT = BigInteger.ONE.shiftLeft(127).negate();

    private JsonNumberRange() {
    }

    /**
     * A parsed document with each number a VARIANT cannot hold exactly read as a DOUBLE, rewritten in place. A
     * document whose text holds no run of digits long enough for such a number is returned as it is, unread.
     *
     * @param text the document's text
     * @param tree the document read
     * @return the document
     */
    public static JsonNode applyDoubleRange(final String text, final JsonNode tree) {
        return mayHoldLongNumber(text) ? narrow(tree) : tree;
    }

    /**
     * Whether a VARIANT holds a value exactly.
     *
     * @param value the value
     * @return true when, its trailing zeros dropped, its scale is at most 37 and its digits as a whole number lie in
     *         the signed 128-bit range
     */
    static boolean heldExactly(final BigDecimal value) {
        final BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() > MAX_SCALE) {
            return false;
        }
        final BigInteger digits = stripped.scale() >= 0 ? stripped.unscaledValue() : stripped.toBigInteger();
        return digits.compareTo(MIN_EXACT) >= 0 && digits.compareTo(MAX_EXACT) <= 0;
    }

    private static JsonNode narrow(final JsonNode node) {
        if (node instanceof ObjectNode) {
            final ObjectNode object = (ObjectNode) node;
            final Map<String, JsonNode> narrowed = new LinkedHashMap<>();
            for (final Map.Entry<String, JsonNode> property : object.properties()) {
                final JsonNode value = narrow(property.getValue());
                if (value != property.getValue()) {
                    narrowed.put(property.getKey(), value);
                }
            }
            for (final Map.Entry<String, JsonNode> property : narrowed.entrySet()) {
                object.set(property.getKey(), property.getValue());
            }
            return object;
        }
        if (node instanceof ArrayNode) {
            final ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                final JsonNode element = array.get(i);
                final JsonNode value = narrow(element);
                if (value != element) {
                    array.set(i, value);
                }
            }
            return array;
        }
        if ((node.isBigInteger() || node.isBigDecimal()) && !heldExactly(node.decimalValue())) {
            return DoubleNode.valueOf(node.doubleValue());
        }
        return node;
    }

    /** Whether the text holds a run of more than 38 digits, a decimal point among them or not. */
    private static boolean mayHoldLongNumber(final String text) {
        int run = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                run++;
                if (run > MAX_DIGITS) {
                    return true;
                }
            } else if (c != '.') {
                run = 0;
            }
        }
        return false;
    }
}
