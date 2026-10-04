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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TASK_HISTORY and ALERT_HISTORY report a run's times as TIMESTAMP_LTZ at the true instant, and a run's body reads
 * a time no earlier than the run's start. Task runs were stamped on the host's wall clock and reported as text, hours
 * off on a host outside UTC. EXECUTE TASK and EXECUTE ALERT ran the body at the caller's statement instant, before
 * the start the run recorded, and ALERT_HISTORY's range bounds compared microseconds against the milliseconds the
 * rows showed.
 */
public class TaskAndAlertRunTimesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE times_db");
        engine.execute("USE SCHEMA times_db.PUBLIC");
        engine.execute("CREATE TABLE tlog (ts TIMESTAMP_LTZ)");
        engine.execute("CREATE TASK tk AS INSERT INTO tlog SELECT CURRENT_TIMESTAMP()");
        engine.execute("CREATE TABLE alog (ts TIMESTAMP_LTZ)");
        engine.execute("CREATE ALERT al WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE'"
            + " IF (EXISTS (SELECT 1)) THEN INSERT INTO alog SELECT CURRENT_TIMESTAMP()");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private Row onlyRow(final String sql) {
        final List<Row> rows = engine.executeQuery(sql).getRows();
        assertEquals(1, rows.size(), sql);
        return rows.get(0);
    }

    @Test
    public void taskHistoryTimesAreTheTrueInstants() {
        engine.execute("EXECUTE TASK tk");
        final ResultSet history = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'TK'))");
        for (final ResultSetColumn column : history.getColumns()) {
            if (Arrays.asList("SCHEDULED_TIME", "QUERY_START_TIME", "NEXT_SCHEDULED_TIME", "COMPLETED_TIME")
                    .contains(column.getName())) {
                assertEquals("TIMESTAMP_LTZ", column.getDataType().getName(), column.getName());
            }
        }
        final Row checks = onlyRow("SELECT scheduled_time <= query_start_time,"
            + " (SELECT MAX(ts) FROM tlog) >= query_start_time,"
            + " query_start_time <= completed_time,"
            + " completed_time <= CURRENT_TIMESTAMP()"
            + " FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY(TASK_NAME => 'TK'))");
        assertEquals(Boolean.TRUE, checks.getValue(0), "scheduled before it started");
        assertEquals(Boolean.TRUE, checks.getValue(1), "the body's clock is no earlier than the run's start");
        assertEquals(Boolean.TRUE, checks.getValue(2), "started before it completed");
        assertEquals(Boolean.TRUE, checks.getValue(3), "completed in the past, whatever the host's zone");
    }

    @Test
    public void alertHistoryBoundsMeetTheTimesItShows() {
        engine.execute("EXECUTE ALERT al");
        engine.execute("SET st = (SELECT scheduled_time FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY())"
            + " WHERE name = 'AL')");
        assertEquals(1L, ((Number) onlyRow("SELECT COUNT(*) FROM TABLE(INFORMATION_SCHEMA.ALERT_HISTORY("
            + "SCHEDULED_TIME_RANGE_START => $st, SCHEDULED_TIME_RANGE_END => $st)) WHERE name = 'AL'")
            .getValue(0)).longValue(), "the row's own scheduled time bounds it");
        assertEquals(Boolean.TRUE, onlyRow("SELECT (SELECT MAX(ts) FROM alog) >= $st").getValue(0),
            "the action's clock is no earlier than the evaluation's scheduled time");
    }
}
