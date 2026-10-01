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
import dev.frostlake.http.rest.RestStatement;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Iceberg tables ({@code iceberg-table.yaml}, {@code …/schemas/{schema}/iceberg-tables}): list, create,
 * {@code :as-select}, fetch, delete, {@code :undrop}, {@code :clone}, {@code :create-like}, the recluster actions,
 * {@code :refresh}, {@code :convert-to-managed} and the tag endpoints.
 *
 * <p>Only Snowflake-managed Iceberg tables exist here: their rows are stored as a table's, and their Iceberg
 * metadata is read from {@code SHOW ICEBERG TABLES}, the rest from SHOW TABLES and DESCRIBE TABLE. The four
 * endpoints creating an externally managed table (from AWS Glue, Delta files, Iceberg files or an Iceberg REST
 * catalog) answer {@code 501}: such a table's data and metadata live outside, where Frostlake does not read.
 * {@code :refresh} and {@code :convert-to-managed} act on externally managed tables, so the statements refuse
 * them for a managed one.
 */
public final class IcebergTableResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/iceberg-tables";
    private static final String ITEM = COLLECTION + "/{name}";
    /** The account default of MAX_DATA_EXTENSION_TIME_IN_DAYS. */
    private static final long DEFAULT_MAX_DATA_EXTENSION_TIME_IN_DAYS = 14L;
    private static final Pattern DATA_TYPE = Pattern.compile(
        "[A-Za-z][A-Za-z0-9_]*( [A-Za-z][A-Za-z0-9_]*)*( ?\\( *[0-9]+ *(, *[0-9]+ *)?\\))?");

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listIcebergTables", this);
        router.add("POST", COLLECTION, "createSnowflakeManagedIcebergTable", this);
        router.add("POST", COLLECTION + ":as-select", "createSnowflakeManagedIcebergTableAsSelect", this);
        router.add("POST", COLLECTION + ":from-aws-glue-catalog", "createUnmanagedIcebergTableFromAWSGlueCatalog",
            this);
        router.add("POST", COLLECTION + ":from-delta", "createUnmanagedIcebergTableFromDelta", this);
        router.add("POST", COLLECTION + ":from-iceberg-files", "createUnmanagedIcebergTableFromIcebergFiles", this);
        router.add("POST", COLLECTION + ":from-iceberg-rest", "createUnmanagedIcebergTableFromIcebergRest", this);
        router.add("GET", ITEM, "fetchIcebergTable", this);
        router.add("DELETE", ITEM, "dropIcebergTable", this);
        router.add("POST", ITEM + ":resume-recluster", "resumeReclusterIcebergTable", this);
        router.add("POST", ITEM + ":suspend-recluster", "suspendReclusterIcebergTable", this);
        router.add("POST", ITEM + ":refresh", "refreshIcebergTable", this);
        router.add("POST", ITEM + ":convert-to-managed", "convertToManagedIcebergTable", this);
        router.add("POST", ITEM + ":undrop", "undropIcebergTable", this);
        router.add("POST", ITEM + ":clone", "cloneSnowflakeManagedIcebergTable", this);
        router.add("POST", ITEM + ":create-like", "createSnowflakeManagedIcebergTableLike", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER ICEBERG TABLE", call.qualifiedSql(name), "TABLE",
                RestIdentifier.display(call.identifier("database").name()) + "."
                    + RestIdentifier.display(call.identifier("schema").name()) + "."
                    + RestIdentifier.display(name.name()), call.databaseSql());
        }
        switch (call.operation()) {
            case "listIcebergTables":
                return list(call);
            case "createSnowflakeManagedIcebergTable":
                return create(call);
            case "createSnowflakeManagedIcebergTableAsSelect":
                return createAsSelect(call);
            case "createUnmanagedIcebergTableFromAWSGlueCatalog":
            case "createUnmanagedIcebergTableFromDelta":
            case "createUnmanagedIcebergTableFromIcebergFiles":
            case "createUnmanagedIcebergTableFromIcebergRest":
                throw RestException.notImplemented("Externally managed Iceberg tables are not provided: their data"
                    + " and metadata live in an external catalog or files, which Frostlake does not read. Only"
                    + " Snowflake-managed Iceberg tables can be created.");
            case "fetchIcebergTable":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "dropIcebergTable":
                return call.sql().action("DROP ICEBERG TABLE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")) + dropBehavior(call));
            case "resumeReclusterIcebergTable":
                return alter(call, "RESUME RECLUSTER");
            case "suspendReclusterIcebergTable":
                return alter(call, "SUSPEND RECLUSTER");
            case "refreshIcebergTable":
                final String path = RestJson.text(call.bodyOrEmpty(), "metadata_file_relative_path");
                return alter(call, "REFRESH" + (path == null ? "" : " " + RestSql.literal(path)));
            case "convertToManagedIcebergTable":
                final JsonNode request = call.bodyOrEmpty();
                final RestStatement convert = new RestStatement("CONVERT TO MANAGED");
                convert.string(request, "base_location", "BASE_LOCATION");
                if (RestJson.text(request, "storage_serialization_policy") != null) {
                    convert.property("STORAGE_SERIALIZATION_POLICY", word(request, "storage_serialization_policy"));
                }
                return alter(call, convert.toString());
            case "undropIcebergTable":
                return call.sql().action("UNDROP ICEBERG TABLE " + call.qualifiedSql(call.identifier("name")));
            case "cloneSnowflakeManagedIcebergTable":
                return cloneOrLike(call, true);
            case "createSnowflakeManagedIcebergTableLike":
                return cloneOrLike(call, false);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse alter(final RestCall call, final String action) {
        return call.sql().action("ALTER ICEBERG TABLE" + call.ifExists() + " "
            + call.qualifiedSql(call.identifier("name")) + " " + action);
    }

    private static String dropBehavior(final RestCall call) {
        final String type = call.query("type");
        if (type == null || type.isEmpty()) {
            return "";
        }
        final String upper = type.toUpperCase(Locale.ROOT);
        if (!"CASCADE".equals(upper) && !"RESTRICT".equals(upper)) {
            throw RestException.badRequest("Invalid value '" + type + "' for query parameter 'type': expected CASCADE"
                + " or RESTRICT.");
        }
        return " " + upper;
    }

    private static RestResponse list(final RestCall call) {
        final boolean deep = call.flag("deep", false);
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW ICEBERG TABLES" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            out.add(details(call, row, RestIdentifier.ofResolved(row.string("name")), deep));
        }
        return RestResponse.json(200, out);
    }

    private static ObjectNode base(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.string(node, "comment", row, "comment");
        final String volume = row.nonEmpty("external_volume_name");
        // The API names Snowflake-managed storage differently from the listing.
        RestJson.put(node, "external_volume", "SNOWFLAKE_MANAGED".equals(volume) ? "SNOWFLAKE_DEFAULT_VOLUME" : volume);
        RestJson.string(node, "catalog", row, "catalog_name");
        RestJson.string(node, "catalog_sync", row, "catalog_sync_name");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "iceberg_table_type", row, "iceberg_table_type");
        RestJson.string(node, "catalog_table_name", row, "catalog_table_name");
        RestJson.string(node, "catalog_namespace", row, "catalog_namespace");
        RestJson.string(node, "can_write_metadata", row, "can_write_metadata");
        final String location = row.nonEmpty("base_location");
        // A base location Snowflake generated (database/schema/name.suffix/) is not reported.
        RestJson.put(node, "base_location", location == null || location.matches("[^/]+/[^/]+/[^/]+\\.[A-Za-z0-9]{8}/")
            ? null : location);
        return node;
    }

    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW ICEBERG TABLES" + RestShow.likeName(name)
            + " IN SCHEMA " + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Iceberg table '" + name + "' does not exist or not authorized.");
        }
        return details(call, rows.get(0), name, true);
    }

    /**
     * Everything the IcebergTable schema names: the listing row, the SHOW TABLES row and — for a fetch or a deep
     * listing — the columns; what neither statement reports is null.
     */
    private static ObjectNode details(final RestCall call, final RestRow row, final RestIdentifier name,
                                      final boolean withColumns) {
        final ObjectNode node = base(row);
        RestTableDetails.tableProperties(node, RestTableDetails.tableRow(call, name));
        if (withColumns) {
            node.set("columns", RestTableDetails.columns(call, call.qualifiedSql(name), true));
        } else {
            node.putNull("columns");
        }
        // The account's defaults: neither is stored per table here.
        node.put("max_data_extension_time_in_days", DEFAULT_MAX_DATA_EXTENSION_TIME_IN_DAYS);
        node.put("storage_serialization_policy", "OPTIMIZED");
        node.put("auto_refresh", false);
        RestJson.nulls(node, "replace_invalid_characters", "metadata_file_path", "constraints");
        return node;
    }

    /** The Iceberg options every create writes after the table's shape: volume, catalog, location and the rest. */
    private static void icebergOptions(final RestStatement sql, final JsonNode body) {
        RestTableDetails.clusterBy(sql, body);
        sql.string(body, "external_volume", "EXTERNAL_VOLUME");
        sql.string(body, "catalog", "CATALOG");
        sql.string(body, "base_location", "BASE_LOCATION");
        sql.string(body, "catalog_sync", "CATALOG_SYNC");
        if (RestJson.text(body, "storage_serialization_policy") != null) {
            sql.property("STORAGE_SERIALIZATION_POLICY", word(body, "storage_serialization_policy"));
        }
        sql.integer(body, "data_retention_time_in_days", "DATA_RETENTION_TIME_IN_DAYS");
        sql.integer(body, "max_data_extension_time_in_days", "MAX_DATA_EXTENSION_TIME_IN_DAYS");
        sql.bool(body, "change_tracking", "CHANGE_TRACKING");
        sql.string(body, "comment", "COMMENT");
    }

    private static String head(final RestCall call, final String tableSql) {
        final RestCreateMode mode = call.createMode();
        return "CREATE" + mode.orReplace() + " ICEBERG TABLE" + mode.ifNotExists() + " " + tableSql;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final JsonNode columns = body.get("columns");
        if (columns == null || !columns.isArray() || columns.isEmpty()) {
            throw RestException.badRequest("Missing required property 'columns': a Snowflake-managed Iceberg table"
                + " is created with its columns (or with :as-select, :clone or :create-like).");
        }
        final RestStatement sql = new RestStatement(head(call, call.qualifiedSql(name)) + " ("
            + columnList(columns, true) + constraints(body) + ")");
        icebergOptions(sql, body);
        return call.sql().action(sql.toString());
    }

    private static RestResponse createAsSelect(final RestCall call) {
        final String query = call.query("query");
        if (query == null || query.isBlank()) {
            throw RestException.badRequest("Missing required query parameter 'query'.");
        }
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final JsonNode columns = body.get("columns");
        final boolean typed = columns != null && columns.isArray() && !columns.isEmpty();
        final RestStatement sql = new RestStatement(head(call, call.qualifiedSql(name))
            + (typed ? " (" + columnList(columns, false) + ")" : ""));
        icebergOptions(sql, body);
        return call.sql().action(sql.append(" AS " + query).toString());
    }

    private static RestResponse cloneOrLike(final RestCall call, final boolean clone) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final String database = call.query("targetDatabase") != null
            ? RestIdentifier.parse(call.query("targetDatabase"), "targetDatabase").sql() : call.databaseSql();
        final String schema = call.query("targetSchema") != null
            ? RestIdentifier.parse(call.query("targetSchema"), "targetSchema").sql()
            : call.identifier("schema").sql();
        final String source = call.qualifiedSql(call.identifier("name"));
        final RestStatement sql = new RestStatement(head(call, database + "." + schema + "." + name.sql())
            + (clone ? " CLONE " + source + pointOfTime(body) : " LIKE " + source));
        if (!clone) {
            RestTableDetails.clusterBy(sql, body);
            sql.string(body, "external_volume", "EXTERNAL_VOLUME");
            sql.string(body, "base_location", "BASE_LOCATION");
            sql.string(body, "comment", "COMMENT");
        }
        return call.sql().action(sql.toString());
    }

    /** {@code AT | BEFORE (TIMESTAMP => … | OFFSET => … | STATEMENT => …)} for a body's point_of_time. */
    private static String pointOfTime(final JsonNode body) {
        final JsonNode point = body.get("point_of_time");
        if (point == null || point.isNull()) {
            return "";
        }
        final String reference = RestJson.text(point, "reference");
        final String keyword = reference == null || "at".equalsIgnoreCase(reference) ? "AT" : "BEFORE";
        if (reference != null && !"at".equalsIgnoreCase(reference) && !"before".equalsIgnoreCase(reference)) {
            throw RestException.badRequest("Invalid point_of_time reference '" + reference + "'.");
        }
        final String type = RestJson.text(point, "point_of_time_type");
        if ("timestamp".equalsIgnoreCase(type)) {
            return " " + keyword + "(TIMESTAMP => " + RestSql.literal(required(point, "timestamp"))
                + "::TIMESTAMP_LTZ)";
        }
        if ("offset".equalsIgnoreCase(type)) {
            final String offset = required(point, "offset").trim();
            if (!offset.matches("-?[0-9]+")) {
                throw RestException.badRequest("Invalid point_of_time offset '" + offset + "'.");
            }
            return " " + keyword + "(OFFSET => " + offset + ")";
        }
        if ("statement".equalsIgnoreCase(type)) {
            return " " + keyword + "(STATEMENT => " + RestSql.literal(required(point, "statement")) + ")";
        }
        throw RestException.badRequest("Invalid point_of_time_type '" + type + "'.");
    }

    private static String required(final JsonNode node, final String property) {
        final String value = RestJson.text(node, property);
        if (value == null) {
            throw RestException.badRequest("Missing required property '" + property + "'.");
        }
        return value;
    }

    /** The column list: each column's name, type, nullability, default and comment. */
    private static String columnList(final JsonNode columns, final boolean typesRequired) {
        final StringBuilder out = new StringBuilder();
        int count = 0;
        for (final JsonNode column : columns.values()) {
            if (count++ > 0) {
                out.append(", ");
            }
            out.append(RestJson.identifier(column, "name", true).sql());
            final String type = RestJson.text(column, "datatype");
            if (type == null && typesRequired) {
                throw RestException.badRequest("Missing required property 'datatype'.");
            }
            if (type != null) {
                if (!DATA_TYPE.matcher(type.trim()).matches()) {
                    throw RestException.badRequest("Invalid datatype '" + type + "'.");
                }
                out.append(' ').append(type.trim());
            }
            final String defaultValue = RestJson.text(column, "default_value");
            if (defaultValue != null) {
                out.append(" DEFAULT ").append(defaultValue);
            }
            if (Boolean.FALSE.equals(RestJson.bool(column, "nullable"))) {
                out.append(" NOT NULL");
            }
            final String comment = RestJson.text(column, "comment");
            if (comment != null) {
                out.append(" COMMENT ").append(RestSql.literal(comment));
            }
        }
        return out.toString();
    }

    /** The out-of-line PRIMARY KEY, UNIQUE and FOREIGN KEY constraints a body names. */
    private static String constraints(final JsonNode body) {
        final JsonNode constraints = body.get("constraints");
        if (constraints == null || !constraints.isArray()) {
            return "";
        }
        final StringBuilder out = new StringBuilder();
        for (final JsonNode constraint : constraints.values()) {
            out.append(", ");
            final RestIdentifier name = RestJson.identifier(constraint, "name", false);
            if (name != null) {
                out.append("CONSTRAINT ").append(name.sql()).append(' ');
            }
            final String type = RestJson.text(constraint, "constraint_type");
            final String upper = type == null ? "" : type.toUpperCase(Locale.ROOT);
            if ("PRIMARY KEY".equals(upper) || "UNIQUE".equals(upper)) {
                out.append(upper).append(' ').append(names(constraint, "column_names"));
            } else if ("FOREIGN KEY".equals(upper)) {
                out.append("FOREIGN KEY ").append(names(constraint, "column_names")).append(" REFERENCES ")
                    .append(RestJson.identifier(constraint, "referenced_table_name", true).sql());
                if (RestJson.present(constraint, "referenced_column_names")) {
                    out.append(' ').append(names(constraint, "referenced_column_names"));
                }
            } else {
                throw RestException.badRequest("Invalid constraint_type '" + type + "'.");
            }
        }
        return out.toString();
    }

    private static String names(final JsonNode node, final String property) {
        final List<String> names = RestJson.strings(node, property);
        if (names == null || names.isEmpty()) {
            throw RestException.badRequest("Missing required property '" + property + "'.");
        }
        final StringBuilder out = new StringBuilder("(");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(RestIdentifier.parse(names.get(i), property).sql());
        }
        return out.append(')').toString();
    }

    /** A property holding a fixed word, written bare after checking it is one. */
    private static String word(final JsonNode node, final String property) {
        final String value = RestJson.text(node, property).toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z_]+")) {
            throw RestException.badRequest("Invalid value for property '" + property + "'.");
        }
        return value;
    }
}
