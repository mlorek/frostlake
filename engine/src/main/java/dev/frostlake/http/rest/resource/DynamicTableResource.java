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
import dev.frostlake.parser.FrostlakeLexer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dynamic tables ({@code dynamic-table.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/dynamic-tables}):
 * list, create, fetch, delete, {@code :clone}, {@code :undrop}, {@code :suspend}, {@code :resume},
 * {@code :refresh}, {@code :suspend-recluster}, {@code :resume-recluster}, {@code :swap-with} and the tag
 * endpoints.
 *
 * <p>A dynamic table is read from {@code SHOW DYNAMIC TABLES} — its {@code query} is the defining query of the
 * CREATE text the listing reports, and its {@code target_lag} the listing's lag in seconds or DOWNSTREAM — and
 * its {@code columns} from {@code DESCRIBE DYNAMIC TABLE}.
 */
public final class DynamicTableResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/dynamic-tables";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final String DOWNSTREAM = "DOWNSTREAM";

    /** Body properties the engine's CREATE DYNAMIC TABLE does not take. */
    private static final String[] UNSUPPORTED = {
        "initialization_warehouse", "frozen_where", "backfill_from", "default_ddl_collation", "log_level",
        "row_timestamp",
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listDynamicTables", this);
        router.add("POST", COLLECTION, "createDynamicTable", this);
        router.add("GET", ITEM, "fetchDynamicTable", this);
        router.add("DELETE", ITEM, "deleteDynamicTable", this);
        router.add("POST", ITEM + ":clone", "cloneDynamicTable", this);
        router.add("POST", ITEM + ":undrop", "undropDynamicTable", this);
        router.add("POST", ITEM + ":suspend", "suspendDynamicTable", this);
        router.add("POST", ITEM + ":resume", "resumeDynamicTable", this);
        router.add("POST", ITEM + ":refresh", "refreshDynamicTable", this);
        router.add("POST", ITEM + ":suspend-recluster", "suspendReclusterDynamicTable", this);
        router.add("POST", ITEM + ":resume-recluster", "resumeReclusterDynamicTable", this);
        router.add("POST", ITEM + ":swap-with", "swapWithDynamicTable", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER DYNAMIC TABLE", call.qualifiedSql(name), "TABLE",
                RelationNames.qualifiedDisplay(call, name), call.databaseSql());
        }
        switch (call.operation()) {
            case "listDynamicTables":
                return list(call);
            case "createDynamicTable":
                return create(call);
            case "fetchDynamicTable":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteDynamicTable":
                return call.sql().action("DROP DYNAMIC TABLE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "cloneDynamicTable":
                return cloneTable(call);
            case "undropDynamicTable":
                return call.sql().action("UNDROP DYNAMIC TABLE " + call.qualifiedSql(call.identifier("name")));
            case "suspendDynamicTable":
                return alter(call, "SUSPEND");
            case "resumeDynamicTable":
                return alter(call, "RESUME");
            case "refreshDynamicTable":
                return alter(call, "REFRESH");
            case "suspendReclusterDynamicTable":
                return alter(call, "SUSPEND RECLUSTER");
            case "resumeReclusterDynamicTable":
                return alter(call, "RESUME RECLUSTER");
            case "swapWithDynamicTable":
                return alter(call, "SWAP WITH " + RelationNames.placed(call,
                    RestIdentifier.parse(call.query("targetName"), "targetName")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse alter(final RestCall call, final String action) {
        return call.sql().action("ALTER DYNAMIC TABLE" + call.ifExists() + " "
            + call.qualifiedSql(call.identifier("name")) + " " + action);
    }

    // ---- read --------------------------------------------------------------------------------------------

    private static RestResponse list(final RestCall call) {
        final boolean deep = call.flag("deep", false);
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW DYNAMIC TABLES" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            final ObjectNode table = toJson(row);
            if (deep) {
                table.set("columns", columns(call, RestIdentifier.ofResolved(row.string("name"))));
            }
            out.add(table);
        }
        return RestResponse.json(200, out);
    }

    /** The dynamic table as the {@code DynamicTable} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW DYNAMIC TABLES" + RestShow.likeName(name)
            + " IN SCHEMA " + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Dynamic table '"
                + RelationNames.qualifiedDisplay(call, name) + "' does not exist or not authorized.");
        }
        final ObjectNode table = toJson(rows.get(0));
        table.set("columns", columns(call, name));
        return table;
    }

    private static ArrayNode columns(final RestCall call, final RestIdentifier name) {
        return TableColumns.columns(call.sql(), "DESCRIBE DYNAMIC TABLE " + call.qualifiedSql(name), false);
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        final String text = row.string("text");
        node.put("kind", RelationNames.declares(text, FrostlakeLexer.TRANSIENT, FrostlakeLexer.DYNAMIC)
            ? "TRANSIENT" : "PERMANENT");
        node.set("target_lag", targetLag(row.nonEmpty("target_lag")));
        RestJson.string(node, "refresh_mode", row, "refresh_mode");
        RestJson.name(node, "warehouse", row, "warehouse");
        RestJson.name(node, "initialization_warehouse", row, "initialization_warehouse");
        TableColumns.clusterBy(node, row);
        final String query = RelationNames.definingQuery(text);
        RestJson.put(node, "query", query != null ? query : text);
        RestJson.string(node, "frozen_where", row, "frozen_where");
        RestJson.string(node, "backfill_from", row, "backfill_from");
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.integer(node, "rows", row, "rows");
        RestJson.integer(node, "bytes", row, "bytes");
        final String state = row.nonEmpty("scheduling_state");
        if (state != null) {
            node.put("scheduling_state", "SUSPENDED".equalsIgnoreCase(state) ? "SUSPENDED" : "RUNNING");
        }
        RestJson.bool(node, "automatic_clustering", row, "automatic_clustering");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "budget", row, "budget");
        RestJson.string(node, "scheduler", row, "scheduler");
        RestJson.nulls(node, "columns", "refresh_mode", "initialize", "warehouse", "initialization_warehouse",
            "cluster_by", "cluster_by_raw", "frozen_where", "backfill_from", "start_at", "data_retention_time_in_days",
            "max_data_extension_time_in_days", "comment", "default_ddl_collation", "data_metric_schedule", "log_level",
            "row_timestamp", "created_on", "database_name", "schema_name", "rows", "bytes", "scheduling_state",
            "automatic_clustering", "owner", "owner_role_type", "budget", "scheduler");
        return node;
    }

    /**
     * The {@code TargetLag} of a SHOW DYNAMIC TABLES {@code target_lag} cell: DOWNSTREAM, or the lag in seconds
     * of its {@code <n> <unit>} parts.
     */
    private static ObjectNode targetLag(final String lag) {
        final ObjectNode node = RestJson.object();
        if (lag == null || DOWNSTREAM.equalsIgnoreCase(lag.trim())) {
            node.put("type", DOWNSTREAM);
            return node;
        }
        final String[] words = lag.trim().split("\\s+");
        long seconds = 0L;
        for (int i = 0; i + 1 < words.length; i += 2) {
            seconds += Long.parseLong(words[i]) * unitSeconds(words[i + 1].toLowerCase(Locale.ROOT));
        }
        node.put("type", "USER_DEFINED");
        node.put("seconds", seconds);
        return node;
    }

    private static long unitSeconds(final String unit) {
        if (unit.startsWith("day")) {
            return 86400L;
        }
        if (unit.startsWith("hour")) {
            return 3600L;
        }
        if (unit.startsWith("minute")) {
            return 60L;
        }
        return 1L;
    }

    // ---- write -------------------------------------------------------------------------------------------

    /** The SQL of a body's {@code target_lag}: {@code '<n> seconds'} or {@code DOWNSTREAM}. */
    private static String targetLagSql(final JsonNode lag) {
        final String type = RestJson.text(lag, "type");
        if (type == null || "USER_DEFINED".equalsIgnoreCase(type)) {
            final Long seconds = RestJson.integer(lag, "seconds");
            if (seconds == null) {
                throw RestException.badRequest("Missing required property 'seconds' of target_lag.");
            }
            return "'" + seconds + " seconds'";
        }
        if (DOWNSTREAM.equalsIgnoreCase(type)) {
            return DOWNSTREAM;
        }
        throw RestException.badRequest("Invalid target_lag type '" + type + "': expected USER_DEFINED or DOWNSTREAM.");
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final String query = RestJson.text(body, "query");
        if (query == null || query.isBlank()) {
            throw RestException.badRequest("Missing required property 'query'.");
        }
        final JsonNode lag = body.get("target_lag");
        if (lag == null || !lag.isObject()) {
            throw RestException.badRequest("Missing required property 'target_lag'.");
        }
        final RestIdentifier warehouse = RestJson.identifier(body, "warehouse", true);
        for (final String property : UNSUPPORTED) {
            if (RestJson.present(body, property)) {
                throw RestException.notImplemented("Property '" + property + "' of a dynamic table is not provided.");
            }
        }
        final String kind = RestJson.text(body, "kind");
        if (kind != null && !"PERMANENT".equalsIgnoreCase(kind) && !"TRANSIENT".equalsIgnoreCase(kind)) {
            throw RestException.badRequest("Invalid kind '" + kind + "': expected PERMANENT or TRANSIENT.");
        }
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace()
            + ("TRANSIENT".equalsIgnoreCase(kind) ? " TRANSIENT" : "") + " DYNAMIC TABLE" + mode.ifNotExists() + " "
            + call.qualifiedSql(name));
        final String columns = columnNames(body);
        if (columns != null) {
            sql.append(" ").append(columns);
        }
        sql.property("TARGET_LAG", targetLagSql(lag));
        sql.property("WAREHOUSE", warehouse.sql());
        final String refreshMode = RestJson.text(body, "refresh_mode");
        if (refreshMode != null) {
            sql.property("REFRESH_MODE", refreshMode.trim().toUpperCase(Locale.ROOT));
        }
        final String initialize = RestJson.text(body, "initialize");
        if (initialize != null) {
            sql.property("INITIALIZE", initialize.trim().toUpperCase(Locale.ROOT));
        }
        sql.append(TableColumns.clusterBy(body));
        sql.integer(body, "data_retention_time_in_days", "DATA_RETENTION_TIME_IN_DAYS");
        sql.string(body, "comment", "COMMENT");
        sql.append(" AS ").append(query);
        final String status = call.sql().status(sql.toString());
        final Long extension = RestJson.integer(body, "max_data_extension_time_in_days");
        if (extension != null) {
            call.sql().status("ALTER DYNAMIC TABLE " + call.qualifiedSql(name) + " SET MAX_DATA_EXTENSION_TIME_IN_DAYS = "
                + extension);
        }
        return RestResponse.success(status);
    }

    /** The column-name list of a body's {@code columns}, or null; a column type or comment is not provided. */
    private static String columnNames(final JsonNode body) {
        final JsonNode columns = body.get("columns");
        if (columns == null || !columns.isArray() || columns.isEmpty()) {
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final JsonNode column : columns.values()) {
            if (RestJson.present(column, "datatype") || RestJson.present(column, "comment")) {
                throw RestException.notImplemented("A data type or a comment in a dynamic table's column list is"
                    + " not provided; name the columns only.");
            }
            names.add(RestJson.identifier(column, "name", true).sql());
        }
        return "(" + String.join(", ", names) + ")";
    }

    /** {@code :clone}: CREATE DYNAMIC TABLE ... CLONE the path's table, placed by targetDatabase/targetSchema. */
    private static RestResponse cloneTable(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier target = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " DYNAMIC TABLE" + mode.ifNotExists()
            + " " + RelationNames.placed(call, target) + " CLONE " + call.qualifiedSql(call.identifier("name"))
            + TableColumns.pointOfTime(body) + (call.flag("copyGrants", false) ? " COPY GRANTS" : ""));
        final JsonNode lag = body.get("target_lag");
        if (lag != null && lag.isObject()) {
            sql.property("TARGET_LAG", targetLagSql(lag));
        }
        sql.identifier(body, "warehouse", "WAREHOUSE");
        return call.sql().action(sql.toString());
    }
}
