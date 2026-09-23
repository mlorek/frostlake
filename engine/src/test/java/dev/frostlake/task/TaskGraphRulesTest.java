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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The rules a task graph keeps: a finalizer finalizes a root of its own schema and has no schedule, no
 * predecessor and no child; a predecessor is another task of the same schema and no finalizer; a task with a
 * configuration or a finalizer cannot be made a child; overlapping runs and retries are a root's settings. Every
 * assertion holds on the embedded engine and on a real account alike.
 */
public class TaskGraphRulesTest extends BaseDatabaseTest {

    private static final String PREFIX = "TEST_DB.TEST_SCHEMA.";

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    private String taskCell(final String task, final String column) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + task + "'");
        return cell(tasks, soleRowWhere(tasks, "name", task), column);
    }

    /** A cell's text without whitespace, which the account puts inside a JSON array cell. */
    private String compactTaskCell(final String task, final String column) {
        return taskCell(task, column).replaceAll("\\s", "");
    }

    /** A second schema holding a scheduled root R9, the session back in the test schema. */
    private void otherSchemaWithRoot() {
        engine.execute("CREATE SCHEMA s2");
        engine.execute("CREATE TASK s2.r9 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("USE SCHEMA test_schema");
    }

    @Test
    public void aFinalizerFinalizesARootOfItsOwnSchema() {
        otherSchemaWithRoot();
        engine.execute("CREATE TASK r1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK r2 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK c1 AFTER r1 AS SELECT 1");
        assertEquals("Task " + PREFIX + "F2 cannot finalize a non-root task " + PREFIX + "C1.",
            refusalOf("CREATE TASK f2 FINALIZE = c1 AS SELECT 1"));
        engine.execute("CREATE TASK f1 FINALIZE = 'r1' AS SELECT 1");
        assertEquals("{\"FinalizedRootTask\":\"" + PREFIX + "R1\",\"Predecessors\":[]}",
            taskCell("F1", "task_relations"));
        assertEquals("Task " + PREFIX + "F3 cannot finalize a non-root task " + PREFIX + "F1.",
            refusalOf("CREATE TASK f3 FINALIZE = f1 AS SELECT 1"));
        assertEquals("Cannot finalize graph S2.R9 in a different schema.",
            refusalOf("CREATE TASK f4 FINALIZE = s2.r9 AS SELECT 1"));
        assertEquals("Invalid finalized root task S2.NO_SUCH was specified.",
            refusalOf("CREATE TASK f4 FINALIZE = s2.no_such AS SELECT 1"));
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'FINALIZE'",
            refusalOf("CREATE TASK f5 FINALIZE = 5 AS SELECT 1"));
        assertEquals("Invalid finalized root task \"r2\" was specified.",
            refusalOf("CREATE TASK f6 FINALIZE = \"r2\" AS SELECT 1"));
        assertEquals("Task " + PREFIX + "F7 cannot both be a non-root task and have a config.",
            refusalOf("CREATE TASK f7 FINALIZE = r1 CONFIG = '{}' AS SELECT 1"));
        assertEquals("Cannot set allow_overlapping_execution on non-root task " + PREFIX + "F8.",
            refusalOf("CREATE TASK f8 FINALIZE = r1 ALLOW_OVERLAPPING_EXECUTION = TRUE AS SELECT 1"));
        assertEquals("Finalize target R1 already have finalizer.",
            refusalOf("CREATE TASK f9 FINALIZE = r1 ALLOW_OVERLAPPING_EXECUTION = FALSE AS SELECT 1"));
        assertEquals("Task F1 cannot have a schedule and be a finalizer task.",
            refusalOf("ALTER TASK f1 SET SCHEDULE = '5 MINUTE'"));
        assertEquals("Cannot set allow_overlapping_execution on non-root task " + PREFIX + "F1.",
            refusalOf("ALTER TASK f1 SET ALLOW_OVERLAPPING_EXECUTION = TRUE"));
        assertEquals("Task R1 cannot finalize itself.",
            refusalOf("CREATE OR REPLACE TASK r1 FINALIZE = r1 AS SELECT 2"));
        assertEquals("SELECT 1", taskCell("R1", "definition"));
    }

    @Test
    public void aTaskBecomesAFinalizerOnlyAsARootWithoutChildren() {
        engine.execute("CREATE TASK r2 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK x1 AS SELECT 1");
        assertEquals("Task X1 cannot finalize itself.", refusalOf("ALTER TASK x1 SET FINALIZE = x1"));
        engine.execute("CREATE TASK r4 AS SELECT 1");
        engine.execute("CREATE TASK c4 AFTER r4 AS SELECT 1");
        assertEquals("Task " + PREFIX + "R4 cannot have children as a finalizer task.",
            refusalOf("ALTER TASK r4 SET FINALIZE = r2"));
        assertEquals("Task " + PREFIX + "R4 cannot finalize a non-root task " + PREFIX + "C4.",
            refusalOf("ALTER TASK r4 SET FINALIZE = c4"));
        assertEquals("Task " + PREFIX + "C4 cannot have predecessors as a finalizer task.",
            refusalOf("ALTER TASK c4 SET FINALIZE = r2"));
        assertEquals("Task " + PREFIX + "C4 cannot finalize a non-root task " + PREFIX + "C4.",
            refusalOf("ALTER TASK c4 SET FINALIZE = c4"));
        assertEquals("Task " + PREFIX + "R4 cannot have children as a finalizer task.",
            refusalOf("CREATE OR REPLACE TASK r4 FINALIZE = r2 AS SELECT 2"));
        engine.execute("CREATE TASK f2 FINALIZE = r2 AS SELECT 1");
        assertEquals("Cannot replace task " + PREFIX + "R2 with a non-root task because it has a finalizer task. "
            + "Please remove the finalizer task first.", refusalOf("CREATE OR REPLACE TASK r2 AFTER r4 AS SELECT 2"));
    }

    @Test
    public void predecessorsAreTasksOfTheSameSchema() {
        otherSchemaWithRoot();
        engine.execute("CREATE TASK r1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK r2 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK r3 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK c1 AFTER r1 AS SELECT 1");
        assertEquals("Cannot have predecessor R9 from a different schema.",
            refusalOf("CREATE TASK c9 AFTER s2.r9 AS SELECT 1"));
        assertEquals("Cannot have predecessor R9 from a different schema.",
            refusalOf("ALTER TASK c1 ADD AFTER s2.r9"));
        assertEquals("Invalid predecessor NO_SUCH_TASK was specified.",
            refusalOf("ALTER TASK c1 ADD AFTER s2.r9, no_such_task"));
        assertEquals("Invalid predecessor S2.NO_SUCH was specified.", refusalOf("ALTER TASK c1 ADD AFTER s2.no_such"));
        assertEquals("Task C1 does not have predecessor R3.", refusalOf("ALTER TASK c1 REMOVE AFTER no_such_task, r3"));
        assertEquals("Task C1 does not have predecessor R9.", refusalOf("ALTER TASK c1 REMOVE AFTER s2.r9"));
        assertEquals("Invalid predecessor NO_SUCH_TASK was specified.",
            refusalOf("ALTER TASK c1 REMOVE AFTER r1, no_such_task"));
        assertEquals("[\"" + PREFIX + "R1\"]", compactTaskCell("C1", "predecessors"));
        engine.execute("CREATE TASK c6 AFTER r1, r1 AS SELECT 1");
        assertEquals("[\"" + PREFIX + "R1\"]", compactTaskCell("C6", "predecessors"));
        engine.execute("ALTER TASK c1 ADD AFTER r3, r2, r3");
        assertEquals("[\"" + PREFIX + "R1\",\"" + PREFIX + "R2\",\"" + PREFIX + "R3\"]",
            compactTaskCell("C1", "predecessors"));
    }

    @Test
    public void aTaskWithAConfigurationOrAFinalizerStaysARoot() {
        engine.execute("CREATE TASK r3 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK rq CONFIG = '{\"a\": 1}' AS SELECT 1");
        assertEquals("Invalid predecessor NO_SUCH_TASK was specified.", refusalOf("ALTER TASK rq ADD AFTER no_such_task"));
        assertEquals("Task " + PREFIX + "RQ cannot both be a non-root task and have a config.",
            refusalOf("ALTER TASK rq ADD AFTER r3"));
        engine.execute("CREATE TASK rf AS SELECT 1");
        engine.execute("CREATE TASK ff FINALIZE = rf AS SELECT 1");
        assertEquals("This task has a finalizer and cannot be made a child task. Please remove the finalizer before "
            + "adding a predecessor.", refusalOf("ALTER TASK rf ADD AFTER rf"));
        assertEquals("Task " + PREFIX + "FF cannot have children as a finalizer task.",
            refusalOf("ALTER TASK rq ADD AFTER ff"));
    }

    @Test
    public void overlappingRunsAndRetriesAreARootsSettings() {
        engine.execute("CREATE TASK r1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK c1 AFTER r1 AS SELECT 1");
        assertEquals("Cannot set allow_overlapping_execution on non-root task " + PREFIX + "C1.",
            refusalOf("ALTER TASK c1 SET ALLOW_OVERLAPPING_EXECUTION = TRUE"));
        engine.execute("ALTER TASK c1 SET ALLOW_OVERLAPPING_EXECUTION = FALSE");
        assertEquals("Cannot set allow_overlapping_execution on non-root task " + PREFIX + "C2.",
            refusalOf("CREATE TASK c2 ALLOW_OVERLAPPING_EXECUTION = TRUE AFTER r1 AS SELECT 1"));
        engine.execute("CREATE TASK c3 ALLOW_OVERLAPPING_EXECUTION = FALSE AFTER r1 AS SELECT 1");
        assertEquals("Task C1 cannot have both a schedule and a predecessor.",
            refusalOf("ALTER TASK c1 SET SCHEDULE = '5 MINUTE'"));
        final String retries = "Cannot set parameter TASK_AUTO_RETRY_ATTEMPTS on non-root task %s. Non-root tasks "
            + "use the parameter setting from their root task. Please set this on the root task for this task instead.";
        assertEquals(String.format(retries, "C1"), refusalOf("ALTER TASK c1 SET TASK_AUTO_RETRY_ATTEMPTS = 2"));
        assertEquals(String.format(retries, "C4"),
            refusalOf("CREATE TASK c4 TASK_AUTO_RETRY_ATTEMPTS = 2 AFTER r1 AS SELECT 1"));
    }
}
