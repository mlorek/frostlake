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

import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestCreateMode;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the three integration resources share: the SHOW row and the DESCRIBE properties of one integration,
 * the list and object values DESCRIBE renders, the CREATE head, and the tag endpoints on the INTEGRATION domain.
 */
final class RestIntegrations {

    private RestIntegrations() {
    }

    /** The SHOW row of the integration of that kind and exact name, or {@code 404}. */
    static RestRow row(final RestCall call, final String kindWords, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW " + kindWords + " INTEGRATIONS" + RestShow.likeName(name),
            name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Integration '" + name + "' does not exist or not authorized.");
        }
        return rows.get(0);
    }

    /** Every SHOW row of the kind, narrowed by the {@code like} parameter. */
    static List<RestRow> rows(final RestCall call, final String kindWords) {
        return call.sql().show("SHOW " + kindWords + " INTEGRATIONS" + RestShow.like(call));
    }

    /** The integration's DESCRIBE answer as property → value. */
    static Map<String, String> describe(final RestCall call, final String kindWords, final String resolvedName) {
        final Map<String, String> out = new LinkedHashMap<>();
        for (final RestRow row : call.sql().show("DESCRIBE " + kindWords + " INTEGRATION "
                + RestIdentifier.quote(resolvedName))) {
            out.put(row.string("property"), row.string("property_value"));
        }
        return out;
    }

    /** The properties every integration schema shares: name, enabled, comment and created_on. */
    static ObjectNode base(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.bool(node, "enabled", row, "enabled");
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        return node;
    }

    /** A text property; null when the value is absent or empty. */
    static void text(final ObjectNode node, final String property, final String value) {
        RestJson.put(node, property, value == null || value.isEmpty() ? null : value);
    }

    /** A list DESCRIBE shows comma-joined, as a JSON array; null when the value is absent or empty. */
    static void list(final ObjectNode node, final String property, final String value) {
        if (value == null || value.isEmpty()) {
            node.putNull(property);
            return;
        }
        final ArrayNode items = RestJson.array();
        if (!value.isEmpty()) {
            for (final String item : value.split(",", -1)) {
                items.add(item);
            }
        }
        node.set(property, items);
    }

    /** A value DESCRIBE shows as a JSON object, parsed; an empty object when it is absent or not JSON. */
    static JsonNode object(final String value) {
        if (value == null || value.isEmpty()) {
            return RestJson.object();
        }
        try {
            final JsonNode parsed = RestJson.mapper().readTree(value);
            return parsed != null && parsed.isObject() ? parsed : RestJson.object();
        } catch (final JacksonException notJson) {
            return RestJson.object();
        }
    }

    /** A JSON object's text member, or null. */
    static String member(final JsonNode object, final String name) {
        final JsonNode value = object.get(name);
        return value == null || value.isNull() ? null : value.isString() ? value.stringValue() : value.toString();
    }

    /** {@code CREATE [OR REPLACE] <kind> INTEGRATION [IF NOT EXISTS] "NAME"} for the call's createMode. */
    static String createHead(final RestCall call, final String kindWords, final RestIdentifier name) {
        final RestCreateMode mode = call.createMode();
        return "CREATE" + mode.orReplace() + " " + kindWords + " INTEGRATION" + mode.ifNotExists() + " " + name.sql();
    }

    /** {@code ('a', 'b')}: a list of string literals. */
    static String literalList(final List<String> values) {
        final StringBuilder out = new StringBuilder("(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(RestSql.literal(values.get(i)));
        }
        return out.append(')').toString();
    }

    /** The tag endpoints of an integration: ALTER … INTEGRATION SET/UNSET TAG and the INTEGRATION domain. */
    static RestResponse tags(final RestCall call, final String kindWords) {
        final RestIdentifier name = call.identifier("name");
        return RestTags.handle(call, "ALTER " + kindWords + " INTEGRATION", name.sql(), "INTEGRATION",
            RestIdentifier.display(name.name()), "SNOWFLAKE");
    }

    /** The body's name, which the path's must match when a path names the integration too. */
    static RestIdentifier bodyName(final JsonNode body, final RestIdentifier pathName) {
        final RestIdentifier named = RestJson.identifier(body, "name", pathName == null);
        if (pathName != null && named != null && !named.equals(pathName)) {
            throw RestException.badRequest("The body names integration '" + named + "' but the path names '"
                + pathName + "'.");
        }
        return named != null ? named : pathName;
    }
}
