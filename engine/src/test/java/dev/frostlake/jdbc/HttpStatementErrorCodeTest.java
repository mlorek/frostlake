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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Over the HTTP transport a failed statement carries the account's vendor code and SQLSTATE as the in-process
 * driver's does (see StatementErrorCodeTest): the answer's errorCode and sqlState, read by the driver into its
 * SQLException - an exception a script raised included, whose code only the exception knows.
 */
public class HttpStatementErrorCodeTest {

    private static DatabaseHttpServer server;
    private static String jdbcUrl;

    private Connection connection;
    private Statement statement;

    @BeforeAll
    public static void startServer() throws IOException {
        final int port;
        try (final ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
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
    public void setup() throws SQLException {
        connection = DriverManager.getConnection(jdbcUrl);
        statement = connection.createStatement();
        statement.execute("CREATE OR REPLACE DATABASE http_codes_db");
        statement.execute("USE DATABASE http_codes_db");
        statement.execute("USE SCHEMA PUBLIC");
        statement.execute("CREATE OR REPLACE TABLE hc (n INT NOT NULL)");
    }

    @AfterEach
    public void teardown() throws SQLException {
        try {
            statement.execute("DROP DATABASE IF EXISTS http_codes_db");
        } finally {
            statement.close();
            connection.close();
        }
    }

    @Test
    public void aFailureCrossesWithItsCodeAndState() throws SQLException {
        final String[][] cases = {
            {"SELECT * FROM no_such_table_hc", "2003 42S02"},
            {"SELECT * FROM http_codes_db.no_such_schema_hc.t", "2003 02000"},
            {"SELEC 1", "1003 42000"},
            {"SELECT 1/0", "100051 22012"},
            {"SELECT 'x'::INT", "100038 22018"},
            {"INSERT INTO hc VALUES (NULL)", "100072 22000"},
            {"CREATE TABLE hc (n INT)", "2002 42710"},
            {"EXECUTE IMMEDIATE $$DECLARE e EXCEPTION (-20001, 'boom'); BEGIN RAISE e; END;$$", "-20001 P0001"},
            {"BEGIN SELECT 1/0; END", "100051 22012"},
        };
        for (final String[] failing : cases) {
            try (final Statement s = connection.createStatement()) {
                s.execute(failing[0]);
                fail("no failure: " + failing[0]);
            } catch (final SQLException refused) {
                assertEquals(failing[1], refused.getErrorCode() + " " + refused.getSQLState(), failing[0]);
            }
        }
    }
}
