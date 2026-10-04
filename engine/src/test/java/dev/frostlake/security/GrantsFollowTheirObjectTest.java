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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A privilege is granted on an OBJECT, not on the spelling of its name. Grants were recorded and checked under the
 * name as written, so the grant reached the table only when a query spelled the name the same way. An unqualified
 * grant also reached same-named tables in other schemas. A rename lost the grants, and a new object under a dropped
 * one's name inherited them. COPY GRANTS copied nothing, and a database clone gave its tables no grants at all.
 */
public class GrantsFollowTheirObjectTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE g");
        engine.execute("USE SCHEMA g.PUBLIC");
        engine.execute("CREATE ROLE reader_role");
        engine.execute("CREATE USER reader");
        engine.execute("GRANT ROLE reader_role TO USER reader");
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** Whether the reader may run the query: run as the reader, then back to the administrator. */
    private boolean readerMay(final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("reader");
        engine.setCurrentRole("reader_role");
        try {
            engine.executeQuery(sql);
            return true;
        } catch (final SecurityException denied) {
            return false;
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
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

    @Test
    public void aGrantReachesItsTableHoweverAQuerySpellsItAndNoSameNamedTableElsewhere() {
        engine.execute("GRANT SELECT ON TABLE t TO ROLE reader_role");
        assertTrue(readerMay("SELECT * FROM t"));
        assertTrue(readerMay("SELECT * FROM PUBLIC.t"));
        assertTrue(readerMay("SELECT * FROM g.PUBLIC.t"));

        engine.execute("CREATE SCHEMA other");
        engine.execute("CREATE TABLE g.other.t (a INT)");
        engine.execute("USE SCHEMA g.PUBLIC");
        assertFalse(readerMay("SELECT * FROM g.other.t"), "another schema's T is another table");
        assertEquals("", readerGrantsOn("TABLE", "g.other.t"));
        assertEquals("SELECT G.PUBLIC.T", readerGrantsOn("TABLE", "t"));
    }

    @Test
    public void aRenamedMovedOrSwappedTableKeepsItsGrants() {
        engine.execute("GRANT SELECT ON TABLE t TO ROLE reader_role");
        engine.execute("ALTER TABLE t RENAME TO t2");
        assertTrue(readerMay("SELECT * FROM t2"));
        engine.execute("CREATE TABLE t (a INT)");
        assertFalse(readerMay("SELECT * FROM t"), "a new T under the old name is not granted");

        engine.execute("CREATE SCHEMA moved");
        engine.execute("USE SCHEMA g.PUBLIC");
        engine.execute("ALTER TABLE t2 RENAME TO g.moved.t3");
        assertTrue(readerMay("SELECT * FROM g.moved.t3"));

        engine.execute("CREATE TABLE sa (v INT)");
        engine.execute("CREATE TABLE sb (v INT)");
        engine.execute("GRANT SELECT ON TABLE sa TO ROLE reader_role");
        engine.execute("ALTER TABLE sa SWAP WITH sb");
        assertTrue(readerMay("SELECT * FROM sb"), "the granted table is now called SB");
        assertFalse(readerMay("SELECT * FROM sa"));
    }

    @Test
    public void aNewObjectUnderADroppedOnesNameStartsWithoutItsGrantsAndUndropBringsThemBack() {
        engine.execute("GRANT SELECT ON TABLE t TO ROLE reader_role");
        engine.execute("DROP TABLE t");
        engine.execute("UNDROP TABLE t");
        assertTrue(readerMay("SELECT * FROM t"), "the undropped table is the granted one");

        engine.execute("DROP TABLE t");
        engine.execute("CREATE TABLE t (a INT)");
        assertFalse(readerMay("SELECT * FROM t"));
    }

    @Test
    public void createOrReplaceKeepsTheGrantsOnlyWithCopyGrants() {
        engine.execute("GRANT SELECT ON TABLE t TO ROLE reader_role");
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        assertFalse(readerMay("SELECT * FROM t"));

        engine.execute("GRANT SELECT ON TABLE t TO ROLE reader_role");
        engine.execute("CREATE OR REPLACE TABLE t (a INT) COPY GRANTS");
        assertTrue(readerMay("SELECT * FROM t"));

        engine.execute("CREATE TABLE t_copied CLONE t COPY GRANTS");
        engine.execute("CREATE TABLE t_plain CLONE t");
        assertTrue(readerMay("SELECT * FROM t_copied"));
        assertFalse(readerMay("SELECT * FROM t_plain"));
    }

    @Test
    public void aReplacedViewKeepsItsGrantsAsOrAlterAndCopyGrantsSay() {
        engine.execute("CREATE VIEW v AS SELECT a FROM t");
        engine.execute("GRANT SELECT ON VIEW v TO ROLE reader_role");
        engine.execute("CREATE OR REPLACE VIEW v COPY GRANTS AS SELECT a FROM t");
        assertEquals("SELECT G.PUBLIC.V", readerGrantsOn("VIEW", "v"));
        try {
            engine.execute("CREATE OR REPLACE VIEW v AS SELECT no_such_column FROM t");
        } catch (final RuntimeException refused) {
            // the failed replacement leaves the view, and its grants, standing
        }
        assertEquals("SELECT G.PUBLIC.V", readerGrantsOn("VIEW", "v"));
        engine.execute("CREATE OR REPLACE VIEW v AS SELECT a FROM t");
        assertEquals("", readerGrantsOn("VIEW", "v"));
    }

    @Test
    public void aDatabaseOrSchemaCloneGrantsItsMembersButNotItself() {
        engine.execute("GRANT SELECT ON TABLE t TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SCHEMA PUBLIC TO ROLE reader_role");
        engine.execute("GRANT USAGE ON DATABASE g TO ROLE reader_role");
        engine.execute("CREATE DATABASE g2 CLONE g");
        assertEquals("SELECT G2.PUBLIC.T", readerGrantsOn("TABLE", "g2.PUBLIC.t"));
        assertEquals("USAGE G2.PUBLIC", readerGrantsOn("SCHEMA", "g2.PUBLIC"));
        assertEquals("", readerGrantsOn("DATABASE", "g2"));

        engine.execute("CREATE SCHEMA g.PUBLIC_COPY CLONE g.PUBLIC");
        assertEquals("SELECT G.PUBLIC_COPY.T", readerGrantsOn("TABLE", "g.PUBLIC_COPY.t"));
        assertEquals("", readerGrantsOn("SCHEMA", "g.PUBLIC_COPY"));

        engine.execute("ALTER DATABASE g2 RENAME TO g3");
        assertEquals("SELECT G3.PUBLIC.T", readerGrantsOn("TABLE", "g3.PUBLIC.t"));
        assertEquals("USAGE G3.PUBLIC", readerGrantsOn("SCHEMA", "g3.PUBLIC"));
    }

    @Test
    public void aRestoredEngineCompletesTheNamesAnOlderSnapshotRecorded() throws Exception {
        engine.getCatalog().getRole("READER_ROLE").grantPrivilege("TABLE", "T", Privilege.SELECT, "SYSADMIN");
        final DatabaseEngine restored = engine.cloneInstance();
        try {
            assertTrue(restored.getCatalog().getRole("READER_ROLE").hasPrivilege("TABLE", "G.PUBLIC.T",
                Privilege.SELECT), "the one T there is completed in full");
            assertFalse(restored.getCatalog().getRole("READER_ROLE").hasPrivilege("TABLE", "T", Privilege.SELECT));
        } finally {
            restored.shutdown();
        }
    }
}
