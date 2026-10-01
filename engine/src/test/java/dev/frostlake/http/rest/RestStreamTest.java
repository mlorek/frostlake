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

/** The stream endpoints ({@code stream.yaml}), each translated into the SQL the engine answers. */
public class RestStreamTest extends BaseRestTest {

    private static String streams(final String database) {
        return "/api/v2/databases/" + database + "/schemas/s/streams";
    }

    private static void schema(final String database) {
        sql("CREATE DATABASE " + database);
        sql("CREATE SCHEMA " + database + ".s");
        sql("CREATE TABLE " + database + ".s.t (a INT)");
    }

    private static String onTable(final String name) {
        return "{\"name\":\"" + name + "\",\"stream_source\":{\"src_type\":\"table\",\"name\":\"t\"},"
            + "\"comment\":\"rest\"}";
    }

    @Test
    public void aStreamIsCreatedFetchedListedAndDropped() throws Exception {
        schema("str_basic");
        assertEquals("Stream S1 successfully created.", ok(post(streams("str_basic"),
            "{\"name\":\"s1\",\"stream_source\":{\"src_type\":\"table\",\"name\":\"t\",\"append_only\":true},"
                + "\"comment\":\"rest\"}")).path("status").asString());
        final JsonNode fetched = ok(get(streams("str_basic") + "/s1"));
        assertEquals("S1", fetched.path("name").asString());
        assertEquals("rest", fetched.path("comment").asString());
        // The source is answered in stream_source alone.
        assertTrue(fetched.get("table_name").isNull(), fetched.toString());
        assertEquals("T", fetched.path("stream_source").path("name").asString());
        assertTrue(fetched.path("stream_source").get("point_of_time").isNull(), fetched.toString());
        assertEquals("APPEND_ONLY", fetched.path("mode").asString());
        assertFalse(fetched.path("stale").asBoolean());
        final JsonNode source = fetched.path("stream_source");
        assertEquals("table", source.path("src_type").asString());
        assertEquals("T", source.path("name").asString());
        assertEquals("STR_BASIC", source.path("database_name").asString());
        assertEquals("S", source.path("schema_name").asString());
        assertTrue(source.path("append_only").asBoolean(), source.toString());

        ok(post(streams("str_basic"), onTable("s2")));
        assertEquals("S1,S2", names(ok(get(streams("str_basic")))));
        assertEquals("S2", names(ok(get(streams("str_basic") + "?like=%252"))));
        assertEquals("S2", names(ok(get(streams("str_basic") + "?startsWith=S2"))));
        assertEquals("S1", names(ok(get(streams("str_basic") + "?showLimit=1"))));

        ok(delete(streams("str_basic") + "/s1"));
        error(404, get(streams("str_basic") + "/s1"));
        error(404, delete(streams("str_basic") + "/s1"));
        ok(delete(streams("str_basic") + "/s1?ifExists=true"));
    }

    @Test
    public void aStreamOnAViewNamesItsSourceAndCreateModesApply() throws Exception {
        schema("str_view");
        sql("CREATE VIEW str_view.s.v AS SELECT a FROM str_view.s.t");
        ok(post(streams("str_view"), "{\"name\":\"sv\",\"stream_source\":{\"src_type\":\"view\",\"name\":\"v\"}}"));
        assertEquals("view", ok(get(streams("str_view") + "/sv")).path("stream_source").path("src_type").asString());
        error(409, post(streams("str_view"), "{\"name\":\"sv\",\"stream_source\":{\"src_type\":\"view\",\"name\":\"v\"}}"));
        ok(post(streams("str_view") + "?createMode=ifNotExists", onTable("sv")));
        assertEquals("view", ok(get(streams("str_view") + "/sv")).path("stream_source").path("src_type").asString());
        ok(post(streams("str_view") + "?createMode=orReplace&copyGrants=true", onTable("sv")));
        assertEquals("table", ok(get(streams("str_view") + "/sv")).path("stream_source").path("src_type").asString());
        error(404, post(streams("str_view"), "{\"name\":\"x\",\"stream_source\":{\"src_type\":\"table\",\"name\":\"nope\"}}"));
        error(400, post(streams("str_view"), "{\"name\":\"x\"}"));
    }

