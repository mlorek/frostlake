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

/**
 * Event tables ({@code event-table.yaml}, {@code …/schemas/{schema}/event-tables}): list, create, fetch, delete,
 * {@code :rename} and the tag endpoints.
 *
 * <p>An event table is read from {@code SHOW EVENT TABLES}; a fetch adds its SHOW TABLES row (clustering,
 * retention, change tracking, sizes) and its fixed column set from {@code DESCRIBE TABLE}. It is dropped, renamed
 * and tagged as a table.
 */
public final class EventTableResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/event-tables";
    private static final String ITEM = COLLECTION + "/{name}";
    /** The account default of MAX_DATA_EXTENSION_TIME_IN_DAYS. */
    private static final long DEFAULT_MAX_DATA_EXTENSION_TIME_IN_DAYS = 14L;

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listEventTables", this);
        router.add("POST", COLLECTION, "createEventTable", this);
        router.add("GET", ITEM, "fetchEventTable", this);
        router.add("DELETE", ITEM, "deleteEventTable", this);
        router.add("POST", ITEM + ":rename", "renameEventTable", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER TABLE", call.qualifiedSql(name), "TABLE",
                RestIdentifier.display(call.identifier("database").name()) + "."
                    + RestIdentifier.display(call.identifier("schema").name()) + "."
                    + RestIdentifier.display(name.name()), call.databaseSql());
        }
        switch (call.operation()) {
            case "listEventTables":
                final ArrayNode out = RestJson.array();
                for (final RestRow row : call.sql().show("SHOW EVENT TABLES" + RestShow.like(call) + " IN SCHEMA "
                        + call.schemaSql() + RestShow.tail(call))) {
                    out.add(details(call, row, RestIdentifier.ofResolved(row.string("name"))));
                }
                return RestResponse.json(200, out);
            case "createEventTable":
                return create(call);
            case "fetchEventTable":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteEventTable":
                return call.sql().action("DROP TABLE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "renameEventTable":
                final String target = call.query("targetName");
                if (target == null) {
                    throw RestException.badRequest("Missing required query parameter 'targetName'.");
                }
                return call.sql().action("ALTER TABLE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")) + " RENAME TO "
                    + call.qualifiedSql(RestIdentifier.parse(target, "targetName")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static ObjectNode base(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        return node;
    }

    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW EVENT TABLES" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Event table '" + name + "' does not exist or not authorized.");
        }
        return details(call, rows.get(0), name);
    }

    /** Everything the EventTable schema names: the listing row, the SHOW TABLES row and the columns. */
    private static ObjectNode details(final RestCall call, final RestRow row, final RestIdentifier name) {
        final ObjectNode node = base(row);
        final RestRow table = RestTableDetails.tableRow(call, name);
        RestTableDetails.tableProperties(node, table);
        // The account's defaults: neither is stored per table here.
        node.put("max_data_extension_time_in_days", DEFAULT_MAX_DATA_EXTENSION_TIME_IN_DAYS);
        node.put("default_ddl_collation", "");
        if (table != null) {
            RestJson.integer(node, "rows", table, "rows");
            RestJson.integer(node, "bytes", table, "bytes");
            RestJson.bool(node, "automatic_clustering", table, "automatic_clustering");
            RestJson.bool(node, "search_optimization", table, "search_optimization");
            RestJson.integer(node, "search_optimization_progress", table, "search_optimization_progress");
            RestJson.integer(node, "search_optimization_bytes", table, "search_optimization_bytes");
        }
        node.set("columns", RestTableDetails.columns(call, call.qualifiedSql(name), false));
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " EVENT TABLE"
            + mode.ifNotExists() + " " + call.qualifiedSql(name));
        RestTableDetails.clusterBy(sql, body);
        sql.integer(body, "data_retention_time_in_days", "DATA_RETENTION_TIME_IN_DAYS");
        sql.integer(body, "max_data_extension_time_in_days", "MAX_DATA_EXTENSION_TIME_IN_DAYS");
        sql.bool(body, "change_tracking", "CHANGE_TRACKING");
        sql.string(body, "default_ddl_collation", "DEFAULT_DDL_COLLATION");
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }
}
