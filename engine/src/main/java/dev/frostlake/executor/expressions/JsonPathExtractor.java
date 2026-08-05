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

package dev.frostlake.executor.expressions;

import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.Map;
import java.util.Set;

/**
 * Pure JSON/VARIANT path helpers extracted from {@link ExpressionEvaluatorVisitor}: extract an object
 * property or array element from a JSON string, convert a JsonNode to a Java value, and render a Java
 * value as a JSON literal.
 */
final class JsonPathExtractor {

    private static final ObjectMapper JACKSON = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private JsonPathExtractor() {
    }

    /** Extract a property from a JSON object string using Jackson. Returns null if not found or invalid JSON. */
    static Object extractJsonProperty(final String jsonString, final String property) {
        if (jsonString == null) return null;
        String s = jsonString.trim();
        if (!s.startsWith("{") && !s.startsWith("[")) return null;
        try {
            return extractProperty(JACKSON.readTree(s), property);
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * Node-based extraction for an already-parsed tree (a typed semi-structured value): avoids the
     * canonical-text round trip, which would lose the number FAMILY (a DOUBLE member re-parses as a
     * plain decimal).
     */
    static Object extractProperty(final JsonNode root, final String property) {
        if (root == null || (!root.isObject() && !root.isArray())) return null;
        JsonNode node = root.get(property);
        if (node == null && root.isObject()) {
            // Case-insensitive fallback
            final Set<Map.Entry<String, JsonNode>> fields = root.properties();
            for (final Map.Entry<String, JsonNode> field : fields) {
                if (field.getKey().equalsIgnoreCase(property)) {
                    node = field.getValue();
                    break;
                }
            }
        }
        return jsonNodeToJava(node);
    }

    static Object extractJsonArrayElement(final String jsonString, final int index) {
        if (jsonString == null) return null;
        String s = jsonString.trim();
        if (!s.startsWith("[")) return null;
        try {
            return extractElement(JACKSON.readTree(s), index);
        } catch (final Exception e) {
            return null;
        }
    }

    /** Node-based array-element extraction — see {@link #extractProperty(JsonNode, String)}. */
    static Object extractElement(final JsonNode root, final int index) {
        if (root == null || !root.isArray() || index < 0 || index >= root.size()) return null;
        return jsonNodeToJava(root.get(index));
    }

    /** Convert a JsonNode to a suitable Java value. Objects/arrays are returned as their JSON string. */
    private static Object jsonNodeToJava(final JsonNode node) {
        // A missing element (path not present) is a SQL NULL; a present JSON null stays a JSON null VARIANT
        // ("null"), distinct from SQL NULL (Snowflake), so IS_NULL_VALUE(col:field) is TRUE and IS NULL is FALSE.
        if (node == null) return null;
        // A VARIANT `undefined` ELEMENT is SQL NULL, never a JSON null — live:
        // ARRAY_CONSTRUCT(1,NULL,2)[1] IS NULL is TRUE while PARSE_JSON('[1,null,2]')[1] IS NULL is FALSE.
        if (VariantUndefined.isUndefined(node)) return null;
        if (node.isNull()) return VariantValue.of("null");
        if (node.isTextual()) {
            // A STRING whose content itself looks like JSON structure ('["ROLE"]', '{"a":1}') keeps its
            // QUOTED JSON form — unquoting it made a string value indistinguishable from a real
            // array/object's JSON text, so ::ARRAY wrongly PARSED it (Snowflake's ::ARRAY, which is
            // TO_ARRAY, wraps a string into a one-element array instead). Plain strings unwrap as before.
            // The literal string "null" keeps the quoted form for the same reason: bare "null" IS this
            // engine's JSON-null marker, and unquoting turned a real string value into a null member
            // when re-embedded (OBJECT_AGG over {"v": "null"} must keep the STRING, as Snowflake does).
            final String text = node.asText();
            final String trimmedText = text.trim();
            if (trimmedText.startsWith("[") || trimmedText.startsWith("{") || "null".equals(text)) {
                return node.toString();
            }
            return text;
        }
        if (node.isBoolean()) return node.asBoolean();
        if (node.isLong() || node.isInt()) return node.asLong();
        // NUMBER(38,0)-scale values live in BigInteger/BigDecimal nodes; asDouble() would round
        // 21000000006420544706 to 21000000006420546000. Double-provenance decimals (re-parsed from
        // canonical text: negative scale or >15 significant digits) go back to Double so the
        // FLOAT::VARCHAR 10-significant-digit rendering applies.
        if (node.isBigDecimal()
                && (node.decimalValue().scale() < 0
                    || (node.decimalValue().scale() > 0 && node.decimalValue().precision() > 15))) {
            return node.asDouble();
        }
        if (node.isBigInteger() || node.isBigDecimal()) return node.decimalValue();
        if (node.isDouble() || node.isFloat() || node.isNumber()) return node.asDouble();
        // Object or array: a typed semi-structured value carrying the node's JSON text.
        return VariantValue.ofNode(node);
    }

    static String formatJsonValue(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Number) {
            return value.toString();
        }
        // String (and anything else rendered as text): JSON-escape the content — a raw quote, backslash
        // or control character (a data value with an embedded newline is real-world common) would make
        // the enclosing object/array literal invalid JSON, and every later path access over it null.
        return JsonNodeFactory.instance.textNode(value.toString()).toString();
    }
}
