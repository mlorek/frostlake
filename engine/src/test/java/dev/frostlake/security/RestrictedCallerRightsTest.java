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
 * A procedure that runs with restricted caller's rights may use only those of its caller's privileges a caller grant
 * names (documented: "every privilege required by the executable must be specified in one or more caller grants").
 * Without a caller grant such a body runs with no privilege at all: what needs none still runs, and whatever needs one
 * is refused, the SYSTEM user's session too (CallerGrantTest has the grants). EXECUTE AS CALLER is unchanged. Engine
 * only: the live account allows no CREATE ROLE or GRANT.
 */
public class RestrictedCallerRightsTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.public");
        engine.execute("CREATE ROLE ownr");
        engine.execute("CREATE ROLE callr");
        engine.execute("CREATE USER uo");
        engine.execute("CREATE USER uc");
        engine.execute("GRANT ROLE ownr TO USER uo");
        engine.execute("GRANT ROLE callr TO USER uc");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE ownr");
        engine.execute("GRANT USAGE, CREATE PROCEDURE ON SCHEMA p.public TO ROLE ownr");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE callr");
        engine.execute("GRANT USAGE, CREATE TABLE ON SCHEMA p.public TO ROLE callr");
        engine.execute("CREATE TABLE callsecret (a INT)");
        engine.execute("INSERT INTO callsecret VALUES (5)");
        engine.execute("GRANT SELECT ON TABLE callsecret TO ROLE callr");
        as("UO", "OWNR");
        procedure("rc", "RESTRICTED CALLER",
            "'DECLARE c INT; BEGIN SELECT MAX(a) INTO :c FROM callsecret; RETURN c::string; END'");
        procedure("rk", "RESTRICTED CALLER", "'BEGIN RETURN ''42''; END'");
        procedure("rcr", "RESTRICTED CALLER", "'BEGIN CREATE TABLE made (a INT); RETURN ''1''; END'");
        procedure("cc", "CALLER",
            "'DECLARE c INT; BEGIN SELECT MAX(a) INTO :c FROM callsecret; RETURN c::string; END'");
        asSystem();
    }

    private void procedure(final String name, final String mode, final String body) {
        engine.execute("CREATE PROCEDURE " + name + "() RETURNS STRING LANGUAGE SQL EXECUTE AS " + mode + " AS "
            + body);
        engine.execute("GRANT USAGE ON PROCEDURE " + name + "() TO ROLE callr");
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

    private void asSystem() {
        as("SYSTEM", systemRole);
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }


    private String call(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void aRestrictedCallerBodyReadsNothingItsCallerMayRead() {
        as("UC", "CALLR");
        assertEquals("5", call("SELECT MAX(a) FROM callsecret"));
        assertTrue(refusal("CALL rc()").contains("must have SELECT granted on TABLE P.PUBLIC.CALLSECRET"),
            refusal("CALL rc()"));
        // The caller's own rights are back once the call is over.
        assertEquals("5", call("SELECT MAX(a) FROM callsecret"));
    }

    @Test
    public void aRestrictedCallerBodyCreatesNothingItsCallerMayCreate() {
        as("UC", "CALLR");
        assertTrue(refusal("CALL rcr()").contains("must have CREATE TABLE granted on SCHEMA"), refusal("CALL rcr()"));
        engine.execute("CREATE TABLE mine (a INT)");
    }

    @Test
    public void aRestrictedCallerBodyThatNeedsNoPrivilegeRuns() {
        as("UC", "CALLR");
        assertEquals("42", call("CALL rk()"));
    }

    @Test
    public void aCallersRightsBodyStillRunsWithTheCallersPrivileges() {
        as("UC", "CALLR");
        assertEquals("5", call("CALL cc()"));
    }
}
