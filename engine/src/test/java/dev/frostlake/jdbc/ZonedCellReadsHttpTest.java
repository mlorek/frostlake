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

import dev.frostlake.http.DatabaseHttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * The temporal reads {@link ZonedCellReadsTest} pins for the in-process driver and the account's, over the HTTP
 * driver: the session's TIMEZONE crosses with each answer, so the driver reads a DATE's midnight and a wall clock in
 * it as the in-process one does.
 */
public class ZonedCellReadsHttpTest {

    private static DatabaseHttpServer server;
    private static String jdbcUrl;
    private static TimeZone jvmZone;
    private Connection connection;
    private Statement statement;

    @BeforeAll
    public static void startServer() throws IOException {
        jvmZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(ZonedCellReadsSupport.JVM_ZONE));
        final int port;
        try (final ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        server = new DatabaseHttpServer(port);
        server.start();
        jdbcUrl = "jdbc:frostlake://localhost:" + port;
    }

    @AfterAll
    public static void stopServer() {
        if (server != null) {
            server.stop();
        }
        TimeZone.setDefault(jvmZone);
    }

    @BeforeEach
    public void connect() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        statement = connection.createStatement();
        statement.execute("CREATE DATABASE IF NOT EXISTS zcr_db");
        statement.execute("USE SCHEMA zcr_db.PUBLIC");
    }

    @AfterEach
    public void disconnect() throws SQLException {
        statement.execute("DROP DATABASE IF EXISTS zcr_db");
        statement.close();
        connection.close();
    }

    @Test
    public void aLosAngelesSessionReadsEachCell() throws SQLException {
        statement.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        ZonedCellReadsSupport.table(statement);
        final List<String> answers = new ArrayList<String>();
        ZonedCellReadsSupport.read(connection, null, answers);
        ZonedCellReadsSupport.assertAnswers(ZonedCellReadsSupport.LOS_ANGELES, answers);
    }

    @Test
    public void aLosAngelesSessionReadsEachCellWithACalendar() throws SQLException {
        statement.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        ZonedCellReadsSupport.table(statement);
        final List<String> answers = new ArrayList<String>();
        ZonedCellReadsSupport.read(connection, ZonedCellReadsSupport.calendar("Asia/Kolkata"), answers);
        ZonedCellReadsSupport.read(connection, ZonedCellReadsSupport.calendar("UTC"), answers);
        ZonedCellReadsSupport.assertAnswers(ZonedCellReadsSupport.LOS_ANGELES_CALENDARS, answers);
    }

    @Test
    public void utcAndTokyoSessionsReadEachCell() throws SQLException {
        statement.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        ZonedCellReadsSupport.table(statement);
        final List<String> answers = new ArrayList<String>();
        ZonedCellReadsSupport.read(connection, null, answers);
        ZonedCellReadsSupport.read(connection, ZonedCellReadsSupport.calendar("Asia/Kolkata"), answers);
        statement.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        ZonedCellReadsSupport.read(connection, null, answers);
        ZonedCellReadsSupport.read(connection, ZonedCellReadsSupport.calendar("America/New_York"), answers);
        ZonedCellReadsSupport.assertAnswers(ZonedCellReadsSupport.UTC_AND_TOKYO, answers);
    }
}
