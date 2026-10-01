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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The task endpoints ({@code task.yaml}), each translated into the SQL the engine answers. */
public class RestTaskTest extends BaseRestTest {

    private static String tasks(final String database) {
        return "/api/v2/databases/" + database + "/schemas/s/tasks";
    }

    private static void schema(final String database) {
        sql("CREATE DATABASE " + database);
        sql("CREATE SCHEMA " + database + ".s");
    }

    @Test
    public void aTaskIsCreatedFetchedListedAndDropped() throws Exception {
        schema("tsk_basic");
        assertEquals("Task T1 successfully created.", ok(post(tasks("tsk_basic"),
            "{\"name\":\"t1\",\"definition\":\"SELECT 1\",\"comment\":\"rest\",\"schedule\":"
                + "{\"schedule_type\":\"MINUTES_TYPE\",\"minutes\":5},\"allow_overlapping_execution\":true}"))
            .path("status").asString());
        ok(post(tasks("tsk_basic"), "{\"name\":\"t2\",\"definition\":\"SELECT 2\",\"predecessors\":[\"t1\"]}"));
        ok(post(tasks("tsk_basic"), "{\"name\":\"t3\",\"definition\":\"SELECT 3\",\"schedule\":"
            + "{\"schedule_type\":\"CRON_TYPE\",\"cron_expr\":\"0 9 * * *\",\"timezone\":\"UTC\"}}"));

        final JsonNode fetched = ok(get(tasks("tsk_basic") + "/t1"));
        assertEquals("T1", fetched.path("name").asString());
        assertEquals("SELECT 1", fetched.path("definition").asString());
        assertEquals("rest", fetched.path("comment").asString());
        assertEquals("MINUTES_TYPE", fetched.path("schedule").path("schedule_type").asString());
        assertEquals(5, fetched.path("schedule").path("minutes").asInt());
        assertTrue(fetched.path("allow_overlapping_execution").asBoolean(), fetched.toString());
        assertEquals("suspended", fetched.path("state").asString());
        assertEquals(0, fetched.path("predecessors").size());
        assertTrue(fetched.get("error_integration").isNull(), "an unset integration is null: " + fetched);
        assertTrue(fetched.path("id").asString().length() > 0);
        // A predecessor in the task's own schema is named bare.
        assertEquals("T1", ok(get(tasks("tsk_basic") + "/t2")).path("predecessors").get(0).asString());
        final JsonNode cron = ok(get(tasks("tsk_basic") + "/t3")).path("schedule");
        assertEquals("CRON_TYPE", cron.path("schedule_type").asString());
        assertEquals("0 9 * * *", cron.path("cron_expr").asString());
        assertEquals("UTC", cron.path("timezone").asString());

        assertEquals("T1,T2,T3", names(ok(get(tasks("tsk_basic")))));
        assertEquals("T1,T3", names(ok(get(tasks("tsk_basic") + "?rootOnly=true"))));
        assertEquals("T2", names(ok(get(tasks("tsk_basic") + "?like=%252"))));
        assertEquals("T3", names(ok(get(tasks("tsk_basic") + "?fromName=T2"))), "the rows after the named one");

        ok(delete(tasks("tsk_basic") + "/t3"));
        error(404, get(tasks("tsk_basic") + "/t3"));
        error(404, delete(tasks("tsk_basic") + "/t3"));
        ok(delete(tasks("tsk_basic") + "/t3?ifExists=true"));
    }

