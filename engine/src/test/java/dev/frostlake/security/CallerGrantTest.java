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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caller grants (documented): GRANT CALLER, its INHERITED and ALL forms, REVOKE CALLER and SHOW CALLER GRANTS. A
 * procedure that runs with restricted caller's rights uses a privilege of its caller's on an object only where a
 * caller grant its owner holds names it - USAGE on the database and the schema included - and its caller holds it.
 * Engine only: the live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class CallerGrantTest {

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
        engine.execute("CREATE ROLE other");
        engine.execute("CREATE USER uo");
        engine.execute("CREATE USER uc");
        engine.execute("CREATE USER ux");
        engine.execute("GRANT ROLE ownr TO USER uo");
        engine.execute("GRANT ROLE callr TO USER uc");
        engine.execute("GRANT ROLE other TO USER ux");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE ownr");
        engine.execute("GRANT USAGE, CREATE PROCEDURE ON SCHEMA p.public TO ROLE ownr");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE callr");
        engine.execute("GRANT USAGE ON SCHEMA p.public TO ROLE callr");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE other");
        engine.execute("GRANT USAGE ON SCHEMA p.public TO ROLE other");
        engine.execute("CREATE TABLE callsecret (a INT)");
        engine.execute("INSERT INTO callsecret VALUES (5)");
        engine.execute("GRANT SELECT ON TABLE callsecret TO ROLE callr");
        as("UO", "OWNR");
        engine.execute("CREATE PROCEDURE rc() RETURNS STRING LANGUAGE SQL EXECUTE AS RESTRICTED CALLER AS "
            + "'DECLARE c INT; BEGIN SELECT MAX(a) INTO :c FROM callsecret; RETURN c::string; END'");
        engine.execute("GRANT USAGE ON PROCEDURE rc() TO ROLE callr");
        engine.execute("GRANT USAGE ON PROCEDURE rc() TO ROLE other");
        asSystem();
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void as(final String user, final String role) {
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

    /** The rows of a SHOW CALLER GRANTS, the created_on column left out, each as its cells joined by '|'. */
    private List<String> shown(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> rows = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            final StringBuilder text = new StringBuilder();
            for (int i = 1; i < rs.getColumns().size(); i++) {
                if (i > 1) {
                    text.append('|');
                }
                text.append(row.getValue(i));
            }
            rows.add(text.toString());
        }
        return rows;
    }

    private void grantTheReadOnTheObject() {
        engine.execute("GRANT CALLER USAGE ON DATABASE p TO ROLE ownr");
        engine.execute("GRANT CALLER USAGE ON SCHEMA p.public TO ROLE ownr");
        engine.execute("GRANT CALLER SELECT ON TABLE callsecret TO ROLE ownr");
    }

    @Test
    public void aCallerGrantLetsTheBodyUseWhatItsCallerHolds() {
        as("UC", "CALLR");
        assertTrue(refusal("CALL rc()").contains("must have SELECT granted on TABLE P.PUBLIC.CALLSECRET"),
            refusal("CALL rc()"));
        asSystem();
        grantTheReadOnTheObject();
        as("UC", "CALLR");
        assertEquals("5", call("CALL rc()"));
    }

    @Test
    public void aCallerGrantGivesNothingTheCallerDoesNotHold() {
        grantTheReadOnTheObject();
        as("UX", "OTHER");
        assertTrue(refusal("SELECT MAX(a) FROM callsecret").contains("must have SELECT granted"));
        assertTrue(refusal("CALL rc()").contains("must have SELECT granted on TABLE P.PUBLIC.CALLSECRET"),
            refusal("CALL rc()"));
    }

    @Test
    public void everyPrivilegeTheBodyNeedsMustBeGranted() {
        engine.execute("GRANT CALLER SELECT ON TABLE callsecret TO ROLE ownr");
        as("UC", "CALLR");
        assertTrue(refusal("CALL rc()").contains("does not exist or not authorized"), refusal("CALL rc()"));
    }

    @Test
    public void anInheritedCallerGrantCoversEveryObjectOfItsKindWhereItReaches() {
        engine.execute("GRANT INHERITED CALLER USAGE ON ALL DATABASES IN ACCOUNT TO ROLE ownr");
        engine.execute("GRANT INHERITED CALLER USAGE ON ALL SCHEMAS IN DATABASE p TO ROLE ownr");
        engine.execute("GRANT INHERITED CALLER SELECT ON ALL TABLES IN SCHEMA p.public TO ROLE ownr");
        as("UC", "CALLR");
        assertEquals("5", call("CALL rc()"));
    }

    @Test
    public void allCallerPrivilegesStandForEveryOne() {
        engine.execute("GRANT ALL INHERITED CALLER PRIVILEGES ON ALL DATABASES IN ACCOUNT TO ROLE ownr");
        engine.execute("GRANT ALL CALLER PRIVILEGES ON SCHEMA p.public TO ROLE ownr");
        engine.execute("GRANT ALL CALLER PRIVILEGES ON TABLE callsecret TO ROLE ownr");
        as("UC", "CALLR");
        assertEquals("5", call("CALL rc()"));
    }

    @Test
    public void aRevokedCallerGrantLetsNothingMore() {
        grantTheReadOnTheObject();
        engine.execute("REVOKE CALLER SELECT ON TABLE callsecret FROM ROLE ownr");
        as("UC", "CALLR");
        assertTrue(refusal("CALL rc()").contains("must have SELECT granted"), refusal("CALL rc()"));
        asSystem();
        engine.execute("GRANT ALL CALLER PRIVILEGES ON TABLE callsecret TO ROLE ownr");
        engine.execute("REVOKE ALL CALLER PRIVILEGES ON TABLE callsecret FROM ROLE ownr");
        as("UC", "CALLR");
        assertTrue(refusal("CALL rc()").contains("must have SELECT granted"), refusal("CALL rc()"));
    }

    @Test
    public void showCallerGrantsListsThemByGranteeObjectOrAccount() {
        grantTheReadOnTheObject();
        engine.execute("GRANT INHERITED CALLER INSERT ON ALL TABLES IN SCHEMA p.public TO ROLE other");
        assertEquals(List.of("USAGE|DATABASE|P|false|null|null|null|ROLE|OWNR",
                "USAGE|SCHEMA|P.PUBLIC|false|null|null|null|ROLE|OWNR",
                "SELECT|TABLE|P.PUBLIC.CALLSECRET|false|null|null|null|ROLE|OWNR"),
            shown("SHOW CALLER GRANTS TO ROLE ownr"));
        assertEquals(List.of("INSERT|TABLE|null|true|SCHEMA|P|PUBLIC|ROLE|OTHER",
                "SELECT|TABLE|P.PUBLIC.CALLSECRET|false|null|null|null|ROLE|OWNR"),
            shown("SHOW CALLER GRANTS ON TABLE callsecret"));
        assertEquals(4, shown("SHOW CALLER GRANTS ON ACCOUNT").size());
        assertEquals(List.of("created_on", "privilege", "granted_on", "name", "is_inherited", "inherited_from",
                "inherited_from_database", "inherited_from_schema", "granted_to", "grantee_name"),
            columnNames(engine.executeQuery("SHOW CALLER GRANTS ON ACCOUNT")));
    }

    private static List<String> columnNames(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            names.add(rs.getColumns().get(i).getName());
        }
        return names;
    }

    @Test
    public void grantingACallerGrantTakesManageCallerGrants() {
        as("UO", "OWNR");
        assertTrue(refusal("GRANT CALLER SELECT ON TABLE callsecret TO ROLE ownr").contains("MANAGE CALLER GRANTS"));
        asSystem();
        engine.execute("GRANT MANAGE CALLER GRANTS ON ACCOUNT TO ROLE callr");
        as("UC", "CALLR");
        engine.execute("GRANT CALLER SELECT ON TABLE callsecret TO ROLE ownr");
        assertEquals(1, shown("SHOW CALLER GRANTS TO ROLE ownr").size());
    }

    @Test
    public void inheritedStaysAName() {
        engine.execute("CREATE TABLE inherited (inherited INT)");
        engine.execute("INSERT INTO inherited VALUES (3)");
        assertEquals("3", call("SELECT inherited FROM inherited"));
    }
}
