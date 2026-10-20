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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * TASK_HISTORY and the task graph functions tell a run that ran the query that ran the task's statement — its id,
 * the code of the error it failed with, the hashes QUERY_HISTORY gives the statement — and the user it was
 * scheduled by: who ran EXECUTE TASK, or for a scheduled run who resumed the task, whose NEXT_SCHEDULED_TIME is the
 * fire after its own (live-verified). SERVERLESS_TASK_HISTORY lists the account's four columns.
 */
public class TaskHistoryRunQueryTest extends MeasuredCellsTestSupport {

    private static final String H = "FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY()) WHERE NAME LIKE 'THR_%'";

    /** Waits for the account to finish the two runs EXECUTE TASK started; the engine runs them as it starts them. */
    private void awaitRuns() {
        for (int i = 0; i < 18; i++) {
            final Object done = engine.executeQuery("SELECT COUNT(*) " + H
                + " AND STATE IN ('SUCCEEDED', 'FAILED')").getRows().get(0).getValue(0);
            if (Long.parseLong(String.valueOf(done)) >= 2L) {
                return;
            }
            engine.execute("CALL SYSTEM$WAIT(10)");
        }
    }

    @Test
    public void aRunTellsItsQueryItsErrorAndWhoScheduledIt() {
        engine.execute("CREATE OR REPLACE TABLE thr_tab (a INT)");
        engine.execute("CREATE OR REPLACE TASK thr_ok AS INSERT INTO thr_tab VALUES (1)");
        engine.execute("CREATE OR REPLACE TASK thr_bad AS SELECT 1 / 0");
        engine.execute("CREATE OR REPLACE TASK thr_sch SCHEDULE = '60 MINUTE' AS SELECT 1");
        try {
            engine.execute("EXECUTE TASK thr_ok");
            engine.execute("EXECUTE TASK thr_bad");
            engine.execute("ALTER TASK thr_sch RESUME");
            awaitRuns();
            assertCells(new String[][] {
                {"SELECT NAME, STATE, LENGTH(QUERY_ID), ERROR_CODE, LENGTH(QUERY_HASH), QUERY_HASH_VERSION, "
                    + "LENGTH(QUERY_PARAMETERIZED_HASH), QUERY_PARAMETERIZED_HASH_VERSION, "
                    + "SCHEDULED_BY_USER = CURRENT_USER() " + H + " AND STATE <> 'SCHEDULED' "
                    + "ORDER BY NAME, ATTEMPT_NUMBER",
                    "THR_BAD|FAILED|36|100051|32|2|32|1|true ;; THR_OK|SUCCEEDED|36|null|32|2|32|1|true"},
                {"SELECT NAME, STATE, SCHEDULED_BY_USER IS NULL, SCHEDULED_BY_USER = CURRENT_USER(), "
                    + "NEXT_SCHEDULED_TIME IS NULL, DATEDIFF('second', SCHEDULED_TIME, NEXT_SCHEDULED_TIME), "
                    + "QUERY_ID IS NULL " + H + " AND STATE = 'SCHEDULED' ORDER BY NAME",
                    "THR_SCH|SCHEDULED|false|true|false|3600|true"},
                {"SELECT ERROR_CODE, ERROR_MESSAGE " + H + " AND STATE = 'FAILED' ORDER BY ATTEMPT_NUMBER",
                    "100051|Division by zero"},
                {"SELECT th.QUERY_HASH = qh.QUERY_HASH, th.QUERY_PARAMETERIZED_HASH = qh.QUERY_PARAMETERIZED_HASH, "
                    + "qh.QUERY_TEXT FROM TABLE(INFORMATION_SCHEMA.TASK_HISTORY()) th "
                    + "JOIN TABLE(INFORMATION_SCHEMA.QUERY_HISTORY()) qh ON qh.QUERY_ID = th.QUERY_ID "
                    + "WHERE th.NAME = 'THR_OK'", "true|true|INSERT INTO thr_tab VALUES (1)"},
                {"SELECT ROOT_TASK_NAME, STATE, FIRST_ERROR_CODE, SCHEDULED_BY_USER = CURRENT_USER() FROM "
                    + "TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS()) WHERE ROOT_TASK_NAME LIKE 'THR_%' "
                    + "ORDER BY 1, 2", "THR_BAD|FAILED|100051|true ;; THR_OK|SUCCEEDED|null|true"},
                {"SELECT ROOT_TASK_NAME, STATE, SCHEDULED_BY_USER IS NULL, NEXT_SCHEDULED_TIME IS NULL "
                    + "FROM TABLE(INFORMATION_SCHEMA.CURRENT_TASK_GRAPHS()) WHERE ROOT_TASK_NAME LIKE 'THR_%' "
                    + "ORDER BY 1", "THR_SCH|SCHEDULED|false|false"},
                {"SELECT COUNT(*) >= 0 FROM TABLE(INFORMATION_SCHEMA.SERVERLESS_TASK_HISTORY(TASK_NAME => 'THR_OK'))",
                    "true"},
                {"SELECT COUNT(*) >= 0 FROM TABLE(INFORMATION_SCHEMA.SERVERLESS_TASK_HISTORY("
                    + "DATE_RANGE_START => DATEADD('hour', -1, CURRENT_TIMESTAMP())))", "true"},
            });
            assertShape("START_TIME:TIMESTAMP_LTZ, END_TIME:TIMESTAMP_LTZ, TASK_NAME:VARCHAR(134217728), "
                + "CREDITS_USED:NUMBER(38,9)",
                "SELECT * FROM TABLE(INFORMATION_SCHEMA.SERVERLESS_TASK_HISTORY()) WHERE 1 = 0");
        } finally {
            engine.execute("ALTER TASK IF EXISTS thr_sch SUSPEND");
        }
    }
}
