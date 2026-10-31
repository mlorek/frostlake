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

import dev.frostlake.DatabaseEngine;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The checks of {@link DriverResultSetSurfaceTest} over the HTTP driver, against an in-process server: both transports
 * answer the driver surface as the account's driver answers it.
 */
public class DriverResultSetSurfaceHttpTest {

    private static DatabaseHttpServer server;
    private static String jdbcUrl;
    private Connection connection;

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
        final Statement statement = connection.createStatement();
        statement.execute("DROP DATABASE IF EXISTS test_db");
        statement.execute("CREATE DATABASE test_db");
        statement.execute("USE SCHEMA test_db.PUBLIC");
        // The in-process twin's session reads labels ignoring case, as the live harness's does (see BaseJdbcTest).
        statement.execute("ALTER SESSION SET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = TRUE");
        statement.close();
    }

    @AfterEach
    public void disconnect() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void anIntegerGetterRefusesAFixedPointNumberWithAFractionByPositionAndByLabel() throws SQLException {
        DriverSurfaceChecks.scaledNumberRefusals(connection, false);
    }

    @Test
    public void theIntegerGettersParseTheCellsTextAndRefuseWhatTheirTypeCannotHold() throws SQLException {
        DriverSurfaceChecks.integerReads(connection);
    }

    @Test
    public void theNumericGettersOverATextOrAVariantRefuseWhatSpellsNoNumberOfTheirKind() throws SQLException {
        DriverSurfaceChecks.textAndVariantReads(connection);
    }

    @Test
    public void getBooleanReadsOneZeroTrueAndFalseAndRefusesAnythingElse() throws SQLException {
        DriverSurfaceChecks.booleanReads(connection);
    }

    @Test
    public void aLabelNamesTheFirstColumnMatchingItIgnoringCaseAndAMissingOneIsRefused() throws SQLException {
        DriverSurfaceChecks.labelsAndPositions(connection);
    }

    @Test
    public void theCursorMovesForwardOnlyAndReportsItsPositionAsTheAccountsDriverDoes() throws SQLException {
        DriverSurfaceChecks.cursor(connection);
    }

    @Test
    public void aClosedResultSetRefusesReadsAndANullReadSaysSo() throws SQLException {
        DriverSurfaceChecks.closedAndNull(connection);
    }

    @Test
    public void getCatalogAndGetSchemaFollowWhereTheSessionStands() throws SQLException {
        DriverSurfaceChecks.namespace(connection);
    }

    @Test
    public void aCallInTheEscapeSyntaxRunsAndItsResultReadsThroughTheGetters() throws SQLException {
        DriverSurfaceChecks.escapedCall(connection);
    }

    @Test
    public void theMetadataDescribesTheSqlAsTheAccountsDriverDoes() throws SQLException {
        DriverSurfaceChecks.metadataAnswers(connection);
    }

    /** A connection that has sent nothing knows where its session stands, as the in-process driver's does. */
    @Test
    public void aFreshConnectionReportsTheSessionsStartingNamespaceAndUser() throws SQLException {
        final Connection fresh = DriverManager.getConnection(jdbcUrl);
        final DatabaseEngine engine = new DatabaseEngine();
        try {
            final Connection direct = new DirectConnection(engine);
            final String expected = direct.getCatalog() + "." + direct.getSchema();
            final String user = direct.getMetaData().getUserName();
            direct.close();
            assertEquals(expected, fresh.getCatalog() + "." + fresh.getSchema());
            assertNotNull(user);
            assertEquals(user, fresh.getMetaData().getUserName());
            assertEquals(jdbcUrl, fresh.getMetaData().getURL());
        } finally {
            fresh.close();
            engine.shutdown();
        }
    }

    @Test
    public void anOutParameterReadsAsItsColumnOfTheCallsResult() throws SQLException {
        DriverSurfaceChecks.outParameters(connection);
    }
}
