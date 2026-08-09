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
import dev.frostlake.config.EngineConfig;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.metastore.model.TaskState;
import dev.frostlake.task.TaskScheduler;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code tasks.autostart} — whether the engine arms the task scheduler as it finishes starting.
 *
 * <p>The flag exists for the restore case: a snapshot writes a task's STARTED state without arming
 * anything, so without either this flag or a manual {@code startTaskScheduler()} a task that was
 * running before a restart comes back dormant. It defaults to FALSE because starting the scheduler
 * spawns timer threads and begins running task bodies against restored data immediately.
 */
public class TaskSchedulerAutoStartTest {

    private EngineConfig configWith(final String autoStart, final Path persistTo) {
        final EngineConfig config = new EngineConfig();
        config.setProperty(EngineConfig.PROP_TASKS_AUTOSTART, autoStart);
        if (persistTo != null) {
            config.setProperty(EngineConfig.PROP_PERSISTENCE_ENABLED, "true");
            config.setProperty(EngineConfig.PROP_PERSISTENCE_DIRECTORY, persistTo.toString());
            config.setProperty(EngineConfig.PROP_PERSISTENCE_AUTO_SAVE, "false");
        }
        return config;
    }

    @Test
    public void theFlagDefaultsToOff() {
        assertFalse(new EngineConfig().isTaskSchedulerAutoStart(),
            "an engine must not spawn timer threads unless asked");
    }

    /** Off (the default): the scheduler stays down until something asks for it. */
    @Test
    public void schedulerStaysDownWhenTheFlagIsOff() {
        final DatabaseEngine engine = new DatabaseEngine(configWith("false", null));
        try {
            assertFalse(engine.getTaskScheduler().isRunning());
        } finally {
            engine.shutdown();
        }
    }

    /** On: the scheduler is up the moment the engine is, with nothing else called. */
    @Test
    public void schedulerIsUpWhenTheFlagIsOn() {
        final DatabaseEngine engine = new DatabaseEngine(configWith("true", null));
        try {
            assertTrue(engine.getTaskScheduler().isRunning(),
                "tasks.autostart=true must start the scheduler during engine startup");
        } finally {
            engine.shutdown();
        }
    }

    /**
     * The case the flag is for, end to end: a task resumed in one engine, persisted, and restored into a
     * new one is armed at boot — no RESUME and no startTaskScheduler() call in the second engine.
     */
    @Test
    public void aRestoredStartedTaskIsArmedAtBoot() throws Exception {
        final Path dir = Files.createTempDirectory("task_autostart_");
        try {
            final DatabaseEngine first = new DatabaseEngine(configWith("false", dir));
            try {
                first.execute("CREATE DATABASE task_db");
                first.execute("USE DATABASE task_db");
                first.execute("USE SCHEMA public");
                first.execute("CREATE TASK nightly WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");
                first.execute("ALTER TASK nightly RESUME");
            } finally {
                first.shutdown();   // snapshot-persistence mode saves the catalog on shutdown
            }

            final DatabaseEngine restarted = new DatabaseEngine(configWith("true", dir));
            try {
                final Task restored = restarted.getCatalog()
                    .getDatabase("TASK_DB").getSchema("PUBLIC").getTask("NIGHTLY");
                assertNotNull(restored, "the task must survive the restart");
                assertEquals(TaskState.STARTED, restored.getState(), "and come back resumed");
                assertTrue(restarted.getTaskScheduler().isScheduled(
                        TaskScheduler.schedulerKey("TASK_DB", "PUBLIC", "NIGHTLY")),
                    "tasks.autostart=true must arm it without a RESUME or a manual scheduler start");
                assertNotNull(restored.getNextRunTime(), "and stamp its next run");
            } finally {
                restarted.shutdown();
            }
        } finally {
            deleteRecursively(dir.toFile());
        }
    }

    /** With the flag off, the same restored task comes back resumed but dormant — the documented default. */
    @Test
    public void aRestoredStartedTaskStaysDormantWhenTheFlagIsOff() throws Exception {
        final Path dir = Files.createTempDirectory("task_autostart_off_");
        try {
            final DatabaseEngine first = new DatabaseEngine(configWith("false", dir));
            try {
                first.execute("CREATE DATABASE task_db");
                first.execute("USE DATABASE task_db");
                first.execute("USE SCHEMA public");
                first.execute("CREATE TASK nightly WAREHOUSE = 'COMPUTE_WH' SCHEDULE = '5 MINUTES' AS SELECT 1");
                first.execute("ALTER TASK nightly RESUME");
            } finally {
                first.shutdown();   // snapshot-persistence mode saves the catalog on shutdown
            }

            final DatabaseEngine restarted = new DatabaseEngine(configWith("false", dir));
            try {
                final Task restored = restarted.getCatalog()
                    .getDatabase("TASK_DB").getSchema("PUBLIC").getTask("NIGHTLY");
                assertEquals(TaskState.STARTED, restored.getState());
                assertFalse(restarted.getTaskScheduler().isRunning());
                assertNull(restored.getNextRunTime(), "nothing is armed until the embedder asks");
            } finally {
                restarted.shutdown();
            }
        } finally {
            deleteRecursively(dir.toFile());
        }
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
