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
 * SHOW lists only the objects the role owns or holds a privilege on, unless it holds MANAGE GRANTS, which sees every
 * object of the account (documented); DESCRIBE and GET_DDL need what SHOW needs, and answer an object the role does
 * not see as one that does not exist. The account is never asked for these: its sessions run no role or grant
 * statement here.
 */
public class ShowAndDescribeVisibilityTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("CREATE ROLE reader_role");
        // The user puts no secondary role in use, so a refusal names the primary role alone.
        engine.execute("CREATE USER reader DEFAULT_SECONDARY_ROLES = ()");
        engine.execute("GRANT ROLE reader_role TO USER reader");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SCHEMA p.PUBLIC TO ROLE reader_role");
        engine.execute("CREATE TABLE secret (x INT, s VARCHAR)");
        engine.execute("INSERT INTO secret VALUES (1, 'top'), (2, 'secret')");
        engine.execute("CREATE TABLE pub (x INT)");
        engine.execute("GRANT SELECT ON TABLE pub TO ROLE reader_role");
        engine.execute("CREATE VIEW v_secret AS SELECT * FROM secret");
        engine.execute("CREATE SECURE VIEW v_sec AS SELECT * FROM secret");
        engine.execute("CREATE SEQUENCE seq START 100");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The reader's answer — rows as "a, b | c, d", or the refusal — then back to the administrator. */
    private String asReader(final String role, final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("reader");
        engine.setCurrentRole(role);
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    private String asReader(final String sql) {
        return asReader("reader_role", sql);
    }

    /** The names a SHOW lists, in its order. */
    private String names(final String role, final String show) {
        return asReader(role, show + " ->> SELECT \"name\" FROM $1");
    }

    @Test
    public void showListsOnlyWhatTheRoleHoldsAPrivilegeOn() {
        assertEquals("PUB", names("reader_role", "SHOW TABLES"));
        assertEquals("", names("reader_role", "SHOW VIEWS"));
        assertEquals("", names("reader_role", "SHOW SEQUENCES"));
        assertEquals("PUB", names("reader_role", "SHOW OBJECTS"));
        assertEquals("PUB", asReader("SHOW COLUMNS IN SCHEMA p.PUBLIC ->> SELECT \"table_name\" FROM $1"));
        assertEquals("PUB", names("reader_role", "SHOW TABLES IN DATABASE p"));
        engine.execute("GRANT SELECT ON VIEW v_secret TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SEQUENCE seq TO ROLE reader_role");
        assertEquals("V_SECRET", names("reader_role", "SHOW VIEWS"));
        assertEquals("SEQ", names("reader_role", "SHOW SEQUENCES"));
        assertEquals("PUB | SECRET | V_SEC | V_SECRET", names(adminRole, "SHOW OBJECTS"));
    }

    @Test
    public void manageGrantsSeesEveryObject() {
        engine.execute("GRANT ROLE SECURITYADMIN TO USER reader");
        assertEquals("PUB | SECRET", names("SECURITYADMIN", "SHOW TABLES"));
        assertEquals("V_SEC | V_SECRET", names("SECURITYADMIN", "SHOW VIEWS"));
        assertEquals("X, NUMBER(38,0) | S, VARCHAR(16777216)",
            asReader("SECURITYADMIN", "DESCRIBE TABLE secret ->> SELECT \"name\", \"type\" FROM $1"));
        engine.execute("REVOKE ROLE SECURITYADMIN FROM USER reader");
        engine.execute("CREATE ROLE grants_role");
        engine.execute("GRANT ROLE grants_role TO USER reader");
        engine.execute("GRANT MANAGE GRANTS ON ACCOUNT TO ROLE grants_role");
        assertEquals("PUB | SECRET", names("grants_role", "SHOW TABLES"));
    }

    /** The refusal of a name that reaches nothing the reader sees, as a missing name's reads. */
    private static String missing(final String kind, final String name, final String grantKind) {
        return "SQL compilation error:|" + kind + " '" + name + "' does not exist or not authorized. Your primary role "
            + "READER_ROLE must have at least one privilege granted on " + grantKind + " " + name + ".";
    }

    @Test
    public void describeAndGetDdlAnswerAnUnseenObjectAsMissing() {
        assertEquals("X, NUMBER(38,0)", asReader("DESCRIBE TABLE pub ->> SELECT \"name\", \"type\" FROM $1"));
        assertEquals(missing("Table", "NOSUCH", "TABLE"), asReader("DESCRIBE TABLE nosuch"));
        assertEquals(missing("Table", "SECRET", "TABLE"), asReader("DESCRIBE TABLE secret"));
        assertEquals(missing("Table", "P.PUBLIC.SECRET", "TABLE"), asReader("DESCRIBE TABLE p.PUBLIC.secret"));
        assertEquals(missing("View", "V_SEC", "TABLE"), asReader("DESCRIBE VIEW v_sec"));
        assertEquals(missing("Table", "SECRET", "TABLE"), asReader("SHOW COLUMNS IN TABLE secret"));
        assertEquals(missing("Table", "SECRET", "TABLE"), asReader("SELECT GET_DDL('TABLE', 'secret')"));
        assertEquals(missing("View", "V_SECRET", "TABLE"), asReader("SELECT GET_DDL('VIEW', 'v_secret')"));
        assertEquals(missing("Sequence", "SEQ", "SEQUENCE"), asReader("SELECT GET_DDL('SEQUENCE', 'seq')"));
        assertEquals("create or replace TABLE PUB (|\tX NUMBER(38,0)|);",
            asReader("SELECT GET_DDL('TABLE', 'pub')").replace('\n', '|'));
    }

    @Test
    public void aSecureViewSeenThroughAGrantIsDescribedButNotSpelled() {
        engine.execute("GRANT SELECT ON VIEW v_sec TO ROLE reader_role");
        assertEquals("X | S", asReader("DESCRIBE VIEW v_sec ->> SELECT \"name\" FROM $1"));
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            asReader("SELECT GET_DDL('VIEW', 'v_sec')"));
    }
}
