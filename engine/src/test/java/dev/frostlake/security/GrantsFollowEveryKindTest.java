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
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Privileges stay on their object through a rename for every kind that can be renamed, not only tables, views,
 * schemas and databases. A routine is granted one overload at a time. A CREATE asks for its privilege on the schema
 * the object lands in, and a read of a view asks for SELECT on the view.
 */
public class GrantsFollowEveryKindTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE g");
        engine.execute("CREATE SCHEMA g.other");
        engine.execute("USE SCHEMA g.PUBLIC");
        engine.execute("CREATE ROLE reader_role");
        engine.execute("CREATE USER reader");
        engine.execute("GRANT ROLE reader_role TO USER reader");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** What SHOW GRANTS TO ROLE lists for the reader's role, as "PRIVILEGE KIND NAME". */
    private Set<String> readerHolds() {
        final Set<String> held = new TreeSet<>();
        for (final Row row : engine.executeQuery("SHOW GRANTS TO ROLE reader_role").getRows()) {
            held.add(row.getValue(1) + " " + row.getValue(2) + " " + row.getValue(3));
        }
        return held;
    }

    /** The privileges SHOW GRANTS ON lists for the reader's role, as "PRIVILEGE name". */
    private String readerGrantsOn(final String kind, final String name) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SHOW GRANTS ON " + kind + " " + name).getRows()) {
            if ("READER_ROLE".equals(String.valueOf(row.getValue(5)))) {
                out.append(out.length() > 0 ? " ; " : "").append(row.getValue(1)).append(' ').append(row.getValue(3));
            }
        }
        return out.toString();
    }

    /** Whether the reader may run the statement: run as the reader, then back to the administrator. */
    private boolean readerMay(final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("reader");
        engine.setCurrentRole("reader_role");
        try {
            engine.execute(sql);
            return true;
        } catch (final SecurityException denied) {
            return false;
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    @Test
    public void everyRenamedKindKeepsItsGrants() {
        engine.execute("CREATE SEQUENCE sq");
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> v");
        engine.execute("CREATE ROW ACCESS POLICY rp AS (v STRING) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE TAG tg");
        engine.execute("GRANT USAGE ON SEQUENCE sq TO ROLE reader_role");
        engine.execute("GRANT READ ON STAGE st TO ROLE reader_role");
        engine.execute("GRANT USAGE ON FILE FORMAT ff TO ROLE reader_role");
        engine.execute("GRANT APPLY ON MASKING POLICY mp TO ROLE reader_role");
        engine.execute("GRANT APPLY ON ROW ACCESS POLICY rp TO ROLE reader_role");
        engine.execute("GRANT APPLY ON TAG tg TO ROLE reader_role");

        engine.execute("ALTER SEQUENCE sq RENAME TO sq2");
        engine.execute("ALTER STAGE st RENAME TO st2");
        engine.execute("ALTER FILE FORMAT ff RENAME TO ff2");
        engine.execute("ALTER MASKING POLICY mp RENAME TO mp2");
        engine.execute("ALTER ROW ACCESS POLICY rp RENAME TO g.other.rp2");
        engine.execute("ALTER TAG tg RENAME TO tg2");

        final Set<String> expected = new TreeSet<>();
        expected.add("USAGE SEQUENCE G.PUBLIC.SQ2");
        expected.add("READ STAGE G.PUBLIC.ST2");
        expected.add("USAGE FILE_FORMAT G.PUBLIC.FF2");
        expected.add("APPLY MASKING_POLICY G.PUBLIC.MP2");
        expected.add("APPLY ROW_ACCESS_POLICY G.OTHER.RP2");
        expected.add("APPLY TAG G.PUBLIC.TG2");
        assertEquals(expected, readerHolds());

        engine.execute("CREATE SEQUENCE sq");
        assertEquals("", readerGrantsOn("SEQUENCE", "sq"), "a new SQ under the old name is not granted");
        assertEquals("USAGE G.PUBLIC.SQ2", readerGrantsOn("SEQUENCE", "sq2"));
    }

    @Test
    public void aRoutineIsGrantedOneOverloadAtATime() {
        engine.execute("CREATE FUNCTION f(x NUMBER) RETURNS NUMBER AS 'x'");
        engine.execute("CREATE FUNCTION f(x VARCHAR) RETURNS VARCHAR AS 'x'");
        engine.execute("GRANT USAGE ON FUNCTION f(INT) TO ROLE reader_role");
        assertEquals("USAGE G.PUBLIC.F(NUMBER)", readerGrantsOn("FUNCTION", "f(NUMBER)"));
        assertEquals("", readerGrantsOn("FUNCTION", "f(VARCHAR)"), "the other overload is another object");

        engine.execute("CREATE FUNCTION f(x DATE) RETURNS DATE AS 'x'");
        assertEquals("", readerGrantsOn("FUNCTION", "f(DATE)"), "a new overload inherits nothing");
        assertEquals("USAGE G.PUBLIC.F(NUMBER)", readerGrantsOn("FUNCTION", "f(NUMBER)"), "nor clears anything");

        engine.execute("ALTER FUNCTION f(NUMBER) RENAME TO g");
        assertEquals("USAGE G.PUBLIC.G(NUMBER)", readerGrantsOn("FUNCTION", "g(NUMBER)"));
        engine.execute("CREATE FUNCTION f(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        assertEquals("", readerGrantsOn("FUNCTION", "f(NUMBER)"), "a new F(NUMBER) is not the renamed one");

        engine.execute("DROP FUNCTION g(NUMBER)");
        engine.execute("CREATE FUNCTION g(x NUMBER) RETURNS NUMBER AS 'x'");
        assertEquals("", readerGrantsOn("FUNCTION", "g(NUMBER)"), "a re-created overload starts clean");

        engine.execute("CREATE PROCEDURE pr() RETURNS VARCHAR LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("GRANT USAGE ON PROCEDURE pr() TO ROLE reader_role");
        engine.execute("ALTER PROCEDURE pr() RENAME TO pr2");
        assertTrue(readerHolds().contains("USAGE PROCEDURE G.PUBLIC.PR2()"), readerHolds().toString());
        engine.execute("REVOKE USAGE ON PROCEDURE pr2() FROM ROLE reader_role");
        assertFalse(readerHolds().contains("USAGE PROCEDURE G.PUBLIC.PR2()"), readerHolds().toString());
    }

    @Test
    public void aRoutineGrantRecordedWithoutASignatureReachesEveryOverloadAfterARestore() throws Exception {
        engine.execute("CREATE FUNCTION f(x NUMBER) RETURNS NUMBER AS 'x'");
        engine.execute("CREATE FUNCTION f(x VARCHAR) RETURNS VARCHAR AS 'x'");
        // The way an older engine recorded it: one name for every overload.
        engine.getCatalog().getRole("READER_ROLE").grantPrivilege("FUNCTION", "G.PUBLIC.F", Privilege.USAGE);

        final DatabaseEngine restored = engine.cloneInstance();
        try {
            final Set<String> held = new TreeSet<>();
            for (final Row row : restored.executeQuery("SHOW GRANTS TO ROLE reader_role").getRows()) {
                held.add(row.getValue(1) + " " + row.getValue(2) + " " + row.getValue(3));
            }
            final Set<String> expected = new TreeSet<>();
            expected.add("USAGE FUNCTION G.PUBLIC.F(NUMBER)");
            expected.add("USAGE FUNCTION G.PUBLIC.F(VARCHAR)");
            assertEquals(expected, held);
        } finally {
            restored.shutdown();
        }
    }

    @Test
    public void aCreateAsksForItsPrivilegeOnTheSchemaItLandsIn() {
        engine.execute("CREATE DATABASE g2");
        engine.execute("USE SCHEMA g.PUBLIC");
        engine.execute("GRANT CREATE TABLE ON SCHEMA g2.PUBLIC TO ROLE reader_role");
        assertTrue(readerMay("CREATE TABLE g2.PUBLIC.made (a INT)"), "granted on G2.PUBLIC");
        assertFalse(readerMay("CREATE TABLE g.PUBLIC.denied (a INT)"), "nothing granted on G.PUBLIC");
        assertFalse(readerMay("CREATE TABLE denied (a INT)"), "nor through the session's schema");
    }

    @Test
    public void aRoleGrantedSelectOnAViewMayReadIt() {
        // A role reaches an object of a schema only with USAGE on its database and its schema.
        engine.execute("GRANT USAGE ON DATABASE g TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SCHEMA g.PUBLIC TO ROLE reader_role");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE VIEW v AS SELECT a FROM t");
        assertFalse(readerMay("SELECT * FROM v"));
        engine.execute("GRANT SELECT ON VIEW v TO ROLE reader_role");
        assertTrue(readerMay("SELECT * FROM v"));
        assertFalse(readerMay("SELECT * FROM t"), "the view's grant is not the table's");
    }
}
