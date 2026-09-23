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

/** The image repository endpoints over CREATE / DROP / SHOW IMAGE REPOSITORIES and SHOW IMAGES. */
public class RestImageRepositoryTest extends BaseRestTest {

    private static final String REPOS = "/api/v2/databases/img_db/schemas/s/image-repositories";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE img_db");
        sql("CREATE SCHEMA img_db.s");
    }

    @Test
    public void aRepositoryIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Image Repository REPO_A successfully created.", ok(post(REPOS, "{\"name\":\"repo_a\"}"))
            .path("status").asString());
        final JsonNode fetched = ok(get(REPOS + "/repo_a"));
        assertEquals("REPO_A", fetched.path("name").asString());
        assertEquals("IMG_DB", fetched.path("database_name").asString());
        assertEquals("S", fetched.path("schema_name").asString());
        assertTrue(fetched.path("repository_url").asString().endsWith(".registry.snowflakecomputing.com/img_db/s/repo_a"),
            fetched.toString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());
        assertTrue(names(ok(get(REPOS + "?like=REPO_%25"))).contains("REPO_A"));
        assertEquals("", names(ok(get(REPOS + "?like=nothing_like_this"))));
        assertEquals(0, ok(get(REPOS + "/repo_a/images")).size());
        ok(delete(REPOS + "/repo_a"));
        error(404, get(REPOS + "/repo_a"));
        error(404, delete(REPOS + "/repo_a"));
        error(404, get(REPOS + "/repo_a/images"));
        ok(delete(REPOS + "/repo_a?ifExists=true"));
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        ok(post(REPOS, "{\"name\":\"repo_modes\"}"));
        error(409, post(REPOS, "{\"name\":\"repo_modes\"}"));
        ok(post(REPOS + "?createMode=ifNotExists", "{\"name\":\"repo_modes\"}"));
        ok(post(REPOS + "?createMode=orReplace", "{\"name\":\"repo_modes\"}"));
        error(400, post(REPOS, "{}"));
    }

    @Test
    public void tagsAreSetThroughAlter() throws Exception {
        sql("CREATE TAG img_db.s.repo_tag");
        ok(post(REPOS, "{\"name\":\"repo_tags\"}"));
        ok(post(REPOS + "/repo_tags:set-tags",
            "[{\"tag_database\":\"img_db\",\"tag_schema\":\"s\",\"tag_name\":\"repo_tag\",\"tag_value\":\"v\"}]"));
        ok(post(REPOS + "/repo_tags:unset-tags",
            "[{\"tag_database\":\"img_db\",\"tag_schema\":\"s\",\"tag_name\":\"repo_tag\"}]"));
        error(404, post(REPOS + "/no_such_repo:set-tags",
            "[{\"tag_database\":\"img_db\",\"tag_schema\":\"s\",\"tag_name\":\"repo_tag\",\"tag_value\":\"v\"}]"));
    }
}
