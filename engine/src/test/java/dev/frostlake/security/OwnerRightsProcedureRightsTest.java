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
 * A stored procedure that runs with owner's rights runs with its owner's privileges and none of its caller's: its
 * body reads what the owner may read, and not what only the caller may. IS_ROLE_IN_SESSION() inside it looks at the
 * owner's roles, the ones acting there (documented), while CURRENT_AVAILABLE_ROLES(), the roles granted to the user,
 * answers inside as it does outside (live-verified). A caller's-rights procedure runs with the caller's. The live
 * account allows no CREATE ROLE, CREATE USER or GRANT, so a caller other than the owner is the embedded engine's alone.
 */
public class OwnerRightsProcedureRightsTest {

    private static final String ROLES = """
        IS_ROLE_IN_SESSION('R') || '/' || IS_ROLE_IN_SESSION('SYSADMIN') || '/' || CURRENT_AVAILABLE_ROLES()""";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.setCurrentRole("SYSADMIN");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE SCHEMA db.public");
        engine.execute("CREATE TABLE owners (a INT)");
        engine.execute("INSERT INTO owners VALUES (1), (2)");
        engine.execute("CREATE ROLE r");
        engine.execute("CREATE USER u");
        engine.execute("GRANT ROLE r TO USER u");
        engine.execute("GRANT USAGE ON DATABASE db TO ROLE r");
        engine.execute("GRANT USAGE, CREATE TABLE ON SCHEMA db.public TO ROLE r");
        engine.execute("""
            CREATE PROCEDURE read_owners_o() RETURNS INT LANGUAGE SQL EXECUTE AS OWNER
            AS 'BEGIN RETURN (SELECT COUNT(*) FROM owners); END'""");
        engine.execute("""
            CREATE PROCEDURE read_callers_o() RETURNS INT LANGUAGE SQL EXECUTE AS OWNER
            AS 'BEGIN RETURN (SELECT COUNT(*) FROM callers); END'""");
        engine.execute("""
            CREATE PROCEDURE read_owners_c() RETURNS INT LANGUAGE SQL EXECUTE AS CALLER
            AS 'BEGIN RETURN (SELECT COUNT(*) FROM owners); END'""");
        engine.execute("""
            CREATE PROCEDURE read_callers_c() RETURNS INT LANGUAGE SQL EXECUTE AS CALLER
            AS 'BEGIN RETURN (SELECT COUNT(*) FROM callers); END'""");
        engine.execute("CREATE PROCEDURE roles_o() RETURNS STRING LANGUAGE SQL EXECUTE AS OWNER AS $$ BEGIN RETURN "
            + ROLES + "; END $$");
        engine.execute("CREATE PROCEDURE roles_c() RETURNS STRING LANGUAGE SQL EXECUTE AS CALLER AS $$ BEGIN RETURN "
            + ROLES + "; END $$");
        engine.execute("GRANT USAGE ON ALL PROCEDURES IN SCHEMA db.public TO ROLE r");
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u");
        engine.setCurrentRole("r");
        engine.execute("CREATE TABLE callers (a INT)");
        engine.execute("INSERT INTO callers VALUES (1)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
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
    public void anOwnersRightsBodyReadsWhatTheOwnerMayAndNotWhatOnlyTheCallerMay() {
        assertTrue(refusal("SELECT COUNT(*) FROM owners").contains("table 'OWNERS'"));
        assertEquals(2L, ((Number) scalar("CALL read_owners_o()")).longValue());
        assertTrue(refusal("CALL read_callers_o()").contains("table 'CALLERS'"), refusal("CALL read_callers_o()"));
    }

    @Test
    public void aCallersRightsBodyReadsWhatTheCallerMay() {
        assertEquals(1L, ((Number) scalar("CALL read_callers_c()")).longValue());
        assertTrue(refusal("CALL read_owners_c()").contains("table 'OWNERS'"), refusal("CALL read_owners_c()"));
    }

    @Test
    public void insideAnOwnersRightsBodyTheOwnersRolesAreInSession() {
        final Object available = scalar("SELECT CURRENT_AVAILABLE_ROLES()");
        assertEquals("true/false/" + available, scalar("SELECT " + ROLES));
        assertEquals("false/true/" + available, scalar("CALL roles_o()"));
        assertEquals("true/false/" + available, scalar("CALL roles_c()"));
    }
}
