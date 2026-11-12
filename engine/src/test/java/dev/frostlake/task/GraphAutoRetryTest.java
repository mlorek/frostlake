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

package dev.frostlake.task;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.TaskRunWait;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TASK_AUTO_RETRY_ATTEMPTS and SUSPEND_TASK_AFTER_NUM_FAILURES act on the graph run, by the root's setting: a graph
 * run that ends FAILED is retried "from the last task in the graph that failed", each retry its next attempt and
 * scheduled from AUTOMATIC RETRY, and the root is suspended once that many graph runs in a row failed (documented).
 * A failing child was never retried, every retry of a root was attempt 1, and a child's failures never suspended the
 * root.
 */
public class GraphAutoRetryTest extends BaseDatabaseTest {

    private static String cells(final ResultSet rs) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (out.length() > 0) {
                out.append(" ;; ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i == 0 ? "" : "|").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private static String graphs(final String root) {
        return "TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS(ROOT_TASK_NAME => '" + root + "'))";
    }

    @Test
    public void aFailedChildIsRetriedAsTheGraphRunsNextAttempt() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String root = "AR_ROOT_" + suffix;
        final String child = "AR_CHILD_" + suffix;
        engine.execute("CREATE TASK " + root + " TASK_AUTO_RETRY_ATTEMPTS = 1 AS SELECT 1");
        engine.execute("CREATE TASK " + child + " AFTER " + root + " AS SELECT 1 / 0");
        engine.execute("ALTER TASK " + child + " RESUME");
        engine.execute("EXECUTE TASK " + root);
        TaskRunWait.untilRows(engine, "SELECT * FROM " + graphs(root), 2);
        assertEquals("1|FAILED|EXECUTE TASK ;; 2|FAILED|AUTOMATIC RETRY", cells(engine.executeQuery(
            "SELECT attempt_number, state, scheduled_from FROM " + graphs(root) + " ORDER BY attempt_number")));
        assertEquals("C:1:EXECUTE TASK ;; R:1:EXECUTE TASK ;; C:2:AUTOMATIC RETRY", cells(engine.executeQuery(
            "SELECT REPLACE(REPLACE(name, '" + child + "', 'C'), '" + root + "', 'R') || ':' || attempt_number || ':'"
                + " || scheduled_from FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY()) WHERE name LIKE 'AR%" + suffix
                + "' AND state <> 'SCHEDULED' ORDER BY attempt_number, name")));
    }

    @Test
    public void aStandaloneTasksRetriesAreAttemptsOfOneGraphRun() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String task = "AS_TASK_" + suffix;
        engine.execute("CREATE TASK " + task + " TASK_AUTO_RETRY_ATTEMPTS = 2 AS SELECT 1 / 0");
        engine.execute("EXECUTE TASK " + task);
        TaskRunWait.untilRows(engine, "SELECT * FROM " + graphs(task), 3);
        assertEquals("1|FAILED|EXECUTE TASK ;; 2|FAILED|AUTOMATIC RETRY ;; 3|FAILED|AUTOMATIC RETRY",
            cells(engine.executeQuery("SELECT attempt_number, state, scheduled_from FROM " + graphs(task)
                + " ORDER BY attempt_number")));
        assertEquals("1", cells(engine.executeQuery("SELECT COUNT(DISTINCT run_id) FROM " + graphs(task))));
    }

    @Test
    public void aChildsFailuresSuspendTheRoot() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String root = "AU_ROOT_" + suffix;
        final String child = "AU_CHILD_" + suffix;
        engine.execute("CREATE TASK " + root + " SCHEDULE = '60 MINUTE' SUSPEND_TASK_AFTER_NUM_FAILURES = 2 "
            + "AS SELECT 1");
        engine.execute("CREATE TASK " + child + " AFTER " + root + " AS SELECT 1 / 0");
        engine.execute("ALTER TASK " + child + " RESUME");
        engine.execute("ALTER TASK " + root + " RESUME");
        try {
            engine.execute("EXECUTE TASK " + root);
            TaskRunWait.untilRows(engine, "SELECT * FROM " + graphs(root), 1);
            assertEquals("started", cells(engine.executeQuery("SELECT state FROM TABLE(INFORMATION_SCHEMA."
                + "TASK_DEPENDENTS(TASK_NAME => '" + root + "', RECURSIVE => FALSE)) WHERE name = '" + root + "'")));
            engine.execute("EXECUTE TASK " + root);
            TaskRunWait.untilRows(engine, "SELECT * FROM " + graphs(root), 2);
            assertEquals("suspended", cells(engine.executeQuery("SELECT state FROM TABLE(INFORMATION_SCHEMA."
                + "TASK_DEPENDENTS(TASK_NAME => '" + root + "', RECURSIVE => FALSE)) WHERE name = '" + root + "'")));
        } finally {
            engine.execute("ALTER TASK " + root + " SUSPEND");
        }
    }
}
