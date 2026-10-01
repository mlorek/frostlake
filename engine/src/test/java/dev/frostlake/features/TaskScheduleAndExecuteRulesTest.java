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
import dev.frostlake.LiveSnowflake;
import dev.frostlake.storage.ResultSet;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The account's rules for a task's CRON schedule at CREATE and for EXECUTE TASK, asserted through
 * the SQL surface so every check runs against whichever engine executed it (live-verified).
 *
 * <p>★ A CRON SCHEDULE IS HELD TO ITS FIELD GRAMMAR AT CREATE: a malformed field is the one
 * invalid-schedule sentence, an unrecognized zone is a sentence naming the token — the last token
 * is always the zone, so five fields and no zone name {@code "*"} — and a well-formed expression
 * that names no instant at all (February 31st) is its own sentence. None carries a compilation
 * prefix.
 *
 * <p>★ ONLY A ROOT RUNS ON DEMAND: EXECUTE TASK on a task with an AFTER list is refused, resumed or
 * suspended, naming it fully qualified. A three-part name reaches a task in another database; a
 * two-part name resolves in the current one.
 *
 * <p>No task is resumed here and the one EXECUTE TASK runs a single insert, so nothing lingers on
 * the account. The status sentences are read over a raw connection on live, because the harness
 * rewrites non-SELECT results there.
 */
public class TaskScheduleAndExecuteRulesTest extends BaseDatabaseTest {

    private static final String INVALID =
        "Invalid schedule was specified. Please refer to the docs on what constitutes a valid schedule.";

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    private String statusOf(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return String.valueOf(rs.getRows().get(0).getValue(0));
        } catch (final RuntimeException e) {
            return "ERR " + e.getMessage();
        }
    }

    @Test
    public void aMalformedCronFieldIsTheOneSentence() {
        assertEquals(INVALID, refusal("CREATE TASK cr_bad_minute SCHEDULE = 'USING CRON 61 * * * * UTC' AS SELECT 1"));
        assertEquals(INVALID, refusal("CREATE TASK cr_bad_dow SCHEDULE = 'USING CRON 0 0 * * 7 UTC' AS SELECT 1"));
        assertEquals(INVALID, refusal("CREATE TASK cr_bad_hash SCHEDULE = 'USING CRON 0 0 * * 1#2 UTC' AS SELECT 1"));
        assertEquals(INVALID, refusal("CREATE TASK cr_bad_step SCHEDULE = 'USING CRON */0 * * * * UTC' AS SELECT 1"));
    }

    @Test
    public void anUnrecognizedZoneIsNamed() {
        assertEquals("Invalid schedule was specified. \"*\" is not a recognized time zone. Please specify time"
                + " zones accepted by the TIMEZONE parameter.",
            refusal("CREATE TASK cr_no_zone SCHEDULE = 'USING CRON * * * * *' AS SELECT 1"));
        assertEquals("Invalid schedule was specified. \"PST\" is not a recognized time zone. Please specify time"
                + " zones accepted by the TIMEZONE parameter.",
            refusal("CREATE TASK cr_abbrev SCHEDULE = 'USING CRON 0 0 * * * PST' AS SELECT 1"));
    }

    @Test
    public void aScheduleThatNamesNoInstantIsRefused() {
        assertEquals("No valid time could be found under the schedule and/or interval specified",
            refusal("CREATE TASK cr_feb31 SCHEDULE = 'USING CRON 0 0 31 2 * UTC' AS SELECT 1"));
    }

    @Test
    public void theFormsTheAccountAcceptsAreCreated() {
        engine.execute("CREATE TASK cr_ok_last_fri SCHEDULE = 'USING CRON 0 0 * * 5L UTC' AS SELECT 1");
        engine.execute("CREATE TASK cr_ok_fril SCHEDULE = 'USING CRON 0 0 * * FRIL UTC' AS SELECT 1");
        engine.execute("CREATE TASK cr_ok_from_step SCHEDULE = 'USING CRON 5/15 * * * * UTC' AS SELECT 1");
        engine.execute("CREATE TASK cr_ok_dom_or_dow SCHEDULE = 'USING CRON 0 0 1 * MON UTC' AS SELECT 1");
        engine.execute("CREATE TASK cr_ok_lower_zone SCHEDULE = 'USING CRON 0 0 * * * utc' AS SELECT 1");
        engine.execute("CREATE TASK cr_ok_etc_zone SCHEDULE = 'USING CRON 0 0 * * * Etc/GMT+5' AS SELECT 1");
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE 'CR_OK_%'");
        assertEquals(6, tasks.getRows().size());
    }

    @Test
    public void executeTaskRefusesANonRootTask() {
        engine.execute("CREATE TASK xr_root SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK xr_child AFTER xr_root AS SELECT 1");
        final String refused = "Execute task cannot be called on non-root task TEST_DB.TEST_SCHEMA.XR_CHILD."
            + " Call EXECUTE TASK on the root task of its graph instead.";
        assertEquals(refused, refusal("EXECUTE TASK xr_child"));
        assertEquals(refused, refusal("EXECUTE TASK test_db.test_schema.xr_child"));
        engine.execute("ALTER TASK xr_child RESUME");
        assertEquals(refused, refusal("EXECUTE TASK xr_child"));
        engine.execute("ALTER TASK xr_child SUSPEND");
    }

    @Test
    public void aThreePartNameReachesATaskInAnotherDatabase() {
        engine.execute("CREATE TABLE xr_log (n INT)");
        engine.execute("CREATE TASK xr_run SCHEDULE = '60 MINUTE' AS INSERT INTO xr_log SELECT 1");
        statusOf("CREATE OR REPLACE DATABASE xr_other_db");
        try {
            assertEquals("Task XR_RUN is scheduled to run immediately.",
                statusOf("EXECUTE TASK test_db.test_schema.xr_run"));
            assertEquals(hinted("ERR SQL compilation error:\nTask 'XR_OTHER_DB.PUBLIC.XR_RUN' does not exist or not"
                + " authorized."), statusOf("EXECUTE TASK public.xr_run"));
        } finally {
            statusOf("USE DATABASE test_db");
            statusOf("USE SCHEMA test_schema");
            statusOf("DROP DATABASE IF EXISTS xr_other_db");
        }
        if (!LiveSnowflake.enabled()) {
            // Live runs the task asynchronously; embedded, the run is over by the time the status returns.
            assertEquals("1", String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM xr_log").getRows().get(0).getValue(0)));
        }
    }
}
