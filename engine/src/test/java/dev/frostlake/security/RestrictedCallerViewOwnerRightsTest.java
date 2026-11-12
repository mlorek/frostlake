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
 * Inside a RESTRICTED CALLER body a view's own query reads its base tables with the view owner's rights, as
 * everywhere else (documented: a view's query runs with its owner's privileges): the caller grants govern only which
 * of the caller's privileges the body uses, here SELECT on the view. The view's body was checked against the caller
 * grants instead, so a view the caller may read failed on its base table. Engine only: the live account allows no
 * CREATE ROLE or GRANT.
 */
public class RestrictedCallerViewOwnerRightsTest {

    private DatabaseEngine engine;
    private String systemRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        systemRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.s1");
        engine.execute("CREATE TABLE d1.s1.secret (a INT)");
        engine.execute("INSERT INTO d1.s1.secret VALUES (42)");
        engine.execute("CREATE VIEW d1.s1.vsec AS SELECT a FROM d1.s1.secret");
        engine.execute("CREATE ROLE rown");
        engine.execute("CREATE ROLE rcall");
        engine.execute("CREATE USER uown");
        engine.execute("CREATE USER ucall");
        engine.execute("GRANT ROLE rown TO USER uown");
        engine.execute("GRANT ROLE rcall TO USER ucall");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE rown");
        engine.execute("GRANT USAGE ON SCHEMA d1.s1 TO ROLE rown");
        engine.execute("GRANT CREATE PROCEDURE ON SCHEMA d1.s1 TO ROLE rown");
        engine.execute("GRANT USAGE ON DATABASE d1 TO ROLE rcall");
        engine.execute("GRANT USAGE ON SCHEMA d1.s1 TO ROLE rcall");
        engine.execute("GRANT SELECT ON VIEW d1.s1.vsec TO ROLE rcall");
        engine.execute("GRANT CALLER USAGE ON DATABASE d1 TO ROLE rown");
        engine.execute("GRANT CALLER USAGE ON SCHEMA d1.s1 TO ROLE rown");
        engine.execute("GRANT CALLER SELECT ON VIEW d1.s1.vsec TO ROLE rown");
        as("UOWN", "ROWN");
        procedure("view_rcr", "RESTRICTED CALLER", "d1.s1.vsec");
        procedure("view_cr", "CALLER", "d1.s1.vsec");
        procedure("table_rcr", "RESTRICTED CALLER", "d1.s1.secret");
        as("SYSTEM", systemRole);
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void procedure(final String name, final String mode, final String relation) {
        engine.execute("CREATE PROCEDURE d1.s1." + name + "() RETURNS INT LANGUAGE SQL EXECUTE AS " + mode
            + " AS 'DECLARE n INT; BEGIN SELECT MAX(a) INTO :n FROM " + relation + "; RETURN n; END'");
        engine.execute("GRANT USAGE ON PROCEDURE d1.s1." + name + "() TO ROLE rcall");
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

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void aRestrictedBodyReadsThroughAViewTheCallerMayRead() {
        as("UCALL", "RCALL");
        assertEquals("42", call("SELECT MAX(a) FROM d1.s1.vsec"));
        assertEquals("42", call("CALL d1.s1.view_cr()"));
        assertEquals("42", call("CALL d1.s1.view_rcr()"));
    }

    @Test
    public void theBaseTableItselfStaysOutOfReach() {
        as("UCALL", "RCALL");
        final String refused = refusal("CALL d1.s1.table_rcr()");
        assertTrue(refused.contains("must have SELECT granted on TABLE D1.S1.SECRET"), refused);
    }

    @Test
    public void theViewStillNeedsItsCallerGrant() {
        engine.execute("REVOKE CALLER SELECT ON VIEW d1.s1.vsec FROM ROLE rown");
        as("UCALL", "RCALL");
        final String refused = refusal("CALL d1.s1.view_rcr()");
        assertTrue(refused.contains("Insufficient privileges to operate on view 'VSEC'"), refused);
    }
}
