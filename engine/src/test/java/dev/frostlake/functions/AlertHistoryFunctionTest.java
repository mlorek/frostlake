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
package dev.frostlake.functions;

import dev.frostlake.AlertEvaluationWait;
import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INFORMATION_SCHEMA.ALERT_HISTORY: one row per evaluation of an alert, newest scheduled time first. The alerts carry
 * a schedule, since one without is an alert on new data, which EXECUTE ALERT does not run; an account evaluates an
 * executed alert a moment later and keeps earlier runs' history, so the rows read are the ones since a mark (see
 * {@link AlertEvaluationWait}).
 */
public class AlertHistoryFunctionTest extends BaseDatabaseTest {

    @AfterEach
    public void stopScheduler() {
        engine.stopTaskScheduler();
    }

    private List<String> states(final ResultSet rs) {
        final List<String> states = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            states.add(cell(rs, row, "STATE"));
        }
        return states;
    }

    @Test
    public void everyOutcomeIsRecordedNewestFirst() {
        engine.executeQuery("CREATE TABLE h (v INT)");
        engine.executeQuery("CREATE ALERT h1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT * FROM h)) THEN SELECT 1");
        AlertEvaluationWait.mark(engine);
        engine.executeQuery("EXECUTE ALERT h1");
        AlertEvaluationWait.until(engine, "H1", 1);
        engine.executeQuery("INSERT INTO h VALUES (1)");
        engine.executeQuery("EXECUTE ALERT h1");
        AlertEvaluationWait.until(engine, "H1", 2);
        engine.executeQuery("ALTER ALERT h1 MODIFY ACTION INSERT INTO no_such_table VALUES (1)");
        engine.executeQuery("EXECUTE ALERT h1");
        AlertEvaluationWait.until(engine, "H1", 3);
        engine.executeQuery("ALTER ALERT h1 MODIFY CONDITION EXISTS (SELECT * FROM no_such_table)");
        engine.executeQuery("EXECUTE ALERT h1");
        AlertEvaluationWait.until(engine, "H1", 4);
        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY("
            + "SCHEDULED_TIME_RANGE_START => $alert_since, ALERT_NAME => 'H1'))");
        assertEquals(Arrays.asList("CONDITION_FAILED", "ACTION_FAILED", "TRIGGERED", "CONDITION_FALSE"), states(rs));
        final Row failed = rs.getRows().get(0);
        assertEquals("H1", cell(rs, failed, "NAME"));
        assertEquals("TEST_DB", cell(rs, failed, "DATABASE_NAME"));
        assertEquals("TEST_SCHEMA", cell(rs, failed, "SCHEMA_NAME"));
        assertEquals("EXECUTE ALERT", cell(rs, failed, "SCHEDULED_FROM"));
        assertTrue(cell(rs, failed, "SQL_ERROR_MESSAGE").contains("NO_SUCH_TABLE"), cell(rs, failed,
            "SQL_ERROR_MESSAGE"));
        assertNotNull(failed.getValue(rs.getColumnIndex("COMPLETED_TIME")),
            "a finished evaluation has a completed time");
        assertNull(cell(rs, rs.getRows().get(2), "SQL_ERROR_MESSAGE"));
    }

    @Test
    public void theColumnsAreTheDocumentedOnes() {
        final ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY())");
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName());
        }
        assertEquals(Arrays.asList("NAME", "DATABASE_NAME", "SCHEMA_NAME", "CONDITION", "CONDITION_QUERY_ID",
            "ACTION", "ACTION_QUERY_ID", "STATE", "SQL_ERROR_CODE", "SQL_ERROR_MESSAGE", "SCHEDULED_TIME",
            "COMPLETED_TIME", "SCHEDULED_FROM", "RUNBOOK", "WAS_AUTO_SUSPENDED", "CONFIG"), names);
    }

    @Test
    public void theNameAndLimitArgumentsNarrowTheRows() {
        engine.executeQuery("CREATE ALERT n1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.executeQuery("CREATE ALERT n2 SCHEDULE = '60 MINUTE' RUNBOOK = 'see wiki' IF (EXISTS (SELECT 1)) "
            + "THEN SELECT 1");
        AlertEvaluationWait.mark(engine);
        engine.executeQuery("EXECUTE ALERT n1");
        AlertEvaluationWait.until(engine, "N1", 1);
        engine.executeQuery("EXECUTE ALERT n2");
        AlertEvaluationWait.until(engine, "N2", 1);
        engine.executeQuery("EXECUTE ALERT n2");
        AlertEvaluationWait.until(engine, "N2", 2);
        final ResultSet named = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY("
            + "SCHEDULED_TIME_RANGE_START => $alert_since, ALERT_NAME => 'n2'))");
        assertEquals(2, named.getRows().size());
        assertEquals("see wiki", cell(named, named.getRows().get(0), "RUNBOOK"));
        assertEquals(1, engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY(RESULT_LIMIT => 1))").getRows().size());
        // The account's other alerts may run meanwhile, so only these two are counted.
        assertEquals(3, engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY(SCHEDULED_TIME_RANGE_START => $alert_since)) "
                + "WHERE name IN ('N1', 'N2')").getRows().size());
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY(RESULT_LIMIT => 0))").getRows().size());
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY(RESULT_LIMIT => 10001))");
            }
        });
        assertTrue(refused.getMessage().contains(
            "Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000)."), refused.getMessage());
    }

    @Test
    public void aStartedAlertListsItsNextScheduledEvaluation() {
        engine.executeQuery("CREATE ALERT s1 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.executeQuery("ALTER ALERT s1 RESUME");
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY(ALERT_NAME => 'S1'))");
        assertEquals(Arrays.asList("SCHEDULED"), states(rs));
        assertEquals("SCHEDULE", cell(rs, rs.getRows().get(0), "SCHEDULED_FROM"));
        engine.executeQuery("ALTER ALERT s1 SUSPEND");
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY(ALERT_NAME => 'S1'))").getRows().size());
    }

    @Test
    public void theFunctionLivesOnlyInInformationSchema() {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(ALERT_HISTORY())");
            }
        });
        assertTrue(refused.getMessage().contains("ALERT_HISTORY"), refused.getMessage());
    }
}
