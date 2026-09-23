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
 * The service function endpoints ({@code function.yaml}): a CREATE FUNCTION naming a SERVICE and an ENDPOINT, with
 * the path the calls go to as its body; SHOW USER FUNCTIONS and DESCRIBE FUNCTION for the listing and the fetch,
 * which report every user function (a service function in the {@code ServiceFunction} shape, any other in the
 * user-defined function one, as the account's listing does); DROP FUNCTION; and the tag endpoints.
 * {@code :execute} answers {@code 501}: no service runs, so none can answer a call.
 */
public final class FunctionResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/functions";
    private static final String ITEM = COLLECTION + "/{nameWithArgs}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listFunctions", this);
        router.add("POST", COLLECTION, "createFunction", this);
        router.add("GET", ITEM, "fetchFunction", this);
        router.add("DELETE", ITEM, "deleteFunction", this);
        router.add("POST", COLLECTION + "/{name}:execute", "executeFunction", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RoutineSignature signature = signature(call);
            return RestTags.handle(call, "ALTER FUNCTION", call.qualifiedSql(signature.name()) + signature.typesSql(),
                "FUNCTION", call.qualifiedSql(signature.name()) + signature.typesSql(),
                call.identifier("database").sql());
        }
        switch (call.operation()) {
            case "listFunctions":
                return list(call);
            case "createFunction":
                return call.sql().action(createSql(call));
            case "fetchFunction":
                return RestResponse.json(200, fetch(call, signature(call)));
            case "deleteFunction":
                return delete(call);
            case "executeFunction":
                throw RestException.notImplemented("Executing a service function is not provided: no service runs "
                    + "to answer the call.");
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse delete(final RestCall call) {
        final RoutineSignature signature = signature(call);
        return call.sql().action("DROP FUNCTION" + call.ifExists() + " " + call.qualifiedSql(signature.name())
            + signature.typesSql());
    }

    private static RoutineSignature signature(final RestCall call) {
        return RoutineSignature.parse(call.pathParameter("nameWithArgs"), "nameWithArgs");
    }

    private static String show(final RestCall call, final String like) {
        return "SHOW USER FUNCTIONS" + like + " IN SCHEMA " + call.schemaSql();
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show(show(call, RestShow.like(call)))) {
            final List<RestRow> describe = RoutineSupport.describe(call, "FUNCTION",
                RestIdentifier.ofResolved(row.string("name")), RoutineSupport.showArgumentTypes(row));
            out.add(RoutineSupport.property(describe, "service") != null ? toJson(row, describe)
                : RoutineSupport.toJson(row, describe, false, true));
        }
        return RestResponse.json(200, out);
    }

    private static ObjectNode fetch(final RestCall call, final RoutineSignature signature) {
        final List<RestRow> shows = call.sql().showNamed(show(call, RestShow.likeName(signature.name())),
            signature.name());
        final String missing = "Function '"
            + UserDefinedFunctionResource.qualified(call, signature.name()) + "' does not exist or not authorized.";
        if (shows.isEmpty()) {
            throw RestException.notFound(missing);
        }
        final List<RestRow> describe = RoutineSupport.describe(call, "FUNCTION", signature.name(),
            signature.hasArguments() ? signature.types() : RoutineSupport.showArgumentTypes(shows.get(0)));
        final RestRow show = RoutineSupport.pick(shows, RoutineSupport.signatureTypes(describe));
        return RoutineSupport.property(describe, "service") != null ? toJson(show, describe)
            : RoutineSupport.toJson(show, describe, false);
    }

    private static ObjectNode toJson(final RestRow show, final List<RestRow> describe) {
        final ObjectNode node = RestJson.object();
        node.put("function_type", "service-function");
        RestJson.name(node, "name", show, "name");
        final ObjectNode routine = RoutineSupport.toJson(show, describe, false);
        node.set("arguments", routine.get("arguments"));
        RestJson.put(node, "returns", RoutineSupport.property(describe, "returns"));
        final String batch = RoutineSupport.property(describe, "max_batch_rows");
        if (batch != null) {
            node.put("max_batch_rows", Long.parseLong(batch));
        }
        RestJson.timestamp(node, "created_on", show, "created_on");
        RestJson.put(node, "signature", RoutineSupport.property(describe, "signature"));
        RestJson.put(node, "language", RoutineSupport.property(describe, "language"));
        final String path = RoutineSupport.property(describe, "body");
        RestJson.put(node, "body", path);
        final String service = RoutineSupport.property(describe, "service");
        final List<String> parts = RoutineSignature.splitList(service.replace('.', ','));
        node.put("service", RestIdentifier.display(parts.get(parts.size() - 1)));
        if (parts.size() >= 3) {
            node.put("service_database", RestIdentifier.display(parts.get(parts.size() - 3)));
        }
        if (parts.size() >= 2) {
            node.put("service_schema", RestIdentifier.display(parts.get(parts.size() - 2)));
        }
        RestJson.put(node, "endpoint", RoutineSupport.property(describe, "endpoint"));
        RestJson.put(node, "path", path);
        RestJson.nulls(node, "max_batch_rows", "service_database", "service_schema");
        return node;
    }

    private static String createSql(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace()).append(" FUNCTION")
            .append(mode.ifNotExists()).append(' ').append(call.qualifiedSql(name)).append('(');
        final JsonNode arguments = body.get("arguments");
        if (arguments == null || !arguments.isArray()) {
            throw RestException.badRequest("Missing required property 'arguments'.");
        }
        int count = 0;
        for (final JsonNode argument : arguments.values()) {
            if (count++ > 0) {
                sql.append(", ");
            }
            final String datatype = RestJson.text(argument, "datatype");
            sql.append(RestJson.identifier(argument, "name", true).sql()).append(' ')
                .append(RoutineSignature.checkType(datatype == null ? "TEXT" : datatype, "arguments"));
        }
        final String returns = RestJson.text(body, "returns");
        sql.append(") RETURNS ").append(RoutineSignature.checkType(returns == null ? "TEXT" : returns, "returns"));
        final RestIdentifier service = RestJson.identifier(body, "service", true);
        final RestIdentifier database = RestJson.identifier(body, "service_database", false);
        final RestIdentifier schema = RestJson.identifier(body, "service_schema", false);
        sql.append(" SERVICE = ").append(database != null ? database.sql() : call.identifier("database").sql())
            .append('.').append(schema != null ? schema.sql() : call.identifier("schema").sql()).append('.')
            .append(service.sql());
        final String endpoint = RestJson.text(body, "endpoint");
        if (endpoint == null) {
            throw RestException.badRequest("Missing required property 'endpoint'.");
        }
        sql.append(" ENDPOINT = ").append(RestIdentifier.parse(endpoint, "endpoint").sql());
        final Long batch = RestJson.integer(body, "max_batch_rows");
        if (batch != null) {
            sql.append(" MAX_BATCH_ROWS = ").append(batch);
        }
        final String path = RestJson.text(body, "path");
        if (path == null) {
            throw RestException.badRequest("Missing required property 'path'.");
        }
        return sql.append(" AS ").append(RestSql.literal(path)).toString();
    }
}
