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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The database role endpoints ({@code database-role.yaml}), each translated into the SQL the engine answers. */
public class RestDatabaseRoleTest extends BaseRestTest {

    private static final String ROLES = "/api/v2/databases/dr_db/database-roles";

    @Test
    public void aDatabaseRoleIsCreatedListedAndDropped() throws Exception {
        sql("CREATE DATABASE IF NOT EXISTS dr_db");
        assertEquals("Role DR_DB.DR_BASIC successfully created.",
            ok(post(ROLES, "{\"name\":\"dr_basic\",\"comment\":\"rest\"}")).path("status").asString());
        ok(post(ROLES, "{\"name\":\"dr_basic_2\"}"));
        final JsonNode roles = ok(get(ROLES));
        assertEquals("DR_BASIC,DR_BASIC_2", names(roles));
        assertEquals("rest", roles.get(0).path("comment").asString());
        assertEquals("SYSADMIN", roles.get(0).path("owner").asString());
        assertEquals("ROLE", roles.get(0).path("owner_role_type").asString());
        assertEquals(0, roles.get(0).path("granted_to_roles").asInt());
        assertTrue(roles.get(0).path("created_on").asString().contains("T"), roles.toString());
        assertEquals("DR_BASIC_2", names(ok(get(ROLES + "?showLimit=1&fromName=DR_BASIC_"))));
        assertEquals("DR_BASIC", names(ok(get(ROLES + "?showLimit=1"))));

        error(409, post(ROLES, "{\"name\":\"dr_basic\"}"));
        ok(post(ROLES + "?createMode=ifNotExists", "{\"name\":\"dr_basic\",\"comment\":\"second\"}"));
        assertEquals("rest", ok(get(ROLES)).get(0).path("comment").asString());
        ok(post(ROLES + "?createMode=orReplace", "{\"name\":\"dr_basic\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get(ROLES)).get(0).path("comment").asString());

        assertEquals("DR_DB.DR_BASIC successfully dropped.",
            ok(delete(ROLES + "/dr_basic")).path("status").asString());
        error(404, delete(ROLES + "/dr_basic"));
        ok(delete(ROLES + "/dr_basic?ifExists=true"));
        error(404, get("/api/v2/databases/no_such_db/database-roles"));
    }

    @Test
    public void grantsAndFutureGrantsAreGrantedListedAndRevoked() throws Exception {
        sql("CREATE DATABASE IF NOT EXISTS dr_db");
        sql("CREATE SCHEMA dr_db.s");
        sql("CREATE TABLE dr_db.s.t (a INT)");
        sql("CREATE DATABASE ROLE dr_db.dr_grants");
        ok(post(ROLES + "/dr_grants/grants", """
            {"securable_type":"table","securable":{"database":"dr_db","schema":"s","name":"t"},
             "privileges":["SELECT"],"grant_option":true}"""));
        final JsonNode grants = ok(get(ROLES + "/dr_grants/grants"));
        assertEquals(2, grants.size(), grants.toString());
        // A database role holds USAGE on its database, granted by no one.
        assertEquals("DATABASE", grants.get(0).path("securable_type").asString());
        assertEquals("DR_DB", grants.get(0).path("securable").path("name").asString());
        assertEquals("", grants.get(0).path("granted_by").asString());
        assertEquals("TABLE", grants.get(1).path("securable_type").asString());
        assertEquals("T", grants.get(1).path("securable").path("name").asString());
        assertEquals("S", grants.get(1).path("securable").path("schema").asString());
        assertTrue(grants.get(1).path("securable").get("service").isNull(), grants.toString());
        assertTrue(grants.get(1).get("containing_scope").isNull(), grants.toString());
        assertTrue(grants.get(1).path("grant_option").asBoolean());
        ok(post(ROLES + "/dr_grants/grants:revoke", """
            {"securable_type":"table","securable":{"database":"dr_db","schema":"s","name":"t"},"privileges":["SELECT"]}"""));
        assertEquals(1, ok(get(ROLES + "/dr_grants/grants")).size());

        ok(post(ROLES + "/dr_grants/future-grants", """
            {"securable_type":"view","containing_scope":{"database":"dr_db"},"privileges":["SELECT"]}"""));
        final JsonNode future = ok(get(ROLES + "/dr_grants/future-grants"));
        assertEquals(1, future.size(), future.toString());
        assertEquals("VIEW", future.get(0).path("securable_type").asString());
        // A future grant answers its scope in the securable, whose name is the quoted placeholder.
        assertEquals("DR_DB", future.get(0).path("securable").path("database").asString());
        assertEquals("\"<VIEW>\"", future.get(0).path("securable").path("name").asString());
        assertTrue(future.get(0).get("containing_scope").isNull(), future.toString());
        assertTrue(future.get(0).get("granted_by").isNull(), future.toString());
        ok(post(ROLES + "/dr_grants/future-grants:revoke?mode=cascade", """
            {"securable_type":"view","containing_scope":{"database":"dr_db"},"privileges":["SELECT"]}"""));
        assertEquals(0, ok(get(ROLES + "/dr_grants/future-grants")).size());
        error(404, get(ROLES + "/no_such_role/grants"));
    }

    @Test
    public void cloneCopiesTheCommentAndWhatTheRoleHolds() throws Exception {
        sql("CREATE DATABASE IF NOT EXISTS dr_db");
        sql("CREATE DATABASE dr_target_db");
        sql("CREATE SCHEMA dr_db.cs");
        sql("CREATE TABLE dr_db.cs.t (a INT)");
        sql("CREATE DATABASE ROLE dr_db.dr_source COMMENT = 'src'");
        sql("CREATE DATABASE ROLE dr_db.dr_inner");
        sql("GRANT SELECT ON TABLE dr_db.cs.t TO DATABASE ROLE dr_db.dr_source WITH GRANT OPTION");
        sql("GRANT DATABASE ROLE dr_db.dr_inner TO DATABASE ROLE dr_db.dr_source");
        sql("GRANT INSERT ON FUTURE TABLES IN SCHEMA dr_db.cs TO DATABASE ROLE dr_db.dr_source");
        assertEquals("Role DR_DB.DR_COPY successfully created.",
            ok(post(ROLES + "/dr_source:clone", "{\"name\":\"dr_copy\"}")).path("status").asString());
        // fromName is a cursor: the page starts after it.
        assertEquals("src", ok(get(ROLES + "?fromName=DR_COP&showLimit=1")).get(0).path("comment").asString());
        assertEquals(3, ok(get(ROLES + "/dr_copy/grants")).size());
        assertEquals(1, ok(get(ROLES + "/dr_copy/future-grants")).size());
        error(409, post(ROLES + "/dr_source:clone", "{\"name\":\"dr_copy\"}"));
        ok(post(ROLES + "/dr_source:clone?createMode=orReplace", "{\"name\":\"dr_copy\"}"));

        ok(post(ROLES + "/dr_source:clone?targetDatabase=dr_target_db", "{\"name\":\"dr_moved\"}"));
        assertEquals("DR_MOVED", names(ok(get("/api/v2/databases/dr_target_db/database-roles"))));
        error(404, post(ROLES + "/no_such_role:clone", "{\"name\":\"x\"}"));
        error(400, post(ROLES + "/dr_source:clone", "{}"));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE DATABASE IF NOT EXISTS dr_db");
        sql("CREATE SCHEMA dr_db.ts");
        sql("CREATE TAG dr_db.ts.level_tag");
        sql("CREATE DATABASE ROLE dr_db.dr_tagged");
        ok(post(ROLES + "/dr_tagged:set-tags",
            "[{\"tag_database\":\"dr_db\",\"tag_schema\":\"ts\",\"tag_name\":\"level_tag\",\"tag_value\":\"v\"}]"));
        final JsonNode tags = ok(get(ROLES + "/dr_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("v", tags.get(0).path("tag_value").asString());
        ok(post(ROLES + "/dr_tagged:unset-tags",
            "[{\"tag_database\":\"dr_db\",\"tag_schema\":\"ts\",\"tag_name\":\"level_tag\"}]"));
        assertEquals(0, ok(get(ROLES + "/dr_tagged:get-tags")).size());
    }
}
