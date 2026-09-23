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
 * Sequences ({@code sequence.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/sequences}): list,
 * create, fetch, delete, {@code :clone} and {@code :rename}.
 *
 * <p>A sequence is read from {@code SHOW SEQUENCES}: its {@code start} is the value it hands out next and its
 * {@code increment} the listing's {@code interval}.
 */
public final class SequenceResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/sequences";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listSequences", this);
        router.add("POST", COLLECTION, "createSequence", this);
        router.add("GET", ITEM, "fetchSequence", this);
        router.add("DELETE", ITEM, "deleteSequence", this);
        router.add("POST", ITEM + ":clone", "cloneSequence", this);
        router.add("POST", ITEM + ":rename", "renameSequence", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listSequences":
                return list(call);
            case "createSequence":
                return create(call);
            case "fetchSequence":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteSequence":
                return call.sql().action("DROP SEQUENCE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "cloneSequence":
                return cloneSequence(call);
            case "renameSequence":
                return call.sql().action("ALTER SEQUENCE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")) + " RENAME TO "
                    + RelationNames.placed(call, RestIdentifier.parse(call.query("targetName"), "targetName")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW SEQUENCES" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql())) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The sequence as the {@code Sequence} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW SEQUENCES" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Sequence '" + RelationNames.qualifiedDisplay(call, name)
                + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.integer(node, "start", row, "next_value");
        RestJson.integer(node, "increment", row, "interval");
        RestJson.bool(node, "ordered", row, "ordered");
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.nulls(node, "start", "increment", "ordered", "comment", "created_on", "database_name", "schema_name",
            "owner", "owner_role_type");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " SEQUENCE" + mode.ifNotExists()
            + " " + call.qualifiedSql(name));
        sql.integer(body, "start", "START");
        sql.integer(body, "increment", "INCREMENT");
        final Boolean ordered = RestJson.bool(body, "ordered");
        if (ordered != null) {
            sql.append(ordered.booleanValue() ? " ORDER" : " NOORDER");
        }
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }

    /** {@code :clone}: CREATE SEQUENCE ... CLONE the path's sequence, placed by targetDatabase/targetSchema. */
    private static RestResponse cloneSequence(final RestCall call) {
        final RestIdentifier target = RestJson.identifier(call.body(), "name", true);
        final RestCreateMode mode = call.createMode();
        return call.sql().action("CREATE" + mode.orReplace() + " SEQUENCE" + mode.ifNotExists() + " "
            + RelationNames.placed(call, target) + " CLONE " + call.qualifiedSql(call.identifier("name")));
    }
}
