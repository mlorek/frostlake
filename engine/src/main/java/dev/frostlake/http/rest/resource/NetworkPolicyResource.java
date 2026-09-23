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
import dev.frostlake.http.rest.RestStatement;
import dev.frostlake.http.rest.RestTags;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

/**
 * Network policies ({@code network-policy.yaml}, {@code /api/v2/network-policies}): list, create, fetch, delete
 * and the tag endpoints.
 *
 * <p>A policy is read from {@code SHOW NETWORK POLICIES} and its four lists from {@code DESCRIBE NETWORK POLICY}.
 * SHOW NETWORK POLICIES reports no owner, so the owner is the role SHOW GRANTS ON names as holding OWNERSHIP.
 */
public final class NetworkPolicyResource implements RestResource {

    private static final String COLLECTION = "/api/v2/network-policies";
    private static final String ITEM = COLLECTION + "/{name}";
    private static final String[] LISTS = {
        "allowed_network_rule_list", "blocked_network_rule_list", "allowed_ip_list", "blocked_ip_list",
    };

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listNetworkPolicies", this);
        router.add("POST", COLLECTION, "createNetworkPolicy", this);
        router.add("GET", ITEM, "fetchNetworkPolicy", this);
        router.add("DELETE", ITEM, "deleteNetworkPolicy", this);
        RestTags.register(router, ITEM, this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        if (RestTags.handles(call)) {
            final RestIdentifier name = call.identifier("name");
            return RestTags.handle(call, "ALTER NETWORK POLICY", name.sql(), "NETWORK POLICY",
                RestIdentifier.display(name.name()), "SNOWFLAKE");
        }
        switch (call.operation()) {
            case "listNetworkPolicies":
                return list(call);
            case "createNetworkPolicy":
                return create(call);
            case "fetchNetworkPolicy":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteNetworkPolicy":
                return call.sql().action("DROP NETWORK POLICY" + call.ifExists() + " " + call.identifier("name").sql());
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW NETWORK POLICIES")) {
            out.add(toJson(call, row));
        }
        return RestResponse.json(200, out);
    }

    /** The policy as the {@code NetworkPolicy} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        for (final RestRow row : call.sql().show("SHOW NETWORK POLICIES")) {
            if (name.name().equals(row.string("name"))) {
                return toJson(call, row);
            }
        }
        throw SecurityRest.missing("Network policy", name.toString());
    }

    private static ObjectNode toJson(final RestCall call, final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        final RestIdentifier name = RestIdentifier.ofResolved(row.string("name"));
        for (final String list : LISTS) {
            node.set(list, RestJson.array());
        }
        for (final RestRow property : call.sql().show("DESCRIBE NETWORK POLICY " + name.sql())) {
            final String key = property.string("name");
            for (final String list : LISTS) {
                if (list.toUpperCase(Locale.ROOT).equals(key)) {
                    // A rule is answered by its own name, without the database and schema that hold it.
                    final List<String> items = list.endsWith("_network_rule_list")
                        ? SecurityRest.ruleNames(property.string("value")) : SecurityRest.items(property.string("value"));
                    node.set(list, SecurityRest.array(items));
                }
            }
        }
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.nulls(node, "owner", "owner_role_type");
        for (final RestRow grant : call.sql().show("SHOW GRANTS ON NETWORK POLICY " + name.sql())) {
            if ("OWNERSHIP".equals(grant.string("privilege"))) {
                RestJson.string(node, "owner", grant, "grantee_name");
                RestJson.string(node, "owner_role_type", grant, "granted_to");
            }
        }
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " NETWORK POLICY"
            + mode.ifNotExists() + " " + name.sql());
        for (final String list : LISTS) {
            sql.stringList(body, list, list.toUpperCase(Locale.ROOT));
        }
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }
}
