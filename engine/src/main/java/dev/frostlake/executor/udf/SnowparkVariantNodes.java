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
import tools.jackson.core.Base64Variants;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

/**
 * The tree a handler's {@code Variant} holds and how its readers read it (live-verified). A text is read as
 * JSON by its first value alone - {@code 7 8} is the number 7 - and is itself where it is no JSON, so
 * {@code 2024-03-05} and {@code [1,undefined]} stay texts. A reader that finds no value of its kind answers
 * nothing rather than refusing: a text that spells no number is 0, a tree or a JSON null is 0, {@code false}
 * and an empty text, and only a text spelling {@code true} or a whole number other than 0 is true.
 */
public final class SnowparkVariantNodes {

    /** Reads a text's first value and leaves what follows it, as the handler's side does. */
    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /** Static helpers only — never instantiated. */
    private SnowparkVariantNodes() {
    }

    /**
     * @param text a text, or null
     * @return the JSON it spells, the text itself where it spells none, and null for no text
     */
    public static JsonNode parse(final String text) {
        if (text == null) {
            return null;
        }
        try {
            final JsonNode read = MAPPER.readTree(text);
            return read == null ? NODES.missingNode() : read;
        } catch (final RuntimeException notJson) {
            return NODES.stringNode(text);
        }
    }

    /**
     * @param elements a list's or an array's elements
     * @return the array they make, a {@code Variant} among them as the value it holds
     */
    public static JsonNode ofElements(final Iterable<?> elements) {
        final ArrayNode array = NODES.arrayNode();
        for (final Object element : elements) {
            array.add(member(element));
        }
        return array;
    }

