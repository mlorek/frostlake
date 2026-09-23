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
import dev.frostlake.http.rest.RestSql;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The compute pool endpoints ({@code compute-pool.yaml}): SHOW COMPUTE POOLS, CREATE COMPUTE POOL, DESCRIBE COMPUTE
 * POOL, DROP COMPUTE POOL, the ALTER COMPUTE POOL actions, SHOW COMPUTE POOL INSTANCE FAMILIES and the tag endpoints.
 * {@code PUT} is create-or-alter done here — no {@code CREATE OR ALTER COMPUTE POOL} exists — creating the pool
 * when it is absent and otherwise applying ALTER COMPUTE POOL … SET for what the body names and UNSET for the settable
 * properties it leaves out.
 */
public final class ComputePoolResource implements RestResource {

    private static final String COLLECTION = "/api/v2/compute-pools";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listComputePools", this);
        router.add("POST", COLLECTION, "createComputePool", this);
        router.add("GET", COLLECTION + "/instance-families", "listComputePoolInstanceFamilies", this);
        router.add("GET", ITEM, "fetchComputePool", this);
        router.add("PUT", ITEM, "createOrAlterComputePool", this);
        router.add("DELETE", ITEM, "deleteComputePool", this);
        router.add("POST", ITEM + ":resume", "resumeComputePool", this);
        router.add("POST", ITEM + ":suspend", "suspendComputePool", this);
        router.add("POST", ITEM + ":stopallservices", "stopAllServicesInComputePoolDeprecated", this);
        router.add("POST", ITEM + ":stop-all-services", "stopAllServicesInComputePool", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER COMPUTE POOL", name.sql(), "COMPUTE POOL",
                RestIdentifier.display(name.name()), "SNOWFLAKE");
        }
        switch (call.operation()) {
            case "listComputePools":
                return list(call);
            case "createComputePool":
                return create(call);
            case "listComputePoolInstanceFamilies":
                return instanceFamilies(call);
            case "fetchComputePool":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterComputePool":
                return createOrAlter(call);
            case "deleteComputePool":
                return call.sql().action("DROP COMPUTE POOL" + call.ifExists() + " " + call.identifier("name").sql());
            case "resumeComputePool":
                return alter(call, "RESUME");
            case "suspendComputePool":
                return alter(call, "SUSPEND");
            case "stopAllServicesInComputePoolDeprecated":
            case "stopAllServicesInComputePool":
                return alter(call, "STOP ALL");
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse alter(final RestCall call, final String action) {
        return call.sql().action("ALTER COMPUTE POOL " + call.identifier("name").sql() + " " + action);
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW COMPUTE POOLS" + RestShow.like(call) + RestShow.tail(call))) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The pool as the {@code ComputePool} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        if (call.sql().showNamed("SHOW COMPUTE POOLS" + RestShow.likeName(name), name).isEmpty()) {
            throw RestException.notFound("Compute pool '" + name + "' does not exist or not authorized.");
        }
        final List<RestRow> rows = call.sql().show("DESCRIBE COMPUTE POOL " + name.sql());
        if (rows.isEmpty()) {
            throw RestException.notFound("Compute pool '" + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.string(node, "state", row, "state");
        RestJson.integer(node, "min_nodes", row, "min_nodes");
        RestJson.integer(node, "max_nodes", row, "max_nodes");
        RestJson.string(node, "instance_family", row, "instance_family");
        RestJson.integer(node, "num_services", row, "num_services");
        RestJson.integer(node, "num_jobs", row, "num_jobs");
        RestJson.integer(node, "auto_suspend_secs", row, "auto_suspend_secs");
        RestJson.bool(node, "auto_resume", row, "auto_resume");
        RestJson.integer(node, "active_nodes", row, "active_nodes");
        RestJson.integer(node, "idle_nodes", row, "idle_nodes");
        RestJson.integer(node, "target_nodes", row, "target_nodes");
        RestJson.timestamp(node, "created_on", row, "created_on");
        final String resumed = row.timestamp("resumed_on");
        RestJson.put(node, "resumed_on", resumed != null && !resumed.startsWith("1970-01-01") ? resumed : null);
        RestJson.timestamp(node, "updated_on", row, "updated_on");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "comment", row, "comment");
        RestJson.bool(node, "is_exclusive", row, "is_exclusive");
        RestJson.string(node, "application", row, "application");
        RestJson.string(node, "budget", row, "budget");
        RestJson.string(node, "error_code", row, "error_code");
        RestJson.string(node, "status_message", row, "status_message");
        RestJson.string(node, "placement_group", row, "placement_group");
        RestJson.string(node, "backup_instance_families", row, "backup_instance_families");
        RestJson.nulls(node, "desired_nodes", "desired_nodes_expire_after", "optimize_for_capacity");
        return node;
    }

    private static RestResponse instanceFamilies(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW COMPUTE POOL INSTANCE FAMILIES")) {
            final ObjectNode node = RestJson.object();
            RestJson.string(node, "name", row, "name");
            RestJson.put(node, "description", row.string("description"));
            RestJson.integer(node, "vcpu", row, "vcpu");
            RestJson.integer(node, "memory_gib", row, "memory_gib");
            final String storage = row.nonEmpty("storage_gib");
            if (storage == null) {
                node.putNull("storage_gib");
            } else {
                try {
                    node.put("storage_gib", new BigDecimal(storage.trim()).doubleValue());
                } catch (final NumberFormatException notNumeric) {
                    node.put("storage_gib", storage);
                }
            }
            RestJson.put(node, "gpu", row.string("gpu"));
            RestJson.integer(node, "gpu_count", row, "gpu_count");
            RestJson.integer(node, "gpu_memory_gib", row, "gpu_memory_gib");
            RestJson.integer(node, "current_node_usage", row, "current_node_usage");
            RestJson.put(node, "message", row.string("message"));
            out.add(node);
        }
        return RestResponse.json(200, out);
    }

    /** The instance family a body names, as the bare upper-case word the statement takes. */
    private static String family(final JsonNode body) {
        final String family = RestJson.text(body, "instance_family");
        if (family == null) {
            return null;
        }
        final String upper = family.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < upper.length(); i++) {
            final char c = upper.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) {
                throw RestException.badRequest("Invalid instance family '" + family + "'.");
            }
        }
        return upper;
    }

    private static String createSql(final RestIdentifier name, final JsonNode body, final String ifNotExists,
                                    final Boolean initiallySuspended) {
        final StringBuilder sql = new StringBuilder("CREATE COMPUTE POOL").append(ifNotExists).append(' ')
            .append(name.sql());
        final Long minNodes = RestJson.integer(body, "min_nodes");
        if (minNodes != null) {
            sql.append(" MIN_NODES = ").append(minNodes);
        }
        final Long maxNodes = RestJson.integer(body, "max_nodes");
        if (maxNodes != null) {
            sql.append(" MAX_NODES = ").append(maxNodes);
        }
        final String family = family(body);
        if (family != null) {
            sql.append(" INSTANCE_FAMILY = ").append(family);
        }
        final Boolean autoResume = RestJson.bool(body, "auto_resume");
        if (autoResume != null) {
            sql.append(" AUTO_RESUME = ").append(autoResume.booleanValue() ? "TRUE" : "FALSE");
        }
        if (initiallySuspended != null) {
            sql.append(" INITIALLY_SUSPENDED = ").append(initiallySuspended.booleanValue() ? "TRUE" : "FALSE");
        }
        final Long autoSuspend = RestJson.integer(body, "auto_suspend_secs");
        if (autoSuspend != null) {
            sql.append(" AUTO_SUSPEND_SECS = ").append(autoSuspend);
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        return sql.toString();
    }

    private static Boolean initiallySuspended(final RestCall call) {
        final String value = call.query("initiallySuspended");
        return value == null || value.isEmpty() ? null : Boolean.valueOf(call.flag("initiallySuspended", false));
    }

    /**
     * CREATE COMPUTE POOL from the body. The statement has no OR REPLACE form, so {@code orReplace} drops a pool of
     * that name first.
     */
    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final String mode = call.query("createMode");
        final Boolean suspended = initiallySuspended(call);
        if ("orReplace".equals(mode)) {
            call.sql().run("DROP COMPUTE POOL IF EXISTS " + name.sql());
        }
        return call.sql().action(createSql(name, body, "ifNotExists".equals(mode) ? " IF NOT EXISTS"
            : call.createMode().ifNotExists(), suspended));
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        call.requireBodyNames(name);
        final List<RestRow> existing = call.sql().showNamed("SHOW COMPUTE POOLS" + RestShow.likeName(name), name);
        if (existing.isEmpty()) {
            return call.sql().action(createSql(name, body, "", null));
        }
        final RestRow current = existing.get(0);
        String status = RestResponse.DEFAULT_STATUS;
        final StringBuilder set = new StringBuilder();
        final Long minNodes = RestJson.integer(body, "min_nodes");
        if (minNodes != null) {
            set.append(" MIN_NODES = ").append(minNodes);
        }
        final Long maxNodes = RestJson.integer(body, "max_nodes");
        if (maxNodes != null) {
            set.append(" MAX_NODES = ").append(maxNodes);
        }
        final String family = family(body);
        if (family != null && !family.equalsIgnoreCase(current.string("instance_family"))) {
            set.append(" INSTANCE_FAMILY = ").append(family);
        }
        final Boolean autoResume = RestJson.bool(body, "auto_resume");
        if (autoResume != null) {
            set.append(" AUTO_RESUME = ").append(autoResume.booleanValue() ? "TRUE" : "FALSE");
        }
        final Long autoSuspend = RestJson.integer(body, "auto_suspend_secs");
        if (autoSuspend != null) {
            set.append(" AUTO_SUSPEND_SECS = ").append(autoSuspend);
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            set.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        if (set.length() > 0) {
            status = call.sql().status("ALTER COMPUTE POOL " + name.sql() + " SET" + set);
        }
        final List<String> unset = new ArrayList<>();
        if (autoResume == null) {
            unset.add("AUTO_RESUME");
        }
        if (autoSuspend == null) {
            unset.add("AUTO_SUSPEND_SECS");
        }
        if (comment == null) {
            unset.add("COMMENT");
        }
        if (!unset.isEmpty()) {
            status = call.sql().status("ALTER COMPUTE POOL " + name.sql() + " UNSET " + String.join(", ", unset));
        }
        return RestResponse.success(status);
    }
}
