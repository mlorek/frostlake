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

import java.util.ArrayList;
import java.util.List;

/**
 * Warehouses ({@code warehouse.yaml}, {@code /api/v2/warehouses}): list, create, fetch, create-or-alter,
 * delete, {@code :resume}, {@code :suspend}, {@code :rename}, {@code :abort}, {@code :use}, the adaptive
 * {@code :enable} and {@code :disable}, and the tag endpoints.
 *
 * <p>A warehouse is read from {@code SHOW WAREHOUSES} and its three parameters from
 * {@code SHOW PARAMETERS IN WAREHOUSE}, in a listing as well as a fetch; every property of the {@code Warehouse}
 * schema is sent, null when the engine has no value for it. {@code PUT} is a create-or-alter done here: the warehouse is created
 * when it is absent, and otherwise set to the body — the properties it names are SET, the ones it leaves out
 * are UNSET back to their defaults, since the body is the whole property set.
 */
public final class WarehouseResource implements RestResource {

    private static final String COLLECTION = "/api/v2/warehouses";
    private static final String ITEM = COLLECTION + "/{name}";

    /** The properties a body may set, as its snake_case name and the SQL keyword; creation-only ones aside. */
    private static final String[][] STRING_PROPERTIES = {
        {"warehouse_type", "WAREHOUSE_TYPE"},
        {"warehouse_size", "WAREHOUSE_SIZE"},
        {"generation", "GENERATION"},
        {"resource_constraint", "RESOURCE_CONSTRAINT"},
        {"scaling_policy", "SCALING_POLICY"},
        {"comment", "COMMENT"},
    };
    private static final String[][] INTEGER_PROPERTIES = {
        {"max_cluster_count", "MAX_CLUSTER_COUNT"},
        {"min_cluster_count", "MIN_CLUSTER_COUNT"},
        {"auto_suspend", "AUTO_SUSPEND"},
        {"query_acceleration_max_scale_factor", "QUERY_ACCELERATION_MAX_SCALE_FACTOR"},
        {"max_concurrency_level", "MAX_CONCURRENCY_LEVEL"},
        {"statement_queued_timeout_in_seconds", "STATEMENT_QUEUED_TIMEOUT_IN_SECONDS"},
        {"statement_timeout_in_seconds", "STATEMENT_TIMEOUT_IN_SECONDS"},
    };
    private static final String[][] BOOLEAN_PROPERTIES = {
        {"auto_resume", "AUTO_RESUME"},
        {"enable_query_acceleration", "ENABLE_QUERY_ACCELERATION"},
    };
    /** The SHOW PARAMETERS keys reported as integer properties. */
    private static final String[][] PARAMETERS = {
        {"max_concurrency_level", "MAX_CONCURRENCY_LEVEL"},
        {"statement_queued_timeout_in_seconds", "STATEMENT_QUEUED_TIMEOUT_IN_SECONDS"},
        {"statement_timeout_in_seconds", "STATEMENT_TIMEOUT_IN_SECONDS"},
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listWarehouses", this);
        router.add("POST", COLLECTION, "createWarehouse", this);
        router.add("GET", ITEM, "fetchWarehouse", this);
        router.add("PUT", ITEM, "createOrAlterWarehouse", this);
        router.add("DELETE", ITEM, "deleteWarehouse", this);
        router.add("POST", ITEM + ":resume", "resumeWarehouse", this);
        router.add("POST", ITEM + ":suspend", "suspendWarehouse", this);
        router.add("POST", ITEM + ":rename", "renameWarehouse", this);
        router.add("POST", ITEM + ":abort", "abortAllQueriesOnWarehouse", this);
        router.add("POST", ITEM + ":use", "useWarehouse", this);
        router.add("POST", ITEM + ":enable", "enableWarehouse", this);
        router.add("POST", ITEM + ":disable", "disableWarehouse", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER WAREHOUSE", name.sql(), "WAREHOUSE",
                RestIdentifier.display(name.name()), "SNOWFLAKE");
        }
        switch (call.operation()) {
            case "listWarehouses":
                return list(call);
            case "createWarehouse":
                return create(call);
            case "fetchWarehouse":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterWarehouse":
                return createOrAlter(call);
            case "deleteWarehouse":
                return call.sql().action("DROP WAREHOUSE" + call.ifExists() + " " + call.identifier("name").sql());
            case "resumeWarehouse":
                return alter(call, "RESUME IF SUSPENDED");
            case "suspendWarehouse":
                return alter(call, "SUSPEND");
            case "abortAllQueriesOnWarehouse":
                return alter(call, "ABORT ALL QUERIES");
            case "enableWarehouse":
                return alter(call, "ENABLE");
            case "disableWarehouse":
                return alter(call, "DISABLE");
            case "renameWarehouse":
                return alter(call, "RENAME TO " + RestJson.identifier(call.body(), "name", true).sql());
            case "useWarehouse":
                return call.sql().action("USE WAREHOUSE " + call.identifier("name").sql());
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse alter(final RestCall call, final String action) {
        return call.sql().action("ALTER WAREHOUSE" + call.ifExists() + " " + call.identifier("name").sql() + " "
            + action);
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW WAREHOUSES" + RestShow.like(call))) {
            out.add(toJson(row, call.sql().show("SHOW PARAMETERS IN WAREHOUSE "
                + RestIdentifier.quote(row.string("name")))));
        }
        return RestResponse.json(200, out);
    }

