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

package dev.frostlake.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A DROP or an ALTER of an object another role owns is refused in the account's sentence, whichever kind it names: a
 * DROP and a rename ask OWNERSHIP, every other change MODIFY, and suspending, resuming, refreshing or pausing a task,
 * an alert, a pipe, a dynamic table or a warehouse OPERATE. An external stage is used with USAGE where an internal
 * one is read with READ and written with WRITE (documented). Engine only: GRANT and USE ROLE are not run on the live
 * account.
 */
public class AlterDropPrivilegeWordsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and USE ROLE are not run on the live account");
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS aw_other");
        engine.execute("CREATE TABLE aw_base (a INT)");
    }

    @Override
    protected void teardownTest() {
        if (isLiveSnowflake()) {
            return;
        }
        quietly("USE ROLE ACCOUNTADMIN");
        quietly("GRANT ROLE aw_other TO ROLE ACCOUNTADMIN");
        quietly("DROP WAREHOUSE IF EXISTS aw_wh");
        quietly("DROP DATABASE IF EXISTS aw_db");
        quietly("DROP ROLE IF EXISTS aw_other");
    }

    private void quietly(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private void runs(final String sql) {
        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
    }

    private void giveAway(final String kindAndName) {
        engine.execute("GRANT OWNERSHIP ON " + kindAndName + " TO ROLE aw_other COPY CURRENT GRANTS");
    }

    private static String refused(final String kind, final String name, final String privilege,
                                  final String grantKind, final String full) {
        return "SQL access control error:\nInsufficient privileges to operate on " + kind + " '" + name
            + "'. Your primary role ACCOUNTADMIN must have " + privilege + " granted on " + grantKind + " " + full
            + ".";
    }

    private static String inSchema(final String name) {
        return "TEST_DB.TEST_SCHEMA." + name;
    }

    @Test
    public void aDynamicTableAndAMaterializedViewAnotherRoleOwns() {
        engine.execute("CREATE DYNAMIC TABLE aw_dt TARGET_LAG = '1 minute' WAREHOUSE = compute_wh "
            + "AS SELECT a FROM aw_base");
        engine.execute("CREATE MATERIALIZED VIEW aw_mv AS SELECT a FROM aw_base");
        giveAway("DYNAMIC TABLE aw_dt");
        giveAway("MATERIALIZED VIEW aw_mv");
        final String dynamicOwnership = refused("dynamic_table", "AW_DT", "OWNERSHIP", "DYNAMIC TABLE",
            inSchema("AW_DT"));
        assertEquals(dynamicOwnership, refusal("DROP DYNAMIC TABLE aw_dt"));
        assertEquals(dynamicOwnership, refusal("ALTER DYNAMIC TABLE aw_dt RENAME TO aw_dt2"));
        assertEquals(refused("dynamic_table", "AW_DT", "MODIFY", "DYNAMIC TABLE", inSchema("AW_DT")),
            refusal("ALTER DYNAMIC TABLE aw_dt SET COMMENT = 'x'"));
        assertEquals(refused("dynamic_table", "AW_DT", "OPERATE", "DYNAMIC TABLE", inSchema("AW_DT")),
            refusal("ALTER DYNAMIC TABLE aw_dt SUSPEND"));
        final String viewOwnership = refused("materialized_view", "AW_MV", "OWNERSHIP", "TABLE", inSchema("AW_MV"));
        assertEquals(viewOwnership, refusal("DROP MATERIALIZED VIEW aw_mv"));
        assertEquals(viewOwnership, refusal("ALTER MATERIALIZED VIEW aw_mv RENAME TO aw_mv2"));
        assertEquals(viewOwnership, refusal("ALTER TABLE aw_mv RENAME TO aw_mv3"));
        // The owner's role granted back is what lets the session drop them.
        engine.execute("GRANT ROLE aw_other TO ROLE ACCOUNTADMIN");
        runs("DROP DYNAMIC TABLE aw_dt");
        runs("DROP MATERIALIZED VIEW aw_mv");
    }

    @Test
    public void aWarehouseIsModifiedOperatedRenamedAndDroppedByItsHoldersOnly() {
        engine.execute("GRANT CREATE WAREHOUSE ON ACCOUNT TO ROLE aw_other");
        engine.execute("GRANT ROLE aw_other TO ROLE ACCOUNTADMIN");
        engine.execute("USE ROLE aw_other");
        engine.execute("CREATE WAREHOUSE aw_wh INITIALLY_SUSPENDED = TRUE");
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("REVOKE ROLE aw_other FROM ROLE ACCOUNTADMIN");
        assertEquals(refused("warehouse", "AW_WH", "MODIFY", "WAREHOUSE", "AW_WH"),
            refusal("ALTER WAREHOUSE aw_wh SET COMMENT = 'x'"));
        final String operate = refused("warehouse", "AW_WH", "OPERATE", "WAREHOUSE", "AW_WH");
        assertEquals(operate, refusal("ALTER WAREHOUSE aw_wh RESUME"));
        assertEquals(operate, refusal("ALTER WAREHOUSE aw_wh SUSPEND"));
        assertEquals(operate, refusal("ALTER WAREHOUSE aw_wh ABORT ALL QUERIES"));
        final String ownership = refused("warehouse", "AW_WH", "OWNERSHIP", "WAREHOUSE", "AW_WH");
        assertEquals(ownership, refusal("ALTER WAREHOUSE aw_wh RENAME TO aw_wh2"));
        assertEquals(ownership, refusal("DROP WAREHOUSE aw_wh"));
        assertEquals(ownership, refusal("DROP WAREHOUSE IF EXISTS aw_wh"));
        engine.execute("GRANT MODIFY, OPERATE ON WAREHOUSE aw_wh TO ROLE ACCOUNTADMIN");
        runs("ALTER WAREHOUSE aw_wh SET COMMENT = 'x'");
        runs("ALTER WAREHOUSE aw_wh RESUME");
        runs("ALTER WAREHOUSE aw_wh SUSPEND");
        assertEquals(ownership, refusal("ALTER WAREHOUSE aw_wh RENAME TO aw_wh2"));
    }

    @Test
    public void policiesTagsStreamsTasksPipesAndDatabasesAnotherRoleOwns() {
        engine.execute("CREATE MASKING POLICY aw_mp AS (v STRING) RETURNS STRING -> v");
        engine.execute("CREATE ROW ACCESS POLICY aw_rap AS (v INT) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE TAG aw_tg");
        engine.execute("CREATE STREAM aw_st ON TABLE aw_base");
        engine.execute("CREATE TASK aw_tk SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE STAGE aw_stg");
        engine.execute("CREATE PIPE aw_p AS COPY INTO aw_base FROM @aw_stg");
        // A pipe changes owner only while it is paused.
        engine.execute("ALTER PIPE aw_p SET PIPE_EXECUTION_PAUSED = TRUE");
        engine.execute("CREATE DATABASE aw_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
        for (final String kind : new String[] {"MASKING POLICY aw_mp", "ROW ACCESS POLICY aw_rap", "TAG aw_tg",
            "STREAM aw_st", "TASK aw_tk", "PIPE aw_p", "DATABASE aw_db"}) {
            giveAway(kind);
        }
        assertEquals(refused("masking_policy", "AW_MP", "MODIFY", "POLICY", inSchema("AW_MP")),
            refusal("ALTER MASKING POLICY aw_mp SET COMMENT = 'x'"));
        assertEquals(refused("masking_policy", "AW_MP", "OWNERSHIP", "POLICY", inSchema("AW_MP")),
            refusal("ALTER MASKING POLICY aw_mp RENAME TO aw_mp2"));
        assertEquals(refused("row_access_policy", "AW_RAP", "MODIFY", "POLICY", inSchema("AW_RAP")),
            refusal("ALTER ROW ACCESS POLICY aw_rap SET COMMENT = 'x'"));
        assertEquals(refused("tag", "AW_TG", "MODIFY", "TAG", inSchema("AW_TG")),
            refusal("ALTER TAG aw_tg SET COMMENT = 'x'"));
        assertEquals(refused("tag", "AW_TG", "OWNERSHIP", "TAG", inSchema("AW_TG")),
            refusal("ALTER TAG aw_tg RENAME TO aw_tg2"));
        assertEquals(refused("stream", "AW_ST", "MODIFY", "STREAM", inSchema("AW_ST")),
            refusal("ALTER STREAM aw_st SET COMMENT = 'x'"));
        assertEquals(refused("task", "AW_TK", "MODIFY", "TASK", inSchema("AW_TK")),
            refusal("ALTER TASK aw_tk SET COMMENT = 'x'"));
        assertEquals(refused("task", "AW_TK", "OPERATE", "TASK", inSchema("AW_TK")),
            refusal("ALTER TASK aw_tk RESUME"));
        assertEquals(refused("pipe", "AW_P", "MODIFY", "PIPE", inSchema("AW_P")),
            refusal("ALTER PIPE aw_p SET COMMENT = 'x'"));
        final String operatePipe = refused("pipe", "AW_P", "OPERATE", "PIPE", inSchema("AW_P"));
        assertEquals(operatePipe, refusal("ALTER PIPE aw_p SET PIPE_EXECUTION_PAUSED = FALSE"));
        assertEquals(operatePipe, refusal("ALTER PIPE aw_p REFRESH"));
        assertEquals(refused("database", "AW_DB", "MODIFY", "DATABASE", "AW_DB"),
            refusal("ALTER DATABASE aw_db SET COMMENT = 'x'"));
        assertEquals(refused("database", "AW_DB", "OWNERSHIP", "DATABASE", "AW_DB"), refusal("DROP DATABASE aw_db"));
        // OPERATE lets a role that does not own the task or the pipe operate it, and no more.
        engine.execute("GRANT OPERATE ON TASK aw_tk TO ROLE ACCOUNTADMIN");
        engine.execute("GRANT OPERATE ON PIPE aw_p TO ROLE ACCOUNTADMIN");
        runs("ALTER TASK aw_tk RESUME");
        runs("ALTER TASK aw_tk SUSPEND");
        runs("ALTER PIPE aw_p REFRESH");
        assertEquals(refused("task", "AW_TK", "MODIFY", "TASK", inSchema("AW_TK")),
            refusal("ALTER TASK aw_tk SET COMMENT = 'x'"));
    }

    @Test
    public void anAlertAnotherRoleOwns() {
        engine.execute("CREATE ALERT aw_al WAREHOUSE = compute_wh SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) "
            + "THEN SELECT 1");
        giveAway("ALERT aw_al");
        assertEquals(refused("alert", "AW_AL", "MODIFY", "ALERT", inSchema("AW_AL")),
            refusal("ALTER ALERT aw_al SET COMMENT = 'x'"));
        assertEquals(refused("alert", "AW_AL", "OPERATE", "ALERT", inSchema("AW_AL")),
            refusal("ALTER ALERT aw_al RESUME"));
        assertEquals(refused("alert", "AW_AL", "OWNERSHIP", "ALERT", inSchema("AW_AL")), refusal("DROP ALERT aw_al"));
        engine.execute("GRANT OPERATE ON ALERT aw_al TO ROLE ACCOUNTADMIN");
        runs("ALTER ALERT aw_al RESUME");
        runs("ALTER ALERT aw_al SUSPEND");
    }

    @Test
    public void anExternalStageIsUsedWithUsageAndAnInternalOneReadAndWritten() {
        engine.execute("CREATE STAGE aw_ext URL = 's3://bucket/path/'");
        engine.execute("CREATE STAGE aw_int");
        giveAway("STAGE aw_ext");
        giveAway("STAGE aw_int");
        final String usage = refused("stage", "AW_EXT", "USAGE", "STAGE", inSchema("AW_EXT"));
        assertEquals(usage, refusal("COPY INTO aw_base FROM @aw_ext"));
        assertEquals(usage, refusal("COPY INTO @aw_ext FROM aw_base"));
        assertEquals(usage, refusal("SELECT $1 FROM @aw_ext"));
        assertEquals(refused("stage", "AW_EXT", "at least one privilege", "STAGE", inSchema("AW_EXT")),
            refusal("LIST @aw_ext"));
        assertEquals(refused("stage", "AW_INT", "READ", "STAGE", inSchema("AW_INT")),
            refusal("COPY INTO aw_base FROM @aw_int"));
        assertEquals(refused("stage", "AW_INT", "WRITE", "STAGE", inSchema("AW_INT")),
            refusal("COPY INTO @aw_int FROM aw_base"));
        engine.execute("GRANT READ, WRITE ON STAGE aw_int TO ROLE ACCOUNTADMIN");
        runs("COPY INTO @aw_int FROM aw_base");
        runs("COPY INTO aw_base FROM @aw_int");
    }
}
