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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How CREATE TASK and ALTER TASK weigh the properties they name. A statement is checked in two steps: the first
 * reads it alone — a property named twice, a name no task carries, a value of the wrong kind, a warehouse that
 * does not exist — and holds even under IF NOT EXISTS; the second weighs each value against its range or
 * vocabulary and the task against its graph, and a task being replaced stays as it was when the replacement is
 * refused. Every assertion holds on the embedded engine and on a real account alike.
 */
public class TaskPropertyChecksTest extends BaseDatabaseTest {

    private static final String INVALID_CONFIG = "Invalid config. Must be a string representation of a valid JSON "
        + "Object.";

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

    private String parameterValue(final String task, final String key) {
        final ResultSet parameters = engine.executeQuery("SHOW PARAMETERS LIKE '" + key + "' IN TASK " + task);
        return cell(parameters, soleRowWhere(parameters, "key", key), "value");
    }

    /** A task with a trivial body, scheduled, carrying the given properties. */
    private static String scheduled(final String name, final String properties) {
        return "CREATE TASK " + name + " SCHEDULE = '60 MINUTE' " + properties + " AS SELECT 1";
    }

    @Test
    public void aRefusedReplacementLeavesTheTaskAsItWas() {
        engine.execute("CREATE TASK r4 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK c1 AFTER r4 AS SELECT 1");
        assertEquals(INVALID_CONFIG,
            refusalOf("CREATE OR REPLACE TASK r4 SCHEDULE = '60 MINUTE' CONFIG = 'bad' AS SELECT 2"));
        assertEquals("Invalid schedule was specified. Please refer to the docs on what constitutes a valid schedule.",
            refusalOf("CREATE OR REPLACE TASK r4 SCHEDULE = 'bad' AS SELECT 2"));
        assertEquals("Invalid predecessor NO_SUCH_TASK was specified.",
            refusalOf("CREATE OR REPLACE TASK c1 AFTER no_such_task AS SELECT 2"));
        assertEquals("Task C1 cannot use itself as its predecessor.",
            refusalOf("CREATE OR REPLACE TASK c1 AFTER c1 AS SELECT 2"));
        assertEquals("SELECT 1", taskCell("R4", "definition"));
        assertEquals("SELECT 1", taskCell("C1", "definition"));
        assertTrue(taskCell("C1", "task_relations").contains("TEST_DB.TEST_SCHEMA.R4"));
        engine.execute("CREATE OR REPLACE TASK c1 AFTER r4 AS SELECT 3");
        assertEquals("SELECT 3", taskCell("C1", "definition"));
    }

