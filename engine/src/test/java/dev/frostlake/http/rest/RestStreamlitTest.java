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

/** The Streamlit endpoints ({@code streamlit.yaml}). */
public class RestStreamlitTest extends BaseRestTest {

    private static final String BASE = "/api/v2/databases/st_db/schemas/s/streamlits";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE st_db");
        sql("CREATE SCHEMA st_db.s");
        sql("CREATE WAREHOUSE st_wh INITIALLY_SUSPENDED = TRUE");
    }

    @Test
    public void anAppIsCreatedFetchedListedDroppedAndUndropped() throws Exception {
        assertEquals("Streamlit ST_BASIC successfully created.", ok(post(BASE, "{\"name\":\"st_basic\","
            + "\"source_location\":\"@st_db.s.stg/app\",\"main_file\":\"streamlit_app.py\",\"title\":\"App\","
            + "\"query_warehouse\":\"st_wh\",\"comment\":\"c\",\"imports\":[\"@st_db.s.stg/lib.py\"],"
            + "\"external_access_integrations\":[\"ext_int\"]}")).path("status").asString());
        final JsonNode fetched = ok(get(BASE + "/st_basic"));
        assertEquals("ST_BASIC", fetched.path("name").asString());
        assertEquals("App", fetched.path("title").asString());
        assertEquals("streamlit_app.py", fetched.path("main_file").asString());
        assertEquals("ST_WH", fetched.path("query_warehouse").asString());
        assertEquals("@st_db.s.stg/lib.py", fetched.path("imports").get(0).asString());
        assertEquals("EXT_INT", fetched.path("external_access_integrations").get(0).asString());
        assertEquals("VERSION$1", fetched.path("last_version_details").path("name").asString());
        assertTrue(fetched.path("live_version_location_uri").isNull(), fetched.toString());

        assertEquals("ST_BASIC", names(ok(get(BASE + "?like=ST_B%25"))));
        ok(delete(BASE + "/st_basic"));
        error(404, get(BASE + "/st_basic"));
        ok(delete(BASE + "/st_basic?ifExists=true"));
        ok(post(BASE + "/st_basic:undrop", null));
        ok(get(BASE + "/st_basic"));
        error(409, post(BASE + "/st_basic:undrop", null));
    }

    @Test
    public void versionActionsMoveTheLiveAndLastVersions() throws Exception {
        ok(post(BASE, "{\"name\":\"st_versions\"}"));
        error(400, post(BASE + "/st_versions:commit", "{}"));
        ok(post(BASE + "/st_versions:add-live-version?fromLast=true", "{}"));
        assertTrue(ok(get(BASE + "/st_versions")).path("live_version_location_uri").asString()
            .endsWith("/versions/live/"));
        ok(post(BASE + "/st_versions:commit", "{}"));
        final JsonNode committed = ok(get(BASE + "/st_versions"));
        assertEquals("VERSION$2", committed.path("last_version_details").path("name").asString());
        assertEquals("LAST", committed.path("default_version").asString());
        assertTrue(committed.path("live_version_location_uri").isNull(), committed.toString());
        error(400, post(BASE + "/st_versions:abort", null));
        ok(post(BASE + "/st_versions:add-live-version?fromLast=true", "{}"));
        error(400, post(BASE + "/st_versions:add-live-version?fromLast=true", "{}"));
        ok(post(BASE + "/st_versions:abort", null));
        assertTrue(ok(get(BASE + "/st_versions")).path("live_version_location_uri").isNull());
        error(501, post(BASE + "/st_versions:add-live-version?fromLast=false", "{}"));
    }

    @Test
    public void renameAndCreateModes() throws Exception {
        ok(post(BASE, "{\"name\":\"st_modes\",\"comment\":\"a\"}"));
        error(409, post(BASE, "{\"name\":\"st_modes\"}"));
        ok(post(BASE + "?createMode=ifNotExists", "{\"name\":\"st_modes\"}"));
        ok(post(BASE + "?createMode=orReplace", "{\"name\":\"st_modes\",\"comment\":\"b\"}"));
        assertEquals("b", ok(get(BASE + "/st_modes")).path("comment").asString());
        ok(post(BASE + "/st_modes:rename?targetName=st_renamed", null));
        ok(get(BASE + "/st_renamed"));
        error(404, get(BASE + "/st_modes"));
    }

    /** A version added from a stage, with its alias and comment; the Git actions refuse as the SQL does. */
    @Test
    public void addVersionAndTheGitActionsFollowTheSql() throws Exception {
        sql("CREATE STAGE st_db.s.app_stage");
        ok(post(BASE, "{\"name\":\"st_git\"}"));
        assertEquals("Version V2 successfully created.", ok(post(BASE + "/st_git:add-version",
            "{\"source_location\":\"@st_db.s.app_stage/app\",\"version\":{\"name\":\"v2\",\"comment\":\"c\"}}"))
            .path("status").asString());
        final JsonNode fetched = ok(get(BASE + "/st_git"));
        assertEquals("VERSION$2", fetched.path("last_version_details").path("name").asString());
        assertEquals("V2", fetched.path("last_version_details").path("alias").asString());
        assertEquals("@st_db.s.app_stage/app/",
            fetched.path("last_version_details").path("source_location_uri").asString());
        assertEquals("There is already a version exists with alias V2.", ok(post(BASE + "/st_git:add-version",
            "{\"source_location\":\"@st_db.s.app_stage\",\"version\":{\"name\":\"v2\",\"ifNotExists\":true}}"))
            .path("status").asString());
        error(400, post(BASE + "/st_git:add-version", "{\"version\":{\"name\":\"v3\"}}"));
        error(404, post(BASE + "/st_git:add-version", "{\"source_location\":\"@no_such_stage/x\"}"));
        assertTrue(error(400, post(BASE + "/st_git:add-version-from-git",
            "{\"version\":{\"name\":\"v\"},\"git_ref\":\"main\"}")).contains("invalid url prefix"));
        assertTrue(error(400, post(BASE + "/st_git:push", null)).contains("not created from a git source"));
        assertTrue(error(400, post(BASE + "/st_git:push", "{\"auth_type\":\"USERNAME_PASSWORD\","
            + "\"git_username\":\"u\",\"git_password\":\"p\"}")).contains("invalid property list"));
        assertTrue(error(400, post(BASE + "/st_git:pull", null)).contains("not created from a git source"));
        ok(post(BASE + "/st_git:add-live-version?fromLast=true", "{\"version\":{\"name\":\"lv\"}}"));
        assertTrue(error(400, post(BASE + "/st_git:pull", null)).contains("already a live version"));
        ok(post(BASE + "/st_git:commit", "{\"version\":{\"comment\":\"done\"}}"));
        assertEquals("LV", ok(get(BASE + "/st_git")).path("last_version_details").path("alias").asString());
    }

    @Test
    public void tagsAreNotProvided() throws Exception {
        ok(post(BASE, "{\"name\":\"st_501\"}"));
        assertEquals(0, ok(get(BASE + "/st_501:get-tags")).size());
        error(501, post(BASE + "/st_501:set-tags", "[]"));
    }
}
