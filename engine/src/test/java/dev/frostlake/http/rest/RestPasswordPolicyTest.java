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

/** The password policy endpoints ({@code password-policy.yaml}). */
public class RestPasswordPolicyTest extends BaseRestTest {

    private static final String POLICIES = "/api/v2/databases/pp_db/schemas/s/password-policies";

    @BeforeAll
    public static void createSchemas() {
        sql("CREATE DATABASE pp_db");
        sql("CREATE SCHEMA pp_db.s");
        sql("CREATE SCHEMA pp_db.t");
        sql("CREATE TAG pp_db.s.level_tag");
    }

    @Test
    public void aPolicyIsCreatedFetchedListedAndDeleted() throws Exception {
        assertEquals("Password policy 'PP_BASIC' created successfully", ok(post(POLICIES,
            "{\"name\":\"pp_basic\",\"password_min_length\":12,\"password_history\":3,\"comment\":\"rest\"}"))
            .path("status").asString());
        final JsonNode fetched = ok(get(POLICIES + "/pp_basic"));
        assertEquals("PP_BASIC", fetched.path("name").asString());
        assertEquals(12, fetched.path("password_min_length").asInt());
        assertEquals(3, fetched.path("password_history").asInt());
        assertEquals(90, fetched.path("password_max_age_days").asInt(), "an unset setting answers its default");
        assertEquals("rest", fetched.path("comment").asString());
        assertEquals("PP_DB", fetched.path("database_name").asString());
        assertEquals("S", fetched.path("schema_name").asString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());

        assertEquals("PP_BASIC", names(ok(get(POLICIES + "?like=pp_bas%25"))));
        assertEquals("PP_BASIC successfully dropped.", ok(delete(POLICIES + "/pp_basic")).path("status").asString());
        error(404, get(POLICIES + "/pp_basic"));
        error(404, delete(POLICIES + "/pp_basic"));
        ok(delete(POLICIES + "/pp_basic?ifExists=true"));
    }

    @Test
    public void createModesAndRefusals() throws Exception {
        ok(post(POLICIES, "{\"name\":\"pp_modes\",\"comment\":\"first\"}"));
        error(409, post(POLICIES, "{\"name\":\"pp_modes\"}"));
        ok(post(POLICIES + "?createMode=ifNotExists", "{\"name\":\"pp_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get(POLICIES + "/pp_modes")).path("comment").asString());
        ok(post(POLICIES + "?createMode=orReplace", "{\"name\":\"pp_modes\",\"password_max_retries\":3}"));
        assertEquals(3, ok(get(POLICIES + "/pp_modes")).path("password_max_retries").asInt());
        error(400, post(POLICIES, "{\"name\":\"pp_bad\",\"password_min_length\":4}"));
        expect(400, post(POLICIES, "{\"name\":\"pp_bad\",\"password_min_length\":\"many\"}"));
    }

    @Test
    public void renameMovesThePolicy() throws Exception {
        ok(post(POLICIES, "{\"name\":\"pp_old\"}"));
        ok(post(POLICIES + "/pp_old:rename?targetName=pp_new", null));
        error(404, get(POLICIES + "/pp_old"));
        ok(get(POLICIES + "/pp_new"));
        ok(post(POLICIES + "/pp_new:rename?targetDatabase=pp_db&targetSchema=t&targetName=pp_moved", null));
        ok(get("/api/v2/databases/pp_db/schemas/t/password-policies/pp_moved"));
        error(404, post(POLICIES + "/pp_new:rename?targetName=pp_again", null));
        ok(post(POLICIES + "/pp_new:rename?ifExists=true&targetName=pp_again", null));
        error(400, post(POLICIES + "/pp_new:rename", null));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        ok(post(POLICIES, "{\"name\":\"pp_tagged\"}"));
        ok(post(POLICIES + "/pp_tagged:set-tags",
            "[{\"tag_database\":\"pp_db\",\"tag_schema\":\"s\",\"tag_name\":\"level_tag\",\"tag_value\":\"high\"}]"));
        final JsonNode tags = ok(get(POLICIES + "/pp_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("high", tags.get(0).path("tag_value").asString());
        ok(post(POLICIES + "/pp_tagged:unset-tags",
            "[{\"tag_database\":\"pp_db\",\"tag_schema\":\"s\",\"tag_name\":\"level_tag\"}]"));
        assertEquals(0, ok(get(POLICIES + "/pp_tagged:get-tags")).size());
    }
}
