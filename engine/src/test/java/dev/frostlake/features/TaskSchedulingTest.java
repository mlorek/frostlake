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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises time-based task scheduling end-to-end through the SQL layer: {@code ALTER TASK … RESUME} arms
 * the {@link dev.frostlake.task.TaskScheduler} (when it is running) so the task fires on its {@code SCHEDULE}
 * and stamps a next-run time; RESUME issued before the scheduler is started only flips state. All assertions
 * read state that {@code resumeTask}/{@code scheduleTask} set synchronously, so there are no sleeps and no
 * waiting for an actual firing (per the no-timing-assertions rule). {@code scheduleAtFixedRate} uses an
 * initial delay of 0, so the task bodies are read-only {@code SELECT 1} to keep the incidental immediate run
 * harmless; {@code shutdown()} in teardown cancels the timer and waits for any in-flight run.
 */
public class TaskSchedulingTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown(); // stops the scheduler (cancels futures, awaits in-flight runs) if started
        }
    }

    private Task task(final String name) {
        return engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getTask(name);
    }

    @Test
    public void resumeArmsSchedulerAndStampsNextRun() {
        engine.startTaskScheduler();
        assertTrue(engine.getTaskScheduler().isRunning(), "scheduler should be running after start");

        engine.execute("CREATE TASK sched_task WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");
        final Task t = task("SCHED_TASK");
        assertEquals(TaskState.SUSPENDED, t.getState(), "a new task starts suspended");
        assertNull(t.getNextRunTime(), "a suspended task is not scheduled");

        engine.execute("ALTER TASK sched_task RESUME");

        assertEquals(TaskState.STARTED, t.getState());
        assertNotNull(t.getNextRunTime(),
            "RESUME with the scheduler running must arm the task on its schedule (next run time stamped)");
    }

    @Test
    public void nextRunTimeReflectsMinutesInterval() {
        final LocalDateTime before = LocalDateTime.now();
        engine.startTaskScheduler();
        engine.execute("CREATE TASK every5 WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");
        engine.execute("ALTER TASK every5 RESUME");

        final LocalDateTime next = task("EVERY5").getNextRunTime();
        assertNotNull(next, "a resumed scheduled task must have a next run time");
        // next == scheduled-at + 5 minutes; bound generously so the assertion is not clock-flaky.
        assertTrue(next.isAfter(before.plusMinutes(4)), "next run should be ~5 minutes out, was " + next);
        assertTrue(next.isBefore(before.plusMinutes(7)), "next run should be ~5 minutes out, was " + next);
    }

    @Test
    public void cronScheduledTaskIsArmed() {
        engine.startTaskScheduler();
        engine.execute("CREATE TASK cron_task WAREHOUSE = 'COMPUTE_WH' SCHEDULE = 'USING CRON 0 * * * *' AS SELECT 1");
        engine.execute("ALTER TASK cron_task RESUME");

        final Task t = task("CRON_TASK");
        assertEquals(TaskState.STARTED, t.getState());
        assertNotNull(t.getNextRunTime(), "a CRON-scheduled task must be armed when resumed");
    }

    @Test
    public void resumeLazilyStartsSchedulerAndArms() {
        // RESUME is what activates a task (Snowflake semantics): the scheduler lazy-starts on the
        // first RESUME, so the embedder does not have to call startTaskScheduler() first.
        assertFalse(engine.getTaskScheduler().isRunning(), "scheduler is not running by default");

        engine.execute("CREATE TASK unsched WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");
        engine.execute("ALTER TASK unsched RESUME");

        final Task t = task("UNSCHED");
        assertEquals(TaskState.STARTED, t.getState(), "RESUME flips state to STARTED");
        assertNotNull(t.getNextRunTime(), "RESUME arms the schedule without a manual scheduler start");
        assertTrue(engine.getTaskScheduler().isRunning(), "scheduler lazy-started on RESUME");
    }
}
