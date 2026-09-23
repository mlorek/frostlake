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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An alert's warehouse must exist, which is judged before the alert is looked for; EXECUTE ALERT and CREATE OR ALTER
 * ALERT answer the account's sentences; a refused CREATE OR ALTER leaves the alert as it was; ALERT_HISTORY takes a
 * RESULT_LIMIT up to ten thousand and reports a run's queries and its error code.
 */
public class AlertWarehouseAndStatusTest extends BaseDatabaseTest {

    private static final String NO_WAREHOUSE = "Nonexistent warehouse NOSUCH_WH was specified.";

    @AfterEach
    public void stopScheduler() {
        if (!isLiveSnowflake()) {
            engine.stopTaskScheduler();
        }
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String show(final String name, final String column) {
        final ResultSet rs = engine.executeQuery("SHOW ALERTS IN SCHEMA test_db.test_schema");
        return cell(rs, soleRowWhere(rs, "name", name), column);
    }

    @Test
    public void anAlertsWarehouseMustExist() {
        assertTrue(refusal("CREATE ALERT awa_1 WAREHOUSE = nosuch_wh SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1))"
            + " THEN SELECT 1").contains(NO_WAREHOUSE));
        assertEquals(0, engine.executeQuery("SHOW ALERTS LIKE 'AWA_1' IN SCHEMA test_db.test_schema").getRowCount());
        engine.executeQuery("CREATE ALERT awa_2 WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE' COMMENT = 'c'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1");
        assertTrue(refusal("ALTER ALERT awa_2 SET WAREHOUSE = nosuch_wh").contains(NO_WAREHOUSE));
        assertTrue(refusal("CREATE OR ALTER ALERT awa_2 WAREHOUSE = nosuch_wh SCHEDULE = '30 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1").contains(NO_WAREHOUSE));
        // The refused statements left the alert as it was.
        assertEquals("COMPUTE_WH", show("AWA_2", "warehouse"));
        assertEquals("60 MINUTE", show("AWA_2", "schedule"));
        assertEquals("c", show("AWA_2", "comment"));
    }

    @Test
    public void theWarehouseIsJudgedBeforeTheAlertIsLookedFor() {
        engine.executeQuery("CREATE ALERT awa_5 WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1");
        // IF NOT EXISTS forgives an alert that exists, not a warehouse that does not or a property written twice.
        assertTrue(refusal("CREATE ALERT IF NOT EXISTS awa_5 WAREHOUSE = nosuch_wh SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1").contains(NO_WAREHOUSE));
        assertTrue(refusal("CREATE ALERT IF NOT EXISTS awa_5 SCHEDULE = '60 MINUTE' COMMENT = 'a' COMMENT = 'b'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1").contains("duplicate property 'COMMENT';"));
        // The schedule is judged only for an alert the statement makes.
        assertEquals("AWA_5 already exists, statement succeeded.", status("CREATE ALERT IF NOT EXISTS awa_5"
            + " SCHEDULE = '9 SECONDS' IF (EXISTS (SELECT 1)) THEN SELECT 1"));
        assertTrue(refusal("CREATE ALERT awa_5 WAREHOUSE = nosuch_wh SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 1").contains(NO_WAREHOUSE));
        assertTrue(refusal("CREATE ALERT awa_5 SCHEDULE = '9 SECONDS' IF (EXISTS (SELECT 1)) THEN SELECT 1")
            .contains("Object 'AWA_5' already exists."));
        assertEquals("COMPUTE_WH", show("AWA_5", "warehouse"));
    }

    @Test
    public void createOrAlterOverAnAlertAnswersAsAnAlter() {
        assertEquals("Alert AWA_3 successfully created.", status("CREATE OR ALTER ALERT awa_3 WAREHOUSE = compute_wh"
            + " SCHEDULE = '60 MINUTE' COMMENT = 'c3' IF (EXISTS (SELECT 1)) THEN SELECT 1"));
        assertEquals("Statement executed successfully.", status("CREATE OR ALTER ALERT awa_3 SCHEDULE = '30 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN SELECT 2"));
        // What the statement leaves out is unset: the comment, and the warehouse, which makes the alert serverless.
        assertEquals("", show("AWA_3", "comment"));
        assertNull(show("AWA_3", "warehouse"));
        assertEquals("30 MINUTE", show("AWA_3", "schedule"));
    }

    @Test
    public void executeAlertIsScheduledToRunImmediately() {
        engine.executeQuery("CREATE ALERT awa_4 WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1 WHERE FALSE)) THEN SELECT 1");
        assertEquals("Alert AWA_4 is scheduled to run immediately.", status("EXECUTE ALERT awa_4"));
        assertTrue(refusal("EXECUTE ALERT awa_nosuch").contains(hinted(
            "Alert 'TEST_DB.TEST_SCHEMA.AWA_NOSUCH' does not exist or not authorized.")));
    }

    @Test
    public void alertHistoryTakesALimitUpToTenThousand() {
        assertEquals(0, engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY(RESULT_LIMIT => 0))").getRowCount());
        assertTrue(refusal("SELECT * FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY(RESULT_LIMIT => 10001))")
            .contains("Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000)."));
    }

    @Test
    public void aRunReportsItsQueriesAndAZeroErrorCode() {
        // The evaluation has finished when EXECUTE ALERT returns only on this engine.
        Assumptions.assumeFalse(isLiveSnowflake());
        engine.executeQuery("CREATE ALERT awa_hit IF (EXISTS (SELECT 1)) THEN SELECT 1");
        engine.executeQuery("CREATE ALERT awa_miss IF (EXISTS (SELECT 1 WHERE FALSE)) THEN SELECT 1");
        engine.executeQuery("EXECUTE ALERT awa_hit");
        engine.executeQuery("EXECUTE ALERT awa_miss");
        final ResultSet rs = engine.executeQuery("SELECT NAME, STATE, SQL_ERROR_CODE, CONDITION_QUERY_ID,"
            + " ACTION_QUERY_ID FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY()) ORDER BY NAME");
        assertEquals(2, rs.getRowCount());
        final Row hit = rs.getRows().get(0);
        assertEquals("AWA_HIT", hit.getValue(0));
        assertEquals("TRIGGERED", hit.getValue(1));
        assertEquals(0L, ((Number) hit.getValue(2)).longValue());
        assertNotNull(hit.getValue(3));
        assertNotNull(hit.getValue(4));
        final Row miss = rs.getRows().get(1);
        assertEquals("CONDITION_FALSE", miss.getValue(1));
        assertEquals(0L, ((Number) miss.getValue(2)).longValue());
        assertNotNull(miss.getValue(3));
        assertNull(miss.getValue(4), "no action ran");
    }
}
