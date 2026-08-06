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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Second HTTP-transport surface pass: {@link DatabaseResultSet} scrollable navigation
 * (beforeFirst/first/last/absolute/afterLast) and the {@link DatabaseMetaData} informational
 * surface (product/driver identity, identifier rules, term names, limits).
 */
public class HttpJdbcSurfaceExtrasTest {

    private static final Logger logger = LoggerFactory.getLogger(HttpJdbcSurfaceExtrasTest.class);

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
        statement.execute("CREATE OR REPLACE DATABASE http_extras_db");
        statement.execute("USE DATABASE http_extras_db");
        statement.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void teardown() throws SQLException {
        try {
            statement.execute("DROP DATABASE IF EXISTS http_extras_db");
        } catch (final SQLException e) {
            // best-effort cleanup
        }
        statement.close();
        connection.close();
    }

    @Test
    public void resultSetScrollableNavigation() throws SQLException {
        statement.execute("CREATE TABLE nav_t (i INTEGER)");
        statement.execute("INSERT INTO nav_t VALUES (1), (2), (3)");
        try (final ResultSet rs = statement.executeQuery("SELECT i FROM nav_t ORDER BY i")) {
            assertTrue(rs.first());
            assertTrue(rs.isFirst());
            assertEquals(1, rs.getInt(1));

            assertTrue(rs.last());
            assertTrue(rs.isLast());
            assertEquals(3, rs.getInt(1));

            assertTrue(rs.absolute(2));
            assertEquals(2, rs.getInt(1));

            rs.beforeFirst();
            assertTrue(rs.isBeforeFirst());
            int count = 0;
            while (rs.next()) {
                count++;
            }
            assertEquals(3, count, "beforeFirst must allow re-iterating all rows");
            assertTrue(rs.isAfterLast());

            rs.afterLast();
            assertTrue(rs.isAfterLast());
            assertFalse(rs.absolute(99), "absolute beyond the end reports false");
        }
    }

    @Test
    public void metaDataInformationalSurface() throws SQLException {
        final java.sql.DatabaseMetaData md = connection.getMetaData();
        assertNotNull(md.getDatabaseProductName());
        assertNotNull(md.getDatabaseProductVersion());
        assertNotNull(md.getDriverName());
        assertNotNull(md.getDriverVersion());
        assertTrue(md.getDriverMajorVersion() >= 0);
        assertTrue(md.getDriverMinorVersion() >= 0);
        assertTrue(md.getJDBCMajorVersion() >= 4);
        assertTrue(md.getJDBCMinorVersion() >= 0);
        assertNotNull(md.getURL());
        assertNotNull(md.getIdentifierQuoteString());
        assertNotNull(md.getConnection());
        logger.info("HTTP metadata identity: {} {} via {}",
            md.getDatabaseProductName(), md.getDatabaseProductVersion(), md.getDriverName());
    }

    /**
     * getProcedures / getFunctions over the HTTP transport. Both transports run the same
     * INFORMATION_SCHEMA-backed query, but only this one carries it across the wire, so the shape is
     * asserted on both sides rather than assumed to travel intact.
     */
    @Test
    public void routineMetadataCrossesTheWire() throws Exception {
        statement.execute("CREATE OR REPLACE PROCEDURE wire_proc(x INTEGER) RETURNS VARCHAR LANGUAGE SQL"
            + " AS $$BEGIN RETURN 'a'; END;$$");
        statement.execute("CREATE OR REPLACE FUNCTION wire_fn(x INTEGER) RETURNS INTEGER AS $$ x + 1 $$");

        try (ResultSet rs = connection.getMetaData().getProcedures("HTTP_EXTRAS_DB", "PUBLIC", "WIRE%")) {
            assertEquals(6, rs.getMetaData().getColumnCount());
            assertTrue(rs.next(), "the procedure must come back over HTTP");
            assertEquals("WIRE_PROC", rs.getString("PROCEDURE_NAME"));
            assertEquals("user-defined procedure", rs.getString("REMARKS"));
            assertEquals(2, rs.getInt("PROCEDURE_TYPE"));
        }
        try (ResultSet rs = connection.getMetaData().getFunctions("HTTP_EXTRAS_DB", "PUBLIC", "WIRE%")) {
            assertEquals(6, rs.getMetaData().getColumnCount());
            assertTrue(rs.next(), "the function must come back over HTTP");
            assertEquals("WIRE_FN", rs.getString("FUNCTION_NAME"));
            assertEquals(1, rs.getInt("FUNCTION_TYPE"));
        }
    }
}
