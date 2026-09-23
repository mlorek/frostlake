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

/**
 * Account roles ({@code role.yaml}, {@code /api/v2/roles}): list, create, delete, the role's grants (listed,
 * granted, revoked with {@code mode} RESTRICT or CASCADE), who holds it ({@code grants-of}), the grants on it
 * ({@code grants-on}), its future grants, and the tag endpoints. A role is read from {@code SHOW ROLES}; the grants
 * endpoints translate into GRANT, REVOKE, SHOW GRANTS and SHOW FUTURE GRANTS (see {@link GrantRequests}).
 */
public final class RoleResource implements RestResource {

    private static final String COLLECTION = "/api/v2/roles";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listRoles", this);
        router.add("POST", COLLECTION, "createRole", this);
        router.add("DELETE", ITEM, "deleteRole", this);
        router.add("GET", ITEM + "/grants", "listGrants", this);
        router.add("POST", ITEM + "/grants", "grantPrivileges", this);
        router.add("POST", ITEM + "/grants:revoke", "revokeGrants", this);
        router.add("GET", ITEM + "/grants-of", "listGrantsOf", this);
        router.add("GET", ITEM + "/grants-on", "listGrantsOn", this);
        router.add("GET", ITEM + "/future-grants", "listFutureGrants", this);
        router.add("POST", ITEM + "/future-grants", "grantFuturePrivileges", this);
        router.add("POST", ITEM + "/future-grants:revoke", "revokeFutureGrants", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER ROLE", name.sql(), "ROLE", RestIdentifier.display(name.name()),
                "SNOWFLAKE");
        }
        switch (call.operation()) {
            case "listRoles":
                return list(call);
            case "createRole":
                return create(call);
            case "deleteRole":
                return call.sql().action("DROP ROLE" + call.ifExists() + " " + call.identifier("name").sql());
            case "listGrants":
                return GrantRequests.list(call, call.sql().show("SHOW GRANTS TO ROLE " + role(call)), false);
            case "grantPrivileges":
                GrantRequests.requirePrivilegesForRoleGrant(call.body());
                return call.sql().action(GrantRequests.grant(call.body(), "ROLE " + role(call), false));
            case "revokeGrants":
                return call.sql().action(GrantRequests.revoke(call.body(), "ROLE " + role(call), false,
                    GrantRequests.mode(call, "mode")));
            case "listGrantsOf":
                return grantsOf(call, call.sql().show("SHOW GRANTS OF ROLE " + role(call)));
            case "listGrantsOn":
                return grantsOn(call, call.sql().show("SHOW GRANTS ON ROLE " + role(call)));
            case "listFutureGrants":
                return GrantRequests.list(call, call.sql().show("SHOW FUTURE GRANTS TO ROLE " + role(call)), true);
            case "grantFuturePrivileges":
                return call.sql().action(GrantRequests.grant(call.body(), "ROLE " + role(call), true));
            case "revokeFutureGrants":
                return call.sql().action(GrantRequests.revoke(call.body(), "ROLE " + role(call), true,
                    GrantRequests.mode(call, "mode")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static String role(final RestCall call) {
        return call.identifier("name").sql();
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW ROLES" + RestShow.like(call) + RestShow.tail(call))) {
            final ObjectNode node = RestJson.object();
            RestJson.name(node, "name", row, "name");
            // A role without a comment lists an empty one, which the account answers as null.
            RestJson.string(node, "comment", row, "comment");
            RestJson.timestamp(node, "created_on", row, "created_on");
            RestJson.string(node, "owner", row, "owner");
            RestJson.bool(node, "is_default", row, "is_default");
            RestJson.bool(node, "is_current", row, "is_current");
            RestJson.bool(node, "is_inherited", row, "is_inherited");
            RestJson.integer(node, "assigned_to_users", row, "assigned_to_users");
            RestJson.integer(node, "granted_to_roles", row, "granted_to_roles");
            RestJson.integer(node, "granted_roles", row, "granted_roles");
            out.add(node);
        }
        return RestResponse.json(200, out);
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " ROLE" + mode.ifNotExists() + " "
            + name.sql());
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }

    /** SHOW GRANTS OF ROLE as {@code GrantOf} objects. */
    static RestResponse grantsOf(final RestCall call, final Iterable<RestRow> rows) {
        final Long limit = call.integer("showLimit");
        final ArrayNode out = RestJson.array();
        for (final RestRow row : rows) {
            if (limit != null && out.size() >= limit.longValue()) {
                break;
            }
            final ObjectNode node = RestJson.object();
            RestJson.timestamp(node, "created_on", row, "created_on");
            RestJson.string(node, "role", row, "role");
            RestJson.string(node, "granted_to", row, "granted_to");
            RestJson.string(node, "grantee_name", row, "grantee_name");
            RestJson.string(node, "granted_by", row, "granted_by");
            out.add(node);
        }
        return RestResponse.json(200, out);
    }

    /** SHOW GRANTS ON ROLE as {@code GrantOn} objects. */
    private static RestResponse grantsOn(final RestCall call, final Iterable<RestRow> rows) {
        final Long limit = call.integer("showLimit");
        final ArrayNode out = RestJson.array();
        for (final RestRow row : rows) {
            if (limit != null && out.size() >= limit.longValue()) {
                break;
            }
            final ObjectNode node = RestJson.object();
            RestJson.timestamp(node, "created_on", row, "created_on");
            RestJson.string(node, "privilege", row, "privilege");
            RestJson.string(node, "granted_on", row, "granted_on");
            RestJson.string(node, "name", row, "name");
            RestJson.string(node, "granted_to", row, "granted_to");
            RestJson.string(node, "grantee_name", row, "grantee_name");
            RestJson.string(node, "grant_option", row, "grant_option");
            RestJson.string(node, "granted_by", row, "granted_by");
            RestJson.string(node, "granted_by_role_type", row, "granted_by_role_type");
            out.add(node);
        }
        return RestResponse.json(200, out);
    }
}
