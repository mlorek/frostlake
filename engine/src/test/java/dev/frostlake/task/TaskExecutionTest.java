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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.metastore.model.Task;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Task execution semantics driven through {@code EXECUTE TASK} (synchronous — no timing-sensitive
 * scheduler assertions): the task's SQL runs, WHEN conditions gate execution, SHOW TASKS reflects
 * RESUME/SUSPEND state, and AFTER dependency chains cascade — a resumed child (and grandchild)
 * runs once its predecessor completes, while suspended children and children of a failed parent
 * do not run.
 */
public class TaskExecutionTest extends BaseDatabaseTest {

    private ResultSet run(final String sql) {
        return engine.executeQuery(sql);
    }

    private long logCount() {
        return ((Number) run("SELECT COUNT(*) FROM tlog").getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void executeTaskRunsItsSql() {
        engine.execute("CREATE TABLE tlog (v INTEGER)");
        engine.execute("CREATE TASK t1 SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES (1)");
        engine.execute("EXECUTE TASK t1");
        assertEquals(1L, logCount());
    }

    @Test
    public void showTasksReflectsResumeAndSuspend() {
        engine.execute("CREATE TABLE tlog (v INTEGER)");
        engine.execute("CREATE TASK t1 SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES (1)");
        assertEquals("SUSPENDED", taskState("t1"));
        engine.execute("ALTER TASK t1 RESUME");
        assertEquals("STARTED", taskState("t1"));
        engine.execute("ALTER TASK t1 SUSPEND");
        assertEquals("SUSPENDED", taskState("t1"));
    }

    private String taskState(final String taskName) {
        final ResultSet rs = run("SHOW TASKS");
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (taskName.equalsIgnoreCase(String.valueOf(rs.getRows().get(i).getValue(1)))) {
                return String.valueOf(rs.getRows().get(i).getValue(rs.getColumnIndex("state")));
            }
        }
        throw new AssertionError("Task not found in SHOW TASKS: " + taskName);
    }

    // WHEN gates execution: skipped while false, runs once true (stream-driven, the canonical use).
    @Test
    public void whenConditionGatesExecution() {
        engine.execute("CREATE TABLE tlog (v INTEGER)");
        engine.execute("CREATE TABLE src (id INTEGER)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute(
            "CREATE TASK t2 SCHEDULE = '1 MINUTE' WHEN SYSTEM$STREAM_HAS_DATA('st')"
            + " AS INSERT INTO tlog VALUES (1)");
        engine.execute("EXECUTE TASK t2");
        assertEquals(0L, logCount()); // no stream data: skipped
        engine.execute("INSERT INTO src VALUES (1)");
        engine.execute("EXECUTE TASK t2");
        assertEquals(1L, logCount()); // stream has data: ran
    }

    // ── AFTER dependency chains ────────────────────────────────────────────────────────────────────

    @Test
    public void afterChainRunsResumedChild() {
        engine.execute("CREATE TABLE tlog (v VARCHAR)");
        engine.execute("CREATE TASK parent SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES ('p')");
        engine.execute("CREATE TASK child AFTER parent AS INSERT INTO tlog VALUES ('c')");
        engine.execute("ALTER TASK child RESUME");
        engine.execute("EXECUTE TASK parent");
        final ResultSet rs = run("SELECT v FROM tlog ORDER BY v");
        assertEquals(2, rs.getRowCount());
        assertEquals("c", rs.getRows().get(0).getValue(0));
        assertEquals("p", rs.getRows().get(1).getValue(0));
    }

    @Test
    public void afterChainSkipsSuspendedChild() {
        engine.execute("CREATE TABLE tlog (v VARCHAR)");
        engine.execute("CREATE TASK parent SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES ('p')");
        engine.execute("CREATE TASK child AFTER parent AS INSERT INTO tlog VALUES ('c')");
        engine.execute("EXECUTE TASK parent");
        final ResultSet rs = run("SELECT v FROM tlog");
        assertEquals(1, rs.getRowCount());
        assertEquals("p", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void afterChainCascadesToGrandchild() {
        engine.execute("CREATE TABLE tlog (v VARCHAR)");
        engine.execute("CREATE TASK gen1 SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES ('a')");
        engine.execute("CREATE TASK gen2 AFTER gen1 AS INSERT INTO tlog VALUES ('b')");
        engine.execute("CREATE TASK gen3 AFTER gen2 AS INSERT INTO tlog VALUES ('c')");
        engine.execute("ALTER TASK gen2 RESUME");
        engine.execute("ALTER TASK gen3 RESUME");
        engine.execute("EXECUTE TASK gen1");
        assertEquals(3L, logCount());
    }

    // A WHEN condition that cannot be evaluated skips the run (it must not execute unconditionally).
    @Test
    public void whenEvaluationErrorSkipsRun() {
        engine.execute("CREATE TABLE tlog (v INTEGER)");
        engine.execute(
            "CREATE TASK t3 SCHEDULE = '1 MINUTE' WHEN 1 / 0 = 1 AS INSERT INTO tlog VALUES (1)");
        engine.execute("EXECUTE TASK t3");
        assertEquals(0L, logCount());
    }

    // SYSTEM$TASK_DEPENDENTS_ENABLE resumes dependent tasks, after which the DAG cascades.
    @Test
    public void taskDependentsEnableResumesChildren() {
        engine.execute("CREATE TABLE tlog (v VARCHAR)");
        engine.execute("CREATE TASK parent SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES ('p')");
        engine.execute("CREATE TASK child AFTER parent AS INSERT INTO tlog VALUES ('c')");
        run("SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('parent')");
        assertEquals("STARTED", taskState("child"));
        engine.execute("EXECUTE TASK parent");
        assertEquals(2L, logCount());
    }

    // A failed parent does not cascade to its children.
    @Test
    public void failedParentDoesNotCascade() {
        engine.execute("CREATE TABLE tlog (v VARCHAR)");
        engine.execute("CREATE TASK parent SCHEDULE = '1 MINUTE' AS INSERT INTO no_such_table VALUES ('p')");
        engine.execute("CREATE TASK child AFTER parent AS INSERT INTO tlog VALUES ('c')");
        engine.execute("ALTER TASK child RESUME");
        engine.execute("EXECUTE TASK parent");
        assertEquals(0L, logCount());
    }

    // ── scheduler fidelity (Phase 2) ───────────────────────────────────────────────────────────────

    private Task taskModel(final String name) {
        return engine.getCatalog().getDatabase("TEST_DB").getSchema("TEST_SCHEMA")
            .getTask(name.toUpperCase());
    }

    // A daily cron arms with a ~1440-minute interval (previously every cron meant 5 minutes).
    @Test
    public void cronDailyArmsWithDailyInterval() {
        engine.execute("CREATE TABLE tlog (v INTEGER)");
        engine.execute("CREATE TASK ct SCHEDULE = 'USING CRON 0 0 * * * UTC' AS INSERT INTO tlog VALUES (1)");
        engine.execute("ALTER TASK ct RESUME");
        final LocalDateTime next = taskModel("ct").getNextRunTime();
        final LocalDateTime now = LocalDateTime.now();
        assertTrue(next.isAfter(now.plusMinutes(1000)) && next.isBefore(now.plusMinutes(2000)),
            "daily cron should arm ~1440 minutes out, got " + next);
        engine.execute("ALTER TASK ct SUSPEND");
    }

    @Test
    public void suspendAfterConsecutiveFailures() {
        engine.execute(
            "CREATE TASK failing SCHEDULE = '1 MINUTE' SUSPEND_TASK_AFTER_NUM_FAILURES = 2"
            + " AS INSERT INTO no_such_table VALUES (1)");
        engine.execute("EXECUTE TASK failing");
        assertEquals("SUSPENDED", taskState("failing")); // still suspended (never resumed), 1 failure
        engine.execute("EXECUTE TASK failing");
        assertEquals(2, taskModel("failing").getFailureCount());
        assertEquals("SUSPENDED", taskState("failing"));
    }

    // A success resets the consecutive-failure streak.
    @Test
    public void successResetsFailureStreak() {
        engine.execute(
            "CREATE TASK flaky SCHEDULE = '1 MINUTE' SUSPEND_TASK_AFTER_NUM_FAILURES = 2"
            + " AS INSERT INTO maybe_t VALUES (1)");
        engine.execute("EXECUTE TASK flaky");            // fails: table missing
        assertEquals(1, taskModel("flaky").getFailureCount());
        engine.execute("CREATE TABLE maybe_t (v INTEGER)");
        engine.execute("EXECUTE TASK flaky");            // succeeds — streak resets
        assertEquals(0, taskModel("flaky").getFailureCount());
        engine.execute("DROP TABLE maybe_t");
        engine.execute("EXECUTE TASK flaky");            // fails again: streak restarts at 1
        assertEquals(1, taskModel("flaky").getFailureCount());
    }

    // TASK_AUTO_RETRY_ATTEMPTS: one EXECUTE of a failing task records 1 + N attempts.
    @Test
    public void autoRetryRecordsEachAttempt() {
        engine.execute(
            "CREATE TASK retrying SCHEDULE = '1 MINUTE' TASK_AUTO_RETRY_ATTEMPTS = 2"
            + " AS INSERT INTO no_such_table VALUES (1)");
        engine.execute("EXECUTE TASK retrying");
        assertEquals(3, taskModel("retrying").getExecutionHistory().size());
        assertEquals("FAILED", taskModel("retrying").getExecutionHistory().get(2).getState());
    }

    // A task is scheduled OR a DAG child — never both.
    @Test
    public void afterPlusScheduleRejected() {
        engine.execute("CREATE TABLE tlog (v VARCHAR)");
        engine.execute("CREATE TASK sched_parent SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES ('p')");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TASK bad_child SCHEDULE = '1 MINUTE' AFTER sched_parent"
                    + " AS INSERT INTO tlog VALUES ('c')");
            }
        });
        assertTrue(ex.getMessage().contains("Cannot specify both SCHEDULE and AFTER"),
            "unexpected message: " + ex.getMessage());
    }

    @Test
    public void dependentsEnableScopedToNamedRoot() {
        engine.execute("CREATE TABLE tlog (v VARCHAR)");
        engine.execute("CREATE TASK p1 SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES ('p1')");
        engine.execute("CREATE TASK c1 AFTER p1 AS INSERT INTO tlog VALUES ('c1')");
        engine.execute("CREATE TASK p2 SCHEDULE = '1 MINUTE' AS INSERT INTO tlog VALUES ('p2')");
        engine.execute("CREATE TASK c2 AFTER p2 AS INSERT INTO tlog VALUES ('c2')");
        run("SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('p1')");
        assertEquals("STARTED", taskState("c1"));
        assertEquals("SUSPENDED", taskState("c2")); // the OTHER root's child stays suspended
    }

    // SYSTEM$SET_RETURN_VALUE in the parent is readable by the child via GET_PREDECESSOR_RETURN_VALUE.
    @Test
    public void predecessorReturnValueFlowsToChild() {
        engine.execute("CREATE TABLE tlog (v VARCHAR)");
        engine.execute(
            "CREATE TASK parent SCHEDULE = '1 MINUTE' AS SELECT SYSTEM$SET_RETURN_VALUE('hello')");
        engine.execute(
            "CREATE TASK child AFTER parent"
            + " AS INSERT INTO tlog VALUES (SYSTEM$GET_PREDECESSOR_RETURN_VALUE())");
        engine.execute("ALTER TASK child RESUME");
        engine.execute("EXECUTE TASK parent");
        final ResultSet rs = run("SELECT v FROM tlog");
        assertEquals(1, rs.getRowCount());
        assertEquals("hello", rs.getRows().get(0).getValue(0));
    }
}
