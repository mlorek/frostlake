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
 * The service endpoints ({@code service.yaml}) over the Snowpark Container Services statements Frostlake keeps as
 * catalog metadata: SHOW SERVICES, CREATE / ALTER / DROP SERVICE, DESCRIBE SERVICE, EXECUTE JOB SERVICE, and the
 * SHOW … IN SERVICE listings. No container runs, so a service's status is its declared state, its logs are empty,
 * its containers and instances list nothing, and a service role has no grants. {@code PUT} is create-or-alter done
 * here: CREATE SERVICE when the service is absent, otherwise ALTER SERVICE for the specification and the properties
 * the body names, and UNSET for the settable ones it leaves out.
 */
public final class ServiceResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/services";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final String ROLE = COLLECTION + "/{service}/roles/{name}";

    /** The integer properties a body may set, as its property name and the statement's keyword. */
    private static final String[][] INTEGERS = {
        {"auto_suspend_secs", "AUTO_SUSPEND_SECS"},
        {"min_instances", "MIN_INSTANCES"},
        {"min_ready_instances", "MIN_READY_INSTANCES"},
        {"max_instances", "MAX_INSTANCES"},
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listServices", this);
        router.add("POST", COLLECTION, "createService", this);
        router.add("POST", COLLECTION + ":execute-job", "executeJobService", this);
        router.add("GET", ITEM, "fetchService", this);
        router.add("PUT", ITEM, "createOrAlterService", this);
        router.add("DELETE", ITEM, "deleteService", this);
        router.add("GET", ITEM + "/logs", "fetchServiceLogs", this);
        router.add("GET", ITEM + "/status", "fetchServiceStatus", this);
        router.add("GET", ITEM + "/containers", "listServiceContainers", this);
        router.add("GET", ITEM + "/instances", "listServiceInstances", this);
        router.add("GET", ITEM + "/roles", "listServiceRoles", this);
        router.add("GET", ROLE + "/grants-of", "listServiceRoleGrantsOf", this);
        router.add("GET", ROLE + "/grants", "listServiceRoleGrantsTo", this);
        router.add("POST", ITEM + ":resume", "resumeService", this);
        router.add("POST", ITEM + ":suspend", "suspendService", this);
        router.add("GET", ITEM + "/endpoints", "showServiceEndpoints", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER SERVICE", call.qualifiedSql(name), "SERVICE",
                call.qualifiedSql(name), call.identifier("database").sql());
        }
        switch (call.operation()) {
            case "listServices":
                return list(call);
            case "createService":
                return create(call);
            case "executeJobService":
                return call.sql().action(jobSql(call, call.body()));
            case "fetchService":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterService":
                return createOrAlter(call);
            case "deleteService":
                return call.sql().action("DROP SERVICE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "fetchServiceLogs":
                return logs(call);
            case "fetchServiceStatus":
                return status(call);
            case "listServiceContainers":
            case "listServiceInstances":
                return listing(call);
            case "listServiceRoles":
                return roles(call);
            case "listServiceRoleGrantsOf":
            case "listServiceRoleGrantsTo":
                return roleGrants(call);
            case "resumeService":
                return alter(call, "RESUME");
            case "suspendService":
                return alter(call, "SUSPEND");
            case "showServiceEndpoints":
                return endpoints(call);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse alter(final RestCall call, final String action) {
        return call.sql().action("ALTER SERVICE" + call.ifExists() + " " + call.qualifiedSql(call.identifier("name"))
            + " " + action);
    }

    private static String show(final RestCall call, final String like) {
        return "SHOW SERVICES" + like + " IN SCHEMA " + call.schemaSql();
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show(show(call, RestShow.like(call)) + RestShow.tail(call))) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The service's DESCRIBE row, or {@code 404}. */
    private static RestRow describe(final RestCall call, final RestIdentifier name) {
        if (call.sql().showNamed(show(call, RestShow.likeName(name)), name).isEmpty()) {
            throw RestException.notFound("Service '"
                + UserDefinedFunctionResource.qualified(call, name) + "' does not exist or not authorized.");
        }
        final List<RestRow> rows = call.sql().show("DESCRIBE SERVICE " + call.qualifiedSql(name));
        if (rows.isEmpty()) {
            throw RestException.notFound("Service '"
                + UserDefinedFunctionResource.qualified(call, name) + "' does not exist or not authorized.");
        }
        return rows.get(0);
    }

    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        return toJson(describe(call, name));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.string(node, "status", row, "status");
        RestJson.name(node, "compute_pool", row, "compute_pool");
        final String spec = row.string("spec");
        final ObjectNode specNode = RestJson.object();
        if (spec != null && spec.startsWith("@")) {
            final int slash = spec.indexOf('/');
            specNode.put("spec_type", "from_file");
            specNode.put("stage", slash < 0 ? spec.substring(1) : spec.substring(1, slash));
            specNode.put("spec_file", slash < 0 ? "" : spec.substring(slash + 1));
        } else {
            specNode.put("spec_type", "from_inline");
            specNode.put("spec_text", spec == null ? "" : spec);
        }
        node.set("spec", specNode);
        final String integrations = row.nonEmpty("external_access_integrations");
        if (integrations != null) {
            final ArrayNode list = RestJson.array();
            for (final String item : RoutineSignature.splitList(integrations.replace("[", "").replace("]", ""))) {
                if (!item.isEmpty()) {
                    list.add(item);
                }
            }
            node.set("external_access_integrations", list);
        }
        RestJson.name(node, "query_warehouse", row, "query_warehouse");
        RestJson.put(node, "comment", row.nonEmpty("comment"));
        RestJson.bool(node, "is_async_job", row, "is_async_job");
        RestJson.bool(node, "auto_resume", row, "auto_resume");
        RestJson.integer(node, "current_instances", row, "current_instances");
        RestJson.integer(node, "target_instances", row, "target_instances");
        RestJson.integer(node, "min_ready_instances", row, "min_ready_instances");
        RestJson.integer(node, "min_instances", row, "min_instances");
        RestJson.integer(node, "max_instances", row, "max_instances");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "dns_name", row, "dns_name");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.timestamp(node, "updated_on", row, "updated_on");
        RestJson.timestamp(node, "resumed_on", row, "resumed_on");
        RestJson.timestamp(node, "suspended_on", row, "suspended_on");
        RestJson.integer(node, "auto_suspend_secs", row, "auto_suspend_secs");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.bool(node, "is_job", row, "is_job");
        RestJson.string(node, "spec_digest", row, "spec_digest");
        RestJson.bool(node, "is_upgrading", row, "is_upgrading");
        RestJson.string(node, "managing_object_domain", row, "managing_object_domain");
        RestJson.string(node, "managing_object_name", row, "managing_object_name");
        RestJson.nulls(node, "external_access_integrations");
        return node;
    }

    /** The FROM clause a body's {@code spec} describes. */
    private static String sourceSql(final JsonNode body) {
        final JsonNode spec = body.get("spec");
        if (spec == null || !spec.isObject()) {
            throw RestException.badRequest("Missing required property 'spec'.");
        }
        final String type = RestJson.text(spec, "spec_type");
        final String text = RestJson.text(spec, "spec_text");
        if ("from_file".equals(type) || type == null && text == null) {
            final String stage = RestJson.text(spec, "stage");
            final String file = RestJson.text(spec, "spec_file");
            if (stage == null || file == null) {
                throw RestException.badRequest("A specification from a file needs 'stage' and 'spec_file'.");
            }
            final String stageName = stage.startsWith("@") ? stage.substring(1) : stage;
            for (final String part : stageName.split("\\.", -1)) {
                RestIdentifier.parse(part, "stage");
            }
            return " FROM @" + stageName + " SPECIFICATION_FILE = " + RestSql.literal(file);
        }
        if (text == null) {
            throw RestException.badRequest("An inline specification needs 'spec_text'.");
        }
        return " FROM SPECIFICATION " + RestSql.literal(text);
    }

    private static String pool(final JsonNode body) {
        final RestIdentifier pool = RestJson.identifier(body, "compute_pool", true);
        return pool.sql();
    }

    /** The properties CREATE SERVICE and ALTER SERVICE … SET share, as {@code KEY = value} clauses. */
    private static List<String> properties(final JsonNode body) {
        final List<String> out = new ArrayList<>();
        for (final String[] p : INTEGERS) {
            final Long value = RestJson.integer(body, p[0]);
            if (value != null) {
                out.add(p[1] + " = " + value);
            }
        }
        final Boolean autoResume = RestJson.bool(body, "auto_resume");
        if (autoResume != null) {
            out.add("AUTO_RESUME = " + (autoResume.booleanValue() ? "TRUE" : "FALSE"));
        }
        final RestIdentifier warehouse = RestJson.identifier(body, "query_warehouse", false);
        if (warehouse != null) {
            out.add("QUERY_WAREHOUSE = " + warehouse.sql());
        }
        final List<String> integrations = RestJson.strings(body, "external_access_integrations");
        if (integrations != null) {
            final List<String> names = new ArrayList<>();
            for (final String integration : integrations) {
                names.add(RestIdentifier.parse(integration, "external_access_integrations").sql());
            }
            out.add("EXTERNAL_ACCESS_INTEGRATIONS = (" + String.join(", ", names) + ")");
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            out.add("COMMENT = " + RestSql.literal(comment));
        }
        return out;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        if (mode == RestCreateMode.OR_REPLACE) {
            throw RestException.badRequest("Invalid createMode 'orReplace' for a service: expected errorIfExists or "
                + "ifNotExists.");
        }
        return call.sql().action(createSql(call, name, body, mode.ifNotExists()));
    }

    private static String createSql(final RestCall call, final RestIdentifier name, final JsonNode body,
                                    final String ifNotExists) {
        return "CREATE SERVICE" + ifNotExists + " " + call.qualifiedSql(name) + " IN COMPUTE POOL " + pool(body)
            + sourceSql(body) + (properties(body).isEmpty() ? "" : " " + String.join(" ", properties(body)));
    }

    private static String jobSql(final RestCall call, final JsonNode body) {
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final StringBuilder sql = new StringBuilder("EXECUTE JOB SERVICE IN COMPUTE POOL ").append(pool(body))
            .append(sourceSql(body)).append(" NAME = ").append(call.qualifiedSql(name));
        final Boolean async = RestJson.bool(body, "is_async_job");
        if (async != null) {
            sql.append(" ASYNC = ").append(async.booleanValue() ? "TRUE" : "FALSE");
        }
        final RestIdentifier warehouse = RestJson.identifier(body, "query_warehouse", false);
        if (warehouse != null) {
            sql.append(" QUERY_WAREHOUSE = ").append(warehouse.sql());
        }
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        return sql.toString();
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        call.requireBodyNames(name);
        if (call.sql().showNamed(show(call, RestShow.likeName(name)), name).isEmpty()) {
            return call.sql().action(createSql(call, name, body, ""));
        }
        final RestRow current = describe(call, name);
        final RestIdentifier pool = RestJson.identifier(body, "compute_pool", false);
        if (pool != null && !pool.name().equals(current.string("compute_pool"))) {
            throw RestException.badRequest("The compute pool of an existing service cannot be changed.");
        }
        final String head = "ALTER SERVICE " + call.qualifiedSql(name);
        String status = RestResponse.DEFAULT_STATUS;
        if (body.get("spec") != null) {
            status = call.sql().status(head + sourceSql(body));
        }
        final List<String> set = properties(body);
        if (!set.isEmpty()) {
            status = call.sql().status(head + " SET " + String.join(" ", set));
        }
        final List<String> unset = new ArrayList<>();
        for (final String[] p : INTEGERS) {
            if (!RestJson.present(body, p[0])) {
                unset.add(p[1]);
            }
        }
        final String[][] others = {{"auto_resume", "AUTO_RESUME"}, {"query_warehouse", "QUERY_WAREHOUSE"},
            {"external_access_integrations", "EXTERNAL_ACCESS_INTEGRATIONS"}, {"comment", "COMMENT"}};
        for (final String[] p : others) {
            if (!RestJson.present(body, p[0])) {
                unset.add(p[1]);
            }
        }
        if (!unset.isEmpty()) {
            status = call.sql().status(head + " UNSET " + String.join(", ", unset));
        }
        return RestResponse.success(status);
    }

    private static RestResponse status(final RestCall call) {
        describe(call, call.identifier("name"));
        final ObjectNode out = RestJson.object();
        out.put("system$get_service_status", "[]");
        return RestResponse.json(200, out);
    }

    private static RestResponse logs(final RestCall call) {
        describe(call, call.identifier("name"));
        final ObjectNode out = RestJson.object();
        out.put("system$get_service_logs", "");
        return RestResponse.json(200, out);
    }

    private static RestResponse listing(final RestCall call) {
        final boolean containers = "listServiceContainers".equals(call.operation());
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW SERVICE " + (containers ? "CONTAINERS" : "INSTANCES")
                + " IN SERVICE " + call.qualifiedSql(call.identifier("name")))) {
            final ObjectNode item = RestJson.object();
            RestJson.name(item, "database_name", row, "database_name");
            RestJson.name(item, "schema_name", row, "schema_name");
            RestJson.name(item, "service_name", row, "service_name");
            RestJson.string(item, "service_status", row, "service_status");
            RestJson.string(item, "instance_id", row, "instance_id");
            RestJson.string(item, "status", row, "status");
            RestJson.nulls(item, "instance_status", "container_name", "message", "image_name", "image_digest",
                "restart_count", "start_time", "spec_digest", "creation_time");
            out.add(item);
        }
        return RestResponse.json(200, out);
    }

    /**
     * The service roles. SHOW ROLES IN SERVICE resolves the service in the current schema, so the call switches to
     * the path's schema for the listing and back to the session's own afterwards.
     */
    private static List<RestRow> serviceRoles(final RestCall call, final RestIdentifier service) {
        describe(call, service);
        final List<RestRow> current = call.sql().show("SELECT CURRENT_DATABASE() AS d, CURRENT_SCHEMA() AS s");
        call.sql().run("USE SCHEMA " + call.schemaSql());
        try {
            return call.sql().show("SHOW ROLES IN SERVICE " + service.sql());
        } finally {
            final String database = current.isEmpty() ? null : current.get(0).string("d");
            final String schema = current.isEmpty() ? null : current.get(0).string("s");
            if (database != null && schema != null) {
                call.sql().run("USE SCHEMA " + RestIdentifier.quote(database) + "." + RestIdentifier.quote(schema));
            } else if (database != null) {
                call.sql().run("USE DATABASE " + RestIdentifier.quote(database));
            }
        }
    }

    private static RestResponse roles(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : serviceRoles(call, call.identifier("name"))) {
            final ObjectNode role = RestJson.object();
            RestJson.timestamp(role, "created_on", row, "created_on");
            RestJson.string(role, "name", row, "name");
            RestJson.string(role, "comment", row, "comment");
            out.add(role);
        }
        return RestResponse.json(200, out);
    }

    private static RestResponse roleGrants(final RestCall call) {
        final RestIdentifier role = call.identifier("name");
        for (final RestRow row : serviceRoles(call, call.identifier("service"))) {
            if (role.name().equalsIgnoreCase(row.string("name"))) {
                return RestResponse.json(200, RestJson.array());
            }
        }
        throw RestException.notFound("Service role '" + role + "' does not exist or not authorized.");
    }

    private static RestResponse endpoints(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW ENDPOINTS IN SERVICE "
                + call.qualifiedSql(call.identifier("name")))) {
            final ObjectNode endpoint = RestJson.object();
            RestJson.string(endpoint, "name", row, "name");
            RestJson.integer(endpoint, "port", row, "port");
            RestJson.string(endpoint, "portRange", row, "port_range");
            RestJson.string(endpoint, "protocol", row, "protocol");
            RestJson.bool(endpoint, "is_public", row, "is_public");
            RestJson.string(endpoint, "ingress_url", row, "ingress_url");
            out.add(endpoint);
        }
        return RestResponse.json(200, out);
    }
}
