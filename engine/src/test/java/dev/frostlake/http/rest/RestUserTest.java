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

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The user endpoints ({@code user.yaml}), each translated into the SQL the engine answers. */
public class RestUserTest extends BaseRestTest {

    @Test
    public void aUserIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("User U_BASIC successfully created.", ok(post("/api/v2/users", """
            {"name":"u_basic","password":"Secret1!","login_name":"ub","display_name":"Basic","first_name":"B",
             "middle_name":"M","last_name":"L","email":"b@example.com","default_role":"PUBLIC",
             "default_secondary_roles":"ALL","must_change_password":true,"comment":"rest"}""")).path("status").asString());
        final JsonNode user = ok(get("/api/v2/users/u_basic"));
        assertEquals("U_BASIC", user.path("name").asString());
        assertEquals("UB", user.path("login_name").asString());
        assertEquals("Basic", user.path("display_name").asString());
        assertEquals("M", user.path("middle_name").asString());
        assertEquals("b@example.com", user.path("email").asString());
        assertEquals("PUBLIC", user.path("default_role").asString());
        assertEquals("ALL", user.path("default_secondary_roles").asString());
        assertTrue(user.path("must_change_password").asBoolean());
        assertFalse(user.path("disabled").asBoolean());
        assertTrue(user.path("has_password").asBoolean());
        assertEquals("rest", user.path("comment").asString());
        assertTrue(user.path("created_on").asString().contains("T"), user.toString());
        assertEquals("********", user.path("password").asString(), "a password is answered masked: " + user);
        assertFalse(user.path("enable_unredacted_query_syntax_error").asBoolean(), user.toString());

        assertEquals("U_BASIC", names(ok(get("/api/v2/users?like=u_bas%25"))));
        assertEquals("", names(ok(get("/api/v2/users?like=nothing_like_this"))));
        assertEquals("U_BASIC", names(ok(get("/api/v2/users?startsWith=U_BAS&showLimit=1"))));

        assertEquals("U_BASIC successfully dropped.", ok(delete("/api/v2/users/u_basic")).path("status").asString());
        error(404, get("/api/v2/users/u_basic"));
        error(404, delete("/api/v2/users/u_basic"));
        ok(delete("/api/v2/users/u_basic?ifExists=true"));
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        ok(post("/api/v2/users", "{\"name\":\"u_modes\",\"comment\":\"first\"}"));
        error(409, post("/api/v2/users", "{\"name\":\"u_modes\"}"));
        ok(post("/api/v2/users?createMode=ifNotExists", "{\"name\":\"u_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get("/api/v2/users/u_modes")).path("comment").asString());
        ok(post("/api/v2/users?createMode=orReplace", "{\"name\":\"u_modes\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get("/api/v2/users/u_modes")).path("comment").asString());
        error(400, post("/api/v2/users", "{\"comment\":\"no name\"}"));
        error(400, post("/api/v2/users", "{\"name\":\"u_bad\",\"default_secondary_roles\":\"SOME\"}"));
    }

    @Test
    public void putCreatesAnAbsentUserAndResetsWhatALaterBodyLeavesOut() throws Exception {
        ok(put("/api/v2/users/u_put", "{\"name\":\"u_put\",\"first_name\":\"F\",\"comment\":\"c\",\"disabled\":true}"));
        JsonNode user = ok(get("/api/v2/users/u_put"));
        assertEquals("F", user.path("first_name").asString());
        assertTrue(user.path("disabled").asBoolean());
        ok(put("/api/v2/users/u_put", "{\"name\":\"u_put\",\"last_name\":\"L\",\"default_secondary_roles\":\"NONE\"}"));
        user = ok(get("/api/v2/users/u_put"));
        assertTrue(user.get("first_name").isNull(), "an omitted first name is unset: " + user);
        assertEquals("L", user.path("last_name").asString());
        assertFalse(user.path("disabled").asBoolean());
        assertEquals("NONE", user.path("default_secondary_roles").asString());
        error(400, put("/api/v2/users/u_put", "{\"name\":\"u_other\"}"));
    }

    @Test
    public void rolesAreGrantedToAndRevokedFromAUser() throws Exception {
        sql("CREATE USER u_grants");
        sql("CREATE ROLE u_grants_role");
        ok(post("/api/v2/users/u_grants/grants",
            "{\"securable_type\":\"role\",\"securable\":{\"name\":\"u_grants_role\"}}"));
        final JsonNode grants = ok(get("/api/v2/users/u_grants/grants"));
        assertEquals(1, grants.size(), grants.toString());
        assertEquals("ROLE", grants.get(0).path("securable_type").asString());
        assertEquals("U_GRANTS_ROLE", grants.get(0).path("securable").path("name").asString());
        ok(post("/api/v2/users/u_grants/grants:revoke",
            "{\"securable_type\":\"role\",\"securable\":{\"name\":\"u_grants_role\"}}"));
        assertEquals(0, ok(get("/api/v2/users/u_grants/grants")).size());

        sql("CREATE DATABASE u_grants_db");
        sql("CREATE DATABASE ROLE u_grants_db.dr");
        ok(post("/api/v2/users/u_grants/grants",
            "{\"securable_type\":\"database role\",\"securable\":{\"database\":\"u_grants_db\",\"name\":\"dr\"}}"));
        assertEquals("DATABASE ROLE", ok(get("/api/v2/users/u_grants/grants")).get(0).path("securable_type").asString());
        assertEquals("Statement executed successfully.", ok(post("/api/v2/users/u_grants/grants:revoke",
            "{\"securable_type\":\"database role\",\"securable\":{\"database\":\"u_grants_db\",\"name\":\"dr\"}}"))
            .path("status").asString());
        assertEquals(0, ok(get("/api/v2/users/u_grants/grants")).size());
        error(400, post("/api/v2/users/u_grants/grants",
            "{\"securable_type\":\"table\",\"securable\":{\"name\":\"t\"}}"));
        error(404, post("/api/v2/users/u_grants/grants",
            "{\"securable_type\":\"role\",\"securable\":{\"name\":\"no_such_role\"}}"));
        error(404, get("/api/v2/users/no_such_user/grants"));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE DATABASE u_tag_db");
        sql("CREATE SCHEMA u_tag_db.s");
        sql("CREATE TAG u_tag_db.s.cost_center");
        sql("CREATE USER u_tagged");
        ok(post("/api/v2/users/u_tagged:set-tags",
            "[{\"tag_database\":\"u_tag_db\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\",\"tag_value\":\"fin\"}]"));
        final JsonNode tags = ok(get("/api/v2/users/u_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("fin", tags.get(0).path("tag_value").asString());
        assertEquals("USER", tags.get(0).path("level").asString());
        ok(post("/api/v2/users/u_tagged:unset-tags",
            "[{\"tag_database\":\"u_tag_db\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\"}]"));
        assertEquals(0, ok(get("/api/v2/users/u_tagged:get-tags")).size());
        error(404, post("/api/v2/users/no_such_user:set-tags",
            "[{\"tag_database\":\"u_tag_db\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\",\"tag_value\":\"x\"}]"));
        ok(post("/api/v2/users/no_such_user:unset-tags?ifExists=true",
            "[{\"tag_database\":\"u_tag_db\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\"}]"));
    }
}
