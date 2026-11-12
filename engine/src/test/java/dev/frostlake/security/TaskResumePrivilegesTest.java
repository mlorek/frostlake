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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resuming a task asks its owning role for the account's EXECUTE TASK privilege, EXECUTE MANAGED TASK for a
 * serverless one, and USAGE on its warehouse (documented, ALTER TASK: the owner "also has the USAGE privilege on the
 * warehouse assigned to the task, as well as the global EXECUTE TASK privilege; if not, an error is produced"). The
 * owner is asked, not the session, so an OPERATE grantee is refused while the owner lacks either. Only OWNERSHIP or
 * OPERATE was asked. The refusal's words are unmeasured on live. Engine only: the live account allows no CREATE ROLE
 * or GRANT.
 */
public class TaskResumePrivilegesTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("USE SCHEMA d1.public");
        engine.execute("CREATE WAREHOUSE trwh");
        engine.execute("CREATE TABLE trlog (x STRING)");
        engine.execute("CREATE ROLE tr");
        engine.execute("CREATE ROLE op");
        engine.execute("CREATE USER ut");
        engine.execute("GRANT ROLE tr TO USER ut");
        engine.execute("GRANT ROLE op TO USER ut");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE tr");
        engine.execute("GRANT USAGE, CREATE TASK ON SCHEMA d1.public TO ROLE tr");
        engine.execute("GRANT INSERT ON TABLE trlog TO ROLE tr");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE op");
        engine.execute("GRANT USAGE ON SCHEMA d1.public TO ROLE op");
        as("UT", "TR");
        engine.execute("CREATE TASK tw WAREHOUSE = trwh SCHEDULE = '60 MINUTE' AS INSERT INTO trlog VALUES ('tw')");
        engine.execute("CREATE TASK ts SCHEDULE = '60 MINUTE' AS INSERT INTO trlog VALUES ('ts')");
        engine.execute("GRANT OPERATE ON TASK tw TO ROLE op");
        as("SYSTEM", systemRole);
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String user, final String role) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser(user);
        engine.setCurrentRole(role);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String state(final String task) {
        engine.execute("SHOW TASKS LIKE '" + task + "'");
        final Row row = engine.executeQuery("SELECT \"state\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))").getRows()
            .get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void theOwnerNeedsExecuteTaskAndTheWarehouse() {
        as("UT", "TR");
        assertTrue(refusal("ALTER TASK tw RESUME").contains("Insufficient privileges to operate on task 'TW'"));
        assertEquals("suspended", state("TW"));
        as("SYSTEM", systemRole);
        engine.execute("GRANT EXECUTE TASK ON ACCOUNT TO ROLE tr");
        as("UT", "TR");
        assertTrue(refusal("ALTER TASK tw RESUME").contains("Insufficient privileges to operate on task 'TW'"),
            "the warehouse is still out of reach");
        as("SYSTEM", systemRole);
        engine.execute("GRANT USAGE ON WAREHOUSE trwh TO ROLE tr");
        as("UT", "TR");
        engine.execute("ALTER TASK tw RESUME");
        assertEquals("started", state("TW"));
        engine.execute("ALTER TASK tw SUSPEND");
    }

    @Test
    public void aServerlessTaskNeedsExecuteManagedTask() {
        engine.execute("GRANT EXECUTE TASK ON ACCOUNT TO ROLE tr");
        as("UT", "TR");
        assertTrue(refusal("ALTER TASK ts RESUME").contains("Insufficient privileges to operate on task 'TS'"));
        as("SYSTEM", systemRole);
        engine.execute("GRANT EXECUTE MANAGED TASK ON ACCOUNT TO ROLE tr");
        as("UT", "TR");
        engine.execute("ALTER TASK ts RESUME");
        assertEquals("started", state("TS"));
        engine.execute("ALTER TASK ts SUSPEND");
    }

    @Test
    public void anOperatorIsRefusedWhileTheOwnerLacksThem() {
        as("UT", "OP");
        assertTrue(refusal("ALTER TASK tw RESUME").contains("Insufficient privileges to operate on task 'TW'"));
        as("SYSTEM", systemRole);
        engine.execute("GRANT EXECUTE TASK ON ACCOUNT TO ROLE tr");
        engine.execute("GRANT USAGE ON WAREHOUSE trwh TO ROLE tr");
        as("UT", "OP");
        engine.execute("ALTER TASK tw RESUME");
        assertEquals("started", state("TW"), "once the owner holds them, the operator resumes the task");
        engine.execute("ALTER TASK tw SUSPEND");
    }
}
