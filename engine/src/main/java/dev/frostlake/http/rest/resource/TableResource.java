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
 * Tables ({@code table.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/tables}): list, create,
 * {@code :as-select}, fetch, create-or-alter, delete, {@code :clone}, {@code :create-like}, {@code :undrop},
 * {@code :suspend-recluster}, {@code :resume-recluster}, {@code :swap-with}, the deprecated underscore spellings
 * of those actions, and the tag endpoints.
 *
 * <p>A table is read from {@code SHOW TABLES}, its {@code columns} from {@code DESCRIBE TABLE} and its
 * {@code constraints} from {@code SHOW PRIMARY | UNIQUE | IMPORTED KEYS}. {@code PUT} is the engine's
 * {@code CREATE OR ALTER TABLE} over the body.
 */
public final class TableResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/tables";
    private static final String ITEM = COLLECTION + "/{name}";

    /** The SHOW TABLES flags that name a table's type, and the {@code table_type} each one means. */
    private static final String[][] TABLE_TYPES = {
        {"is_dynamic", "DYNAMIC"},
        {"is_external", "EXTERNAL"},
        {"is_event", "EVENT"},
        {"is_hybrid", "HYBRID"},
        {"is_iceberg", "ICEBERG"},
        {"is_immutable", "IMMUTABLE"},
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listTables", this);
        router.add("POST", COLLECTION, "createTable", this);
        router.add("POST", COLLECTION + ":as-select", "createTableAsSelect", this);
        router.add("POST", ITEM + ":as_select", "createTableAsSelectDeprecated", this);
        router.add("POST", COLLECTION + ":using-template", "createTableUsingTemplate", this);
        router.add("POST", ITEM + ":using_template", "createTableUsingTemplateDeprecated", this);
        router.add("GET", ITEM, "fetchTable", this);
        router.add("PUT", ITEM, "createOrAlterTable", this);
        router.add("DELETE", ITEM, "deleteTable", this);
        router.add("POST", ITEM + ":clone", "cloneTable", this);
        router.add("POST", ITEM + ":create-like", "createTableLike", this);
        router.add("POST", ITEM + ":create_like", "createTableLikeDeprecated", this);
        router.add("POST", ITEM + ":undrop", "undropTable", this);
        router.add("POST", ITEM + ":suspend-recluster", "suspendReclusterTable", this);
        router.add("POST", ITEM + ":suspend_recluster", "suspendReclusterTableDeprecated", this);
        router.add("POST", ITEM + ":resume-recluster", "resumeReclusterTable", this);
        router.add("POST", ITEM + ":resume_recluster", "resumeReclusterTableDeprecated", this);
        router.add("POST", ITEM + ":swap-with", "swapWithTable", this);
        router.add("POST", ITEM + ":swapwith", "swapWithTableDeprecated", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER TABLE", call.qualifiedSql(name), "TABLE",
                RelationNames.qualifiedDisplay(call, name), call.databaseSql());
        }
        switch (call.operation()) {
            case "listTables":
                return list(call);
            case "createTable":
                return create(call, call.body(), RestJson.identifier(call.body(), "name", true), call.createMode(),
                    null);
            case "createTableAsSelect":
                return create(call, call.body(), RestJson.identifier(call.body(), "name", true), call.createMode(),
                    requiredQuery(call));
            case "createTableAsSelectDeprecated":
                return create(call, call.bodyOrEmpty(), call.identifier("name"), call.createMode(),
                    requiredQuery(call));
            case "createTableUsingTemplate":
                return usingTemplate(call, RestJson.identifier(call.body(), "name", true));
            case "createTableUsingTemplateDeprecated":
                return usingTemplate(call, call.identifier("name"));
            case "fetchTable":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterTable":
                return createOrAlter(call);
            case "deleteTable":
                return call.sql().action("DROP TABLE" + call.ifExists() + " " + call.qualifiedSql(call.identifier("name")));
            case "cloneTable":
                return cloneTable(call);
            case "createTableLike":
                return like(call, RestJson.identifier(call.body(), "name", true));
            case "createTableLikeDeprecated":
                return like(call, RestIdentifier.parse(call.query("newTableName"), "newTableName"));
            case "undropTable":
                return call.sql().action("UNDROP TABLE " + call.qualifiedSql(call.identifier("name")));
            case "suspendReclusterTable":
            case "suspendReclusterTableDeprecated":
                return alter(call, "SUSPEND RECLUSTER");
            case "resumeReclusterTable":
            case "resumeReclusterTableDeprecated":
                return alter(call, "RESUME RECLUSTER");
            case "swapWithTable":
                return alter(call, "SWAP WITH " + RelationNames.placed(call,
                    RestIdentifier.parse(call.query("targetName"), "targetName")));
            case "swapWithTableDeprecated":
                return alter(call, "SWAP WITH " + RelationNames.qualified(call, call.query("targetTableName"),
                    "targetTableName"));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse alter(final RestCall call, final String action) {
        return call.sql().action("ALTER TABLE" + call.ifExists() + " " + call.qualifiedSql(call.identifier("name"))
            + " " + action);
    }

    private static String requiredQuery(final RestCall call) {
        final String query = call.query("query");
        if (query == null || query.isBlank()) {
            throw RestException.badRequest("Missing required query parameter 'query'.");
        }
        return query;
    }

    private static String copyGrants(final RestCall call) {
        return call.flag("copyGrants", false) ? " COPY GRANTS" : "";
    }

    // ---- read --------------------------------------------------------------------------------------------

    private static RestResponse list(final RestCall call) {
        final boolean deep = call.flag("deep", false);
        final String history = call.flag("history", false) ? " HISTORY" : "";
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW TABLES" + history + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            final ObjectNode table = toJson(row);
            if (deep) {
                details(call, table, call.qualifiedSql(RestIdentifier.ofResolved(row.string("name"))));
            }
            out.add(table);
        }
        return RestResponse.json(200, out);
    }

    /** The table as the {@code Table} schema describes it, columns and constraints included, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = existing(call, name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Table '" + RelationNames.qualifiedDisplay(call, name)
                + "' does not exist or not authorized.");
        }
        final ObjectNode table = toJson(rows.get(0));
        details(call, table, call.qualifiedSql(name));
        return table;
    }

    private static List<RestRow> existing(final RestCall call, final RestIdentifier name) {
        return call.sql().showNamed("SHOW TABLES" + RestShow.likeName(name) + " IN SCHEMA " + call.schemaSql(), name);
    }

    private static void details(final RestCall call, final ObjectNode table, final String tableSql) {
        table.set("columns", TableColumns.columns(call.sql(), "DESCRIBE TABLE " + tableSql, true));
        table.set("constraints", TableColumns.constraints(call.sql(), tableSql));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        final String kind = row.nonEmpty("kind");
        if (kind != null) {
            node.put("kind", "TABLE".equalsIgnoreCase(kind) ? "PERMANENT" : kind.toUpperCase(Locale.ROOT));
        }
        TableColumns.clusterBy(node, row);
        RestJson.bool(node, "enable_schema_evolution", row, "enable_schema_evolution");
        RestJson.bool(node, "change_tracking", row, "change_tracking");
        RestJson.integer(node, "data_retention_time_in_days", row, "retention_time");
        RestJson.string(node, "comment", row, "comment");
        RestJson.bool(node, "row_timestamp", row, "row_timestamp");
        RestJson.bool(node, "error_logging", row, "error_logging");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.integer(node, "rows", row, "rows");
        RestJson.integer(node, "bytes", row, "bytes");
        RestJson.string(node, "owner", row, "owner");
        RestJson.timestamp(node, "dropped_on", row, "dropped_on");
        RestJson.bool(node, "automatic_clustering", row, "automatic_clustering");
        RestJson.bool(node, "search_optimization", row, "search_optimization");
        RestJson.integer(node, "search_optimization_progress", row, "search_optimization_progress");
        RestJson.integer(node, "search_optimization_bytes", row, "search_optimization_bytes");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "budget", row, "budget");
        String type = "NORMAL";
        for (final String[] flag : TABLE_TYPES) {
            if (Boolean.TRUE.equals(row.bool(flag[0]))) {
                type = flag[1];
            }
        }
        node.put("table_type", type);
        RestJson.nulls(node, "kind", "cluster_by", "cluster_by_raw", "enable_schema_evolution", "change_tracking",
            "data_retention_time_in_days", "max_data_extension_time_in_days", "default_ddl_collation",
            "data_metric_schedule", "columns", "constraints", "comment", "row_timestamp", "error_logging", "created_on",
            "database_name", "schema_name", "rows", "bytes", "owner", "dropped_on", "automatic_clustering",
            "search_optimization", "search_optimization_progress", "search_optimization_bytes",
            "search_optimization_configuration", "owner_role_type", "budget");
        return node;
    }

    // ---- write -------------------------------------------------------------------------------------------

    /** CREATE TABLE from a {@code Table} body, or CTAS when a query is given. */
    private static RestResponse create(final RestCall call, final JsonNode body, final RestIdentifier name,
                                       final RestCreateMode mode, final String query) {
        final String head = "CREATE" + mode.orReplace() + kind(body) + " TABLE" + mode.ifNotExists() + " "
            + call.qualifiedSql(name);
        return call.sql().action(definition(call, head, body, query));
    }

    /** The statement after its head: the column list, the table properties, COPY GRANTS, and a CTAS query. */
    private static String definition(final RestCall call, final String head, final JsonNode body,
                                     final String query) {
        final String columns = TableColumns.definitions(call, body);
        if (columns == null && query == null) {
            throw RestException.badRequest("A table needs its columns: the body names none.");
        }
        final RestStatement sql = new RestStatement(head);
        if (columns != null) {
            sql.append(" ").append(columns);
        }
        sql.append(TableColumns.clusterBy(body));
        properties(sql, body);
        sql.append(copyGrants(call));
        sql.string(body, "comment", "COMMENT");
        if (query != null) {
            sql.append(" AS ").append(query);
        }
        return sql.toString();
    }

    /** The table properties a body sets, in the order CREATE TABLE lists them. */
    private static void properties(final RestStatement sql, final JsonNode body) {
        sql.bool(body, "enable_schema_evolution", "ENABLE_SCHEMA_EVOLUTION");
        sql.integer(body, "data_retention_time_in_days", "DATA_RETENTION_TIME_IN_DAYS");
        sql.integer(body, "max_data_extension_time_in_days", "MAX_DATA_EXTENSION_TIME_IN_DAYS");
        sql.bool(body, "change_tracking", "CHANGE_TRACKING");
        sql.string(body, "default_ddl_collation", "DEFAULT_DDL_COLLATION");
        sql.bool(body, "error_logging", "ERROR_LOGGING");
        sql.bool(body, "row_timestamp", "ROW_TIMESTAMP");
    }

    /** The CREATE's kind keyword for the body's {@code kind}: nothing for a permanent table. */
    private static String kind(final JsonNode body) {
        final String kind = RestJson.text(body, "kind");
        if (kind == null || kind.isEmpty() || "PERMANENT".equalsIgnoreCase(kind)) {
            return "";
        }
        if ("TRANSIENT".equalsIgnoreCase(kind) || "TEMPORARY".equalsIgnoreCase(kind)) {
            return " " + kind.toUpperCase(Locale.ROOT);
        }
        throw RestException.badRequest("Invalid kind '" + kind + "': expected PERMANENT, TRANSIENT or TEMPORARY.");
    }

    /** PUT: the engine's CREATE OR ALTER TABLE, which creates the table or alters it into the body's shape. */
    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        call.requireBodyNames(name);
        final String head = "CREATE OR ALTER" + kind(body) + " TABLE " + call.qualifiedSql(name);
        return call.sql().action(definition(call, head, body, null));
    }

    /** {@code :clone}: CREATE TABLE ... CLONE the path's table, placed by targetDatabase/targetSchema. */
    private static RestResponse cloneTable(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier target = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        // The clone's kind follows its source: a body's kind is not applied.
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " TABLE"
            + mode.ifNotExists() + " " + RelationNames.placed(call, target) + " CLONE "
            + call.qualifiedSql(call.identifier("name")) + TableColumns.pointOfTime(body) + copyGrants(call));
        return call.sql().action(sql.toString());
    }

    /** {@code :using-template}: CREATE TABLE ... USING TEMPLATE over the {@code query} parameter. */
    private static RestResponse usingTemplate(final RestCall call, final RestIdentifier name) {
        final RestCreateMode mode = call.createMode();
        return call.sql().action("CREATE" + mode.orReplace() + " TABLE" + mode.ifNotExists() + " "
            + call.qualifiedSql(name) + copyGrants(call) + " USING TEMPLATE " + requiredQuery(call));
    }

    /** {@code :create-like}: an empty table shaped like the path's table, in the same schema. */
    private static RestResponse like(final RestCall call, final RestIdentifier target) {
        final RestCreateMode mode = call.createMode();
        return call.sql().action("CREATE" + mode.orReplace() + " TABLE" + mode.ifNotExists() + " "
            + call.qualifiedSql(target) + " LIKE " + call.qualifiedSql(call.identifier("name")) + copyGrants(call));
    }
}
