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

/** The procedure endpoints over CREATE / SHOW / DESCRIBE / DROP PROCEDURE and CALL. */
public class RestProcedureTest extends BaseRestTest {

    private static final String PROCS = "/api/v2/databases/proc_db/schemas/s/procedures";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE proc_db");
        sql("CREATE SCHEMA proc_db.s");
    }

    private static String procedure(final String name, final String comment) {
        return "{\"name\":\"" + name + "\",\"arguments\":[{\"name\":\"x\",\"datatype\":\"NUMBER\"}],"
            + "\"return_type\":{\"type\":\"DATATYPE\",\"datatype\":\"VARCHAR\"},\"language_config\":{\"language\":\"SQL\"},"
            + "\"execute_as\":\"CALLER\",\"body\":\"BEGIN RETURN 'v' || x; END\",\"comment\":\"" + comment + "\"}";
    }

    @Test
    public void aProcedureIsCreatedFetchedListedCalledAndDropped() throws Exception {
        ok(post(PROCS, procedure("proc_a", "first")));
        final JsonNode fetched = ok(get(PROCS + "/proc_a(NUMBER)"));
        assertEquals("PROC_A", fetched.path("name").asString());
        assertEquals("CALLER", fetched.path("execute_as").asString());
        assertEquals("X", fetched.path("arguments").get(0).path("name").asString());
        assertEquals("VARCHAR", fetched.path("return_type").path("datatype").asString());
        assertEquals("SQL", fetched.path("language_config").path("language").asString());
        assertEquals("BEGIN RETURN 'v' || x; END", fetched.path("body").asString());
        assertEquals("first", fetched.path("comment").asString());
        assertEquals("PROC_DB", fetched.path("database_name").asString());
        assertTrue(names(ok(get(PROCS + "?like=PROC_%25"))).contains("PROC_A"));
        final JsonNode result = ok(post(PROCS + "/proc_a:call",
            "{\"call_arguments\":[{\"name\":\"x\",\"datatype\":\"NUMBER\",\"value\":3}]}"));
        assertEquals("v3", result.get(0).path("proc_a").asString(), result.toString());
        assertEquals("v4", ok(post(PROCS + "/proc_a:call",
            "{\"call_arguments\":[{\"datatype\":\"NUMBER\",\"value\":4}]}")).get(0).path("proc_a").asString());
        // the call takes the bare name: argument types in the path meet CALL's syntax refusal
        error(400, post(PROCS + "/proc_a(NUMBER):call", "{\"call_arguments\":[{\"datatype\":\"NUMBER\",\"value\":4}]}"));
        ok(delete(PROCS + "/proc_a(NUMBER)"));
        error(404, get(PROCS + "/proc_a(NUMBER)"));
        error(404, delete(PROCS + "/proc_a(NUMBER)"));
        ok(delete(PROCS + "/proc_a(NUMBER)?ifExists=true"));
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        ok(post(PROCS, procedure("proc_modes", "first")));
        error(409, post(PROCS, procedure("proc_modes", "again")));
        ok(post(PROCS + "?createMode=ifNotExists", procedure("proc_modes", "second")));
        assertEquals("first", ok(get(PROCS + "/proc_modes(NUMBER)")).path("comment").asString());
        ok(post(PROCS + "?createMode=orReplace", procedure("proc_modes", "third")));
        assertEquals("third", ok(get(PROCS + "/proc_modes(NUMBER)")).path("comment").asString());
    }

    @Test
    public void aTableProcedureAnswersEveryRow() throws Exception {
        ok(post(PROCS, "{\"name\":\"proc_rows\",\"arguments\":[],\"return_type\":{\"type\":\"TABLE\",\"column_list\":"
            + "[{\"name\":\"n\",\"datatype\":\"NUMBER\"}]},\"language_config\":{\"language\":\"SQL\"},\"body\":"
            + "\"DECLARE r RESULTSET DEFAULT (SELECT column1 AS n FROM VALUES (1), (2)); BEGIN RETURN TABLE(r); END\"}"));
        final JsonNode rows = ok(post(PROCS + "/proc_rows:call", "{\"call_arguments\":[]}"));
        assertEquals(2, rows.size(), rows.toString());
        assertEquals("1", rows.get(0).path("n").asString());
        assertEquals(1, ok(post(PROCS + "/proc_rows:call", null)).get(1).size());
    }

    @Test
    public void unsupportedFormsAndMissingProceduresAreRefused() throws Exception {
        error(501, post(PROCS, "{\"name\":\"sec_proc\",\"is_secure\":true,\"arguments\":[],"
            + "\"return_type\":{\"datatype\":\"NUMBER\"},\"language_config\":{\"language\":\"SQL\"},\"body\":\"BEGIN RETURN 1; END\"}"));
        error(501, post(PROCS, "{\"name\":\"rc_proc\",\"execute_as\":\"RESTRICTED CALLER\",\"arguments\":[],"
            + "\"return_type\":{\"datatype\":\"NUMBER\"},\"language_config\":{\"language\":\"SQL\"},\"body\":\"BEGIN RETURN 1; END\"}"));
        error(400, post(PROCS, "{\"name\":\"no_ret\",\"arguments\":[],\"language_config\":{\"language\":\"SQL\"}}"));
        assertEquals("002141", json(post(PROCS + "/no_such_proc:call", "{\"call_arguments\":[]}")).path("code")
            .asString());
        error(400, post(PROCS + "/no_such_proc:call", "{\"call_arguments\":[]}"));
    }

    @Test
    public void tagsAreSetAndUnsetOnTheOverload() throws Exception {
        sql("CREATE TAG proc_db.s.proc_tag");
        ok(post(PROCS, procedure("proc_tagged", "t")));
        ok(post(PROCS + "/proc_tagged(NUMBER):set-tags",
            "[{\"tag_database\":\"proc_db\",\"tag_schema\":\"s\",\"tag_name\":\"proc_tag\",\"tag_value\":\"v\"}]"));
        ok(post(PROCS + "/proc_tagged(NUMBER):unset-tags",
            "[{\"tag_database\":\"proc_db\",\"tag_schema\":\"s\",\"tag_name\":\"proc_tag\"}]"));
    }
}
