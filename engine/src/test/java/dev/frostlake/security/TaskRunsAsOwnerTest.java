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

import dev.frostlake.AlertEvaluationWait;
import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A task and an alert always run with the privileges of the role that owns them, even when a different role with
 * OPERATE runs them, and a task run uses the system user with the task owner role's privileges (documented): the
 * run's primary role is the owner, whose CURRENT_ROLE() the body reads, whatever role the starting session holds.
 * Engine only: GRANT and the switch of user are not run on the live account.
 */
public class TaskRunsAsOwnerTest extends BaseDatabaseTest {

    private String openerUser;

    private String opener;

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and the switch of user are not run on the live account");
        openerUser = engine.getSessionContext().getCurrentUser();
        opener = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE ROLE ro_low");
        engine.execute("CREATE USER ro_u DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE ro_low TO USER ro_u");
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE ro_low");
        engine.execute("GRANT USAGE, CREATE TASK, CREATE TABLE ON SCHEMA test_db.test_schema TO ROLE ro_low");
        engine.execute("GRANT EXECUTE TASK ON ACCOUNT TO ROLE ro_low");
        engine.execute("CREATE TABLE ro_payroll (name VARCHAR, salary INT)");
        engine.execute("INSERT INTO ro_payroll VALUES ('ceo', 999)");
        engine.execute("CREATE TABLE ro_log (who VARCHAR)");
    }

    /** The session as RO_U with only RO_LOW active. */
    private void asLow() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("RO_U");
        engine.setCurrentRole("RO_LOW");
    }

    private void asOpener() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(openerUser);
        engine.setCurrentRole(opener);
    }

    private String lastRun(final String task) {
        final ResultSet rs = engine.executeQuery("SELECT state, error_message FROM TABLE(information_schema"
            + ".task_history(task_name => '" + task + "')) WHERE state NOT IN ('SCHEDULED', 'EXECUTING') "
            + "ORDER BY query_start_time DESC LIMIT 1");
        return rs.getRows().get(0).getValue(0) + "|" + rs.getRows().get(0).getValue(1);
    }

    private String logged() {
        final ResultSet rs = engine.executeQuery("SELECT LISTAGG(who, ',') WITHIN GROUP (ORDER BY who) FROM ro_log");
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aLowRolesTaskRunFromAnAdministratorsSessionKeepsTheLowRolesRights() {
        asLow();
        try {
            engine.execute("CREATE TASK ro_zero SCHEDULE = '60 MINUTE' AS UPDATE ro_payroll SET salary = salary - 1");
            engine.execute("CREATE TABLE ro_own (who VARCHAR)");
            engine.execute("CREATE TASK ro_who SCHEDULE = '60 MINUTE' AS INSERT INTO ro_own VALUES (CURRENT_ROLE())");
            engine.execute("GRANT OPERATE ON TASK ro_zero TO ROLE SYSADMIN");
            engine.execute("GRANT OPERATE ON TASK ro_who TO ROLE SYSADMIN");
        } finally {
            asOpener();
        }
        engine.execute("EXECUTE TASK ro_zero");
        engine.execute("EXECUTE TASK ro_who");
        assertEquals("FAILED|SQL access control error:\nInsufficient privileges to operate on table 'RO_PAYROLL'. "
            + "Your primary role RO_LOW must have SELECT granted on TABLE TEST_DB.TEST_SCHEMA.RO_PAYROLL.",
            lastRun("RO_ZERO"));
        assertEquals("999", String.valueOf(engine.executeQuery("SELECT salary FROM ro_payroll").getRows().get(0)
            .getValue(0)));
        // The body reads the owner as its role and writes the owner's own table.
        assertEquals("SUCCEEDED|null", lastRun("RO_WHO"));
        asLow();
        try {
            assertEquals("RO_LOW", String.valueOf(engine.executeQuery("SELECT who FROM ro_own").getRows().get(0)
                .getValue(0)));
        } finally {
            asOpener();
        }
    }

    @Test
    public void anOperateGranteesRunHasTheOwnersRights() {
        engine.execute("CREATE TASK ro_c SCHEDULE = '60 MINUTE' AS INSERT INTO ro_log VALUES (CURRENT_ROLE())");
        engine.execute("GRANT OPERATE ON TASK ro_c TO ROLE ro_low");
        engine.execute("CREATE ALERT ro_al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) "
            + "THEN INSERT INTO ro_log VALUES ('alert')");
        engine.execute("GRANT OPERATE ON ALERT ro_al TO ROLE ro_low");
        engine.execute("GRANT EXECUTE ALERT ON ACCOUNT TO ROLE ro_low");
        AlertEvaluationWait.mark(engine);
        asLow();
        try {
            engine.execute("EXECUTE TASK ro_c");
            engine.execute("EXECUTE ALERT ro_al");
        } finally {
            asOpener();
        }
        AlertEvaluationWait.until(engine, "RO_AL", 1);
        assertEquals("SUCCEEDED|null", lastRun("RO_C"));
        assertEquals("SYSADMIN,alert", logged());
    }
}
