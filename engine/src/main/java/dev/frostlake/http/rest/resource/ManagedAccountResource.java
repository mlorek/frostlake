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
import dev.frostlake.http.rest.RestSql;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Managed accounts ({@code managed-account.yaml}, {@code /api/v2/managed-accounts}): list ({@code like}), create
 * and delete, over SHOW MANAGED ACCOUNTS and CREATE and DROP MANAGED ACCOUNT. A managed account is a reader
 * account: {@code account_type} is always READER.
 */
public final class ManagedAccountResource implements RestResource {

    private static final String COLLECTION = "/api/v2/managed-accounts";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listManagedAccounts", this);
        router.add("POST", COLLECTION, "createManagedAccount", this);
        router.add("DELETE", COLLECTION + "/{name}", "deleteManagedAccount", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listManagedAccounts":
                return list(call);
            case "createManagedAccount":
                return create(call);
            case "deleteManagedAccount":
                return call.sql().action("DROP MANAGED ACCOUNT " + call.identifier("name").sql());
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW MANAGED ACCOUNTS" + RestShow.like(call))) {
            final ObjectNode node = RestJson.object();
            RestJson.name(node, "name", row, "account_name");
            RestJson.string(node, "cloud", row, "cloud");
            RestJson.string(node, "region", row, "region");
            RestJson.string(node, "locator", row, "account_locator");
            RestJson.timestamp(node, "created_on", row, "created_on");
            RestJson.string(node, "url", row, "account_url");
            RestJson.string(node, "account_locator_url", row, "account_locator_url");
            RestJson.put(node, "comment", row.string("comment"));
            node.put("account_type", "READER");
            out.add(node);
        }
        return RestResponse.json(200, out);
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final String adminName = RestJson.text(body, "admin_name");
        final String adminPassword = RestJson.text(body, "admin_password");
        if (adminName == null) {
            throw RestException.badRequest("Missing required property 'admin_name'.");
        }
        if (adminPassword == null) {
            throw RestException.badRequest("Missing required property 'admin_password'.");
        }
        final String type = RestJson.text(body, "account_type");
        if (type != null && !"READER".equalsIgnoreCase(type)) {
            throw RestException.badRequest("Invalid value '" + type + "' for property 'account_type': expected READER.");
        }
        final StringBuilder sql = new StringBuilder("CREATE MANAGED ACCOUNT ").append(name.sql())
            .append(" ADMIN_NAME = ").append(RestSql.literal(adminName))
            .append(", ADMIN_PASSWORD = ").append(RestSql.literal(adminPassword))
            .append(", TYPE = READER");
        final String comment = RestJson.text(body, "comment");
        if (comment != null) {
            sql.append(", COMMENT = ").append(RestSql.literal(comment));
        }
        return call.sql().action(sql.toString());
    }
}
