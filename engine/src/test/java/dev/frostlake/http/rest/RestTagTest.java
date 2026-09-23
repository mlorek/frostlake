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

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tag endpoints ({@code tag.yaml}) and the lineage of {@code :get-tags}. */
public class RestTagTest extends BaseRestTest {

    private static final String TAGS = "/api/v2/databases/rt_db/schemas/s1/tags";

    private static void database(final String name) {
        sql("CREATE DATABASE IF NOT EXISTS " + name);
        sql("CREATE SCHEMA IF NOT EXISTS " + name + ".s1");
    }

    @Test
    public void aTagIsCreatedFetchedListedAndDropped() throws Exception {
        database("rt_db");
        assertEquals("Tag T_BASIC successfully created.", ok(post(TAGS,
            "{\"name\":\"t_basic\",\"allowed_values\":[\"a\",\"b\"],\"comment\":\"rest\"}")).path("status").asString());
        final JsonNode fetched = ok(get(TAGS + "/t_basic"));
        assertEquals("T_BASIC", fetched.path("name").asString());
        assertEquals("[\"a\",\"b\"]", fetched.path("allowed_values").toString());
        assertEquals("rest", fetched.path("comment").asString());
        assertEquals("RT_DB", fetched.path("database_name").asString());
        assertEquals("S1", fetched.path("schema_name").asString());
        assertEquals("ROLE", fetched.path("owner_role_type").asString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());
        assertEquals("NONE", fetched.path("propagate").asString(), "an unset propagation reads NONE: " + fetched);
        assertTrue(names(ok(get(TAGS + "?like=T_BAS%25"))).contains("T_BASIC"));
        assertEquals("", names(ok(get(TAGS + "?like=nothing_like_this"))));
        assertEquals("T_BASIC successfully dropped.", ok(delete(TAGS + "/t_basic")).path("status").asString());
        error(404, get(TAGS + "/t_basic"));
        error(404, delete(TAGS + "/t_basic"));
        ok(delete(TAGS + "/t_basic?ifExists=true"));
        ok(post(TAGS + "/t_basic:undrop", ""));
        assertEquals("rest", ok(get(TAGS + "/t_basic")).path("comment").asString());
    }

    @Test
    public void createModesAndPropagation() throws Exception {
        database("rt_db");
        ok(post(TAGS, "{\"name\":\"t_modes\",\"comment\":\"first\"}"));
        error(409, post(TAGS + "?createMode=errorIfExists", "{\"name\":\"t_modes\"}"));
        ok(post(TAGS + "?createMode=ifNotExists", "{\"name\":\"t_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get(TAGS + "/t_modes")).path("comment").asString());
        // Ordering by the allowed values needs allowed values.
        final HttpResponse<String> unordered = post(TAGS + "?createMode=orReplace",
            "{\"name\":\"t_modes\",\"propagate\":\"ON_DEPENDENCY\",\"on_conflict\":\"ALLOWED_VALUES_SEQUENCE\"}");
        assertEquals("invalid on_conflict strategy: on conflict as allowed_values_sequence requires allowed values to "
            + "be added to the tag", error(400, unordered));
        assertEquals("391892", json(unordered).path("code").asString());
        ok(post(TAGS + "?createMode=orReplace", "{\"name\":\"t_modes\",\"allowed_values\":[\"a\",\"b\"],"
            + "\"propagate\":\"ON_DEPENDENCY\",\"on_conflict\":\"ALLOWED_VALUES_SEQUENCE\"}"));
        final JsonNode fetched = ok(get(TAGS + "/t_modes"));
        assertEquals("ON_DEPENDENCY", fetched.path("propagate").asString());
        assertEquals("ALLOWED_VALUES_SEQUENCE", fetched.path("on_conflict").asString());
        error(400, post(TAGS, "{\"name\":\"t_bad\",\"on_conflict\":\"x\"}"));
        error(400, post(TAGS, "{\"name\":\"t_bad\",\"propagate\":\"ON DEPENDENCY; DROP\"}"));
        error(400, post(TAGS, "{\"comment\":\"no name\"}"));
    }

    @Test
    public void putCreatesAndThenReshapesTheTag() throws Exception {
        database("rt_db");
        ok(put(TAGS + "/t_put", "{\"name\":\"t_put\",\"allowed_values\":[\"x\"],\"comment\":\"c\","
            + "\"propagate\":\"ON_DATA_MOVEMENT\"}"));
        JsonNode fetched = ok(get(TAGS + "/t_put"));
        assertEquals("[\"x\"]", fetched.path("allowed_values").toString());
        assertEquals("ON_DATA_MOVEMENT", fetched.path("propagate").asString());
        ok(put(TAGS + "/t_put", "{\"name\":\"t_put\",\"allowed_values\":[\"y\",\"z\"]}"));
        fetched = ok(get(TAGS + "/t_put"));
        assertEquals("[\"y\",\"z\"]", fetched.path("allowed_values").toString());
        assertTrue(fetched.get("comment").isNull(), "a tag without a comment answers null: " + fetched);
        assertEquals("NONE", fetched.path("propagate").asString(), fetched.toString());
        error(400, put(TAGS + "/t_put", "{\"name\":\"other\"}"));
    }

    @Test
    public void renameMovesTheTag() throws Exception {
        database("rt_db");
        sql("CREATE SCHEMA IF NOT EXISTS rt_db.s2");
        ok(post(TAGS, "{\"name\":\"t_old\"}"));
        ok(post(TAGS + "/t_old:rename?targetName=t_new", ""));
        error(404, get(TAGS + "/t_old"));
        ok(get(TAGS + "/t_new"));
        ok(post(TAGS + "/t_new:rename?targetSchema=s2&targetName=t_moved", ""));
        ok(get("/api/v2/databases/rt_db/schemas/s2/tags/t_moved"));
        error(400, post(TAGS + "/t_moved:rename", ""));
        error(404, post(TAGS + "/t_nosuch:rename?targetName=x", ""));
        ok(post(TAGS + "/t_nosuch:rename?ifExists=true&targetName=x", ""));
    }

    @Test
    public void getTagsListsInheritedAssignmentsOnlyWithLineage() throws Exception {
        database("rt_lineage");
        sql("CREATE TAG rt_lineage.s1.owner");
        sql("CREATE WAREHOUSE rt_wh_lineage");
        sql("ALTER WAREHOUSE rt_wh_lineage SET TAG rt_lineage.s1.owner = 'ops'");
        final JsonNode direct = ok(get("/api/v2/warehouses/rt_wh_lineage:get-tags"));
        assertEquals(1, direct.size(), direct.toString());
        assertEquals("RT_LINEAGE", direct.get(0).path("tag_database").asString());
        assertEquals("S1", direct.get(0).path("tag_schema").asString());
        assertEquals("OWNER", direct.get(0).path("tag_name").asString());
        assertEquals("WAREHOUSE", direct.get(0).path("level").asString());
    }
}