    @Test
    public void createModesAndRefusals() throws Exception {
        schema("tsk_modes");
        ok(post(tasks("tsk_modes"), "{\"name\":\"m\",\"definition\":\"SELECT 1\",\"comment\":\"first\"}"));
        error(409, post(tasks("tsk_modes"), "{\"name\":\"m\",\"definition\":\"SELECT 1\"}"));
        ok(post(tasks("tsk_modes") + "?createMode=ifNotExists", "{\"name\":\"m\",\"definition\":\"SELECT 1\"}"));
        assertEquals("first", ok(get(tasks("tsk_modes") + "/m")).path("comment").asString());
        ok(post(tasks("tsk_modes") + "?createMode=orReplace",
            "{\"name\":\"m\",\"definition\":\"SELECT 9\",\"comment\":\"third\"}"));
        assertEquals("third", ok(get(tasks("tsk_modes") + "/m")).path("comment").asString());
        error(400, post(tasks("tsk_modes"), "{\"name\":\"nodef\"}"));
        ok(post(tasks("tsk_modes"), "{\"name\":\"cfg\",\"definition\":\"SELECT 1\",\"config\":{\"a\":1},"
            + "\"overlap_policy\":\"ALLOW_ALL_OVERLAP\",\"session_parameters\":{\"QUERY_TAG\":\"rest\"}}"));
        final JsonNode configured = ok(get(tasks("tsk_modes") + "/cfg"));
        assertEquals(1, configured.path("config").path("a").asInt(), configured.toString());
        assertEquals("ALLOW_ALL_OVERLAP", configured.path("overlap_policy").asString());
        ok(post(tasks("tsk_modes"),
            "{\"name\":\"fin\",\"definition\":\"SELECT 1\",\"finalize\":\"cfg\"}"));
        assertEquals("CFG", ok(get(tasks("tsk_modes") + "/fin")).path("finalize").asString());
        error(400, post(tasks("tsk_modes"), "{\"name\":\"bad\",\"definition\":\"SELECT 1\",\"config\":[1]}"));
    }

    @Test
    public void putCreatesAnAbsentTaskAndResetsWhatALaterBodyLeavesOut() throws Exception {
        schema("tsk_put");
        ok(put(tasks("tsk_put") + "/p", "{\"name\":\"p\",\"definition\":\"SELECT 1\",\"comment\":\"c\","
            + "\"schedule\":{\"schedule_type\":\"MINUTES_TYPE\",\"minutes\":10},\"condition\":\"1 = 1\"}"));
        JsonNode fetched = ok(get(tasks("tsk_put") + "/p"));
        assertEquals("c", fetched.path("comment").asString());
        assertEquals(10, fetched.path("schedule").path("minutes").asInt());
        ok(put(tasks("tsk_put") + "/p", "{\"name\":\"p\",\"definition\":\"SELECT 2\",\"condition\":\"2 = 2\"}"));
        fetched = ok(get(tasks("tsk_put") + "/p"));
        assertEquals("SELECT 2", fetched.path("definition").asString());
        assertEquals("2 = 2", fetched.path("condition").asString());
        assertTrue(fetched.get("schedule").isNull(), "an omitted schedule is unset: " + fetched);
        assertEquals("", fetched.path("comment").asString(""), "an omitted comment is unset");
        error(400, put(tasks("tsk_put") + "/p", "{\"name\":\"other\",\"definition\":\"SELECT 1\"}"));
        ok(put(tasks("tsk_put") + "/p", "{\"name\":\"p\",\"definition\":\"SELECT 1\"}"));
        assertTrue(ok(get(tasks("tsk_put") + "/p")).get("condition").isNull(), "an omitted condition is removed");
        ok(put(tasks("tsk_put") + "/c", "{\"name\":\"c\",\"definition\":\"SELECT 1\",\"predecessors\":[\"p\"]}"));
        ok(put(tasks("tsk_put") + "/c", "{\"name\":\"c\",\"definition\":\"SELECT 1\"}"));
        assertEquals(0, ok(get(tasks("tsk_put") + "/c")).path("predecessors").size(), "the predecessor is removed");
        ok(put(tasks("tsk_put") + "/c", "{\"name\":\"c\",\"definition\":\"SELECT 1\",\"predecessors\":[\"p\"]}"));
        // A predecessor in the task's own schema is named bare.
        assertEquals("P", ok(get(tasks("tsk_put") + "/c")).path("predecessors").get(0).asString());
    }

