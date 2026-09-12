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

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

/**
 * The {@code ::ARRAY} and {@code ::OBJECT} casts, sharing the semantics of the TO_ARRAY function so that a
 * cast and the function agree. Both were previously no-ops that let a bare VARCHAR be stored in an
 * ARRAY-declared column, which then matched nothing in ARRAY_CONTAINS and similar.
 */
final class SemiStructuredCasts {

    private SemiStructuredCasts() {
    }

    /**
     * A value cast to ARRAY: an existing array is returned unchanged, JSON null yields null, and any other
     * scalar becomes a single-element array — Snowflake's documented TO_ARRAY behaviour ("for any other
     * value, the value returned is a single-element array that contains this value").
     */
    static Object toArrayText(final Object value) {
        // A QUOTED JSON string ('"[\"ROLE\"]"' — a string VALUE whose content merely looks like an
        // array) is a scalar: TO_ARRAY wraps its unquoted text as the single element.
        final String quoted = quotedJsonStringText(value);
        if (quoted != null) {
            final ArrayNode wrappedString = ArrayFunctionHelper.MAPPER.createArrayNode();
            wrappedString.add(ArrayFunctionHelper.MAPPER.getNodeFactory().textNode(quoted));
            return VariantValue.ofNode(wrappedString);
        }
        final ArrayNode existing = ArrayFunctionHelper.parseArray(value);
        if (existing != null) {
            return VariantValue.ofNode(existing);
        }
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        if (node != null && node.isNull()) {
            return null;
        }
        final ArrayNode wrapped = ArrayFunctionHelper.MAPPER.createArrayNode();
        wrapped.add(ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value));
        return VariantValue.ofNode(wrapped);
    }

    /**
     * A value cast to OBJECT: an object passes through unchanged and JSON null yields null. A scalar cannot
     * become an object (Snowflake rejects it), so it is reported rather than silently kept.
     */
    static Object toObjectText(final Object value) {
        if (quotedJsonStringText(value) != null) {
            throw objectCastFailure(value);
        }
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        if (node != null && node.isNull()) {
            return null;
        }
        if (node != null && node.isObject()) {
            return VariantValue.ofNode(node);
        }
        throw objectCastFailure(value);
    }

    /**
     * Snowflake's rejection of a value that cannot become an OBJECT. A VARIANT source names itself and
     * its target the way every other variant cast failure does — {@code Failed to cast variant value 1
     * to OBJECT} — where anything else keeps the plainer sentence.
     */
    private static RuntimeException objectCastFailure(final Object value) {
        if (value instanceof VariantValue) {
            return new RuntimeException("Failed to cast variant value "
                + ((VariantValue) value).text() + " to OBJECT");
        }
        return new RuntimeException("Cannot cast value to OBJECT: " + value);
    }

    /**
     * The unquoted text of a value that is a QUOTED JSON string ({@code "..."}) — the form path
     * extraction now uses for string values whose content looks like JSON structure — or null when the
     * value is anything else.
     */
    static String quotedJsonStringText(final Object value) {
        return JsonTypeHelper.quotedJsonStringText(value);
    }
}
