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
import dev.frostlake.http.rest.RestResource;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRouter;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.http.rest.RestStatement;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tags ({@code tag.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/tags}): list, create, fetch,
 * create-or-alter, delete, {@code :undrop} and {@code :rename}.
 *
 * <p>A tag is read from {@code SHOW TAGS IN SCHEMA}, which carries every property the {@code Tag} schema names.
 * {@code PUT} is the documented {@code CREATE OR ALTER TAG} for the allowed values and the comment, followed by
 * an {@code ALTER TAG} that sets the body's propagation or unsets it when the body names none.
 */
public final class TagResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/tags";
    private static final String ITEM = COLLECTION + "/{name}";

    /** A propagation mode is a keyword; it is checked before it is written into the statement. */
    private static final Pattern KEYWORD = Pattern.compile("[A-Za-z_]+");

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listTags", this);
        router.add("POST", COLLECTION, "createTag", this);
        router.add("GET", ITEM, "fetchTag", this);
        router.add("PUT", ITEM, "createOrAlterTag", this);
        router.add("DELETE", ITEM, "deleteTag", this);
        router.add("POST", ITEM + ":undrop", "undropTag", this);
        router.add("POST", ITEM + ":rename", "renameTag", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listTags":
                return list(call);
            case "createTag":
                return create(call);
            case "fetchTag":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterTag":
                return createOrAlter(call);
            case "deleteTag":
                return call.sql().action("DROP TAG" + call.ifExists() + " " + call.qualifiedSql(call.identifier("name")));
            case "undropTag":
                return call.sql().action("UNDROP TAG " + call.qualifiedSql(call.identifier("name")));
            case "renameTag":
                return rename(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW TAGS" + RestShow.like(call) + " IN SCHEMA " + call.schemaSql())) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The tag as the {@code Tag} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW TAGS" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Tag '" + call.identifier("database") + "." + call.identifier("schema") + "."
                + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        final String allowed = row.nonEmpty("allowed_values");
        if (allowed != null) {
            try {
                final JsonNode values = RestJson.mapper().readTree(allowed);
                if (values.isArray()) {
                    node.set("allowed_values", values);
                }
            } catch (final JacksonException unreadable) {
                // a listing that is no JSON array is left out
            }
        }
        // A tag that does not propagate reads back as NONE, as SHOW TAGS lists it.
        RestJson.string(node, "propagate", row, "propagate");
        RestJson.string(node, "on_conflict", row, "on_conflict");
        // A tag without a comment lists an empty one, which the account answers as null.
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.bool(node, "multi_value", row, "multi_value");
        RestJson.nulls(node, "allowed_values", "propagate", "on_conflict", "comment", "name", "created_on",
            "database_name", "schema_name", "owner", "owner_role_type", "multi_value");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " TAG" + mode.ifNotExists() + " "
            + call.qualifiedSql(name));
        allowedValues(sql, body);
        propagation(sql, body);
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }

    /** {@code ALLOWED_VALUES 'a', 'b'} when the body names allowed values. */
    private static void allowedValues(final RestStatement sql, final JsonNode body) {
        final List<String> values = RestJson.strings(body, "allowed_values");
        if (values == null || values.isEmpty()) {
            return;
        }
        sql.append(" ALLOWED_VALUES ");
        for (int i = 0; i < values.size(); i++) {
            sql.append((i > 0 ? ", " : "") + RestSql.literal(values.get(i)));
        }
    }

    /** {@code PROPAGATE = mode [ON_CONFLICT = …]} when the body names a propagation. */
    private static boolean propagation(final RestStatement sql, final JsonNode body) {
        final String propagate = RestJson.text(body, "propagate");
        final String onConflict = RestJson.text(body, "on_conflict");
        if (propagate == null) {
            if (onConflict != null) {
                throw RestException.badRequest("Property 'on_conflict' requires 'propagate'.");
            }
            return false;
        }
        if (!KEYWORD.matcher(propagate).matches()) {
            throw RestException.badRequest("Invalid value '" + propagate + "' for property 'propagate'.");
        }
        sql.append(" PROPAGATE = " + propagate.toUpperCase(Locale.ROOT));
        if (onConflict != null) {
            sql.append(" ON_CONFLICT = " + ("ALLOWED_VALUES_SEQUENCE".equalsIgnoreCase(onConflict)
                ? "ALLOWED_VALUES_SEQUENCE" : RestSql.literal(onConflict)));
        }
        return true;
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        final RestIdentifier named = RestJson.identifier(body, "name", false);
        if (named != null && !named.equals(name)) {
            throw RestException.badRequest("The body names tag '" + named + "' but the path names '" + name + "'.");
        }
        final RestStatement sql = new RestStatement("CREATE OR ALTER TAG " + call.qualifiedSql(name));
        allowedValues(sql, body);
        sql.string(body, "comment", "COMMENT");
        final String status = call.sql().status(sql.toString());
        final RestStatement propagation = new RestStatement("ALTER TAG " + call.qualifiedSql(name) + " SET");
        if (propagation(propagation, body)) {
            call.sql().status(propagation.toString());
        } else {
            call.sql().status("ALTER TAG " + call.qualifiedSql(name) + " UNSET PROPAGATE");
        }
        return RestResponse.success(status);
    }

    private static RestResponse rename(final RestCall call) {
        final String targetName = call.query("targetName");
        if (targetName == null || targetName.isEmpty()) {
            throw RestException.badRequest("Missing required query parameter 'targetName'.");
        }
        final String targetDatabase = call.query("targetDatabase");
        final String targetSchema = call.query("targetSchema");
        final String target = (targetDatabase != null ? RestIdentifier.parse(targetDatabase, "targetDatabase")
            : call.identifier("database")).sql() + "."
            + (targetSchema != null ? RestIdentifier.parse(targetSchema, "targetSchema")
                : call.identifier("schema")).sql() + "."
            + RestIdentifier.parse(targetName, "targetName").sql();
        return call.sql().action("ALTER TAG" + call.ifExists() + " " + call.qualifiedSql(call.identifier("name"))
            + " RENAME TO " + target);
    }
}
