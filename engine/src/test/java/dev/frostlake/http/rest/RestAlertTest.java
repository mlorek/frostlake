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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The alert endpoints ({@code alert.yaml}), each translated into the ALERT statements. */
public class RestAlertTest extends BaseRestTest {

    private static final String ALERTS = "/api/v2/databases/ra_db/schemas/s1/alerts";

    private static void database() {
        sql("CREATE DATABASE IF NOT EXISTS ra_db");
        sql("CREATE SCHEMA IF NOT EXISTS ra_db.s1");
        sql("CREATE SCHEMA IF NOT EXISTS ra_db.s2");
        sql("CREATE TABLE IF NOT EXISTS ra_db.s1.fired (msg VARCHAR)");
    }

    @Test
    public void anAlertIsCreatedFetchedListedAndDropped() throws Exception {
        database();
        // An alert's warehouse must exist.
        final HttpResponse<String> missing = post(ALERTS, "{\"name\":\"al_nowh\",\"warehouse\":\"no_such_wh\","
            + "\"condition\":\"SELECT 1\",\"action\":\"SELECT 2\"}");
        assertEquals("nonexistent warehouse no_such_wh was specified.", error(400, missing));
        sql("CREATE WAREHOUSE IF NOT EXISTS wh1");
        assertEquals("Alert AL_BASIC successfully created.", ok(post(ALERTS, "{\"name\":\"al_basic\","
            + "\"schedule\":{\"schedule_type\":\"SCHEDULE_TYPE\",\"minutes\":5},\"warehouse\":\"wh1\","
            + "\"comment\":\"rest\",\"config\":{\"k\":\"v\"},\"runbook\":\"rb\",\"suspend_alert_after_num_failures\":3,"
            + "\"condition\":\"SELECT 1\",\"action\":\"SELECT 2\"}")).path("status").asString());
        final JsonNode fetched = ok(get(ALERTS + "/al_basic"));
        assertEquals("AL_BASIC", fetched.path("name").asString());
        assertEquals("SCHEDULE_TYPE", fetched.path("schedule").path("schedule_type").asString());
        assertEquals(5, fetched.path("schedule").path("minutes").asInt());
        assertEquals("WH1", fetched.path("warehouse").asString());
        assertEquals("rest", fetched.path("comment").asString());
        assertEquals("v", fetched.path("config").path("k").asString());
        assertEquals("rb", fetched.path("runbook").asString());
        assertEquals("SELECT 1", fetched.path("condition").asString());
        assertEquals("SELECT 2", fetched.path("action").asString());
        assertEquals("suspended", fetched.path("state").asString());
        assertEquals("RA_DB", fetched.path("database_name").asString());
        assertTrue(fetched.path("created_on").asString().contains("T"), fetched.toString());
        assertTrue(names(ok(get(ALERTS + "?like=AL_BAS%25"))).contains("AL_BASIC"));
        assertEquals("", names(ok(get(ALERTS + "?like=nothing_like_this"))));
        assertEquals("AL_BASIC successfully dropped.", ok(delete(ALERTS + "/al_basic")).path("status").asString());
        error(404, get(ALERTS + "/al_basic"));
        error(404, delete(ALERTS + "/al_basic"));
        ok(delete(ALERTS + "/al_basic?ifExists=true"));
    }

    @Test
    public void aCronScheduleRoundTrips() throws Exception {
        database();
        ok(post(ALERTS, "{\"name\":\"al_cron\",\"schedule\":{\"schedule_type\":\"CRON_TYPE\","
            + "\"cron_expr\":\"0 9 * * MON-FRI\",\"timezone\":\"America/Los_Angeles\"},"
            + "\"condition\":\"SELECT 1\",\"action\":\"SELECT 2\"}"));
        final JsonNode schedule = ok(get(ALERTS + "/al_cron")).path("schedule");
        assertEquals("CRON_TYPE", schedule.path("schedule_type").asString());
        assertEquals("0 9 * * MON-FRI", schedule.path("cron_expr").asString());
        assertEquals("America/Los_Angeles", schedule.path("timezone").asString());
        error(400, post(ALERTS, "{\"name\":\"al_bad\",\"schedule\":{\"schedule_type\":\"HOURLY\"},"
            + "\"condition\":\"SELECT 1\",\"action\":\"SELECT 2\"}"));
    }

    @Test
    public void createModesAndMissingProperties() throws Exception {
        database();
        final String body = "{\"name\":\"al_modes\",\"condition\":\"SELECT 1\",\"action\":\"SELECT 2\"}";
        ok(post(ALERTS, body));
        error(409, post(ALERTS + "?createMode=errorIfExists", body));
        ok(post(ALERTS + "?createMode=ifNotExists", body));
        ok(post(ALERTS + "?createMode=orReplace", "{\"name\":\"al_modes\",\"condition\":\"SELECT 3\","
            + "\"action\":\"SELECT 4\"}"));
        assertEquals("SELECT 3", ok(get(ALERTS + "/al_modes")).path("condition").asString());
        error(400, post(ALERTS, "{\"name\":\"al_nocond\",\"action\":\"SELECT 2\"}"));
        error(400, post(ALERTS, "{\"condition\":\"SELECT 1\",\"action\":\"SELECT 2\"}"));
        error(501, post(ALERTS, "{\"name\":\"al_tpl\",\"template\":{\"name\":\"t\"},\"condition\":\"SELECT 1\","
            + "\"action\":\"SELECT 2\"}"));
    }

