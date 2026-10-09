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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SHOW TASKS … ROOT ONLY, EXECUTE TASK … RETRY LAST and ALTER TASK … SET | UNSET TAG. */
public class TaskRootOnlyRetryAndTagsTest extends BaseDatabaseTest {

    private String names(final ResultSet rs) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(rs.getRows().get(i).getValue(rs.getColumnIndex("name")));
        }
        return out.toString();
    }

    @Test
    public void rootOnlyKeepsTheTasksWithoutPredecessors() {
        engine.execute("CREATE TASK r_a AS SELECT 1");
        engine.execute("CREATE TASK r_b AFTER r_a AS SELECT 1");
        engine.execute("CREATE TASK r_c AS SELECT 1");
        assertEquals("R_A,R_B,R_C", names(engine.executeQuery("SHOW TASKS LIKE 'R_%'")));
        assertEquals("R_A,R_C", names(engine.executeQuery("SHOW TASKS LIKE 'R_%' ROOT ONLY")));
        assertEquals("R_A", names(engine.executeQuery("SHOW TASKS IN SCHEMA test_db.test_schema STARTS WITH 'R_'"
            + " ROOT ONLY LIMIT 1 FROM 'R_A'")), "a task listing's page starts at the name itself");
        assertEquals("R_C", names(engine.executeQuery("SHOW TASKS IN SCHEMA test_db.test_schema STARTS WITH 'R_'"
            + " ROOT ONLY LIMIT 1 FROM 'R_B'")), "at the first root named at or after it");
        assertEquals("R_B,R_C", names(engine.executeQuery("SHOW TASKS IN SCHEMA test_db.test_schema STARTS WITH 'R_'"
            + " LIMIT 2 FROM 'R_B'")));
        assertEquals("R_A", names(engine.executeQuery("SHOW TERSE TASKS LIKE 'R_A' ROOT ONLY")));
    }

    @Test
    public void rootOnlyIsATaskListingsModifierOnly() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SHOW TABLES ROOT ONLY");
            }
        });
        assertTrue(refused.getMessage().contains("unexpected 'ROOT'"), refused.getMessage());
        engine.execute("CREATE TABLE words (root INT, retry INT)");
        engine.execute("INSERT INTO words VALUES (1, 2)");
        assertEquals(3L, ((Number) engine.executeQuery("SELECT root + retry FROM words").getRows().get(0).getValue(0))
            .longValue());
    }

    @Test
    public void retryLastRerunsTheFailedPartOfTheLastGraphRun() {
        // The run history outlives the database by the task's name, so the tasks are named apart from any other
        // run's.
        final String suffix = Long.toString(System.nanoTime() % 100000000L);
        final String root = "RT_ROOT_" + suffix;
        final String child = "RT_CHILD_" + suffix;
        engine.execute("CREATE TABLE retry_log (n INT)");
        engine.execute("CREATE TASK " + root + " AS INSERT INTO retry_log VALUES (1)");
        engine.execute("CREATE TASK " + child + " AFTER " + root + " AS SELECT 1/0");
        engine.execute("ALTER TASK " + child + " RESUME");
        final RuntimeException neverRun = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("EXECUTE TASK " + root + " RETRY LAST");
            }
        });
        assertEquals("Cannot perform retry: no suitable run of graph with root task " + root + " to retry.",
            neverRun.getMessage());
        final String runsOfChild = "SELECT name, scheduled_from FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY("
            + "TASK_NAME => '" + child + "')) WHERE state = 'FAILED'";
        engine.execute("EXECUTE TASK " + root);
        TaskRunWait.untilRows(engine, runsOfChild, 1);
        engine.execute("EXECUTE TASK " + root + " RETRY LAST");
        TaskRunWait.untilRows(engine, runsOfChild, 2);
        assertEquals(1L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM retry_log").getRows().get(0)
            .getValue(0)).longValue(), "the root succeeded, so only the failed child runs again");
        final ResultSet history = engine.executeQuery(runsOfChild + " AND scheduled_from = 'MANUAL RETRY'");
        assertEquals(1, history.getRowCount());
        assertEquals(child, history.getRows().get(0).getValue(0));
    }

    @Test
    public void aTaskCarriesTags() {
        engine.execute("CREATE TAG cost_center");
        engine.execute("CREATE TASK tagged_task AS SELECT 1");
        engine.execute("ALTER TASK tagged_task SET TAG cost_center = 'fin'");
        assertEquals("fin", engine.executeQuery("SELECT SYSTEM$GET_TAG('cost_center', 'tagged_task', 'TASK')")
            .getRows().get(0).getValue(0));
        final ResultSet tags = engine.executeQuery("SELECT tag_name, tag_value, level FROM TABLE("
            + "INFORMATION_SCHEMA.TAG_REFERENCES('test_db.test_schema.tagged_task', 'TASK'))");
        assertEquals(1, tags.getRowCount());
        assertEquals("TASK", tags.getRows().get(0).getValue(2));
        engine.execute("ALTER TASK tagged_task UNSET TAG cost_center");
        assertEquals(0, engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES("
            + "'tagged_task', 'TASK'))").getRowCount());
    }
}
