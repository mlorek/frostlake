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
package dev.frostlake.http.rest.resource;

import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.metastore.QualifiedName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** What the network rule, network policy, password policy and secret resources share. */
final class SecurityRest {

    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private SecurityRest() {
    }

    /**
     * A body property that SQL takes as a bare word — a TYPE, a MODE, an ALGORITHM — upper-cased, or null when it
     * is absent.
     *
     * @throws RestException {@code 400} when it is required and absent, or is not a single word
     */
    static String word(final JsonNode body, final String property, final boolean required) {
        final String value = RestJson.text(body, property);
        if (value == null) {
            if (required) {
                throw RestException.missingProperty(property);
            }
            return null;
        }
        if (!WORD.matcher(value).matches()) {
            throw RestException.badRequest("Invalid value '" + value + "' for property '" + property + "'.");
        }
        return value.toUpperCase(Locale.ROOT);
    }

    /** The items of a list a description shows as text: comma-separated, possibly in brackets. */
    static List<String> items(final String text) {
        final List<String> items = new ArrayList<>();
        if (text == null) {
            return items;
        }
        String inner = text.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1);
        }
        for (final String item : inner.split(",")) {
            final String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    /**
     * The rules a network policy's rule list holds, each by its own name: the description lists them as JSON
     * objects naming each rule's fully qualified name.
     */
    static List<String> ruleNames(final String described) {
        final List<String> names = new ArrayList<>();
        if (described == null || described.isEmpty()) {
            return names;
        }
        for (final JsonNode rule : RestJson.mapper().readTree(described).values()) {
            names.add(QualifiedName.parse(rule.path("fullyQualifiedRuleName").asString()).last());
        }
        return names;
    }

    /** A JSON array of strings. */
    static ArrayNode array(final List<String> items) {
        final ArrayNode array = RestJson.array();
        for (final String item : items) {
            array.add(item);
        }
        return array;
    }

    /** Refuses a body that leaves out any of these properties. */
    static void require(final JsonNode body, final String... properties) {
        for (final String property : properties) {
            if (!RestJson.present(body, property)) {
                throw RestException.missingProperty(property);
            }
        }
    }

    /** The answer for an object a SHOW does not list, worded as the statement's own refusal is. */
    static RestException missing(final String noun, final String name) {
        return RestException.notFound(noun + " '" + name + "' does not exist or not authorized.");
    }
}
