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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The task scheduler refreshes a dynamic table once its target lag has passed — since its last refresh, or since it
 * was created for one that never refreshed — and leaves a suspended table and a DOWNSTREAM one alone. The look is
 * judged at a given instant, so the test needs no clock of its own; the account's scheduler is not driven from a
 * test, so this runs on the embedded engine only.
 */
public class DynamicTableScheduleTest extends BaseDatabaseTest {

    @BeforeEach
    public void aSourceTable() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the scheduler is the engine's own");
        engine.execute("CREATE OR REPLACE TABLE src_sched (a INT)");
        engine.execute("INSERT INTO src_sched VALUES (1), (2)");
    }

    private void create(final String name, final String lag, final String initialize) {
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE " + name + " TARGET_LAG = " + lag
            + " WAREHOUSE = COMPUTE_WH INITIALIZE = " + initialize + " AS SELECT a FROM src_sched");
    }

    private int refreshDueIn(final Duration later) {
        return engine.getTaskScheduler().refreshDueDynamicTables(Instant.now().plus(later));
    }

    private long rows(final String table) {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM " + table);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    private void requireUninitialized(final String table) {
        final String refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM " + table);
            }
        }).getMessage();
        assertTrue(refused.contains("is not initialized"), refused);
    }

    @Test
    public void aTableThatNeverRefreshedRefreshesOnceItsLagHasPassed() {
        create("dt_hourly", "'1 hour'", "ON_SCHEDULE");
        assertEquals(0, refreshDueIn(Duration.ofMinutes(30)));
        requireUninitialized("dt_hourly");
        assertEquals(1, refreshDueIn(Duration.ofMinutes(61)));
        assertEquals(2L, rows("dt_hourly"));
        // Refreshed now, the table is due again an hour later, not before.
        assertEquals(0, refreshDueIn(Duration.ofMinutes(30)));
        engine.execute("INSERT INTO src_sched VALUES (3)");
        assertEquals(1, refreshDueIn(Duration.ofMinutes(61)));
        final ResultSet shown = engine.executeQuery("SHOW DYNAMIC TABLES LIKE 'DT_HOURLY'");
        assertEquals("3", String.valueOf(shown.getRows().get(0).getValue(shown.getColumnIndex("rows"))));
    }

    @Test
    public void aSuspendedOrDownstreamTableWaits() {
        create("dt_down", "DOWNSTREAM", "ON_SCHEDULE");
        create("dt_paused", "'1 minute'", "ON_SCHEDULE");
        engine.execute("ALTER DYNAMIC TABLE dt_paused SUSPEND");
        assertEquals(0, refreshDueIn(Duration.ofDays(2)));
        requireUninitialized("dt_down");
        requireUninitialized("dt_paused");
        engine.execute("ALTER DYNAMIC TABLE dt_paused RESUME");
        assertEquals(1, refreshDueIn(Duration.ofMinutes(2)));
        assertEquals(2L, rows("dt_paused"));
    }
}
