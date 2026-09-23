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

/** The compute pool endpoints over CREATE / SHOW / DESCRIBE / ALTER / DROP COMPUTE POOL. */
public class RestComputePoolTest extends BaseRestTest {

    private static final String POOLS = "/api/v2/compute-pools";

    private static String pool(final String name, final int maxNodes, final String extra) {
        return "{\"name\":\"" + name + "\",\"min_nodes\":1,\"max_nodes\":" + maxNodes
            + ",\"instance_family\":\"CPU_X64_XS\"" + extra + "}";
    }

    @Test
    public void aPoolIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Compute pool CP_BASIC successfully created.", ok(post(POOLS + "?initiallySuspended=true",
            pool("cp_basic", 2, ",\"auto_resume\":false,\"auto_suspend_secs\":600,\"comment\":\"rest\""))).path("status")
            .asString());
        final JsonNode fetched = ok(get(POOLS + "/cp_basic"));
        assertEquals("CP_BASIC", fetched.path("name").asString());
        assertEquals(1, fetched.path("min_nodes").asInt());
        assertEquals(2, fetched.path("max_nodes").asInt());
        assertEquals("CPU_X64_XS", fetched.path("instance_family").asString());
        assertEquals("SUSPENDED", fetched.path("state").asString());
        assertFalse(fetched.path("auto_resume").asBoolean());
        assertEquals(600, fetched.path("auto_suspend_secs").asInt());
        assertEquals("rest", fetched.path("comment").asString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());
        assertTrue(fetched.has("num_services"), fetched.toString());
        assertTrue(names(ok(get(POOLS + "?like=CP_BAS%25"))).contains("CP_BASIC"));
        assertEquals("", names(ok(get(POOLS + "?like=nothing_like_this"))));
        assertEquals("CP_BASIC", names(ok(get(POOLS + "?startsWith=CP_BASIC&showLimit=1"))));
        ok(delete(POOLS + "/cp_basic"));
        error(404, get(POOLS + "/cp_basic"));
        error(404, delete(POOLS + "/cp_basic"));
        ok(delete(POOLS + "/cp_basic?ifExists=true"));
    }

    @Test
    public void createModeSelectsTheCreateSpelling() throws Exception {
        ok(post(POOLS, pool("cp_modes", 2, ",\"comment\":\"first\"")));
        error(409, post(POOLS, pool("cp_modes", 2, ",\"comment\":\"again\"")));
        ok(post(POOLS + "?createMode=ifNotExists", pool("cp_modes", 2, ",\"comment\":\"second\"")));
        assertEquals("first", ok(get(POOLS + "/cp_modes")).path("comment").asString());
        ok(post(POOLS + "?createMode=orReplace", pool("cp_modes", 2, ",\"comment\":\"third\"")));
        assertEquals("third", ok(get(POOLS + "/cp_modes")).path("comment").asString());
        error(400, post(POOLS, "{\"name\":\"cp_bad\",\"min_nodes\":1,\"max_nodes\":1,\"instance_family\":\"x; y\"}"));
    }

    @Test
    public void theActionsChangeTheState() throws Exception {
        ok(post(POOLS + "?initiallySuspended=true", pool("cp_actions", 1, "")));
        ok(post(POOLS + "/cp_actions:resume", null));
        assertFalse("SUSPENDED".equals(ok(get(POOLS + "/cp_actions")).path("state").asString()));
        ok(post(POOLS + "/cp_actions:stop-all-services", null));
        ok(post(POOLS + "/cp_actions:stopallservices", null));
        ok(post(POOLS + "/cp_actions:suspend", null));
        assertEquals("SUSPENDED", ok(get(POOLS + "/cp_actions")).path("state").asString());
        error(404, post(POOLS + "/no_such_pool:resume", null));
    }

    @Test
    public void putCreatesAnAbsentPoolAndResetsWhatALaterBodyLeavesOut() throws Exception {
        ok(put(POOLS + "/cp_put", pool("cp_put", 2, ",\"auto_suspend_secs\":900,\"comment\":\"c\"")));
        JsonNode fetched = ok(get(POOLS + "/cp_put"));
        assertEquals(900, fetched.path("auto_suspend_secs").asInt());
        assertEquals("c", fetched.path("comment").asString());
        ok(put(POOLS + "/cp_put", pool("cp_put", 3, ",\"auto_resume\":false")));
        fetched = ok(get(POOLS + "/cp_put"));
        assertEquals(3, fetched.path("max_nodes").asInt());
        assertFalse(fetched.path("auto_resume").asBoolean());
        assertEquals(3600, fetched.path("auto_suspend_secs").asInt(), fetched.toString());
        assertFalse(fetched.has("comment") && !fetched.path("comment").asString().isEmpty(), fetched.toString());
        error(409, put(POOLS + "/cp_put", pool("other_name", 3, "")));
    }

    @Test
    public void instanceFamiliesAreListed() throws Exception {
        final JsonNode families = ok(get(POOLS + "/instance-families"));
        assertTrue(families.size() > 0);
        assertTrue(names(families).contains("CPU_X64_XS"), families.toString());
        assertTrue(families.get(0).path("storage_gib").isNumber(), families.get(0).toString());
    }

    @Test
    public void tagsAreSetAndUnset() throws Exception {
        sql("CREATE DATABASE cp_tags_db");
        sql("CREATE TAG cp_tags_db.public.pool_tag");
        ok(post(POOLS, pool("cp_tagged", 1, "")));
        ok(post(POOLS + "/cp_tagged:set-tags",
            "[{\"tag_database\":\"cp_tags_db\",\"tag_schema\":\"public\",\"tag_name\":\"pool_tag\",\"tag_value\":\"v\"}]"));
        ok(post(POOLS + "/cp_tagged:unset-tags",
            "[{\"tag_database\":\"cp_tags_db\",\"tag_schema\":\"public\",\"tag_name\":\"pool_tag\"}]"));
    }
}
