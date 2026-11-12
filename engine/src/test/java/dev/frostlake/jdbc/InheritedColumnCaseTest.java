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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.DatabaseEngine;
import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CLIENT_RESULT_COLUMN_CASE_INSENSITIVE set on the account or on the user is the session's value as the session's own
 * setting is (documented: a session parameter set at the account or user level is the default of every session
 * there), so a connection opened after it matches a column label ignoring case. Both transports read only a value the
 * session set itself. Engine only: the live account allows no ALTER ACCOUNT.
 */
public class InheritedColumnCaseTest extends BaseDatabaseTest {

    private static final String PARAMETER = "CLIENT_RESULT_COLUMN_CASE_INSENSITIVE";

    private static String execute(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            if (!statement.execute(sql)) {
                return "";
            }
            try (ResultSet rs = statement.getResultSet()) {
                return rs.next() ? rs.getString(1) : "";
            }
        }
    }

    /** What getInt answers for the label {@code x} over the column {@code X}. */
    private static String lowerCaseLabel(final Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 7 AS X")) {
            rs.next();
            try {
                return String.valueOf(rs.getInt("x"));
            } catch (final SQLException refused) {
                return refused.getMessage();
            }
        }
    }

    /** Sets the parameter at a level through one connection, then reads a label through a new one. */
    private static String afterSettingOn(final String url, final String level) throws SQLException {
        final String target;
        try (Connection setter = DriverManager.getConnection(url)) {
            target = "ACCOUNT".equals(level) ? "ACCOUNT"
                : "USER \"" + execute(setter, "SELECT CURRENT_USER()") + "\"";
            execute(setter, "ALTER " + target + " SET " + PARAMETER + " = TRUE");
        }
        try (Connection reader = DriverManager.getConnection(url)) {
            return lowerCaseLabel(reader);
        } finally {
            try (Connection setter = DriverManager.getConnection(url)) {
                execute(setter, "ALTER " + target + " UNSET " + PARAMETER);
            }
        }
    }

    @Test
    public void anAccountOrUserSettingMakesTheDirectDriverIgnoreCase() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), "alters the account");
        final String url = "jdbc:frostlake:direct:inherited_column_case";
        try {
            try (Connection fresh = DriverManager.getConnection(url)) {
                assertEquals("Column not found: x", lowerCaseLabel(fresh));
            }
            assertEquals("7", afterSettingOn(url, "ACCOUNT"));
            assertEquals("7", afterSettingOn(url, "USER"));
        } finally {
            final DatabaseEngine engine = DatabaseDriver.unregisterDirectEngine("inherited_column_case");
            if (engine != null) {
                engine.shutdown();
            }
        }
    }

    @Test
    public void anAccountOrUserSettingMakesTheHttpDriverIgnoreCase() throws SQLException, IOException {
        Assumptions.assumeFalse(isLiveSnowflake(), "alters the account");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try {
            final String url = "jdbc:frostlake://localhost:" + port;
            try (Connection fresh = DriverManager.getConnection(url)) {
                assertEquals("Column not found: x", lowerCaseLabel(fresh));
            }
            assertEquals("7", afterSettingOn(url, "ACCOUNT"));
            assertEquals("7", afterSettingOn(url, "USER"));
        } finally {
            server.stop();
        }
    }
}
