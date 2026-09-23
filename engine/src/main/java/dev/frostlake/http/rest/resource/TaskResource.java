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
import dev.frostlake.task.IntervalSchedule;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tasks ({@code task.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/tasks}): list (with
 * {@code rootOnly} as {@code SHOW TASKS … ROOT ONLY}), create, fetch, create-or-alter, delete, {@code :execute}
 * ({@code retryLast} as {@code EXECUTE TASK … RETRY LAST}), {@code :resume}, {@code :suspend}, the dependents
 * ({@code TASK_DEPENDENTS}), the current and complete graph runs ({@code CURRENT_TASK_GRAPHS},
 * {@code COMPLETE_TASK_GRAPHS}, each under both of its paths) and the tag endpoints.
 *
 * <p>A task is read from {@code SHOW TASKS}. A body's {@code schedule} is written as
 * {@code SCHEDULE = '<n> SECOND'} or {@code 'USING CRON <expr> <timezone>'}, its {@code predecessors} as the
 * {@code AFTER} list, its {@code condition} as {@code WHEN} and its {@code definition} as the body after
 * {@code AS} — the last two are SQL by the schema's definition and are sent as written. Its {@code config} is
 * written as {@code CONFIG}, its {@code session_parameters} as one {@code <name> = <value>} each, its
 * {@code finalize} as {@code FINALIZE} and its {@code execute_as_user} as {@code EXECUTE AS USER}. {@code PUT} is
 * a create-or-alter done here: create when absent, otherwise ALTER SET what the body names, UNSET the settable
 * properties it leaves out, change the predecessors with ADD AFTER and REMOVE AFTER, remove a dropped
 * condition with REMOVE WHEN, and MODIFY the definition and the condition when they changed.
 */
