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
import dev.frostlake.http.rest.RestJson;
import dev.frostlake.http.rest.RestResource;
import dev.frostlake.http.rest.RestResponse;
import dev.frostlake.http.rest.RestRouter;
import dev.frostlake.http.rest.RestRow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Grants ({@code grant.yaml}, {@code /api/v2/grants/{granteeType}/{granteeName}}): privileges granted to a role,
 * a user or a database role on a named securable ({@code …/{securableType}/{securableName}/privileges}) or on all
 * or future securables of a kind in a database or schema ({@code …/{bulkGrantType}/{securableTypePlural}/
 * {scopeType}/{scopeName}/privileges}); a privilege revoked, or only its grant option ({@code …/grant-option}),
 * with {@code deleteMode} RESTRICT or CASCADE; and the grantee's grants listed from {@code SHOW GRANTS TO}.
 * Securable names in the path are dotted, {@code db.schema.name}; a database role grantee is {@code db.role}.
 * Shares, applications and application roles are not grantees Frostlake models: those answer {@code 501}.
 */
public final class GrantResource implements RestResource {

    private static final String GRANTEE = "/api/v2/grants/{granteeType}/{granteeName}";
    private static final String NAMED = GRANTEE + "/{securableType}/{securableName}/privileges";
    private static final String BULK = GRANTEE + "/{bulkGrantType}/{securableTypePlural}/{scopeType}/{scopeName}"
        + "/privileges";

