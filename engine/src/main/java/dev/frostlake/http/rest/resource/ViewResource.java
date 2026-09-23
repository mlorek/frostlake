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

import java.util.ArrayList;
import java.util.List;

/**
 * Views ({@code view.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/views}): list, create, fetch,
 * delete and the tag endpoints.
 *
 * <p>A view is read from {@code SHOW VIEWS} — its {@code query} is the defining query of the CREATE text the
 * listing reports — and its {@code columns} from {@code DESCRIBE VIEW}.
 */
public final class ViewResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/views";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listViews", this);
        router.add("POST", COLLECTION, "createView", this);
        router.add("GET", ITEM, "fetchView", this);
        router.add("DELETE", ITEM, "deleteView", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER VIEW", call.qualifiedSql(name), "TABLE",
                RelationNames.qualifiedDisplay(call, name), call.databaseSql());
        }
        switch (call.operation()) {
            case "listViews":
                return list(call);
            case "createView":
                return create(call);
            case "fetchView":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteView":
                return call.sql().action("DROP VIEW" + call.ifExists() + " " + call.qualifiedSql(call.identifier("name")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final boolean deep = call.flag("deep", false);
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW VIEWS" + RestShow.like(call) + " IN SCHEMA " + call.schemaSql()
                + RestShow.tail(call))) {
            if (Boolean.TRUE.equals(row.bool("is_materialized"))) {
                continue;
            }
            out.add(toJson(call, row, deep));
        }
        return RestResponse.json(200, out);
    }

    /** The view as the {@code View} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW VIEWS" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty() || Boolean.TRUE.equals(rows.get(0).bool("is_materialized"))) {
            throw RestException.notFound("View '" + RelationNames.qualifiedDisplay(call, name)
                + "' does not exist or not authorized.");
        }
        return toJson(call, rows.get(0), true);
    }

    /** A SHOW VIEWS row; the columns are described when asked for, and are otherwise an empty list. */
    private static ObjectNode toJson(final RestCall call, final RestRow row, final boolean withColumns) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.bool(node, "secure", row, "is_secure");
        node.put("kind", "TEMPORARY".equalsIgnoreCase(row.nonEmpty("kind")) ? "TEMPORARY" : "PERMANENT");
        node.put("recursive", false);
        node.set("columns", withColumns
            ? TableColumns.columns(call.sql(), "DESCRIBE VIEW " + call.qualifiedSql(
                RestIdentifier.ofResolved(row.string("name"))), false)
            : RestJson.array());
        RestJson.string(node, "comment", row, "comment");
        // query is the CREATE text as listed; select_query its defining query.
        RestJson.put(node, "query", row.string("text"));
        RestJson.put(node, "select_query", RelationNames.definingQuery(row.string("text")));
        RestJson.bool(node, "change_tracking", row, "change_tracking");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.nulls(node, "secure", "kind", "recursive", "comment", "select_query", "change_tracking",
            "data_metric_schedule", "created_on", "database_name", "schema_name", "owner", "owner_role_type");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final String query = RestJson.text(body, "query");
        if (query == null || query.isBlank()) {
            throw RestException.badRequest("Missing required property 'query'.");
        }
        final Boolean recursive = RestJson.bool(body, "recursive");
        if (recursive != null && recursive.booleanValue()) {
            throw RestException.notImplemented("RECURSIVE views are not provided.");
        }
        final RestCreateMode mode = call.createMode();
        final Boolean secure = RestJson.bool(body, "secure");
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace())
            .append(secure != null && secure.booleanValue() ? " SECURE" : "")
            .append(kind(body)).append(" VIEW").append(mode.ifNotExists()).append(' ').append(call.qualifiedSql(name));
        final List<String> columns = new ArrayList<>();
        final JsonNode columnList = body.get("columns");
        if (columnList != null && columnList.isArray()) {
            for (final JsonNode column : columnList.values()) {
                final StringBuilder item = new StringBuilder(RestJson.identifier(column, "name", true).sql());
                final String comment = RestJson.text(column, "comment");
                if (comment != null) {
                    item.append(" COMMENT ").append(RestSql.literal(comment));
                }
                columns.add(item.toString());
            }
        }
        if (!columns.isEmpty()) {
            sql.append(" (").append(String.join(", ", columns)).append(')');
        }
        if (call.flag("copyGrants", false)) {
            sql.append(" COPY GRANTS");
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        sql.append(" AS ").append(query);
        return call.sql().action(sql.toString());
    }

    private static String kind(final JsonNode body) {
        final String kind = RestJson.text(body, "kind");
        if (kind == null || kind.isEmpty() || "PERMANENT".equalsIgnoreCase(kind)) {
            return "";
        }
        if ("TEMPORARY".equalsIgnoreCase(kind)) {
            return " TEMPORARY";
        }
        throw RestException.badRequest("Invalid kind '" + kind + "': expected PERMANENT or TEMPORARY.");
    }
}
