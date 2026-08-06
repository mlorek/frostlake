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
import dev.frostlake.task.TaskScheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises time-based task scheduling end-to-end through the SQL layer: {@code ALTER TASK … RESUME} arms
 * the {@link dev.frostlake.task.TaskScheduler} so the task fires on its {@code SCHEDULE} and stamps a
 * next-run time — lazy-starting the scheduler if the embedder never did — and {@code start()} arms every
 * task that is already STARTED, which is how a task restored from a snapshot resumes firing. All assertions
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
        engine.execute("CREATE TASK cron_task WAREHOUSE = 'COMPUTE_WH' SCHEDULE = 'USING CRON 0 * * * * UTC' AS SELECT 1");
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

    /** The key the scheduler arms under, for the tests that check what is scheduled. */
    private String key(final String taskName) {
        return TaskScheduler.schedulerKey("TEST_DB", "PUBLIC", taskName);
    }

    /**
     * The case RESUME cannot cover: a snapshot restore writes STARTED straight onto the model without
     * going through resumeTask, so before start() armed what it finds, a task that was running when the
     * engine was checkpointed came back STARTED and never fired again.
     */
    @Test
    public void startArmsATaskRestoredAsStarted() throws Exception {
        engine.execute("CREATE TASK persisted WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");
        engine.execute("ALTER TASK persisted RESUME");
        assertEquals(TaskState.STARTED, task("PERSISTED").getState());

        final Path dir = Files.createTempDirectory("task_sched_restore_");
        try {
            engine.checkpointStateTo(dir);

            final DatabaseEngine restored = new DatabaseEngine();
            try {
                restored.restoreStateFrom(dir);
                final Task restoredTask = restored.getCatalog()
                    .getDatabase("TEST_DB").getSchema("PUBLIC").getTask("PERSISTED");
                assertEquals(TaskState.STARTED, restoredTask.getState(),
                    "the restore must carry the resumed state");
                assertNull(restoredTask.getNextRunTime(),
                    "restoring sets state only — nothing is armed yet");

                restored.startTaskScheduler();

                assertNotNull(restoredTask.getNextRunTime(),
                    "start() must arm a task restored in the STARTED state");
                assertTrue(restored.getTaskScheduler().isScheduled(key("PERSISTED")),
                    "and it must be armed under the canonical DB.SCHEMA.TASK key");
            } finally {
                restored.shutdown();
            }
        } finally {
            deleteRecursively(dir.toFile());
        }
    }

    /** A stop() shuts the timer pool down for good, so a restart needs a fresh one AND a re-arm. */
    @Test
    public void restartAfterStopRearmsFromTheCatalog() {
        engine.execute("CREATE TASK restarted WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");
        engine.execute("ALTER TASK restarted RESUME");
        assertTrue(engine.getTaskScheduler().isScheduled(key("RESTARTED")));

        engine.getTaskScheduler().stop();
        assertFalse(engine.getTaskScheduler().isScheduled(key("RESTARTED")), "stop() cancels every entry");

        engine.startTaskScheduler();

        assertTrue(engine.getTaskScheduler().isRunning());
        assertTrue(engine.getTaskScheduler().isScheduled(key("RESTARTED")),
            "the task is still STARTED, so a restart must arm it again");
    }

    /** Arming on start() must not fire for tasks nobody resumed. */
    @Test
    public void startWithNothingResumedArmsNothing() {
        engine.execute("CREATE TASK idle WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");

        engine.startTaskScheduler();

        assertTrue(engine.getTaskScheduler().isRunning());
        assertEquals(TaskState.SUSPENDED, task("IDLE").getState());
        assertNull(task("IDLE").getNextRunTime(), "a suspended task stays unarmed through start()");
        assertFalse(engine.getTaskScheduler().isScheduled(key("IDLE")));
    }

    /**
     * Key consistency: SUSPEND cancels by the key ALTER builds, so start()'s arming scan must have used
     * the same one. A drift between the two would leave the timer running behind a SUSPENDED task.
     */
    @Test
    public void suspendCancelsWhatStartArmed() {
        engine.execute("CREATE TASK armed WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");
        engine.execute("ALTER TASK armed RESUME");
        engine.getTaskScheduler().stop();
        engine.startTaskScheduler();
        assertTrue(engine.getTaskScheduler().isScheduled(key("ARMED")), "start() armed it");

        engine.execute("ALTER TASK armed SUSPEND");

        assertEquals(TaskState.SUSPENDED, task("ARMED").getState());
        assertFalse(engine.getTaskScheduler().isScheduled(key("ARMED")),
            "SUSPEND must cancel the entry start() created — same key on both paths");
    }

    private void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
