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

package dev.frostlake.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two direct (in-process) connections share one engine per URL name, but each carries its OWN
 * session context, as two real Snowflake connections would: a USE on one connection must not
 * change what unqualified names resolve to on the other. Before per-connection scoping, both
 * connections read/wrote the engine's single global current-database — the second connection's
 * USE silently retargeted the first (the cross-test-class "Database does not exist: <dropped
 * clone>" failure mode of the vendor suite).
 */
public class DirectConnectionSessionIsolationTest {

    private Connection a;
    private Connection b;

    @BeforeEach
    public void setUp() throws Exception {
        final String url = "jdbc:frostlake:direct:session_isolation_" + System.nanoTime();
        a = DriverManager.getConnection(url);
        try (final Statement s = a.createStatement()) {
            s.execute("CREATE DATABASE db_one");
            s.execute("CREATE DATABASE db_two");
            s.execute("CREATE TABLE db_one.public.marker (v VARCHAR)");
            s.execute("INSERT INTO db_one.public.marker VALUES ('one')");
            s.execute("CREATE TABLE db_two.public.marker (v VARCHAR)");
            s.execute("INSERT INTO db_two.public.marker VALUES ('two')");
        }
        b = DriverManager.getConnection(url);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (a != null) a.close();
        if (b != null) b.close();
    }

    private String queryMarker(final Connection c) throws Exception {
        try (final Statement s = c.createStatement(); final ResultSet rs = s.executeQuery("SELECT v FROM public.marker")) {
            assertTrue(rs.next());
            return rs.getString(1);
        }
    }

    @Test
    public void testUseOnOneConnectionDoesNotRetargetTheOther() throws Exception {
        try (final Statement sa = a.createStatement()) {
            sa.execute("USE DATABASE db_one");
        }
        try (final Statement sb = b.createStatement()) {
            sb.execute("USE DATABASE db_two");
        }
        // Connection a must still resolve against db_one AFTER b's USE — the old shared-global
        // behavior made this return 'two'.
        assertEquals("one", queryMarker(a));
        assertEquals("two", queryMarker(b));
        assertEquals("DB_ONE", a.getCatalog());
        assertEquals("DB_TWO", b.getCatalog());
    }

    @Test
    public void testSetCatalogIsPerConnection() throws Exception {
        a.setCatalog("db_one");
        b.setCatalog("db_two");
        assertEquals("one", queryMarker(a));
        assertEquals("two", queryMarker(b));
    }

    @Test
    public void testAutoCommitIsPerConnection() throws Exception {
        try (final Statement sa = a.createStatement()) {
            sa.execute("USE DATABASE db_one");
        }
        try (final Statement sb = b.createStatement()) {
            sb.execute("USE DATABASE db_one");
        }
        a.setAutoCommit(false);
        try (final Statement sb = b.createStatement()) {
            // b stays autocommit: its insert is immediately durable and visible to a.
            sb.execute("INSERT INTO public.marker VALUES ('from_b')");
        }
        try (final Statement sa = a.createStatement(); final ResultSet rs = sa.executeQuery(
                "SELECT COUNT(*) FROM public.marker WHERE v = 'from_b'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }
}
