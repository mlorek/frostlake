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
import dev.frostlake.http.rest.RestStatement;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

/**
 * Password policies ({@code password-policy.yaml},
 * {@code /api/v2/databases/{database}/schemas/{schema}/password-policies}): list, create, fetch, delete,
 * {@code :rename} and the tag endpoints.
 *
 * <p>A policy is read from {@code SHOW PASSWORD POLICIES} and its eleven settings from
 * {@code DESCRIBE PASSWORD POLICY}.
 */
public final class PasswordPolicyResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/password-policies";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final String[] SETTINGS = {
        "password_min_length", "password_max_length", "password_min_upper_case_chars",
        "password_min_lower_case_chars", "password_min_numeric_chars", "password_min_special_chars",
        "password_min_age_days", "password_max_age_days", "password_max_retries", "password_lockout_time_mins",
        "password_history",
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listPasswordPolicies", this);
        router.add("POST", COLLECTION, "createPasswordPolicy", this);
        router.add("GET", ITEM, "fetchPasswordPolicy", this);
        router.add("DELETE", ITEM, "deletePasswordPolicy", this);
        router.add("POST", ITEM + ":rename", "renamePasswordPolicy", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER PASSWORD POLICY", call.qualifiedSql(name), "PASSWORD POLICY",
                call.identifier("database") + "." + call.identifier("schema") + "." + name, call.databaseSql());
        }
        switch (call.operation()) {
            case "listPasswordPolicies":
                return list(call);
            case "createPasswordPolicy":
                return create(call);
            case "fetchPasswordPolicy":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deletePasswordPolicy":
                return call.sql().action("DROP PASSWORD POLICY" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "renamePasswordPolicy":
                return rename(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW PASSWORD POLICIES" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            out.add(toJson(call, row));
        }
        return RestResponse.json(200, out);
    }

    /** The policy as the {@code PasswordPolicy} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW PASSWORD POLICIES" + RestShow.likeName(name)
            + " IN SCHEMA " + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw SecurityRest.missing("Password policy", call.identifier("database") + "."
                + call.identifier("schema") + "." + name);
        }
        return toJson(call, rows.get(0));
    }

    private static ObjectNode toJson(final RestCall call, final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.string(node, "comment", row, "comment");
        final RestIdentifier name = RestIdentifier.ofResolved(row.string("name"));
        final List<RestRow> described = call.sql().show("DESCRIBE PASSWORD POLICY " + call.qualifiedSql(name));
        for (final String setting : SETTINGS) {
            for (final RestRow property : described) {
                if (setting.toUpperCase(Locale.ROOT).equals(property.string("property"))) {
                    RestJson.integer(node, setting, property, "value");
                }
            }
        }
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.name(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.nulls(node, SETTINGS);
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " PASSWORD POLICY"
            + mode.ifNotExists() + " " + call.qualifiedSql(name));
        for (final String setting : SETTINGS) {
            sql.integer(body, setting, setting.toUpperCase(Locale.ROOT));
        }
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }

    /** {@code :rename}: to {@code targetName}, in {@code targetDatabase}.{@code targetSchema} when they are given. */
    private static RestResponse rename(final RestCall call) {
        final String targetName = call.query("targetName");
        if (targetName == null || targetName.isEmpty()) {
            throw RestException.badRequest("Missing required query parameter 'targetName'.");
        }
        final RestIdentifier target = RestIdentifier.parse(targetName, "targetName");
        final String database = call.query("targetDatabase");
        final String schema = call.query("targetSchema");
        final String into = (database != null ? RestIdentifier.parse(database, "targetDatabase")
            : call.identifier("database")).sql() + "." + (schema != null ? RestIdentifier.parse(schema, "targetSchema")
            : call.identifier("schema")).sql();
        return call.sql().action("ALTER PASSWORD POLICY" + call.ifExists() + " "
            + call.qualifiedSql(call.identifier("name")) + " RENAME TO " + into + "." + target.sql());
    }
}
