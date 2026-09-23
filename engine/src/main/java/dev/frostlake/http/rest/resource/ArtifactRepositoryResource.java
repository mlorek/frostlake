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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

/**
 * The artifact repository endpoints ({@code artifact-repository.yaml}): SHOW ARTIFACT REPOSITORIES, CREATE / DROP
 * ARTIFACT REPOSITORY, and {@code PUT} as create-or-alter done here — no {@code CREATE OR ALTER ARTIFACT REPOSITORY}
 * exists — creating the repository when it is absent and otherwise setting or unsetting its comment, the one
 * property ALTER changes. {@code :rename} answers {@code 501}: no statement renames an artifact repository. The API
 * calls the package-index type {@code PIP}, which the statement spells {@code PYPI}.
 */
public final class ArtifactRepositoryResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/artifact-repositories";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listArtifactRepositories", this);
        router.add("POST", COLLECTION, "createArtifactRepository", this);
        router.add("GET", ITEM, "fetchArtifactRepository", this);
        router.add("DELETE", ITEM, "deleteArtifactRepository", this);
        router.add("PUT", ITEM, "createOrAlterArtifactRepository", this);
        router.add("POST", ITEM + ":rename", "renameArtifactRepository", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listArtifactRepositories":
                return list(call);
            case "createArtifactRepository":
                return create(call, call.createMode());
            case "fetchArtifactRepository":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteArtifactRepository":
                return call.sql().action("DROP ARTIFACT REPOSITORY" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "createOrAlterArtifactRepository":
                return createOrAlter(call);
            case "renameArtifactRepository":
                throw RestException.notImplemented("Renaming an artifact repository is not provided: no statement "
                    + "renames one.");
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static String show(final RestCall call, final String like) {
        return "SHOW ARTIFACT REPOSITORIES" + like + " IN SCHEMA " + call.schemaSql();
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        final String startsWith = call.query("startsWith");
        final Long limit = call.integer("showLimit");
        final String fromName = call.query("fromName");
        for (final RestRow row : call.sql().show(show(call, RestShow.like(call)))) {
            final String name = row.string("name");
            if (startsWith != null && (name == null || !name.startsWith(startsWith))
                    || fromName != null && (name == null || name.compareTo(fromName) < 0)) {
                continue;
            }
            if (limit != null && out.size() >= limit.longValue()) {
                break;
            }
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    private static List<RestRow> named(final RestCall call, final RestIdentifier name) {
        return call.sql().showNamed(show(call, RestShow.likeName(name)), name);
    }

    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = named(call, name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Artifact Repository '"
                + UserDefinedFunctionResource.qualified(call, name) + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        final String type = row.nonEmpty("type");
        if (type != null) {
            node.put("type", "PYPI".equals(type) ? "PIP" : type);
        }
        RestJson.put(node, "comment", row.nonEmpty("comment"));
        RestJson.name(node, "api_integration", row, "api_integration");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        return node;
    }

    private static RestResponse create(final RestCall call, final RestCreateMode mode) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        return call.sql().action(createSql(call, name, body, mode));
    }

    private static String createSql(final RestCall call, final RestIdentifier name, final JsonNode body,
                                    final RestCreateMode mode) {
        final String type = RestJson.text(body, "type");
        if (type == null) {
            throw RestException.badRequest("Missing required property 'type'.");
        }
        final String sqlType = "PIP".equalsIgnoreCase(type.trim()) ? "PYPI" : type.trim().toUpperCase(Locale.ROOT);
        if (!"PYPI".equals(sqlType) && !"APPLICATION".equals(sqlType)) {
            throw RestException.badRequest("Invalid artifact repository type '" + type + "'.");
        }
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace())
            .append(" ARTIFACT REPOSITORY").append(mode.ifNotExists()).append(' ').append(call.qualifiedSql(name))
            .append(" TYPE = ").append(sqlType);
        final RestIdentifier integration = RestJson.identifier(body, "api_integration", false);
        if (integration != null) {
            sql.append(" API_INTEGRATION = ").append(RestSql.literal(integration.name()));
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        return sql.toString();
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        call.requireBodyNames(name);
        final List<RestRow> existing = named(call, name);
        if (existing.isEmpty()) {
            return call.sql().action(createSql(call, name, body, RestCreateMode.ERROR_IF_EXISTS));
        }
        final String comment = RestJson.text(body, "comment");
        final String head = "ALTER ARTIFACT REPOSITORY " + call.qualifiedSql(name);
        return call.sql().action(comment != null ? head + " SET COMMENT = " + RestSql.literal(comment)
            : head + " UNSET COMMENT");
    }
}
