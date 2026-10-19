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
import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A Timestamp bound without a Calendar binds its moment, as the account's driver binds it: a TIMESTAMP_NTZ column
 * holds the moment as the session's zone shows it, whatever the client's zone; a Time binds the time its moment
 * shows in UTC, in any session; setObject binds each as its own setter does (live-verified).
 */
public class TimestampBindMomentTest extends BaseJdbcTest {

    private static final Timestamp MIDNIGHT_UTC = Timestamp.from(Instant.parse("2024-01-01T00:00:00Z"));

    private static final Time HALF_PAST_ELEVEN_UTC = new Time(Instant.parse("1970-01-01T23:30:00Z").toEpochMilli());

    /** The text of each value bound in the session's zone, read back from a TIMESTAMP_NTZ or a TIME column. */
    private static String binds(final Connection on, final String zone) throws SQLException {
        final List<String> lines = new ArrayList<String>();
        try (Statement statement = on.createStatement()) {
            statement.execute("CREATE OR REPLACE TABLE tbm (k VARCHAR, ts TIMESTAMP_NTZ, t TIME)");
            statement.execute("ALTER SESSION SET TIMEZONE = '" + zone + "'");
            try (PreparedStatement insert = on.prepareStatement("INSERT INTO tbm VALUES (?, ?, ?)")) {
                insert.setString(1, "setters");
                insert.setTimestamp(2, MIDNIGHT_UTC);
                insert.setTime(3, HALF_PAST_ELEVEN_UTC);
                insert.executeUpdate();
                insert.setString(1, "setObject");
                insert.setObject(2, MIDNIGHT_UTC);
                insert.setObject(3, HALF_PAST_ELEVEN_UTC);
                insert.executeUpdate();
            }
            try (ResultSet rs = statement.executeQuery(
                    "SELECT k, TO_VARCHAR(ts, 'YYYY-MM-DD HH24:MI:SS'), TO_VARCHAR(t) FROM tbm ORDER BY k DESC")) {
                while (rs.next()) {
                    lines.add(zone + " " + rs.getString(1) + " " + rs.getString(2) + " " + rs.getString(3));
                }
            }
            statement.execute("ALTER SESSION UNSET TIMEZONE");
        }
        return String.join("\n", lines);
    }

    private static final String EXPECTED = String.join("\n",
        "UTC setters 2024-01-01 00:00:00 23:30:00",
        "UTC setObject 2024-01-01 00:00:00 23:30:00",
        "America/New_York setters 2023-12-31 19:00:00 23:30:00",
        "America/New_York setObject 2023-12-31 19:00:00 23:30:00");

    @Test
    public void aTimestampBindsItsMomentAndATimeItsUtcClock() throws SQLException {
        assertEquals(EXPECTED, binds(connection, "UTC") + "\n" + binds(connection, "America/New_York"));
    }

    @Test
    public void overHttpTheDriverBindsThemAlike() throws Exception {
        if (LiveSnowflake.enabled()) {
            return;   // the account has one transport; the in-process one is measured above
        }
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port + "/test_db")) {
            try (Statement use = http.createStatement()) {
                use.execute("CREATE DATABASE IF NOT EXISTS test_db");
                use.execute("USE SCHEMA test_db.public");
            }
            assertEquals(EXPECTED, binds(http, "UTC") + "\n" + binds(http, "America/New_York"));
        } finally {
            server.stop();
        }
    }
}
