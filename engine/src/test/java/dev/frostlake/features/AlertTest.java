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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.task.TaskScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ALERT object: CREATE [OR REPLACE | OR ALTER] ALERT … IF (EXISTS (…)) THEN …, CREATE ALERT … CLONE,
 * ALTER ALERT RESUME | SUSPEND | SET | UNSET | MODIFY CONDITION | MODIFY ACTION, DROP ALERT, SHOW ALERTS,
 * DESCRIBE ALERT and EXECUTE ALERT.
 */
public class AlertTest extends BaseDatabaseTest {

    @AfterEach
    public void stopScheduler() {
        engine.stopTaskScheduler();
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return refused.getMessage();
    }

    private Row alertRow(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW ALERTS IN SCHEMA test_db.test_schema");
        return soleRowWhere(rs, "name", name);
    }

    private String show(final String name, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW ALERTS IN SCHEMA test_db.test_schema");
        return cell(rs, soleRowWhere(rs, "name", name), column);
    }

    private static List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (final ResultSetColumn column : rs.getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    @Test
    public void aNewAlertIsSuspendedAndShowsEveryProperty() {
        assertEquals("Alert A1 successfully created.", status("CREATE ALERT a1 WAREHOUSE = compute_wh"
            + " SCHEDULE = '5 MINUTE'"
            + " COMMENT = 'watch' CONFIG = '{\"k\":1}' RUNBOOK = 'rb' SUSPEND_ALERT_AFTER_NUM_FAILURES = 3"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 2"));
        final ResultSet rs = engine.executeQuery("SHOW ALERTS");
        assertEquals(Arrays.asList("created_on", "name", "database_name", "schema_name", "owner", "comment",
            "warehouse", "schedule", "state", "condition", "action", "owner_role_type", "runbook", "config",
            "was_auto_suspended"), columnNames(rs));
        assertNull(alertRow("A1").getValue(14));
        assertEquals("suspended", show("A1", "state"));
        assertEquals("COMPUTE_WH", show("A1", "warehouse"));
        assertEquals("5 MINUTE", show("A1", "schedule"));
        assertEquals("watch", show("A1", "comment"));
        assertEquals("{\"k\":1}", show("A1", "config"));
        assertEquals("rb", show("A1", "runbook"));
        assertEquals("SELECT 1", show("A1", "condition"));
        assertEquals("SELECT 2", show("A1", "action"));
        assertEquals("TEST_DB", show("A1", "database_name"));
        assertEquals("TEST_SCHEMA", show("A1", "schema_name"));
        final ResultSet described = engine.executeQuery("DESCRIBE ALERT a1");
        assertEquals(columnNames(rs), columnNames(described));
        assertEquals(1, described.getRows().size());
    }

    @Test
    public void createModesFollowTheObjectFamilies() {
        engine.executeQuery("CREATE ALERT a2 IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertEquals("A2 already exists, statement succeeded.",
            status("CREATE ALERT IF NOT EXISTS a2 IF (EXISTS (SELECT 2)) THEN SELECT 2"));
        assertTrue(refusal("CREATE ALERT a2 IF (EXISTS (SELECT 2)) THEN SELECT 2").contains("Object 'A2' already exists."));
        assertTrue(refusal("CREATE OR REPLACE ALERT IF NOT EXISTS a2 IF (EXISTS (SELECT 2)) THEN SELECT 2")
            .contains("incompatible"));
        engine.executeQuery("CREATE OR REPLACE ALERT a2 IF (EXISTS (SELECT 3)) THEN SELECT 3");
        assertEquals("SELECT 3", show("A2", "condition"));
        engine.executeQuery("ALTER ALERT a2 RESUME");
        engine.executeQuery("CREATE OR ALTER ALERT a2 COMMENT = 'altered' IF (EXISTS (SELECT 4)) THEN SELECT 5");
        assertEquals("SELECT 4", show("A2", "condition"));
        assertEquals("SELECT 5", show("A2", "action"));
        assertEquals("altered", show("A2", "comment"));
        assertEquals("started", show("A2", "state"), "CREATE OR ALTER keeps the state");
        engine.executeQuery("CREATE OR ALTER ALERT a2 IF (EXISTS (SELECT 4)) THEN SELECT 5");
        assertEquals("", show("A2", "comment"), "a property the statement leaves out is unset");
        engine.executeQuery("CREATE OR ALTER ALERT a2b IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertEquals("suspended", show("A2B", "state"));
    }

    @Test
    public void executeRunsTheActionOnlyWhenTheConditionReturnsRows() {
        engine.executeQuery("CREATE TABLE gauge (v INT)");
        engine.executeQuery("CREATE TABLE fired (msg VARCHAR)");
        engine.executeQuery("CREATE ALERT a3 IF (EXISTS (SELECT * FROM gauge WHERE v > 5)) "
            + "THEN INSERT INTO fired VALUES ('high')");
        assertEquals("Alert A3 is scheduled to run immediately.", status("EXECUTE ALERT a3"));
        assertEquals("0", status("SELECT COUNT(*) FROM fired"));
        engine.executeQuery("INSERT INTO gauge VALUES (10)");
        engine.executeQuery("EXECUTE ALERT test_db.test_schema.a3");
        assertEquals("1", status("SELECT COUNT(*) FROM fired"));
        assertEquals("suspended", show("A3", "state"), "EXECUTE ALERT does not resume the alert");
    }

    @Test
    public void alterSetsUnsetsAndModifies() {
        engine.executeQuery("CREATE ALERT a4 WAREHOUSE = compute_wh COMMENT = 'c' IF (EXISTS (SELECT 1))"
            + " THEN SELECT 1");
        engine.executeQuery("ALTER ALERT a4 SET SCHEDULE = 'USING CRON 0 9 * * * UTC' RUNBOOK = 'rb' CONFIG = 'x'");
        assertEquals("USING CRON 0 9 * * * UTC", show("A4", "schedule"));
        assertEquals("rb", show("A4", "runbook"));
        engine.executeQuery("ALTER ALERT a4 UNSET COMMENT, WAREHOUSE, CONFIG, RUNBOOK");
        assertEquals("", show("A4", "comment"));
        assertNull(show("A4", "warehouse"));
        assertNull(show("A4", "runbook"));
        engine.executeQuery("ALTER ALERT a4 MODIFY CONDITION EXISTS (SELECT 1 WHERE 1 = 0)");
        engine.executeQuery("ALTER ALERT a4 MODIFY ACTION INSERT INTO nowhere VALUES (1)");
        assertEquals("SELECT 1 WHERE 1 = 0", show("A4", "condition"));
        assertEquals("INSERT INTO nowhere VALUES (1)", show("A4", "action"));
        assertTrue(refusal("ALTER ALERT a4 SET SCHEDULE = '1 MINUTE' SCHEDULE = '2 MINUTE'")
            .contains("duplicate property"));
    }

    @Test
    public void resumeArmsTheScheduleAndSuspendDisarmsIt() {
        engine.executeQuery("CREATE ALERT a5 SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        final String key = TaskScheduler.alertKey("TEST_DB", "TEST_SCHEMA", "A5");
        assertFalse(engine.getTaskScheduler().isScheduled(key));
        engine.executeQuery("ALTER ALERT a5 RESUME");
        assertEquals("started", show("A5", "state"));
        assertTrue(engine.getTaskScheduler().isScheduled(key));
        engine.executeQuery("ALTER ALERT a5 SUSPEND");
        assertEquals("suspended", show("A5", "state"));
        assertFalse(engine.getTaskScheduler().isScheduled(key));
    }

    @Test
    public void aMissingAlertIsRefusedByItsQualifiedName() {
        engine.executeQuery("CREATE ALERT a6 IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertEquals("A6 successfully dropped.", status("DROP ALERT a6"));
        final String missing = "Alert 'TEST_DB.TEST_SCHEMA.A6' does not exist or not authorized.";
        assertTrue(refusal("DROP ALERT a6").contains(missing));
        assertEquals("Drop statement executed successfully (A6 already dropped).", status("DROP ALERT IF EXISTS a6"));
        assertTrue(refusal("ALTER ALERT a6 RESUME").contains(missing));
        assertEquals("Statement executed successfully.", status("ALTER ALERT IF EXISTS a6 RESUME"));
        assertTrue(refusal("DESCRIBE ALERT a6").contains(missing));
        assertTrue(refusal("EXECUTE ALERT a6").contains(missing));
    }

    @Test
    public void aCloneKeepsTheDefinitionAndStartsSuspended() {
        engine.executeQuery("CREATE ALERT c1 SCHEDULE = '30 MINUTE' COMMENT = 'src' IF (EXISTS (SELECT 1)) THEN SELECT 9");
        engine.executeQuery("ALTER ALERT c1 RESUME");
        assertEquals("Alert C2 successfully created.", status("CREATE ALERT c2 CLONE c1"));
        assertEquals("suspended", show("C2", "state"));
        assertEquals("SELECT 9", show("C2", "action"));
        assertEquals("src", show("C2", "comment"));
        final ResultSet terse = engine.executeQuery("SHOW TERSE ALERTS LIKE 'C%'");
        assertEquals(Arrays.asList("created_on", "name", "kind", "database_name", "schema_name", "schedule", "state"),
            columnNames(terse));
        assertEquals(2, terse.getRows().size());
        engine.executeQuery("ALTER ALERT c1 SUSPEND");
    }

    @Test
    public void showScopesAndModifiers() {
        engine.executeQuery("CREATE ALERT s1 IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.executeQuery("CREATE ALERT s2 IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.executeQuery("CREATE SCHEMA other_schema");
        engine.executeQuery("CREATE ALERT other_schema.s3 IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertEquals(3, engine.executeQuery("SHOW ALERTS IN ACCOUNT").getRows().size());
        assertEquals(3, engine.executeQuery("SHOW ALERTS IN DATABASE test_db").getRows().size());
        assertEquals(1, engine.executeQuery("SHOW ALERTS IN SCHEMA test_db.other_schema").getRows().size());
        assertEquals(2, engine.executeQuery("SHOW ALERTS IN SCHEMA test_db.test_schema").getRows().size());
        assertEquals(1, engine.executeQuery("SHOW ALERTS LIKE 's2' IN SCHEMA test_db.test_schema").getRows().size());
        assertEquals(1, engine.executeQuery("SHOW ALERTS IN SCHEMA test_db.test_schema LIMIT 1").getRows().size());
        assertEquals(3, engine.executeQuery("SHOW ALERTS IN ACCOUNT STARTS WITH 'S'").getRows().size());
        assertEquals(0, engine.executeQuery("SHOW ALERTS IN ACCOUNT STARTS WITH 'Z'").getRows().size());
    }

    @Test
    public void schedulesAreValidated() {
        engine.executeQuery("CREATE ALERT v1 SCHEDULE = '2 M' IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertEquals("2 M", show("V1", "schedule"));
        assertTrue(refusal("CREATE ALERT v2 SCHEDULE = 'every day' IF (EXISTS (SELECT 1)) THEN SELECT 1")
            .contains("Invalid schedule"));
        assertTrue(refusal("CREATE ALERT v3 SCHEDULE = '1 MINUTE' SCHEDULE = '2 MINUTE' "
            + "IF (EXISTS (SELECT 1)) THEN SELECT 1").contains("duplicate property"));
    }

    @Test
    public void consecutiveFailuresSuspendAStartedAlert() {
        engine.executeQuery("CREATE ALERT f1 SCHEDULE = '60 MINUTE' SUSPEND_ALERT_AFTER_NUM_FAILURES = 2 "
            + "IF (EXISTS (SELECT * FROM no_such_table)) THEN SELECT 1");
        engine.executeQuery("ALTER ALERT f1 RESUME");
        engine.executeQuery("EXECUTE ALERT f1");
        assertEquals("started", show("F1", "state"));
        engine.executeQuery("EXECUTE ALERT f1");
        assertEquals("suspended", show("F1", "state"));
        assertEquals("true", show("F1", "was_auto_suspended"));
        engine.executeQuery("ALTER ALERT f1 RESUME");
        assertEquals("false", show("F1", "was_auto_suspended"));
        engine.executeQuery("ALTER ALERT f1 SUSPEND");
    }

    @Test
    public void alertsCarryTags() {
        engine.executeQuery("CREATE TAG sev");
        engine.executeQuery("CREATE ALERT t1 WITH TAG (sev = 'high') IF (EXISTS (SELECT 1)) THEN SELECT 1");
        ResultSet refs = engine.executeQuery(
            "SELECT TAG_NAME, TAG_VALUE, LEVEL, APPLY_METHOD FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t1', 'ALERT'))");
        assertEquals(1, refs.getRows().size());
        assertEquals(Arrays.<Object>asList("SEV", "high", "ALERT", "MANUAL"), refs.getRows().get(0).getValues());
        engine.executeQuery("ALTER ALERT t1 SET TAG sev = 'low'");
        assertEquals("low", status("SELECT TAG_VALUE FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t1', 'ALERT'))"));
        assertEquals("low", status("SELECT SYSTEM$GET_TAG('sev', 't1', 'ALERT')"));
        engine.executeQuery("ALTER ALERT t1 UNSET TAG sev");
        refs = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('t1', 'ALERT'))");
        assertEquals(0, refs.getRows().size());
    }

    @Test
    public void theNewKeywordsStayUsableAsNames() {
        engine.executeQuery("CREATE TABLE alert (alert INT, alerts INT, config INT, condition INT, runbook INT, "
            + "propagate INT, on_conflict INT)");
        engine.executeQuery("INSERT INTO alert VALUES (1, 2, 3, 4, 5, 6, 7)");
        assertEquals("4", status("SELECT condition FROM alert WHERE config = 3 AND runbook = 5"));
    }
}
