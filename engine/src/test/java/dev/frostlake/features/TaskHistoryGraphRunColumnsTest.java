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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.TaskRunWait;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The cells of INFORMATION_SCHEMA.TASK_HISTORY that name a run's graph run: ROOT_TASK_ID, RUN_ID and
 * GRAPH_RUN_GROUP_ID are the ones the task graph functions report for the same run, RUN_ID is the epoch
 * milliseconds the run was scheduled at, and ATTEMPT_NUMBER counts a retry as one more attempt of its run. The
 * history outlives the database a task ran in, by the task's name, so each test names its tasks apart.
 */
public class TaskHistoryGraphRunColumnsTest extends BaseDatabaseTest {

    private static String history(final String task) {
        return "TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => '" + task + "'))";
    }

    private static String cells(final Row row) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < row.getValues().size(); i++) {
            out.append(i == 0 ? "" : "|").append(String.valueOf(row.getValue(i)).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    @Test
    public void aRunCarriesItsGraphRunsIds() {
        final String task = "TH_OK_" + Long.toString(System.nanoTime() % 100000000L);
        final String graphs = "TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS(ROOT_TASK_NAME => '" + task + "'))";
        engine.execute("CREATE TASK " + task + " AS SELECT 1");
        engine.execute("EXECUTE TASK " + task);
        TaskRunWait.untilRows(engine, "SELECT * FROM " + graphs, 1);
        final ResultSet run = engine.executeQuery("""
            SELECT h.graph_run_group_id = g.graph_run_group_id, h.run_id = g.run_id, h.root_task_id = g.root_task_id,
                   h.attempt_number = g.attempt_number, h.run_id = DATE_PART(epoch_millisecond, h.scheduled_time),
                   h.attempt_number, h.graph_version, h.scheduled_from, h.state
            FROM %s h JOIN %s g ON TRUE
            WHERE h.state <> 'SCHEDULED'
            """.formatted(history(task), graphs));
        assertEquals(1, run.getRowCount());
        assertEquals("true|true|true|true|true|1|1|execute task|succeeded", cells(run.getRows().get(0)));
    }

    @Test
    public void aRetryIsOneMoreAttemptOfItsRun() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String root = "TH_ROOT_" + suffix;
        final String child = "TH_CHILD_" + suffix;
        engine.execute("CREATE TASK " + root + " AS SELECT 1");
        engine.execute("CREATE TASK " + child + " AFTER " + root + " AS SELECT 1/0");
        engine.execute("ALTER TASK " + child + " RESUME");
        final String failed = "SELECT scheduled_from, attempt_number FROM " + history(child) + " WHERE state = 'FAILED'";
        engine.execute("EXECUTE TASK " + root);
        TaskRunWait.untilRows(engine, failed, 1);
        engine.execute("EXECUTE TASK " + root + " RETRY LAST");
        TaskRunWait.untilRows(engine, failed, 2);
        final ResultSet attempts = engine.executeQuery(failed + " ORDER BY attempt_number");
        assertEquals(2, attempts.getRowCount());
        assertEquals("execute task|1", cells(attempts.getRows().get(0)));
        assertEquals("manual retry|2", cells(attempts.getRows().get(1)));
        // Both attempts belong to the graph run the root started, and so does the root's own run.
        final ResultSet run = engine.executeQuery("""
            SELECT COUNT(DISTINCT c.run_id), COUNT(DISTINCT c.root_task_id), MIN(c.run_id) = MIN(r.run_id),
                   MIN(c.root_task_id) = MIN(r.root_task_id)
            FROM %s c JOIN %s r ON r.state <> 'SCHEDULED'
            WHERE c.state = 'FAILED'
            """.formatted(history(child), history(root)));
        assertEquals("1|1|true|true", cells(run.getRows().get(0)));
    }
}
