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

/** The dynamic table endpoints ({@code dynamic-table.yaml}), each translated into the SQL the engine answers. */
public class RestDynamicTableTest extends BaseRestTest {

    private static final String DTS = "/api/v2/databases/dt_db/schemas/s1/dynamic-tables";

    @BeforeAll
    public static void createBaseTable() {
        sql("CREATE WAREHOUSE dt_wh INITIALLY_SUSPENDED = TRUE");
        sql("CREATE DATABASE dt_db");
        sql("CREATE SCHEMA dt_db.s1");
        sql("CREATE SCHEMA dt_db.s2");
        sql("CREATE TABLE dt_db.s1.base (id INT, label VARCHAR(10))");
        sql("INSERT INTO dt_db.s1.base VALUES (1, 'a'), (2, 'b')");
    }

    private static String body(final String name, final String extra) {
        return "{\"name\":\"" + name + "\",\"target_lag\":{\"type\":\"USER_DEFINED\",\"seconds\":120},"
            + "\"warehouse\":\"dt_wh\",\"query\":\"SELECT id, label FROM dt_db.s1.base\"" + extra + "}";
    }

    @Test
    public void aDynamicTableIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Dynamic table DT_BASIC successfully created.", ok(post(DTS, body("dt_basic",
            ",\"cluster_by\":[\"id\"],\"comment\":\"rest\",\"refresh_mode\":\"FULL\",\"initialize\":\"ON_SCHEDULE\","
            + "\"data_retention_time_in_days\":2,\"max_data_extension_time_in_days\":5")))
            .path("status").asString());
        final JsonNode table = ok(get(DTS + "/dt_basic"));
        assertEquals("DT_BASIC", table.path("name").asString());
        assertEquals("PERMANENT", table.path("kind").asString());
        assertEquals("USER_DEFINED", table.path("target_lag").path("type").asString());
        assertEquals(120, table.path("target_lag").path("seconds").asInt());
        assertEquals("DT_WH", table.path("warehouse").asString());
        assertEquals("FULL", table.path("refresh_mode").asString());
        assertEquals("SELECT id, label FROM dt_db.s1.base", table.path("query").asString());
        assertEquals("id", table.path("cluster_by").get(0).asString());
        assertEquals("rest", table.path("comment").asString());
        assertEquals("RUNNING", table.path("scheduling_state").asString());
        assertTrue(table.path("automatic_clustering").asBoolean());
        assertEquals("ID", table.path("columns").get(0).path("name").asString());
        assertEquals("NUMBER(38,0)", table.path("columns").get(0).path("datatype").asString());
        assertEquals("DT_BASIC", names(ok(get(DTS + "?like=dt_bas%25"))));
        assertTrue(ok(get(DTS + "?like=dt_bas%25")).get(0).path("columns").isNull());
        assertEquals(2, ok(get(DTS + "?like=dt_bas%25&deep=true")).get(0).path("columns").size());
        assertEquals("DT_BASIC successfully dropped.", ok(delete(DTS + "/dt_basic")).path("status").asString());
        error(404, get(DTS + "/dt_basic"));
        error(404, delete(DTS + "/dt_basic"));
        ok(delete(DTS + "/dt_basic?ifExists=true"));
        ok(post(DTS + "/dt_basic:undrop", null));
        ok(get(DTS + "/dt_basic"));
    }

    @Test
    public void createModesKindsAndRefusals() throws Exception {
        ok(post(DTS, body("dt_modes", ",\"comment\":\"first\"")));
        error(409, post(DTS, body("dt_modes", "")));
        ok(post(DTS + "?createMode=ifNotExists", body("dt_modes", ",\"comment\":\"second\"")));
        assertEquals("first", ok(get(DTS + "/dt_modes")).path("comment").asString());
        ok(post(DTS + "?createMode=orReplace", body("dt_modes", ",\"comment\":\"third\",\"kind\":\"TRANSIENT\"")));
        final JsonNode replaced = ok(get(DTS + "/dt_modes"));
        assertEquals("third", replaced.path("comment").asString());
        assertEquals("TRANSIENT", replaced.path("kind").asString());
        ok(post(DTS, "{\"name\":\"dt_down\",\"target_lag\":{\"type\":\"DOWNSTREAM\"},\"warehouse\":\"dt_wh\","
            + "\"query\":\"SELECT id FROM dt_db.s1.base\",\"columns\":[{\"name\":\"ident\"}]}"));
        assertEquals("DOWNSTREAM", ok(get(DTS + "/dt_down")).path("target_lag").path("type").asString());
        error(400, post(DTS, "{\"name\":\"dt_nolag\",\"warehouse\":\"dt_wh\",\"query\":\"SELECT 1 AS one\"}"));
        error(400, post(DTS, "{\"name\":\"dt_nowh\",\"target_lag\":{\"type\":\"DOWNSTREAM\"},\"query\":\"SELECT 1 AS one\"}"));
        error(501, post(DTS, body("dt_frozen", ",\"frozen_where\":\"id > 1\"")));
        error(501, post(DTS, body("dt_typed", ",\"columns\":[{\"name\":\"i\",\"datatype\":\"INT\"}]")));
    }

    @Test
    public void theActionsRun() throws Exception {
        ok(post(DTS, body("dt_act", ",\"cluster_by\":[\"id\"]")));
        ok(post(DTS + "/dt_act:suspend", null));
        assertEquals("SUSPENDED", ok(get(DTS + "/dt_act")).path("scheduling_state").asString());
        ok(post(DTS + "/dt_act:resume", null));
        assertEquals("RUNNING", ok(get(DTS + "/dt_act")).path("scheduling_state").asString());
        ok(post(DTS + "/dt_act:refresh", null));
        ok(post(DTS + "/dt_act:suspend-recluster", null));
        assertFalse(ok(get(DTS + "/dt_act")).path("automatic_clustering").asBoolean());
        ok(post(DTS + "/dt_act:resume-recluster", null));
        assertTrue(ok(get(DTS + "/dt_act")).path("automatic_clustering").asBoolean());
        error(404, post(DTS + "/nosuch:suspend", null));
        ok(post(DTS + "/nosuch:suspend?ifExists=true", null));
    }

    @Test
    public void cloneAndSwap() throws Exception {
        ok(post(DTS, body("dt_src", ",\"comment\":\"source\"")));
        ok(post(DTS + "/dt_src:clone?targetSchema=s2", """
            {"name": "dt_copy", "target_lag": {"type": "DOWNSTREAM"},
             "point_of_time": {"point_of_time_type": "offset", "reference": "before", "offset": "-5"}}
            """));
        final JsonNode copy = ok(get("/api/v2/databases/dt_db/schemas/s2/dynamic-tables/dt_copy"));
        assertEquals("source", copy.path("comment").asString());
        assertEquals("DOWNSTREAM", copy.path("target_lag").path("type").asString());
        assertEquals("SUSPENDED", copy.path("scheduling_state").asString());
        ok(post(DTS, "{\"name\":\"dt_other\",\"target_lag\":{\"type\":\"DOWNSTREAM\"},\"warehouse\":\"dt_wh\","
            + "\"query\":\"SELECT 1 AS one\",\"comment\":\"other\"}"));
        ok(post(DTS + "/dt_src:swap-with?targetName=dt_other", null));
        assertEquals("other", ok(get(DTS + "/dt_src")).path("comment").asString());
        assertEquals("source", ok(get(DTS + "/dt_other")).path("comment").asString());
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE TAG dt_db.s1.dt_tag");
        ok(post(DTS, body("dt_tags", "")));
        ok(post(DTS + "/dt_tags:set-tags",
            "[{\"tag_database\":\"dt_db\",\"tag_schema\":\"s1\",\"tag_name\":\"dt_tag\",\"tag_value\":\"v\"}]"));
        final JsonNode tags = ok(get(DTS + "/dt_tags:get-tags"));
        assertEquals("DT_TAG", tags.get(0).path("tag_name").asString());
        assertEquals("v", tags.get(0).path("tag_value").asString());
        ok(post(DTS + "/dt_tags:unset-tags",
            "[{\"tag_database\":\"dt_db\",\"tag_schema\":\"s1\",\"tag_name\":\"dt_tag\"}]"));
        assertEquals(0, ok(get(DTS + "/dt_tags:get-tags")).size());
    }
}
