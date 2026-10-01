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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * The procedure endpoints ({@code procedure.yaml}): CREATE PROCEDURE from the body, SHOW PROCEDURES with DESCRIBE
 * PROCEDURE for the listing and the fetch, DROP PROCEDURE, {@code :call} as a CALL answering the result's rows, and
 * the tag endpoints. A procedure is addressed by its name with its argument types, {@code {nameWithArgs}}; the call
 * takes the bare name, the overload being the one the arguments pick.
 */
public final class ProcedureResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/procedures";
    private static final String ITEM = COLLECTION + "/{nameWithArgs}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listProcedures", this);
        router.add("POST", COLLECTION, "createProcedure", this);
        router.add("GET", ITEM, "fetchProcedure", this);
        router.add("DELETE", ITEM, "deleteProcedure", this);
        router.add("POST", ITEM + ":call", "callProcedure", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RoutineSignature signature = signature(call);
            return RestTags.handle(call, "ALTER PROCEDURE",
                call.qualifiedSql(signature.name()) + signature.typesSql(), "PROCEDURE",
                call.qualifiedSql(signature.name()) + signature.typesSql(), call.identifier("database").sql());
        }
        switch (call.operation()) {
            case "listProcedures":
                return list(call);
            case "createProcedure":
                return call.sql().action(RoutineSupport.createSql(call, true));
            case "fetchProcedure":
                return RestResponse.json(200, fetch(call, signature(call)));
            case "deleteProcedure":
                return delete(call);
            case "callProcedure":
                return callProcedure(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RoutineSignature signature(final RestCall call) {
        return RoutineSignature.parse(call.pathParameter("nameWithArgs"), "nameWithArgs");
    }

    private static String show(final RestCall call, final String like) {
        return "SHOW PROCEDURES" + like + " IN SCHEMA " + call.schemaSql();
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show(show(call, RestShow.like(call)))) {
            final RestIdentifier name = RestIdentifier.ofResolved(row.string("name"));
            final List<RestRow> describe = RoutineSupport.describe(call, "PROCEDURE", name,
                RoutineSupport.showArgumentTypes(row));
            out.add(RoutineSupport.toJson(row, describe, true, true));
        }
        return RestResponse.json(200, out);
    }

    /** The overload as the {@code Procedure} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RoutineSignature signature) {
        final List<RestRow> shows = call.sql().showNamed(show(call, RestShow.likeName(signature.name())),
            signature.name());
        if (shows.isEmpty()) {
            throw RestException.notFound("Procedure '"
                + UserDefinedFunctionResource.qualified(call, signature.name())
                + "' does not exist or not authorized.");
        }
        final List<String> types = signature.hasArguments() ? signature.types()
            : RoutineSupport.showArgumentTypes(shows.get(0));
        final List<RestRow> describe = RoutineSupport.describe(call, "PROCEDURE", signature.name(), types);
        return RoutineSupport.toJson(RoutineSupport.pick(shows, RoutineSupport.signatureTypes(describe)), describe,
            true);
    }

    private static RestResponse delete(final RestCall call) {
        final RoutineSignature signature = signature(call);
        return call.sql().action("DROP PROCEDURE" + call.ifExists() + " " + call.qualifiedSql(signature.name())
            + signature.typesSql());
    }

    /** Calls the procedure with the body's {@code call_arguments} and answers the rows it returns. */
    private static RestResponse callProcedure(final RestCall call) {
        final RoutineSignature signature = signature(call);
        final JsonNode body = call.bodyOrEmpty();
        // The path goes into the statement as written: a name carrying its argument types meets CALL's syntax
        // refusal, as on the account, where the call takes the procedure's bare name.
        return RestResponse.json(200, RoutineSupport.rows(RoutineSupport.call(call, "CALL "
            + call.qualifiedSql(signature.name()) + (signature.hasArguments() ? signature.typesSql() : "")
            + RoutineSupport.arguments(body.get("call_arguments")))));
    }
}
