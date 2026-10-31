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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * While the root of a graph with a child or a finalizer runs, none of the graph's tasks is altered, redefined or
 * dropped and no child or finalizer is resumed or suspended, IF EXISTS or not; the tasks' tags and contacts still
 * change, the root still resumes and suspends, and CREATE OR REPLACE of the root is taken. A parameter a child takes
 * from its root is refused before the graph is asked, wherever the statement names it. A task that runs alone takes
 * every change but a child (all live-verified).
 */
public class RunningGraphChangeTest extends MeasuredCellsTestSupport {

    @Test
    public void orAlterUnderARunningRoot() {
        engine.execute("CREATE OR REPLACE TASK rt SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK ct AFTER rt AS SELECT 1");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct RESUME");
        assertAnswer("Statement executed successfully.", "ALTER TASK rt RESUME");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "CREATE OR ALTER TASK ct AFTER rt AS SELECT 2");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "CREATE OR ALTER TASK ct2 AFTER rt AS SELECT 2");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "CREATE OR ALTER TASK rt SCHEDULE = '60 MINUTE' AS SELECT 3");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "CREATE OR REPLACE TASK ct AFTER rt AS SELECT 4");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct SUSPEND");
        assertAnswer("Statement executed successfully.", "ALTER TASK rt SUSPEND");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER TASK ct AFTER rt AS SELECT 5");
        assertAnswer("Task CT2 successfully created.", "CREATE OR ALTER TASK ct2 AFTER rt AS SELECT 5");
    }

    @Test
    public void everyChangeToARunningGraphIsRefused() {
        engine.execute("CREATE OR REPLACE TASK rt SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK ct AFTER rt AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK ct2 AFTER rt AS SELECT 1");
        engine.execute("ALTER TASK ct RESUME");
        engine.execute("ALTER TASK rt RESUME");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct2 RESUME");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct RESUME");
        assertAnswer("Statement executed successfully.", "ALTER TASK rt RESUME");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct SET COMMENT = 'x'");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct UNSET COMMENT");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct MODIFY AS SELECT 9");
        assertAnswer("REFUSED Cannot set parameter SUSPEND_TASK_AFTER_NUM_FAILURES on non-root task CT. Non-root "
                + "tasks use the parameter setting from their root task. Please set this on the root task for this "
                + "task instead.",
            "ALTER TASK ct SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK rt SET COMMENT = 'x'");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK rt MODIFY AS SELECT 9");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct2 SUSPEND");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK IF EXISTS ct SUSPEND");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct REMOVE AFTER rt");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "DROP TASK ct2");
    }

    @Test
    public void aTaskThatRunsAloneTakesEveryChange() {
        engine.execute("CREATE OR REPLACE TAG t582");
        engine.execute("CREATE OR REPLACE CONTACT c582 URL = 'https://example.com/help'");
        engine.execute("CREATE OR REPLACE TASK st SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK st3 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("ALTER TASK st RESUME");
        engine.execute("ALTER TASK st3 RESUME");
        assertAnswer("Statement executed successfully.", "ALTER TASK st SET COMMENT = 'x'");
        assertAnswer("Statement executed successfully.", "ALTER TASK st MODIFY AS SELECT 2");
        assertAnswer("Statement executed successfully.", "ALTER TASK st SET SCHEDULE = '30 MINUTE'");
        assertAnswer("Statement executed successfully.", "ALTER TASK st UNSET COMMENT");
        assertAnswer("Statement executed successfully.", "ALTER TASK st SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3");
        assertAnswer("Statement executed successfully.", "ALTER TASK st SET TAG t582 = 'v'");
        assertAnswer("Statement executed successfully.", "ALTER TASK st UNSET TAG t582");
        assertAnswer("Statement executed successfully.", "ALTER TASK st SET CONTACT SUPPORT = c582");
        assertAnswer("Statement executed successfully.", "ALTER TASK st UNSET CONTACT SUPPORT");
        assertAnswer("Statement executed successfully.", "CREATE OR ALTER TASK st SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertAnswer("Task ST successfully created.", "CREATE OR REPLACE TASK st SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertAnswer("Statement executed successfully.", "ALTER TASK st RESUME");
        assertAnswer("ST3 successfully dropped.", "DROP TASK st3");
    }

    @Test
    public void aRunningRootLocksItsGraphButItsTags() {
        engine.execute("CREATE OR REPLACE TAG t582");
        engine.execute("CREATE OR REPLACE CONTACT c582 URL = 'https://example.com/help'");
        engine.execute("CREATE OR REPLACE TASK rt SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK ct AFTER rt AS SELECT 1");
        engine.execute("ALTER TASK ct RESUME");
        engine.execute("ALTER TASK rt RESUME");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct SET TAG t582 = 'v'");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct UNSET TAG t582");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct SET CONTACT SUPPORT = c582");
        assertAnswer("Statement executed successfully.", "ALTER TASK rt SET TAG t582 = 'v'");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK rt UNSET COMMENT");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK rt SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK rt SET SCHEDULE = '30 MINUTE'");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct SET SCHEDULE = '30 MINUTE'");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct SET USER_TASK_TIMEOUT_MS = 1000");
        assertAnswer("REFUSED Cannot set parameter TASK_AUTO_RETRY_ATTEMPTS on non-root task CT. Non-root tasks use "
                + "the parameter setting from their root task. Please set this on the root task for this task instead.",
            "ALTER TASK ct SET TASK_AUTO_RETRY_ATTEMPTS = 1");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct SET ALLOW_OVERLAPPING_EXECUTION = TRUE");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "DROP TASK ct");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "DROP TASK rt");
    }

    @Test
    public void aChildTakesNoRootParameter() {
        engine.execute("CREATE OR REPLACE TASK rt2 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK ct2 AFTER rt2 AS SELECT 1");
        assertAnswer("REFUSED Cannot set parameter SUSPEND_TASK_AFTER_NUM_FAILURES on non-root task CT2. Non-root "
                + "tasks use the parameter setting from their root task. Please set this on the root task for this "
                + "task instead.",
            "ALTER TASK ct2 SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3");
        assertAnswer("REFUSED Cannot set parameter TASK_AUTO_RETRY_ATTEMPTS on non-root task CT2. Non-root tasks use "
                + "the parameter setting from their root task. Please set this on the root task for this task instead.",
            "ALTER TASK ct2 SET TASK_AUTO_RETRY_ATTEMPTS = 1");
        assertAnswer("REFUSED Cannot set allow_overlapping_execution on non-root task TEST_DB.TEST_SCHEMA.CT2.",
            "ALTER TASK ct2 SET ALLOW_OVERLAPPING_EXECUTION = TRUE");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct2 SET USER_TASK_TIMEOUT_MS = 1000");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct2 SET COMMENT = 'x'");
        assertAnswer("REFUSED Task CT2 cannot have both a schedule and a predecessor.",
            "ALTER TASK ct2 SET SCHEDULE = '30 MINUTE'");
        assertAnswer("REFUSED Cannot set parameter SUSPEND_TASK_AFTER_NUM_FAILURES on non-root task CT2. Non-root "
                + "tasks use the parameter setting from their root task. Please set this on the root task for this "
                + "task instead.",
            "ALTER TASK ct2 SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3, COMMENT = 'y'");
        assertAnswer("Statement executed successfully.", "ALTER TASK ct2 UNSET SUSPEND_TASK_AFTER_NUM_FAILURES");
    }

    @Test
    public void rootParametersAreRefusedBeforeTheGraph() {
        engine.execute("CREATE OR REPLACE TASK rt SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK ct AFTER rt AS SELECT 1");
        engine.execute("ALTER TASK ct RESUME");
        engine.execute("ALTER TASK rt RESUME");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct REMOVE WHEN");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct UNSET SUSPEND_TASK_AFTER_NUM_FAILURES");
        assertAnswer("REFUSED Cannot set parameter SUSPEND_TASK_AFTER_NUM_FAILURES on non-root task CT. Non-root "
                + "tasks use the parameter setting from their root task. Please set this on the root task for this "
                + "task instead.",
            "ALTER TASK ct SET SUSPEND_TASK_AFTER_NUM_FAILURES = 3, COMMENT = 'y'");
        assertAnswer("REFUSED Cannot set parameter TASK_AUTO_RETRY_ATTEMPTS on non-root task CT. Non-root tasks use "
                + "the parameter setting from their root task. Please set this on the root task for this task instead.",
            "ALTER TASK ct SET COMMENT = 'y', TASK_AUTO_RETRY_ATTEMPTS = 1");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "ALTER TASK ct UNSET COMMENT, SUSPEND_TASK_AFTER_NUM_FAILURES");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.RT since that root task is "
                + "not suspended.",
            "DROP TASK IF EXISTS ct");
        assertAnswer("Task RT successfully created.", "CREATE OR REPLACE TASK rt SCHEDULE = '60 MINUTE' AS SELECT 1");
    }

    @Test
    public void noChildJoinsALoneRunningTask() {
        engine.execute("CREATE OR REPLACE TASK st SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK lone AS SELECT 1");
        engine.execute("ALTER TASK st RESUME");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.ST since that root task is "
                + "not suspended.",
            "ALTER TASK lone ADD AFTER st");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.ST since that root task is "
                + "not suspended.",
            "CREATE OR ALTER TASK nc AFTER st AS SELECT 1");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.ST since that root task is "
                + "not suspended.",
            "CREATE TASK nc2 AFTER st AS SELECT 1");
        assertAnswer("REFUSED Task ST does not have predecessor LONE.", "ALTER TASK st REMOVE AFTER lone");
    }

    @Test
    public void aFinalizerLocksItsRootsGraph() {
        engine.execute("CREATE OR REPLACE TASK fr SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK fin FINALIZE = fr AS SELECT 1");
        engine.execute("ALTER TASK fin RESUME");
        engine.execute("ALTER TASK fr RESUME");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.FR since that root task is "
                + "not suspended.",
            "ALTER TASK fr SET COMMENT = 'x'");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.FR since that root task is "
                + "not suspended.",
            "ALTER TASK fin SET COMMENT = 'x'");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.FR since that root task is "
                + "not suspended.",
            "ALTER TASK fin SUSPEND");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.FR since that root task is "
                + "not suspended.",
            "ALTER TASK fin RESUME");
        assertAnswer("REFUSED Unable to update graph with root task TEST_DB.TEST_SCHEMA.FR since that root task is "
                + "not suspended.",
            "DROP TASK fin");
    }
}
