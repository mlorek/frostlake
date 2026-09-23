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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

/**
 * Cortex Search services ({@code cortex-search-service.yaml},
 * {@code /api/v2/databases/{database}/schemas/{schema}/cortex-search-services}): list, create, fetch, delete,
 * {@code :suspend} and {@code :resume} (optionally narrowed to {@code target=indexing|serving}), and
 * {@code :query}, which calls {@code SNOWFLAKE.CORTEX.SEARCH_PREVIEW} — a function of the optional
 * {@code frostlake-ai} module, so it answers {@code 501} on a server without it. {@code :suggest} and
 * {@code :feedback} have no SQL counterpart and answer {@code 501}.
 *
 * <p>A service is read from {@code SHOW CORTEX SEARCH SERVICES}. Its {@code target_lag} travels as the
 * specification's {@code TargetLag} object: {@code {"type": "USER_DEFINED", "seconds": n}} for the SQL
 * {@code TARGET_LAG = 'n seconds'}. The two layer states use the specification's {@code SchedulingState} words:
 * a running layer is {@code ACTIVE}, a suspended one {@code SUSPENDED}.
 */
public final class CortexSearchServiceResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/cortex-search-services";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final String SERVICE = COLLECTION + "/{service_name}";
    private static final String SEARCH_PREVIEW = "SNOWFLAKE.CORTEX.SEARCH_PREVIEW";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listCortexSearchServices", this);
        router.add("POST", COLLECTION, "createCortexSearchService", this);
        router.add("GET", ITEM, "fetchCortexSearchService", this);
        router.add("DELETE", ITEM, "deleteCortexSearchService", this);
        router.add("POST", SERVICE + ":query", "queryCortexSearchService", this);
        router.add("POST", SERVICE + ":suggest", "suggestCortexSearchService", this);
        router.add("POST", ITEM + ":suspend", "suspendCortexSearchService", this);
        router.add("POST", ITEM + ":resume", "resumeCortexSearchService", this);
        router.add("POST", ITEM + ":feedback", "sendFeedback", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listCortexSearchServices":
                return list(call);
            case "createCortexSearchService":
                return create(call);
            case "fetchCortexSearchService":
                return RestResponse.json(200, toJson(fetch(call, call.identifier("name"))));
            case "deleteCortexSearchService":
                return call.sql().action("DROP CORTEX SEARCH SERVICE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "suspendCortexSearchService":
                return alter(call, "SUSPEND");
            case "resumeCortexSearchService":
                return alter(call, "RESUME");
            case "queryCortexSearchService":
                return query(call);
            case "suggestCortexSearchService":
                throw RestException.notImplemented("Cortex Search suggestions are not supported: Frostlake has no "
                    + "suggestion index.");
            case "sendFeedback":
                throw RestException.notImplemented("Cortex Search feedback is not supported: Frostlake keeps no "
                    + "search request log to attach it to.");
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW CORTEX SEARCH SERVICES" + RestShow.like(call)
                + " IN SCHEMA " + call.schemaSql() + RestShow.tail(call))) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The service's SHOW row, or {@code 404}. */
    private static RestRow fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW CORTEX SEARCH SERVICES" + RestShow.likeName(name)
            + " IN SCHEMA " + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Cortex Search Service '" + AppObjectRest.qualifiedDisplay(call, name)
                + "' does not exist or not authorized.");
        }
        return rows.get(0);
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.string(node, "search_column", row, "search_column");
        final ArrayNode columns = CortexFunctions.nameArray(row.string("columns"));
        if (columns != null) {
            node.set("columns", columns);
        }
        final ArrayNode attributes = CortexFunctions.nameArray(row.string("attribute_columns"));
        if (attributes != null) {
            node.set("attribute_columns", attributes);
        }
        final Long lag = lagSeconds(row.nonEmpty("target_lag"));
        if (lag != null) {
            final ObjectNode targetLag = RestJson.object();
            targetLag.put("type", "USER_DEFINED");
            targetLag.put("seconds", lag.longValue());
            node.set("target_lag", targetLag);
        }
        RestJson.name(node, "warehouse", row, "warehouse");
        RestJson.string(node, "definition", row, "definition");
        RestJson.put(node, "comment", row.string("comment"));
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.integer(node, "source_data_num_rows", row, "source_data_num_rows");
        RestJson.put(node, "indexing_state", state(row.nonEmpty("indexing_state")));
        RestJson.put(node, "serving_state", state(row.nonEmpty("serving_state")));
        RestJson.nulls(node, "columns", "attribute_columns", "target_lag", "data_timestamp", "indexing_error",
            "serving_data_bytes");
        return node;
    }

    /** A layer state in the specification's words: RUNNING is ACTIVE. */
    private static String state(final String shown) {
        if (shown == null) {
            return null;
        }
        final String upper = shown.toUpperCase(Locale.ROOT);
        return "RUNNING".equals(upper) ? "ACTIVE" : upper;
    }

    /** A {@code TARGET_LAG} text, {@code '<n> <unit>'}, in seconds; null when it is not of that form. */
    static Long lagSeconds(final String lag) {
        if (lag == null) {
            return null;
        }
        final String text = lag.trim().toLowerCase(Locale.ROOT);
        final int space = text.indexOf(' ');
        if (space <= 0) {
            return null;
        }
        final long amount;
        try {
            amount = Long.parseLong(text.substring(0, space).trim());
        } catch (final NumberFormatException notNumeric) {
            return null;
        }
        final String unit = text.substring(space + 1).trim();
        if (unit.startsWith("second")) {
            return Long.valueOf(amount);
        }
        if (unit.startsWith("minute")) {
            return Long.valueOf(amount * 60L);
        }
        if (unit.startsWith("hour")) {
            return Long.valueOf(amount * 3600L);
        }
        if (unit.startsWith("day")) {
            return Long.valueOf(amount * 86400L);
        }
        return null;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestIdentifier searchColumn = RestJson.identifier(body, "search_column", true);
        final RestIdentifier warehouse = RestJson.identifier(body, "warehouse", true);
        final String definition = RestJson.text(body, "definition");
        if (definition == null) {
            throw RestException.missingProperty("definition");
        }
        final RestCreateMode mode = call.createMode();
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace())
            .append(" CORTEX SEARCH SERVICE").append(mode.ifNotExists()).append(' ').append(call.qualifiedSql(name))
            .append(" ON ").append(searchColumn.sql());
        final List<String> attributes = RestJson.strings(body, "attribute_columns");
        if (attributes != null && !attributes.isEmpty()) {
            sql.append(" ATTRIBUTES ");
            for (int i = 0; i < attributes.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(RestIdentifier.parse(attributes.get(i), "attribute_columns").sql());
            }
        }
        sql.append(" WAREHOUSE = ").append(warehouse.sql());
        sql.append(" TARGET_LAG = ").append(RestSql.literal(targetLag(body)));
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        sql.append(" AS ").append(definition);
        return call.sql().action(sql.toString());
    }

    /** The body's {@code target_lag} as the SQL text, {@code '<n> seconds'}. */
    private static String targetLag(final JsonNode body) {
        if (!RestJson.present(body, "target_lag")) {
            throw RestException.missingProperty("target_lag");
        }
        final JsonNode lag = body.get("target_lag");
        if (!lag.isObject()) {
            throw RestException.unreadable("Property 'target_lag' must be an object.");
        }
        final String type = RestJson.text(lag, "type");
        if (type != null && !"USER_DEFINED".equalsIgnoreCase(type)) {
            throw RestException.badRequest("Invalid target_lag type '" + type
                + "': a Cortex Search Service takes a USER_DEFINED lag.");
        }
        final Long seconds = RestJson.integer(lag, "seconds");
        if (seconds == null) {
            throw RestException.missingProperty("target_lag.seconds");
        }
        return seconds + " seconds";
    }

    private static RestResponse alter(final RestCall call, final String verb) {
        final String target = call.query("target");
        String layer = "";
        if (target != null && !target.isEmpty()) {
            if (!"INDEXING".equals(target) && !"indexing".equals(target) && !"SERVING".equals(target)
                    && !"serving".equals(target)) {
                throw RestException.badRequest("Invalid value '" + target + "' for query parameter 'target': "
                    + "expected indexing or serving.");
            }
            layer = " " + target.toUpperCase(Locale.ROOT);
        }
        return call.sql().action("ALTER CORTEX SEARCH SERVICE" + call.ifExists() + " "
            + call.qualifiedSql(call.identifier("name")) + " " + verb + layer);
    }

    private static RestResponse query(final RestCall call) {
        final RestIdentifier name = call.identifier("service_name");
        final JsonNode body = call.body();
        final RestRow row = fetch(call, name);
        final String service = RestIdentifier.display(row.string("database_name")) + "."
            + RestIdentifier.display(row.string("schema_name")) + "." + RestIdentifier.display(name.name());
        final Object answer = CortexFunctions.firstValue(CortexFunctions.query(call, "SELECT " + SEARCH_PREVIEW
            + "(" + RestSql.literal(service) + ", " + RestSql.literal(body.toString()) + ")", SEARCH_PREVIEW));
        if (answer == null) {
            final ObjectNode empty = RestJson.object();
            empty.set("results", RestJson.array());
            empty.put("request_id", call.requestId());
            return RestResponse.json(200, empty);
        }
        return RestResponse.json(200, RestJson.mapper().readTree(answer.toString()));
    }
}
