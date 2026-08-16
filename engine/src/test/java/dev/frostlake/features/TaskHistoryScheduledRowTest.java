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
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TASK_HISTORY reports how each run was triggered, and lists the NEXT fire of every started task as a
 * row of its own. A run an EXECUTE TASK statement began reads {@code EXECUTE TASK} — the runs of its
 * DAG children too — while the schedule's own runs and the pending fire read {@code SCHEDULE}. The
 * pending row is listed only while that fire is within eight days. Live-verified.
 */
public class TaskHistoryScheduledRowTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(TaskHistoryScheduledRowTest.class);

    /** How long a live account may take to publish a run's history row. */
    private static final long HISTORY_BUDGET_MILLIS = 90_000L;

    /** The history rows for one task: state and scheduled_from, newest scheduled time last. */
    private ResultSet historyOf(final String taskName) {
        return engine.executeQuery("SELECT state, scheduled_from"
            + " FROM TABLE(information_schema.task_history(task_name=>'" + taskName + "'))"
            + " WHERE database_name = CURRENT_DATABASE() ORDER BY scheduled_time");
    }

    /** A cron that fires at noon UTC on a day {@code days} from today. */
    private String cronDaysAhead(final int days) {
        final LocalDate when = LocalDate.now().plusDays(days);
        return "USING CRON 0 12 " + when.getDayOfMonth() + " " + when.getMonthValue() + " * UTC";
    }

    /** A started task's next fire is listed as a SCHEDULED row, triggered by its schedule. */
    @Test
    public void aStartedTaskListsItsNextFire() {
        engine.execute("CREATE OR REPLACE TASK pending_fire"
            + " USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'"
            + " SCHEDULE = 'USING CRON */7 * * * * UTC' AS SELECT 1");
        engine.execute("ALTER TASK pending_fire RESUME");
        try {
            final ResultSet rs = historyOf("PENDING_FIRE");
            assertEquals(1, rs.getRowCount(), "a started task lists exactly its next fire");
            assertEquals("SCHEDULED", rs.getRows().get(0).getValue(0).toString());
            assertEquals("SCHEDULE", rs.getRows().get(0).getValue(1).toString());
            logger.info("Pending fire row: {}", rs.getRows().get(0).getValues());
        } finally {
            engine.execute("ALTER TASK pending_fire SUSPEND");
        }
    }

    /** A suspended task has no pending fire to list. */
    @Test
    public void aSuspendedTaskListsNothing() {
        engine.execute("CREATE OR REPLACE TASK idle_fire"
            + " USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'"
            + " SCHEDULE = 'USING CRON */7 * * * * UTC' AS SELECT 1");
        assertEquals(0, historyOf("IDLE_FIRE").getRowCount());
    }

    /** A fire beyond the eight-day look-ahead is not listed, however far the schedule reaches. */
    @Test
    public void aFireBeyondTheLookAheadIsNotListed() {
        engine.execute("CREATE OR REPLACE TASK near_fire"
            + " USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'"
            + " SCHEDULE = '" + cronDaysAhead(3) + "' AS SELECT 1");
        engine.execute("CREATE OR REPLACE TASK distant_fire"
            + " USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'"
            + " SCHEDULE = '" + cronDaysAhead(14) + "' AS SELECT 1");
        engine.execute("ALTER TASK near_fire RESUME");
        engine.execute("ALTER TASK distant_fire RESUME");
        try {
            assertEquals(1, historyOf("NEAR_FIRE").getRowCount(), "a fire three days out is listed");
            assertEquals(0, historyOf("DISTANT_FIRE").getRowCount(),
                "a fire fourteen days out is beyond the look-ahead");
        } finally {
            engine.execute("ALTER TASK near_fire SUSPEND");
            engine.execute("ALTER TASK distant_fire SUSPEND");
        }
    }

    /** A run an EXECUTE TASK statement started reads EXECUTE TASK, not SCHEDULE. */
    @Test
    public void aManualRunNamesExecuteTask() {
        engine.execute("CREATE OR REPLACE TASK manual_run"
            + " USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'"
            + " SCHEDULE = '1440 MINUTE' AS SELECT 1");
        engine.execute("EXECUTE TASK manual_run");
        // A live account publishes the row with latency, so the read is repeated rather than assumed.
        final long deadline = System.currentTimeMillis() + HISTORY_BUDGET_MILLIS;
        ResultSet rs = historyOf("MANUAL_RUN");
        while (rs.getRowCount() == 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(3_000L);
            } catch (final InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            rs = historyOf("MANUAL_RUN");
        }
        assertTrue(rs.getRowCount() >= 1, "the manual run must reach the history");
        for (int i = 0; i < rs.getRowCount(); i++) {
            assertEquals("EXECUTE TASK", rs.getRows().get(i).getValue(1).toString(),
                "a manually started run is triggered by the statement, not the schedule");
        }
        logger.info("Manual run rows: {}", rs.getRowCount());
    }
}
