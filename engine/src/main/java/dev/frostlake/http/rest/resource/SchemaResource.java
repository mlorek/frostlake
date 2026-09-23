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

/**
 * Schemas ({@code schema.yaml}, {@code /api/v2/databases/{database}/schemas}): list, create, {@code :clone},
 * {@code :undrop}, fetch, create-or-alter, delete and the tag endpoints.
 *
 * <p>A schema is read from {@code SHOW SCHEMAS … IN DATABASE} and its parameters from
 * {@code SHOW PARAMETERS IN SCHEMA}; {@code managed_access} is the {@code MANAGED ACCESS} its {@code options} cell
 * spells. {@code PUT} is a create-or-alter done here: the schema is created when it is absent, and otherwise set
 * to the body, one ALTER per property, with {@code ENABLE | DISABLE MANAGED ACCESS} for {@code managed_access}.
 */
public final class SchemaResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas";
    private static final String ITEM = COLLECTION + "/{name}";

    /** Properties only a schema takes, beside the shared ones. */
    private static final String[][] SCHEMA_PROPERTIES = {
        {"pipe_execution_paused", "PIPE_EXECUTION_PAUSED", ContainerRest.BOOLEAN, ContainerRest.IGNORED},
        {"classification_profile", "CLASSIFICATION_PROFILE", ContainerRest.TEXT},
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listSchemas", this);
        router.add("POST", COLLECTION, "createSchema", this);
        router.add("POST", ITEM + ":clone", "cloneSchema", this);
        router.add("POST", ITEM + ":undrop", "undropSchema", this);
        router.add("GET", ITEM, "fetchSchema", this);
        router.add("PUT", ITEM, "createOrAlterSchema", this);
        router.add("DELETE", ITEM, "deleteSchema", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            final RestIdentifier database = call.identifier("database");
            return RestTags.handle(call, "ALTER SCHEMA", qualified(call, name), "SCHEMA",
                RestIdentifier.display(database.name()) + "." + RestIdentifier.display(name.name()), database.sql());
        }
        switch (call.operation()) {
            case "listSchemas":
                return list(call);
            case "createSchema":
                return create(call);
            case "cloneSchema":
                return cloneSchema(call);
            case "undropSchema":
                return call.sql().action("UNDROP SCHEMA " + qualified(call, call.identifier("name")));
            case "fetchSchema":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterSchema":
                return createOrAlter(call);
            case "deleteSchema":
                return call.sql().action("DROP SCHEMA" + call.ifExists() + " "
                    + qualified(call, call.identifier("name")) + ContainerRest.dropBehavior(call));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    /** {@code {database}.<name>} as SQL. */
    private static String qualified(final RestCall call, final RestIdentifier name) {
        return call.databaseSql() + "." + name.sql();
    }

    private static RestResponse list(final RestCall call) {
        final String history = call.flag("history", false) ? " HISTORY" : "";
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW SCHEMAS" + history + RestShow.like(call) + " IN DATABASE "
                + call.databaseSql() + RestShow.tail(call))) {
            // SHOW PARAMETERS cannot list a dropped schema: its row reports the defaults.
            final List<RestRow> parameters = row.timestamp("dropped_on") != null ? ContainerRest.defaults(true, null)
                : call.sql().show("SHOW PARAMETERS IN SCHEMA " + call.databaseSql() + "."
                    + RestIdentifier.quote(row.string("name")));
            out.add(toJson(row, parameters));
        }
        return RestResponse.json(200, out);
    }

    /** The schema as the {@code Schema} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = existing(call, name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Schema '" + call.identifier("database") + "." + name
                + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0), call.sql().show("SHOW PARAMETERS IN SCHEMA " + qualified(call, name)));
    }

    private static List<RestRow> existing(final RestCall call, final RestIdentifier name) {
        return call.sql().showNamed("SHOW SCHEMAS" + RestShow.likeName(name) + " IN DATABASE " + call.databaseSql(),
            name);
    }

    private static ObjectNode toJson(final RestRow row, final List<RestRow> parameters) {
        final ObjectNode node = RestJson.object();
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "name", row, "name");
        node.put("kind", ContainerRest.kindOf(row));
        RestJson.bool(node, "is_default", row, "is_default");
        RestJson.bool(node, "is_current", row, "is_current");
        RestJson.string(node, "database_name", row, "database_name");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "comment", row, "comment");
        RestJson.put(node, "options", row.string("options"));
        node.put("managed_access", managed(row));
        RestJson.integer(node, "retention_time", row, "retention_time");
        RestJson.timestamp(node, "dropped_on", row, "dropped_on");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.string(node, "budget", row, "budget");
        ContainerRest.parameters(node, parameters, ContainerRest.PROPERTIES);
        ContainerRest.parameters(node, parameters, SCHEMA_PROPERTIES);
        RestJson.nulls(node, "object_visibility");
        return node;
    }

    /** Whether a SHOW SCHEMAS row's options cell spells MANAGED ACCESS. */
    private static boolean managed(final RestRow row) {
        final String options = row.string("options");
        return options != null && options.contains("MANAGED ACCESS");
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + ContainerRest.transientWord(call,
            body) + " SCHEMA" + mode.ifNotExists() + " " + qualified(call, name));
        properties(sql, body);
        return call.sql().action(sql.toString());
    }

    private static RestResponse cloneSchema(final RestCall call) {
        final RestIdentifier source = call.identifier("name");
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final String target = call.query("targetDatabase");
        final String targetDatabase = target == null || target.isEmpty() ? call.databaseSql()
            : RestIdentifier.parse(target, "targetDatabase").sql();
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + ContainerRest.transientWord(call,
            body) + " SCHEMA" + mode.ifNotExists() + " " + targetDatabase + "." + name.sql() + " CLONE "
            + qualified(call, source) + ContainerRest.pointOfTime(body));
        properties(sql, body);
        return call.sql().action(sql.toString());
    }

    /** Appends WITH MANAGED ACCESS and every property the body sets. */
    private static void properties(final RestStatement sql, final JsonNode body) {
        final Boolean managed = RestJson.bool(body, "managed_access");
        if (managed != null && managed.booleanValue()) {
            sql.append(" WITH MANAGED ACCESS");
        }
        ContainerRest.append(sql, body, ContainerRest.PROPERTIES);
        ContainerRest.append(sql, body, SCHEMA_PROPERTIES);
        sql.string(body, "comment", "COMMENT");
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        final RestIdentifier named = RestJson.identifier(body, "name", false);
        if (named != null && !named.equals(name)) {
            throw RestException.badRequest("The body names schema '" + named + "' but the path names '" + name
                + "'.");
        }
        final List<RestRow> existing = existing(call, name);
        if (existing.isEmpty()) {
            final RestStatement sql = new RestStatement("CREATE" + ContainerRest.transientWord(call, body)
                + " SCHEMA " + qualified(call, name));
            properties(sql, body);
            return call.sql().action(sql.toString());
        }
        final RestRow row = existing.get(0);
        final String kind = ContainerRest.kind(call, body);
        if (kind != null && !kind.equals(ContainerRest.kindOf(row))) {
            throw RestException.badRequest("Schema '" + name + "' is " + ContainerRest.kindOf(row)
                + "; its kind cannot be changed to " + kind + ".");
        }
        final String alterHead = "ALTER SCHEMA " + qualified(call, name);
        final List<RestRow> parameters = call.sql().show("SHOW PARAMETERS IN SCHEMA " + qualified(call, name));
        final String[][] properties = new String[ContainerRest.PROPERTIES.length + SCHEMA_PROPERTIES.length][];
        System.arraycopy(ContainerRest.PROPERTIES, 0, properties, 0, ContainerRest.PROPERTIES.length);
        System.arraycopy(SCHEMA_PROPERTIES, 0, properties, ContainerRest.PROPERTIES.length,
            SCHEMA_PROPERTIES.length);
        String status = ContainerRest.alter(call, alterHead, body, properties, parameters, "SCHEMA",
            row.string("comment"));
        final Boolean managed = RestJson.bool(body, "managed_access");
        final boolean wanted = managed != null && managed.booleanValue();
        if (wanted != managed(row)) {
            status = call.sql().status(alterHead + (wanted ? " ENABLE" : " DISABLE") + " MANAGED ACCESS");
        }
        return RestResponse.success(status);
    }
}
