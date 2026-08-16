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
import dev.frostlake.task.TaskScheduler;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A resumed CRON task is armed for its next CALENDAR fire, read in the schedule's own zone — not
 * for a fixed interval counted from the RESUME. The assertions read the next-run stamp that RESUME
 * sets synchronously, so nothing waits for an actual firing; the schedules chosen fire at most once
 * a year, so no run happens while the test is up.
 */
public class TaskCronArmingTest {

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

    /** The next-run stamp is the host's wall clock, as every other task stamp is. */
    private static LocalDateTime hostWallClock(final ZonedDateTime instant) {
        return instant.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    @Test
    public void resumeArmsTheNextCalendarFire() {
        engine.execute("CREATE TASK new_year SCHEDULE = 'USING CRON 0 0 1 1 * UTC' AS SELECT 1");
        engine.execute("ALTER TASK new_year RESUME");

        final ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        final ZonedDateTime newYear = ZonedDateTime.of(now.getYear() + 1, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(hostWallClock(newYear), task("NEW_YEAR").getNextRunTime());
        assertTrue(engine.getTaskScheduler().isScheduled(TaskScheduler.schedulerKey("TEST_DB", "PUBLIC", "NEW_YEAR")));
    }

    @Test
    public void theScheduleIsReadInItsOwnZone() {
        engine.execute("CREATE TASK noon_tokyo SCHEDULE = 'USING CRON 0 12 25 12 * Asia/Tokyo' AS SELECT 1");
        engine.execute("ALTER TASK noon_tokyo RESUME");

        final ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        final ZonedDateTime now = ZonedDateTime.now(tokyo);
        ZonedDateTime expected = ZonedDateTime.of(now.getYear(), 12, 25, 12, 0, 0, 0, tokyo);
        if (!expected.isAfter(now)) {
            expected = expected.plusYears(1);
        }
        assertEquals(hostWallClock(expected), task("NOON_TOKYO").getNextRunTime());
    }

    @Test
    public void suspendRetiresTheTimerAndResumeRearmsIt() {
        final String key = TaskScheduler.schedulerKey("TEST_DB", "PUBLIC", "YEARLY");
        engine.execute("CREATE TASK yearly SCHEDULE = 'USING CRON 0 0 1 1 * UTC' AS SELECT 1");
        engine.execute("ALTER TASK yearly RESUME");
        assertTrue(engine.getTaskScheduler().isScheduled(key));

        engine.execute("ALTER TASK yearly SUSPEND");
        assertFalse(engine.getTaskScheduler().isScheduled(key), "SUSPEND must retire the CRON timer");

        engine.execute("ALTER TASK yearly RESUME");
        assertTrue(engine.getTaskScheduler().isScheduled(key), "a second RESUME arms it again");
    }
}
