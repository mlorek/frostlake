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

/** The grant endpoints ({@code grant.yaml}), each translated into GRANT, REVOKE or SHOW GRANTS TO. */
public class RestGrantTest extends BaseRestTest {

    @Test
    public void aPrivilegeOnANamedSecurableIsGrantedListedAndRevoked() throws Exception {
        sql("CREATE DATABASE g_db");
        sql("CREATE SCHEMA g_db.s");
        sql("CREATE TABLE g_db.s.t (a INT)");
        sql("CREATE ROLE g_role");
        ok(post("/api/v2/grants/role/g_role/table/g_db.s.t/privileges",
            "{\"privileges\":[\"SELECT\",\"INSERT\"],\"grant_option\":true}"));
        ok(post("/api/v2/grants/role/g_role/database/g_db/privileges", "{\"privileges\":[\"USAGE\"]}"));
        final JsonNode grants = ok(get("/api/v2/grants/role/g_role"));
        assertEquals(3, grants.size(), grants.toString());
        for (final JsonNode grant : grants.values()) {
            // The listing's own words: granted_to and granted_on as SHOW GRANTS spells them.
            assertEquals("ROLE", grant.path("grantee_type").asString());
            assertEquals("G_ROLE", grant.path("grantee_name").asString());
            assertEquals("", grant.path("granted_by_role_type").asString());
            if ("TABLE".equals(grant.path("securable_type").asString())) {
                assertEquals("G_DB.S.T", grant.path("securable_name").asString());
                assertTrue(grant.path("grant_option").asBoolean(), grant.toString());
            }
        }
        assertEquals(1, ok(get("/api/v2/grants/role/g_role?showLimit=1")).size());

        ok(delete("/api/v2/grants/role/g_role/table/g_db.s.t/privileges/INSERT/grant-option?deleteMode=cascade"));
        ok(delete("/api/v2/grants/role/g_role/table/g_db.s.t/privileges/SELECT?deleteMode=restrict"));
        final JsonNode after = ok(get("/api/v2/grants/role/g_role"));
        assertEquals(2, after.size(), after.toString());
        for (final JsonNode grant : after.values()) {
            assertFalse(grant.path("grant_option").asBoolean(), grant.toString());
        }
        // An unknown deleteMode is no mode at all.
        assertEquals("Statement executed successfully. 0 objects affected.",
            ok(delete("/api/v2/grants/role/g_role/table/g_db.s.t/privileges/SELECT?deleteMode=later"))
                .path("status").asString());
        error(404, post("/api/v2/grants/role/g_role/table/g_db.s.no_such/privileges", "{\"privileges\":[\"SELECT\"]}"));
        error(404, post("/api/v2/grants/role/no_such_role/database/g_db/privileges", "{\"privileges\":[\"USAGE\"]}"));
    }

    @Test
    public void allAndFutureGrantsInAScope() throws Exception {
        sql("CREATE DATABASE gb_db");
        sql("CREATE SCHEMA gb_db.s");
        sql("CREATE TABLE gb_db.s.t1 (a INT)");
        sql("CREATE TABLE gb_db.s.t2 (a INT)");
        sql("CREATE ROLE gb_role");
        ok(post("/api/v2/grants/role/gb_role/all/tables/schema/gb_db.s/privileges", "{\"privileges\":[\"SELECT\"]}"));
        assertEquals(2, ok(get("/api/v2/grants/role/gb_role")).size());
        ok(delete("/api/v2/grants/role/gb_role/all/tables/schema/gb_db.s/privileges/SELECT"));
        assertEquals(0, ok(get("/api/v2/grants/role/gb_role")).size());

        ok(post("/api/v2/grants/role/gb_role/future/tables/database/gb_db/privileges",
            "{\"privileges\":[\"SELECT\"],\"grant_option\":true}"));
        assertEquals(1, ok(get("/api/v2/roles/gb_role/future-grants")).size());
        ok(delete("/api/v2/grants/role/gb_role/future/tables/database/gb_db/privileges/SELECT/grant-option"));
        assertFalse(ok(get("/api/v2/roles/gb_role/future-grants")).get(0).path("grant_option").asBoolean());
        ok(delete("/api/v2/grants/role/gb_role/future/tables/database/gb_db/privileges/SELECT"));
        assertEquals(0, ok(get("/api/v2/roles/gb_role/future-grants")).size());
        error(400, post("/api/v2/grants/role/gb_role/some/tables/database/gb_db/privileges",
            "{\"privileges\":[\"SELECT\"]}"));
        error(400, post("/api/v2/grants/role/gb_role/all/tables/warehouse/gb_db/privileges",
            "{\"privileges\":[\"SELECT\"]}"));
    }

    @Test
    public void usersDatabaseRolesAndRolesAsSecurables() throws Exception {
        sql("CREATE DATABASE gu_db");
        sql("CREATE DATABASE ROLE gu_db.gu_dr");
        sql("CREATE ROLE gu_role");
        sql("CREATE USER gu_user");
        ok(post("/api/v2/grants/user/gu_user/role/gu_role/privileges", null));
        final JsonNode userGrants = ok(get("/api/v2/grants/user/gu_user"));
        assertEquals(1, userGrants.size(), userGrants.toString());
        assertEquals("ROLE", userGrants.get(0).path("securable_type").asString());
        assertEquals("USER", userGrants.get(0).path("grantee_type").asString());
        ok(delete("/api/v2/grants/user/gu_user/role/gu_role/privileges/USAGE"));
        assertEquals(0, ok(get("/api/v2/grants/user/gu_user")).size());

        ok(post("/api/v2/grants/database-role/gu_db.gu_dr/database/gu_db/privileges", "{\"privileges\":[\"USAGE\"]}"));
        final JsonNode roleGrants = ok(get("/api/v2/grants/database-role/gu_db.gu_dr"));
        // The USAGE a database role holds on its database by itself is listed beside the one granted to it.
        assertEquals(2, roleGrants.size(), roleGrants.toString());
        assertEquals("", roleGrants.get(0).path("granted_by_name").asString(), roleGrants.toString());
        assertEquals("DATABASE_ROLE", roleGrants.get(0).path("grantee_type").asString());
        assertEquals("DATABASE_ROLE", roleGrants.get(1).path("grantee_type").asString());
        ok(post("/api/v2/grants/role/gu_role/account/-/privileges", "{\"privileges\":[\"CREATE DATABASE\"]}"));
        assertEquals("ACCOUNT", ok(get("/api/v2/grants/role/gu_role")).get(0).path("securable_type").asString());
        error(501, get("/api/v2/grants/share/some_share"));
        error(501, post("/api/v2/grants/application-role/app.r/database/gu_db/privileges", "{\"privileges\":[\"USAGE\"]}"));
    }
}
