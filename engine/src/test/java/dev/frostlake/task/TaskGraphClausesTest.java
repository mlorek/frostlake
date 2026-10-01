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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CREATE TASK and ALTER TASK clauses a task graph is built with — CONFIG, OVERLAP_POLICY, the session
 * parameters a run sets, LOG_LEVEL, SERVERLESS_TASK_MIN_STATEMENT_SIZE, FINALIZE, ADD / REMOVE AFTER, REMOVE WHEN
 * and EXECUTE AS USER — read back through SHOW TASKS and SHOW PARAMETERS IN TASK, and refused in the account's
 * words. Every assertion holds on the embedded engine and on a real account alike.
 */
public class TaskGraphClausesTest extends BaseDatabaseTest {

    private static final String PREFIX = "TEST_DB.TEST_SCHEMA.";

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    private String taskCell(final String task, final String column) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + task + "'");
        return cell(tasks, soleRowWhere(tasks, "name", task), column);
    }

    private String parameterCell(final String task, final String key, final String column) {
        final ResultSet parameters = engine.executeQuery("SHOW PARAMETERS LIKE '" + key + "' IN TASK " + task);
        return cell(parameters, soleRowWhere(parameters, "key", key), column);
    }

    private List<String> dependents(final String root) {
        final ResultSet rows = engine.executeQuery("SELECT name FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS("
            + "TASK_NAME => '" + root + "', RECURSIVE => FALSE)) ORDER BY name");
        final List<String> names = new ArrayList<>();
        for (final Row row : rows.getRows()) {
            names.add(String.valueOf(row.getValue(0)));
        }
        return names;
    }

    @Test
    public void aConfigIsKeptAsWrittenAndMustBeAJsonObject() {
        engine.execute("CREATE TASK r1 SCHEDULE = '60 MINUTE' CONFIG = $${\"env\": \"prod\", \"n\": 1}$$ AS SELECT 1");
        assertEquals("{\"env\": \"prod\", \"n\": 1}", taskCell("R1", "config"));
        engine.execute("CREATE TASK r2 SCHEDULE = '60 MINUTE' CONFIG = '{\"a\": 2}' AS SELECT 1");
        assertEquals("{\"a\": 2}", taskCell("R2", "config"));
        final String invalid = "Invalid config. Must be a string representation of a valid JSON Object.";
        assertEquals(invalid, refusalOf("CREATE TASK r3 SCHEDULE = '60 MINUTE' CONFIG = 'not json' AS SELECT 1"));
        assertEquals(invalid, refusalOf("CREATE TASK r4 SCHEDULE = '60 MINUTE' CONFIG = '[1,2]' AS SELECT 1"));
        assertEquals(invalid, refusalOf("ALTER TASK r1 SET CONFIG = 'bad'"));
        engine.execute("ALTER TASK r1 SET CONFIG = $${\"k\": \"v\"}$$");
        assertEquals("{\"k\": \"v\"}", taskCell("R1", "config"));
        engine.execute("ALTER TASK r1 UNSET CONFIG");
        assertNull(taskCell("R1", "config"));
        assertEquals("Task " + PREFIX + "C1 cannot both be a non-root task and have a config.",
            refusalOf("CREATE TASK c1 CONFIG = '{\"a\":1}' AFTER r1 AS SELECT 1"));
    }

    @Test
    public void theOverlapPolicyAndTheLegacyOverlapSwitchAreOneSetting() {
        engine.execute("CREATE TASK r5 SCHEDULE = '60 MINUTE' OVERLAP_POLICY = ALLOW_ALL_OVERLAP AS SELECT 1");
        assertEquals("ALLOW_ALL_OVERLAP", taskCell("R5", "overlap_policy"));
        assertEquals("false", taskCell("R5", "allow_overlapping_execution"));
        engine.execute("CREATE TASK r6 SCHEDULE = '60 MINUTE' OVERLAP_POLICY = 'ALLOW_CHILD_OVERLAP' AS SELECT 1");
        assertEquals("ALLOW_CHILD_OVERLAP", taskCell("R6", "overlap_policy"));
        assertEquals("true", taskCell("R6", "allow_overlapping_execution"));
        assertEquals("SQL compilation error:\ninvalid value 'BOGUS' for property 'OVERLAP_POLICY'",
            refusalOf("CREATE TASK r8 SCHEDULE = '60 MINUTE' OVERLAP_POLICY = BOGUS AS SELECT 1"));
        assertEquals("Cannot set overlap policy on non-root task " + PREFIX + "C1.",
            refusalOf("CREATE TASK c1 OVERLAP_POLICY = NO_OVERLAP AFTER r5 AS SELECT 1"));
        engine.execute("ALTER TASK r5 UNSET OVERLAP_POLICY");
        assertEquals("NO_OVERLAP", taskCell("R5", "overlap_policy"));
        engine.execute("ALTER TASK r5 SET ALLOW_OVERLAPPING_EXECUTION = TRUE");
        assertEquals("ALLOW_CHILD_OVERLAP", taskCell("R5", "overlap_policy"));
        assertEquals("Cannot specify ALLOW_OVERLAPPING_EXECUTION and OVERLAP_POLICY to different values in the "
            + "same command.", refusalOf("ALTER TASK r5 SET OVERLAP_POLICY = NO_OVERLAP ALLOW_OVERLAPPING_EXECUTION = TRUE"));
        engine.execute("ALTER TASK r5 UNSET ALLOW_OVERLAPPING_EXECUTION");
        assertEquals("NO_OVERLAP", taskCell("R5", "overlap_policy"));
    }

    @Test
    public void sessionParametersAreReportedAtTheTaskLevel() {
        engine.execute("CREATE TASK r9 SCHEDULE = '60 MINUTE' TIMESTAMP_INPUT_FORMAT = 'YYYY-MM-DD HH24', "
            + "QUERY_TAG = 'qt' STATEMENT_TIMEOUT_IN_SECONDS = 10 LOG_LEVEL = 'info' AS SELECT 1");
        assertEquals("qt", parameterCell("r9", "QUERY_TAG", "value"));
        assertEquals("TASK", parameterCell("r9", "QUERY_TAG", "level"));
        assertEquals("YYYY-MM-DD HH24", parameterCell("r9", "TIMESTAMP_INPUT_FORMAT", "value"));
        assertEquals("10", parameterCell("r9", "STATEMENT_TIMEOUT_IN_SECONDS", "value"));
        assertEquals("172800", parameterCell("r9", "STATEMENT_TIMEOUT_IN_SECONDS", "default"));
        assertEquals("info", parameterCell("r9", "LOG_LEVEL", "value"));
        assertEquals("OFF", parameterCell("r9", "LOG_LEVEL", "default"));
        assertEquals("", parameterCell("r9", "TIME_INPUT_FORMAT", "level"), "a parameter the task left alone");
        engine.execute("ALTER TASK r9 SET QUERY_TAG = 'q1', TRACE_LEVEL = 'ALWAYS'");
        assertEquals("q1", parameterCell("r9", "QUERY_TAG", "value"));
        assertEquals("ALWAYS", parameterCell("r9", "TRACE_LEVEL", "value"));
        engine.execute("ALTER TASK r9 UNSET QUERY_TAG, TIMESTAMP_INPUT_FORMAT, LOG_LEVEL");
        assertEquals("", parameterCell("r9", "QUERY_TAG", "level"));
        assertEquals("OFF", parameterCell("r9", "LOG_LEVEL", "value"));
    }

    @Test
    public void aParameterNoTaskCarriesOrAValueOfTheWrongKindIsRefused() {
        engine.execute("CREATE TASK r1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertEquals("SQL compilation error:\ninvalid property 'NOT_A_PARAM' for 'TASK'",
            refusalOf("CREATE TASK r10 SCHEDULE = '60 MINUTE' NOT_A_PARAM = 1 AS SELECT 1"));
        assertEquals("SQL compilation error:\ninvalid property 'NOT_A_PARAM' for 'TASK'",
            refusalOf("ALTER TASK r1 UNSET NOT_A_PARAM"));
        assertEquals("SQL compilation error:\ninvalid value ['abc'] for parameter 'STATEMENT_TIMEOUT_IN_SECONDS'",
            refusalOf("CREATE TASK a6 SCHEDULE = '5 MINUTE' STATEMENT_TIMEOUT_IN_SECONDS = 'abc' AS SELECT 1"));
        assertEquals("SQL compilation error:\ninvalid value ['5000'] for parameter 'USER_TASK_TIMEOUT_MS'",
            refusalOf("CREATE TASK a2 SCHEDULE = '5 MINUTE' USER_TASK_TIMEOUT_MS = '5000' AS SELECT 1"));
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'COMMENT'",
            refusalOf("CREATE TASK a4 SCHEDULE = '5 MINUTE' COMMENT = 5 AS SELECT 1"));
        assertEquals("The parameter AUTOCOMMIT = false is not supported with Tasks. Remove the parameter and try "
            + "again.", refusalOf("CREATE TASK a7 SCHEDULE = '5 MINUTE' AUTOCOMMIT = FALSE AS SELECT 1"));
        assertEquals("The parameter SEARCH_PATH is not supported with Tasks. Remove the parameter and try again.",
            refusalOf("CREATE TASK a8 SCHEDULE = '5 MINUTE' SEARCH_PATH = '$current' AS SELECT 1"));
        assertEquals("SQL compilation error:\nduplicate property 'QUERY_TAG';",
            refusalOf("CREATE TASK a9 SCHEDULE = '5 MINUTE' QUERY_TAG = 'q' QUERY_TAG = 'r' AS SELECT 1"));
        assertEquals("Invalid value for log level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF.",
            refusalOf("CREATE TASK a12 SCHEDULE = '5 MINUTE' LOG_LEVEL = 'BOGUS' AS SELECT 1"));
    }

    @Test
    public void aServerlessStatementSizeFloorFitsUnderItsCeilingAndItsInitialSize() {
        engine.execute("CREATE TASK r11 SCHEDULE = '60 MINUTE' SERVERLESS_TASK_MIN_STATEMENT_SIZE = 'SMALL' AS SELECT 1");
        assertEquals("SMALL", parameterCell("r11", "SERVERLESS_TASK_MIN_STATEMENT_SIZE", "value"));
        assertEquals("XSMALL", parameterCell("r11", "SERVERLESS_TASK_MIN_STATEMENT_SIZE", "default"));
        assertEquals("TASK", parameterCell("r11", "SERVERLESS_TASK_MIN_STATEMENT_SIZE", "level"));
        engine.execute("ALTER TASK r11 UNSET SERVERLESS_TASK_MIN_STATEMENT_SIZE");
        assertEquals("XSMALL", parameterCell("r11", "SERVERLESS_TASK_MIN_STATEMENT_SIZE", "value"));
        assertEquals("SQL compilation error:\ninvalid value [HUGE] for parameter 'SERVERLESS_TASK_MIN_STATEMENT_SIZE'",
            refusalOf("ALTER TASK r11 SET SERVERLESS_TASK_MIN_STATEMENT_SIZE = 'HUGE'"));
        assertEquals("SERVERLESS_TASK_MIN_STATEMENT_SIZE (LARGE) cannot be greater than "
            + "SERVERLESS_TASK_MAX_STATEMENT_SIZE (SMALL).", refusalOf("CREATE TASK a15 SCHEDULE = '5 MINUTE' "
            + "SERVERLESS_TASK_MIN_STATEMENT_SIZE = 'LARGE' SERVERLESS_TASK_MAX_STATEMENT_SIZE = 'SMALL' AS SELECT 1"));
        assertEquals("USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE (SMALL) cannot be less than "
            + "SERVERLESS_TASK_MIN_STATEMENT_SIZE (LARGE).", refusalOf("CREATE TASK a16 SCHEDULE = '5 MINUTE' "
            + "SERVERLESS_TASK_MIN_STATEMENT_SIZE = 'LARGE' USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'SMALL' "
            + "AS SELECT 1"));
        assertEquals("Cannot set SERVERLESS_TASK_MIN_STATEMENT_SIZE on a non-serverless task.",
            refusalOf("CREATE TASK a17 SCHEDULE = '5 MINUTE' WAREHOUSE = COMPUTE_WH "
                + "SERVERLESS_TASK_MIN_STATEMENT_SIZE = 'SMALL' AS SELECT 1"));
    }

    @Test
    public void aFinalizerTaskIsAttachedToOneRootAndEndsItsGraph() {
        engine.execute("CREATE TASK r1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK r2 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK f1 FINALIZE = r1 AS SELECT 2");
        assertEquals("{\"FinalizedRootTask\":\"" + PREFIX + "R1\",\"Predecessors\":[]}", taskCell("F1", "task_relations"));
        assertEquals("{\"Predecessors\":[],\"FinalizerTask\":\"" + PREFIX + "F1\"}", taskCell("R1", "task_relations"));
        assertNull(taskCell("F1", "schedule"));
        assertEquals("null", taskCell("F1", "allow_overlapping_execution"));
        assertNull(taskCell("F1", "overlap_policy"));
        assertTrue(dependents("R1").contains("F1"), "the finalizer is a dependent of its root");

        assertEquals("Finalize target R1 already have finalizer.",
            refusalOf("CREATE TASK f2 FINALIZE = r1 AS SELECT 3"));
        assertEquals("Task F3 cannot have a schedule and be a finalizer task.",
            refusalOf("CREATE TASK f3 FINALIZE = r2 SCHEDULE = '5 MINUTE' AS SELECT 3"));
        assertEquals("Task " + PREFIX + "F5 cannot have predecessors as a finalizer task.",
            refusalOf("CREATE TASK f5 FINALIZE = r2 AFTER r1 AS SELECT 3"));
        assertEquals("Task " + PREFIX + "F1 cannot have children as a finalizer task.",
            refusalOf("CREATE TASK c2 AFTER f1 AS SELECT 1"));
        assertEquals("Invalid finalized root task NO_SUCH_TASK was specified.",
            refusalOf("CREATE TASK f6 FINALIZE = no_such_task AS SELECT 3"));
        assertEquals("Execute task cannot be called on non-root task " + PREFIX + "F1. Call EXECUTE TASK on the "
            + "root task of its graph instead.", refusalOf("EXECUTE TASK f1"));
        assertEquals("Task " + PREFIX + "F1 cannot both be a non-root task and have a config.",
            refusalOf("ALTER TASK f1 SET CONFIG = '{\"a\": 1}'"));
        engine.execute("CREATE TASK r3 SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertEquals("Task R2 cannot have a schedule and be a finalizer task.",
            refusalOf("ALTER TASK r2 SET FINALIZE = r3"));

        engine.execute("ALTER TASK f1 UNSET FINALIZE");
        assertEquals("{\"Predecessors\":[]}", taskCell("F1", "task_relations"));
        assertEquals("{\"Predecessors\":[]}", taskCell("R1", "task_relations"));
        engine.execute("ALTER TASK f1 SET FINALIZE = 'R1'");
        assertEquals("{\"FinalizedRootTask\":\"" + PREFIX + "R1\",\"Predecessors\":[]}", taskCell("F1", "task_relations"));
        engine.execute("DROP TASK r1");
        assertEquals("{\"Predecessors\":[]}", taskCell("F1", "task_relations"), "a dropped root frees its finalizer");
        assertEquals("NO_OVERLAP", taskCell("F1", "overlap_policy"));
    }

    @Test
    public void predecessorsAreAddedAndRemovedOneListAtATime() {
        engine.execute("CREATE TASK r1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK r2 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK c1 AFTER r1 AS SELECT 1");
        engine.execute("ALTER TASK c1 ADD AFTER r2");
        engine.execute("ALTER TASK c1 ADD AFTER r2");
        assertEquals("{\"Predecessors\":[\"" + PREFIX + "R1\",\"" + PREFIX + "R2\"]}", taskCell("C1", "task_relations"));
        engine.execute("ALTER TASK c1 REMOVE AFTER r1");
        assertEquals("{\"Predecessors\":[\"" + PREFIX + "R2\"]}", taskCell("C1", "task_relations"));
        assertEquals("Task C1 does not have predecessor R1.", refusalOf("ALTER TASK c1 REMOVE AFTER r1"));
        assertEquals("Invalid predecessor NO_SUCH_TASK was specified.", refusalOf("ALTER TASK c1 ADD AFTER no_such_task"));
        assertEquals("Invalid predecessor NO_SUCH_TASK was specified.",
            refusalOf("CREATE TASK c9 AFTER no_such_task AS SELECT 1"));
        assertEquals("Task R1 cannot have both a schedule and a predecessor.", refusalOf("ALTER TASK r1 ADD AFTER r2"));
        assertEquals("Task C1 cannot use itself as its predecessor.", refusalOf("ALTER TASK c1 ADD AFTER c1"));
        engine.execute("ALTER TASK c1 REMOVE AFTER r2");
        assertEquals("{\"Predecessors\":[]}", taskCell("C1", "task_relations"));
        assertEquals("false", taskCell("C1", "allow_overlapping_execution"), "without predecessors it is a root");
        assertEquals("NO_OVERLAP", taskCell("C1", "overlap_policy"));
        engine.execute("ALTER TASK c1 ADD AFTER TEST_SCHEMA.r1, r2");
        assertEquals("{\"Predecessors\":[\"" + PREFIX + "R1\",\"" + PREFIX + "R2\"]}", taskCell("C1", "task_relations"));
    }

    @Test
    public void removeWhenDropsTheCondition() {
        engine.execute("CREATE TASK w1 SCHEDULE = '60 MINUTE' WHEN SYSTEM$STREAM_HAS_DATA('x') AS SELECT 1");
        engine.execute("ALTER TASK w1 REMOVE WHEN");
        assertNull(taskCell("W1", "condition"));
        engine.execute("ALTER TASK w1 REMOVE WHEN");
        engine.execute("ALTER TASK w1 MODIFY WHEN SYSTEM$STREAM_HAS_DATA('nope')");
        assertEquals("SYSTEM$STREAM_HAS_DATA('nope')", taskCell("W1", "condition"));
    }

    @Test
    public void aTaskRunsAsTheUserItNames() {
        final String user = String.valueOf(engine.executeQuery("SELECT CURRENT_USER()").getRows().get(0).getValue(0));
        engine.execute("CREATE TASK u1 SCHEDULE = '60 MINUTE' EXECUTE AS USER \"" + user + "\" AS SELECT 1");
        assertEquals(user, taskCell("U1", "execute_as_user"));
        engine.execute("ALTER TASK u1 UNSET EXECUTE AS USER");
        assertNull(taskCell("U1", "execute_as_user"));
        engine.execute("ALTER TASK u1 SET EXECUTE AS USER \"" + user + "\"");
        assertEquals(user, taskCell("U1", "execute_as_user"));
        assertTrue(refusalOf("CREATE TASK u9 SCHEDULE = '60 MINUTE' EXECUTE AS USER no_such_user AS SELECT 1")
            .startsWith("SQL compilation error:\nUser 'NO_SUCH_USER' does not exist or not authorized."));
    }
}
