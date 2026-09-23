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
import dev.frostlake.storage.ResultSetColumn;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task's interval schedule in seconds, minutes or hours, kept as written; the refusals of a schedule under ten
 * seconds, of one over 11,520 minutes and of a malformed one; and the task graph surfaces around them: RETRY LAST
 * with nothing to retry, TASK_DEPENDENTS of a name that is no task, the graph functions' RESULT_LIMIT and their
 * columns.
 */
public class TaskScheduleUnitsTest extends BaseDatabaseTest {

    private static final String INVALID =
        "Invalid schedule was specified. Please refer to the docs on what constitutes a valid schedule.";
    private static final String TOO_LONG = "Cannot set schedule greater than 11,520 minutes.";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String schedule(final String task) {
        final ResultSet rs = engine.executeQuery("SHOW TASKS LIKE '" + task + "' IN SCHEMA test_db.test_schema");
        return cell(rs, soleRowWhere(rs, "name", task), "schedule");
    }

    @Test
    public void anIntervalIsSecondsMinutesOrHoursKeptAsWritten() {
        final String[] schedules = {"90 SECONDS", "10 SECOND", "90 S", "11 minutes", "1 HOUR", "2 HOURS"};
        for (int i = 0; i < schedules.length; i++) {
            engine.executeQuery("CREATE TASK tsu_" + i + " WAREHOUSE = compute_wh SCHEDULE = '" + schedules[i]
                + "' AS SELECT 1");
            assertEquals(schedules[i], schedule("TSU_" + i));
        }
        engine.executeQuery("ALTER TASK tsu_0 SET SCHEDULE = '30 SECONDS'");
        assertEquals("30 SECONDS", schedule("TSU_0"));
    }

    @Test
    public void anIntervalUnderTenSecondsOrOfNothingIsRefused() {
        for (final String tooShort : new String[] {"9 SECONDS", "1 SECONDS", "5 seconds"}) {
            assertTrue(refusal("CREATE TASK tsu_short SCHEDULE = '" + tooShort + "' AS SELECT 1")
                .contains("Cannot set schedule less than 10 seconds."), tooShort);
        }
        for (final String invalid : new String[] {"0 SECONDS", "1 DAY"}) {
            assertTrue(refusal("CREATE TASK tsu_bad SCHEDULE = '" + invalid + "' AS SELECT 1")
                .contains("Invalid schedule was specified. Please refer to the docs on what constitutes a valid "
                    + "schedule."), invalid);
        }
    }

    @Test
    public void theShorthandUnitsAndASignedCountAreIntervalsToo() {
        final String[] schedules = {"1 H", "2 h", "5 M", "3 m", "+5 M", "1 Minute", "10 SECONDS  "};
        for (int i = 0; i < schedules.length; i++) {
            engine.executeQuery("CREATE TASK tsu_s" + i + " WAREHOUSE = compute_wh SCHEDULE = '" + schedules[i]
                + "' AS SELECT 1");
            assertEquals(schedules[i], schedule("TSU_S" + i));
        }
    }

    @Test
    public void aSpaceBeforeTheCountOrASecondOneBeforeTheUnitIsNoSchedule() {
        for (final String invalid : new String[] {" 10 SECONDS ", "10  SECONDS", " 10 MINUTES", "10  MINUTES", "5M",
            "1 MINS", "1 SEC", "1 HR", "-5 MINUTES", "+0 MINUTES", "0 H", "99999999999999999999 MINUTES"}) {
            assertTrue(refusal("CREATE TASK tsu_bad WAREHOUSE = compute_wh SCHEDULE = '" + invalid + "' AS SELECT 1")
                .contains(INVALID), invalid);
        }
        engine.executeQuery("CREATE TASK tsu_sp WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE' AS SELECT 1");
        assertTrue(refusal("ALTER TASK tsu_sp SET SCHEDULE = ' 5 MINUTES'").contains(INVALID));
        for (final String invalid : new String[] {" 10 SECONDS ", "10  MINUTES", "5M", "0 M"}) {
            assertTrue(refusal("CREATE ALERT tsu_bad SCHEDULE = '" + invalid + "' IF (EXISTS (SELECT 1)) THEN SELECT 1")
                .contains(INVALID), invalid);
        }
    }

