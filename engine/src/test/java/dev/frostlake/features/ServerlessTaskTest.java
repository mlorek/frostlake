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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task is SERVERLESS exactly when it names no WAREHOUSE — Snowflake then manages the compute.
 * Three task options are meaningful only in that mode and are refused on a task that has a
 * warehouse, measured cell by cell on a real account with two distinct sentences; a fourth,
 * USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS, is NOT serverless-only and stays legal there.
 *
 * <p>The rule is one-directional, as live's is: it fires when a serverless-only option is set on a
 * warehouse task, but attaching a warehouse to a task that already carries one of those options is
 * accepted, and so is UNSET WAREHOUSE (which makes the task serverless).
 */
public class ServerlessTaskTest extends BaseDatabaseTest {

    private static final String TAIL = " SCHEDULE = '60 MINUTE' AS INSERT INTO srv_sink VALUES (1)";

    @BeforeEach
    public void fixtures() {
        engine.execute("CREATE TABLE srv_sink (v INTEGER)");
        engine.execute("CREATE WAREHOUSE srv_wh");
    }

    private void assertRejected(final String sql, final String expectedMessage) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains(expectedMessage),
            "expected \"" + expectedMessage + "\", got: " + error.getMessage());
    }

    @Test
    public void serverlessOnlyOptionsAreRefusedOnAWarehouseTask() {
        assertRejected("CREATE TASK m1 WAREHOUSE = srv_wh"
                + " USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'" + TAIL,
            "Cannot set USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE on a non-serverless task.");
        assertRejected("CREATE TASK m2 WAREHOUSE = srv_wh"
                + " SERVERLESS_TASK_MAX_STATEMENT_SIZE = 'MEDIUM'" + TAIL,
            "Cannot set SERVERLESS_TASK_MAX_STATEMENT_SIZE on a non-serverless task.");
    }

    /** TARGET_COMPLETION_INTERVAL has its own sentence, naming the task in full. */
    @Test
    public void targetCompletionIntervalNamesTheTaskInItsRejection() {
        assertRejected("CREATE TASK m3 WAREHOUSE = srv_wh"
                + " TARGET_COMPLETION_INTERVAL = '30 MINUTE'" + TAIL,
            "TARGET_COMPLETION_INTERVAL is only allowed for serverless Tasks."
                + " TEST_DB.TEST_SCHEMA.M3 is not a serverless task.");
    }

    /** The minimum-trigger interval is NOT serverless-only. */
    @Test
    public void minimumTriggerIntervalIsLegalOnAWarehouseTask() {
        engine.execute("CREATE TASK m4 WAREHOUSE = srv_wh"
            + " USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS = 15" + TAIL);
        assertEquals(1, engine.executeQuery("SHOW TASKS").getRowCount());
    }

    @Test
    public void alterSettingAServerlessOptionOnAWarehouseTaskIsRefused() {
        engine.execute("CREATE TASK wh_task WAREHOUSE = srv_wh" + TAIL);
        assertRejected("ALTER TASK wh_task SET USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'",
            "Cannot set USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE on a non-serverless task.");
        assertRejected("ALTER TASK wh_task SET SERVERLESS_TASK_MAX_STATEMENT_SIZE = 'MEDIUM'",
            "Cannot set SERVERLESS_TASK_MAX_STATEMENT_SIZE on a non-serverless task.");
    }

    /**
     * The reverse direction is allowed: a serverless task takes the options, then takes a
     * warehouse, and UNSET WAREHOUSE turns a warehouse task serverless — none of which live
     * refuses.
     */
    @Test
    public void theRuleDoesNotFireOnWarehouseAssignmentOrUnset() {
        engine.execute("CREATE TASK srv_task" + TAIL);
        engine.execute("ALTER TASK srv_task SET USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'");
        engine.execute("ALTER TASK srv_task SET WAREHOUSE = srv_wh");
        engine.execute("CREATE TASK wh_task2 WAREHOUSE = srv_wh" + TAIL);
        engine.execute("ALTER TASK wh_task2 UNSET WAREHOUSE");
    }

    /** A serverless task takes every option, in either spelling of the max-statement size. */
    @Test
    public void aServerlessTaskAcceptsEveryOptionAndRuns() {
        engine.execute("CREATE TASK srv_full"
            + " USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'"
            + " SERVERLESS_TASK_MAX_STATEMENT_SIZE = 'MEDIUM'"
            + " TARGET_COMPLETION_INTERVAL = '30 MINUTE'"
            + " USER_TASK_MINIMUM_TRIGGER_INTERVAL_IN_SECONDS = 15" + TAIL);
        engine.execute("CREATE TASK srv_bare SERVERLESS_TASK_MAX_STATEMENT_SIZE = MEDIUM" + TAIL);

        final ResultSet tasks = engine.executeQuery("SHOW TASKS");
        final Row full = showTaskRow("SRV_FULL");
        assertTrue(full != null, "SRV_FULL must be listed");
        // A serverless task has no warehouse, and SHOW TASKS leaves that cell NULL — one of the
        // few SHOW columns that does, since the column does not apply rather than being unset.
        assertNull(full.getValue(tasks.getColumnIndex("warehouse")),
            "a serverless task has no warehouse");
    }

    /**
     * A warehouse-less task still runs its body. Engine-only: this engine executes a task in
     * process, so the effect is visible as soon as EXECUTE TASK returns, whereas a real account
     * submits the run ASYNCHRONOUSLY (EXECUTE TASK hands back a query id and the body completes
     * later) — reading the row count straight afterwards would be a timing assertion there.
     */
    @Test
    public void aServerlessTaskRunsItsBody() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "EXECUTE TASK is asynchronous on a real account — the body completes after the "
            + "statement returns, so an immediate row count would be timing-dependent");
        engine.execute("CREATE TASK srv_run USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'" + TAIL);
        engine.execute("EXECUTE TASK srv_run");
        assertEquals(1L, ((Number) engine.executeQuery("SELECT count(*) FROM srv_sink")
            .getRows().get(0).getValue(0)).longValue());
    }

    /**
     * The stored serverless settings come back through SHOW PARAMETERS IN TASK. Neither SHOW TASKS
     * nor DESCRIBE TASK carries them — the parameter listing is where a task's settings are read.
     * A parameter the task set itself reports at the TASK level; one left alone reports a blank
     * level and its default value.
     */
    @Test
    public void theStoredServerlessSettingsComeBackThroughShowParameters() {
        engine.execute("CREATE TASK srv_full"
            + " USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'XSMALL'"
            + " SERVERLESS_TASK_MAX_STATEMENT_SIZE = 'MEDIUM'"
            + " TARGET_COMPLETION_INTERVAL = '30 MINUTE'" + TAIL);
        final ResultSet params = engine.executeQuery("SHOW PARAMETERS IN TASK srv_full");
        assertEquals("XSMALL", parameterValue(params, "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE"));
        assertEquals("TASK", parameterLevel(params, "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE"));
        assertEquals("MEDIUM", parameterValue(params, "SERVERLESS_TASK_MAX_STATEMENT_SIZE"));
        assertEquals("TASK", parameterLevel(params, "SERVERLESS_TASK_MAX_STATEMENT_SIZE"));
        // Untouched parameters sit at their defaults with no level.
        assertEquals("10", parameterValue(params, "SUSPEND_TASK_AFTER_NUM_FAILURES"));
        assertEquals("", parameterLevel(params, "SUSPEND_TASK_AFTER_NUM_FAILURES"));

        // TARGET_COMPLETION_INTERVAL is not a parameter — it is a SHOW TASKS column.
        final ResultSet tasks = engine.executeQuery("SHOW TASKS");
        assertEquals("30 MINUTE",
            showTaskRow("SRV_FULL").getValue(tasks.getColumnIndex("target_completion_interval")));
    }

    /** The {@code value} cell of the named parameter in a SHOW PARAMETERS result. */
    private String parameterValue(final ResultSet params, final String key) {
        return parameterCell(params, key, "value");
    }

    /** The {@code level} cell of the named parameter in a SHOW PARAMETERS result. */
    private String parameterLevel(final ResultSet params, final String key) {
        return parameterCell(params, key, "level");
    }

    private String parameterCell(final ResultSet params, final String key, final String column) {
        for (final Row row : params.getRows()) {
            if (key.equalsIgnoreCase(String.valueOf(row.getValue(params.getColumnIndex("key"))))) {
                return String.valueOf(row.getValue(params.getColumnIndex(column)));
            }
        }
        throw new IllegalArgumentException("No such parameter: " + key);
    }

    /** The SHOW TASKS row for {@code taskName}, or null when it is not listed. */
    private Row showTaskRow(final String taskName) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS");
        for (final Row row : tasks.getRows()) {
            if (taskName.equals(String.valueOf(row.getValue(tasks.getColumnIndex("name"))).toUpperCase())) {
                return row;
            }
        }
        return null;
    }

    /** EXECUTE TASK answers with one status row, as live does. */
    @Test
    public void executeTaskReturnsLiveStatusRow() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "the status row is the console surface — over JDBC a real account hands back no result "
            + "set at all for EXECUTE TASK, which the comparison harness reports as a row count");
        engine.execute("CREATE TASK status_task" + TAIL);
        final ResultSet rs = engine.executeQuery("EXECUTE TASK status_task");
        assertEquals(1, rs.getColumns().size());
        assertEquals("status", rs.getColumns().get(0).getName());
        assertEquals("Task STATUS_TASK is scheduled to run immediately.",
            rs.getRows().get(0).getValue(0));
    }
}
