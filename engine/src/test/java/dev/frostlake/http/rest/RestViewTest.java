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

/** The view endpoints ({@code view.yaml}), each translated into the SQL the engine answers. */
public class RestViewTest extends BaseRestTest {

    private static final String VIEWS = "/api/v2/databases/view_db/schemas/s1/views";

    @BeforeAll
    public static void createBaseTable() {
        sql("CREATE DATABASE view_db");
        sql("CREATE SCHEMA view_db.s1");
        sql("CREATE TABLE view_db.s1.base (id INT, label VARCHAR(10))");
    }

    @Test
    public void aViewIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("View V_BASIC successfully created.", ok(post(VIEWS, """
            {"name": "v_basic", "secure": true, "comment": "rest",
             "columns": [{"name": "i", "comment": "the id"}, {"name": "l"}],
             "query": "SELECT id, label FROM view_db.s1.base"}
            """)).path("status").asString());
        final JsonNode view = ok(get(VIEWS + "/v_basic"));
        assertEquals("V_BASIC", view.path("name").asString());
        assertTrue(view.path("secure").asBoolean());
        assertEquals("rest", view.path("comment").asString());
        assertEquals("SELECT id, label FROM view_db.s1.base", view.path("select_query").asString());
        assertTrue(view.path("query").asString().startsWith("CREATE"), view.toString());
        assertEquals("PERMANENT", view.path("kind").asString());
        assertEquals("I", view.path("columns").get(0).path("name").asString());
        assertEquals("NUMBER(38,0)", view.path("columns").get(0).path("datatype").asString());
        assertEquals("the id", view.path("columns").get(0).path("comment").asString());
        assertEquals("VIEW_DB", view.path("database_name").asString());
        assertTrue(view.path("created_on").asString().contains("T"), view.toString());
        assertEquals("V_BASIC", names(ok(get(VIEWS + "?like=v_bas%25"))));
        assertEquals(0, ok(get(VIEWS + "?like=v_bas%25")).get(0).path("columns").size());
        assertEquals(2, ok(get(VIEWS + "?like=v_bas%25&deep=true")).get(0).path("columns").size());
        assertEquals("V_BASIC successfully dropped.", ok(delete(VIEWS + "/v_basic")).path("status").asString());
        error(404, get(VIEWS + "/v_basic"));
        error(404, delete(VIEWS + "/v_basic"));
        ok(delete(VIEWS + "/v_basic?ifExists=true"));
    }

    @Test
    public void createModesAndRefusals() throws Exception {
        final String body = "{\"name\":\"v_modes\",\"columns\":[],\"comment\":\"%s\",\"query\":\"SELECT 1 AS one\"}";
        ok(post(VIEWS, String.format(body, "first")));
        error(409, post(VIEWS, String.format(body, "x")));
        ok(post(VIEWS + "?createMode=ifNotExists", String.format(body, "second")));
        assertEquals("first", ok(get(VIEWS + "/v_modes")).path("comment").asString());
        ok(post(VIEWS + "?createMode=orReplace&copyGrants=true", String.format(body, "third")));
        assertEquals("third", ok(get(VIEWS + "/v_modes")).path("comment").asString());
        assertFalse(ok(get(VIEWS + "/v_modes")).path("secure").asBoolean());
        error(400, post(VIEWS, "{\"name\":\"v_noquery\",\"columns\":[]}"));
        error(400, post(VIEWS, "{\"name\":\"v_kind\",\"kind\":\"TRANSIENT\",\"columns\":[],\"query\":\"SELECT 1\"}"));
        assertTrue(error(501, post(VIEWS, "{\"name\":\"v_rec\",\"recursive\":true,\"columns\":[],\"query\":\"SELECT 1\"}"))
            .contains("RECURSIVE"));
        ok(post(VIEWS, "{\"name\":\"v_temp\",\"kind\":\"TEMPORARY\",\"columns\":[],\"query\":\"SELECT 1 AS one\"}"));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE TAG view_db.s1.view_tag");
        ok(post(VIEWS, "{\"name\":\"v_tags\",\"columns\":[],\"query\":\"SELECT 1 AS one\"}"));
        ok(post(VIEWS + "/v_tags:set-tags",
            "[{\"tag_database\":\"view_db\",\"tag_schema\":\"s1\",\"tag_name\":\"view_tag\",\"tag_value\":\"v\"}]"));
        final JsonNode tags = ok(get(VIEWS + "/v_tags:get-tags"));
        assertEquals("VIEW_TAG", tags.get(0).path("tag_name").asString());
        assertEquals("v", tags.get(0).path("tag_value").asString());
        ok(post(VIEWS + "/v_tags:unset-tags",
            "[{\"tag_database\":\"view_db\",\"tag_schema\":\"s1\",\"tag_name\":\"view_tag\"}]"));
        assertEquals(0, ok(get(VIEWS + "/v_tags:get-tags")).size());
    }

    @Test
    public void putIsNotAViewEndpoint() throws Exception {
        expect(405, put(VIEWS + "/v_put", "{\"name\":\"v_put\",\"columns\":[],\"query\":\"SELECT 1\"}"));
    }
}
