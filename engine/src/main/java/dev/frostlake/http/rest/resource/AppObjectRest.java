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
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestSql;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * What the notebook and Streamlit resources share: an object is read from its SHOW row and its DESCRIBE row,
 * and its versions from DESCRIBE's {@code default_version_*}, {@code last_version_*} and
 * {@code live_version_location_uri} columns.
 */
final class AppObjectRest {

    /** The account's code for an action that needs a live version the object does not have. */
    private static final String LIVE_VERSION_NOT_FOUND = "099108";

    /** Static helpers only. */
    private AppObjectRest() {
    }

    /**
     * The object's SHOW row, or {@code 404}.
     *
     * @param kind the object kind as SHOW spells its plural, e.g. {@code NOTEBOOKS}
     * @param display the kind as the refusal names it, e.g. {@code Notebook}
     */
    static RestRow showRow(final RestCall call, final String kind, final String display,
                           final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW " + kind + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound(display + " '" + qualifiedDisplay(call, name)
                + "' does not exist or not authorized.");
        }
        return rows.get(0);
    }

    /** A schema object's name as a refusal spells it: {@code DB.SCHEMA.NAME}, each part quoted only if it must be. */
    static String qualifiedDisplay(final RestCall call, final RestIdentifier name) {
        return RestIdentifier.display(call.identifier("database").name()) + "."
            + RestIdentifier.display(call.identifier("schema").name()) + "." + RestIdentifier.display(name.name());
    }

    /**
     * An {@code ALTER <kind> … <action>} version or Git action. A refusal for a missing live version carries the
     * account's code for it.
     *
     * @param kind {@code NOTEBOOK} or {@code STREAMLIT}
     * @param action the action as the statement spells it, with its parameters
     */
    static RestResponse versionAction(final RestCall call, final String kind, final String action) {
        try {
            return call.sql().action("ALTER " + kind + " " + call.qualifiedSql(call.identifier("name")) + " "
                + action);
        } catch (final RestException refusal) {
            if (refusal.getMessage() != null && refusal.getMessage().contains("live version is not found")) {
                throw new RestException(refusal.getStatus(), refusal.getMessage(), LIVE_VERSION_NOT_FOUND);
            }
            throw refusal;
        }
    }

    /** {@code COMMENT = '…'} after a version action, or nothing when the comment is absent. */
    static String comment(final String comment) {
        return comment == null ? "" : " COMMENT = " + RestSql.literal(comment);
    }

    /** A version's alias, written after the action's words, or nothing when the body names none. */
    static String alias(final JsonNode version, final String property, final boolean required) {
        final RestIdentifier alias = RestJson.identifier(version, property, required);
        return alias == null ? "" : " " + alias.sql();
    }

    /** The object's DESCRIBE row; the statement refuses an object that does not exist. */
    static RestRow describeRow(final RestCall call, final String kind, final RestIdentifier name) {
        return call.sql().show("DESCRIBE " + kind + " " + call.qualifiedSql(name)).get(0);
    }

    /** Whether the object exists, read from SHOW. */
    static boolean exists(final RestCall call, final String kind, final RestIdentifier name) {
        return !call.sql().showNamed("SHOW " + kind + RestShow.likeName(name) + " IN SCHEMA " + call.schemaSql(),
            name).isEmpty();
    }

    /** The properties every app object's SHOW row carries. */
    static void showProperties(final ObjectNode node, final RestRow row) {
        RestJson.name(node, "name", row, "name");
        RestJson.put(node, "comment", row.string("comment"));
        RestJson.name(node, "query_warehouse", row, "query_warehouse");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "url_id", row, "url_id");
    }

    /** DESCRIBE's version columns as {@code default_version}, the two details objects and the live URI. */
    static void versions(final ObjectNode node, final RestRow described) {
        RestJson.string(node, "default_version", described, "default_version");
        final ObjectNode defaultDetails = details(described, "default_version_");
        if (defaultDetails != null) {
            node.set("default_version_details", defaultDetails);
        }
        final ObjectNode lastDetails = details(described, "last_version_");
        if (lastDetails != null) {
            node.set("last_version_details", lastDetails);
        }
        RestJson.string(node, "live_version_location_uri", described, "live_version_location_uri");
    }

    private static ObjectNode details(final RestRow described, final String prefix) {
        if (described.nonEmpty(prefix + "name") == null) {
            return null;
        }
        final ObjectNode details = RestJson.object();
        RestJson.string(details, "name", described, prefix + "name");
        RestJson.string(details, "alias", described, prefix + "alias");
        RestJson.string(details, "location_url", described, prefix + "location_uri");
        RestJson.string(details, "source_location_uri", described, prefix + "source_location_uri");
        RestJson.string(details, "git_commit_hash", described, prefix + "git_commit_hash");
        return details;
    }

    /** A DESCRIBE cell holding a JSON array of strings, as a property; left out when absent. */
    static void list(final ObjectNode node, final String property, final RestRow described, final String column) {
        final ArrayNode values = CortexFunctions.nameArray(described.string(column));
        if (values != null) {
            node.set(property, values);
        }
    }

    /** {@code KEYWORD = ("A", "B")} for a body property holding an array of object names, or nothing. */
    static String identifierList(final JsonNode body, final String property, final String keyword) {
        final List<String> names = RestJson.strings(body, property);
        if (names == null) {
            return "";
        }
        final StringBuilder out = new StringBuilder(" ").append(keyword).append(" = (");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(RestIdentifier.parse(names.get(i), property).sql());
        }
        return out.append(')').toString();
    }

    /** A comma-separated cell as an array of its parts; an empty cell is one empty part. */
    static void commaList(final ObjectNode node, final String property, final RestRow row, final String column) {
        final String text = row.string(column);
        if (text != null) {
            final ArrayNode parts = RestJson.array();
            for (final String part : text.split(",", -1)) {
                parts.add(part);
            }
            node.set(property, parts);
        }
    }

    /** {@code :get-tags}: an app object carries no tags here, so an existing one answers an empty list. */
    static RestResponse noTags(final RestCall call, final String kind, final String display) {
        showRow(call, kind, display, call.identifier("name"));
        return RestResponse.json(200, RestJson.array());
    }

    /**
     * {@code :rename}: {@code ALTER <kind> [IF EXISTS] <name> RENAME TO <db>.<schema>.<new name>}, each part of
     * the new name taken from {@code targetDatabase}, {@code targetSchema} and {@code targetName}, or from the
     * object's own path where one is not given.
     */
    static RestResponse rename(final RestCall call, final String kind) {
        final String targetName = call.query("targetName");
        if (targetName == null || targetName.isEmpty()) {
            throw RestException.badRequest("Missing required query parameter 'targetName'.");
        }
        final String database = call.query("targetDatabase");
        final String schema = call.query("targetSchema");
        final String target = (database == null || database.isEmpty() ? call.databaseSql()
            : RestIdentifier.parse(database, "targetDatabase").sql()) + "."
            + (schema == null || schema.isEmpty() ? call.identifier("schema").sql()
            : RestIdentifier.parse(schema, "targetSchema").sql()) + "."
            + RestIdentifier.parse(targetName, "targetName").sql();
        return call.sql().action("ALTER " + kind + call.ifExists() + " "
            + call.qualifiedSql(call.identifier("name")) + " RENAME TO " + target);
    }
}
