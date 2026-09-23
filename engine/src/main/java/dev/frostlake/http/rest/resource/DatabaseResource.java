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
 * Databases ({@code database.yaml}, {@code /api/v2/databases}): list, create, {@code :clone}, fetch,
 * create-or-alter, delete, {@code :undrop} and the tag endpoints.
 *
 * <p>A database is read from {@code SHOW DATABASES} and its parameters from {@code SHOW PARAMETERS IN DATABASE}.
 * {@code PUT} is a create-or-alter done here: the database is created when it is absent, and otherwise set to the
 * body, one ALTER per property. Frostlake has no shares and no replication, so creating a database from a share
 * and the six replication and failover endpoints answer {@code 501}.
 */
public final class DatabaseResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases";
    private static final String ITEM = COLLECTION + "/{name}";

    /** Properties only a database takes, beside the shared ones. */
    private static final String[][] DATABASE_PROPERTIES = {
        {"catalog_sync_namespace_mode", "CATALOG_SYNC_NAMESPACE_MODE", ContainerRest.TEXT},
        {"catalog_sync_namespace_flatten_delimiter", "CATALOG_SYNC_NAMESPACE_FLATTEN_DELIMITER", ContainerRest.TEXT},
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listDatabases", this);
        router.add("POST", COLLECTION, "createDatabase", this);
        router.add("POST", COLLECTION + ":from-share", "createDatabaseFromShare", this);
        router.add("POST", ITEM + ":from_share", "createDatabaseFromShareDeprecated", this);
        router.add("POST", ITEM + ":clone", "cloneDatabase", this);
        router.add("GET", ITEM, "fetchDatabase", this);
        router.add("PUT", ITEM, "createOrAlterDatabase", this);
        router.add("DELETE", ITEM, "deleteDatabase", this);
        router.add("POST", ITEM + ":undrop", "undropDatabase", this);
        router.add("POST", ITEM + "/replication:enable", "enableDatabaseReplication", this);
        router.add("POST", ITEM + "/replication:disable", "disableDatabaseReplication", this);
        router.add("POST", ITEM + "/replication:refresh", "refreshDatabaseReplication", this);
        router.add("POST", ITEM + "/failover:enable", "enableDatabaseFailover", this);
        router.add("POST", ITEM + "/failover:disable", "disableDatabaseFailover", this);
        router.add("POST", ITEM + "/failover:primary", "primaryDatabaseFailover", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER DATABASE", name.sql(), "DATABASE",
                RestIdentifier.display(name.name()), name.sql());
        }
        switch (call.operation()) {
            case "listDatabases":
                return list(call);
            case "createDatabase":
                return create(call);
            case "cloneDatabase":
                return cloneDatabase(call);
            case "fetchDatabase":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterDatabase":
                return createOrAlter(call);
            case "deleteDatabase":
                return call.sql().action("DROP DATABASE" + call.ifExists() + " " + call.identifier("name").sql()
                    + ContainerRest.dropBehavior(call));
            case "undropDatabase":
                return call.sql().action("UNDROP DATABASE " + call.identifier("name").sql());
            case "createDatabaseFromShare":
            case "createDatabaseFromShareDeprecated":
                throw RestException.notImplemented("Frostlake has no shares, so a database cannot be created from "
                    + "a share.");
            case "enableDatabaseReplication":
            case "disableDatabaseReplication":
            case "refreshDatabaseReplication":
                throw RestException.notImplemented("Frostlake has no database replication, so replication cannot "
                    + "be enabled, disabled or refreshed.");
            case "enableDatabaseFailover":
            case "disableDatabaseFailover":
            case "primaryDatabaseFailover":
                throw RestException.notImplemented("Frostlake has no database replication, so failover cannot be "
                    + "enabled, disabled or promoted.");
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final String history = call.flag("history", false) ? " HISTORY" : "";
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW DATABASES" + history + RestShow.like(call)
                + RestShow.tail(call))) {
            // SHOW PARAMETERS cannot list a dropped database: its row reports the defaults and its retention.
            final List<RestRow> parameters = row.timestamp("dropped_on") != null
                ? ContainerRest.defaults(false, row.integer("retention_time"))
                : call.sql().show("SHOW PARAMETERS IN DATABASE " + RestIdentifier.quote(row.string("name")));
            out.add(toJson(row, parameters));
        }
        return RestResponse.json(200, out);
    }

    /** The database as the {@code Database} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW DATABASES" + RestShow.likeName(name), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("Database '" + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0), call.sql().show("SHOW PARAMETERS IN DATABASE " + name.sql()));
    }

    private static ObjectNode toJson(final RestRow row, final List<RestRow> parameters) {
        final ObjectNode node = RestJson.object();
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "name", row, "name");
        node.put("kind", ContainerRest.kindOf(row));
        RestJson.bool(node, "is_default", row, "is_default");
        RestJson.bool(node, "is_current", row, "is_current");
        RestJson.put(node, "origin", row.string("origin"));
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "comment", row, "comment");
        RestJson.put(node, "options", row.string("options"));
        RestJson.integer(node, "retention_time", row, "retention_time");
        RestJson.timestamp(node, "dropped_on", row, "dropped_on");
        RestJson.string(node, "budget", row, "budget");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        ContainerRest.parameters(node, parameters, ContainerRest.PROPERTIES);
        RestJson.nulls(node, "classification_profile", "catalog_sync_namespace_mode",
            "catalog_sync_namespace_flatten_delimiter", "object_visibility");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + ContainerRest.transientWord(call,
            body) + " DATABASE" + mode.ifNotExists() + " " + name.sql());
        properties(sql, body);
        return call.sql().action(sql.toString());
    }

    private static RestResponse cloneDatabase(final RestCall call) {
        final RestIdentifier source = call.identifier("name");
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + ContainerRest.transientWord(call,
            body) + " DATABASE" + mode.ifNotExists() + " " + name.sql() + " CLONE " + source.sql()
            + ContainerRest.pointOfTime(body));
        properties(sql, body);
        return call.sql().action(sql.toString());
    }

    /** Appends every property the body sets. */
    private static void properties(final RestStatement sql, final JsonNode body) {
        ContainerRest.append(sql, body, ContainerRest.PROPERTIES);
        ContainerRest.append(sql, body, DATABASE_PROPERTIES);
        sql.string(body, "comment", "COMMENT");
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        final RestIdentifier named = RestJson.identifier(body, "name", false);
        if (named != null && !named.equals(name)) {
            throw RestException.badRequest("The body names database '" + named + "' but the path names '" + name
                + "'.");
        }
        final List<RestRow> existing = call.sql().showNamed("SHOW DATABASES" + RestShow.likeName(name), name);
        if (existing.isEmpty()) {
            final RestStatement sql = new RestStatement("CREATE" + ContainerRest.transientWord(call, body)
                + " DATABASE " + name.sql());
            properties(sql, body);
            return call.sql().action(sql.toString());
        }
        final String kind = ContainerRest.kind(call, body);
        if (kind != null && !kind.equals(ContainerRest.kindOf(existing.get(0)))) {
            throw RestException.badRequest("Database '" + name + "' is " + ContainerRest.kindOf(existing.get(0))
                + "; its kind cannot be changed to " + kind + ".");
        }
        final List<RestRow> parameters = call.sql().show("SHOW PARAMETERS IN DATABASE " + name.sql());
        final String[][] properties = new String[ContainerRest.PROPERTIES.length + DATABASE_PROPERTIES.length][];
        System.arraycopy(ContainerRest.PROPERTIES, 0, properties, 0, ContainerRest.PROPERTIES.length);
        System.arraycopy(DATABASE_PROPERTIES, 0, properties, ContainerRest.PROPERTIES.length,
            DATABASE_PROPERTIES.length);
        return RestResponse.success(ContainerRest.alter(call, "ALTER DATABASE " + name.sql(), body, properties,
            parameters, "DATABASE", existing.get(0).string("comment")));
    }
}