    @Test
    public void executeRunsTheAction() throws Exception {
        database();
        ok(post(ALERTS, "{\"name\":\"al_exec\",\"condition\":\"SELECT 1\","
            + "\"action\":\"INSERT INTO ra_db.s1.fired VALUES ('rest')\"}"));
        ok(post(ALERTS + "/al_exec:execute", ""));
        error(404, post(ALERTS + "/al_nosuch:execute", ""));
        assertEquals("suspended", ok(get(ALERTS + "/al_exec")).path("state").asString());
    }

    @Test
    public void cloneCopiesTheAlert() throws Exception {
        database();
        ok(post(ALERTS, "{\"name\":\"al_src\",\"comment\":\"src\",\"condition\":\"SELECT 1\",\"action\":\"SELECT 2\"}"));
        ok(post(ALERTS + "/al_src:clone?targetDatabase=ra_db&targetSchema=s2", "{\"name\":\"al_copy\"}"));
        assertEquals("src", ok(get("/api/v2/databases/ra_db/schemas/s2/alerts/al_copy")).path("comment").asString());
        ok(post(ALERTS + "/al_src:clone?targetDatabase=ra_db&targetSchema=s1", "{\"name\":\"al_copy2\"}"));
        error(409, post(ALERTS + "/al_src:clone?targetDatabase=ra_db&targetSchema=s1", "{\"name\":\"al_copy2\"}"));
        ok(post(ALERTS + "/al_src:clone?createMode=orReplace&targetDatabase=ra_db&targetSchema=s1",
            "{\"name\":\"al_copy2\"}"));
        error(404, post(ALERTS + "/al_none:clone?targetDatabase=ra_db&targetSchema=s1", "{\"name\":\"x\"}"));
        error(501, post(ALERTS + "/al_src:clone?targetDatabase=ra_db&targetSchema=s1", "{\"name\":\"al_pit\","
            + "\"point_of_time\":{\"point_of_time_type\":\"offset\",\"reference\":\"at\",\"offset\":\"-60\"}}"));
    }

    @Test
    public void tagsAreSetReadWithLineageAndUnset() throws Exception {
        database();
        sql("CREATE TAG IF NOT EXISTS ra_db.s1.sev");
        ok(post(ALERTS, "{\"name\":\"al_tags\",\"condition\":\"SELECT 1\",\"action\":\"SELECT 2\"}"));
        ok(post(ALERTS + "/al_tags:set-tags",
            "[{\"tag_database\":\"ra_db\",\"tag_schema\":\"s1\",\"tag_name\":\"sev\",\"tag_value\":\"high\"}]"));
        JsonNode tags = ok(get(ALERTS + "/al_tags:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("SEV", tags.get(0).path("tag_name").asString());
        assertEquals("high", tags.get(0).path("tag_value").asString());
        assertEquals("ALERT", tags.get(0).path("level").asString());
        sql("CREATE TAG IF NOT EXISTS ra_db.s1.area");
        sql("ALTER SCHEMA ra_db.s1 SET TAG ra_db.s1.area = 'ops'");
        assertEquals(1, ok(get(ALERTS + "/al_tags:get-tags?withLineage=false")).size());
        tags = ok(get(ALERTS + "/al_tags:get-tags?withLineage=true"));
        assertEquals(2, tags.size(), tags.toString());
        boolean inherited = false;
        for (final JsonNode tag : tags.values()) {
            if ("AREA".equals(tag.path("tag_name").asString())) {
                inherited = "SCHEMA".equals(tag.path("level").asString());
            }
        }
        assertTrue(inherited, tags.toString());
        ok(post(ALERTS + "/al_tags:unset-tags", "[{\"tag_database\":\"ra_db\",\"tag_schema\":\"s1\",\"tag_name\":\"sev\"}]"));
        assertEquals(0, ok(get(ALERTS + "/al_tags:get-tags")).size());
        error(404, post(ALERTS + "/al_nosuch:set-tags",
            "[{\"tag_database\":\"ra_db\",\"tag_schema\":\"s1\",\"tag_name\":\"sev\",\"tag_value\":\"x\"}]"));
        ok(post(ALERTS + "/al_nosuch:set-tags?ifExists=true",
            "[{\"tag_database\":\"ra_db\",\"tag_schema\":\"s1\",\"tag_name\":\"sev\",\"tag_value\":\"x\"}]"));
        assertFalse(tags.isEmpty());
    }
}
