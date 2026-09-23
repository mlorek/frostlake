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

/** The artifact repository endpoints over CREATE / ALTER / DROP / SHOW ARTIFACT REPOSITORIES. */
public class RestArtifactRepositoryTest extends BaseRestTest {

    private static final String REPOS = "/api/v2/databases/art_db/schemas/s/artifact-repositories";

    @BeforeAll
    public static void createSchema() {
        sql("CREATE DATABASE art_db");
        sql("CREATE SCHEMA art_db.s");
    }

    private static String repository(final String name, final String comment) {
        return "{\"name\":\"" + name + "\",\"type\":\"PIP\",\"api_integration\":\"pypi_int\""
            + (comment == null ? "" : ",\"comment\":\"" + comment + "\"") + "}";
    }

    @Test
    public void aRepositoryIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Artifact Repository AR_A successfully created.", ok(post(REPOS, repository("ar_a", "first")))
            .path("status").asString());
        final JsonNode fetched = ok(get(REPOS + "/ar_a"));
        assertEquals("AR_A", fetched.path("name").asString());
        assertEquals("PIP", fetched.path("type").asString());
        assertEquals("PYPI_INT", fetched.path("api_integration").asString());
        assertEquals("first", fetched.path("comment").asString());
        assertEquals("ART_DB", fetched.path("database_name").asString());
        assertTrue(names(ok(get(REPOS + "?like=AR_%25"))).contains("AR_A"));
        assertEquals("AR_A", names(ok(get(REPOS + "?startsWith=AR_A&showLimit=1"))));
        ok(delete(REPOS + "/ar_a"));
        error(404, get(REPOS + "/ar_a"));
        error(404, delete(REPOS + "/ar_a"));
        ok(delete(REPOS + "/ar_a?ifExists=true"));
    }

    @Test
    public void createModesAndPut() throws Exception {
        ok(post(REPOS, repository("ar_modes", "first")));
        error(409, post(REPOS, repository("ar_modes", "again")));
        ok(post(REPOS + "?createMode=ifNotExists", repository("ar_modes", "second")));
        assertEquals("first", ok(get(REPOS + "/ar_modes")).path("comment").asString());
        ok(post(REPOS + "?createMode=orReplace", repository("ar_modes", "third")));
        assertEquals("third", ok(get(REPOS + "/ar_modes")).path("comment").asString());
        ok(put(REPOS + "/ar_modes", repository("ar_modes", "fourth")));
        assertEquals("fourth", ok(get(REPOS + "/ar_modes")).path("comment").asString());
        ok(put(REPOS + "/ar_modes", repository("ar_modes", null)));
        assertTrue(ok(get(REPOS + "/ar_modes")).path("comment").isNull());
        ok(put(REPOS + "/ar_put", repository("ar_put", "created")));
        assertEquals("created", ok(get(REPOS + "/ar_put")).path("comment").asString());
        error(400, post(REPOS, "{\"name\":\"ar_bad\",\"type\":\"MAVEN\"}"));
        error(501, post(REPOS + "/ar_put:rename?targetName=ar_new", null));
    }
}
