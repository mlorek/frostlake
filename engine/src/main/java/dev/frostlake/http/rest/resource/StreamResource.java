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
import dev.frostlake.metastore.QualifiedName;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

/**
 * Streams ({@code stream.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/streams}): list, create,
 * fetch, delete, {@code :clone} ({@code CREATE STREAM … CLONE}) and the tag endpoints.
 *
 * <p>A stream is read from {@code SHOW STREAMS}; its {@code stream_source} is rebuilt from the listing's
 * {@code source_type}, {@code table_name}, {@code mode} and {@code base_tables}. A create writes
 * {@code CREATE STREAM … ON TABLE | VIEW | EXTERNAL TABLE | STAGE} from the {@code StreamSource}, its
 * {@code point_of_time} as the {@code AT | BEFORE} clause. The engine keeps no external tables, so a stream on
 * one is refused as the statement refuses it.
 */
public final class StreamResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/streams";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listStreams", this);
        router.add("POST", COLLECTION, "createStream", this);
        router.add("GET", ITEM, "fetchStream", this);
        router.add("DELETE", ITEM, "deleteStream", this);
        router.add("POST", ITEM + ":clone", "cloneStream", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER STREAM", call.qualifiedSql(name), "STREAM",
                SchemaObjectNames.dotted(call, name), SchemaObjectNames.informationSchemaDatabase(call));
        }
        switch (call.operation()) {
            case "listStreams":
                return list(call);
            case "createStream":
                return create(call);
            case "fetchStream":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteStream":
                return call.sql().action("DROP STREAM" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "cloneStream":
                return cloneStream(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW STREAMS" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The stream as the {@code Stream} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW STREAMS" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Stream '" + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "name", row, "name");
        node.set("stream_source", source(row));
        RestJson.put(node, "comment", row.string("comment"));
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        // The account answers the stream's source in stream_source alone: its top-level table_name is null.
        node.putNull("table_name");
        RestJson.bool(node, "stale", row, "stale");
        RestJson.string(node, "mode", row, "mode");
        RestJson.timestamp(node, "stale_after", row, "stale_after");
        RestJson.string(node, "invalid_reason", row, "invalid_reason");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "type", row, "type");
        return node;
    }

    /** The {@code StreamSource}: its kind from {@code source_type}, its name from {@code table_name}. */
    private static ObjectNode source(final RestRow row) {
        final ObjectNode source = RestJson.object();
        source.putNull("point_of_time");
        final String sourceType = row.nonEmpty("source_type");
        final String kind = sourceType == null ? "table"
            : sourceType.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        source.put("src_type", kind);
        final String table = row.nonEmpty("table_name");
        if (table != null) {
            final String[] parts = QualifiedName.parse(table).parts();
            source.put("name", RestIdentifier.display(parts[parts.length - 1]));
            if (parts.length == 3) {
                source.put("database_name", RestIdentifier.display(parts[0]));
                source.put("schema_name", RestIdentifier.display(parts[1]));
            }
        }
        if ("table".equals(kind) || "view".equals(kind)) {
            final String mode = row.nonEmpty("mode");
            if (mode != null) {
                source.put("append_only", "APPEND_ONLY".equalsIgnoreCase(mode));
            }
        }
        final String baseTables = row.nonEmpty("base_tables");
        if ("view".equals(kind) && baseTables != null) {
            final ArrayNode tables = RestJson.array();
            for (final String table2 : baseTables.split(",")) {
                if (!table2.isBlank()) {
                    tables.add(table2.trim());
                }
            }
            source.set("base_tables", tables);
        }
        return source;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final JsonNode source = body.get("stream_source");
        if (source == null || !source.isObject()) {
            throw RestException.badRequest("Missing required property 'stream_source'.");
        }
        final String kind = RestJson.text(source, "src_type");
        if (kind == null) {
            throw RestException.badRequest("Missing required property 'src_type' of 'stream_source'.");
        }
        final String objectKind;
        switch (kind.toLowerCase(Locale.ROOT)) {
            case "table":
                objectKind = "TABLE";
                break;
            case "view":
                objectKind = "VIEW";
                break;
            case "external_table":
                objectKind = "EXTERNAL TABLE";
                break;
            case "stage":
                objectKind = "STAGE";
                break;
            default:
                throw RestException.badRequest("Invalid value '" + kind + "' for property 'src_type'.");
        }
        final RestIdentifier sourceName = RestJson.identifier(source, "name", true);
        final RestIdentifier sourceDatabase = RestJson.identifier(source, "database_name", false);
        final RestIdentifier sourceSchema = RestJson.identifier(source, "schema_name", false);
        final String sourceSql = (sourceDatabase != null ? sourceDatabase.sql() : call.databaseSql()) + "."
            + (sourceSchema != null ? sourceSchema.sql() : call.identifier("schema").sql()) + "." + sourceName.sql();
        final RestCreateMode mode = call.createMode();
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace()).append(" STREAM")
            .append(mode.ifNotExists()).append(' ').append(call.qualifiedSql(name));
        if (call.flag("copyGrants", false)) {
            sql.append(" COPY GRANTS");
        }
        sql.append(" ON ").append(objectKind).append(' ').append(sourceSql);
        final JsonNode point = source.get("point_of_time");
        if (point != null && !point.isNull()) {
            sql.append(pointOfTime(call, point));
        }
        final Boolean insertOnly = RestJson.bool(source, "insert_only");
        if (insertOnly != null) {
            sql.append(" INSERT_ONLY = ").append(insertOnly.booleanValue() ? "TRUE" : "FALSE");
        }
        final Boolean appendOnly = RestJson.bool(source, "append_only");
        if (appendOnly != null) {
            sql.append(" APPEND_ONLY = ").append(appendOnly.booleanValue() ? "TRUE" : "FALSE");
        }
        final Boolean initialRows = RestJson.bool(source, "show_initial_rows");
        if (initialRows != null) {
            sql.append(" SHOW_INITIAL_ROWS = ").append(initialRows.booleanValue() ? "TRUE" : "FALSE");
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        return call.sql().action(sql.toString());
    }

    /**
     * A {@code PointOfTime} as the {@code AT | BEFORE} clause: a timestamp or an offset is an expression and is
     * sent as written, a statement's query id and a stream's name are quoted — a stream named without its
     * schema is one of the path's schema.
     */
    private static String pointOfTime(final RestCall call, final JsonNode point) {
        if (!point.isObject()) {
            throw RestException.badRequest("Property 'point_of_time' must be an object.");
        }
        final String reference = RestJson.text(point, "reference");
        if (reference == null || !("at".equalsIgnoreCase(reference) || "before".equalsIgnoreCase(reference))) {
            throw RestException.badRequest("Invalid value '" + reference + "' for property 'reference'.");
        }
        final String type = RestJson.text(point, "point_of_time_type");
        if (type == null) {
            throw RestException.badRequest("Missing required property 'point_of_time_type' of 'point_of_time'.");
        }
        final String key = type.toLowerCase(Locale.ROOT);
        final String value = RestJson.text(point, key);
        if (value == null) {
            throw RestException.badRequest("Missing required property '" + key + "' of 'point_of_time'.");
        }
        final String argument;
        switch (key) {
            case "timestamp":
            case "offset":
                argument = value;
                break;
            case "statement":
                argument = RestSql.literal(value);
                break;
            case "stream":
                argument = RestSql.literal(value.indexOf('.') >= 0 ? value
                    : SchemaObjectNames.dotted(call, RestIdentifier.parse(value, "stream")));
                break;
            default:
                throw RestException.badRequest("Invalid value '" + type + "' for property 'point_of_time_type'.");
        }
        return " " + reference.toUpperCase(Locale.ROOT) + " (" + key.toUpperCase(Locale.ROOT) + " => " + argument
            + ")";
    }

    /**
     * {@code CREATE STREAM <target> CLONE <source>}: the target database and schema default to the source's;
     * a comment in the body is set on the clone afterwards, since the clone takes the source's.
     */
    private static RestResponse cloneStream(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier target = RestJson.identifier(body, "name", true);
        final String database = call.query("targetDatabase") != null
            ? RestIdentifier.parse(call.query("targetDatabase"), "targetDatabase").sql() : call.databaseSql();
        final String schema = call.query("targetSchema") != null
            ? RestIdentifier.parse(call.query("targetSchema"), "targetSchema").sql()
            : call.identifier("schema").sql();
        final String targetSql = database + "." + schema + "." + target.sql();
        final RestCreateMode mode = call.createMode();
        final String status = call.sql().status("CREATE" + mode.orReplace() + " STREAM" + mode.ifNotExists() + " "
            + targetSql + " CLONE " + call.qualifiedSql(call.identifier("name"))
            + (call.flag("copyGrants", false) ? " COPY GRANTS" : ""));
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            call.sql().run("ALTER STREAM " + targetSql + " SET COMMENT = " + RestSql.literal(comment));
        }
        return RestResponse.success(status);
    }
}