    /** The warehouse as the {@code Warehouse} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW WAREHOUSES" + RestShow.likeName(name), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Warehouse '" + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0), call.sql().show("SHOW PARAMETERS IN WAREHOUSE " + name.sql()));
    }

    private static ObjectNode toJson(final RestRow row, final List<RestRow> parameters) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.string(node, "state", row, "state");
        RestJson.string(node, "type", row, "type");
        RestJson.string(node, "warehouse_type", row, "type");
        RestJson.string(node, "size", row, "size");
        RestJson.string(node, "warehouse_size", row, "size");
        RestJson.integer(node, "min_cluster_count", row, "min_cluster_count");
        RestJson.integer(node, "max_cluster_count", row, "max_cluster_count");
        RestJson.integer(node, "started_clusters", row, "started_clusters");
        RestJson.integer(node, "running", row, "running");
        RestJson.integer(node, "queued", row, "queued");
        RestJson.bool(node, "is_default", row, "is_default");
        RestJson.bool(node, "is_current", row, "is_current");
        RestJson.integer(node, "auto_suspend", row, "auto_suspend");
        RestJson.boolText(node, "auto_resume", row, "auto_resume");
        RestJson.put(node, "available", row.string("available"));
        RestJson.put(node, "provisioning", row.string("provisioning"));
        RestJson.put(node, "quiescing", row.string("quiescing"));
        RestJson.put(node, "other", row.string("other"));
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.timestamp(node, "resumed_on", row, "resumed_on");
        RestJson.timestamp(node, "updated_on", row, "updated_on");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "comment", row, "comment");
        RestJson.boolText(node, "enable_query_acceleration", row, "enable_query_acceleration");
        RestJson.integer(node, "query_acceleration_max_scale_factor", row, "query_acceleration_max_scale_factor");
        final String monitor = row.nonEmpty("resource_monitor");
        RestJson.put(node, "resource_monitor", monitor == null || "null".equals(monitor) ? null
            : RestIdentifier.display(monitor));
        RestJson.string(node, "scaling_policy", row, "scaling_policy");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "budget", row, "budget");
        RestJson.string(node, "resource_constraint", row, "resource_constraint");
        RestJson.string(node, "generation", row, "generation");
        RestJson.integer(node, "query_throughput_multiplier", row, "query_throughput_multiplier");
        RestJson.string(node, "max_query_performance_level", row, "max_query_performance_level");
        for (final String[] parameter : PARAMETERS) {
            Long value = null;
            for (final RestRow p : parameters) {
                if (parameter[1].equalsIgnoreCase(p.string("key"))) {
                    value = p.integer("value");
                }
            }
            RestJson.put(node, parameter[0], value);
        }
        RestJson.nulls(node, "initially_suspended", "wait_for_completion", "kind", "warehouse_credit_limit",
            "target_statement_size");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " WAREHOUSE" + mode.ifNotExists()
            + " " + name.sql());
        properties(sql, body);
        sql.bool(body, "initially_suspended", "INITIALLY_SUSPENDED");
        return call.sql().action(sql.toString());
    }

    /** Appends every property the body sets, in the order the grammar reads them. */
    private static void properties(final RestStatement sql, final JsonNode body) {
        for (final String[] p : STRING_PROPERTIES) {
            if (!"comment".equals(p[0])) {
                sql.string(body, p[0], p[1]);
            }
        }
        for (final String[] p : INTEGER_PROPERTIES) {
            sql.integer(body, p[0], p[1]);
        }
        for (final String[] p : BOOLEAN_PROPERTIES) {
            sql.bool(body, p[0], p[1]);
        }
        sql.identifier(body, "resource_monitor", "RESOURCE_MONITOR");
        sql.string(body, "comment", "COMMENT");
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        call.requireBodyNames(name);
        final List<RestRow> existing = call.sql().showNamed("SHOW WAREHOUSES" + RestShow.likeName(name), name);
        if (existing.isEmpty()) {
            final RestStatement sql = new RestStatement("CREATE WAREHOUSE " + name.sql());
            properties(sql, body);
            sql.bool(body, "initially_suspended", "INITIALLY_SUSPENDED");
            return call.sql().action(sql.toString());
        }
        String status = RestResponse.DEFAULT_STATUS;
        final RestStatement set = new RestStatement("ALTER WAREHOUSE " + name.sql() + " SET");
        final String constraint = RestJson.text(body, "resource_constraint");
        if (constraint != null && constraint.equalsIgnoreCase(existing.get(0).string("resource_constraint"))) {
            // The constraint the warehouse already has: a body read back from a fetch sets nothing new.
            final ObjectNode unchanged = ((ObjectNode) body).deepCopy();
            unchanged.remove("resource_constraint");
            properties(set, unchanged);
        } else {
            properties(set, body);
        }
        if (RestJson.present(body, "warehouse_size")) {
            // WAIT_FOR_COMPLETION waits for a resize, so the account takes it only beside WAREHOUSE_SIZE.
            set.bool(body, "wait_for_completion", "WAIT_FOR_COMPLETION");
        }
        if (set.propertyCount() > 0) {
            status = call.sql().status(set.toString());
        }
        final List<String> unset = new ArrayList<>();
        for (final String[] p : STRING_PROPERTIES) {
            // GENERATION and RESOURCE_CONSTRAINT spell the same setting: either one in the body keeps both, and
            // a standard warehouse's constraint is unset only through GENERATION.
            if (("generation".equals(p[0]) || "resource_constraint".equals(p[0]))
                    && (RestJson.present(body, "generation") || RestJson.present(body, "resource_constraint"))) {
                continue;
            }
            if ("resource_constraint".equals(p[0]) && !"SNOWPARK-OPTIMIZED".equals(existing.get(0).string("type"))) {
                continue;
            }
            unsetIfAbsent(unset, body, p);
        }
        for (final String[] p : INTEGER_PROPERTIES) {
            unsetIfAbsent(unset, body, p);
        }
        for (final String[] p : BOOLEAN_PROPERTIES) {
            unsetIfAbsent(unset, body, p);
        }
        unsetIfAbsent(unset, body, new String[] {"resource_monitor", "RESOURCE_MONITOR"});
        if (!unset.isEmpty()) {
            status = call.sql().status("ALTER WAREHOUSE " + name.sql() + " UNSET " + String.join(", ", unset));
        }
        return RestResponse.success(status);
    }

    private static void unsetIfAbsent(final List<String> unset, final JsonNode body, final String[] property) {
        if (!RestJson.present(body, property[0])) {
            unset.add(property[1]);
        }
    }
}
