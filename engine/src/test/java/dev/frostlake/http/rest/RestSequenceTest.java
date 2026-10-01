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

/** The sequence endpoints ({@code sequence.yaml}), each translated into the SQL the engine answers. */
public class RestSequenceTest extends BaseRestTest {

    private static final String SEQUENCES = "/api/v2/databases/seq_db/schemas/s1/sequences";

    @BeforeAll
    public static void createSchemas() {
        sql("CREATE DATABASE seq_db");
        sql("CREATE SCHEMA seq_db.s1");
        sql("CREATE SCHEMA seq_db.s2");
    }

    @Test
    public void aSequenceIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Sequence Q_BASIC successfully created.", ok(post(SEQUENCES,
            "{\"name\":\"q_basic\",\"start\":5,\"increment\":3,\"ordered\":true,\"comment\":\"rest\"}"))
            .path("status").asString());
        final JsonNode sequence = ok(get(SEQUENCES + "/q_basic"));
        assertEquals("Q_BASIC", sequence.path("name").asString());
        assertEquals(5, sequence.path("start").asInt());
        assertEquals(3, sequence.path("increment").asInt());
        assertTrue(sequence.path("ordered").asBoolean());
        assertEquals("rest", sequence.path("comment").asString());
        assertEquals("SEQ_DB", sequence.path("database_name").asString());
        assertTrue(sequence.path("created_on").asString().contains("T"), sequence.toString());
        assertEquals("Q_BASIC", names(ok(get(SEQUENCES + "?like=q_bas%25"))));
        assertEquals("", names(ok(get(SEQUENCES + "?like=nothing_like_this"))));
        assertEquals("Q_BASIC successfully dropped.", ok(delete(SEQUENCES + "/q_basic")).path("status").asString());
        error(404, get(SEQUENCES + "/q_basic"));
        error(404, delete(SEQUENCES + "/q_basic"));
        ok(delete(SEQUENCES + "/q_basic?ifExists=true"));
    }

    @Test
    public void createModesApply() throws Exception {
        ok(post(SEQUENCES, "{\"name\":\"q_modes\",\"comment\":\"first\"}"));
        error(409, post(SEQUENCES, "{\"name\":\"q_modes\"}"));
        ok(post(SEQUENCES + "?createMode=ifNotExists", "{\"name\":\"q_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get(SEQUENCES + "/q_modes")).path("comment").asString());
        ok(post(SEQUENCES + "?createMode=orReplace", "{\"name\":\"q_modes\",\"ordered\":false,\"comment\":\"third\"}"));
        final JsonNode replaced = ok(get(SEQUENCES + "/q_modes"));
        assertEquals("third", replaced.path("comment").asString());
        assertFalse(replaced.path("ordered").asBoolean());
        error(400, post(SEQUENCES, "{\"start\":1}"));
    }

    @Test
    public void cloneAndRename() throws Exception {
        ok(post(SEQUENCES, "{\"name\":\"q_src\",\"start\":10,\"increment\":5}"));
        sql("SELECT seq_db.s1.q_src.NEXTVAL");
        ok(post(SEQUENCES + "/q_src:clone?targetSchema=s2", "{\"name\":\"q_copy\"}"));
        final JsonNode copy = ok(get("/api/v2/databases/seq_db/schemas/s2/sequences/q_copy"));
        assertEquals(15, copy.path("start").asInt(), "the clone continues from where its source stands");
        assertEquals(5, copy.path("increment").asInt());
        error(409, post(SEQUENCES + "/q_src:clone?targetSchema=s2", "{\"name\":\"q_copy\"}"));
        ok(post(SEQUENCES + "/q_src:clone?targetSchema=s2&createMode=orReplace", "{\"name\":\"q_copy\"}"));
        ok(post(SEQUENCES + "/q_src:rename?targetName=q_moved&targetSchema=s2", null));
        error(404, get(SEQUENCES + "/q_src"));
        assertEquals(15, ok(get("/api/v2/databases/seq_db/schemas/s2/sequences/q_moved")).path("start").asInt());
        error(404, post(SEQUENCES + "/nosuch:rename?targetName=q_x", null));
        ok(post(SEQUENCES + "/nosuch:rename?targetName=q_x&ifExists=true", null));
        error(400, post(SEQUENCES + "/q_src:rename", null));
    }
}
