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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Secrets ({@code secret.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/secrets}): list, create,
 * fetch and delete.
 *
 * <p>A secret is read from {@code SHOW SECRETS} and {@code DESCRIBE SECRET}, neither of which shows a credential:
 * a password, a secret string or an OAuth refresh token is written and never answered back. SHOW SECRETS takes no
 * STARTS WITH or LIMIT, so {@code startsWith}, {@code showLimit} and {@code fromName} narrow its rows here.
 */
public final class SecretResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/secrets";
    private static final String ITEM = COLLECTION + "/{name}";
    /** What a credential is answered as. */
    private static final String MASK = "********";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listSecrets", this);
        router.add("POST", COLLECTION, "createSecret", this);
        router.add("GET", ITEM, "fetchSecret", this);
        router.add("DELETE", ITEM, "deleteSecret", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listSecrets":
                return list(call);
            case "createSecret":
                return create(call);
            case "fetchSecret":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteSecret":
                return call.sql().action("DROP SECRET" + call.ifExists() + " " + call.qualifiedSql(call.identifier("name")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW SECRETS" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            out.add(toJson(call, row));
        }
        return RestResponse.json(200, out);
    }

    /** The secret as the {@code Secret} schema of its type describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW SECRETS" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw SecurityRest.missing("Secret", call.identifier("database") + "." + call.identifier("schema") + "."
                + name);
        }
        return toJson(call, rows.get(0));
    }

    private static ObjectNode toJson(final RestCall call, final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        final String type = row.nonEmpty("secret_type");
        RestJson.put(node, "type", type);
        RestJson.string(node, "comment", row, "comment");
        final RestIdentifier name = RestIdentifier.ofResolved(row.string("name"));
        final List<RestRow> described = call.sql().show("DESCRIBE SECRET " + call.qualifiedSql(name));
        final RestRow secret = described.isEmpty() ? row : described.get(0);
        // Each type answers its own properties; a credential is answered masked, never as written.
        if ("PASSWORD".equals(type)) {
            RestJson.string(node, "username", secret, "username");
            node.put("password", MASK);
        } else if ("GENERIC_STRING".equals(type)) {
            node.put("secret_string", MASK);
        } else if ("OAUTH2".equals(type)) {
            RestJson.name(node, "api_authentication", secret, "integration_name");
            final String scopes = secret.string("oauth_scopes");
            if (scopes == null) {
                node.putNull("oauth_scopes");
            } else {
                node.set("oauth_scopes", SecurityRest.array(SecurityRest.items(scopes)));
            }
            RestJson.string(node, "oauth_refresh_token_expiry_time", secret, "oauth_refresh_token_expiry_time");
        } else if ("CLOUD_PROVIDER_TOKEN".equals(type)) {
            RestJson.name(node, "api_authentication", secret, "integration_name");
        } else if ("SYMMETRIC_KEY".equals(type)) {
            RestJson.string(node, "algorithm", secret, "algorithm");
        }
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.name(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final String type = SecurityRest.word(body, "type", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " SECRET" + mode.ifNotExists()
            + " " + call.qualifiedSql(name));
        sql.property("TYPE", type);
        if ("PASSWORD".equals(type)) {
            SecurityRest.require(body, "username", "password");
            sql.string(body, "username", "USERNAME");
            sql.string(body, "password", "PASSWORD");
        } else if ("GENERIC_STRING".equals(type)) {
            SecurityRest.require(body, "secret_string");
            sql.string(body, "secret_string", "SECRET_STRING");
        } else if ("OAUTH2".equals(type)) {
            SecurityRest.require(body, "api_authentication");
            sql.identifier(body, "api_authentication", "API_AUTHENTICATION");
            sql.stringList(body, "oauth_scopes", "OAUTH_SCOPES");
            sql.string(body, "oauth_refresh_token", "OAUTH_REFRESH_TOKEN");
            sql.string(body, "oauth_refresh_token_expiry_time", "OAUTH_REFRESH_TOKEN_EXPIRY_TIME");
        } else if ("CLOUD_PROVIDER_TOKEN".equals(type)) {
            SecurityRest.require(body, "api_authentication");
            sql.identifier(body, "api_authentication", "API_AUTHENTICATION");
        } else if ("SYMMETRIC_KEY".equals(type)) {
            final String algorithm = SecurityRest.word(body, "algorithm", true);
            if (algorithm != null) {
                sql.property("ALGORITHM", algorithm);
            }
        }
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }
}
