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
import dev.frostlake.task.IntervalSchedule;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Alerts ({@code alert.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/alerts}): list, create, fetch,
 * delete, {@code :clone}, {@code :execute}, and the tag endpoints.
 *
 * <p>An alert is read from {@code SHOW ALERTS IN SCHEMA}. Its schedule is written and read in the two shapes the
 * {@code Schedule} schema names: {@code CRON_TYPE} ({@code USING CRON <cron_expr> <timezone>}) and
 * {@code SCHEDULE_TYPE} ({@code <minutes> MINUTE}); an interval written in seconds or hours reads back as its whole
 * minutes. The condition and the action are SQL text the body carries, placed into the
 * {@code IF (EXISTS (…)) THEN …} of the CREATE as written. Alert templates are not provided.
 */
public final class AlertResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/alerts";
    private static final String ITEM = COLLECTION + "/{name}";

    /** A CRON schedule: the five fields, then the time zone. */
    private static final Pattern CRON = Pattern.compile("\\s*USING\\s+CRON\\s+(.+?)\\s+(\\S+)\\s*",
        Pattern.CASE_INSENSITIVE);

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listAlerts", this);
        router.add("POST", COLLECTION, "createAlert", this);
        router.add("GET", ITEM, "fetchAlert", this);
        router.add("DELETE", ITEM, "deleteAlert", this);
        router.add("POST", ITEM + ":clone", "cloneAlert", this);
        router.add("POST", ITEM + ":execute", "executeAlert", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER ALERT", call.qualifiedSql(name), "ALERT",
                call.identifier("database") + "." + call.identifier("schema") + "." + name, call.databaseSql());
        }
        switch (call.operation()) {
            case "listAlerts":
                return list(call);
            case "createAlert":
                return create(call);
            case "fetchAlert":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteAlert":
                return call.sql().action("DROP ALERT" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            case "cloneAlert":
                return cloneAlert(call);
            case "executeAlert":
                return call.sql().action("EXECUTE ALERT " + call.qualifiedSql(call.identifier("name")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW ALERTS" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            out.add(toJson(row));
        }
        return RestResponse.json(200, out);
    }

    /** The alert as the {@code Alert} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW ALERTS" + RestShow.likeName(name) + " IN SCHEMA "
            + call.schemaSql(), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Alert '" + call.identifier("database") + "." + call.identifier("schema")
                + "." + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0));
    }

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        // An alert without a comment lists an empty one, which the account answers as null.
        RestJson.string(node, "comment", row, "comment");
        final ObjectNode schedule = schedule(row.nonEmpty("schedule"));
        if (schedule != null) {
            node.set("schedule", schedule);
        }
        final String warehouse = row.nonEmpty("warehouse");
        if (warehouse != null) {
            node.put("warehouse", RestIdentifier.display(warehouse));
        }
        final String config = row.nonEmpty("config");
        if (config != null) {
            try {
                final JsonNode parsed = RestJson.mapper().readTree(config);
                if (parsed.isObject()) {
                    node.set("config", parsed);
                }
            } catch (final JacksonException notJson) {
                // a configuration that is no JSON object is left out
            }
        }
        if (!node.has("config")) {
            // The account answers an alert without a configuration with an empty one.
            node.set("config", RestJson.object());
        }
        RestJson.put(node, "condition", row.string("condition"));
        RestJson.put(node, "action", row.string("action"));
        RestJson.string(node, "runbook", row, "runbook");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "state", row, "state");
        RestJson.nulls(node, "name", "comment", "schedule", "warehouse", "config", "condition", "action", "runbook",
            "suspend_alert_after_num_failures", "template", "measurement", "evaluation", "created_on",
            "database_name", "schema_name", "owner", "owner_role_type", "state");
        return node;
    }

    /** A schedule as SHOW ALERTS reports it, in the {@code Schedule} schema's shape, or null. */
    private static ObjectNode schedule(final String text) {
        if (text == null) {
            return null;
        }
        final ObjectNode node = RestJson.object();
        final long seconds = IntervalSchedule.seconds(text);
        if (seconds > 0L) {
            // The account reads an interval back in whole minutes: 90 SECONDS is one minute, 45 S none.
            node.put("schedule_type", "SCHEDULE_TYPE");
            node.put("minutes", seconds / 60L);
            return node;
        }
        final Matcher cron = CRON.matcher(text);
        if (cron.matches()) {
            node.put("schedule_type", "CRON_TYPE");
            node.put("cron_expr", cron.group(1));
            node.put("timezone", cron.group(2));
            return node;
        }
        return null;
    }

    /** The body's {@code Schedule} as the SQL {@code SCHEDULE} string. */
    private static String scheduleSql(final JsonNode body) {
        final JsonNode schedule = body.get("schedule");
        if (schedule == null || schedule.isNull()) {
            return null;
        }
        if (!schedule.isObject()) {
            throw RestException.badRequest("Property 'schedule' must be an object.");
        }
        final String type = RestJson.text(schedule, "schedule_type");
        if ("CRON_TYPE".equalsIgnoreCase(type)) {
            final String expression = RestJson.text(schedule, "cron_expr");
            final String timezone = RestJson.text(schedule, "timezone");
            if (expression == null || timezone == null) {
                throw RestException.badRequest("A CRON_TYPE schedule needs 'cron_expr' and 'timezone'.");
            }
            return "USING CRON " + expression + " " + timezone;
        }
        if ("SCHEDULE_TYPE".equalsIgnoreCase(type) || "MINUTES_TYPE".equalsIgnoreCase(type)) {
            final Long minutes = RestJson.integer(schedule, "minutes");
            if (minutes == null) {
                throw RestException.badRequest("A SCHEDULE_TYPE schedule needs 'minutes'.");
            }
            return minutes + " MINUTE";
        }
        throw RestException.badRequest("Invalid value '" + type + "' for property 'schedule_type': expected "
            + "CRON_TYPE or SCHEDULE_TYPE.");
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        if (RestJson.present(body, "template")) {
            throw RestException.notImplemented("Alert templates are not provided: an alert needs a condition and "
                + "an action.");
        }
        final String condition = RestJson.text(body, "condition");
        final String action = RestJson.text(body, "action");
        if (condition == null || action == null) {
            throw RestException.badRequest("Missing required property '" + (condition == null ? "condition"
                : "action") + "'.");
        }
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " ALERT" + mode.ifNotExists() + " "
            + call.qualifiedSql(name));
        final String schedule = scheduleSql(body);
        if (schedule != null) {
            sql.property("SCHEDULE", RestSql.literal(schedule));
        }
        sql.identifier(body, "warehouse", "WAREHOUSE");
        sql.string(body, "comment", "COMMENT");
        final JsonNode config = body.get("config");
        // An empty object is how a fetched alert without a configuration reads back: it sets none.
        if (config != null && !config.isNull() && !(config.isObject() && config.isEmpty())) {
            sql.property("CONFIG", RestSql.literal(config.isString() ? config.asString() : config.toString()));
        }
        sql.string(body, "runbook", "RUNBOOK");
        sql.integer(body, "suspend_alert_after_num_failures", "SUSPEND_ALERT_AFTER_NUM_FAILURES");
        sql.append(" IF (EXISTS (" + condition + ")) THEN " + action);
        return call.sql().action(sql.toString());
    }

    private static RestResponse cloneAlert(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier target = RestJson.identifier(body, "name", true);
        if (RestJson.present(body, "point_of_time")) {
            throw RestException.notImplemented("Cloning an alert at a point in time is not provided.");
        }
        final String targetDatabase = call.query("targetDatabase");
        final String targetSchema = call.query("targetSchema");
        final String targetSql = (targetDatabase != null ? RestIdentifier.parse(targetDatabase, "targetDatabase")
            : call.identifier("database")).sql() + "."
            + (targetSchema != null ? RestIdentifier.parse(targetSchema, "targetSchema")
                : call.identifier("schema")).sql() + "." + target.sql();
        final RestCreateMode mode = call.createMode();
        return call.sql().action("CREATE" + mode.orReplace() + " ALERT" + mode.ifNotExists() + " " + targetSql
            + " CLONE " + call.qualifiedSql(call.identifier("name")));
    }
}
