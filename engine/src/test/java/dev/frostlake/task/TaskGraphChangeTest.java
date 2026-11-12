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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * How a task graph may change. A graph may close a loop through other tasks, and a task of the loop then has a
 * predecessor (TaskGraphLoopTest asks the account the same); the root "must be suspended before any task is
 * recreated" and before "a child task is added (using CREATE TASK … AFTER or ALTER TASK … ADD AFTER) or removed
 * (using ALTER TASK … REMOVE AFTER)" (documented), which CREATE OR ALTER of a child follows too. SHOW TASKS is not run
 * on the live account, so the engine alone is asked.
 */
public class TaskGraphChangeTest extends BaseDatabaseTest {

    private static final String PREFIX = "TEST_DB.TEST_SCHEMA.";

    private static final String RUNNING = "Unable to update graph with root task " + PREFIX
        + "GC_R since that root task is not suspended.";

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "SHOW TASKS is not run on the live account");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String taskCell(final String task, final String column) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + task + "'");
        return cell(tasks, soleRowWhere(tasks, "name", task), column).replaceAll("\\s", "");
    }

    @Test
    public void aGraphMayCloseALoopThroughOtherTasks() {
        engine.execute("CREATE TASK gc_a SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK gc_b AFTER gc_a AS SELECT 2");
        engine.execute("CREATE TASK gc_c AFTER gc_b AS SELECT 3");
        // A follows its own descendant C, and B follows its own child C as well: the account takes both.
        engine.execute("CREATE OR ALTER TASK gc_a AFTER gc_c AS SELECT 1");
        engine.execute("ALTER TASK gc_b ADD AFTER gc_c");
        assertEquals("[\"" + PREFIX + "GC_C\"]", taskCell("GC_A", "predecessors"));
        assertEquals("[\"" + PREFIX + "GC_A\",\"" + PREFIX + "GC_C\"]", taskCell("GC_B", "predecessors"));
        assertEquals("[\"" + PREFIX + "GC_B\"]", taskCell("GC_C", "predecessors"));
        // A task named as its own predecessor is still refused.
        assertEquals("Task GC_B cannot use itself as its predecessor.", refusalOf("ALTER TASK gc_b ADD AFTER gc_b"));
        // Dropping a task of the loop takes it out of the lists it was in.
        engine.execute("DROP TASK gc_c");
        assertEquals("[]", taskCell("GC_A", "predecessors"));
        assertEquals("[\"" + PREFIX + "GC_A\"]", taskCell("GC_B", "predecessors"));
    }

    @Test
    public void theGraphOfARunningRootIsNotChanged() {
        engine.execute("CREATE TASK gc_r SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_b AFTER gc_r AS SELECT 2");
        engine.execute("CREATE TASK gc_c AFTER gc_b AS SELECT 3");
        engine.execute("ALTER TASK gc_r RESUME");
        try {
            assertEquals(RUNNING, refusalOf("CREATE TASK gc_x AFTER gc_r AS SELECT 3"));
            assertEquals(RUNNING, refusalOf("CREATE TASK gc_x AFTER gc_c AS SELECT 3"));
            assertEquals(RUNNING, refusalOf("ALTER TASK gc_b REMOVE AFTER gc_r"));
            assertEquals(RUNNING, refusalOf("ALTER TASK gc_c ADD AFTER gc_r"));
            assertEquals(RUNNING, refusalOf("CREATE OR REPLACE TASK gc_b AFTER gc_r AS SELECT 97"));
            // CREATE OR ALTER of a child of the running root is refused as ALTER TASK … ADD AFTER is.
            assertEquals(RUNNING, refusalOf("CREATE OR ALTER TASK gc_b AFTER gc_r AS SELECT 99"));
            assertEquals(RUNNING, refusalOf("CREATE OR ALTER TASK gc_c AS SELECT 98"));
            assertEquals("[\"" + PREFIX + "GC_R\"]", taskCell("GC_B", "predecessors"));
            assertEquals("SELECT2", taskCell("GC_B", "definition"));
            assertEquals("[\"" + PREFIX + "GC_B\"]", taskCell("GC_C", "predecessors"));
            assertTrue(engine.executeQuery("SHOW TASKS LIKE 'GC_X'").getRows().isEmpty());
        } finally {
            engine.execute("ALTER TASK gc_r SUSPEND");
        }
        // Suspended, the root's graph takes each change.
        engine.execute("CREATE TASK gc_x AFTER gc_r AS SELECT 3");
        engine.execute("ALTER TASK gc_c ADD AFTER gc_r");
        engine.execute("ALTER TASK gc_c REMOVE AFTER gc_r");
        engine.execute("CREATE OR ALTER TASK gc_c AFTER gc_b AS SELECT 98");
        engine.execute("CREATE OR REPLACE TASK gc_b AFTER gc_r AS SELECT 97");
        assertEquals("SELECT97", taskCell("GC_B", "definition"));
    }

    @Test
    public void aFinalizerIsNotAttachedToARunningRoot() {
        engine.execute("CREATE TASK gc_r SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_b AFTER gc_r AS SELECT 2");
        engine.execute("CREATE TASK gc_o SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_f AS SELECT 5");
        engine.execute("ALTER TASK gc_r RESUME");
        engine.execute("ALTER TASK gc_o RESUME");
        try {
            assertEquals(RUNNING, refusalOf("ALTER TASK gc_f SET FINALIZE = gc_r"));
            assertEquals(RUNNING, refusalOf("CREATE TASK gc_g FINALIZE = gc_r AS SELECT 9"));
            // A root that runs alone takes no finalizer either, as it takes no child.
            final String alone = "Unable to update graph with root task " + PREFIX
                + "GC_O since that root task is not suspended.";
            assertEquals(alone, refusalOf("ALTER TASK gc_f SET FINALIZE = gc_o"));
            assertEquals(alone, refusalOf("CREATE OR REPLACE TASK gc_g FINALIZE = gc_o AS SELECT 9"));
            assertFalse(taskCell("GC_R", "task_relations").contains("FinalizerTask"));
            assertFalse(taskCell("GC_O", "task_relations").contains("FinalizerTask"));
            assertTrue(engine.executeQuery("SHOW TASKS LIKE 'GC_G'").getRows().isEmpty());
        } finally {
            engine.execute("ALTER TASK gc_r SUSPEND");
            engine.execute("ALTER TASK gc_o SUSPEND");
        }
        // Suspended, each root takes its finalizer.
        engine.execute("ALTER TASK gc_f SET FINALIZE = gc_r");
        engine.execute("CREATE TASK gc_g FINALIZE = gc_o AS SELECT 9");
        assertTrue(taskCell("GC_R", "task_relations").contains("\"FinalizerTask\":\"" + PREFIX + "GC_F\""));
        assertTrue(taskCell("GC_O", "task_relations").contains("\"FinalizerTask\":\"" + PREFIX + "GC_G\""));
    }

    @Test
    public void droppingATaskSuspendsTheTasksAfterIt() {
        engine.execute("CREATE TASK gc_r SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_b AFTER gc_r AS SELECT 2");
        engine.execute("CREATE TASK gc_c AFTER gc_b AS SELECT 3");
        engine.execute("CREATE TASK gc_d AFTER gc_r, gc_b AS SELECT 4");
        engine.execute("SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('gc_r')");
        engine.execute("ALTER TASK gc_r SUSPEND");
        assertEquals("started", taskCell("GC_C", "state"));
        assertEquals("started", taskCell("GC_D", "state"));
        engine.execute("DROP TASK gc_b");
        // C now stands alone and D runs after R alone; both are suspended until resumed by hand.
        assertEquals("suspended", taskCell("GC_C", "state"));
        assertEquals("[]", taskCell("GC_C", "predecessors"));
        assertEquals("suspended", taskCell("GC_D", "state"));
        assertEquals("[\"" + PREFIX + "GC_R\"]", taskCell("GC_D", "predecessors"));
    }

    @Test
    public void aStandaloneRunningTaskIsNotAGraphTheChangeTouches() {
        engine.execute("CREATE TASK gc_r SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_o SCHEDULE = '60 MINUTE' AS SELECT 0");
        engine.execute("CREATE TASK gc_b AFTER gc_r AS SELECT 2");
        engine.execute("ALTER TASK gc_o RESUME");
        try {
            // Another root runs; this graph's root is suspended.
            engine.execute("CREATE TASK gc_x AFTER gc_b AS SELECT 3");
            engine.execute("CREATE OR ALTER TASK gc_b AFTER gc_r AS SELECT 5");
        } finally {
            engine.execute("ALTER TASK gc_o SUSPEND");
        }
    }
}