    @Test
    public void stageSourcesExternalTablesAndPointsOfTime() throws Exception {
        schema("str_src");
        sql("CREATE STAGE str_src.s.st DIRECTORY = (ENABLE = TRUE)");
        ok(post(streams("str_src"), "{\"name\":\"ss\",\"stream_source\":{\"src_type\":\"stage\",\"name\":\"st\"}}"));
        final JsonNode staged = ok(get(streams("str_src") + "/ss"));
        assertEquals("stage", staged.path("stream_source").path("src_type").asString());
        assertEquals("ST", staged.path("stream_source").path("name").asString());
        // The engine keeps no external tables, so a stream on one is refused as the statement is.
        error(404, post(streams("str_src"),
            "{\"name\":\"x\",\"stream_source\":{\"src_type\":\"external_table\",\"name\":\"et\"}}"));
        ok(post(streams("str_src"), onTable("first")));
        sql("INSERT INTO str_src.s.t VALUES (1), (2)");
        ok(post(streams("str_src"), "{\"name\":\"second\",\"stream_source\":{\"src_type\":\"table\",\"name\":\"t\","
            + "\"point_of_time\":{\"point_of_time_type\":\"stream\",\"reference\":\"at\",\"stream\":\"first\"}}}"));
        assertEquals(2L, ((Number) server.getEngine().executeQuery("SELECT COUNT(*) FROM str_src.s.second",
            server.getEngine().createSession()).getRows().get(0).getValue(0)).longValue(),
            "a stream created at another's offset holds its pending changes");
        error(400, post(streams("str_src"), "{\"name\":\"x\",\"stream_source\":{\"src_type\":\"table\",\"name\":\"t\","
            + "\"point_of_time\":{\"point_of_time_type\":\"offset\",\"reference\":\"during\",\"offset\":\"-60\"}}}"));
    }

    @Test
    public void aCloneTakesTheSourcesPendingChanges() throws Exception {
        schema("str_clone");
        sql("CREATE SCHEMA str_clone.other");
        ok(post(streams("str_clone"), onTable("src")));
        sql("INSERT INTO str_clone.s.t VALUES (1), (2)");
        assertEquals("Stream COPY1 successfully created.", ok(post(streams("str_clone") + "/src:clone",
            "{\"name\":\"copy1\",\"comment\":\"cloned\"}")).path("status").asString());
        final JsonNode clone = ok(get(streams("str_clone") + "/copy1"));
        assertEquals("cloned", clone.path("comment").asString());
        assertEquals("T", clone.path("stream_source").path("name").asString());
        assertEquals(2L, ((Number) server.getEngine().executeQuery("SELECT COUNT(*) FROM str_clone.s.copy1",
            server.getEngine().createSession()).getRows().get(0).getValue(0)).longValue());
        ok(post(streams("str_clone") + "/src:clone?targetSchema=other", "{\"name\":\"copy2\"}"));
        assertEquals("rest", ok(get("/api/v2/databases/str_clone/schemas/other/streams/copy2")).path("comment")
            .asString());
        error(409, post(streams("str_clone") + "/src:clone", "{\"name\":\"copy1\"}"));
        ok(post(streams("str_clone") + "/src:clone?createMode=orReplace", "{\"name\":\"copy1\"}"));
        error(404, post(streams("str_clone") + "/missing:clone", "{\"name\":\"copy3\"}"));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        schema("str_tags");
        sql("CREATE TAG str_tags.s.cost_center");
        ok(post(streams("str_tags"), onTable("tagged")));
        ok(post(streams("str_tags") + "/tagged:set-tags",
            "[{\"tag_database\":\"str_tags\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\",\"tag_value\":\"fin\"}]"));
        final JsonNode tags = ok(get(streams("str_tags") + "/tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("fin", tags.get(0).path("tag_value").asString());
        assertEquals("STREAM", tags.get(0).path("level").asString());
        ok(post(streams("str_tags") + "/tagged:unset-tags",
            "[{\"tag_database\":\"str_tags\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\"}]"));
        assertEquals(0, ok(get(streams("str_tags") + "/tagged:get-tags")).size());
    }
}
