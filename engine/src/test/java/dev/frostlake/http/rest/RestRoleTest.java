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

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The role endpoints ({@code role.yaml}), each translated into the SQL the engine answers. */
public class RestRoleTest extends BaseRestTest {

    @Test
    public void aRoleIsCreatedListedAndDropped() throws Exception {
        assertEquals("Role R_BASIC successfully created.",
            ok(post("/api/v2/roles", "{\"name\":\"r_basic\",\"comment\":\"rest\"}")).path("status").asString());
        final JsonNode roles = ok(get("/api/v2/roles?like=r_bas%25"));
        assertEquals("R_BASIC", names(roles));
        final JsonNode role = roles.get(0);
        assertEquals("rest", role.path("comment").asString());
        assertEquals("SYSADMIN", role.path("owner").asString());
        assertFalse(role.path("is_default").asBoolean());
        assertEquals(0, role.path("assigned_to_users").asInt());
        assertTrue(role.path("created_on").asString().contains("T"), role.toString());
        assertEquals("R_BASIC", names(ok(get("/api/v2/roles?startsWith=R_BAS&showLimit=5"))));

        error(409, post("/api/v2/roles", "{\"name\":\"r_basic\"}"));
        ok(post("/api/v2/roles?createMode=ifNotExists", "{\"name\":\"r_basic\",\"comment\":\"second\"}"));
        assertEquals("rest", ok(get("/api/v2/roles?like=R_BASIC")).get(0).path("comment").asString());
        ok(post("/api/v2/roles?createMode=orReplace", "{\"name\":\"r_basic\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get("/api/v2/roles?like=R_BASIC")).get(0).path("comment").asString());

        assertEquals("R_BASIC successfully dropped.", ok(delete("/api/v2/roles/r_basic")).path("status").asString());
        error(404, delete("/api/v2/roles/r_basic"));
        ok(delete("/api/v2/roles/r_basic?ifExists=true"));
    }

    @Test
    public void privilegesAreGrantedListedAndRevoked() throws Exception {
        sql("CREATE DATABASE r_priv_db");
        sql("CREATE SCHEMA r_priv_db.s");
        sql("CREATE TABLE r_priv_db.s.t1 (a INT)");
        sql("CREATE TABLE r_priv_db.s.t2 (a INT)");
        sql("CREATE ROLE r_priv");
        ok(post("/api/v2/roles/r_priv/grants", """
            {"securable_type":"database","securable":{"name":"r_priv_db"},"privileges":["USAGE"],"grant_option":true}"""));
        ok(post("/api/v2/roles/r_priv/grants", """
            {"securable_type":"table","containing_scope":{"database":"r_priv_db","schema":"s"},"privileges":["SELECT"]}"""));
        ok(post("/api/v2/roles/r_priv/grants", """
            {"securable_type":"table","securable":{"database":"r_priv_db","schema":"s","name":"t1"},
             "privileges":["INSERT"]}"""));
        final JsonNode grants = ok(get("/api/v2/roles/r_priv/grants"));
        assertEquals(4, grants.size(), grants.toString());
        boolean usageWithOption = false;
        for (final JsonNode grant : grants.values()) {
            if ("DATABASE".equals(grant.path("securable_type").asString())) {
                assertEquals("R_PRIV_DB", grant.path("securable").path("name").asString());
                assertEquals("USAGE", grant.path("privileges").get(0).asString());
                usageWithOption = grant.path("grant_option").asBoolean();
            } else {
                assertEquals("TABLE", grant.path("securable_type").asString());
                assertEquals("R_PRIV_DB", grant.path("securable").path("database").asString());
                assertEquals("S", grant.path("securable").path("schema").asString());
            }
        }
        assertTrue(usageWithOption, grants.toString());
        assertEquals(2, ok(get("/api/v2/roles/r_priv/grants?showLimit=2")).size());

        ok(post("/api/v2/roles/r_priv/grants:revoke?mode=cascade", """
            {"securable_type":"database","securable":{"name":"r_priv_db"},"privileges":["USAGE"],"grant_option":true}"""));
        for (final JsonNode grant : ok(get("/api/v2/roles/r_priv/grants")).values()) {
            assertFalse(grant.path("grant_option").asBoolean(), "only the grant option went: " + grant);
        }
        ok(post("/api/v2/roles/r_priv/grants:revoke?mode=restrict", """
            {"securable_type":"table","containing_scope":{"database":"r_priv_db","schema":"s"},"privileges":["SELECT"]}"""));
        assertEquals(2, ok(get("/api/v2/roles/r_priv/grants")).size());
        // An unknown mode is no mode at all.
        assertEquals("Statement executed successfully. 1 objects affected.",
            ok(post("/api/v2/roles/r_priv/grants:revoke?mode=sometimes", """
            {"securable_type":"database","securable":{"name":"r_priv_db"},"privileges":["USAGE"]}""")).path("status")
                .asString());
        error(400, post("/api/v2/roles/r_priv/grants", "{\"securable_type\":\"database\",\"securable\":{\"name\":\"x\"}}"));
        error(404, post("/api/v2/roles/r_priv/grants", """
            {"securable_type":"database","securable":{"name":"no_such_db"},"privileges":["USAGE"]}"""));
        error(404, get("/api/v2/roles/no_such_role/grants"));
    }

    @Test
    public void rolesAreGrantedToRolesAndListedAsGrantsOfAndOn() throws Exception {
        sql("CREATE ROLE r_child");
        sql("CREATE ROLE r_parent");
        sql("CREATE USER r_user");
        sql("GRANT ROLE r_child TO USER r_user");
        // A role grant names its privileges: without them the account's translation is a syntax error.
        final HttpResponse<String> bare = post("/api/v2/roles/r_parent/grants",
            "{\"securable_type\":\"role\",\"securable\":{\"name\":\"r_child\"}}");
        assertEquals("\nsyntax error line 1 at position 12 unexpected 'on'.", error(400, bare));
        assertEquals("001003", json(bare).path("code").asString());
        ok(post("/api/v2/roles/r_parent/grants",
            "{\"securable_type\":\"role\",\"securable\":{\"name\":\"r_child\"},\"privileges\":[\"USAGE\"]}"));
        final JsonNode of = ok(get("/api/v2/roles/r_child/grants-of"));
        assertEquals(2, of.size(), of.toString());
        assertEquals("R_CHILD", of.get(0).path("role").asString());
        // The grants on a role: its OWNERSHIP and a USAGE per role holding it (a user holding it is not listed).
        final JsonNode on = ok(get("/api/v2/roles/r_child/grants-on"));
        assertEquals(2, on.size(), on.toString());
        assertEquals("USAGE", on.get(0).path("privilege").asString());
        assertEquals("R_PARENT", on.get(0).path("grantee_name").asString());
        assertEquals("false", on.get(0).path("grant_option").asString());
        assertEquals("OWNERSHIP", on.get(1).path("privilege").asString());
        assertEquals("ROLE", on.get(1).path("granted_on").asString());
        assertEquals("true", on.get(1).path("grant_option").asString());
        ok(post("/api/v2/roles/r_parent/grants:revoke", "{\"securable_type\":\"role\",\"securable\":{\"name\":\"r_child\"}}"));
        assertEquals(1, ok(get("/api/v2/roles/r_child/grants-of")).size());
        error(404, get("/api/v2/roles/no_such_role/grants-of"));
        error(404, get("/api/v2/roles/no_such_role/grants-on"));
    }

    @Test
    public void futureGrantsAreGrantedListedAndRevoked() throws Exception {
        sql("CREATE DATABASE r_fut_db");
        sql("CREATE SCHEMA r_fut_db.s");
        sql("CREATE ROLE r_fut");
        ok(post("/api/v2/roles/r_fut/future-grants", """
            {"securable_type":"table","containing_scope":{"database":"r_fut_db","schema":"s"},
             "privileges":["SELECT","INSERT"],"grant_option":true}"""));
        ok(post("/api/v2/roles/r_fut/future-grants", """
            {"securable_type":"schema","containing_scope":{"database":"r_fut_db"},"privileges":["USAGE"]}"""));
        final JsonNode future = ok(get("/api/v2/roles/r_fut/future-grants"));
        assertEquals(3, future.size(), future.toString());
        // Ordered by the kind granted on; the scope is answered in the securable, the placeholder quoted.
        final JsonNode schemas = future.get(0);
        assertEquals("SCHEMA", schemas.path("securable_type").asString());
        assertEquals("R_FUT_DB", schemas.path("securable").path("database").asString());
        assertTrue(schemas.path("securable").get("schema").isNull(), future.toString());
        assertEquals("\"<SCHEMA>\"", schemas.path("securable").path("name").asString());
        final JsonNode tables = future.get(1);
        assertEquals("TABLE", tables.path("securable_type").asString());
        assertEquals("R_FUT_DB", tables.path("securable").path("database").asString());
        assertEquals("S", tables.path("securable").path("schema").asString());
        assertEquals("\"<TABLE>\"", tables.path("securable").path("name").asString());
        assertTrue(tables.get("containing_scope").isNull(), future.toString());
        assertTrue(tables.path("grant_option").asBoolean());
        ok(post("/api/v2/roles/r_fut/future-grants:revoke?mode=restrict", """
            {"securable_type":"table","containing_scope":{"database":"r_fut_db","schema":"s"},"privileges":["INSERT"]}"""));
        assertEquals(2, ok(get("/api/v2/roles/r_fut/future-grants")).size());
        assertEquals(0, ok(get("/api/v2/roles/r_fut/grants")).size());
        error(400, post("/api/v2/roles/r_fut/future-grants", "{\"securable_type\":\"table\",\"privileges\":[\"SELECT\"]}"));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE DATABASE r_tag_db");
        sql("CREATE SCHEMA r_tag_db.s");
        sql("CREATE TAG r_tag_db.s.owner_team");
        sql("CREATE ROLE r_tagged");
        ok(post("/api/v2/roles/r_tagged:set-tags",
            "[{\"tag_database\":\"r_tag_db\",\"tag_schema\":\"s\",\"tag_name\":\"owner_team\",\"tag_value\":\"core\"}]"));
        final JsonNode tags = ok(get("/api/v2/roles/r_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("OWNER_TEAM", tags.get(0).path("tag_name").asString());
        assertEquals("ROLE", tags.get(0).path("level").asString());
        ok(post("/api/v2/roles/r_tagged:unset-tags",
            "[{\"tag_database\":\"r_tag_db\",\"tag_schema\":\"s\",\"tag_name\":\"owner_team\"}]"));
        assertEquals(0, ok(get("/api/v2/roles/r_tagged:get-tags")).size());
    }
}