    @Test
    public void aTabOrALineBreakSeparatesTheCountFromTheUnitAsASpaceDoes() {
        final String[] schedules = {"10 MINUTE\t", "10\tMINUTE", "10 MINUTE \t", "10\nMINUTE", "10 MINUTE\n",
            "10\rMINUTE"};
        for (int i = 0; i < schedules.length; i++) {
            engine.executeQuery("CREATE TASK tsu_w" + i + " WAREHOUSE = compute_wh SCHEDULE = '" + schedules[i]
                + "' AS SELECT 1");
            assertEquals(schedules[i], schedule("TSU_W" + i));
        }
        for (final String invalid : new String[] {"\t10 MINUTE", "10\t\tMINUTE", "10 \tMINUTE", "10 MINUTE"}) {
            assertTrue(refusal("CREATE TASK tsu_bad WAREHOUSE = compute_wh SCHEDULE = '" + invalid + "' AS SELECT 1")
                .contains(INVALID), invalid);
        }
    }

    @Test
    public void anIntervalLongerThanElevenThousandFiveHundredTwentyMinutesIsRefused() {
        final String[] longest = {"11520 MINUTES", "192 HOURS", "691200 SECONDS"};
        for (int i = 0; i < longest.length; i++) {
            engine.executeQuery("CREATE TASK tsu_l" + i + " WAREHOUSE = compute_wh SCHEDULE = '" + longest[i]
                + "' AS SELECT 1");
        }
        for (final String tooLong : new String[] {"11521 MINUTES", "11521 M", "193 H", "691201 SECONDS",
            "99999999999 MINUTES", "2562047788016 HOURS"}) {
            assertTrue(refusal("CREATE TASK tsu_long WAREHOUSE = compute_wh SCHEDULE = '" + tooLong + "' AS SELECT 1")
                .contains(TOO_LONG), tooLong);
        }
        assertTrue(refusal("ALTER TASK tsu_l0 SET SCHEDULE = '200 HOURS'").contains(TOO_LONG));
        assertTrue(refusal("CREATE ALERT tsu_long SCHEDULE = '100000 HOURS' IF (EXISTS (SELECT 1)) THEN SELECT 1")
            .contains(TOO_LONG));
        // The length is counted in 64-bit milliseconds: a count of minutes or seconds whose length passes that range
        // wraps around, onto a negative length or, here, onto 52 seconds.
        assertTrue(refusal("CREATE TASK tsu_wrap WAREHOUSE = compute_wh SCHEDULE = '153722867280913 MINUTES'"
            + " AS SELECT 1").contains(INVALID));
        assertTrue(refusal("CREATE TASK tsu_wrap WAREHOUSE = compute_wh SCHEDULE = '9223372036854776 SECONDS'"
            + " AS SELECT 1").contains(INVALID));
        engine.executeQuery("CREATE TASK tsu_wrap WAREHOUSE = compute_wh SCHEDULE = '153722867280912931 MINUTES'"
            + " AS SELECT 1");
        assertEquals("153722867280912931 MINUTES", schedule("TSU_WRAP"));
    }

