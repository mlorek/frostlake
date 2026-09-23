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

/** The schema endpoints ({@code schema.yaml}), each translated into the SQL the engine answers. */
public class RestSchemaTest extends BaseRestTest {

    private static final String SCHEMAS = "/api/v2/databases/rsc_db/schemas";

    private void database(final String name) {
        sql("CREATE DATABASE IF NOT EXISTS " + name);
    }

    @Test
    public void aSchemaIsCreatedFetchedListedAndDropped() throws Exception {
        database("rsc_db");
        assertEquals("Schema RSC_BASIC successfully created.", ok(post(SCHEMAS,
            "{\"name\":\"rsc_basic\",\"comment\":\"rest\",\"managed_access\":true,\"log_level\":\"INFO\","
                + "\"pipe_execution_paused\":true,\"user_task_timeout_ms\":5000}")).path("status").asString());
        final JsonNode fetched = ok(get(SCHEMAS + "/rsc_basic"));
        assertEquals("RSC_BASIC", fetched.path("name").asString());
        assertEquals("RSC_DB", fetched.path("database_name").asString());
        assertEquals("PERMANENT", fetched.path("kind").asString());
        assertEquals("rest", fetched.path("comment").asString());
        assertTrue(fetched.path("managed_access").asBoolean(), fetched.toString());
        assertEquals("MANAGED ACCESS", fetched.path("options").asString());
        assertEquals("OFF", fetched.path("log_level").asString(), "log_level in a body is ignored, as the account does");
        assertFalse(fetched.path("pipe_execution_paused").asBoolean(), "so is pipe_execution_paused: " + fetched);
        assertEquals(5000, fetched.path("user_task_timeout_ms").asInt());
        assertEquals(1, fetched.path("data_retention_time_in_days").asInt());

        assertTrue(names(ok(get(SCHEMAS + "?like=RSC_BAS%25"))).contains("RSC_BASIC"));
        assertEquals("", names(ok(get(SCHEMAS + "?like=nothing_like_this"))));
        assertEquals("RSC_BASIC successfully dropped.", ok(delete(SCHEMAS + "/rsc_basic")).path("status").asString());
        error(404, get(SCHEMAS + "/rsc_basic"));
        error(404, delete(SCHEMAS + "/rsc_basic"));
        ok(delete(SCHEMAS + "/rsc_basic?ifExists=true"));
        error(404, get("/api/v2/databases/rsc_no_such_db/schemas/public"));
        error(404, get("/api/v2/databases/rsc_no_such_db/schemas"));
    }

    @Test
    public void listingHistoryAndUndrop() throws Exception {
        database("rsc_hist");
        final String schemas = "/api/v2/databases/rsc_hist/schemas";
        ok(post(schemas, "{\"name\":\"s_a\"}"));
        ok(post(schemas, "{\"name\":\"s_b\"}"));
        assertEquals("S_A,S_B", names(ok(get(schemas + "?startsWith=S_"))));
        assertEquals("S_A", names(ok(get(schemas + "?startsWith=S_&showLimit=1"))));
        ok(delete(schemas + "/s_b"));
        assertEquals("S_A", names(ok(get(schemas + "?like=S_%25"))));
        final JsonNode history = ok(get(schemas + "?like=S_%25&history=true"));
        assertEquals("S_A,S_B", names(history));
        assertFalse(history.get(1).get("dropped_on").isNull(), history.toString());
        assertTrue(history.get(0).get("dropped_on").isNull(), history.toString());
        assertEquals("OFF", history.get(1).path("log_level").asString(), "a dropped schema reports the defaults");
        assertEquals("OFF", history.get(0).path("log_level").asString(), "a listing carries the parameters");
        ok(post(schemas + "/s_b:undrop", null));
        ok(get(schemas + "/s_b"));
    }