    @Test
    public void actionsResumeSuspendExecuteAndTheGraphs() throws Exception {
        schema("tsk_run");
        ok(post(tasks("tsk_run"), "{\"name\":\"root\",\"definition\":\"SELECT 1\"}"));
        ok(post(tasks("tsk_run"), "{\"name\":\"child\",\"definition\":\"SELECT 1/0\",\"predecessors\":[\"root\"]}"));
        ok(post(tasks("tsk_run"), "{\"name\":\"grandchild\",\"definition\":\"SELECT 3\","
            + "\"predecessors\":[\"child\"]}"));
        ok(post(tasks("tsk_run") + "/child:resume", null));
        assertEquals("started", ok(get(tasks("tsk_run") + "/child")).path("state").asString());
        ok(post(tasks("tsk_run") + "/grandchild:resume", null));
        ok(post(tasks("tsk_run") + "/grandchild:suspend", null));
        assertEquals("suspended", ok(get(tasks("tsk_run") + "/grandchild")).path("state").asString());
        error(404, post(tasks("tsk_run") + "/missing:resume", null));

        assertEquals("ROOT,CHILD,GRANDCHILD", names(ok(get(tasks("tsk_run") + "/root/dependents"))));
        assertEquals("ROOT,CHILD", names(ok(get(tasks("tsk_run") + "/root/dependents?recursive=false"))));

        error(400, post(tasks("tsk_run") + "/root:execute?retryLast=true", null));
        ok(post(tasks("tsk_run") + "/root:execute", null));
        final JsonNode complete = ok(get(tasks("tsk_run") + "/root/complete-graphs"));
        assertEquals(1, complete.size(), complete.toString());
        assertEquals("FAILED", complete.get(0).path("state").asString());
        assertEquals("CHILD", complete.get(0).path("first_error_task_name").asString());
        assertEquals("ROOT", complete.get(0).path("root_task_name").asString());
        assertEquals(1, complete.get(0).path("graph_version").asInt());
        assertTrue(complete.get(0).path("run_id").isNumber(), complete.toString());
        assertEquals(complete, ok(get(tasks("tsk_run") + "/root/complete_graphs?errorOnly=true")));
        ok(post(tasks("tsk_run") + "/root:execute?retryLast=true", null));
        assertEquals(0, ok(get(tasks("tsk_run") + "/root/current-graphs")).size());
        assertEquals(0, ok(get(tasks("tsk_run") + "/root/current_graphs?resultLimit=5")).size());
    }

    @Test
    public void aScheduledRootIsACurrentGraph() throws Exception {
        schema("tsk_sched");
        ok(post(tasks("tsk_sched"), "{\"name\":\"every\",\"definition\":\"SELECT 1\",\"schedule\":"
            + "{\"schedule_type\":\"MINUTES_TYPE\",\"minutes\":60}}"));
        ok(post(tasks("tsk_sched") + "/every:resume", null));
        try {
            final JsonNode current = ok(get(tasks("tsk_sched") + "/every/current-graphs"));
            assertEquals(1, current.size(), current.toString());
            assertEquals("SCHEDULED", current.get(0).path("state").asString());
            assertTrue(current.get(0).path("scheduled_time").asString().contains("T"), current.toString());
        } finally {
            ok(post(tasks("tsk_sched") + "/every:suspend", null));
        }
    }

    @Test
    public void tagsAreSetReadAndUnset() throws Exception {
        schema("tsk_tags");
        sql("CREATE TAG tsk_tags.s.cost_center");
        ok(post(tasks("tsk_tags"), "{\"name\":\"tagged\",\"definition\":\"SELECT 1\"}"));
        ok(post(tasks("tsk_tags") + "/tagged:set-tags",
            "[{\"tag_database\":\"tsk_tags\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\",\"tag_value\":\"fin\"}]"));
        final JsonNode tags = ok(get(tasks("tsk_tags") + "/tagged:get-tags"));
        assertEquals(1, tags.size(), tags.toString());
        assertEquals("TASK", tags.get(0).path("level").asString());
        ok(post(tasks("tsk_tags") + "/tagged:unset-tags",
            "[{\"tag_database\":\"tsk_tags\",\"tag_schema\":\"s\",\"tag_name\":\"cost_center\"}]"));
        assertEquals(0, ok(get(tasks("tsk_tags") + "/tagged:get-tags")).size());
    }
}