    @Test
    public void anAlertTakesTheSameIntervals() {
        engine.executeQuery("CREATE ALERT tsu_a1 WAREHOUSE = compute_wh SCHEDULE = '90 SECONDS' IF (EXISTS (SELECT 1))"
            + " THEN SELECT 1");
        engine.executeQuery("CREATE ALERT tsu_a2 WAREHOUSE = compute_wh SCHEDULE = '2 HOURS' IF (EXISTS (SELECT 1))"
            + " THEN SELECT 1");
        engine.executeQuery("CREATE ALERT tsu_a3 SCHEDULE = '1 H' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.executeQuery("CREATE ALERT tsu_a4 SCHEDULE = '2 m' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        final ResultSet rs = engine.executeQuery("SHOW ALERTS IN SCHEMA test_db.test_schema");
        assertEquals("90 SECONDS", cell(rs, soleRowWhere(rs, "name", "TSU_A1"), "schedule"));
        assertEquals("2 HOURS", cell(rs, soleRowWhere(rs, "name", "TSU_A2"), "schedule"));
        assertEquals("1 H", cell(rs, soleRowWhere(rs, "name", "TSU_A3"), "schedule"));
        assertEquals("2 m", cell(rs, soleRowWhere(rs, "name", "TSU_A4"), "schedule"));
    }

    @Test
    public void retryLastWithNoRunToRetryIsRefused() {
        engine.executeQuery("CREATE TASK tsu_root WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.executeQuery("CREATE TASK tsu_child WAREHOUSE = compute_wh AFTER tsu_root AS SELECT 2");
        assertTrue(refusal("EXECUTE TASK tsu_root RETRY LAST")
            .contains("Cannot perform retry: no suitable run of graph with root task TSU_ROOT to retry."));
        assertTrue(refusal("EXECUTE TASK tsu_child RETRY LAST").contains("Execute task cannot be called on non-root "
            + "task TEST_DB.TEST_SCHEMA.TSU_CHILD. Call EXECUTE TASK on the root task of its graph instead."));
    }

    @Test
    public void retryLastAfterARunWithoutFailuresNamesTheRun() {
        // The run completes before the statement returns only on this engine.
        Assumptions.assumeFalse(isLiveSnowflake());
        engine.executeQuery("CREATE TASK tsu_ok AS SELECT 1");
        engine.executeQuery("EXECUTE TASK tsu_ok");
        final ResultSet runs = engine.executeQuery(
            "SELECT GRAPH_RUN_GROUP_ID FROM TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS("
                + "ROOT_TASK_NAME => 'TSU_OK'))");
        final Object group = runs.getRows().get(0).getValue(0);
        assertEquals("Cannot perform retry: run (GRAPH_RUN_GROUP_ID = " + group + ", attempt = 1) of graph with root "
            + "task TSU_OK had no failures.", refusal("EXECUTE TASK tsu_ok RETRY LAST"));
    }

    @Test
    public void taskDependentsOfANameThatIsNoTaskIsRefused() {
        assertTrue(refusal("SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'NOSUCH'))")
            .contains("Invalid value [NOSUCH] for function 'TASK_DEPENDENTS_SCAN', parameter 1: must be a valid "
                + "task name"));
    }

    @Test
    public void theGraphFunctionsTakeALimitUpToTenThousandAndListTheirColumns() {
        for (final String function : new String[] {"COMPLETE_TASK_GRAPHS", "CURRENT_TASK_GRAPHS"}) {
            assertTrue(refusal("SELECT * FROM TABLE(INFORMATION_SCHEMA." + function + "(RESULT_LIMIT => 10001))")
                .contains("Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000)."), function);
            engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA." + function + "(RESULT_LIMIT => 0))");
        }
        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS("
            + "RESULT_LIMIT => 1))");
        final List<String> columns = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            columns.add(column.getName());
        }
        assertEquals(List.of("ROOT_TASK_NAME", "DATABASE_NAME", "SCHEMA_NAME", "STATE", "FIRST_ERROR_TASK_NAME",
            "FIRST_ERROR_CODE", "FIRST_ERROR_MESSAGE", "SCHEDULED_TIME", "QUERY_START_TIME", "NEXT_SCHEDULED_TIME",
            "COMPLETED_TIME", "ROOT_TASK_ID", "GRAPH_VERSION", "RUN_ID", "ATTEMPT_NUMBER", "SCHEDULED_FROM", "CONFIG",
            "GRAPH_RUN_GROUP_ID", "BACKFILL_INFO", "SCHEDULED_BY_USER"), columns);
    }
}
