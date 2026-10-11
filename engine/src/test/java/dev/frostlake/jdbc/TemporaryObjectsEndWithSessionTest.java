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

import dev.frostlake.BaseJdbcTest;
import dev.frostlake.LiveSnowflake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A temporary object lives as long as the session that created it (live-verified): once that session is closed, a
 * session opened afterwards finds none of its temporary function, table, view, procedure or stage - {@code
 * Unknown function TMPF.}, {@code Object 'TMPT' does not exist or not authorized.} - lists none of them, and
 * creates temporary objects of the same names for itself. What the closed session created without TEMPORARY is
 * still there.
 */
public class TemporaryObjectsEndWithSessionTest extends BaseJdbcTest {

    /** Another session on the same account or engine as the test's own connection, in the test's schema. */
    private Connection another() throws SQLException {
        final Connection opened = LiveSnowflake.enabled() ? LiveSnowflake.open()
            : new DirectConnection(sharedEngine);
        try (Statement use = opened.createStatement()) {
            use.execute("USE SCHEMA test_db.public");
        }
        return opened;
    }

    private static String cell(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getString(1);
        }
    }

    private static int rows(final Statement on, final String sql) throws SQLException {
        int count = 0;
        try (ResultSet rs = on.executeQuery(sql)) {
            while (rs.next()) {
                count++;
            }
        }
        return count;
    }

    private String refusal(final Statement on, final String sql) {
        return assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                on.execute(sql);
            }
        }, sql).getMessage();
    }

    private static void createTemporaryObjects(final Statement on, final String answer) throws SQLException {
        on.execute("CREATE TEMPORARY FUNCTION tmpf() RETURNS NUMBER AS '" + answer + "'");
        on.execute("CREATE TEMPORARY TABLE tmpt (x NUMBER)");
        on.execute("INSERT INTO tmpt VALUES (" + answer + ")");
        on.execute("CREATE TEMPORARY VIEW tmpv AS SELECT " + answer + " AS c");
        on.execute("CREATE TEMPORARY PROCEDURE tmpp() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN " + answer
            + "; END; $$");
        on.execute("CREATE TEMPORARY STAGE tmps");
    }

    @Test
    public void aClosedSessionsTemporaryObjectsAreGone() throws SQLException {
        try (Connection creator = another(); Statement on = creator.createStatement()) {
            createTemporaryObjects(on, "1");
            on.execute("CREATE TABLE kept (z NUMBER)");
            on.execute("INSERT INTO kept VALUES (4)");
            assertEquals("1", cell(on, "SELECT tmpf()"));
            assertEquals("1", cell(on, "SELECT x FROM tmpt"));
            assertEquals("1", cell(on, "SELECT c FROM tmpv"));
            assertEquals("1", cell(on, "CALL tmpp()"));
            assertEquals(1, rows(on, "SHOW STAGES LIKE 'TMPS' IN SCHEMA test_db.public"));
        }
        try (Connection later = another(); Statement on = later.createStatement()) {
            assertTrue(refusal(on, "SELECT tmpf()").contains("Unknown function TMPF."));
            assertTrue(refusal(on, "SELECT COUNT(*) FROM tmpt")
                .contains("Object 'TMPT' does not exist or not authorized."));
            assertTrue(refusal(on, "SELECT c FROM tmpv").contains("Object 'TMPV' does not exist or not authorized."));
            assertTrue(refusal(on, "CALL tmpp()").contains("Unknown function TMPP."));
            assertEquals(0, rows(on, "SHOW TABLES LIKE 'TMPT' IN SCHEMA test_db.public"));
            assertEquals(0, rows(on, "SHOW VIEWS LIKE 'TMPV' IN SCHEMA test_db.public"));
            assertEquals(0, rows(on, "SHOW USER FUNCTIONS LIKE 'TMPF' IN SCHEMA test_db.public"));
            assertEquals(0, rows(on, "SHOW PROCEDURES LIKE 'TMPP' IN SCHEMA test_db.public"));
            assertEquals(0, rows(on, "SHOW STAGES LIKE 'TMPS' IN SCHEMA test_db.public"));
            // What was created without TEMPORARY is the schema's, not the session's.
            assertEquals("4", cell(on, "SELECT z FROM kept"));
            // The names are free: this session's own temporary objects answer for themselves.
            createTemporaryObjects(on, "2");
            assertEquals("2", cell(on, "SELECT tmpf()"));
            assertEquals("2", cell(on, "SELECT x FROM tmpt"));
            assertEquals("2", cell(on, "SELECT c FROM tmpv"));
            assertEquals("2", cell(on, "CALL tmpp()"));
        }
        // The test's own session, older than both, finds none either.
        assertTrue(refusal(statement, "SELECT tmpf()").contains("Unknown function TMPF."));
        assertFalse(statement.executeQuery("SHOW TABLES LIKE 'TMPT' IN SCHEMA test_db.public").next());
    }
}
