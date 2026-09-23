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

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The HTTP transport holds to the same execute / update-count contract as the in-process one, reading each
 * statement's update count off the wire's {@code jdbcUpdateCount}. The assertions are Snowflake's, shared with
 * {@link JdbcUpdateCountContractTest}, which also runs them against a live account.
 */
public class HttpUpdateCountContractTest {

    private static DatabaseHttpServer server;
    private static String jdbcUrl;
    private Connection connection;
    private Statement statement;

    @BeforeAll
    public static void startServer() throws IOException {
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
    }

    @BeforeEach
    public void connect() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        statement = connection.createStatement();
        statement.execute("CREATE OR REPLACE DATABASE update_count_db");
        statement.execute("USE SCHEMA PUBLIC");
        UpdateCountContract.setUp(statement);
    }

    @AfterEach
    public void disconnect() throws SQLException {
        statement.execute("DROP DATABASE IF EXISTS update_count_db");
        connection.close();
    }

    @Test
    public void everyStatementWithoutRowsCountsZero() throws SQLException {
        UpdateCountContract.everyStatementWithoutRowsCountsZero(statement);
    }

    @Test
    public void dmlCountsItsRows() throws SQLException {
        UpdateCountContract.dmlCountsItsRows(statement);
    }

    @Test
    public void copyCountsTheRowsItLoaded() throws SQLException {
        UpdateCountContract.copyCountsTheRowsItLoaded(statement);
    }

    @Test
    public void rowAnsweringStatementsAnswerRows() throws SQLException {
        UpdateCountContract.rowAnsweringStatementsAnswerRows(statement);
    }

    @Test
    public void executeUpdateRefusesRows() throws SQLException {
        UpdateCountContract.executeUpdateRefusesRows(statement);
    }

    @Test
    public void executeQueryReadsOneStatementsGrid() throws SQLException {
        UpdateCountContract.executeQueryReadsOneStatementsGrid(connection);
    }

    @Test
    public void severalStatementsWalkInOrder() throws SQLException {
        UpdateCountContract.severalStatementsWalkInOrder(statement);
    }

    @Test
    public void batchesCountEachStatement() throws SQLException {
        UpdateCountContract.batchesCountEachStatement(connection);
    }

    @Test
    public void preparedStatementsFollowTheRule() throws SQLException {
        UpdateCountContract.preparedStatementsFollowTheRule(connection);
    }
}
