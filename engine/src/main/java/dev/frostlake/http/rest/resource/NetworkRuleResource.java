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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Locale;

/**
 * Network rules ({@code network-rule.yaml}, {@code /api/v2/databases/{database}/schemas/{schema}/network-rules}):
 * list, create, fetch and delete.
 *
 * <p>A rule is read from {@code SHOW NETWORK RULES} and its value list from {@code DESCRIBE NETWORK RULE}.
 */
public final class NetworkRuleResource implements RestResource {

    private static final String COLLECTION = "/api/v2/databases/{database}/schemas/{schema}/network-rules";
    private static final String ITEM = COLLECTION + "/{name}";

    @Override
    public void register(final RestRouter router) {
        router.add("GET", COLLECTION, "listNetworkRules", this);
        router.add("POST", COLLECTION, "createNetworkRule", this);
        router.add("GET", ITEM, "fetchNetworkRule", this);
        router.add("DELETE", ITEM, "deleteNetworkRule", this);
    }

    @Override
    public RestResponse handle(final RestCall call) {
        switch (call.operation()) {
            case "listNetworkRules":
                return list(call);
            case "createNetworkRule":
                return create(call);
            case "fetchNetworkRule":
                return RestResponse.json(200, fetch(call, call.identifier("name")));
            case "deleteNetworkRule":
                return call.sql().action("DROP NETWORK RULE" + call.ifExists() + " "
                    + call.qualifiedSql(call.identifier("name")));
            default:
                throw RestException.notImplemented("Operation " + call.operation() + " is not supported.");
        }
    }

    private static RestResponse list(final RestCall call) {
        final ArrayNode out = RestJson.array();
        for (final RestRow row : call.sql().show("SHOW NETWORK RULES" + RestShow.like(call) + " IN SCHEMA "
                + call.schemaSql() + RestShow.tail(call))) {
            out.add(toJson(call, row));
        }
        return RestResponse.json(200, out);
    }

    private static List<RestRow> named(final RestCall call, final RestIdentifier name) {
        return call.sql().showNamed("SHOW NETWORK RULES" + RestShow.likeName(name) + " IN SCHEMA " + call.schemaSql(),
            name);
    }

    /** The rule as the {@code NetworkRule} schema describes it, or {@code 404}. */
    private static ObjectNode fetch(final RestCall call, final RestIdentifier name) {
        final List<RestRow> rows = named(call, name);
        if (rows.isEmpty()) {
            throw SecurityRest.missing("Network rule", call.identifier("database") + "." + call.identifier("schema")
                + "." + name);
        }
        return toJson(call, rows.get(0));
    }

    private static ObjectNode toJson(final RestCall call, final RestRow row) {
        final ObjectNode node = RestJson.object();
        RestJson.name(node, "name", row, "name");
        // The API spells the IP types IPv4 and IPv6 and the modes in lower case.
        final String type = row.nonEmpty("type");
        RestJson.put(node, "type", "IPV4".equals(type) ? "IPv4" : "IPV6".equals(type) ? "IPv6" : type);
        final String mode = row.nonEmpty("mode");
        RestJson.put(node, "mode", mode == null ? null : mode.toLowerCase(Locale.ROOT));
        final RestIdentifier name = RestIdentifier.ofResolved(row.string("name"));
        final List<RestRow> described = call.sql().show("DESCRIBE NETWORK RULE " + call.qualifiedSql(name));
        if (!described.isEmpty()) {
            node.set("value_list", SecurityRest.array(SecurityRest.items(described.get(0).string("value_list"))));
        }
        RestJson.string(node, "comment", row, "comment");
        RestJson.timestamp(node, "created_on", row, "created_on");
        RestJson.name(node, "database_name", row, "database_name");
        RestJson.name(node, "schema_name", row, "schema_name");
        RestJson.name(node, "owner", row, "owner");
        RestJson.string(node, "owner_role_type", row, "owner_role_type");
        RestJson.nulls(node, "value_list");
        return node;
    }

    private static RestResponse create(final RestCall call) {
        final JsonNode body = call.body();
        final RestIdentifier name = RestJson.identifier(body, "name", true);
        final RestCreateMode mode = call.createMode();
        final RestStatement sql = new RestStatement("CREATE" + mode.orReplace() + " NETWORK RULE" + mode.ifNotExists()
            + " " + call.qualifiedSql(name));
        sql.property("TYPE", SecurityRest.word(body, "type", true));
        final String ruleMode = SecurityRest.word(body, "mode", false);
        if (ruleMode != null) {
            sql.property("MODE", ruleMode);
        }
        sql.stringList(body, "value_list", "VALUE_LIST");
        sql.string(body, "comment", "COMMENT");
        return call.sql().action(sql.toString());
    }
}