    @Test
    public void ifNotExistsOverAnExistingTaskWeighsOnlyTheStatementItself() {
        engine.execute("CREATE TASK r1 SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK IF NOT EXISTS r1 SCHEDULE = '60 MINUTE' CONFIG = 'bad' AS SELECT 2");
        engine.execute("CREATE TASK IF NOT EXISTS r1 SCHEDULE = 'bad' AS SELECT 2");
        engine.execute("CREATE TASK IF NOT EXISTS r1 AFTER no_such_task AS SELECT 2");
        engine.execute("CREATE TASK IF NOT EXISTS r1 FINALIZE = no_such_task AS SELECT 2");
        engine.execute("CREATE TASK IF NOT EXISTS r1 SCHEDULE = '60 MINUTE' TIMEZONE = 'Not/AZone' AS SELECT 2");
        engine.execute("CREATE TASK IF NOT EXISTS r1 SCHEDULE = '60 MINUTE' OVERLAP_POLICY = BOGUS AS SELECT 2");
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'QUERY_TAG'",
            refusalOf("CREATE TASK IF NOT EXISTS r1 SCHEDULE = '60 MINUTE' QUERY_TAG = 5 AS SELECT 2"));
        assertEquals("SQL compilation error:\ninvalid property 'NOT_A_PARAM' for 'TASK'",
            refusalOf("CREATE TASK IF NOT EXISTS r1 SCHEDULE = '60 MINUTE' NOT_A_PARAM = 1 AS SELECT 2"));
        assertEquals("SQL compilation error:\nduplicate property 'QUERY_TAG';",
            refusalOf("CREATE TASK IF NOT EXISTS r1 SCHEDULE = '60 MINUTE' QUERY_TAG = 'a' QUERY_TAG = 'b' AS SELECT 2"));
        assertEquals("Nonexistent warehouse NO_SUCH_WH was specified.",
            refusalOf("CREATE TASK IF NOT EXISTS r1 WAREHOUSE = no_such_wh SCHEDULE = '60 MINUTE' AS SELECT 2"));
        assertEquals("SELECT 1", taskCell("R1", "definition"));
    }

    @Test
    public void parameterValuesAreWeighedAgainstTheirRangeAndVocabulary() {
        assertEquals("SQL compilation error:\ninvalid value [Not/AZone] for parameter 'TIMEZONE'",
            refusalOf(scheduled("p1", "TIMEZONE = 'Not/AZone'")));
        assertEquals("SQL compilation error:\ninvalid value [utc] for parameter 'TIMEZONE'",
            refusalOf(scheduled("p2", "TIMEZONE = 'utc'")));
        assertEquals("SQL compilation error:\nInvalid binary format string 'BOGUS': Must be 'HEX', 'BASE64', or "
            + "'UTF-8'", refusalOf(scheduled("p3", "BINARY_INPUT_FORMAT = 'BOGUS'")));
        assertEquals("Invalid value for trace level. Allowed values are ALWAYS,ON_EVENT,PROPAGATE,OFF.",
            refusalOf(scheduled("p4", "TRACE_LEVEL = 'BOGUS'")));
        assertEquals("SQL compilation error: \nparameter value out of range: -5. Must be between 0 and 604,800.",
            refusalOf(scheduled("p5", "STATEMENT_TIMEOUT_IN_SECONDS = -5")));
        assertEquals("SQL compilation error:\ninvalid value [9] for parameter 'WEEK_START'",
            refusalOf(scheduled("p6", "WEEK_START = 9")));
        assertEquals("SQL compilation error:\ninvalid value [3,601] for parameter "
            + "'CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY'",
            refusalOf(scheduled("p7", "CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY = 3601")));
        assertEquals("SQL compilation error:\ninvalid value [TRUE] for parameter 'QUERY_TAG'",
            refusalOf(scheduled("p8", "QUERY_TAG = TRUE")));
        assertEquals("SQL compilation error:\ninvalid value [5] for parameter 'LOG_LEVEL'",
            refusalOf(scheduled("p9", "LOG_LEVEL = 5")));
        assertEquals("SQL compilation error:\ninvalid value [EN] for parameter 'Snowflake only supports [ja, en, "
            + "fr-FR]'", refusalOf(scheduled("p10", "LANGUAGE = 'EN'")));
        assertEquals("Unsupported feature 'transaction isolation level SERIALIZABLE'.",
            refusalOf(scheduled("p11", "TRANSACTION_DEFAULT_ISOLATION_LEVEL = 'SERIALIZABLE'")));
        assertEquals("SQL compilation error:\ninvalid value [-5] for parameter 'USER_TASK_TIMEOUT_MS'",
            refusalOf(scheduled("p12", "USER_TASK_TIMEOUT_MS = -5")));
        assertEquals("SQL compilation error:\ninvalid value [1 DAY] for parameter 'TARGET_COMPLETION_INTERVAL'",
            refusalOf(scheduled("p13", "TARGET_COMPLETION_INTERVAL = '1 DAY'")));
        assertEquals("SQL compilation error:\ninvalid value [HUGE] for parameter 'USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE'",
            refusalOf(scheduled("p14", "USER_TASK_MANAGED_INITIAL_WAREHOUSE_SIZE = 'HUGE'")));
        engine.execute(scheduled("a1", "TIMEZONE = 'EST' UNSUPPORTED_DDL_ACTION = 'Fail' JSON_INDENT = 99"));
        assertEquals("EST", parameterValue("a1", "TIMEZONE"));
        assertEquals("Fail", parameterValue("a1", "UNSUPPORTED_DDL_ACTION"));
        engine.execute(scheduled("a2", "SERVERLESS_TASK_MIN_STATEMENT_SIZE = 'X3LARGE'"));
        assertEquals("X3LARGE", parameterValue("a2", "SERVERLESS_TASK_MIN_STATEMENT_SIZE"));
        assertEquals("SERVERLESS_TASK_MIN_STATEMENT_SIZE (X3LARGE) cannot be greater than "
            + "SERVERLESS_TASK_MAX_STATEMENT_SIZE (XXLARGE).",
            refusalOf("ALTER TASK a2 SET SERVERLESS_TASK_MAX_STATEMENT_SIZE = 'XXLARGE'"));
    }

    @Test
    public void aCompletionIntervalIsAScheduleIntervalFromTenSecondsToADay() {
        engine.execute(scheduled("i1", "TARGET_COMPLETION_INTERVAL = '600 second'"));
        assertEquals("600 second", taskCell("I1", "target_completion_interval"));
        engine.execute(scheduled("i2", "TARGET_COMPLETION_INTERVAL = '10 M'"));
        assertEquals("10 M", taskCell("I2", "target_completion_interval"));
        engine.execute(scheduled("i3", "TARGET_COMPLETION_INTERVAL = '1 H'"));
        engine.execute(scheduled("i4", "TARGET_COMPLETION_INTERVAL = '10 SECOND'"));
        engine.execute(scheduled("i5", "TARGET_COMPLETION_INTERVAL = '86400 SECOND'"));
        engine.execute(scheduled("i6", "TARGET_COMPLETION_INTERVAL = '+10 MINUTE'"));
        engine.execute(scheduled("i7", "TARGET_COMPLETION_INTERVAL = '10 MINUTE '"));
        // The count times the unit is taken in 64-bit milliseconds, as a schedule's is: this one wraps to 52 seconds.
        engine.execute(scheduled("i8", "TARGET_COMPLETION_INTERVAL = '153722867280912931 MINUTES'"));
        // A tab or a line break separates, and trails, as a space does.
        engine.execute(scheduled("i9", "TARGET_COMPLETION_INTERVAL = '10 MINUTE\t'"));
        assertEquals("10 MINUTE\t", taskCell("I9", "target_completion_interval"));
        engine.execute(scheduled("i10", "TARGET_COMPLETION_INTERVAL = '10\tMINUTE'"));
        engine.execute(scheduled("i11", "TARGET_COMPLETION_INTERVAL = '10\nMINUTE'"));
        final String[] refused = {"9 SECOND", "0 SECOND", "86401 SECOND", "1441 M", " 10 MINUTE", "\t10 MINUTE",
            "-10 MINUTE", "10 MIN", "1.5 HOURS", "153722867280913 MINUTES", "2562047788016 HOURS"};
        for (final String interval : refused) {
            assertEquals("SQL compilation error:\ninvalid value [" + interval
                + "] for parameter 'TARGET_COMPLETION_INTERVAL'",
                refusalOf(scheduled("j1", "TARGET_COMPLETION_INTERVAL = '" + interval + "'")));
        }
        engine.execute("ALTER TASK i4 SET TARGET_COMPLETION_INTERVAL = '30 SECOND'");
        assertEquals("30 SECOND", taskCell("I4", "target_completion_interval"));
        assertEquals("SQL compilation error:\ninvalid value [9 SECOND] for parameter 'TARGET_COMPLETION_INTERVAL'",
            refusalOf("ALTER TASK i4 SET TARGET_COMPLETION_INTERVAL = '9 SECOND'"));
        assertEquals("30 SECOND", taskCell("I4", "target_completion_interval"));
    }

    @Test
    public void aValueReadsBackTheWayTheAccountKeepsIt() {
        engine.execute(scheduled("v1", "LOG_LEVEL = info QUERY_TAG = abc TRACE_LEVEL = always"));
        assertEquals("info", parameterValue("v1", "LOG_LEVEL"));
        assertEquals("ABC", parameterValue("v1", "QUERY_TAG"));
        assertEquals("always", parameterValue("v1", "TRACE_LEVEL"));
        engine.execute(scheduled("v2", "QUERY_TAG = ('a', 'b')"));
        assertEquals("TOK_CONSTANT_LIST", parameterValue("v2", "QUERY_TAG"));
        assertEquals("Invalid value specified for property 'CONFIG'",
            refusalOf(scheduled("v3", "CONFIG = ('{\"a\": 1}')")));
        assertEquals("SQL compilation error:\ninvalid value [TOK_CONSTANT_LIST] for parameter 'JSON_INDENT'",
            refusalOf(scheduled("v4", "JSON_INDENT = (3)")));
        assertEquals(INVALID_CONFIG, refusalOf(scheduled("v5", "CONFIG = abc")));
        assertEquals("Invalid schedule was specified. Please refer to the docs on what constitutes a valid schedule.",
            refusalOf("CREATE TASK v6 SCHEDULE = abc AS SELECT 1"));
    }

    @Test
    public void aPropertyIsRepeatedOnlyWhenSpelledAlike() {
        assertEquals("SQL compilation error:\nduplicate property 'query_tag';",
            refusalOf(scheduled("d1", "query_tag = 'a' query_tag = 'b'")));
        engine.execute(scheduled("d2", "QUERY_TAG = 'a' query_tag = 'b'"));
        engine.execute(scheduled("r2", ""));
        assertEquals("SQL compilation error:\nduplicate property 'QUERY_TAG';",
            refusalOf("ALTER TASK r2 SET QUERY_TAG = 'a' QUERY_TAG = 'b'"));
        assertEquals("SQL compilation error:\nduplicate property 'SCHEDULE';",
            refusalOf("ALTER TASK r2 SET SCHEDULE = '5 MINUTE' SCHEDULE = '6 MINUTE'"));
        engine.execute("ALTER TASK r2 SET QUERY_TAG = 'a', query_tag = 'b'");
        assertEquals("SQL compilation error:\nduplicate property 'QUERY_TAG';",
            refusalOf("ALTER TASK r2 UNSET QUERY_TAG, QUERY_TAG"));
        engine.execute("ALTER TASK r2 UNSET CONFIG, config");
    }

    @Test
    public void aQuotedOverlapPolicyIsMatchedExactly() {
        assertEquals("SQL compilation error:\ninvalid value 'allow_all_overlap' for property 'OVERLAP_POLICY'",
            refusalOf(scheduled("o1", "OVERLAP_POLICY = 'allow_all_overlap'")));
        assertEquals("SQL compilation error:\ninvalid value 'allow_all_overlap' for property 'OVERLAP_POLICY'",
            refusalOf(scheduled("o2", "OVERLAP_POLICY = \"allow_all_overlap\"")));
        engine.execute(scheduled("o3", "OVERLAP_POLICY = Allow_Child_Overlap"));
        assertEquals("ALLOW_CHILD_OVERLAP", taskCell("O3", "overlap_policy"));
        assertEquals("true", taskCell("O3", "allow_overlapping_execution"));
    }

    @Test
    public void executeTaskAndTheConfigFunctionRefuseInTheAccountsOrder() {
        engine.execute(scheduled("r1", ""));
        engine.execute("CREATE TASK c1 AFTER r1 AS SELECT 1");
        assertEquals("Execute task cannot be called on non-root task TEST_DB.TEST_SCHEMA.C1. Call EXECUTE TASK on "
            + "the root task of its graph instead.", refusalOf("EXECUTE TASK c1 USING CONFIG = 'bad'"));
        assertEquals("SQL compilation error: error line 1 at position 7\ntoo many arguments for function "
            + "[SYSTEM$GET_TASK_GRAPH_CONFIG('a', 'b')] expected 1, got 2",
            refusalOf("SELECT SYSTEM$GET_TASK_GRAPH_CONFIG('a', 'b')"));
    }
}
