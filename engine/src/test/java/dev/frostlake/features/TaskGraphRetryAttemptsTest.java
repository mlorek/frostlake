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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * EXECUTE TASK … RETRY LAST runs a graph run again as one more attempt of it: COMPLETE_TASK_GRAPHS answers a row
 * per attempt, each scheduled from MANUAL RETRY after the first, under the graph run's one RUN_ID and
 * GRAPH_RUN_GROUP_ID; TASK_HISTORY numbers every run of a retry with its attempt, a dependent the retry reaches
 * for the first time too; and a retry of a graph run whose last attempt failed nowhere names that attempt
 * (live-verified).
 */
public class TaskGraphRetryAttemptsTest extends BaseDatabaseTest {

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

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aRetryOfAFailedDependentIsTheGraphRunsNextAttempt() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String root = "TA_ROOT_" + suffix;
        final String child = "TA_CHILD_" + suffix;
        final String grandchild = "TA_GRAND_" + suffix;
        engine.execute("CREATE OR REPLACE TABLE attempt_log (n INT)");
        engine.execute("CREATE TASK " + root + " AS INSERT INTO attempt_log VALUES (1)");
        // The child divides by zero until a second row is there.
        engine.execute("CREATE TASK " + child + " AFTER " + root
            + " AS INSERT INTO attempt_log SELECT 1 / (COUNT(*) - 1) FROM attempt_log");
        engine.execute("CREATE TASK " + grandchild + " AFTER " + child + " AS SELECT 1");
        engine.execute("ALTER TASK " + grandchild + " RESUME");
        engine.execute("ALTER TASK " + child + " RESUME");
        final String attempts = "SELECT * FROM " + graphs(root);
        engine.execute("EXECUTE TASK " + root);
        TaskRunWait.untilRows(engine, attempts, 1);
        engine.execute("EXECUTE TASK " + root + " RETRY LAST");
        TaskRunWait.untilRows(engine, attempts, 2);
        engine.execute("INSERT INTO attempt_log VALUES (2)");
        engine.execute("EXECUTE TASK " + root + " RETRY LAST");
        TaskRunWait.untilRows(engine, attempts, 3);
        TaskRunWait.untilRows(engine, "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => '"
            + grandchild + "')) WHERE state = 'SUCCEEDED'", 1);

        assertEquals("1|FAILED|EXECUTE TASK|C|false ;; 2|FAILED|MANUAL RETRY|C|true ;; "
            + "3|SUCCEEDED|MANUAL RETRY|null|false", cells(engine.executeQuery("SELECT attempt_number, state,"
            + " scheduled_from, REPLACE(first_error_task_name, '" + child + "', 'C'), query_start_time IS NULL"
            + " FROM " + graphs(root) + " ORDER BY attempt_number")));
        assertEquals("1|1|3|3", cells(engine.executeQuery("SELECT COUNT(DISTINCT run_id),"
            + " COUNT(DISTINCT graph_run_group_id), COUNT(DISTINCT scheduled_time), COUNT(*) FROM " + graphs(root))));
        assertEquals("C:1 ;; R:1 ;; C:2 ;; C:3 ;; G:3", cells(engine.executeQuery("SELECT REPLACE(REPLACE(REPLACE("
            + "name, '" + grandchild + "', 'G'), '" + child + "', 'C'), '" + root + "', 'R') || ':' || attempt_number"
            + " FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY()) WHERE name LIKE 'TA%" + suffix + "'"
            + " AND state <> 'SCHEDULED' ORDER BY attempt_number, name")));
        assertEquals("Cannot perform retry: run (GRAPH_RUN_GROUP_ID = <ID>, attempt = 3) of graph with root task "
            + root + " had no failures.", refusal("EXECUTE TASK " + root + " RETRY LAST")
            .replaceAll("GRAPH_RUN_GROUP_ID = [0-9a-f-]+", "GRAPH_RUN_GROUP_ID = <ID>"));
    }

    @Test
    public void aRetryOfAFailedRootRunsItAgainAsTheNextAttempt() {
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String root = "TF_ROOT_" + suffix;
        engine.execute("CREATE OR REPLACE TABLE attempt_log (n INT)");
        engine.execute("INSERT INTO attempt_log VALUES (0)");
        engine.execute("CREATE TASK " + root
            + " AS INSERT INTO attempt_log SELECT 1 / (COUNT(*) - 1) FROM attempt_log");
        final String attempts = "SELECT * FROM " + graphs(root);
        engine.execute("EXECUTE TASK " + root);
        TaskRunWait.untilRows(engine, attempts, 1);
        engine.execute("EXECUTE TASK " + root + " RETRY LAST");
        TaskRunWait.untilRows(engine, attempts, 2);
        engine.execute("INSERT INTO attempt_log VALUES (2)");
        engine.execute("EXECUTE TASK " + root + " RETRY LAST");
        TaskRunWait.untilRows(engine, attempts, 3);

        assertEquals("1|FAILED|EXECUTE TASK|R|false ;; 2|FAILED|MANUAL RETRY|R|false ;; "
            + "3|SUCCEEDED|MANUAL RETRY|null|false", cells(engine.executeQuery("SELECT attempt_number, state,"
            + " scheduled_from, REPLACE(first_error_task_name, '" + root + "', 'R'), query_start_time IS NULL"
            + " FROM " + graphs(root) + " ORDER BY attempt_number")));
        // Each attempt is scheduled no earlier than the one before it completed, and keeps the graph run's ids.
        assertEquals("1|null|true ;; 2|true|true ;; 3|true|true", cells(engine.executeQuery("SELECT attempt_number,"
            + " scheduled_time >= LAG(completed_time) OVER (ORDER BY attempt_number),"
            + " run_id = FIRST_VALUE(run_id) OVER (ORDER BY attempt_number)"
            + " FROM " + graphs(root) + " ORDER BY attempt_number")));
        // A new run of the root is a graph run of its own, at its first attempt.
        engine.execute("EXECUTE TASK " + root);
        TaskRunWait.untilRows(engine, attempts, 4);
        assertEquals("2", cells(engine.executeQuery("SELECT COUNT(DISTINCT run_id) FROM " + graphs(root))));
        assertEquals("1|SUCCEEDED|EXECUTE TASK", cells(engine.executeQuery("SELECT attempt_number, state,"
            + " scheduled_from FROM " + graphs(root) + " ORDER BY query_start_time DESC LIMIT 1")));
    }
}
