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
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Pipes ({@code pipe.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/pipes}): list, create, fetch,
 * delete, {@code :refresh} ({@code ALTER PIPE … REFRESH [PREFIX = …] [MODIFIED_AFTER = …]}) and the tag
 * endpoints.
 *
 * <p>A pipe is read from {@code SHOW PIPES}, its {@code definition} being the {@code copy_statement}. A create
 * writes {@code CREATE PIPE … AS <copy_statement>}: the COPY statement is SQL by the schema's own definition, so
 * it is sent as written and the parser decides whether it is one.
 */
public final class PipeResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/pipes";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listPipes", this);
        router.add("POST", COLLECTION, "createPipe", this);
        router.add("GET", ITEM, "fetchPipe", this);
        router.add("DELETE", ITEM, "deletePipe", this);
        router.add("POST", ITEM + ":refresh", "refreshPipe", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER PIPE", call.qualifiedSql(name), "PIPE",
                SchemaObjectNames.dotted(call, name),
                SchemaObjectNames.informationSchemaDatabase(call));
        }
        switch (call.operation()) {
            case "listPipes":
                return list(call);
            case "createPipe":
                return create(call);
            case "fetchPipe":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deletePipe":
                return call.sql().action("DROP PIPE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "refreshPipe":
                return refresh(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW PIPES" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql())) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The pipe as the {@code Pipe} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW PIPES" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Pipe '" + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.put(node, "comment", row.string("comment"));
        RestJson.string(node, "error_integration", row, "error_integration");
        RestJson.string(node, "integration", row, "integration");
        RestJson.string(node, "copy_statement", row, "definition");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "pattern", row, "pattern");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "invalid_reason", row, "invalid_reason");
        RestJson.string(node, "budget", row, "budget");
        RestJson.nulls(node, "name", "comment", "auto_ingest", "error_integration", "aws_sns_topic", "integration",
            "copy_statement", "created_on", "database_name", "schema_name", "owner", "pattern", "owner_role_type",
            "invalid_reason", "budget");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final String copy = RestJson.text(body, "copy_statement");
        if (copy == null || copy.isBlank()) {
            throw RestException.badRequest("Missing required property 'copy_statement'.");
        }
        final RestCreateMode mode = call.createMode();
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace()).append(" PIPE")
            .append(mode.ifNotExists()).append(' ').append(call.qualifiedSql(name));
        final Boolean autoIngest = RestJson.bool(body, "auto_ingest");
        if (autoIngest != null) {
            sql.append(" AUTO_INGEST = ").append(autoIngest.booleanValue() ? "TRUE" : "FALSE");
        }
        final RestIdentifier errorIntegration = RestJson.identifier(body, "error_integration", false);
        if (errorIntegration != null) {
            sql.append(" ERROR_INTEGRATION = ").append(errorIntegration.sql());
        }
        final String topic = RestJson.text(body, "aws_sns_topic");
        if (topic != null) {
            sql.append(" AWS_SNS_TOPIC = ").append(RestSql.literal(topic));
        }
        final String integration = RestJson.text(body, "integration");
        if (integration != null) {
            sql.append(" INTEGRATION = ").append(RestSql.literal(integration));
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        sql.append(" AS ").append(copy);
        return call.sql().action(sql.toString());
    }

    private static RestResponse refresh(final RestCall call) {
        final StringBuilder sql = new StringBuilder("ALTER PIPE").append(call.ifExists()).append(' ')
            .append(call.qualifiedSql(call.identifier("name"))).append(" REFRESH");
        final String prefix = call.query("prefix");
        if (prefix != null) {
            sql.append(" PREFIX = ").append(RestSql.literal(prefix));
        }
        final String modifiedAfter = call.query("modified_after");
        if (modifiedAfter != null) {
            sql.append(" MODIFIED_AFTER = ").append(RestSql.literal(modifiedAfter));
        }
        return call.sql().action(sql.toString());
    }
}
