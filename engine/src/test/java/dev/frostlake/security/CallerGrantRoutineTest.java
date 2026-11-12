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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A caller grant on one procedure or function is recorded under the routine's signature, the name its privileges are
 * asked by, so a RESTRICTED CALLER body may call it: GRANT CALLER ... ON PROCEDURE p(INT) takes the argument types,
 * and without them the grant covers every overload of the name, as an ordinary GRANT does. With the types the grant
 * was a syntax error, and without them it was recorded under the bare name, which no check matched. Unmeasured on
 * live. Engine only: the live account allows no CREATE ROLE or GRANT.
 */
public class CallerGrantRoutineTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        for (final String sql : new String[] {
            "CREATE DATABASE d1", "CREATE SCHEMA d1.s1", "CREATE ROLE rown", "CREATE ROLE rcall", "CREATE USER uown",
            "CREATE USER ucall", "GRANT ROLE rown TO USER uown", "GRANT ROLE rcall TO USER ucall",
            "GRANT USAGE ON DATABASE d1 TO ROLE rown", "GRANT USAGE ON SCHEMA d1.s1 TO ROLE rown",
            "GRANT CREATE PROCEDURE, CREATE FUNCTION ON SCHEMA d1.s1 TO ROLE rown",
            "GRANT USAGE ON DATABASE d1 TO ROLE rcall", "GRANT USAGE ON SCHEMA d1.s1 TO ROLE rcall",
            "CREATE PROCEDURE d1.s1.helper(x INT) RETURNS INT LANGUAGE SQL EXECUTE AS CALLER"
                + " AS 'BEGIN RETURN x + 1; END'",
            "CREATE FUNCTION d1.s1.fhelp(x INT) RETURNS INT AS 'x * 2'",
            "GRANT USAGE ON PROCEDURE d1.s1.helper(INT) TO ROLE rcall",
            "GRANT USAGE ON FUNCTION d1.s1.fhelp(INT) TO ROLE rcall",
            "GRANT CALLER USAGE ON DATABASE d1 TO ROLE rown", "GRANT CALLER USAGE ON SCHEMA d1.s1 TO ROLE rown"}) {
            engine.execute(sql);
        }
        as("UOWN", "ROWN");
        engine.execute("CREATE PROCEDURE d1.s1.outer_p() RETURNS INT LANGUAGE SQL EXECUTE AS RESTRICTED CALLER"
            + " AS 'DECLARE n INT; BEGIN CALL d1.s1.helper(1) INTO :n; RETURN n; END'");
        engine.execute("CREATE PROCEDURE d1.s1.outer_f() RETURNS INT LANGUAGE SQL EXECUTE AS RESTRICTED CALLER"
            + " AS 'DECLARE n INT; BEGIN SELECT d1.s1.fhelp(5) INTO :n; RETURN n; END'");
        engine.execute("GRANT USAGE ON PROCEDURE d1.s1.outer_p() TO ROLE rcall");
        engine.execute("GRANT USAGE ON PROCEDURE d1.s1.outer_f() TO ROLE rcall");
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

    private String call(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void aGrantWithTheSignatureCoversTheProcedure() {
        engine.execute("GRANT CALLER USAGE ON PROCEDURE d1.s1.helper(INT) TO ROLE rown");
        as("UCALL", "RCALL");
        assertEquals("2", call("CALL d1.s1.outer_p()"));
    }

    @Test
    public void aGrantWithoutTheSignatureCoversEveryOverload() {
        engine.execute("GRANT CALLER USAGE ON FUNCTION d1.s1.fhelp TO ROLE rown");
        as("UCALL", "RCALL");
        assertEquals("10", call("CALL d1.s1.outer_f()"));
    }

    @Test
    public void aRevokeWithTheSignatureTakesItBack() {
        engine.execute("GRANT CALLER USAGE ON PROCEDURE d1.s1.helper(INT) TO ROLE rown");
        engine.execute("REVOKE CALLER USAGE ON PROCEDURE d1.s1.helper(INT) FROM ROLE rown");
        assertEquals(2, engine.executeQuery("SHOW CALLER GRANTS TO ROLE rown").getRows().size());
    }
}
