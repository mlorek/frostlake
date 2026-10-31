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
import dev.frostlake.task.TaskGraphs;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An interval schedule counts elapsed time: the fire after one is the interval later on the instant line, whatever
 * the host's clock does in between, so a 60-minute task's next fire reads 3600 seconds after the one it follows
 * across a night the clock falls back or springs forward, as on the account.
 */
public class TaskIntervalElapsedTimeTest {

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
            engine.shutdown();
        }
    }

    private Task task(final String name) {
        return engine.getCatalog().getDatabase("TEST_DB").getSchema("PUBLIC").getTask(name);
    }

    @Test
    public void theNextFireIsTheIntervalOfElapsedTimeLater() {
        engine.execute("CREATE TASK every_hour SCHEDULE = '60 MINUTE' AS SELECT 1");
        // The hours around a night Europe/London's clock falls back, and around one it springs forward.
        for (final String at : new String[] {"2026-10-25T00:10:00Z", "2026-10-25T00:59:59Z", "2026-10-25T01:10:00Z",
            "2026-03-29T00:30:00Z", "2026-03-29T01:00:00Z"}) {
            final Instant scheduled = Instant.parse(at);
            assertEquals(scheduled.plusSeconds(3600), TaskGraphs.fireAfter(task("EVERY_HOUR"), scheduled), at);
        }
    }

    @Test
    public void aResumedTaskIsArmedAnIntervalAheadOfNow() {
        final Instant before = Instant.now();
        engine.execute("CREATE TASK every_ten SCHEDULE = '10 MINUTE' AS SELECT 1");
        engine.execute("ALTER TASK every_ten RESUME");
        final Instant next = task("EVERY_TEN").getNextRunTime();
        assertNotNull(next);
        assertTrue(!next.isBefore(before.plus(Duration.ofMinutes(10))), "armed at " + next);
        assertTrue(next.isBefore(Instant.now().plus(Duration.ofMinutes(11))), "armed at " + next);
    }
}