public final class TaskResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/tasks";
    private static final String ITEM = COLLECTION + "/{name}";

    private static final String[][] INTEGER_PROPERTIES = {
        {"user_task_timeout_ms", "USER_TASK_TIMEOUT_MS"},
        {"suspend_task_after_num_failures", "SUSPEND_TASK_AFTER_NUM_FAILURES"},
        {"task_auto_retry_attempts", "TASK_AUTO_RETRY_ATTEMPTS"},
        {"user_task_minimum_trigger_interval_in_seconds", "USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS"},
    };
    private static final String[][] STRING_PROPERTIES = {
        {"user_task_managed_initial_warehouse_size", "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE"},
        {"serverless_task_max_statement_size", "SERVERLESS_TASK_MAX_STATEMENT_SIZE"},
        {"serverless_task_min_statement_size", "SERVERLESS_TASK_MIN_STATEMENT_SIZE"},
        {"overlap_policy", "OVERLAP_POLICY"},
    };
    /** The properties a PUT unsets when its body leaves them out, as the body property and the UNSET name. */
    private static final String[][] UNSETTABLE = {
        {"warehouse", "WAREHOUSE"},
        {"schedule", "SCHEDULE"},
        {"comment", "COMMENT"},
        {"user_task_timeout_ms", "USER_TASK_TIMEOUT_MS"},
        {"suspend_task_after_num_failures", "SUSPEND_TASK_AFTER_NUM_FAILURES"},
        {"task_auto_retry_attempts", "TASK_AUTO_RETRY_ATTEMPTS"},
        {"user_task_minimum_trigger_interval_in_seconds", "USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS"},
        {"user_task_managed_initial_warehouse_size", "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE"},
        {"serverless_task_max_statement_size", "SERVERLESS_TASK_MAX_STATEMENT_SIZE"},
        {"target_completion_interval", "TARGET_COMPLETION_INTERVAL"},
        {"error_integration", "ERROR_INTEGRATION"},
        {"allow_overlapping_execution", "ALLOW_OVERLAPPING_EXECUTION"},
        {"config", "CONFIG"},
        {"overlap_policy", "OVERLAP_POLICY"},
        {"success_integration", "SUCCESS_INTEGRATION"},
        {"serverless_task_min_statement_size", "SERVERLESS_TASK_MIN_STATEMENT_SIZE"},
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listTasks", this);
        router.add("POST", COLLECTION, "createTask", this);
        router.add("GET", ITEM, "fetchTask", this);
        router.add("PUT", ITEM, "createOrAlterTask", this);
        router.add("DELETE", ITEM, "deleteTask", this);
        router.add("POST", ITEM + ":execute", "executeTask", this);
        router.add("POST", ITEM + ":resume", "resumeTask", this);
        router.add("POST", ITEM + ":suspend", "suspendTask", this);
        router.add("GET", ITEM + "/dependents", "fetchTaskDependents", this);
        router.add("GET", ITEM + "/current_graphs", "getCurrentGraphsDeprecated", this);
        router.add("GET", ITEM + "/current-graphs", "getCurrentGraphs", this);
        router.add("GET", ITEM + "/complete_graphs", "getCompleteGraphsDeprecated", this);
        router.add("GET", ITEM + "/complete-graphs", "getCompleteGraphs", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER TASK", call.qualifiedSql(name), "TASK",
                SchemaObjectNames.dotted(call, name), SchemaObjectNames.informationSchemaDatabase(call));
        }
        switch (call.operation()) {
            case "listTasks":
                return list(call);
            case "createTask":
                return create(call, call.createMode(), call.body(), RestJson.identifier(call.body(), "name", true));
            case "fetchTask":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterTask":
                return createOrAlter(call);
            case "deleteTask":
                return call.sql().action("DROP TASK" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "executeTask":
                return call.sql().action("EXECUTE TASK " + call.qualifiedSql(call.identifier("name"))
                    + (call.flag("retryLast", false) ? " RETRY LAST" : ""));
            case "resumeTask":
                return call.sql().action("ALTER TASK " + call.qualifiedSql(call.identifier("name")) + " RESUME");
            case "suspendTask":
                return call.sql().action("ALTER TASK " + call.qualifiedSql(call.identifier("name")) + " SUSPEND");
            case "fetchTaskDependents":
                return dependents(call);
            case "getCurrentGraphs":
            case "getCurrentGraphsDeprecated":
                return graphs(call, false);
            case "getCompleteGraphs":
            case "getCompleteGraphsDeprecated":
                return graphs(call, true);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    // ---- reading ----------------------------------------------------------------------------------------

    private static RestResponse list(final RestCall call) {
        final StringBuilder sql = new StringBuilder("SHOW TASKS").append(RestShow.like(call)).append(" IN SCHEMA ")
            .append(call.schemaSql());
        final String startsWith = call.query("startsWith");
        if (startsWith != null) {
            sql.append(" STARTS WITH ").append(RestSql.literal(startsWith));
        }
        if (call.flag("rootOnly", false)) {
            sql.append(" ROOT ONLY");
        }
        final Long limit = call.integer("showLimit");
        if (limit != null && (limit.longValue() < 1 || limit.longValue() > RestShow.MAX_LIMIT)) {
            throw RestException.badRequest("Invalid value " + limit + " for query parameter 'showLimit': "
                + "expected 1 to " + RestShow.MAX_LIMIT + ".");
        }
        final String fromName = call.query("fromName");
        if (limit != null || fromName != null) {
            sql.append(" LIMIT ").append(limit != null ? limit.longValue() : RestShow.MAX_LIMIT);
            if (fromName != null) {
                sql.append(" FROM ").append(RestSql.literal(fromName));
            }
        }
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show(sql.toString())) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The task's SHOW TASKS row, or null when it does not exist. */
    private static RestRow row(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW TASKS" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** The task as the {@code Task} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final RestRow row = row(call, name);
        if (row == null) {
            throw RestException.notFound("Task '" + name + "' does not exist or not authorized.");
        }
        return toJson(row);
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.name(node, "warehouse", row, "warehouse");
        final ObjectNode schedule = schedule(row.nonEmpty("schedule"));
        if (schedule != null) {
            node.set("schedule", schedule);
        }
        RestJson.put(node, "comment", row.string("comment"));
        final JsonNode config = parse(row.nonEmpty("config"));
        if (config != null && config.isObject()) {
            node.set("config", config);
        }
        RestJson.string(node, "definition", row, "definition");
        final JsonNode predecessors = parse(row.nonEmpty("predecessors"));
        final ArrayNode names = RestJson.array();
        if (predecessors != null && predecessors.isArray()) {
            // A predecessor in the task's own schema is named bare, as a body names it.
            final String home = row.string("database_name") + "." + row.string("schema_name") + ".";
            for (final JsonNode predecessor : predecessors.values()) {
                final String qualified = predecessor.asString();
                names.add(qualified.startsWith(home) ? qualified.substring(home.length()) : qualified);
            }
        }
        node.set("predecessors", names);
        RestJson.string(node, "task_relations", row, "task_relations");
        final JsonNode relations = parse(row.nonEmpty("task_relations"));
        if (relations != null && relations.has("FinalizedRootTask")) {
            final String[] rootParts = QualifiedName.parse(relations.get("FinalizedRootTask").asString()).parts();
            node.put("finalize", RestIdentifier.display(rootParts[rootParts.length - 1]));
        }
        final ObjectNode interval = schedule(row.nonEmpty("target_completion_interval"));
        if (interval != null && interval.has("minutes")) {
            node.set("target_completion_interval", interval);
        }
        RestJson.string(node, "condition", row, "condition");
        RestJson.bool(node, "allow_overlapping_execution", row, "allow_overlapping_execution");
        integration(node, "error_integration", row);
        integration(node, "success_integration", row);
        RestJson.string(node, "overlap_policy", row, "overlap_policy");
        RestJson.string(node, "execute_as_user", row, "execute_as_user");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.string(node, "id", row, "id");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        final String state = row.nonEmpty("state");
        if (state != null) {
            node.put("state", state.toLowerCase(Locale.ROOT));
        }
        RestJson.timestamp(node, "last_committed_on", row, "last_committed_on");
        RestJson.timestamp(node, "last_suspended_on", row, "last_suspended_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        // The account answers an empty config and session parameters as empty objects, and no owner role type.
        if (!node.has("config")) {
            node.set("config", RestJson.object());
        }
        node.set("session_parameters", RestJson.object());
        node.putNull("owner_role_type");
        RestJson.nulls(node, "name", "warehouse", "schedule", "comment", "finalize", "task_auto_retry_attempts",
            "config", "session_parameters", "definition", "predecessors", "task_relations",
            "user_task_managed_initial_warehouse_size", "target_completion_interval",
            "serverless_task_min_statement_size", "serverless_task_max_statement_size", "user_task_timeout_ms",
            "suspend_task_after_num_failures", "user_task_minimum_trigger_interval_in_seconds", "condition",
            "allow_overlapping_execution", "error_integration", "success_integration", "overlap_policy",
            "execute_as_user", "created_on", "id", "owner", "owner_role_type", "state", "last_committed_on",
            "last_suspended_on", "database_name", "schema_name");
        return node;
    }

    /** An integration column, which the listing spells {@code null} when none is set. */
    private static void integration(final ObjectNode node, final String column, final RestRow row) {
        final String value = row.nonEmpty(column);
        if (value != null && !"null".equalsIgnoreCase(value)) {
            node.put(column, RestIdentifier.display(value));
        }
    }

    /** A JSON cell's value, or null when it is empty or not JSON. */
    private static JsonNode parse(final String text) {
        if (text == null || text.isBlank() || "null".equals(text)) {
            return null;
        }
        try {
            return RestJson.mapper().readTree(text);
        } catch (final JacksonException notJson) {
            return null;
        }
    }

    /**
     * A schedule's text as a {@code TaskSchedule}: {@code USING CRON <expr> <timezone>} is a {@code CRON_TYPE}
     * schedule, an interval ({@code <n> SECOND[S]}, {@code <n> S}, {@code <n> MINUTE[S]}, {@code <n> HOUR[S]}) a
     * {@code MINUTES_TYPE} one.
     */
    private static ObjectNode schedule(final String text) {
        if (text == null) {
            return null;
        }
        final String trimmed = text.trim();
        final ObjectNode schedule = RestJson.object();
        if (trimmed.toUpperCase(Locale.ROOT).startsWith("USING CRON ")) {
            final String rest = trimmed.substring("USING CRON ".length()).trim();
            final int zone = rest.lastIndexOf(' ');
            schedule.put("schedule_type", "CRON_TYPE");
            schedule.put("cron_expr", zone > 0 ? rest.substring(0, zone).trim() : rest);
            if (zone > 0) {
                schedule.put("timezone", rest.substring(zone + 1));
            }
            return schedule;
        }
        final long seconds = IntervalSchedule.seconds(trimmed);
        if (seconds < 0L) {
            return null;
        }
        // An interval reads back as whole minutes and the seconds left over, the seconds always present.
        schedule.put("schedule_type", "MINUTES_TYPE");
        schedule.put("minutes", seconds / 60);
        schedule.put("seconds", seconds % 60);
        return schedule;
    }

    // ---- writing ----------------------------------------------------------------------------------------

    private static RestResponse create(final RestCall call, final RestCreateMode mode, final JsonNode body,
                                       final RestIdentifier name) {
        final String definition = RestJson.text(body, "definition");
        if (definition == null || definition.isBlank()) {
            throw RestException.badRequest("Missing required property 'definition'.");
        }
        final StringBuilder sql = new StringBuilder("CREATE").append(mode.orReplace()).append(" TASK")
            .append(mode.ifNotExists()).append(' ').append(call.qualifiedSql(name));
        sql.append(options(body, true));
        final RestIdentifier finalize = RestJson.identifier(body, "finalize", false);
        if (finalize != null) {
            sql.append(" FINALIZE = ").append(finalize.sql());
        }
        final List<String> predecessors = RestJson.strings(body, "predecessors");
        if (predecessors != null && !predecessors.isEmpty()) {
            sql.append(" AFTER ");
            for (int i = 0; i < predecessors.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(taskName(call, predecessors.get(i)));
            }
        }
        final RestIdentifier runAs = RestJson.identifier(body, "execute_as_user", false);
        if (runAs != null) {
            sql.append(" EXECUTE AS USER ").append(runAs.sql());
        }
        final String condition = RestJson.text(body, "condition");
        if (condition != null) {
            sql.append(" WHEN ").append(condition);
        }
        sql.append(" AS ").append(definition);
        return call.sql().action(sql.toString());
    }

    /** The properties a body sets, as the options of CREATE TASK or ALTER TASK … SET. */
    private static String options(final JsonNode body, final boolean withComment) {
        final StringBuilder sql = new StringBuilder();
        final RestIdentifier warehouse = RestJson.identifier(body, "warehouse", false);
        if (warehouse != null) {
            sql.append(" WAREHOUSE = ").append(warehouse.sql());
        }
        final String schedule = scheduleSql(body.get("schedule"), "schedule");
        if (schedule != null) {
            sql.append(" SCHEDULE = ").append(RestSql.literal(schedule));
        }
        final Boolean overlapping = RestJson.bool(body, "allow_overlapping_execution");
        if (overlapping != null) {
            sql.append(" ALLOW_OVERLAPPING_EXECUTION = ").append(overlapping.booleanValue() ? "TRUE" : "FALSE");
        }
        for (final String[] property : INTEGER_PROPERTIES) {
            final Long value = RestJson.integer(body, property[0]);
            if (value != null) {
                sql.append(' ').append(property[1]).append(" = ").append(value.longValue());
            }
        }
        for (final String[] property : STRING_PROPERTIES) {
            final String value = RestJson.text(body, property[0]);
            if (value != null) {
                sql.append(' ').append(property[1]).append(" = ").append(RestSql.literal(value));
            }
        }
        final String interval = scheduleSql(body.get("target_completion_interval"), "target_completion_interval");
        if (interval != null) {
            sql.append(" TARGET_COMPLETION_INTERVAL = ").append(RestSql.literal(interval));
        }
        final RestIdentifier errorIntegration = RestJson.identifier(body, "error_integration", false);
        if (errorIntegration != null) {
            sql.append(" ERROR_INTEGRATION = ").append(errorIntegration.sql());
        }
        final RestIdentifier successIntegration = RestJson.identifier(body, "success_integration", false);
        if (successIntegration != null) {
            sql.append(" SUCCESS_INTEGRATION = ").append(successIntegration.sql());
        }
        final JsonNode config = body.get("config");
        if (given(body, "config")) {
            if (!config.isObject()) {
                throw RestException.badRequest("Property 'config' must be an object.");
            }
            sql.append(" CONFIG = ").append(RestSql.literal(RestJson.mapper().writeValueAsString(config)));
        }
        final JsonNode sessionParameters = body.get("session_parameters");
        if (sessionParameters != null && !sessionParameters.isNull()) {
            if (!sessionParameters.isObject()) {
                throw RestException.badRequest("Property 'session_parameters' must be an object.");
            }
            for (final Map.Entry<String, JsonNode> parameter : sessionParameters.properties()) {
                sql.append(' ').append(RestIdentifier.parse(parameter.getKey(), "session_parameters").sql())
                    .append(" = ").append(parameterValue(parameter.getValue()));
            }
        }
        final String comment = RestJson.text(body, "comment");
        if (withComment && comment != null) {
            sql.append(" COMMENT = ").append(RestSql.literal(comment));
        }
        return sql.toString();
    }

    /**
     * Whether the body sets a property: present, and not an empty object. An empty object is what a fetched task
     * answers for a config it has none of, and the account takes it as no config: a create writes none, and a
     * create-or-alter unsets the one the task has.
     */
    private static boolean given(final JsonNode body, final String property) {
        final JsonNode value = body.get(property);
        return RestJson.present(body, property) && !(value.isObject() && value.isEmpty());
    }

    /** A session parameter's JSON value as SQL: a string quoted, a number or a boolean as it is. */
    private static String parameterValue(final JsonNode value) {
        if (value.isBoolean()) {
            return value.asBoolean() ? "TRUE" : "FALSE";
        }
        if (value.isNumber()) {
            return value.asString();
        }
        if (value.isString()) {
            return RestSql.literal(value.asString());
        }
        throw RestException.badRequest("A session parameter's value must be a string, a number or a boolean.");
    }

    /** A {@code TaskSchedule} object as the schedule text the SQL takes, or null when absent. */
    private static String scheduleSql(final JsonNode schedule, final String property) {
        if (schedule == null || schedule.isNull()) {
            return null;
        }
        if (!schedule.isObject()) {
            throw RestException.badRequest("Property '" + property + "' must be an object.");
        }
        final String cron = RestJson.text(schedule, "cron_expr");
        final String type = RestJson.text(schedule, "schedule_type");
        if ("CRON_TYPE".equalsIgnoreCase(type) || type == null && cron != null) {
            final String timezone = RestJson.text(schedule, "timezone");
            if (cron == null || timezone == null) {
                throw RestException.badRequest("A CRON_TYPE schedule needs 'cron_expr' and 'timezone'.");
            }
            return "USING CRON " + cron + " " + timezone;
        }
        final Long minutes = RestJson.integer(schedule, "minutes");
        if (minutes == null) {
            throw RestException.badRequest("A MINUTES_TYPE schedule needs 'minutes'.");
        }
        // The account writes every MINUTES_TYPE schedule as its length in seconds, singular unit and all:
        // {minutes: 5} is '300 SECOND', and SHOW TASKS lists it so.
        final Long seconds = RestJson.integer(schedule, "seconds");
        return (minutes.longValue() * 60 + (seconds == null ? 0L : seconds.longValue())) + " SECOND";
    }

    /** A predecessor as a qualified task name: a bare name is a task of the path's schema. */
    private static String taskName(final RestCall call, final String text) {
        final List<String> parts = new ArrayList<>();
        final StringBuilder part = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            }
            if (c == '.' && !quoted) {
                parts.add(part.toString());
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        parts.add(part.toString());
        if (parts.size() > 3) {
            throw RestException.badRequest("Invalid task name '" + text + "' in property 'predecessors'.");
        }
        final RestIdentifier task = RestIdentifier.parse(parts.get(parts.size() - 1), "predecessors");
        final String schema = parts.size() >= 2
            ? RestIdentifier.parse(parts.get(parts.size() - 2), "predecessors").sql()
            : call.identifier("schema").sql();
        final String database = parts.size() == 3 ? RestIdentifier.parse(parts.get(0), "predecessors").sql()
            : call.databaseSql();
        return database + "." + schema + "." + task.sql();
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        final RestIdentifier named = RestJson.identifier(body, "name", false);
        if (named != null && !named.equals(name)) {
            throw RestException.badRequest("The body names task '" + named + "' but the path names '" + name + "'.");
        }
        final RestRow existing = row(call, name);
        if (existing == null) {
            return create(call, RestCreateMode.ERROR_IF_EXISTS, body, name);
        }
        final String qualified = call.qualifiedSql(name);
        final List<String> wanted = RestJson.strings(body, "predecessors");
        final List<String> current = new ArrayList<>();
        final JsonNode currentPredecessors = parse(existing.nonEmpty("predecessors"));
        if (currentPredecessors != null && currentPredecessors.isArray()) {
            for (final JsonNode predecessor : currentPredecessors.values()) {
                current.add(QualifiedName.parse(predecessor.asString()).last());
            }
        }
        final List<String> wantedNames = new ArrayList<>();
        final Map<String, String> wantedSql = new HashMap<>();
        if (wanted != null) {
            for (final String predecessor : wanted) {
                final String sql = taskName(call, predecessor);
                wantedNames.add(QualifiedName.parse(sql).last());
                wantedSql.put(QualifiedName.parse(sql).last(), sql);
            }
        }
        final List<String> removed = new ArrayList<>();
        for (final String predecessor : current) {
            if (!wantedNames.contains(predecessor)) {
                removed.add(call.databaseSql() + "." + call.identifier("schema").sql() + "."
                    + RestIdentifier.parse(RestIdentifier.display(predecessor), "predecessors").sql());
            }
        }
        final List<String> added = new ArrayList<>();
        for (final String predecessor : wantedNames) {
            if (!current.contains(predecessor)) {
                added.add(wantedSql.get(predecessor));
            }
        }
        final JsonNode relations = parse(existing.nonEmpty("task_relations"));
        final boolean finalizer = relations != null && relations.has("FinalizedRootTask");
        final RestIdentifier finalize = RestJson.identifier(body, "finalize", false);
        String status = RestResponse.DEFAULT_STATUS;
        if (finalizer && finalize == null) {
            status = call.sql().status("ALTER TASK " + qualified + " UNSET FINALIZE");
        }
        if (!removed.isEmpty()) {
            status = call.sql().status("ALTER TASK " + qualified + " REMOVE AFTER " + String.join(", ", removed));
        }
        final String condition = RestJson.text(body, "condition");
        final String currentCondition = existing.nonEmpty("condition");
        if (condition == null && currentCondition != null) {
            status = call.sql().status("ALTER TASK " + qualified + " REMOVE WHEN");
        }
        final List<String> unset = new ArrayList<>();
        for (final String[] property : UNSETTABLE) {
            if (!given(body, property[0]) && !unset.contains(property[1])) {
                unset.add(property[1]);
            }
        }
        if (!unset.isEmpty()) {
            status = call.sql().status("ALTER TASK " + qualified + " UNSET " + String.join(", ", unset));
        }
        final String set = options(body, true);
        if (!set.isEmpty()) {
            status = call.sql().status("ALTER TASK " + qualified + " SET" + set);
        }
        if (!added.isEmpty()) {
            status = call.sql().status("ALTER TASK " + qualified + " ADD AFTER " + String.join(", ", added));
        }
        if (finalize != null) {
            status = call.sql().status("ALTER TASK " + qualified + " SET FINALIZE = " + finalize.sql());
        }
        final RestIdentifier runAs = RestJson.identifier(body, "execute_as_user", false);
        if (runAs != null) {
            status = call.sql().status("ALTER TASK " + qualified + " SET EXECUTE AS USER " + runAs.sql());
        } else if (existing.nonEmpty("execute_as_user") != null) {
            status = call.sql().status("ALTER TASK " + qualified + " UNSET EXECUTE AS USER");
        }
        final String definition = RestJson.text(body, "definition");
        if (definition != null && !definition.equals(existing.string("definition"))) {
            status = call.sql().status("ALTER TASK " + qualified + " MODIFY AS " + definition);
        }
        if (condition != null && !condition.equals(currentCondition)) {
            status = call.sql().status("ALTER TASK " + qualified + " MODIFY WHEN " + condition);
        }
        return RestResponse.success(status);
    }

    // ---- graphs -----------------------------------------------------------------------------------------

    /** The task and its dependents, read through TASK_DEPENDENTS and shaped from their SHOW TASKS rows. */
    private static RestResponse dependents(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        // A task that is not there is a 404 here, before the function would refuse its argument.
        fetch(call, name);
        final boolean recursive = call.flag("recursive", true);
        final List<RestRow> dependents = call.sql().show("SELECT * FROM TABLE("
            + SchemaObjectNames.informationSchemaDatabase(call) + ".INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => "
            + RestSql.literal(SchemaObjectNames.dotted(call, name))
            + ", RECURSIVE => " + (recursive ? "TRUE" : "FALSE") + "))");
        final Map<String, RestRow> tasks = new HashMap<>();
        for (final RestRow row : call.sql().show("SHOW TASKS IN SCHEMA " + call.schemaSql())) {
            tasks.put(row.string("name"), row);
        }
        final ArrayNode out = RestJson.array();
        for (final RestRow dependent : dependents) {
            final RestRow row = tasks.get(dependent.string("name"));
            if (row != null) {
                out.add(toJson(row));
            }
        }
        return RestResponse.json(200, out);
    }

    /** The graph runs of the task's graph, from CURRENT_TASK_GRAPHS or COMPLETE_TASK_GRAPHS. */
    private static RestResponse graphs(final RestCall call, final boolean complete) {
        final RestIdentifier name = call.identifier("name");
        final StringBuilder args = new StringBuilder("ROOT_TASK_NAME => ").append(name.literal());
        final Long limit = call.integer("resultLimit");
        if (limit != null) {
            args.append(", RESULT_LIMIT => ").append(limit.longValue());
        }
        if (complete && call.flag("errorOnly", false)) {
            args.append(", ERROR_ONLY => TRUE");
        }
        final String function = complete ? "COMPLETE_TASK_GRAPHS" : "CURRENT_TASK_GRAPHS";
        final String database = call.identifier("database").name();
        final String schema = call.identifier("schema").name();
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SELECT * FROM TABLE("
                + SchemaObjectNames.informationSchemaDatabase(call) + ".INFORMATION_SCHEMA." + function + "(" + args + "))")) {
            if (!database.equals(row.string("DATABASE_NAME")) || !schema.equals(row.string("SCHEMA_NAME"))
                    || !name.name().equals(row.string("ROOT_TASK_NAME"))) {
                continue;
            }
            final ObjectNode run = RestJson.object();
            RestJson.name(run, "root_task_name", row, "ROOT_TASK_NAME");
            RestJson.name(run, "database_name", row, "DATABASE_NAME");
            RestJson.name(run, "schema_name", row, "SCHEMA_NAME");
            RestJson.string(run, "state", row, "STATE");
            RestJson.name(run, "first_error_task_name", row, "FIRST_ERROR_TASK_NAME");
            // A run without an error answers code 0 here, where the function lists no code.
            final Long errorCode = row.integer("FIRST_ERROR_CODE");
            run.put("first_error_code", errorCode == null ? 0L : errorCode.longValue());
            RestJson.string(run, "first_error_message", row, "FIRST_ERROR_MESSAGE");
            RestJson.timestamp(run, "scheduled_time", row, "SCHEDULED_TIME");
            RestJson.timestamp(run, "query_start_time", row, "QUERY_START_TIME");
            RestJson.timestamp(run, "next_scheduled_time", row, "NEXT_SCHEDULED_TIME");
            RestJson.timestamp(run, "completed_time", row, "COMPLETED_TIME");
            RestJson.string(run, "root_task_id", row, "ROOT_TASK_ID");
            RestJson.integer(run, "graph_version", row, "GRAPH_VERSION");
            // The API's run_id is a 32-bit integer: the account answers the RUN_ID's low 32 bits.
            final Long runId = row.integer("RUN_ID");
            RestJson.put(run, "run_id", runId == null ? null : Long.valueOf(runId.intValue()));
            out.add(run);
        }
        return RestResponse.json(200, out);
    }
}
