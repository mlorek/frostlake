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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Database roles ({@code database-role.yaml}, {@code /api/v2/databases/{database}/database-roles}): list, create,
 * delete, {@code :clone}, the role's grants and future grants (listed, granted, revoked), and the tag endpoints.
 *
 * <p>A database role is read from {@code SHOW DATABASE ROLES IN DATABASE}. The SQL has no CLONE form for a database
 * role, so {@code :clone} is done here: the copy is created (per {@code createMode}, in {@code targetDatabase} when
 * given) with the source's comment, then granted what the source holds — its privileges with their grant options,
 * the database roles granted to it, and its future grants. Who holds the source is not copied.
 */
public final class DatabaseRoleResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/database-roles";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listDatabaseRoles", this);
        router.add("POST", COLLECTION, "createDatabaseRole", this);
        router.add("DELETE", ITEM, "deleteDatabaseRole", this);
        router.add("POST", ITEM + ":clone", "cloneDatabaseRole", this);
        router.add("GET", ITEM + "/grants", "listGrants", this);
        router.add("POST", ITEM + "/grants", "grantPrivileges", this);
        router.add("POST", ITEM + "/grants:revoke", "revokeGrants", this);
        router.add("GET", ITEM + "/future-grants", "listFutureGrants", this);
        router.add("POST", ITEM + "/future-grants", "grantFuturePrivileges", this);
        router.add("POST", ITEM + "/future-grants:revoke", "revokeFutureGrants", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier database = call.identifier("database");
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER DATABASE ROLE", role(call), "DATABASE ROLE",
                RestIdentifier.display(database.name()) + "." + RestIdentifier.display(name.name()), database.sql());
        }
        switch (call.operation()) {
            case "listDatabaseRoles":
                return list(call);
            case "createDatabaseRole":
                return create(call);
            case "deleteDatabaseRole":
                return call.sql().action("DROP DATABASE ROLE" + call.ifExists() + " " + role(call));
            case "cloneDatabaseRole":
                return cloneRole(call);
            case "listGrants":
                return GrantRequests.list(call, call.sql().show("SHOW GRANTS TO DATABASE ROLE " + role(call)), false);
            case "grantPrivileges":
                return call.sql().action(GrantRequests.grant(call.body(), "DATABASE ROLE " + role(call), false));
            case "revokeGrants":
                return call.sql().action(GrantRequests.revoke(call.body(), "DATABASE ROLE " + role(call), false,
                    GrantRequests.mode(call, "mode")));
            case "listFutureGrants":
                return GrantRequests.list(call,
                    call.sql().show("SHOW FUTURE GRANTS TO DATABASE ROLE " + role(call)), true);
            case "grantFuturePrivileges":
                return call.sql().action(GrantRequests.grant(call.body(), "DATABASE ROLE " + role(call), true));
            case "revokeFutureGrants":
                return call.sql().action(GrantRequests.revoke(call.body(), "DATABASE ROLE " + role(call), true,
                    GrantRequests.mode(call, "mode")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    /** The path's database role as a qualified SQL name. */
    private static String role(final RestCall call) {
        return call.databaseSql() + "." + call.identifier("name").sql();
    }

    private static RestResponse list(final RestCall call) {
        final StringBuilder sql = new StringBuilder("SHOW DATABASE ROLES IN DATABASE ").append(call.databaseSql());
        final Long limit = call.integer("showLimit");
        final String fromName = call.query("fromName");
        if (limit != null && (limit.longValue() < 1 || limit.longValue() > RestShow.MAX_LIMIT)) {
            throw RestException.badRequest("Invalid value " + limit + " for query parameter 'showLimit': "
                + "expected 1 to " + RestShow.MAX_LIMIT + ".");
        }
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

    private static ObjectNode toJson(final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.put(node, "comment", row.string("comment"));
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.integer(node, "granted_to_roles", row, "granted_to_roles");
        RestJson.integer(node, "granted_to_database_roles", row, "granted_to_database_roles");
        RestJson.integer(node, "granted_database_roles", row, "granted_database_roles");
        RestJson.string(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " DATABASE ROLE"
            + mode.ifNotExists() + " " + call.databaseSql() + "." + name.sql());
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }

    private static RestResponse cloneRole(final RestCall call) {
        final RestIdentifier source = call.identifier("name");
        RestRow found = null;
        for (final RestRow row : call.sql().show("SHOW DATABASE ROLES IN DATABASE " + call.databaseSql())) {
            if (source.name().equals(row.string("name"))) {
                found = row;
            }
        }
        if (found == null) {
            throw RestException.notFound("Database role '" + source + "' does not exist or not authorized.");
        }
        final RestIdentifier name = RestJson.identifier(call.body(), "name", true);
        final String targetDatabase = call.query("targetDatabase") == null ? call.databaseSql()
            : RestIdentifier.parse(call.query("targetDatabase"), "targetDatabase").sql();
        final String target = targetDatabase + "." + name.sql();
        final RestCreateMode mode = call.createMode();
        final RestStatement create = new RestStatement("CREATE" + mode.orReplace() + " DATABASE ROLE"
            + mode.ifNotExists() + " " + target);
        final String comment = found.nonEmpty("comment");
        if (comment != null) {
            create.property("COMMENT", RestSql.literal(comment));
        }
        final String status = call.sql().status(create.toString());
        final String grantee = "DATABASE ROLE " + target;
        final List<RestRow> grants = call.sql().show("SHOW GRANTS TO DATABASE ROLE " + role(call));
        for (final RestRow grant : grants) {
            final String kind = grant.string("granted_on").replace('_', ' ');
            final String privilege = grant.string("privilege");
            if ("OWNERSHIP".equals(privilege) || grant.nonEmpty("granted_by") == null) {
                // The role's USAGE on its own database is granted by no one: every database role holds it.
                continue;
            }
            if ("DATABASE ROLE".equals(kind)) {
                call.sql().run("GRANT DATABASE ROLE " + quoted(grant.string("name")) + " TO " + grantee);
            } else if ("ACCOUNT".equals(kind)) {
                call.sql().run("GRANT " + privilege + " ON ACCOUNT TO " + grantee);
            } else {
                call.sql().run("GRANT " + privilege + " ON " + kind + " " + quoted(grant.string("name")) + " TO "
                    + grantee + (Boolean.TRUE.equals(grant.bool("grant_option")) ? " WITH GRANT OPTION" : ""));
            }
        }
        for (final RestRow grant : call.sql().show("SHOW FUTURE GRANTS TO DATABASE ROLE " + role(call))) {
            final List<String> scope = GrantRequests.nameParts(grant.string("name"));
            final String in = scope.size() > 2
                ? "IN SCHEMA " + RestIdentifier.quote(scope.get(0)) + "." + RestIdentifier.quote(scope.get(1))
                : "IN DATABASE " + RestIdentifier.quote(scope.get(0));
            call.sql().run("GRANT " + grant.string("privilege") + " ON FUTURE "
                + GrantRequests.plural(grant.string("grant_on").replace('_', ' ')) + " " + in + " TO " + grantee
                + (Boolean.TRUE.equals(grant.bool("grant_option")) ? " WITH GRANT OPTION" : ""));
        }
        return RestResponse.success(status);
    }

    /** A dotted name from a listing as SQL, each part quoted. */
    private static String quoted(final String listed) {
        final StringBuilder out = new StringBuilder();
        for (final String part : GrantRequests.nameParts(listed)) {
            if (out.length() > 0) {
                out.append('.');
            }
            out.append(RestIdentifier.quote(part));
        }
        return out.toString();
    }
}
