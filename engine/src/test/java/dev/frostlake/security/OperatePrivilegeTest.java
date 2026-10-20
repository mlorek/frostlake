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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * OPERATE on a task "Grants the ability to suspend or resume a task", and on a dynamic table it is what a role needs to
 * "suspend, resume, refresh, or change the warehouse or target lag" (documented). A role granted OPERATE does those, a
 * manual refresh reading with the table owner's rights; every other change stays the owner's, and a role holding only
 * MONITOR or SELECT does none of them (FL-437). Engine only: GRANT and the switch of user are not run on the live
 * account.
 */
public class OperatePrivilegeTest extends BaseDatabaseTest {

    private String openerUser;

    private String opener;

    @Override
    protected void setupTest() {
        assumeFalse(isLiveSnowflake(), "GRANT and USE ROLE are not run on the live account");
        openerUser = engine.getSessionContext().getCurrentUser();
        opener = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE ROLE op_r");
        engine.execute("CREATE USER op_u");
        engine.execute("GRANT ROLE op_r TO USER op_u");
        engine.execute("GRANT USAGE ON DATABASE test_db TO ROLE op_r");
        engine.execute("GRANT USAGE ON SCHEMA test_db.test_schema TO ROLE op_r");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String taskState(final String task) {
        final ResultSet tasks = engine.executeQuery("SHOW TASKS LIKE '" + task + "'");
        return cell(tasks, tasks.getRows().get(0), "state");
    }

    /** The session as OP_U with only OP_R active: the engine's own user is asked for no privilege. */
    private void asRole() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("OP_U");
        engine.setCurrentRole("OP_R");
    }

    private void asOpener() {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(openerUser);
        engine.setCurrentRole(opener);
    }

    @Test
    public void operateResumesAndSuspendsATask() {
        engine.execute("CREATE TASK op_a SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK op_m SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("GRANT OPERATE ON TASK op_a TO ROLE op_r");
        engine.execute("GRANT MONITOR ON TASK op_m TO ROLE op_r");
        asRole();
        try {
            engine.execute("ALTER TASK op_a RESUME");
            asOpener();
            assertEquals("started", taskState("OP_A"));
            asRole();
            engine.execute("ALTER TASK op_a SUSPEND");
            // A property is the owner's to change, and MONITOR operates nothing.
            assertTrue(refusal("ALTER TASK op_a SET COMMENT = 'x'").contains("does not have ALTER privilege"));
            assertTrue(refusal("ALTER TASK op_m RESUME").contains("does not have OPERATE privilege on TASK"));
        } finally {
            asOpener();
        }
        assertEquals("suspended", taskState("OP_A"));
        assertEquals("suspended", taskState("OP_M"));
    }

    @Test
    public void operateRefreshesSuspendsAndResumesADynamicTable() {
        engine.execute("CREATE TABLE op_base (a INT)");
        engine.execute("INSERT INTO op_base VALUES (1)");
        engine.execute("CREATE DYNAMIC TABLE op_dt TARGET_LAG = '1 minute' WAREHOUSE = compute_wh "
            + "AS SELECT a FROM op_base");
        engine.execute("CREATE DYNAMIC TABLE op_sel TARGET_LAG = '1 minute' WAREHOUSE = compute_wh "
            + "AS SELECT a FROM op_base");
        engine.execute("GRANT OPERATE, SELECT ON DYNAMIC TABLE op_dt TO ROLE op_r");
        engine.execute("GRANT SELECT ON DYNAMIC TABLE op_sel TO ROLE op_r");
        engine.execute("INSERT INTO op_base VALUES (2)");
        asRole();
        try {
            // The refresh reads OP_BASE with the owner's rights: the role holds nothing on it.
            engine.execute("ALTER DYNAMIC TABLE op_dt REFRESH");
            assertEquals(2, engine.executeQuery("SELECT * FROM op_dt").getRows().size());
            engine.execute("ALTER DYNAMIC TABLE op_dt SUSPEND");
            engine.execute("ALTER DYNAMIC TABLE op_dt RESUME");
            engine.execute("ALTER DYNAMIC TABLE op_dt SET TARGET_LAG = '5 minutes'");
            assertTrue(refusal("ALTER DYNAMIC TABLE op_dt SET COMMENT = 'x'")
                .contains("does not have ALTER privilege"));
            assertTrue(refusal("ALTER DYNAMIC TABLE op_dt RENAME TO op_dt2")
                .contains("does not have ALTER privilege"));
            assertTrue(refusal("ALTER DYNAMIC TABLE op_sel REFRESH")
                .contains("does not have OPERATE privilege on DYNAMIC TABLE"));
            assertTrue(refusal("ALTER DYNAMIC TABLE op_sel SUSPEND")
                .contains("does not have OPERATE privilege on DYNAMIC TABLE"));
        } finally {
            asOpener();
        }
    }
}
