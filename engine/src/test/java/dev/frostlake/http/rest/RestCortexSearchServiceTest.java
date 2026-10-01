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

/** The Cortex Search Service endpoints ({@code cortex-search-service.yaml}). */
public class RestCortexSearchServiceTest extends BaseRestTest {

    private static final String BASE = "/api/v2/databases/css_db/schemas/s/cortex-search-services";

    @BeforeAll
    public static void createSource() {
        sql("CREATE DATABASE css_db");
        sql("CREATE SCHEMA css_db.s");
        sql("CREATE WAREHOUSE css_wh");
        sql("CREATE TABLE css_db.s.docs (id INT, body STRING, cat STRING)");
    }

    private static String body(final String name) {
        return "{\"name\":\"" + name + "\",\"search_column\":\"body\",\"attribute_columns\":[\"cat\"],"
            + "\"warehouse\":\"css_wh\",\"target_lag\":{\"type\":\"USER_DEFINED\",\"seconds\":3600},"
            + "\"definition\":\"SELECT id, body, cat FROM css_db.s.docs\",\"comment\":\"docs\"}";
    }

    @Test
    public void aServiceIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Cortex search service SVC_BASIC successfully created.",
            ok(post(BASE, body("svc_basic"))).path("status").asString());
        final JsonNode fetched = ok(get(BASE + "/svc_basic"));
        assertEquals("SVC_BASIC", fetched.path("name").asString());
        assertEquals("BODY", fetched.path("search_column").asString());
        assertEquals("CAT", fetched.path("attribute_columns").get(0).asString());
        assertEquals(3, fetched.path("columns").size());
        assertEquals("USER_DEFINED", fetched.path("target_lag").path("type").asString());
        assertEquals(3600, fetched.path("target_lag").path("seconds").asInt());
        assertEquals("CSS_WH", fetched.path("warehouse").asString());
        assertEquals("docs", fetched.path("comment").asString());
        assertEquals("CSS_DB", fetched.path("database_name").asString());
        assertEquals("ACTIVE", fetched.path("indexing_state").asString());
        assertEquals("ACTIVE", fetched.path("serving_state").asString());
        assertTrue(fetched.path("definition").asString().contains("FROM css_db.s.docs"), fetched.toString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());

        assertTrue(names(ok(get(BASE + "?like=SVC_B%25"))).contains("SVC_BASIC"));
        assertEquals("", names(ok(get(BASE + "?like=nothing_like_this"))));

        ok(delete(BASE + "/svc_basic"));
        error(404, get(BASE + "/svc_basic"));
        error(404, delete(BASE + "/svc_basic"));
        ok(delete(BASE + "/svc_basic?ifExists=true"));
    }

    @Test
    public void createModesAndMissingProperties() throws Exception {
        ok(post(BASE, body("svc_modes")));
        error(409, post(BASE, body("svc_modes")));
        ok(post(BASE + "?createMode=ifNotExists", body("svc_modes")));
        ok(post(BASE + "?createMode=orReplace", body("svc_modes")));
        error(400, post(BASE, "{\"name\":\"svc_x\",\"search_column\":\"body\",\"warehouse\":\"css_wh\","
            + "\"definition\":\"SELECT id, body FROM css_db.s.docs\"}"));
        error(400, post(BASE, "{\"name\":\"svc_x\",\"search_column\":\"body\",\"warehouse\":\"css_wh\","
            + "\"target_lag\":{\"type\":\"DOWNSTREAM\"},\"definition\":\"SELECT id, body FROM css_db.s.docs\"}"));
    }

    @Test
    public void suspendAndResumeActOnTheNamedLayer() throws Exception {
        ok(post(BASE, body("svc_layers")));
        ok(post(BASE + "/svc_layers:suspend?target=serving", null));
        JsonNode fetched = ok(get(BASE + "/svc_layers"));
        assertEquals("ACTIVE", fetched.path("indexing_state").asString());
        assertEquals("SUSPENDED", fetched.path("serving_state").asString());
        ok(post(BASE + "/svc_layers:suspend", null));
        assertEquals("SUSPENDED", ok(get(BASE + "/svc_layers")).path("indexing_state").asString());
        ok(post(BASE + "/svc_layers:resume?target=INDEXING", null));
        fetched = ok(get(BASE + "/svc_layers"));
        assertEquals("ACTIVE", fetched.path("indexing_state").asString());
        assertEquals("SUSPENDED", fetched.path("serving_state").asString());
        error(400, post(BASE + "/svc_layers:resume?target=everything", null));
        error(404, post(BASE + "/svc_nosuch:resume", null));
        ok(post(BASE + "/svc_nosuch:resume?ifExists=true", null));
    }

    @Test
    public void queryNeedsTheAiModuleAndSuggestAndFeedbackAreNotProvided() throws Exception {
        ok(post(BASE, body("svc_query")));
        assertTrue(error(501, post(BASE + "/svc_query:query", "{\"query\":\"battery\",\"limit\":1}"))
            .contains("frostlake-ai"));
        error(404, post(BASE + "/svc_nosuch:query", "{\"query\":\"battery\"}"));
        error(501, post(BASE + "/svc_query:suggest", "{\"query\":\"bat\"}"));
        error(501, post(BASE + "/svc_query:feedback", "{\"request_id\":\"r\",\"positive\":true}"));
        assertTrue(ok(get(BASE + "/svc_query")).path("indexing_error").isNull());
    }
}
