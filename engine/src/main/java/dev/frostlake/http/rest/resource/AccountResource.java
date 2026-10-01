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
import dev.frostlake.http.rest.RestStatement;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.regex.Pattern;

/**
 * Accounts ({@code account.yaml}, {@code /api/v2/accounts}): list ({@code like}, {@code showLimit},
 * {@code history}), create, delete ({@code gracePeriodInDays}, required, and {@code ifExists}) and {@code :undrop},
 * over SHOW ACCOUNTS, CREATE, DROP and UNDROP ACCOUNT. The engine is one account; the others are the records
 * CREATE ACCOUNT keeps, listed after it.
 */
public final class AccountResource implements RestResource {

    private static final String COLLECTION = "/api/v2/accounts";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listAccounts", this);
        router.add("POST", COLLECTION, "createAccount", this);
        router.add("DELETE", ITEM, "deleteAccount", this);
        router.add("POST", ITEM + ":undrop", "UndropAccount", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listAccounts":
                return list(call);
            case "createAccount":
                return create(call);
            case "deleteAccount":
                final Long grace = call.integer("gracePeriodInDays");
                if (grace == null) {
                    throw RestException.badRequest("Missing required query parameter 'gracePeriodInDays'.");
                }
                return call.sql().action("DROP ACCOUNT" + call.ifExists() + " " + call.identifier("name").sql()
                    + " GRACE_PERIOD_IN_DAYS = " + grace);
            case "UndropAccount":
                return call.sql().action("UNDROP ACCOUNT " + call.identifier("name").sql());
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final Long limit = call.integer("showLimit");
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW ACCOUNTS" + (call.flag("history", false) ? " HISTORY" : "")
                + RestShow.like(call))) {
            if (limit != null && out.size() >= limit.longValue()) {
                break;
            }
            final ObjectNode node = RestJson.object();
            RestJson.string(node, "organization_name", row, "organization_name");
            RestJson.name(node, "name", row, "account_name");
            RestJson.string(node, "region_group", row, "region_group");
            RestJson.string(node, "region", row, "snowflake_region");
            RestJson.string(node, "edition", row, "edition");
            RestJson.timestamp(node, "created_on", row, "created_on");
            RestJson.string(node, "account_url", row, "account_url");
            RestJson.string(node, "account_locator", row, "account_locator");
            RestJson.string(node, "account_locator_url", row, "account_locator_url");
            RestJson.integer(node, "managed_accounts", row, "managed_accounts");
            RestJson.string(node, "consumption_billing_entity_name", row, "consumption_billing_entity_name");
            RestJson.string(node, "marketplace_consumer_billing_entity_name", row,
                "marketplace_consumer_billing_entity_name");
            RestJson.string(node, "marketplace_provider_billing_entity_name", row,
                "marketplace_provider_billing_entity_name");
            RestJson.put(node, "old_account_url", row.string("old_account_url"));
            RestJson.put(node, "comment", row.string("comment"));
            RestJson.bool(node, "is_org_admin", row, "is_org_admin");
            RestJson.timestamp(node, "dropped_on", row, "dropped_on");
            RestJson.timestamp(node, "scheduled_deletion_time", row, "scheduled_deletion_time");
            RestJson.timestamp(node, "restored_on", row, "restored_on");
            RestJson.timestamp(node, "account_old_url_saved_on", row, "account_old_url_saved_on");
            RestJson.timestamp(node, "account_old_url_last_used", row, "account_old_url_last_used");
            RestJson.put(node, "organization_old_url", row.string("organization_old_url"));
            RestJson.timestamp(node, "organization_old_url_saved_on", row, "organization_old_url_saved_on");
            RestJson.timestamp(node, "organization_old_url_last_used", row, "organization_old_url_last_used");
            RestJson.string(node, "moved_to_organization", row, "moved_to_organization");
            RestJson.timestamp(node, "moved_on", row, "moved_on");
            RestJson.timestamp(node, "organization_URL_expiration_on", row, "organization_URL_expiration_on");
            RestJson.bool(node, "is_events_account", row, "is_events_account");
            // What the listing does not carry answers as the account answers it for an account read back: the
            // administrator's properties unknown, the flags off, the public region group.
            if (!node.hasNonNull("region_group")) {
                node.put("region_group", "PUBLIC");
            }
            node.put("must_change_password", false);
            node.put("polaris", false);
            RestJson.nulls(node, "admin_name", "admin_password", "admin_rsa_public_key", "admin_user_type", "email",
                "first_name", "last_name", "retention_time", "dropped_on", "scheduled_deletion_time", "restored_on");
            out.add(node);
        }
        return RestResponse.json(200, out);
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestStatement sql = new RestStatement("CREATE ACCOUNT " + name.sql());
        sql.string(body, "admin_name", "ADMIN_NAME");
        sql.string(body, "admin_password", "ADMIN_PASSWORD");
        sql.string(body, "admin_rsa_public_key", "ADMIN_RSA_PUBLIC_KEY");
        word(sql, body, "admin_user_type", "ADMIN_USER_TYPE");
        sql.string(body, "first_name", "FIRST_NAME");
        sql.string(body, "last_name", "LAST_NAME");
        sql.string(body, "email", "EMAIL");
        sql.bool(body, "must_change_password", "MUST_CHANGE_PASSWORD");
        word(sql, body, "edition", "EDITION");
        word(sql, body, "region_group", "REGION_GROUP");
        word(sql, body, "region", "REGION");
        sql.string(body, "comment", "COMMENT");
        sql.bool(body, "polaris", "POLARIS");
        return call.sql().action(sql.toString());
    }

    /** {@code KEYWORD = WORD} for a property whose SQL value is a bare word: an edition, a region, a type. */
    private static void word(final RestStatement sql, final JsonNode body, final String property,
                             final String keyword) {
        final String value = RestJson.text(body, property);
        if (value != null) {
            if (!WORD.matcher(value).matches()) {
                throw RestException.badRequest("Invalid value '" + value + "' for property '" + property + "'.");
            }
            sql.property(keyword, value);
        }
    }
}
