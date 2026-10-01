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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The notebook endpoints ({@code notebook.yaml}). */
public class RestNotebookTest extends BaseRestTest {

    private static final String BASE = "/api/v2/databases/nb_db/schemas/s/notebooks";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE nb_db");
        sql("CREATE SCHEMA nb_db.s");
        sql("CREATE SCHEMA nb_db.other");
        sql("CREATE WAREHOUSE nb_wh INITIALLY_SUSPENDED = TRUE");
    }

    @Test
    public void aNotebookIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Notebook NB_BASIC successfully created.", ok(post(BASE, "{\"name\":\"nb_basic\","
            + "\"fromLocation\":\"@nb_db.s.stg/nb\",\"main_file\":\"nb.ipynb\",\"comment\":\"first\","
            + "\"query_warehouse\":\"nb_wh\",\"idle_auto_shutdown_time_seconds\":900,"
            + "\"runtime_name\":\"SYSTEM$WAREHOUSE_RUNTIME\"}")).path("status").asString());
        final JsonNode fetched = ok(get(BASE + "/nb_basic"));
        assertEquals("NB_BASIC", fetched.path("name").asString());
        assertEquals("nb.ipynb", fetched.path("main_file").asString());
        assertEquals("first", fetched.path("comment").asString());
        assertEquals("NB_WH", fetched.path("query_warehouse").asString());
        assertEquals(900, fetched.path("idle_auto_shutdown_time_seconds").asInt());
        assertEquals("SYSTEM$WAREHOUSE_RUNTIME", fetched.path("runtime_name").asString());
        assertEquals("NB_DB", fetched.path("database_name").asString());
        assertEquals("ROLE", fetched.path("owner_role_type").asString());
        assertFalse(fetched.path("url_id").asString().isEmpty(), fetched.toString());
        assertEquals("VERSION$1", fetched.path("default_version_details").path("name").asString());
        assertTrue(fetched.has("budget") && fetched.path("budget").isNull(), "every property is sent: " + fetched);
        assertTrue(fetched.path("last_version_details").path("location_url").asString()
            .startsWith("snow://notebook/NB_DB.S.NB_BASIC/versions/"), fetched.toString());
        assertTrue(fetched.path("live_version_location_uri").isNull(), fetched.toString());

        assertEquals("NB_BASIC", names(ok(get(BASE + "?like=NB_B%25"))));
        assertEquals("", names(ok(get(BASE + "?startsWith=ZZ"))));

        ok(delete(BASE + "/nb_basic"));
        error(404, get(BASE + "/nb_basic"));
        error(404, delete(BASE + "/nb_basic"));
        ok(delete(BASE + "/nb_basic?ifExists=true"));
    }

    @Test
    public void createModes() throws Exception {
        ok(post(BASE, "{\"name\":\"nb_modes\",\"comment\":\"a\"}"));
        error(409, post(BASE, "{\"name\":\"nb_modes\"}"));
        ok(post(BASE + "?createMode=ifNotExists", "{\"name\":\"nb_modes\",\"comment\":\"b\"}"));
        assertEquals("a", ok(get(BASE + "/nb_modes")).path("comment").asString());
        ok(post(BASE + "?createMode=orReplace", "{\"name\":\"nb_modes\",\"comment\":\"c\"}"));
        assertEquals("c", ok(get(BASE + "/nb_modes")).path("comment").asString());
        error(400, post(BASE, "{\"comment\":\"no name\"}"));
    }

    @Test
    public void renameMovesTheNotebook() throws Exception {
        ok(post(BASE, "{\"name\":\"nb_rename\"}"));
        ok(post(BASE + "/nb_rename:rename?targetName=nb_renamed", null));
        error(404, get(BASE + "/nb_rename"));
        ok(get(BASE + "/nb_renamed"));
        ok(post(BASE + "/nb_renamed:rename?targetSchema=other&targetName=nb_moved", null));
        ok(get("/api/v2/databases/nb_db/schemas/other/notebooks/nb_moved"));
        error(400, post(BASE + "/nb_moved:rename", null));
        error(404, post(BASE + "/nb_nosuch:rename?targetName=x", null));
        ok(post(BASE + "/nb_nosuch:rename?targetName=x&ifExists=true", null));
    }

    /** Executing needs a query warehouse and a live version; the version actions are the SQL's own. */
    @Test
    public void executeAndTheVersionActionsFollowTheSql() throws Exception {
        ok(post(BASE, "{\"name\":\"nb_exec\"}"));
        assertTrue(error(400, post(BASE + "/nb_exec:execute", null)).contains("set the query warehouse"));
        sql("ALTER NOTEBOOK nb_db.s.nb_exec SET QUERY_WAREHOUSE = nb_wh");
        assertTrue(error(400, post(BASE + "/nb_exec:execute", null)).contains("live version is not found"));
        error(400, post(BASE + "/nb_exec:commit", null));
        assertEquals("Live version successfully created.",
            ok(post(BASE + "/nb_exec:add-live-version?fromLast=true&comment=draft", null)).path("status").asString());
        assertTrue(ok(get(BASE + "/nb_exec")).path("live_version_location_uri").asString().endsWith("/versions/live/"));
        assertEquals("Statement executed successfully.",
            ok(post(BASE + "/nb_exec:execute", null)).path("status").asString());
        error(501, post(BASE + "/nb_exec:commit?version=v2", null));
        assertEquals("Live version successfully committed.",
            ok(post(BASE + "/nb_exec:commit?comment=done", null)).path("status").asString());
        assertEquals("VERSION$2", ok(get(BASE + "/nb_exec")).path("last_version_details").path("name").asString());
        error(501, post(BASE + "/nb_exec:add-live-version?fromLast=false", null));
        error(404, post(BASE + "/nb_nosuch:execute", null));
    }

    @Test
    public void tagsAreNotProvided() throws Exception {
        ok(post(BASE, "{\"name\":\"nb_501\"}"));
        assertEquals(0, ok(get(BASE + "/nb_501:get-tags")).size());
        error(501, post(BASE + "/nb_501:set-tags", "[]"));
    }
}
