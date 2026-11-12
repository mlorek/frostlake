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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TASK_DEPENDENTS "only returns rows for a task owner" or a role with MONITOR or OPERATE on the task, and to view a
 * task graph through TASK_HISTORY and the graph functions "the invoking role requires at least one of" OWNERSHIP,
 * MONITOR or OPERATE on the task, or MONITOR EXECUTION (documented). They listed every task and its SQL to any role.
 * Engine only: the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class TaskFunctionVisibilityTest {

    private static final String DEPENDENTS = "SELECT name FROM TABLE(d1.information_schema.task_dependents("
        + "task_name => 'd1.priv.root', recursive => TRUE)) ORDER BY name";
    private static final String HISTORY = "SELECT name FROM TABLE(d1.information_schema.task_history()) "
        + "WHERE state <> 'SCHEDULED' ORDER BY name";
    private static final String GRAPHS = "SELECT root_task_name FROM TABLE(d1.information_schema."
        + "complete_task_graphs())";

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.priv");
        engine.execute("CREATE TABLE d1.priv.t (a INT)");
        engine.execute("CREATE TASK d1.priv.root SCHEDULE = '60 MINUTE' AS INSERT INTO d1.priv.t SELECT 1");
        engine.execute("CREATE TASK d1.priv.child AFTER d1.priv.root AS DELETE FROM d1.priv.t");
        engine.execute("EXECUTE TASK d1.priv.root");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA d1.priv TO ROLE r1");
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

    private String names(final String sql) {
        final List<Row> rows = engine.executeQuery(sql).getRows();
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            out.append(i > 0 ? "," : "").append(rows.get(i).getValue(0));
        }
        return out.toString();
    }

    @Test
    public void aRoleHoldingNothingOnTheTasksReadsNone() {
        as("U1", "R1");
        assertEquals("SQL compilation error:\nInvalid value [d1.priv.root] for function 'TASK_DEPENDENTS_SCAN', "
            + "parameter 1: must be a valid task name", assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery(DEPENDENTS);
                }
            }).getMessage());
        assertEquals("", names(HISTORY));
        assertEquals("", names(GRAPHS));
    }

    @Test
    public void monitorShowsTheTaskItIsGrantedOn() {
        engine.execute("GRANT MONITOR ON TASK d1.priv.root TO ROLE r1");
        as("U1", "R1");
        assertEquals("ROOT", names(DEPENDENTS));
        assertEquals("ROOT", names(HISTORY));
        assertEquals("ROOT", names(GRAPHS));
    }

    @Test
    public void monitorExecutionShowsEveryTask() {
        engine.execute("GRANT MONITOR EXECUTION ON ACCOUNT TO ROLE r1");
        as("U1", "R1");
        assertEquals("CHILD,ROOT", names(DEPENDENTS));
        as("SYSTEM", systemRole);
        assertEquals("CHILD,ROOT", names(DEPENDENTS));
    }
}
