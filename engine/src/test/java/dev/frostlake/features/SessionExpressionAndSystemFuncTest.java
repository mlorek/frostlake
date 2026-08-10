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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Statement-level expression evaluation (the SET / session-variable path): unary operators,
 * parentheses, {@code $var} references inside expressions, and the SYSTEM$ function family
 * (SYSTEM$STREAM_HAS_DATA, SYSTEM$USER_TASK_CANCEL, SYSTEM$TYPEOF).
 */
public class SessionExpressionAndSystemFuncTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void setEvaluatesUnaryParensAndSessionVarRefs() {
        engine.execute("SET n = -5");
        assertEquals(-5, ((Number) scalar("SELECT $n")).intValue());

        engine.execute("SET p = +7");
        assertEquals(7, ((Number) scalar("SELECT $p")).intValue());

        engine.execute("SET q = (3)");
        assertEquals(3, ((Number) scalar("SELECT $q")).intValue());

        // A $var reference inside a SET expression resolves through the same evaluator.
        engine.execute("SET r = $n");
        assertEquals(-5, ((Number) scalar("SELECT $r")).intValue());
    }

    @Test
    public void systemStreamHasDataReflectsPendingChanges() {
        engine.execute("CREATE TABLE shd_src (id INTEGER)");
        engine.execute("CREATE STREAM shd_stream ON TABLE shd_src");

        engine.execute("SET empty_state = SYSTEM$STREAM_HAS_DATA('SHD_STREAM')");
        assertEquals(false, scalar("SELECT $empty_state"));

        engine.execute("INSERT INTO shd_src VALUES (1)");
        engine.execute("SET full_state = SYSTEM$STREAM_HAS_DATA('SHD_STREAM')");
        assertEquals(true, scalar("SELECT $full_state"));
    }

    /**
     * The function cancels runs that are in flight; with none in flight it says so, and either way
     * it leaves the task itself alone — a started task is still started afterwards.
     */
    @Test
    public void systemUserTaskCancelReportsNoRunningExecutions() {
        engine.execute("CREATE TASK cancel_me SCHEDULE = '1 MINUTE' AS SELECT 1");
        engine.execute("ALTER TASK cancel_me RESUME");
        engine.execute("SET outcome = SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('CANCEL_ME')");
        final Object outcome = scalar("SELECT $outcome");
        assertNotNull(outcome);
        assertEquals("Task CANCEL_ME has no currently running executions. If the task was dropped"
            + " or replaced after a previous execution started, use SYSTEM$CANCEL_QUERY along with"
            + " the query id to cancel the run.", outcome.toString());

        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE 'CANCEL_ME'");
        assertEquals(1, tasks.getRows().size());
        assertEquals("started", tasks.getRows().get(0).getValue(tasks.getColumnIndex("state")));
    }

    /** The name is echoed into the message exactly as passed, while the lookup ignores case. */
    @Test
    public void systemUserTaskCancelEchoesTheNameVerbatim() {
        engine.execute("CREATE TASK echo_me SCHEDULE = '1 MINUTE' AS SELECT 1");
        engine.execute("SET outcome = SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('echo_me')");
        assertTrue(scalar("SELECT $outcome").toString()
            .startsWith("Task echo_me has no currently running executions."),
            "got: " + scalar("SELECT $outcome"));
    }

    /** An unknown task is an error, not a status row. */
    @Test
    public void systemUserTaskCancelRejectsAnUnknownTask() {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("SET outcome = SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('NO_SUCH_TASK')");
            }
        });
        assertTrue(e.getMessage().contains("Task NO_SUCH_TASK not found or not authorized."),
            "got: " + e.getMessage());
    }

    @Test
    public void systemTypeofReturnsATypeName() {
        engine.execute("SET t = SYSTEM$TYPEOF(123)");
        assertNotNull(scalar("SELECT $t"));
    }

    /** The cancel argument may be schema-qualified; the echo keeps the exact text passed. */
    @Test
    public void testUserTaskCancelAcceptsQualifiedName() {
        engine.execute("CREATE TASK cancel_q SCHEDULE = '1 MINUTE' AS SELECT 1");
        final Object outcome = scalar(
            "SELECT SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('TEST_SCHEMA.CANCEL_Q')");
        assertEquals("Task TEST_SCHEMA.CANCEL_Q has no currently running executions. If the task"
            + " was dropped or replaced after a previous execution started, use SYSTEM$CANCEL_QUERY"
            + " along with the query id to cancel the run.", outcome.toString());
    }
}
