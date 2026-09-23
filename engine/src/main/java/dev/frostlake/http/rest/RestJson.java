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

package dev.frostlake.http.rest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Building response bodies from SHOW rows and reading request bodies. A response carries every property its
 * schema defines, as the account's does: a value that is unknown or empty is sent as {@code null}.
 */
public final class RestJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Static helpers only. */
    private RestJson() {
    }

    /** The shared mapper. */
    public static ObjectMapper mapper() {
        return MAPPER;
    }

    /** A new, empty JSON object. */
    public static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    /** A new, empty JSON array. */
    public static ArrayNode array() {
        return MAPPER.createArrayNode();
    }

    // ---- response properties -----------------------------------------------------------------------------

    /** Sets a text property; null when the value is. */
    public static void put(final ObjectNode node, final String property, final String value) {
        if (value != null) {
            node.put(property, value);
        } else {
            node.putNull(property);
        }
    }

    /** Sets an integer property; null when the value is. */
    public static void put(final ObjectNode node, final String property, final Long value) {
        if (value != null) {
            node.put(property, value.longValue());
        } else {
            node.putNull(property);
        }
    }

    /** Sets a boolean property; null when the value is. */
    public static void put(final ObjectNode node, final String property, final Boolean value) {
        if (value != null) {
            node.put(property, value.booleanValue());
        } else {
            node.putNull(property);
        }
    }

    /** Sets properties the engine has no value for to null, as the account sends them. */
    public static void nulls(final ObjectNode node, final String... properties) {
        for (final String property : properties) {
            if (!node.has(property)) {
                node.putNull(property);
            }
        }
    }

    /** A column's text as a property; an empty value counts as unknown. */
    public static void string(final ObjectNode node, final String property, final RestRow row, final String column) {
        put(node, property, row.nonEmpty(column));
    }

    /** A column's integer as a property. */
    public static void integer(final ObjectNode node, final String property, final RestRow row, final String column) {
        put(node, property, row.integer(column));
    }

    /** A column's flag as a boolean property. */
    public static void bool(final ObjectNode node, final String property, final RestRow row, final String column) {
        put(node, property, row.bool(column));
    }

    /**
     * A column's flag as the text {@code "true"} or {@code "false"}: how the specification types the few
     * properties it declares as a string enumeration of the two words.
     */
    public static void boolText(final ObjectNode node, final String property, final RestRow row,
                                final String column) {
        final Boolean value = row.bool(column);
        put(node, property, value == null ? null : value.booleanValue() ? "true" : "false");
    }

    /** A column's instant as an ISO-8601 date-time property. */
    public static void timestamp(final ObjectNode node, final String property, final RestRow row,
                                 final String column) {
        put(node, property, row.timestamp(column));
    }

    /** A column holding an object name, spelled as the API spells names (see {@link RestIdentifier#display}). */
    public static void name(final ObjectNode node, final String property, final RestRow row, final String column) {
        final String value = row.nonEmpty(column);
        put(node, property, value == null ? null : RestIdentifier.display(value));
    }

    // ---- request properties ------------------------------------------------------------------------------

    /** Whether the body sets the property to a value other than null. */
    public static boolean present(final JsonNode body, final String property) {
        final JsonNode value = body == null ? null : body.get(property);
        return value != null && !value.isNull() && !value.isMissingNode();
    }

    /**
     * A property's value as text: a string as written, a number or a boolean in its JSON spelling.
     *
     * @return the text, or null when the property is absent or null
     * @throws RestException {@code 400} when the value is an object or an array
     */
    public static String text(final JsonNode body, final String property) {
        if (!present(body, property)) {
            return null;
        }
        final JsonNode value = body.get(property);
        if (value.isObject() || value.isArray()) {
            throw RestException.unreadable("property '" + property + "' is not a single value");
        }
        return value.isString() ? value.stringValue() : value.toString();
    }

    /**
     * A property's value as an integer; a string holding a whole number is accepted.
     *
     * @throws RestException {@code 400} when the value is not a whole number
     */
    public static Long integer(final JsonNode body, final String property) {
        if (!present(body, property)) {
            return null;
        }
        final JsonNode value = body.get(property);
        if (value.isIntegralNumber()) {
            return Long.valueOf(value.longValue());
        }
        if (value.isString()) {
            try {
                return Long.valueOf(value.stringValue().trim());
            } catch (final NumberFormatException notNumeric) {
                throw RestException.unreadable("property '" + property + "' is not an integer");
            }
        }
        throw RestException.unreadable("property '" + property + "' is not an integer");
    }

    /**
     * A property's value as a boolean; the strings {@code "true"} and {@code "false"} are accepted in any case.
     *
     * @throws RestException {@code 400} when the value is neither
     */
    public static Boolean bool(final JsonNode body, final String property) {
        if (!present(body, property)) {
            return null;
        }
        final JsonNode value = body.get(property);
        if (value.isBoolean()) {
            return Boolean.valueOf(value.booleanValue());
        }
        if (value.isString()) {
            final String text = value.stringValue().trim().toLowerCase(Locale.ROOT);
            if ("true".equals(text)) {
                return Boolean.TRUE;
            }
            if ("false".equals(text)) {
                return Boolean.FALSE;
            }
        }
        throw RestException.unreadable("property '" + property + "' is not a boolean");
    }

    /**
     * A property holding an object name.
     *
     * @param required whether a missing name is refused
     * @return the name, or null when it is absent and not required
     * @throws RestException {@code 400} when the name is required and absent (code {@code 390400}), or is not an
     *                       identifier
     */
    public static RestIdentifier identifier(final JsonNode body, final String property, final boolean required) {
        final String text = text(body, property);
        if (text == null || text.isEmpty()) {
            if (required) {
                throw RestException.missingProperty(property);
            }
            return null;
        }
        return RestIdentifier.parse(text, property);
    }

    /**
     * A property holding an array of strings.
     *
     * @return the strings, or null when the property is absent
     * @throws RestException {@code 400} when the value is not an array of scalars
     */
    public static List<String> strings(final JsonNode body, final String property) {
        if (!present(body, property)) {
            return null;
        }
        final JsonNode value = body.get(property);
        if (!value.isArray()) {
            throw RestException.unreadable("property '" + property + "' is not an array");
        }
        final List<String> out = new ArrayList<>();
        for (final JsonNode item : value.values()) {
            if (item.isObject() || item.isArray() || item.isNull()) {
                throw RestException.unreadable("property '" + property + "' is not an array of strings");
            }
            out.add(item.isString() ? item.stringValue() : item.toString());
        }
        return out;
    }
}
