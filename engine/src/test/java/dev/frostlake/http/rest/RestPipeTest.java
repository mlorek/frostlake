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

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pipe endpoints ({@code pipe.yaml}), each translated into the SQL the engine answers. */
public class RestPipeTest extends BaseRestTest {

    private static String pipes(final String database) {
        return "/api/v2/databases/" + database + "/schemas/s/pipes";
    }

    /** A schema holding a table and a stage a pipe can load from. */
    private static void schema(final String database) {
        sql("CREATE DATABASE " + database);
        sql("CREATE SCHEMA " + database + ".s");
        sql("CREATE TABLE " + database + ".s.t (a INT, b VARCHAR)");
        sql("CREATE STAGE " + database + ".s.st");
    }

    private static String pipe(final String name, final String database) {
        return "{\"name\":\"" + name + "\",\"comment\":\"loads t\",\"copy_statement\":\"COPY INTO " + database
            + ".s.t FROM @" + database + ".s.st FILE_FORMAT = (TYPE = CSV)\"}";
    }

    @Test
    public void aPipeIsCreatedFetchedListedAndDropped() throws Exception {
        schema("pipe_basic");
        assertEquals("Pipe P1 successfully created.", ok(post(pipes("pipe_basic"), pipe("p1", "pipe_basic")))
            .path("status").asString());
        final JsonNode fetched = ok(get(pipes("pipe_basic") + "/p1"));
        assertEquals("P1", fetched.path("name").asString());
        assertEquals("loads t", fetched.path("comment").asString());
        assertTrue(fetched.path("copy_statement").asString().startsWith("COPY INTO pipe_basic.s.t"),
            fetched.toString());
        assertEquals("PIPE_BASIC", fetched.path("database_name").asString());
        assertEquals("S", fetched.path("schema_name").asString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());

        ok(post(pipes("pipe_basic"), pipe("p2", "pipe_basic")));
        assertEquals("P1,P2", names(ok(get(pipes("pipe_basic")))));
        assertEquals("P2", names(ok(get(pipes("pipe_basic") + "?like=%252"))));

        ok(delete(pipes("pipe_basic") + "/p1"));
        error(404, get(pipes("pipe_basic") + "/p1"));
        error(404, delete(pipes("pipe_basic") + "/p1"));
        ok(delete(pipes("pipe_basic") + "/p1?ifExists=true"));
        assertEquals("P2", names(ok(get(pipes("pipe_basic")))));
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        schema("pipe_modes");
        ok(post(pipes("pipe_modes"), pipe("m", "pipe_modes")));
        error(409, post(pipes("pipe_modes") + "?createMode=errorIfExists", pipe("m", "pipe_modes")));
        ok(post(pipes("pipe_modes") + "?createMode=ifNotExists", pipe("m", "pipe_modes")));
        ok(post(pipes("pipe_modes") + "?createMode=orReplace",
            "{\"name\":\"m\",\"comment\":\"new\",\"copy_statement\":\"COPY INTO pipe_modes.s.t FROM @pipe_modes.s.st\"}"));
        assertEquals("new", ok(get(pipes("pipe_modes") + "/m")).path("comment").asString());
        error(400, post(pipes("pipe_modes"), "{\"name\":\"nocopy\"}"));
        error(400, post(pipes("pipe_modes"), "{\"name\":\"bad\",\"copy_statement\":\"SELECT 1\"}"));
    }

    @Test
    public void refreshLoadsTheStagedFiles() throws Exception {
        schema("pipe_refresh");
        ok(post(pipes("pipe_refresh"), pipe("loader", "pipe_refresh")));
        final Path dir = Files.createTempDirectory("rest-pipe");
        final Path csv = dir.resolve("rows.csv");
        Files.writeString(csv, "1,a\n2,b\n");
        sql("PUT 'file://" + csv + "' @pipe_refresh.s.st AUTO_COMPRESS = FALSE");
        ok(post(pipes("pipe_refresh") + "/loader:refresh?prefix=nothing", null));
        assertEquals(0L, count("pipe_refresh.s.t"));
        ok(post(pipes("pipe_refresh") + "/loader:refresh", null));
        assertEquals(2L, count("pipe_refresh.s.t"));
        error(404, post(pipes("pipe_refresh") + "/missing:refresh", null));
        ok(post(pipes("pipe_refresh") + "/missing:refresh?ifExists=true", null));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        schema("pipe_tags");
        sql("CREATE TAG pipe_tags.s.cost_center");
        ok(post(pipes("pipe_tags"), pipe("tagged", "pipe_tags")));
        ok(post(pipes("pipe_tags") + "/tagged:set-tags",
            "[{\"tag_database\":\"pipe_tags\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\",\"tag_value\":\"fin\"}]"));
        final JsonNode tags = ok(get(pipes("pipe_tags") + "/tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("COST_CENTER", tags.get(0).path("tag_name").asString());
        assertEquals("fin", tags.get(0).path("tag_value").asString());
        assertEquals("PIPE", tags.get(0).path("level").asString());
        ok(post(pipes("pipe_tags") + "/tagged:unset-tags",
            "[{\"tag_database\":\"pipe_tags\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\"}]"));
        assertEquals(0, ok(get(pipes("pipe_tags") + "/tagged:get-tags")).size());
        error(404, post(pipes("pipe_tags") + "/missing:set-tags",
            "[{\"tag_database\":\"pipe_tags\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\",\"tag_value\":\"x\"}]"));
    }

    private static long count(final String table) {
        return ((Number) server.getEngine().executeQuery("SELECT COUNT(*) FROM " + table,
            server.getEngine().createSession()).getRows().get(0).getValue(0)).longValue();
    }
}
