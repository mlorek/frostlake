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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A task takes APPLYBUDGET, MONITOR, OPERATE and OWNERSHIP alone (documented, "Task privileges"): SELECT, INSERT,
 * USAGE and MODIFY granted on one are refused as privileges the kind does not define, and only the task's owner sets,
 * unsets or modifies it (documented, ALTER TASK: "Only the task owner ... can set or unset properties on a task"),
 * whatever another role holds on it. A granted MODIFY let another role rewrite the task's body. The GRANT refusal's
 * words for a task are unmeasured on live. Engine only: the live account allows no CREATE ROLE or GRANT.
 */
public class TaskPrivilegeKindsTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("USE SCHEMA d1.public");
        engine.execute("CREATE ROLE op");
        engine.execute("CREATE USER uo");
        engine.execute("GRANT ROLE op TO USER uo");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE op");
        engine.execute("GRANT USAGE ON SCHEMA d1.public TO ROLE op");
        engine.execute("CREATE TASK tk SCHEDULE = '60 MINUTE' AS SELECT 1");
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

    @Test
    public void aPrivilegeNoTaskTakesIsRefused() {
        for (final String privilege : new String[] {"SELECT", "INSERT", "USAGE", "MODIFY"}) {
            assertEquals("SQL compilation error:\nInvalid object type 'TASK' for privilege '" + privilege + "'.",
                refusal("GRANT " + privilege + " ON TASK tk TO ROLE op"));
        }
        engine.execute("GRANT MONITOR, OPERATE ON TASK tk TO ROLE op");
        engine.execute("GRANT ALL ON TASK tk TO ROLE op");
    }

    @Test
    public void onlyTheOwnerChangesTheTask() {
        engine.execute("GRANT ALL ON TASK tk TO ROLE op");
        as("UO", "OP");
        final String body = refusal("ALTER TASK tk MODIFY AS SELECT 2");
        assertTrue(body.contains("Insufficient privileges to operate on task 'TK'"), body);
        final String schedule = refusal("ALTER TASK tk SET SCHEDULE = '1 MINUTE'");
        assertTrue(schedule.contains("Insufficient privileges to operate on task 'TK'"), schedule);
        as("SYSTEM", systemRole);
        engine.execute("ALTER TASK tk SET SCHEDULE = '1 MINUTE'");
    }
}
