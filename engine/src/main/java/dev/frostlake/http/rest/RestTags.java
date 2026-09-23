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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Locale;

/**
 * The tag endpoints every taggable resource shares: {@code :set-tags} ({@code TagAssignment} array →
 * {@code ALTER <domain> <name> SET TAG t = 'v', …}), {@code :unset-tags} ({@code TagReference} array →
 * {@code ALTER <domain> <name> UNSET TAG t, …}) and {@code :get-tags} (the {@code TAG_REFERENCES} table function,
 * narrowed to the assignments made on the object itself unless {@code withLineage=true}, which adds the ones
 * it inherits from its table, schema and database).
 */
public final class RestTags {

    /** The {@code APPLY_METHOD} of an assignment the object inherits from an object above it. */
    private static final String INHERITED = "INHERITED";

    /** Static helpers only. */
    private RestTags() {
    }

    /** Registers the three tag endpoints under a resource's item path. */
    public static void register(final RestRouter router, final String itemPath, final RestResource resource) {
        router.add("POST", itemPath + ":set-tags", "setTags", resource);
        router.add("POST", itemPath + ":unset-tags", "unsetTags", resource);
        router.add("GET", itemPath + ":get-tags", "getTags", resource);
    }

    /** Whether the call is one of the three tag endpoints. */
    public static boolean handles(final RestCall call) {
        final String operation = call.operation();
        return "setTags".equals(operation) || "unsetTags".equals(operation) || "getTags".equals(operation);
    }

    /**
     * Serves a tag endpoint.
     *
     * @param call the call
     * @param alterHead the ALTER statement's head up to the object name, e.g. {@code ALTER WAREHOUSE}; an
     *                  {@code IF EXISTS} is added when the call asks for it
     * @param objectSql the object's name as SQL
     * @param domain the object domain {@code TAG_REFERENCES} takes, e.g. {@code WAREHOUSE}
     * @param objectName the object's name as {@code TAG_REFERENCES} reads it: its qualified name
     * @param informationSchema the database whose {@code INFORMATION_SCHEMA} is asked, as SQL
     */
    public static RestResponse handle(final RestCall call, final String alterHead, final String objectSql,
                                      final String domain, final String objectName, final String informationSchema) {
        if ("setTags".equals(call.operation())) {
            final StringBuilder sql = new StringBuilder(alterHead).append(call.ifExists()).append(' ')
                .append(objectSql).append(" SET TAG ");
            int count = 0;
            for (final JsonNode assignment : call.bodyArray().values()) {
                final String value = RestJson.text(assignment, "tag_value");
                if (value == null) {
                    throw RestException.badRequest("Missing required property 'tag_value'.");
                }
                if (count++ > 0) {
                    sql.append(", ");
                }
                sql.append(tagName(assignment)).append(" = ").append(RestSql.literal(value));
            }
            if (count == 0) {
                throw RestException.badRequest("No tag assignment given.");
            }
            return call.sql().action(sql.toString());
        }
        if ("unsetTags".equals(call.operation())) {
            final StringBuilder sql = new StringBuilder(alterHead).append(call.ifExists()).append(' ')
                .append(objectSql).append(" UNSET TAG ");
            int count = 0;
            for (final JsonNode reference : call.bodyArray().values()) {
                if (count++ > 0) {
                    sql.append(", ");
                }
                sql.append(tagName(reference));
            }
            if (count == 0) {
                throw RestException.badRequest("No tag reference given.");
            }
            return call.sql().action(sql.toString());
        }
        final boolean lineage = call.flag("withLineage", false);
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SELECT * FROM TABLE(" + informationSchema
                + ".INFORMATION_SCHEMA.TAG_REFERENCES(" + RestSql.literal(objectName) + ", "
                + RestSql.literal(domain) + "))")) {
            // Without lineage only the assignments made on the object itself are listed; an inherited one
            // names the level above the object it was set on.
            final String method = row.nonEmpty("APPLY_METHOD");
            if (!lineage && method != null && INHERITED.equals(method.toUpperCase(Locale.ROOT))) {
                continue;
            }
            final ObjectNode assignment = RestJson.object();
            RestJson.name(assignment, "tag_database", row, "TAG_DATABASE");
            RestJson.name(assignment, "tag_schema", row, "TAG_SCHEMA");
            RestJson.name(assignment, "tag_name", row, "TAG_NAME");
            RestJson.put(assignment, "tag_value", row.string("TAG_VALUE"));
            RestJson.string(assignment, "level", row, "LEVEL");
            out.add(assignment);
        }
        return RestResponse.json(200, out);
    }

    /** A tag's name as SQL, qualified by the reference's database and schema when it names them. */
    private static String tagName(final JsonNode reference) {
        final RestIdentifier name = RestJson.identifier(reference, "tag_name", true);
        final RestIdentifier database = RestJson.identifier(reference, "tag_database", false);
        final RestIdentifier schema = RestJson.identifier(reference, "tag_schema", false);
        if (database != null && schema != null) {
            return database.sql() + "." + schema.sql() + "." + name.sql();
        }
        if (schema != null) {
            return schema.sql() + "." + name.sql();
        }
        return name.sql();
    }
}
