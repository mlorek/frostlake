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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A task runs in its HOME database/schema, bound to the executing thread only: the scheduler fires
 * on its own thread against the shared engine, so resolving the body's unqualified names — or
 * unwinding a CALL — through the GLOBAL current-database raced whatever the interactive thread had
 * switched to (a slow task restored a stale, possibly dropped, database over the live session's).
 */
public class TaskContextIsolationTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE db_task_home");
        engine.execute("USE DATABASE db_task_home");
        engine.execute("CREATE TABLE public.marker (v NUMBER)");
        engine.execute("CREATE TASK public.t_home SCHEDULE = '60 MINUTES' AS INSERT INTO public.marker VALUES (1)");
        engine.execute("CREATE DATABASE db_other");
        engine.execute("USE DATABASE db_other");
        engine.execute("CREATE TABLE public.marker (v NUMBER)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private Task homeTask() {
        for (final Task t : engine.getCatalog().getDatabase("DB_TASK_HOME").getSchema("PUBLIC").getTasks()) {
            if (t.getName().equalsIgnoreCase("T_HOME")) {
                return t;
            }
        }
        throw new IllegalStateException("task not found");
    }

    @Test
    public void testTaskResolvesUnqualifiedNamesInItsHomeDatabase() {
        // Current database is db_other; the task's body must still write db_task_home's marker.
        engine.getTaskScheduler().executeTaskNow("DB_TASK_HOME.PUBLIC.T_HOME", homeTask());

        assertEquals(1, ((Number) engine.executeQuery("SELECT COUNT(*) FROM db_task_home.public.marker")
            .getRows().get(0).getValue(0)).intValue());
        assertEquals(0, ((Number) engine.executeQuery("SELECT COUNT(*) FROM db_other.public.marker")
            .getRows().get(0).getValue(0)).intValue());
        // And the interactive context is untouched.
        assertEquals("DB_OTHER", engine.getCurrentDatabase());
    }

    @Test
    public void testTaskOnAnotherThreadLeavesInteractiveContextAlone() throws InterruptedException {
        final AtomicReference<Exception> failure = new AtomicReference<>();
        final Thread schedulerLike = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    engine.getTaskScheduler().executeTaskNow("DB_TASK_HOME.PUBLIC.T_HOME", homeTask());
                } catch (final Exception e) {
                    failure.set(e);
                }
            }
        });
        schedulerLike.start();
        schedulerLike.join(30000);

        assertNull(failure.get());
        assertEquals("DB_OTHER", engine.getCurrentDatabase());
        assertEquals(1, ((Number) engine.executeQuery("SELECT COUNT(*) FROM db_task_home.public.marker")
            .getRows().get(0).getValue(0)).intValue());
    }
}
