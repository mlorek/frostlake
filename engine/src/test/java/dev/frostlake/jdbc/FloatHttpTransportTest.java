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
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A FLOAT through the HTTP transport answers as it does in process: {@code getString} is Java's own text of the
 * double and {@code getObject} is a Double, decided by the column's declared type carried in the wire metadata,
 * with every digit of the double, as the account's default Arrow results hand it back. The JSON wire parses every
 * fraction as a BigDecimal so a NUMBER stays exact, which is why the FLOAT cell has to be re-typed on the
 * client from the type it was declared with. The one number a BigDecimal cannot hold, a negative zero, is
 * read off the wire token's own text as a double, so the sign survives the transport the way it does in
 * process.
 */
public class FloatHttpTransportTest {

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
        statement.execute("CREATE OR REPLACE DATABASE test_db");
        statement.execute("USE DATABASE test_db");
        statement.execute("USE SCHEMA PUBLIC");
    }

    @AfterEach
    public void disconnect() throws SQLException {
        statement.execute("DROP DATABASE IF EXISTS test_db");
        statement.close();
        connection.close();
    }

    private String text(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        rs.next();
        final String value = rs.getString(1);
        rs.close();
        return value;
    }

    private Object object(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        rs.next();
        final Object value = rs.getObject(1);
        rs.close();
        return value;
    }

    /** The text is Java's own of the double, as the in-process driver spells it. */
    @Test
    public void getStringIsJavasTextOfTheDouble() throws SQLException {
        assertEquals("1.4142135623730951", text("SELECT SQRT(2) AS v"));
        assertEquals("-1.4142135623730951", text("SELECT -SQRT(2) AS v"));
        assertEquals("3.0", text("SELECT 3.0::FLOAT AS v"), "a whole double keeps its point");
        assertEquals("0.1", text("SELECT 0.1::FLOAT AS v"));
        assertEquals("1.0E18", text("SELECT 1e18::FLOAT AS v"));
        assertEquals(Double.toString(1234567890123456768.0), text("SELECT 1234567890123456789::FLOAT AS v"));
        assertEquals("NaN", text("SELECT 'NaN'::FLOAT AS v"));
        assertEquals("-Infinity", text("SELECT '-inf'::FLOAT AS v"));
        assertEquals("1.50", text("SELECT 1.50::NUMBER(10,2) AS v"), "an exact number keeps its scale");
    }

    /** The object is a Double for the approximate family and a BigDecimal for the exact one. */
    @Test
    public void getObjectFollowsTheDeclaredFamily() throws SQLException {
        assertEquals(Double.valueOf(Math.sqrt(2)), object("SELECT SQRT(2) AS v"));
        assertEquals(Double.valueOf(3.0), object("SELECT 3.0::FLOAT AS v"));
        assertEquals(Double.valueOf(0.1), object("SELECT 0.1::FLOAT AS v"));
        assertEquals(Double.valueOf(1.0E18), object("SELECT 1e18::FLOAT AS v"));
        assertEquals(new BigDecimal("1.50"), object("SELECT 1.50::NUMBER(10,2) AS v"));
    }

    /** The typed getters read a FLOAT as the in-process driver reads it (see {@link FloatColumnChecks}). */
    @Test
    public void theTypedGettersReadAsInProcess() throws SQLException {
        FloatColumnChecks.integerReads(connection);
        FloatColumnChecks.decimalReads(connection, false);
        FloatColumnChecks.booleanReads(connection);
        FloatColumnChecks.refusedReads(connection);
        FloatColumnChecks.doubleReads(connection);
    }

    /** Every digit of the double crosses, a bound one too, and the range's edges stay numbers. */
    @Test
    public void theDoubleKeepsEveryDigit() throws SQLException {
        assertEquals(Double.valueOf(0.30000000000000004), object("SELECT 0.1::FLOAT + 0.2::FLOAT AS v"));
        assertEquals(Double.valueOf(1.100000023841858), object("SELECT 1.100000023841858::FLOAT AS v"));
        assertEquals(Double.valueOf(Double.MIN_NORMAL), object("SELECT 2.2250738585072014e-308::FLOAT AS v"));
        assertEquals(Double.valueOf(Double.MAX_VALUE), object("SELECT 1.7976931348623157e308::FLOAT AS v"));
        try (final PreparedStatement prepared = connection.prepareStatement("SELECT ?::FLOAT AS v")) {
            prepared.setDouble(1, 0.1 + 0.2);
            try (final ResultSet rs = prepared.executeQuery()) {
                rs.next();
                assertEquals(0.30000000000000004, rs.getDouble(1));
            }
        }
    }

    /** A stored FLOAT column reads the same way, being a double in the table. */
    @Test
    public void aStoredColumnReadsTheSameWay() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE fh (id INT, f FLOAT)");
        statement.execute("INSERT INTO fh VALUES (1, 0.1), (2, 3), (3, 1234567890123456789)");
        statement.execute("INSERT INTO fh SELECT 4, SQRT(2)");
        assertEquals("0.1", text("SELECT f FROM fh WHERE id = 1"));
        assertEquals("3.0", text("SELECT f FROM fh WHERE id = 2"));
        assertEquals(Double.toString(1234567890123456768.0), text("SELECT f FROM fh WHERE id = 3"));
        assertEquals("1.4142135623730951", text("SELECT f FROM fh WHERE id = 4"));
        assertEquals(Double.valueOf(0.1), object("SELECT f FROM fh WHERE id = 1"));
        assertTrue(object("SELECT f FROM fh WHERE id = 3") instanceof Double);
        assertEquals("0.10000000000000000555", text("SELECT f::NUMBER(38,20) FROM fh WHERE id = 1"),
            "the column holds the double, whose exact digits a cast shows");
    }

    /**
     * A FLOAT's negative zero keeps its sign across the wire, computed and stored alike, while a NUMBER
     * zero has no sign to keep and a NUMBER fraction stays exact to the digit.
     */
    @Test
    public void aNegativeZeroKeepsItsSign() throws SQLException {
        assertEquals("-0.0", text("SELECT -0.0::FLOAT AS v"));
        assertEquals("-0.0", text("SELECT -(0.0::FLOAT) AS v"));
        final Object computed = object("SELECT -0.0::FLOAT AS v");
        assertTrue(computed instanceof Double, String.valueOf(computed));
        assertEquals("-0.0", computed.toString());
        statement.execute("CREATE OR REPLACE TABLE fz (id INT, f FLOAT)");
        statement.execute("INSERT INTO fz SELECT 1, -0.0::FLOAT");
        assertEquals("-0.0", text("SELECT f FROM fz WHERE id = 1"));
        assertEquals("-0.0", object("SELECT f FROM fz WHERE id = 1").toString());
        assertEquals("0.0", text("SELECT -0.0::NUMBER(3,1) AS v"));
        assertEquals(new BigDecimal("0.0"), object("SELECT -0.0::NUMBER(3,1) AS v"));
        assertEquals(new BigDecimal("0.00000000000000000001"),
            object("SELECT 0.00000000000000000001::NUMBER(38,20) AS v"));
        assertEquals("0.00000000000000000001", text("SELECT 0.00000000000000000001::NUMBER(38,20) AS v"));
    }
}
