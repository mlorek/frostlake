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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CURRENT_ROLE() inside a stored procedure: the role that owns the procedure while it runs with owner's rights,
 * whoever called it, and the calling session's role while it runs with the caller's. The session's own role is
 * back once the call ends.
 */
public class OwnersRightsCurrentRoleTest {

    private DatabaseEngine engine;
    private String callerRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        callerRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE g");
        engine.execute("USE SCHEMA g.PUBLIC");
        engine.execute("CREATE ROLE cr_owner");
        engine.execute("GRANT USAGE ON DATABASE g TO ROLE cr_owner");
        engine.execute("GRANT USAGE ON SCHEMA g.PUBLIC TO ROLE cr_owner");
        engine.execute("GRANT CREATE PROCEDURE ON SCHEMA g.PUBLIC TO ROLE cr_owner");
        // A role is activated by a user it reaches: here through the role the session opens with.
        engine.execute("GRANT ROLE cr_owner TO ROLE " + engine.getSessionContext().getCurrentRole());
        engine.execute("USE ROLE cr_owner");
        engine.execute("CREATE PROCEDURE p_owner() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN CURRENT_ROLE(); END'");
        engine.execute("CREATE PROCEDURE p_caller() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS CALLER AS "
            + "'BEGIN RETURN CURRENT_ROLE(); END'");
        engine.execute("CREATE PROCEDURE p_outer() RETURNS VARCHAR LANGUAGE SQL AS "
            + "'BEGIN CALL p_owner(); RETURN CURRENT_ROLE(); END'");
        engine.execute("GRANT USAGE ON PROCEDURE p_owner() TO ROLE " + callerRole);
        engine.execute("GRANT USAGE ON PROCEDURE p_caller() TO ROLE " + callerRole);
        engine.execute("GRANT USAGE ON PROCEDURE p_outer() TO ROLE " + callerRole);
        engine.execute("USE ROLE " + callerRole);
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String text(final String sql) {
        final ResultSet answer = engine.executeQuery(sql);
        return String.valueOf(answer.getRows().get(0).getValue(0));
    }

    @Test
    public void anOwnersRightsBodyReadsItsOwnersRole() {
        assertEquals("CR_OWNER", text("CALL p_owner()"));
        assertEquals(callerRole, text("SELECT CURRENT_ROLE()"));
    }

    @Test
    public void aCallersRightsBodyReadsTheCallersRole() {
        assertEquals(callerRole, text("CALL p_caller()"));
    }

    @Test
    public void theOwnersRoleIsBackAfterACallInsideTheBody() {
        assertEquals("CR_OWNER", text("CALL p_outer()"));
        assertEquals(callerRole, text("SELECT CURRENT_ROLE()"));
    }
}
