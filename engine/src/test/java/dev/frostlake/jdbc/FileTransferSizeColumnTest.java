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

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The size a file transfer answers — a GET's {@code size}, a PUT's {@code source_size} and {@code target_size} —
 * is the client's own DECIMAL(10,0), where every other count is a NUMBER: the driver names the column DECIMAL,
 * reports it as {@code java.sql.Types.DECIMAL} though it holds no fraction, reads it as a {@code BigDecimal} and
 * shows it twelve wide. LIST's {@code size} beside it is the server's NUMBER(38,0), a {@code Long}. A fixed-point
 * and an approximate number are the only columns a driver reports signed. The in-process and the HTTP transport
 * answer alike, and every answer is live-verified.
 */
public class FileTransferSizeColumnTest extends BaseJdbcTest {

    private static final String CLIENT_DECIMAL = "DECIMAL 3 10 0 java.math.BigDecimal 12 true BigDecimal";

    private static final String SERVER_NUMBER = "NUMBER -5 38 0 java.lang.Long 39 true Long";

    private static final String EVERY_FAMILY = """
        SELECT 1::NUMBER(10,0) n0, 1.5::NUMBER(10,2) n2, 1::INT i, 1.5::FLOAT f, 1.5::DOUBLE d, 'x'::VARCHAR v,
               TRUE b, '2020-01-02'::DATE dt, '03:04:05'::TIME tm, '2020-01-02'::TIMESTAMP_NTZ ntz,
               '2020-01-02'::TIMESTAMP_LTZ ltz, '2020-01-02 00:00:00 +01:00'::TIMESTAMP_TZ tz,
               TO_BINARY('AB', 'HEX') x, PARSE_JSON('1') var, ARRAY_CONSTRUCT(1) arr, OBJECT_CONSTRUCT('k', 1) obj,
               NULL nl, 12345678901234567890123 big, 1::REAL r""";

    private static final String SIGNED = "N0 true | N2 true | I true | F true | D true | V false | B false | DT false"
        + " | TM false | NTZ false | LTZ false | TZ false | X false | VAR false | ARR false | OBJ false | NL false"
        + " | BIG true | R true";

    /**
     * One column of a statement's result as the driver describes and reads it: its type name, type code,
     * precision, scale, class name, display size, whether it is signed, and the class {@code getObject} answers.
     */
    private static String described(final Statement on, final String sql, final String label) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            final boolean row = rs.next();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (md.getColumnLabel(i).equalsIgnoreCase(label)) {
                    final Object read = row ? rs.getObject(i) : null;
                    return md.getColumnTypeName(i) + ' ' + md.getColumnType(i) + ' ' + md.getPrecision(i) + ' '
                        + md.getScale(i) + ' ' + md.getColumnClassName(i) + ' ' + md.getColumnDisplaySize(i) + ' '
                        + md.isSigned(i) + ' ' + (read == null ? "null" : read.getClass().getSimpleName());
                }
            }
            return "no column " + label;
        }
    }

    /** Whether each column of a statement's result is signed, a bar between columns. */
    private static String signs(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                out.append(i == 1 ? "" : " | ").append(md.getColumnLabel(i)).append(' ').append(md.isSigned(i));
            }
            return out.toString();
        }
    }

    /** A stage holding one small file, and the PUT that put it there. */
    private static String put(final Statement on, final Path directory) throws SQLException, IOException {
        Files.writeString(directory.resolve("a.csv"), "1,x\n");
        on.execute("CREATE OR REPLACE STAGE transfer_size_stage");
        return "PUT 'file://" + directory.resolve("a.csv") + "' @transfer_size_stage AUTO_COMPRESS = FALSE"
            + " OVERWRITE = TRUE";
    }

    private static void assertTransferSizes(final Statement on) throws SQLException, IOException {
        final Path directory = Files.createTempDirectory("fl-transfer-size");
        final String put = put(on, directory);
        assertEquals(CLIENT_DECIMAL, described(on, put, "source_size"), "a PUT's source size");
        assertEquals(CLIENT_DECIMAL, described(on, put, "target_size"), "a PUT's target size");
        assertEquals(SERVER_NUMBER, described(on, "LIST @transfer_size_stage", "size"), "a listed size");
        final Path target = Files.createDirectories(directory.resolve("got"));
        assertEquals(CLIENT_DECIMAL,
            described(on, "GET @transfer_size_stage/a.csv 'file://" + target + "'", "size"), "a GET's size");
    }

    /** A PUT's two sizes and a GET's size are the client's DECIMAL; LIST's is the server's NUMBER. */
    @Test
    public void aTransferredSizeIsTheClientsDecimal() throws SQLException, IOException {
        assertTransferSizes(statement);
    }

    /** NUMBER, whatever its scale, and DOUBLE are signed; no other family is. */
    @Test
    public void numbersAreTheSignedColumns() throws SQLException {
        assertEquals(SIGNED, signs(statement, EVERY_FAMILY));
    }

    /** The HTTP transport, which reads the type off the wire, answers what the in-process one answers. */
    @Test
    public void theHttpTransportAnswersAlike() throws SQLException, IOException {
        assumeFalse(LiveSnowflake.enabled(), "the account has one transport");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection overHttp = DriverManager.getConnection("jdbc:frostlake://localhost:" + port);
             Statement on = overHttp.createStatement()) {
            on.execute("CREATE DATABASE IF NOT EXISTS transfer_size_db");
            on.execute("USE DATABASE transfer_size_db");
            on.execute("USE SCHEMA PUBLIC");
            assertTransferSizes(on);
            assertEquals(SIGNED, signs(on, EVERY_FAMILY));
        } finally {
            server.stop();
        }
    }
}
