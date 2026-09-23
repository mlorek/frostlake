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
import java.util.Locale;

/**
 * Users ({@code user.yaml}, {@code /api/v2/users}): list, create, fetch, create-or-alter, delete, the user's role
 * grants, and the tag endpoints.
 *
 * <p>A user is read from {@code SHOW USERS} and, for the properties only it carries, {@code DESCRIBE USER}.
 * {@code PUT} is a create-or-alter done here, since the SQL has no CREATE OR ALTER USER: the user is created when
 * absent, and otherwise set to the body — the properties it names are SET and the settable ones it leaves out are
 * UNSET, except the password, which a fetched user never carries. A fetched user carries its public keys as
 * DESCRIBE USER prints them, so a fetch sent back keeps them. Body properties the engine's USER statements do not
 * take (network_policy, enable_unredacted_query_syntax_error) are not applied.
 */
public final class UserResource implements RestResource {

    private static final String COLLECTION = "/api/v2/users";
    private static final String ITEM = COLLECTION + "/{name}";

    /** The text properties, as the body's name and the SQL keyword. */
    private static final String[][] STRING_PROPERTIES = {
        {"login_name", "LOGIN_NAME"},
        {"display_name", "DISPLAY_NAME"},
        {"first_name", "FIRST_NAME"},
        {"middle_name", "MIDDLE_NAME"},
        {"last_name", "LAST_NAME"},
        {"email", "EMAIL"},
        {"default_warehouse", "DEFAULT_WAREHOUSE"},
        {"default_namespace", "DEFAULT_NAMESPACE"},
        {"default_role", "DEFAULT_ROLE"},
        {"type", "TYPE"},
        {"comment", "COMMENT"},
        {"rsa_public_key", "RSA_PUBLIC_KEY"},
        {"rsa_public_key_2", "RSA_PUBLIC_KEY_2"},
    };
    private static final String[][] BOOLEAN_PROPERTIES = {
        {"must_change_password", "MUST_CHANGE_PASSWORD"},
        {"disabled", "DISABLED"},
    };
    private static final String[][] INTEGER_PROPERTIES = {
        {"days_to_expiry", "DAYS_TO_EXPIRY"},
        {"mins_to_unlock", "MINS_TO_UNLOCK"},
        {"mins_to_bypass_mfa", "MINS_TO_BYPASS_MFA"},
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listUsers", this);
        router.add("POST", COLLECTION, "createUser", this);
        router.add("GET", ITEM, "fetchUser", this);
        router.add("PUT", ITEM, "createOrAlterUser", this);
        router.add("DELETE", ITEM, "deleteUser", this);
        router.add("GET", ITEM + "/grants", "listGrants", this);
        router.add("POST", ITEM + "/grants", "grant", this);
        router.add("POST", ITEM + "/grants:revoke", "revokeGrants", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER USER", name.sql(), "USER", RestIdentifier.display(name.name()),
                "SNOWFLAKE");
        }
        switch (call.operation()) {
            case "listUsers":
                return list(call);
            case "createUser":
                return create(call);
            case "fetchUser":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "createOrAlterUser":
                return createOrAlter(call);
            case "deleteUser":
                return call.sql().action("DROP USER" + call.ifExists() + " " + call.identifier("name").sql());
            case "listGrants":
                return GrantRequests.list(call,
                    call.sql().show("SHOW GRANTS TO USER " + call.identifier("name").sql()), false);
            case "grant":
                return grantRole(call, true);
            case "revokeGrants":
                return grantRole(call, false);
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW USERS" + RestShow.like(call) + RestShow.tail(call))) {
            out.add(toJson(row, null));
        }
        return RestResponse.json(200, out);
    }

    /** The user as the {@code User} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = call.sql().showNamed("SHOW USERS" + RestShow.likeName(name), name);
        if (rows.isEmpty()) {
            throw RestException.notFound("User '" + name + "' does not exist or not authorized.");
        }
        return toJson(rows.get(0), call.sql().show("DESCRIBE USER " + name.sql()));
    }

    private static ObjectNode toJson(final RestRow row, final List<RestRow> described) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        RestJson.string(node, "login_name", row, "login_name");
        RestJson.string(node, "display_name", row, "display_name");
        text(node, "first_name", row, "first_name");
        text(node, "last_name", row, "last_name");
        text(node, "email", row, "email");
        RestJson.bool(node, "must_change_password", row, "must_change_password");
        RestJson.bool(node, "disabled", row, "disabled");
        RestJson.integer(node, "days_to_expiry", row, "days_to_expiry");
        RestJson.integer(node, "mins_to_unlock", row, "mins_to_unlock");
        text(node, "default_warehouse", row, "default_warehouse");
        text(node, "default_namespace", row, "default_namespace");
        text(node, "default_role", row, "default_role");
        final String secondary = row.nonEmpty("default_secondary_roles");
        if (secondary != null) {
            node.put("default_secondary_roles", secondary.toUpperCase(Locale.ROOT).contains("ALL") ? "ALL" : "NONE");
        }
        RestJson.integer(node, "mins_to_bypass_mfa", row, "mins_to_bypass_mfa");
        RestJson.put(node, "comment", row.string("comment"));
        text(node, "type", row, "type");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.timestamp(node, "last_successful_login", row, "last_success_login");
        RestJson.timestamp(node, "expires_at", row, "expires_at_time");
        RestJson.timestamp(node, "locked_until", row, "locked_until_time");
        RestJson.bool(node, "has_password", row, "has_password");
        RestJson.bool(node, "has_rsa_public_key", row, "has_rsa_public_key");
        RestJson.bool(node, "ext_authn_duo", row, "ext_authn_duo");
        text(node, "ext_authn_uid", row, "ext_authn_uid");
        RestJson.string(node, "owner", row, "owner");
        RestJson.bool(node, "snowflake_lock", row, "snowflake_lock");
        if (described != null) {
            for (final RestRow property : described) {
                final String key = property.string("property");
                if ("MIDDLE_NAME".equals(key)) {
                    text(node, "middle_name", property, "value");
                } else if ("SNOWFLAKE_SUPPORT".equals(key)) {
                    RestJson.bool(node, "snowflake_support", property, "value");
                } else if ("RSA_PUBLIC_KEY".equals(key)) {
                    text(node, "rsa_public_key", property, "value");
                } else if ("RSA_PUBLIC_KEY_2".equals(key)) {
                    text(node, "rsa_public_key_2", property, "value");
                } else if ("RSA_PUBLIC_KEY_FP".equals(key)) {
                    text(node, "rsa_public_key_fp", property, "value");
                } else if ("RSA_PUBLIC_KEY_2_FP".equals(key)) {
                    text(node, "rsa_public_key_2_fp", property, "value");
                } else if ("MINS_TO_BYPASS_NETWORK_POLICY".equals(key)) {
                    RestJson.integer(node, "mins_to_bypass_network_policy", property, "value");
                } else if ("PASSWORD_LAST_SET_TIME".equals(key)) {
                    text(node, "password_last_set", property, "value");
                } else if ("CUSTOM_LANDING_PAGE_URL".equals(key)) {
                    text(node, "custom_landing_page_url", property, "value");
                } else if ("CUSTOM_LANDING_PAGE_URL_FLUSH_NEXT_UI_LOAD".equals(key)) {
                    RestJson.bool(node, "custom_landing_page_url_flush_next_ui_load", property, "value");
                }
            }
        }
        if (described != null) {
            // A fetched user masks a password it has, and reports the one flag it has no statement for as off.
            node.put("password", Boolean.TRUE.equals(row.bool("has_password")) ? "********" : null);
            node.put("enable_unredacted_query_syntax_error", false);
        }
        RestJson.nulls(node, "name", "password", "login_name", "display_name", "first_name", "middle_name",
            "last_name", "email", "must_change_password", "disabled", "days_to_expiry", "mins_to_unlock",
            "default_warehouse", "default_namespace", "default_role", "default_secondary_roles", "mins_to_bypass_mfa",
            "rsa_public_key", "rsa_public_key_2", "comment", "type", "enable_unredacted_query_syntax_error",
            "network_policy", "created_on", "last_successful_login", "expires_at", "locked_until", "has_password",
            "has_rsa_public_key", "rsa_public_key_fp", "rsa_public_key_2_fp", "ext_authn_duo", "ext_authn_uid",
            "owner", "snowflake_lock", "snowflake_support", "mins_to_bypass_network_policy", "password_last_set",
            "custom_landing_page_url", "custom_landing_page_url_flush_next_ui_load");
        return node;
    }

    /** A column's text, unless it is empty or the text {@code null} the user listings spell nothing with. */
    private static void text(final ObjectNode node, final String property, final RestRow row, final String column) {
        final String value = row.nonEmpty(column);
        if (value != null && !"null".equals(value)) {
            node.put(property, value);
        }
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " USER" + mode.ifNotExists() + " "
            + name.sql());
        sql.string(body, "password", "PASSWORD");
        properties(sql, body);
        return call.sql().action(sql.toString());
    }

    /** Appends every property the body sets that the USER statements take. */
    private static void properties(final RestStatement sql, final JsonNode body) {
        for (final String[] p : STRING_PROPERTIES) {
            sql.string(body, p[0], p[1]);
        }
        for (final String[] p : BOOLEAN_PROPERTIES) {
            sql.bool(body, p[0], p[1]);
        }
        for (final String[] p : INTEGER_PROPERTIES) {
            sql.integer(body, p[0], p[1]);
        }
        final String secondary = RestJson.text(body, "default_secondary_roles");
        if (secondary != null) {
            if ("ALL".equalsIgnoreCase(secondary)) {
                sql.property("DEFAULT_SECONDARY_ROLES", "('ALL')");
            } else if ("NONE".equalsIgnoreCase(secondary)) {
                sql.property("DEFAULT_SECONDARY_ROLES", "()");
            } else {
                throw RestException.badRequest("Invalid value '" + secondary
                    + "' for property 'default_secondary_roles': expected ALL or NONE.");
            }
        }
    }

    private static RestResponse createOrAlter(final RestCall call) {
        final RestIdentifier name = call.identifier("name");
        final JsonNode body = call.body();
        final RestIdentifier named = RestJson.identifier(body, "name", false);
        if (named != null && !named.equals(name)) {
            throw RestException.badRequest("The body names user '" + named + "' but the path names '" + name + "'.");
        }
        final List<RestRow> existing = call.sql().showNamed("SHOW USERS" + RestShow.likeName(name), name);
        if (existing.isEmpty()) {
            final RestStatement sql = new RestStatement("CREATE USER " + name.sql());
            sql.string(body, "password", "PASSWORD");
            properties(sql, body);
            return call.sql().action(sql.toString());
        }
        String status = RestResponse.DEFAULT_STATUS;
        final RestStatement set = new RestStatement("ALTER USER " + name.sql() + " SET");
        set.string(body, "password", "PASSWORD");
        properties(set, body);
        if (set.propertyCount() > 0) {
            status = call.sql().status(set.toString());
        }
        final List<String> unset = new ArrayList<>();
        for (final String[] p : STRING_PROPERTIES) {
            if (!RestJson.present(body, p[0])) {
                unset.add(p[1]);
            }
        }
        for (final String[] p : BOOLEAN_PROPERTIES) {
            if (!RestJson.present(body, p[0])) {
                unset.add(p[1]);
            }
        }
        for (final String[] p : INTEGER_PROPERTIES) {
            if (!RestJson.present(body, p[0])) {
                unset.add(p[1]);
            }
        }
        if (!unset.isEmpty()) {
            status = call.sql().status("ALTER USER " + name.sql() + " UNSET " + String.join(", ", unset));
        }
        return RestResponse.success(status);
    }

    /** POST …/grants and …/grants:revoke: a role or a database role granted to, or revoked from, the user. */
    private static RestResponse grantRole(final RestCall call, final boolean grant) {
        final JsonNode body = call.body();
        final String kind = GrantRequests.kind(body);
        final String user = call.identifier("name").sql();
        if (!"ROLE".equals(kind) && !"DATABASE ROLE".equals(kind)) {
            throw RestException.badRequest("Only a role or a database role is granted to a user, not " + kind + ".");
        }
        final String role = GrantRequests.securable(body, kind);
        if (grant) {
            return call.sql().action("GRANT " + kind + " " + role + " TO USER " + user);
        }
        return call.sql().action("REVOKE " + kind + " " + role + " FROM USER " + user);
    }
}
