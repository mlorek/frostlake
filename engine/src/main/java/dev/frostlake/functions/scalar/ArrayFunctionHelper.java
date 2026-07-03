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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

/** Shared helpers for array/object scalar functions. */
public class ArrayFunctionHelper {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    /** Parse a value to JsonNode. Returns null if unparseable. */
    public static JsonNode parseNode(final Object value) {
        if (value == null) return null;
        if (value instanceof JsonNode) return (JsonNode) value;
        try { return MAPPER.readTree(value.toString().trim()); }
        catch (final Exception e) { return null; }
    }

    /** Parse value as ArrayNode, or return null. */
    public static ArrayNode parseArray(final Object value) {
        JsonNode node = parseNode(value);
        return (node != null && node.isArray()) ? (ArrayNode) node : null;
    }

    /** Convert a Java value to a JsonNode for insertion into arrays/objects. */
    public static JsonNode toNode(final ObjectMapper mapper, final Object value) {
        if (value == null) return mapper.nullNode();
        if (value instanceof Boolean) return mapper.getNodeFactory().booleanNode((Boolean) value);
        if (value instanceof Long || value instanceof Integer)
            return mapper.getNodeFactory().numberNode(((Number) value).longValue());
        if (value instanceof Number) return mapper.getNodeFactory().numberNode(((Number) value).doubleValue());
        String s = value.toString().trim();
        if (s.startsWith("{") || s.startsWith("[")) {
            try { return mapper.readTree(s); } catch (final Exception ignored) {}
        }
        return mapper.getNodeFactory().textNode(s);
    }

    /**
     * JsonNode equality that also treats two numeric nodes of equal value as equal (2 == 2 whether one is an
     * int node and the other a long/double node). A strict {@link JsonNode#equals} distinguishes IntNode from
     * LongNode, which makes ARRAY_CONTAINS / ARRAY_REMOVE miss numeric members built by ARRAY_CONSTRUCT.
     */
    public static boolean nodesEqual(final JsonNode a, final JsonNode b) {
        if (a.equals(b)) {
            return true;
        }
        return a.isNumber() && b.isNumber() && a.doubleValue() == b.doubleValue();
    }

    /**
     * Ordering comparison between two JsonNodes: numeric-aware (two numeric nodes compare by value),
     * otherwise a lexical comparison of their textual form. Used by ARRAY_SORT / ARRAY_MIN / ARRAY_MAX.
     */
    public static int compareNodes(final JsonNode a, final JsonNode b) {
        if (a.isNumber() && b.isNumber()) {
            return Double.compare(a.doubleValue(), b.doubleValue());
        }
        final String sa = a.isTextual() ? a.asText() : a.toString();
        final String sb = b.isTextual() ? b.asText() : b.toString();
        return sa.compareTo(sb);
    }

    /** Convert a JsonNode back to a plain Java value. Objects/arrays stay as JSON strings. */
    public static Object fromNode(final JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual()) return node.asText();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isLong() || node.isInt()) return node.asLong();
        if (node.isNumber()) return node.asDouble();
        return node.toString();
    }
}
