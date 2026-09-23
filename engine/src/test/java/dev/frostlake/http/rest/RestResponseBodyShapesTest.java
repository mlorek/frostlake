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

import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Response bodies as the account answers them where the specification leaves room: a task schedule written as its
 * length in seconds and read back with its seconds, an alert schedule read back in whole minutes, the empty objects
 * and nulls a fetched task, alert, role and tag carry, a tag's {@code multi_value} and {@code NONE} propagation, a
 * stage's file format read back from its listing's escapes, and the codes of a task's RETRY LAST refusals.
 */
public class RestResponseBodyShapesTest extends BaseRestTest {

    private static final String SCHEMA = "/api/v2/databases/rmb_db/schemas/s";

    private static void database() {
        sql("CREATE DATABASE IF NOT EXISTS rmb_db");
        sql("CREATE SCHEMA IF NOT EXISTS rmb_db.s");
    }

    /** One cell of a SHOW statement's rows, run in a session of its own. */
    private static String showCell(final String statement, final String column) {
        final SessionContext session = server.getEngine().createSession();
        try {
            final ExecutionResult result = server.getEngine().execute(statement, session);
            assertTrue(result.isSuccess(), statement + " -> " + result.getErrorMessage());
            final ResultSet rs = result.getResultSets().get(0);
            return String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex(column)));
        } finally {
            server.getEngine().removeSession(session.getSessionId());
        }
    }

    @Test
    public void aTaskScheduleIsWrittenInSecondsAndReadBackWithThem() throws Exception {
        database();
        ok(post(SCHEMA + "/tasks", "{\"name\":\"rmb_t1\",\"warehouse\":\"compute_wh\",\"schedule\":{\"schedule_type\":"
            + "\"MINUTES_TYPE\",\"minutes\":2,\"seconds\":30},\"definition\":\"SELECT 1\"}"));
        ok(post(SCHEMA + "/tasks", "{\"name\":\"rmb_t2\",\"schedule\":{\"schedule_type\":\"MINUTES_TYPE\","
            + "\"minutes\":5},"
            + "\"target_completion_interval\":{\"schedule_type\":\"MINUTES_TYPE\",\"minutes\":10},"
            + "\"definition\":\"SELECT 1\"}"));
        assertEquals("150 SECOND", showCell("SHOW TASKS LIKE 'RMB_T1' IN SCHEMA rmb_db.s", "schedule"));
        assertEquals("300 SECOND", showCell("SHOW TASKS LIKE 'RMB_T2' IN SCHEMA rmb_db.s", "schedule"));
        final JsonNode t1 = ok(get(SCHEMA + "/tasks/rmb_t1"));
        assertEquals(2, t1.path("schedule").path("minutes").asInt(), t1.toString());
        assertEquals(30, t1.path("schedule").path("seconds").asInt(), t1.toString());
        assertTrue(t1.path("config").isObject() && t1.path("config").isEmpty(), t1.toString());
        assertTrue(t1.path("session_parameters").isObject() && t1.path("session_parameters").isEmpty(), t1.toString());
        assertTrue(t1.get("owner_role_type").isNull(), t1.toString());
        final JsonNode t2 = ok(get(SCHEMA + "/tasks/rmb_t2"));
        assertEquals(5, t2.path("schedule").path("minutes").asInt(), t2.toString());
        assertEquals(0, t2.path("schedule").path("seconds").asInt(), t2.toString());
        assertTrue(t2.path("schedule").has("seconds"), "the seconds are always answered: " + t2);
        assertEquals(10, t2.path("target_completion_interval").path("minutes").asInt(), t2.toString());
        assertEquals(0, t2.path("target_completion_interval").path("seconds").asInt(), t2.toString());
    }

    @Test
    public void aRetryWithNothingToRetryIsAnsweredWithItsCode() throws Exception {
        database();
        ok(post(SCHEMA + "/tasks", "{\"name\":\"rmb_root\",\"definition\":\"SELECT 1\"}"));
        final HttpResponse<String> response = post(SCHEMA + "/tasks/rmb_root:execute?retryLast=true", null);
        assertEquals("cannot perform retry: no suitable run of graph with root task rmb_root to retry.",
            error(400, response));
        assertEquals("091457", json(response).path("code").asString());
        ok(post(SCHEMA + "/tasks/rmb_root:execute", null));
        final HttpResponse<String> again = post(SCHEMA + "/tasks/rmb_root:execute?retryLast=true", null);
        assertTrue(error(400, again).startsWith("cannot perform retry: run (graph_run_group_id = "), again.body());
        assertEquals("091456", json(again).path("code").asString());
    }

    @Test
    public void anEmptyCommentIsNullAndAnAbsentConfigIsEmpty() throws Exception {
        database();
        sql("CREATE ROLE rmb_role");
        final JsonNode roles = ok(get("/api/v2/roles?like=RMB_ROLE"));
        assertTrue(roles.get(0).get("comment").isNull(), roles.toString());
        sql("CREATE ALERT rmb_db.s.rmb_alert IF (EXISTS (SELECT 1)) THEN SELECT 1");
        final JsonNode alert = ok(get(SCHEMA + "/alerts/rmb_alert"));
        assertTrue(alert.get("comment").isNull(), alert.toString());
        assertTrue(alert.path("config").isObject() && alert.path("config").isEmpty(), alert.toString());
        sql("CREATE TAG rmb_db.s.rmb_tag");
        final JsonNode tag = ok(get(SCHEMA + "/tags/rmb_tag"));
        assertTrue(tag.path("multi_value").isBoolean(), tag.toString());
        assertFalse(tag.path("multi_value").asBoolean(), tag.toString());
        assertTrue(tag.get("comment").isNull(), tag.toString());
        assertEquals("NONE", tag.path("propagate").asString(), tag.toString());
    }

    @Test
    public void anAlertScheduleIsReadBackInWholeMinutes() throws Exception {
        database();
        final String[][] cases = {{"rmb_a1", "90 SECONDS", "1"}, {"rmb_a2", "45 S", "0"}, {"rmb_a3", "2 HOURS", "120"},
            {"rmb_a4", "1 H", "60"}, {"rmb_a5", "5 M", "5"}, {"rmb_a6", "10 MINUTES ", "10"}};
        for (final String[] c : cases) {
            sql("CREATE ALERT rmb_db.s." + c[0] + " SCHEDULE = '" + c[1] + "' IF (EXISTS (SELECT 1)) THEN SELECT 1");
            final JsonNode alert = ok(get(SCHEMA + "/alerts/" + c[0]));
            assertEquals("SCHEDULE_TYPE", alert.path("schedule").path("schedule_type").asString(), alert.toString());
            assertEquals(Integer.parseInt(c[2]), alert.path("schedule").path("minutes").asInt(-1), alert.toString());
        }
    }

    @Test
    public void aStagesFileFormatIsReadBackFromTheListingsEscapes() throws Exception {
        database();
        ok(post(SCHEMA + "/stages", "{\"name\":\"rmb_st\",\"file_format\":{\"type\":\"CSV\",\"field_delimiter\":\"|\","
            + "\"skip_header\":1}}"));
        final JsonNode stage = ok(get(SCHEMA + "/stages/rmb_st"));
        final JsonNode format = stage.path("file_format");
        assertEquals("|", format.path("field_delimiter").asString(), stage.toString());
        assertEquals("\n", format.path("record_delimiter").asString(), stage.toString());
        assertEquals("\\", format.path("escape_unenclosed_field").asString(), stage.toString());
        assertEquals("\\N", format.path("null_if").get(0).asString(), stage.toString());
        assertTrue(format.get("file_extension").isNull(), stage.toString());
        assertTrue(stage.get("comment").isNull(), stage.toString());
    }

    @Test
    public void anEmptyConfigSentBackIsNoConfig() throws Exception {
        database();
        sql("CREATE TASK rmb_db.s.cf1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        sql("CREATE TASK rmb_db.s.cf2 SCHEDULE = '60 MINUTE' CONFIG = $${\"a\": 1}$$ AS SELECT 1");
        final String rest = "\"schedule\":{\"schedule_type\":\"MINUTES_TYPE\",\"minutes\":60},"
            + "\"definition\":\"SELECT 1\",\"config\":{},\"session_parameters\":{}}";
        ok(put(SCHEMA + "/tasks/cf1", "{\"name\":\"cf1\"," + rest));
        ok(put(SCHEMA + "/tasks/cf2", "{\"name\":\"cf2\"," + rest));
        ok(post(SCHEMA + "/tasks", "{\"name\":\"cf3\"," + rest));
        for (final String task : new String[] {"CF1", "CF2", "CF3"}) {
            assertEquals("null", showCell("SHOW TASKS LIKE '" + task + "' IN SCHEMA rmb_db.s", "config"), task);
            final JsonNode fetched = ok(get(SCHEMA + "/tasks/" + task));
            assertTrue(fetched.path("config").isObject() && fetched.path("config").isEmpty(), fetched.toString());
        }
    }
}
