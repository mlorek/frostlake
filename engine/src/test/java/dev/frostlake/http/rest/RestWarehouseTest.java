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

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The warehouse endpoints ({@code warehouse.yaml}), each translated into the SQL the engine answers. */
public class RestWarehouseTest extends BaseRestTest {

    @Test
    public void aWarehouseIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Warehouse WH_BASIC successfully created.", ok(post("/api/v2/warehouses",
            "{\"name\":\"wh_basic\",\"warehouse_size\":\"SMALL\",\"auto_suspend\":120,\"comment\":\"rest\"}"))
            .path("status").asString());

        final JsonNode fetched = ok(get("/api/v2/warehouses/wh_basic"));
        assertEquals("WH_BASIC", fetched.path("name").asString());
        assertEquals("Small", fetched.path("warehouse_size").asString());
        assertEquals(120, fetched.path("auto_suspend").asInt());
        assertEquals("rest", fetched.path("comment").asString());
        assertEquals("true", fetched.path("auto_resume").asString());
        assertEquals(8, fetched.path("max_concurrency_level").asInt());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());
        assertTrue(fetched.get("resource_monitor").isNull(), "an unset monitor is null: " + fetched);
        assertTrue(fetched.get("budget").isNull(), "every schema property is sent: " + fetched);
        assertTrue(fetched.get("initially_suspended").isNull(), fetched.toString());
        final JsonNode listed = ok(get("/api/v2/warehouses?like=WH_BASIC")).get(0);
        assertEquals(8, listed.path("max_concurrency_level").asInt(), "a listing carries the parameters too");

        assertTrue(names(ok(get("/api/v2/warehouses?like=WH_BAS%25"))).contains("WH_BASIC"));
        assertEquals("", names(ok(get("/api/v2/warehouses?like=nothing_like_this"))));

        assertEquals("WH_BASIC successfully dropped.", ok(delete("/api/v2/warehouses/wh_basic"))
            .path("status").asString());
        error(404, get("/api/v2/warehouses/wh_basic"));
        error(404, delete("/api/v2/warehouses/wh_basic"));
        ok(delete("/api/v2/warehouses/wh_basic?ifExists=true"));
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        ok(post("/api/v2/warehouses", "{\"name\":\"wh_modes\",\"comment\":\"first\"}"));
        error(409, post("/api/v2/warehouses?createMode=errorIfExists", "{\"name\":\"wh_modes\"}"));
        ok(post("/api/v2/warehouses?createMode=ifNotExists", "{\"name\":\"wh_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get("/api/v2/warehouses/wh_modes")).path("comment").asString());
        ok(post("/api/v2/warehouses?createMode=orReplace", "{\"name\":\"wh_modes\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get("/api/v2/warehouses/wh_modes")).path("comment").asString());
    }

    @Test
    public void aCreateWithoutANameIs400() throws Exception {
        assertEquals("390400", json(post("/api/v2/warehouses", "{\"warehouse_size\":\"SMALL\"}")).path("code")
            .asString());
    }

    @Test
    public void putCreatesAnAbsentWarehouseAndResetsWhatALaterBodyLeavesOut() throws Exception {
        ok(put("/api/v2/warehouses/wh_put", "{\"name\":\"wh_put\",\"warehouse_size\":\"MEDIUM\",\"comment\":\"c\"}"));
        JsonNode fetched = ok(get("/api/v2/warehouses/wh_put"));
        assertEquals("Medium", fetched.path("warehouse_size").asString());
        assertEquals("c", fetched.path("comment").asString());

        ok(put("/api/v2/warehouses/wh_put", "{\"name\":\"wh_put\",\"auto_suspend\":60}"));
        fetched = ok(get("/api/v2/warehouses/wh_put"));
        assertEquals("X-Small", fetched.path("warehouse_size").asString(), "an omitted size goes back to the default");
        assertEquals(60, fetched.path("auto_suspend").asInt());
        assertTrue(fetched.get("comment").isNull(), "an omitted comment is unset: " + fetched);
    }

    @Test
    public void aPutWhoseBodyNamesAnotherWarehouseIsTheCreateOrAlterConflict() throws Exception {
        final HttpResponse<String> response = put("/api/v2/warehouses/wh_put_a", "{\"name\":\"wh_put_b\"}");
        assertEquals("Retryable race condition in create or alter", error(409, response));
        assertEquals("001520", json(response).path("code").asString());
    }

    @Test
    public void actionsSuspendResumeAbortAndRename() throws Exception {
        ok(post("/api/v2/warehouses", "{\"name\":\"wh_actions\"}"));
        ok(post("/api/v2/warehouses/wh_actions:suspend", null));
        assertEquals("SUSPENDED", ok(get("/api/v2/warehouses/wh_actions")).path("state").asString());
        ok(post("/api/v2/warehouses/wh_actions:resume", null));
        assertEquals("STARTED", ok(get("/api/v2/warehouses/wh_actions")).path("state").asString());
        ok(post("/api/v2/warehouses/wh_actions:resume", null));
        ok(post("/api/v2/warehouses/wh_actions:abort", null));
        ok(post("/api/v2/warehouses/wh_actions:rename", "{\"name\":\"wh_renamed\"}"));
        error(404, get("/api/v2/warehouses/wh_actions"));
        ok(get("/api/v2/warehouses/wh_renamed"));
        error(404, post("/api/v2/warehouses/wh_actions:suspend", null));
        ok(post("/api/v2/warehouses/wh_actions:suspend?ifExists=true", null));
    }

    @Test
    public void useBindsTheWarehouseToTheSessionTheRequestNames() throws Exception {
        ok(post("/api/v2/warehouses", "{\"name\":\"wh_use\"}"));
        final String session = server.getEngine().createSession().getSessionId();
        final HttpResponse<String> used = client.send(HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/v2/warehouses/wh_use:use"))
            .header("X-Sfc-Session", session)
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        ok(used);
        assertEquals("WH_USE", server.getEngine().executeQuery("SELECT CURRENT_WAREHOUSE()",
            server.getEngine().getSession(session)).getRows().get(0).getValue(0));
        error(404, post("/api/v2/warehouses/wh_no_such:use", null));
    }

    @Test
    public void anAdaptiveWarehouseIsEnabledAndDisabled() throws Exception {
        ok(post("/api/v2/warehouses", "{\"name\":\"wh_adaptive\",\"warehouse_type\":\"ADAPTIVE\"}"));
        assertEquals("ADAPTIVE", ok(get("/api/v2/warehouses/wh_adaptive")).path("warehouse_type").asString());
        ok(post("/api/v2/warehouses/wh_adaptive:disable", null));
        ok(post("/api/v2/warehouses/wh_adaptive:enable", null));
        ok(post("/api/v2/warehouses", "{\"name\":\"wh_not_adaptive\"}"));
        error(400, post("/api/v2/warehouses/wh_not_adaptive:disable", null));
        error(404, post("/api/v2/warehouses/wh_no_such:enable", null));
        ok(post("/api/v2/warehouses/wh_no_such:enable?ifExists=true", null));
    }

    @Test
    public void theResourceConstraintIsSetAndResetByPut() throws Exception {
        ok(put("/api/v2/warehouses/wh_constraint", "{\"name\":\"wh_constraint\","
            + "\"warehouse_type\":\"SNOWPARK-OPTIMIZED\",\"resource_constraint\":\"MEMORY_1X\"}"));
        assertEquals("MEMORY_1X", ok(get("/api/v2/warehouses/wh_constraint")).path("resource_constraint").asString());
        ok(put("/api/v2/warehouses/wh_constraint", "{\"name\":\"wh_constraint\","
            + "\"warehouse_type\":\"SNOWPARK-OPTIMIZED\",\"warehouse_size\":\"MEDIUM\",\"wait_for_completion\":\"true\"}"));
        assertEquals("MEMORY_16X", ok(get("/api/v2/warehouses/wh_constraint")).path("resource_constraint").asString());
        ok(put("/api/v2/warehouses/wh_gen", "{\"name\":\"wh_gen\",\"generation\":\"1\"}"));
        ok(put("/api/v2/warehouses/wh_gen", "{\"name\":\"wh_gen\",\"generation\":\"1\"}"));
        final JsonNode gen = ok(get("/api/v2/warehouses/wh_gen"));
        assertEquals("1", gen.path("generation").asString());
        assertEquals("STANDARD_GEN_1", gen.path("resource_constraint").asString());
        error(400, post("/api/v2/warehouses", "{\"name\":\"wh_bad_constraint\",\"resource_constraint\":\"MEMORY_2X\"}"));
        assertEquals("000682", json(post("/api/v2/warehouses", "{\"name\":\"wh_gen_constraint\","
            + "\"resource_constraint\":\"STANDARD_GEN_1\"}")).path("code").asString());
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE DATABASE wh_tag_db");
        sql("CREATE SCHEMA wh_tag_db.s");
        sql("CREATE TAG wh_tag_db.s.cost_center");
        ok(post("/api/v2/warehouses", "{\"name\":\"wh_tagged\"}"));
        ok(post("/api/v2/warehouses/wh_tagged:set-tags",
            "[{\"tag_database\":\"wh_tag_db\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\",\"tag_value\":\"fin\"}]"));
        final JsonNode tags = ok(get("/api/v2/warehouses/wh_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("COST_CENTER", tags.get(0).path("tag_name").asString());
        assertEquals("fin", tags.get(0).path("tag_value").asString());
        assertEquals("WAREHOUSE", tags.get(0).path("level").asString());
        ok(post("/api/v2/warehouses/wh_tagged:unset-tags",
            "[{\"tag_database\":\"wh_tag_db\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\"}]"));
        assertEquals(0, ok(get("/api/v2/warehouses/wh_tagged:get-tags")).size());
    }
}
