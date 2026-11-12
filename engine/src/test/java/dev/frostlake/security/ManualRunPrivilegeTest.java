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
import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * "Executing a task requires either the OWNERSHIP or OPERATE privilege on the task" (documented, EXECUTE TASK), and an
 * alert's run on demand asks the same of the alert (documented, EXECUTE ALERT); a task or an alert the session cannot
 * see, holding no privilege on it, is one that is not there. Both statements asked nothing, so any role that could
 * name one ran it. Engine only: the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class ManualRunPrivilegeTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE TABLE log (who STRING)");
        engine.execute("CREATE TASK c SCHEDULE = '60 MINUTE' AS INSERT INTO log VALUES ('task')");
        engine.execute("CREATE ALERT al SCHEDULE = '60 MINUTE' IF (EXISTS (SELECT 1)) "
            + "THEN INSERT INTO log VALUES ('alert')");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1 DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE r1 TO USER u1");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE r1");
        engine.execute("GRANT USAGE ON SCHEMA p.public TO ROLE r1");
        engine.execute("GRANT INSERT ON TABLE log TO ROLE r1");
        engine.execute("CREATE ROLE nobody");
        engine.execute("CREATE USER u_nobody DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE nobody TO USER u_nobody");
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

    private long logged() {
        as("SYSTEM", systemRole);
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM p.public.log").getRows().get(0).getValue(0))
            .longValue();
    }

    @Test
    public void aRoleThatSeesThemWithoutOperateRunsNeither() {
        engine.execute("GRANT MONITOR ON TASK c TO ROLE r1");
        engine.execute("GRANT MONITOR ON ALERT al TO ROLE r1");
        as("U1", "R1");
        assertEquals("SQL access control error:\nInsufficient privileges to operate on task 'C'. Your primary role R1 "
            + "must have OPERATE granted on TASK P.PUBLIC.C.", refusal("EXECUTE TASK p.public.c"));
        assertEquals("SQL access control error:\nInsufficient privileges to operate on alert 'AL'. Your primary role "
            + "R1 must have OPERATE granted on ALERT P.PUBLIC.AL.", refusal("EXECUTE ALERT p.public.al"));
        assertEquals(0L, logged());
    }

    @Test
    public void anAlertAlsoAsksExecuteAlertOnTheAccount() {
        engine.execute("GRANT OPERATE ON ALERT al TO ROLE r1");
        as("U1", "R1");
        final String refused = refusal("EXECUTE ALERT p.public.al");
        assertEquals(true, refused.startsWith("SQL access control error:\nInsufficient privileges to operate on "
            + "account '") && refused.endsWith("Your primary role R1 must have EXECUTE ALERT granted on ACCOUNT "
            + refused.substring(refused.indexOf("account '") + 9, refused.indexOf("'.")) + "."), refused);
        assertEquals(0L, logged());
    }

    @Test
    public void aRoleThatHoldsNothingOnThemIsToldThereAreNone() {
        for (final String[] session : new String[][] {{"U_NOBODY", "NOBODY"}, {"U1", "R1"}}) {
            as(session[0], session[1]);
            assertEquals("SQL compilation error:\nTask 'P.PUBLIC.C' does not exist or not authorized. Your primary "
                + "role " + session[1] + " must have at least one privilege granted on TASK P.PUBLIC.C.",
                refusal("EXECUTE TASK p.public.c"));
            assertEquals("SQL compilation error:\nAlert 'P.PUBLIC.AL' does not exist or not authorized. Your primary "
                + "role " + session[1] + " must have at least one privilege granted on ALERT P.PUBLIC.AL.",
                refusal("EXECUTE ALERT p.public.al"));
        }
        assertEquals(0L, logged());
    }

    @Test
    public void operateRunsThem() {
        engine.execute("GRANT OPERATE ON TASK c TO ROLE r1");
        engine.execute("GRANT OPERATE ON ALERT al TO ROLE r1");
        engine.execute("GRANT EXECUTE ALERT ON ACCOUNT TO ROLE r1");
        AlertEvaluationWait.mark(engine);
        as("U1", "R1");
        engine.execute("EXECUTE TASK p.public.c");
        engine.execute("EXECUTE ALERT p.public.al");
        as("SYSTEM", systemRole);
        AlertEvaluationWait.until(engine, "AL", 1);
        assertEquals(2L, logged());
    }
}
