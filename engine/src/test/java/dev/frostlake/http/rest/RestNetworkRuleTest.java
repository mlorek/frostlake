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

/** The network rule endpoints ({@code network-rule.yaml}). */
public class RestNetworkRuleTest extends BaseRestTest {

    private static final String RULES = "/api/v2/databases/nr_db/schemas/s/network-rules";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE nr_db");
        sql("CREATE SCHEMA nr_db.s");
    }

    @Test
    public void aRuleIsCreatedFetchedListedAndDeleted() throws Exception {
        assertEquals("Network rule NR_BASIC is created.", ok(post(RULES,
            "{\"name\":\"nr_basic\",\"type\":\"IPV4\",\"mode\":\"INGRESS\","
            + "\"value_list\":[\"10.0.0.0/8\",\"1.2.3.4\"],\"comment\":\"rest\"}")).path("status").asString());
        final JsonNode fetched = ok(get(RULES + "/nr_basic"));
        assertEquals("NR_BASIC", fetched.path("name").asString());
        assertEquals("IPv4", fetched.path("type").asString());
        assertEquals("ingress", fetched.path("mode").asString());
        assertEquals(2, fetched.path("value_list").size());
        assertEquals("1.2.3.4", fetched.path("value_list").get(1).asString());
        assertEquals("rest", fetched.path("comment").asString());
        assertEquals("NR_DB", fetched.path("database_name").asString());
        assertEquals("S", fetched.path("schema_name").asString());
        assertEquals("ROLE", fetched.path("owner_role_type").asString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());

        assertEquals("NR_BASIC", names(ok(get(RULES + "?like=nr_bas%25"))));
        assertEquals("", names(ok(get(RULES + "?like=nothing%25"))));

        assertEquals("NR_BASIC successfully dropped.", ok(delete(RULES + "/nr_basic")).path("status").asString());
        error(404, get(RULES + "/nr_basic"));
        error(404, delete(RULES + "/nr_basic"));
        ok(delete(RULES + "/nr_basic?ifExists=true"));
    }

    @Test
    public void createModesAndRefusals() throws Exception {
        ok(post(RULES, "{\"name\":\"nr_modes\",\"type\":\"IPV4\",\"comment\":\"first\"}"));
        error(409, post(RULES + "?createMode=errorIfExists", "{\"name\":\"nr_modes\",\"type\":\"IPV4\"}"));
        assertEquals("NR_MODES already exists, statement succeeded.", ok(post(RULES + "?createMode=ifNotExists",
            "{\"name\":\"nr_modes\",\"type\":\"IPV4\",\"comment\":\"second\"}")).path("status").asString());
        assertEquals("first", ok(get(RULES + "/nr_modes")).path("comment").asString());
        ok(post(RULES + "?createMode=orReplace", "{\"name\":\"nr_modes\",\"type\":\"HOST_PORT\","
            + "\"mode\":\"EGRESS\",\"value_list\":[\"example.com:443\"]}"));
        final JsonNode replaced = ok(get(RULES + "/nr_modes"));
        assertEquals("HOST_PORT", replaced.path("type").asString());
        assertEquals("egress", replaced.path("mode").asString());
        error(400, post(RULES, "{\"name\":\"nr_untyped\"}"));
        error(400, post(RULES, "{\"name\":\"nr_bad\",\"type\":\"IPV4; DROP\"}"));
        error(400, post(RULES, "{\"name\":\"nr_bad\",\"type\":\"NOSUCH\"}"));
        error(404, get("/api/v2/databases/nr_db/schemas/nope/network-rules"));
    }

    @Test
    public void listingPagesByName() throws Exception {
        ok(post(RULES, "{\"name\":\"nr_page_a\",\"type\":\"IPV4\"}"));
        ok(post(RULES, "{\"name\":\"nr_page_b\",\"type\":\"IPV4\"}"));
        ok(post(RULES, "{\"name\":\"nr_page_c\",\"type\":\"IPV4\"}"));
        assertEquals("NR_PAGE_A,NR_PAGE_B,NR_PAGE_C", names(ok(get(RULES + "?startsWith=NR_PAGE"))));
        assertEquals("NR_PAGE_B", names(ok(get(RULES + "?startsWith=NR_PAGE&showLimit=1&fromName=NR_PAGE_A"))));
    }
}
