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
import dev.frostlake.http.rest.RestResource;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRouter;
import dev.frostlake.http.rest.RestRow;
import dev.frostlake.http.rest.RestShow;
import dev.frostlake.http.rest.RestTags;
import dev.frostlake.storage.ResultSet;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

/**
 * The user-defined function endpoints ({@code user-defined-function.yaml}): CREATE FUNCTION from the body, SHOW
 * USER FUNCTIONS with DESCRIBE FUNCTION for the listing and the fetch, DROP FUNCTION, {@code :execute} as a SELECT
 * of the function, {@code :rename} as ALTER FUNCTION … RENAME TO, and the tag endpoints. A function is addressed by
 * its name with its argument types, {@code {nameWithArgs}}.
 */
public final class UserDefinedFunctionResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/user-defined-functions";
    private static final String ITEM = COLLECTION + "/{nameWithArgs}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listUserDefinedFunctions", this);
        router.add("POST", COLLECTION, "createUserDefinedFunction", this);
        router.add("GET", ITEM, "fetchUserDefinedFunction", this);
        router.add("DELETE", ITEM, "deleteUserDefinedFunction", this);
        router.add("POST", COLLECTION + "/{name}:execute", "executeUserDefinedFunction", this);
        router.add("POST", ITEM + ":rename", "renameUserDefinedFunction", this);
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
            case "listUserDefinedFunctions":
                return list(call);
            case "createUserDefinedFunction":
                return call.sql().action(RoutineSupport.createSql(call, false));
            case "fetchUserDefinedFunction":
                return RestResponse.json(200, fetch(call, signature(call)));
            case "deleteUserDefinedFunction":
                return delete(call);
            case "executeUserDefinedFunction":
                return execute(call);
            case "renameUserDefinedFunction":
                return rename(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    /** A schema object's name qualified by the path's database and schema, as a refusal spells it. */
    static String qualified(final RestCall call, final RestIdentifier name) {
        return call.identifier("database").name() + "." + call.identifier("schema").name() + "." + name.name();
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
            final RestIdentifier name = RestIdentifier.ofResolved(row.string("name"));
            final List<RestRow> describe = RoutineSupport.describe(call, "FUNCTION", name,
                RoutineSupport.showArgumentTypes(row));
            out.add(RoutineSupport.toJson(row, describe, false, true));
        }
        return RestResponse.json(200, out);
    }

    /** The overload as the {@code UserDefinedFunction} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RoutineSignature signature) {
        final List<RestRow> shows = call.sql().showNamed(show(call, RestShow.likeName(signature.name())),
            signature.name());
        if (shows.isEmpty()) {
            throw RestException.notFound("Function '"
                + qualified(call, signature.name()) + "' does not exist or not authorized.");
        }
        final List<String> types = signature.hasArguments() ? signature.types()
            : RoutineSupport.showArgumentTypes(shows.get(0));
        final List<RestRow> describe = RoutineSupport.describe(call, "FUNCTION", signature.name(), types);
        return RoutineSupport.toJson(RoutineSupport.pick(shows, RoutineSupport.signatureTypes(describe)), describe,
            false);
    }

    private static RestResponse delete(final RestCall call) {
        final RoutineSignature signature = signature(call);
        return call.sql().action("DROP FUNCTION" + call.ifExists() + " " + call.qualifiedSql(signature.name())
            + signature.typesSql());
    }

    /** Runs the function once on the body's arguments and answers its value, keyed by the function's name. */
    private static RestResponse execute(final RestCall call) {
        final RestIdentifier name = RoutineSignature.parse(call.pathParameter("name"), "name").name();
        final JsonNode arguments = call.json();
        final ResultSet result = RoutineSupport.call(call, "SELECT " + call.qualifiedSql(name)
            + RoutineSupport.arguments(arguments) + " AS " + name.sql());
        final ArrayNode rows = RoutineSupport.rows(result);
        final ObjectNode answer = RestJson.object();
        if (!rows.isEmpty()) {
            answer.set(name.name(), rows.get(0).get(name.name().toLowerCase(Locale.ROOT)));
        }
        return RestResponse.json(200, answer);
    }

    private static RestResponse rename(final RestCall call) {
        final RoutineSignature signature = signature(call);
        final String targetName = call.query("targetName");
        if (targetName == null || targetName.isEmpty()) {
            throw RestException.badRequest("Missing required query parameter 'targetName'.");
        }
        final String database = call.query("targetDatabase");
        final String schema = call.query("targetSchema");
        final String target = (database == null ? call.identifier("database") : RestIdentifier.parse(database,
            "targetDatabase")).sql() + "." + (schema == null ? call.identifier("schema")
            : RestIdentifier.parse(schema, "targetSchema")).sql() + "."
            + RestIdentifier.parse(targetName, "targetName").sql();
        return call.sql().action("ALTER FUNCTION" + call.ifExists() + " " + call.qualifiedSql(signature.name())
            + signature.typesSql() + " RENAME TO " + target);
    }
}
