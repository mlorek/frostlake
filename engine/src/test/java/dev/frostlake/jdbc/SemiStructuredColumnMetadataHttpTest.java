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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.http.DatabaseHttpServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Types;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The HTTP transport reports a semi-structured or VECTOR column as the in-process one does (see
 * {@link SemiStructuredColumnMetadataTest}): the metadata is computed from the type name on the wire, with the same
 * mapping.
 */
public class SemiStructuredColumnMetadataHttpTest {

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
    }

    @AfterEach
    public void disconnect() throws SQLException {
        statement.close();
        connection.close();
    }

    @Test
    public void semiStructuredColumnsAreTextOverHttp() throws SQLException {
        try (ResultSet rs = statement.executeQuery(
                "SELECT TO_VARIANT(1), OBJECT_CONSTRUCT('a', 1), ARRAY_CONSTRUCT(1), {'a': 1}::MAP(VARCHAR, INT)")) {
            final ResultSetMetaData md = rs.getMetaData();
            assertTrue(rs.next());
            final String[] names = {"VARIANT", "OBJECT", "ARRAY", "OBJECT"};
            for (int i = 1; i <= 4; i++) {
                assertEquals(names[i - 1], md.getColumnTypeName(i));
                assertEquals(Types.VARCHAR, md.getColumnType(i));
                assertEquals("java.lang.String", md.getColumnClassName(i));
                assertEquals(String.class, rs.getObject(i).getClass());
            }
        }
    }

    @Test
    public void aVectorHasNoClassOverHttp() throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT [1,2]::VECTOR(INT, 2)")) {
            final ResultSetMetaData md = rs.getMetaData();
            assertTrue(rs.next());
            assertEquals("VECTOR", md.getColumnTypeName(1));
            assertEquals(50003, md.getColumnType(1));
            assertThrows(SQLFeatureNotSupportedException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    md.getColumnClassName(1);
                }
            });
            assertEquals(String.class, rs.getObject(1).getClass());
        }
    }
}
