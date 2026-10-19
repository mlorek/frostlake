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

package dev.frostlake.session;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each session of one engine keeps its own roles: a USE ROLE or a USE SECONDARY ROLES in one session leaves every
 * other session's CURRENT_ROLE() and CURRENT_SECONDARY_ROLES() as they were, and what a session creates is owned by
 * its own role. The same holds for two direct JDBC connections to one engine.
 */
public class SessionRolesTest {

    private ConcurrentDatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new ConcurrentDatabaseEngine();
        final SessionContext setup = engine.createSession();
        one(setup, "CREATE OR REPLACE DATABASE srdb");
        engine.removeSession(setup.getSessionId());
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private Object one(final SessionContext session, final String sql) {
        final ExecutionResult result = engine.execute(sql, session);
        if (!result.isSuccess()) {
            throw new IllegalStateException(sql + " failed: " + result.getErrorMessage());
        }
        final List<ResultSet> sets = result.getResultSets();
        if (sets.isEmpty()) {
            return null;
        }
        final ResultSet rs = sets.get(sets.size() - 1);
        return rs.next() ? rs.getValue(0) : null;
    }

    @Test
    public void useRoleInOneSessionLeavesTheOthersRole() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        final Object before = one(b, "SELECT CURRENT_ROLE()");
        one(a, "USE ROLE ACCOUNTADMIN");
        assertEquals("ACCOUNTADMIN", one(a, "SELECT CURRENT_ROLE()"));
        assertEquals(before, one(b, "SELECT CURRENT_ROLE()"));
        one(b, "USE ROLE PUBLIC");
        assertEquals("ACCOUNTADMIN", one(a, "SELECT CURRENT_ROLE()"));
        assertEquals("PUBLIC", one(b, "SELECT CURRENT_ROLE()"));
    }

    @Test
    public void whatASessionCreatesIsOwnedByItsOwnRole() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        one(a, "USE ROLE ACCOUNTADMIN");
        one(b, "USE SCHEMA srdb.PUBLIC");
        final Object roleOfB = one(b, "SELECT CURRENT_ROLE()");
        one(b, "CREATE TABLE b_owned (x INT)");
        assertEquals(roleOfB, one(b, "SELECT table_owner FROM INFORMATION_SCHEMA.TABLES"
            + " WHERE table_name = 'B_OWNED'"));
    }

    @Test
    public void secondaryRolesAreTheSessionsOwn() {
        final SessionContext a = engine.createSession();
        final SessionContext b = engine.createSession();
        final Object before = one(b, "SELECT CURRENT_SECONDARY_ROLES()");
        one(a, "USE SECONDARY ROLES ALL");
        assertEquals(before, one(b, "SELECT CURRENT_SECONDARY_ROLES()"));
        assertTrue(String.valueOf(one(a, "SELECT CURRENT_SECONDARY_ROLES()")).contains("ALL"));
    }

    @Test
    public void twoDirectConnectionsKeepTheirOwnRoles() throws SQLException {
        final String url = "jdbc:frostlake:direct:roles_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c1 = DriverManager.getConnection(url); Connection c2 = DriverManager.getConnection(url);
             Statement s1 = c1.createStatement(); Statement s2 = c2.createStatement()) {
            final java.sql.ResultSet before = s2.executeQuery("SELECT CURRENT_ROLE()");
            before.next();
            final String roleOfC2 = before.getString(1);
            s1.execute("USE ROLE ACCOUNTADMIN");
            final java.sql.ResultSet after = s2.executeQuery("SELECT CURRENT_ROLE()");
            after.next();
            assertEquals(roleOfC2, after.getString(1));
            final java.sql.ResultSet own = s1.executeQuery("SELECT CURRENT_ROLE()");
            own.next();
            assertEquals("ACCOUNTADMIN", own.getString(1));
        }
    }
}
