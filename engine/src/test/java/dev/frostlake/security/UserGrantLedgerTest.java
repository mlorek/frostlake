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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A user's grants are kept as a role's are: a privilege granted WITH GRANT OPTION keeps the option when its table
 * is renamed, a table created under a dropped one's name starts with no grant, a role revoked from the user is gone
 * from SHOW GRANTS TO USER, and a grant survives a snapshot with its option. Engine only: the live account allows
 * no GRANT.
 */
public class UserGrantLedgerTest {

    private final List<DatabaseEngine> engines = new ArrayList<>();
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = track(new DatabaseEngine());
        engine.execute("CREATE DATABASE ul");
        engine.execute("USE SCHEMA ul.PUBLIC");
        engine.execute("CREATE ROLE r1");
        engine.execute("CREATE USER u1");
        engine.execute("CREATE TABLE t (a INT)");
    }

    @AfterEach
    public void tearDown() {
        for (final DatabaseEngine tracked : engines) {
            tracked.shutdown();
        }
    }

    private DatabaseEngine track(final DatabaseEngine tracked) {
        engines.add(tracked);
        return tracked;
    }

    /** The grants SHOW GRANTS TO lists, each as "PRIVILEGE kind name option", in the order listed. */
    private static String grants(final DatabaseEngine from, final String grantee) {
        final ResultSet listed = from.executeQuery("SHOW GRANTS TO " + grantee);
        final int privilege = listed.getColumnIndex("privilege");
        final int kind = listed.getColumnIndex("granted_on");
        final int name = listed.getColumnIndex("name");
        final int option = listed.getColumnIndex("grant_option");
        final StringBuilder out = new StringBuilder();
        for (final Row row : listed.getRows()) {
            out.append(out.length() > 0 ? " ; " : "").append(row.getValue(privilege)).append(' ')
                .append(row.getValue(kind)).append(' ').append(row.getValue(name)).append(' ')
                .append(row.getValue(option));
        }
        return out.toString();
    }

    @Test
    public void aRenameKeepsTheGrantOption() {
        engine.execute("GRANT SELECT ON TABLE t TO ROLE r1 WITH GRANT OPTION");
        engine.execute("GRANT SELECT ON TABLE t TO USER u1 WITH GRANT OPTION");
        engine.execute("ALTER TABLE t RENAME TO t2");
        assertEquals("SELECT TABLE UL.PUBLIC.T2 true", grants(engine, "ROLE r1"));
        assertEquals("SELECT TABLE UL.PUBLIC.T2 true", grants(engine, "USER u1"));
    }

    @Test
    public void aTableUnderADroppedNameStartsWithNoGrant() {
        engine.execute("GRANT SELECT ON TABLE t TO USER u1 WITH GRANT OPTION");
        engine.execute("DROP TABLE t");
        engine.execute("CREATE TABLE t (a INT)");
        assertEquals("", grants(engine, "USER u1"));
        engine.execute("GRANT SELECT ON TABLE t TO USER u1");
        assertEquals("SELECT TABLE UL.PUBLIC.T false", grants(engine, "USER u1"));
    }

    @Test
    public void aRevokedRoleLeavesTheUsersGrants() {
        engine.execute("GRANT ROLE r1 TO USER u1");
        assertEquals("USAGE ROLE R1 false", grants(engine, "USER u1"));
        engine.execute("REVOKE ROLE r1 FROM USER u1");
        assertEquals("", grants(engine, "USER u1"));
        engine.execute("GRANT ROLE r1 TO USER u1");
        assertEquals("USAGE ROLE R1 false", grants(engine, "USER u1"));
    }

    @Test
    public void aGrantSurvivesASnapshot() throws Exception {
        engine.execute("GRANT SELECT ON TABLE t TO USER u1 WITH GRANT OPTION");
        final String dated = String.valueOf(engine.executeQuery("SHOW GRANTS TO USER u1").getRows().get(0).getValue(0));
        final Path saved = Files.createTempDirectory("user_grant_ledger_");
        engine.checkpointStateTo(saved);
        final DatabaseEngine restored = track(new DatabaseEngine());
        restored.restoreStateFrom(saved);
        assertEquals("SELECT TABLE UL.PUBLIC.T true", grants(restored, "USER u1"));
        assertEquals(dated,
            String.valueOf(restored.executeQuery("SHOW GRANTS TO USER u1").getRows().get(0).getValue(0)));
    }
}
