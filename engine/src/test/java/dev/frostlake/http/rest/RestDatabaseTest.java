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

/** The database endpoints ({@code database.yaml}), each translated into the SQL the engine answers. */
public class RestDatabaseTest extends BaseRestTest {

    @Test
    public void aDatabaseIsCreatedFetchedListedAndDropped() throws Exception {
        assertEquals("Database RDB_BASIC successfully created.", ok(post("/api/v2/databases",
            "{\"name\":\"rdb_basic\",\"comment\":\"rest\",\"data_retention_time_in_days\":3,"
                + "\"log_level\":\"WARN\",\"max_data_extension_time_in_days\":7,\"user_task_timeout_ms\":5000}"))
            .path("status").asString());
        final JsonNode fetched = ok(get("/api/v2/databases/rdb_basic"));
        assertEquals("RDB_BASIC", fetched.path("name").asString());
        assertEquals("PERMANENT", fetched.path("kind").asString());
        assertEquals("rest", fetched.path("comment").asString());
        assertEquals(3, fetched.path("retention_time").asInt());
        assertEquals(3, fetched.path("data_retention_time_in_days").asInt());
        assertEquals(7, fetched.path("max_data_extension_time_in_days").asInt());
        assertEquals("OFF", fetched.path("log_level").asString(), "log_level in a body is ignored, as the account does");
        assertEquals("OFF", fetched.path("trace_level").asString());
        assertEquals(5000, fetched.path("user_task_timeout_ms").asInt());
        assertEquals(10, fetched.path("suspend_task_after_num_failures").asInt());
        assertEquals("OPTIMIZED", fetched.path("storage_serialization_policy").asString());
        assertFalse(fetched.path("replace_invalid_characters").asBoolean(true), fetched.toString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());
        assertTrue(fetched.get("dropped_on").isNull(), fetched.toString());
        assertTrue(fetched.get("budget").isNull(), "every schema property is sent: " + fetched);
        assertTrue(fetched.get("object_visibility").isNull(), fetched.toString());

        assertTrue(names(ok(get("/api/v2/databases?like=RDB_BAS%25"))).contains("RDB_BASIC"));
        assertEquals("", names(ok(get("/api/v2/databases?like=nothing_like_this"))));

        assertEquals("RDB_BASIC successfully dropped.", ok(delete("/api/v2/databases/rdb_basic"))
            .path("status").asString());
        error(404, get("/api/v2/databases/rdb_basic"));
        error(404, delete("/api/v2/databases/rdb_basic"));
        ok(delete("/api/v2/databases/rdb_basic?ifExists=true"));
    }

    @Test
    public void listingFiltersTranslateToShowModifiers() throws Exception {
        ok(post("/api/v2/databases", "{\"name\":\"rdb_list_a\"}"));
        ok(post("/api/v2/databases", "{\"name\":\"rdb_list_b\"}"));
        ok(post("/api/v2/databases", "{\"name\":\"rdb_list_c\"}"));
        assertEquals("RDB_LIST_A,RDB_LIST_B,RDB_LIST_C", names(ok(get("/api/v2/databases?startsWith=RDB_LIST"))));
        assertEquals("RDB_LIST_A,RDB_LIST_B",
            names(ok(get("/api/v2/databases?startsWith=RDB_LIST&showLimit=2"))));
        assertEquals("RDB_LIST_C",
            names(ok(get("/api/v2/databases?startsWith=RDB_LIST&showLimit=5&fromName=RDB_LIST_B"))));
        error(400, get("/api/v2/databases?showLimit=0"));

        ok(delete("/api/v2/databases/rdb_list_c"));
        assertEquals("RDB_LIST_A,RDB_LIST_B", names(ok(get("/api/v2/databases?like=RDB_LIST%25"))));
        final JsonNode history = ok(get("/api/v2/databases?like=RDB_LIST%25&history=true"));
        assertEquals("RDB_LIST_A,RDB_LIST_B,RDB_LIST_C", names(history));
        assertFalse(history.get(2).get("dropped_on").isNull(), history.toString());
        assertTrue(history.get(0).get("dropped_on").isNull(), history.toString());
        assertEquals(1, history.get(0).path("data_retention_time_in_days").asInt(), "a listing carries the parameters");
        ok(post("/api/v2/databases/rdb_list_c:undrop", null));
        ok(get("/api/v2/databases/rdb_list_c"));
        error(400, post("/api/v2/databases/rdb_list_none:undrop", null));
    }