    @Override
    public void register(final RestRouter router) {
        router.add("POST", NAMED, "grantPrivilege", this);
        router.add("POST", BULK, "grantGroupPrivilege", this);
        router.add("DELETE", NAMED + "/{privilege}", "revokePrivilege", this);
        router.add("DELETE", NAMED + "/{privilege}/grant-option", "revokePrivilegeGrantOption", this);
        router.add("DELETE", BULK + "/{privilege}", "revokeGroupPrivilege", this);
        router.add("DELETE", BULK + "/{privilege}/grant-option", "revokeGroupPrivilegeGrantOption", this);
        router.add("GET", GRANTEE, "listGrantsTo", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        final String grantee = grantee(call);
        switch (call.operation()) {
            case "grantPrivilege":
                return grantNamed(call, grantee);
            case "grantGroupPrivilege":
                return grantBulk(call, grantee);
            case "revokePrivilege":
                return revokeNamed(call, grantee, false);
            case "revokePrivilegeGrantOption":
                return revokeNamed(call, grantee, true);
            case "revokeGroupPrivilege":
                return revokeBulk(call, grantee, false);
            case "revokeGroupPrivilegeGrantOption":
                return revokeBulk(call, grantee, true);
            case "listGrantsTo":
                return list(call, grantee);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    /** The path's grantee as SQL: {@code ROLE "R"}, {@code USER "U"} or {@code DATABASE ROLE "D"."R"}. */
    private static String grantee(final RestCall call) {
        final String type = GrantRequests.words(call.pathParameter("granteeType").replace('-', ' '), "granteeType");
        final String name = call.pathParameter("granteeName");
        if ("ROLE".equals(type) || "USER".equals(type)) {
            return type + " " + GrantRequests.dotted(name, "granteeName");
        }
        if ("DATABASE ROLE".equals(type)) {
            return type + " " + GrantRequests.dotted(name, "granteeName");
        }
        throw RestException.notImplemented("Grantee type '" + call.pathParameter("granteeType")
            + "' is not provided: Frostlake grants to roles, users and database roles.");
    }

    private static String kind(final RestCall call) {
        return GrantRequests.words(call.pathParameter("securableType"), "securableType");
    }

    /** {@code ON ACCOUNT} or {@code ON <KIND> <name>} for the path's named securable. */
    private static String onNamed(final RestCall call, final String kind) {
        if ("ACCOUNT".equals(kind)) {
            return "ACCOUNT";
        }
        return kind + " " + GrantRequests.dotted(call.pathParameter("securableName"), "securableName");
    }

    /** {@code ALL|FUTURE <KINDS> IN DATABASE|SCHEMA <scope>} for the path's bulk securable. */
    private static String onBulk(final RestCall call) {
        final String bulk = GrantRequests.words(call.pathParameter("bulkGrantType"), "bulkGrantType");
        if (!"ALL".equals(bulk) && !"FUTURE".equals(bulk)) {
            throw RestException.badRequest("Invalid bulkGrantType '" + call.pathParameter("bulkGrantType")
                + "': expected all or future.");
        }
        final String scope = GrantRequests.words(call.pathParameter("scopeType"), "scopeType");
        if (!"DATABASE".equals(scope) && !"SCHEMA".equals(scope)) {
            throw RestException.badRequest("Invalid scopeType '" + call.pathParameter("scopeType")
                + "': expected database or schema.");
        }
        return bulk + " " + GrantRequests.words(call.pathParameter("securableTypePlural"), "securableTypePlural")
            + " IN " + scope + " " + GrantRequests.dotted(call.pathParameter("scopeName"), "scopeName");
    }

    private static boolean grantOption(final JsonNode body) {
        return Boolean.TRUE.equals(RestJson.bool(body, "grant_option"));
    }

    private static RestResponse grantNamed(final RestCall call, final String grantee) {
        final JsonNode body = call.bodyOrEmpty();
        final String kind = kind(call);
        if (("ROLE".equals(kind) || "DATABASE ROLE".equals(kind)) && !RestJson.present(body, "privileges")) {
            return call.sql().action("GRANT " + kind + " " + GrantRequests.dotted(call.pathParameter("securableName"),
                "securableName") + " TO " + grantee);
        }
        return call.sql().action("GRANT " + GrantRequests.privileges(body) + " ON " + onNamed(call, kind) + " TO "
            + grantee + (grantOption(body) ? " WITH GRANT OPTION" : ""));
    }

    private static RestResponse grantBulk(final RestCall call, final String grantee) {
        final JsonNode body = call.bodyOrEmpty();
        return call.sql().action("GRANT " + GrantRequests.privileges(body) + " ON " + onBulk(call) + " TO " + grantee
            + (grantOption(body) ? " WITH GRANT OPTION" : ""));
    }

    private static RestResponse revokeNamed(final RestCall call, final String grantee, final boolean optionOnly) {
        final String kind = kind(call);
        final String mode = GrantRequests.mode(call, "deleteMode");
        if (("ROLE".equals(kind) || "DATABASE ROLE".equals(kind)) && !optionOnly) {
            return call.sql().action("REVOKE " + kind + " " + GrantRequests.dotted(call.pathParameter("securableName"),
                "securableName") + " FROM " + grantee);
        }
        return call.sql().action("REVOKE " + (optionOnly ? "GRANT OPTION FOR " : "")
            + GrantRequests.words(call.pathParameter("privilege"), "privilege") + " ON " + onNamed(call, kind)
            + " FROM " + grantee + mode);
    }

    private static RestResponse revokeBulk(final RestCall call, final String grantee, final boolean optionOnly) {
        return call.sql().action("REVOKE " + (optionOnly ? "GRANT OPTION FOR " : "")
            + GrantRequests.words(call.pathParameter("privilege"), "privilege") + " ON " + onBulk(call) + " FROM "
            + grantee + GrantRequests.mode(call, "deleteMode"));
    }

    private static RestResponse list(final RestCall call, final String grantee) {
        final Long limit = call.integer("showLimit");
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW GRANTS TO " + grantee)) {
            if (limit != null && out.size() >= limit.longValue()) {
                break;
            }
            // The account answers the listing's own words here: granted_to and granted_on as SHOW GRANTS spells
            // them (ROLE, DATABASE_ROLE), the name as listed, and an empty grantor role type.
            final ObjectNode node = RestJson.object();
            final ArrayNode privileges = RestJson.array();
            privileges.add(row.string("privilege"));
            node.set("privileges", privileges);
            RestJson.bool(node, "grant_option", row, "grant_option");
            RestJson.timestamp(node, "created_on", row, "created_on");
            RestJson.string(node, "grantee_type", row, "granted_to");
            RestJson.string(node, "grantee_name", row, "grantee_name");
            RestJson.string(node, "securable_type", row, "granted_on");
            RestJson.string(node, "securable_name", row, "name");
            RestJson.put(node, "granted_by_name", row.string("granted_by"));
            node.put("granted_by_role_type", "");
            out.add(node);
        }
        return RestResponse.json(200, out);
    }
}