    @Test
    public void createModeKindAndClone() throws Exception {
        database("rsc_db");
        database("rsc_target");
        ok(post(SCHEMAS, "{\"name\":\"rsc_modes\",\"comment\":\"first\"}"));
        error(409, post(SCHEMAS, "{\"name\":\"rsc_modes\"}"));
        ok(post(SCHEMAS + "?createMode=ifNotExists", "{\"name\":\"rsc_modes\",\"comment\":\"second\"}"));
        assertEquals("first", ok(get(SCHEMAS + "/rsc_modes")).path("comment").asString());
        ok(post(SCHEMAS + "?createMode=orReplace", "{\"name\":\"rsc_modes\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get(SCHEMAS + "/rsc_modes")).path("comment").asString());
        ok(post(SCHEMAS + "?kind=transient", "{\"name\":\"rsc_transient\"}"));
        assertEquals("TRANSIENT", ok(get(SCHEMAS + "/rsc_transient")).path("kind").asString());

        sql("CREATE TABLE rsc_db.rsc_modes.t (id INT)");
        ok(post(SCHEMAS + "/rsc_modes:clone", "{\"name\":\"rsc_copy\"}"));
        assertEquals("third", ok(get(SCHEMAS + "/rsc_copy")).path("comment").asString());
        ok(post(SCHEMAS + "/rsc_modes:clone?targetDatabase=rsc_target", "{\"name\":\"rsc_moved\",\"point_of_time\":"
            + "{\"point_of_time_type\":\"statement\",\"reference\":\"before\",\"statement\":\"01b2c3d4\"}}"));
        ok(get("/api/v2/databases/rsc_target/schemas/rsc_moved"));
        error(404, post(SCHEMAS + "/rsc_no_source:clone", "{\"name\":\"rsc_copy_none\"}"));
    }

    @Test
    public void putCreatesAnAbsentSchemaAndResetsWhatALaterBodyLeavesOut() throws Exception {
        database("rsc_db");
        ok(put(SCHEMAS + "/rsc_put", "{\"name\":\"rsc_put\",\"comment\":\"c\",\"managed_access\":true,"
            + "\"suspend_task_after_num_failures\":2}"));
        JsonNode fetched = ok(get(SCHEMAS + "/rsc_put"));
        assertTrue(fetched.path("managed_access").asBoolean(), fetched.toString());
        assertEquals(2, fetched.path("suspend_task_after_num_failures").asInt());

        ok(put(SCHEMAS + "/rsc_put", "{\"name\":\"rsc_put\",\"data_retention_time_in_days\":2}"));
        fetched = ok(get(SCHEMAS + "/rsc_put"));
        assertFalse(fetched.path("managed_access").asBoolean(), fetched.toString());
        assertEquals(10, fetched.path("suspend_task_after_num_failures").asInt());
        assertEquals(2, fetched.path("data_retention_time_in_days").asInt());
        assertTrue(fetched.get("comment").isNull(), "an omitted comment is unset: " + fetched);
        error(400, put(SCHEMAS + "/rsc_put", "{\"name\":\"rsc_put\",\"kind\":\"TRANSIENT\"}"));
    }

    @Test
    public void quotedNamesAddressTheExactObject() throws Exception {
        ok(post("/api/v2/databases", "{\"name\":\"\\\"Mixed Db\\\"\",\"max_data_extension_time_in_days\":6}"));
        final JsonNode database = ok(get("/api/v2/databases/%22Mixed%20Db%22"));
        assertEquals("\"Mixed Db\"", database.path("name").asString());
        assertEquals(6, database.path("max_data_extension_time_in_days").asInt());
        ok(post("/api/v2/databases/%22Mixed%20Db%22/schemas", "{\"name\":\"\\\"lower s\\\"\"}"));
        final JsonNode schema = ok(get("/api/v2/databases/%22Mixed%20Db%22/schemas/%22lower%20s%22"));
        assertEquals("\"lower s\"", schema.path("name").asString());
        assertEquals("Mixed Db", schema.path("database_name").asString());
        assertEquals(6, schema.path("max_data_extension_time_in_days").asInt(),
            "a schema inherits its database's parameter");
        error(404, get("/api/v2/databases/mixed_db"));
    }

    @Test
    public void deleteTranslatesRestrictAndTagsWork() throws Exception {
        database("rsc_db");
        ok(post(SCHEMAS, "{\"name\":\"rsc_restrict\"}"));
        ok(delete(SCHEMAS + "/rsc_restrict?restrict=true"));
        ok(post(SCHEMAS, "{\"name\":\"rsc_cascade\"}"));
        sql("CREATE TABLE rsc_db.rsc_cascade.t (id INT)");
        ok(delete(SCHEMAS + "/rsc_cascade?restrict=false"));

        sql("CREATE SCHEMA IF NOT EXISTS rsc_db.tags");
        sql("CREATE TAG rsc_db.tags.tier");
        ok(post(SCHEMAS, "{\"name\":\"rsc_tagged\"}"));
        ok(post(SCHEMAS + "/rsc_tagged:set-tags", "[{\"tag_database\":\"rsc_db\",\"tag_schema\":\"tags\","
            + "\"tag_name\":\"tier\",\"tag_value\":\"gold\"}]"));
        final JsonNode tags = ok(get(SCHEMAS + "/rsc_tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("gold", tags.get(0).path("tag_value").asString());
        ok(post(SCHEMAS + "/rsc_tagged:unset-tags", "[{\"tag_database\":\"rsc_db\",\"tag_schema\":\"tags\","
            + "\"tag_name\":\"tier\"}]"));
        assertEquals(0, ok(get(SCHEMAS + "/rsc_tagged:get-tags")).size());
    }
}
