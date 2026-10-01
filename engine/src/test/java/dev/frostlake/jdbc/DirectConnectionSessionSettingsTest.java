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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import dev.frostlake.LiveSnowflake;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Two {@code jdbc:frostlake:direct} connections to the same name are two sessions: a connection's ALTER SESSION
 * parameters and SET variables are its own, so the other still reads the default TIMEZONE and QUERY_TAG, refuses
 * {@code $v}, and an UNSET in one resets nothing in the other. Embedded, the connections share one named engine;
 * live, they are separate connections. Live-verified.
 */
public class DirectConnectionSessionSettingsTest {

    private Connection[] connections;

    @BeforeEach
    public void open() throws SQLException {
        if (LiveSnowflake.enabled()) {
            connections = new Connection[] {LiveSnowflake.open(), LiveSnowflake.open(), LiveSnowflake.open()};
        } else {
            final String url = "jdbc:frostlake:direct:settings_" + UUID.randomUUID().toString().replace("-", "");
            connections = new Connection[] {DriverManager.getConnection(url), DriverManager.getConnection(url),
                DriverManager.getConnection(url)};
        }
    }

    @AfterEach
    public void close() throws SQLException {
        for (final Connection connection : connections) {
            connection.close();
        }
    }

    private void run(final int session, final String sql) throws SQLException {
        try (Statement statement = connections[session].createStatement()) {
            statement.execute(sql);
        }
    }

    /** A column of a one-row query's result, as text. */
    private String cell(final int session, final String sql, final String column) throws SQLException {
        try (Statement statement = connections[session].createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return String.valueOf(rs.getString(column));
        }
    }

    /** Whether the session's parameter reads its default. */
    private boolean atDefault(final int session, final String name) throws SQLException {
        final String show = "SHOW PARAMETERS LIKE '" + name + "' IN SESSION";
        return cell(session, show, "value").equals(cell(session, show, "default"));
    }

    private String refusal(final int session, final String sql) {
        try {
            run(session, sql);
        } catch (final SQLException | RuntimeException refused) {
            return refused.getMessage();
        }
        fail("expected a refusal: " + sql);
        return null;
    }

    @Test
    public void aParameterIsTheConnectionsOwn() throws SQLException {
        run(0, "ALTER SESSION SET TIMEZONE = 'America/New_York'");
        assertEquals("America/New_York", cell(0, "SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION", "value"));
        assertTrue(atDefault(1, "TIMEZONE"));
        run(0, "ALTER SESSION SET QUERY_TAG = 'from_a'");
        assertEquals("from_a", cell(0, "SHOW PARAMETERS LIKE 'QUERY_TAG' IN SESSION", "value"));
        assertTrue(atDefault(1, "QUERY_TAG"));
        run(0, "ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_LTZ'");
        assertEquals("TIMESTAMP_LTZ(9)[SB16]", cell(0, "SELECT SYSTEM$TYPEOF('2026-08-13 01:02:03'::TIMESTAMP) AS t", "t"));
        assertEquals("TIMESTAMP_NTZ(9)[SB16]", cell(1, "SELECT SYSTEM$TYPEOF('2026-08-13 01:02:03'::TIMESTAMP) AS t", "t"));
        assertTrue(atDefault(2, "TIMEZONE"));
    }

    @Test
    public void anUnsetResetsOnlyItsOwnConnection() throws SQLException {
        run(0, "ALTER SESSION SET TIMEZONE = 'America/New_York'");
        run(1, "ALTER SESSION SET TIMEZONE = 'Europe/London'");
        run(1, "ALTER SESSION UNSET TIMEZONE");
        assertEquals("America/New_York", cell(0, "SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION", "value"));
        assertTrue(atDefault(1, "TIMEZONE"));
    }

    @Test
    public void aVariableIsTheConnectionsOwn() throws SQLException {
        run(0, "SET v = 1");
        assertEquals("1", cell(0, "SELECT $v AS v", "v"));
        assertEquals("SQL compilation error: error line 1 at position 7\nSession variable '$V' does not exist",
            refusal(1, "SELECT $v AS v"));
        run(1, "SET v = 2");
        assertEquals("1", cell(0, "SELECT $v AS v", "v"));
        assertEquals("2", cell(1, "SELECT $v AS v", "v"));
    }
}
