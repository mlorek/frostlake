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
package dev.frostlake.http.rest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The network policy endpoints ({@code network-policy.yaml}). */
public class RestNetworkPolicyTest extends BaseRestTest {

    private static final String POLICIES = "/api/v2/network-policies";

    @BeforeAll
    public static void createRules() {
        sql("CREATE DATABASE np_db");
        sql("CREATE SCHEMA np_db.s");
        sql("CREATE NETWORK RULE np_db.s.allow_rule TYPE = IPV4 VALUE_LIST = ('10.0.0.0/8')");
        sql("CREATE TAG np_db.s.owner_tag");
    }

    @Test
    public void aPolicyIsCreatedFetchedListedAndDeleted() throws Exception {
        assertEquals("Network policy NP_BASIC is created.", ok(post(POLICIES,
            "{\"name\":\"np_basic\",\"allowed_network_rule_list\":[\"np_db.s.allow_rule\"],"
            + "\"allowed_ip_list\":[\"1.1.1.1\",\"2.2.2.2\"],\"blocked_ip_list\":[],\"comment\":\"rest\"}"))
            .path("status").asString());
        final JsonNode fetched = ok(get(POLICIES + "/np_basic"));
        assertEquals("NP_BASIC", fetched.path("name").asString());
        assertEquals("ALLOW_RULE", fetched.path("allowed_network_rule_list").get(0).asString());
        assertEquals(2, fetched.path("allowed_ip_list").size());
        assertEquals(0, fetched.path("blocked_ip_list").size());
        assertEquals(0, fetched.path("blocked_network_rule_list").size(), "an unset list is empty: " + fetched);
        assertEquals("rest", fetched.path("comment").asString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());

        assertTrue(names(ok(get(POLICIES))).contains("NP_BASIC"));
        assertEquals("NP_BASIC successfully dropped.", ok(delete(POLICIES + "/np_basic")).path("status").asString());
        error(404, get(POLICIES + "/np_basic"));
        error(404, delete(POLICIES + "/np_basic"));
        ok(delete(POLICIES + "/np_basic?ifExists=true"));
    }

    @Test
    public void createModesAndRefusals() throws Exception {
        ok(post(POLICIES, "{\"name\":\"np_modes\",\"comment\":\"first\"}"));
        error(409, post(POLICIES, "{\"name\":\"np_modes\"}"));
        ok(post(POLICIES + "?createMode=ifNotExists", "{\"name\":\"np_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get(POLICIES + "/np_modes")).path("comment").asString());
        ok(post(POLICIES + "?createMode=orReplace", "{\"name\":\"np_modes\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get(POLICIES + "/np_modes")).path("comment").asString());
        error(404, post(POLICIES, "{\"name\":\"np_bad\",\"allowed_network_rule_list\":[\"np_db.s.no_rule\"]}"));
        error(400, post(POLICIES, "{\"comment\":\"nameless\"}"));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        ok(post(POLICIES, "{\"name\":\"np_tagged\"}"));
        ok(post(POLICIES + "/np_tagged:set-tags",
            "[{\"tag_database\":\"np_db\",\"tag_schema\":\"s\",\"tag_name\":\"owner_tag\",\"tag_value\":\"sec\"}]"));
        final JsonNode tags = ok(get(POLICIES + "/np_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("OWNER_TAG", tags.get(0).path("tag_name").asString());
        assertEquals("sec", tags.get(0).path("tag_value").asString());
        ok(post(POLICIES + "/np_tagged:unset-tags",
            "[{\"tag_database\":\"np_db\",\"tag_schema\":\"s\",\"tag_name\":\"owner_tag\"}]"));
        assertEquals(0, ok(get(POLICIES + "/np_tagged:get-tags")).size());
        error(404, post(POLICIES + "/np_untagged:set-tags",
            "[{\"tag_database\":\"np_db\",\"tag_schema\":\"s\",\"tag_name\":\"owner_tag\",\"tag_value\":\"x\"}]"));
    }
}
