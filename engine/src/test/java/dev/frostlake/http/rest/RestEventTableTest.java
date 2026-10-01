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

/** The event table endpoints ({@code event-table.yaml}). */
public class RestEventTableTest extends BaseRestTest {

    private static final String BASE = "/api/v2/databases/re_db/schemas/s1/event-tables";

    @BeforeAll
    public static void schema() {
        sql("CREATE DATABASE IF NOT EXISTS re_db");
        sql("CREATE SCHEMA IF NOT EXISTS re_db.s1");
    }

    @Test
    public void anEventTableIsCreatedFetchedListedRenamedAndDropped() throws Exception {
        assertEquals("Table EV1 successfully created.", ok(post(BASE,
            "{\"name\":\"ev1\",\"comment\":\"telemetry\",\"change_tracking\":true,"
                + "\"data_retention_time_in_days\":1}")).path("status").asString());
        final JsonNode fetched = ok(get(BASE + "/ev1"));
        assertEquals("EV1", fetched.path("name").asString());
        assertEquals("telemetry", fetched.path("comment").asString());
        assertEquals("RE_DB", fetched.path("database_name").asString());
        assertEquals("S1", fetched.path("schema_name").asString());
        assertTrue(fetched.path("change_tracking").asBoolean(), fetched.toString());
        assertEquals(1, fetched.path("data_retention_time_in_days").asInt());
        assertEquals(13, fetched.path("columns").size());
        assertEquals("TIMESTAMP", fetched.path("columns").get(0).path("name").asString());
        assertEquals("EXEMPLARS", fetched.path("columns").get(12).path("name").asString());

        assertEquals("EV1", names(ok(get(BASE + "?like=EV%25"))));
        assertEquals(13, ok(get(BASE + "?like=EV1")).get(0).path("columns").size(),
            "a listing carries what a fetch does");

        ok(post(BASE + "/ev1:rename?targetName=ev2", ""));
        error(404, get(BASE + "/ev1"));
        assertEquals("EV2", ok(get(BASE + "/ev2")).path("name").asString());
        error(400, post(BASE + "/ev2:rename", ""));

        ok(delete(BASE + "/ev2"));
        error(404, get(BASE + "/ev2"));
        error(404, delete(BASE + "/ev2"));
        ok(delete(BASE + "/ev2?ifExists=true"));
    }

    @Test
    public void createModesAndTags() throws Exception {
        ok(post(BASE, "{\"name\":\"ev_modes\",\"comment\":\"first\"}"));
        error(409, post(BASE, "{\"name\":\"ev_modes\"}"));
        ok(post(BASE + "?createMode=ifNotExists", "{\"name\":\"ev_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get(BASE + "/ev_modes")).path("comment").asString());
        ok(post(BASE + "?createMode=orReplace", "{\"name\":\"ev_modes\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get(BASE + "/ev_modes")).path("comment").asString());

        sql("CREATE TAG IF NOT EXISTS re_db.s1.re_tag");
        ok(post(BASE + "/ev_modes:set-tags",
            "[{\"tag_database\":\"re_db\",\"tag_schema\":\"s1\",\"tag_name\":\"re_tag\",\"tag_value\":\"v\"}]"));
        final JsonNode tags = ok(get(BASE + "/ev_modes:get-tags"));
        assertEquals("v", tags.get(0).path("tag_value").asString(), tags.toString());
        ok(post(BASE + "/ev_modes:unset-tags", "[{\"tag_database\":\"re_db\",\"tag_schema\":\"s1\","
            + "\"tag_name\":\"re_tag\"}]"));
        assertEquals(0, ok(get(BASE + "/ev_modes:get-tags")).size());
    }
}