    @Test
    public void createModeAndKindSelectTheCreateSpelling() throws Exception {
        ok(post("/api/v2/databases", "{\"name\":\"rdb_modes\",\"comment\":\"first\"}"));
        error(409, post("/api/v2/databases?createMode=errorIfExists", "{\"name\":\"rdb_modes\"}"));
        ok(post("/api/v2/databases?createMode=ifNotExists", "{\"name\":\"rdb_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get("/api/v2/databases/rdb_modes")).path("comment").asString());
        ok(post("/api/v2/databases?createMode=orReplace", "{\"name\":\"rdb_modes\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get("/api/v2/databases/rdb_modes")).path("comment").asString());

        ok(post("/api/v2/databases?kind=transient", "{\"name\":\"rdb_transient\"}"));
        final JsonNode transientDb = ok(get("/api/v2/databases/rdb_transient"));
        assertEquals("TRANSIENT", transientDb.path("kind").asString());
        assertEquals("TRANSIENT", transientDb.path("options").asString());
        ok(post("/api/v2/databases", "{\"name\":\"rdb_transient2\",\"kind\":\"TRANSIENT\"}"));
        assertEquals("TRANSIENT", ok(get("/api/v2/databases/rdb_transient2")).path("kind").asString());
        ok(post("/api/v2/databases?kind=TRANSIENT", "{\"name\":\"rdb_upper_kind\"}"));
        assertEquals("PERMANENT", ok(get("/api/v2/databases/rdb_upper_kind")).path("kind").asString(),
            "only the lower-case query word is read, as the account reads it");
        error(400, post("/api/v2/databases", "{\"name\":\"rdb_temporary\",\"kind\":\"TEMPORARY\"}"));
        error(400, post("/api/v2/databases", "{\"comment\":\"no name\"}"));
    }

    @Test
    public void aCloneTakesItsPointOfTimeAndProperties() throws Exception {
        ok(post("/api/v2/databases", "{\"name\":\"rdb_src\",\"suspend_task_after_num_failures\":4}"));
        sql("CREATE TABLE rdb_src.public.t (id INT)");
        sql("INSERT INTO rdb_src.public.t VALUES (1), (2)");
        assertEquals("Database RDB_COPY successfully created.", ok(post("/api/v2/databases/rdb_src:clone",
            "{\"name\":\"rdb_copy\",\"comment\":\"copied\"}")).path("status").asString());
        final JsonNode copy = ok(get("/api/v2/databases/rdb_copy"));
        assertEquals("copied", copy.path("comment").asString());
        assertEquals(4, copy.path("suspend_task_after_num_failures").asInt());
        ok(post("/api/v2/databases/rdb_src:clone?createMode=orReplace", "{\"name\":\"rdb_copy\","
            + "\"point_of_time\":{\"point_of_time_type\":\"offset\",\"reference\":\"at\",\"offset\":\"-60\"}}"));
        ok(post("/api/v2/databases/rdb_src:clone", "{\"name\":\"rdb_copy_ts\",\"point_of_time\":"
            + "{\"point_of_time_type\":\"timestamp\",\"reference\":\"before\",\"timestamp\":\"2020-01-01T00:00:00Z\"}}"));
        error(400, post("/api/v2/databases/rdb_src:clone", "{\"name\":\"rdb_copy_bad\",\"point_of_time\":"
            + "{\"point_of_time_type\":\"offset\",\"offset\":\"1; DROP DATABASE rdb_src\"}}"));
        error(404, post("/api/v2/databases/rdb_no_source:clone", "{\"name\":\"rdb_copy_none\"}"));
    }

    @Test
    public void putCreatesAnAbsentDatabaseAndResetsWhatALaterBodyLeavesOut() throws Exception {
        ok(put("/api/v2/databases/rdb_put", "{\"name\":\"rdb_put\",\"comment\":\"c\",\"max_data_extension_time_in_days\":9,"
            + "\"data_retention_time_in_days\":4}"));
        JsonNode fetched = ok(get("/api/v2/databases/rdb_put"));
        assertEquals("c", fetched.path("comment").asString());
        assertEquals(9, fetched.path("max_data_extension_time_in_days").asInt());
        assertEquals(4, fetched.path("data_retention_time_in_days").asInt());

        ok(put("/api/v2/databases/rdb_put", "{\"name\":\"rdb_put\",\"user_task_timeout_ms\":7000}"));
        fetched = ok(get("/api/v2/databases/rdb_put"));
        assertEquals(7000, fetched.path("user_task_timeout_ms").asInt());
        assertEquals(14, fetched.path("max_data_extension_time_in_days").asInt(),
            "an omitted parameter goes back to its default");
        assertEquals(1, fetched.path("data_retention_time_in_days").asInt());
        assertTrue(fetched.get("comment").isNull(), "an omitted comment is unset: " + fetched);
        error(400, put("/api/v2/databases/rdb_put", "{\"name\":\"rdb_put\",\"kind\":\"TRANSIENT\"}"));
        error(400, put("/api/v2/databases/rdb_put", "{\"name\":\"rdb_other\"}"));
    }

    @Test
    public void deleteTranslatesRestrict() throws Exception {
        ok(post("/api/v2/databases", "{\"name\":\"rdb_restrict\"}"));
        ok(delete("/api/v2/databases/rdb_restrict?restrict=true"));
        ok(post("/api/v2/databases", "{\"name\":\"rdb_cascade\"}"));
        ok(delete("/api/v2/databases/rdb_cascade?restrict=false"));
        error(404, get("/api/v2/databases/rdb_cascade"));
    }

    @Test
    public void sharesReplicationAndFailoverAre501() throws Exception {
        ok(post("/api/v2/databases", "{\"name\":\"rdb_replica\"}"));
        error(501, post("/api/v2/databases:from-share?share=ORG.ACCT.SHARE", "{\"name\":\"rdb_shared\"}"));
        error(501, post("/api/v2/databases/rdb_shared:from_share?share=ORG.ACCT.SHARE", null));
        for (final String action : new String[] {"replication:enable", "replication:disable", "failover:enable",
            "failover:disable"}) {
            error(501, post("/api/v2/databases/rdb_replica/" + action, "{\"accounts\":[\"ORG.ACCT\"]}"));
        }
        error(501, post("/api/v2/databases/rdb_replica/replication:refresh", null));
        error(501, post("/api/v2/databases/rdb_replica/failover:primary", null));
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        sql("CREATE DATABASE rdb_tag_home");
        sql("CREATE SCHEMA rdb_tag_home.s");
        sql("CREATE TAG rdb_tag_home.s.owner_team");
        ok(post("/api/v2/databases", "{\"name\":\"rdb_tagged\"}"));
        ok(post("/api/v2/databases/rdb_tagged:set-tags", "[{\"tag_database\":\"rdb_tag_home\",\"tag_schema\":\"s\","
            + "\"tag_name\":\"owner_team\",\"tag_value\":\"data\"}]"));
        final JsonNode tags = ok(get("/api/v2/databases/rdb_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("OWNER_TEAM", tags.get(0).path("tag_name").asString());
        assertEquals("data", tags.get(0).path("tag_value").asString());
        ok(post("/api/v2/databases/rdb_tagged:unset-tags", "[{\"tag_database\":\"rdb_tag_home\","
            + "\"tag_schema\":\"s\",\"tag_name\":\"owner_team\"}]"));
        assertEquals(0, ok(get("/api/v2/databases/rdb_tagged:get-tags")).size());
    }
}
