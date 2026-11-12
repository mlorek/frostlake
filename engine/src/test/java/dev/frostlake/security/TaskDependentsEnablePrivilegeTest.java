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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SYSTEM$TASK_DEPENDENTS_ENABLE resumes a graph, which "allows the owner of a task graph" to do in one statement what
 * ALTER TASK … RESUME does task by task (documented): each task it would resume asks OPERATE or OWNERSHIP, all of
 * them before any is resumed, and a task the session does not see is a name no task answers to. It asked nothing,
 * so a role holding nothing started another role's graph. Engine only: the live account allows no CREATE ROLE,
 * CREATE USER or GRANT.
 */
public class TaskDependentsEnablePrivilegeTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("CREATE TASK d1.s1.root SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE TASK d1.s1.child AFTER d1.s1.root AS SELECT 2");
        engine.execute("CREATE ROLE nobody");
        engine.execute("CREATE USER u_nobody DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE nobody TO USER u_nobody");
        engine.execute("CREATE ROLE op");
        engine.execute("CREATE USER u_op DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE op TO USER u_op");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE op");
        engine.execute("GRANT USAGE ON SCHEMA d1.s1 TO ROLE op");
        engine.execute("GRANT OPERATE ON TASK d1.s1.root TO ROLE op");
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
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private String states() {
        as("SYSTEM", systemRole);
        final ResultSet rs = engine.executeQuery("SHOW TASKS IN SCHEMA d1.s1");
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getRows().size(); i++) {
            out.append(i > 0 ? "," : "").append(cell(rs, i, "name")).append('=')
                .append(cell(rs, i, "state"));
        }
        return out.toString();
    }

    private static String cell(final ResultSet rs, final int row, final String column) {
        for (int c = 0; c < rs.getColumns().size(); c++) {
            if (rs.getColumns().get(c).getName().equalsIgnoreCase(column)) {
                return String.valueOf(rs.getRows().get(row).getValue(c));
            }
        }
        throw new IllegalArgumentException(column);
    }

    @Test
    public void aRoleHoldingNothingIsToldThereIsNoSuchTask() {
        as("U_NOBODY", "NOBODY");
        assertEquals("SQL compilation error:\ninvalid value [d1.s1.root] for parameter 'RECURSIVE_TASK_ENABLE'",
            refusal("SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('d1.s1.root')"));
        assertEquals("CHILD=suspended,ROOT=suspended", states());
    }

    @Test
    public void everyTaskOfTheGraphIsAskedBeforeAnyIsResumed() {
        as("U_OP", "OP");
        assertEquals("SQL access control error:\nInsufficient privileges to operate on task 'CHILD'. Your primary "
            + "role OP must have OPERATE granted on TASK D1.S1.CHILD.",
            refusal("SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('d1.s1.root')"));
        assertEquals("CHILD=suspended,ROOT=suspended", states());
        engine.execute("GRANT OPERATE ON TASK d1.s1.child TO ROLE op");
        as("U_OP", "OP");
        assertEquals("Statement executed successfully.", String.valueOf(engine.executeQuery(
            "SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('d1.s1.root')").getRows().get(0).getValue(0)));
        assertEquals("CHILD=started,ROOT=started", states());
        engine.execute("ALTER TASK d1.s1.root SUSPEND");
    }
}