    /**
     * @param value any value
     * @return the tree it makes: a map an object by its keys' texts, with a {@code Variant} among its values as
     *         the value that holds, and anything else as JSON writes it
     */
    public static JsonNode ofObject(final Object value) {
        if (value instanceof Map) {
            final ObjectNode object = NODES.objectNode();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.set(entry.getKey().toString(), member(entry.getValue()));
            }
            return object;
        }
        return tree(value);
    }

    private static JsonNode member(final Object value) {
        return value instanceof Variant ? ((Variant) value).asJsonNode() : tree(value);
    }

    /** A value as JSON writes it: a date its milliseconds, bytes a binary, a collection an array. */
    private static JsonNode tree(final Object value) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof Variant) {
            throw new IllegalArgumentException("No serializer found for class " + Variant.class.getName()
                + " and no properties discovered to create BeanSerializer (to avoid exception, disable"
                + " SerializationFeature.FAIL_ON_EMPTY_BEANS)");
        }
        if (value instanceof String) {
            return NODES.stringNode((String) value);
        }
        if (value instanceof Boolean) {
            return NODES.booleanNode(((Boolean) value).booleanValue());
        }
        if (value instanceof Integer || value instanceof Byte) {
            return NODES.numberNode(((Number) value).intValue());
        }
        if (value instanceof Long) {
            return NODES.numberNode(((Long) value).longValue());
        }
        if (value instanceof Short) {
            return NODES.numberNode(((Short) value).shortValue());
        }
        if (value instanceof Float) {
            return NODES.numberNode(((Float) value).floatValue());
        }
        if (value instanceof Double) {
            return NODES.numberNode(((Double) value).doubleValue());
        }
        if (value instanceof BigDecimal) {
            // Written and read back, a decimal loses its trailing zeros; one handed over directly keeps them.
            return NODES.numberNode(((BigDecimal) value).signum() == 0 ? BigDecimal.ZERO
                : ((BigDecimal) value).stripTrailingZeros());
        }
        if (value instanceof BigInteger) {
            return NODES.numberNode((BigInteger) value);
        }
        if (value instanceof byte[]) {
            return NODES.binaryNode((byte[]) value);
        }
        if (value instanceof Time) {
            return NODES.stringNode(SnowparkVariantTimes.text((Time) value));
        }
        if (value instanceof Date) {
            return NODES.numberNode(((Date) value).getTime());
        }
        if (value instanceof Map) {
            final ObjectNode object = NODES.objectNode();
            for (final Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                object.set(String.valueOf(entry.getKey()), tree(entry.getValue()));
            }
            return object;
        }
        if (value instanceof Iterable) {
            final ArrayNode array = NODES.arrayNode();
            for (final Object element : (Iterable<?>) value) {
                array.add(tree(element));
            }
            return array;
        }
        if (value.getClass().isArray()) {
            final ArrayNode array = NODES.arrayNode();
            final int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                array.add(tree(Array.get(value, i)));
            }
            return array;
        }
        return MAPPER.valueToTree(value);
    }

    /**
     * @param node a tree
     * @return its text: a text's content, a number's and a boolean's spelling, {@code null} for a JSON null,
     *         a binary's base64, and nothing for an object, an array and no value
     */
    public static String text(final JsonNode node) {
        if (node.isString()) {
            return node.stringValue();
        }
        if (node.isBinary()) {
            return Base64.getEncoder().encodeToString(node.binaryValue());
        }
        if (node.isNumber()) {
            return node.numberValue().toString();
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? "true" : "false";
        }
        return node.isNull() ? "null" : "";
    }

    /**
     * @param node a tree
     * @return its JSON
     */
    public static String json(final JsonNode node) {
        return node.isMissingNode() ? "" : node.toString();
    }

    /**
     * @param bytes bytes
     * @return their lower-case hexadecimal digits
     */
    public static String hex(final byte[] bytes) {
        final char[] digits = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            digits[2 * i] = HEX[(bytes[i] >> 4) & 0xF];
            digits[2 * i + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(digits);
    }

    /**
     * @param node a tree
     * @return the whole number it reads as, 0 where it reads as none
     */
    public static int asInt(final JsonNode node) {
        if (node.isNumber()) {
            return node.numberValue().intValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? 1 : 0;
        }
        if (!node.isString()) {
            return 0;
        }
        final String digits = signless(node.stringValue());
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return whole(digits) ? Integer.parseInt(digits) : (int) Double.parseDouble(digits);
        } catch (final NumberFormatException noNumber) {
            return 0;
        }
    }

    /**
     * @param node a tree
     * @return the whole number it reads as, 0 where it reads as none
     */
    public static long asLong(final JsonNode node) {
        if (node.isNumber()) {
            return node.numberValue().longValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? 1L : 0L;
        }
        if (!node.isString()) {
            return 0L;
        }
        final String digits = signless(node.stringValue());
        if (digits.isEmpty()) {
            return 0L;
        }
        try {
            return whole(digits) ? Long.parseLong(digits) : (long) Double.parseDouble(digits);
        } catch (final NumberFormatException noNumber) {
            return 0L;
        }
    }

    /**
     * @param node a tree
     * @return the number it reads as, 0 where it reads as none
     */
    public static double asDouble(final JsonNode node) {
        if (node.isNumber()) {
            return node.numberValue().doubleValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? 1.0 : 0.0;
        }
        if (!node.isString()) {
            return 0.0;
        }
        final String text = node.stringValue().trim();
        if (text.isEmpty()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text);
        } catch (final NumberFormatException noNumber) {
            return 0.0;
        }
    }

    /**
     * @param node a tree
     * @return whether it is {@code true}, a whole number other than 0, or a text spelling {@code true}
     */
    public static boolean asBoolean(final JsonNode node) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isInt() || node.isLong() || node.isShort()) {
            return node.numberValue().longValue() != 0L;
        }
        if (node.isBigInteger()) {
            return ((BigInteger) node.numberValue()).signum() != 0;
        }
        return node.isString() && "true".equals(node.stringValue().trim());
    }

    /**
     * @param node a tree
     * @return a number as a decimal, a number held as a double by its shortest spelling; 0 for anything else
     */
    public static BigDecimal decimal(final JsonNode node) {
        if (!node.isNumber()) {
            return BigDecimal.ZERO;
        }
        final Number number = node.numberValue();
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        if (number instanceof BigInteger) {
            return new BigDecimal((BigInteger) number);
        }
        if (number instanceof Double || number instanceof Float) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.valueOf(number.longValue());
    }

    /**
     * @param node a tree
     * @return a number's whole part; 0 for anything else
     */
    public static BigInteger bigInteger(final JsonNode node) {
        if (!node.isNumber()) {
            return BigInteger.ZERO;
        }
        final Number number = node.numberValue();
        return number instanceof BigInteger ? (BigInteger) number : decimal(node).toBigInteger();
    }

    /**
     * @param node a tree
     * @return a binary's bytes; the bytes a text spells in base64 or, where it spells none, in hexadecimal
     *         digits; null for anything else
     */
    public static byte[] binary(final JsonNode node) {
        if (node.isBinary()) {
            return node.binaryValue();
        }
        if (!node.isString()) {
            return null;
        }
        final String text = node.stringValue();
        try {
            return Base64Variants.getDefaultVariant().decode(text);
        } catch (final RuntimeException noBase64) {
            final byte[] bytes = unhex(text);
            if (bytes == null) {
                throw new UncheckedIOException(new IOException("Failed to convert " + text
                    + " to Binary. Only Hex string is supported."));
            }
            return bytes;
        }
    }

    /**
     * @param one   a tree
     * @param other another
     * @return whether they hold the same value: numbers of the same kind alone, decimals whatever their
     *         trailing zeros, trees by their members
     */
    public static boolean same(final JsonNode one, final JsonNode other) {
        if (one.isBigDecimal() && other.isBigDecimal()) {
            return one.decimalValue().compareTo(other.decimalValue()) == 0;
        }
        if (one.isArray() && other.isArray()) {
            if (one.size() != other.size()) {
                return false;
            }
            for (int i = 0; i < one.size(); i++) {
                if (!same(one.get(i), other.get(i))) {
                    return false;
                }
            }
            return true;
        }
        if (one.isObject() && other.isObject()) {
            if (one.size() != other.size()) {
                return false;
            }
            for (final Map.Entry<String, JsonNode> member : one.properties()) {
                final JsonNode counterpart = other.get(member.getKey());
                if (counterpart == null || !same(member.getValue(), counterpart)) {
                    return false;
                }
            }
            return true;
        }
        return one.equals(other);
    }

    /**
     * @param node a tree
     * @return a hash that two trees holding the same value share
     */
    public static int hash(final JsonNode node) {
        if (node.isBigDecimal()) {
            return Double.hashCode(node.decimalValue().doubleValue());
        }
        if (node.isArray()) {
            int hash = 1;
            for (final JsonNode element : node) {
                hash = 31 * hash + hash(element);
            }
            return hash;
        }
        if (node.isObject()) {
            int hash = 0;
            for (final Map.Entry<String, JsonNode> member : node.properties()) {
                hash += member.getKey().hashCode() ^ hash(member.getValue());
            }
            return hash;
        }
        return node.hashCode();
    }

    /** A text without its surrounding blanks and without a leading plus sign. */
    private static String signless(final String written) {
        final String text = written.trim();
        return text.startsWith("+") ? text.substring(1) : text;
    }

    /** Whether a text is digits alone, after a minus sign it may lead with. */
    private static boolean whole(final String text) {
        for (int i = text.charAt(0) == '-' ? 1 : 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /** The bytes an even count of hexadecimal digits spells, or null. */
    private static byte[] unhex(final String text) {
        if (text.length() % 2 != 0) {
            return null;
        }
        final byte[] bytes = new byte[text.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            final int high = Character.digit(text.charAt(2 * i), 16);
            final int low = Character.digit(text.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) {
                return null;
            }
            bytes[i] = (byte) (high * 16 + low);
        }
        return bytes;
    }
}
