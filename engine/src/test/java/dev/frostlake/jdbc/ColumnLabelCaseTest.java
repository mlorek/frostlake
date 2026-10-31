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
import dev.frostlake.LiveSnowflake;
import dev.frostlake.http.DatabaseHttpServer;

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
 * A JDBC connection reads a column label by its exact spelling until its session sets
 * CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = TRUE, as the account's driver does (live-verified): over columns "a" and
 * "A", getInt("A") reads the second and getInt("a") the first, and a label spelled otherwise than every column is
 * "Column not found: x", the unquoted alias X included. Once the session sets the parameter TRUE a label names the
 * first column matching it ignoring case, getInt("A") the first, and so it stays for the connection, after a FALSE
 * or an UNSET too. Both of Frostlake's transports read labels so.
 */
public class ColumnLabelCaseTest extends BaseDatabaseTest {

    private static final String SHARED = "SELECT 1 AS \"a\", 2 AS \"A\"";
    private static final String CASED = "SELECT 7 AS X, 8 AS \"y\"";
    private static final String EXACT = "getInt(A)=2 getString(A)=2 getObject(A)=2 findColumn(A)=2 findColumn(a)=1"
        + " getInt(a)=1 | getInt(x)=Column not found: x getInt(Y)=Column not found: Y getInt(X)=7";
    private static final String IGNORING_CASE = "getInt(A)=1 getString(A)=1 getObject(A)=1 findColumn(A)=1"
        + " findColumn(a)=1 getInt(a)=1 | getInt(x)=7 getInt(Y)=8 getInt(X)=7";

    @Test
    public void aLabelIsSpelledExactlyUntilTheSessionSetsTheParameter() throws SQLException, IOException {
        if (isLiveSnowflake()) {
            try (Connection live = LiveSnowflake.openUnprepared()) {
                assertLabelsAcrossTheSession(live);
            }
            return;
        }
        try (Connection direct = DriverManager.getConnection("jdbc:frostlake:direct:column_label_case")) {
            assertLabelsAcrossTheSession(direct);
        }
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port)) {
            assertLabelsAcrossTheSession(http);
        } finally {
            server.stop();
        }
    }

    private static void assertLabelsAcrossTheSession(final Connection connection) throws SQLException {
        assertEquals(EXACT, labels(connection));
        execute(connection, "ALTER SESSION SET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = FALSE");
        assertEquals(EXACT, labels(connection));
        execute(connection, "ALTER SESSION SET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = TRUE");
        assertEquals(IGNORING_CASE, labels(connection));
        execute(connection, "ALTER SESSION SET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = FALSE");
        assertEquals(IGNORING_CASE, labels(connection));
        execute(connection, "ALTER SESSION UNSET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE");
        assertEquals(IGNORING_CASE, labels(connection));
    }

    /** What each getter reads by label, over the two columns sharing a label and the two spelled in one case. */
    private static String labels(final Connection connection) throws SQLException {
        final StringBuilder out = new StringBuilder();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(SHARED)) {
            rs.next();
            out.append("getInt(A)=").append(rs.getInt("A")).append(" getString(A)=").append(rs.getString("A"))
                .append(" getObject(A)=").append(rs.getObject("A")).append(" findColumn(A)=")
                .append(rs.findColumn("A")).append(" findColumn(a)=").append(rs.findColumn("a"))
                .append(" getInt(a)=").append(rs.getInt("a"));
        }
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(CASED)) {
            rs.next();
            out.append(" | getInt(x)=").append(intOrRefusal(rs, "x")).append(" getInt(Y)=")
                .append(intOrRefusal(rs, "Y")).append(" getInt(X)=").append(intOrRefusal(rs, "X"));
        }
        return out.toString();
    }

    private static String intOrRefusal(final ResultSet rs, final String label) {
        try {
            return String.valueOf(rs.getInt(label));
        } catch (final SQLException refused) {
            return refused.getMessage();
        }
    }

    private static void execute(final Connection connection, final String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
