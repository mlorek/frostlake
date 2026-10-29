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
import dev.frostlake.http.DatabaseHttpServer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The text a client reads for a scaled decimal held in a VARIANT is the decimal's plain digits with no trailing zero,
 * the account's own text for the cell, read the same over JSON and Arrow results (live-verified): a bare
 * {@code 0.0000001::VARIANT} reads {@code 0.0000001}, as {@code TO_VARIANT} and {@code TO_JSON} of it do, never a
 * Java exponent. getObject hands every semi-structured cell back as that same text, a String, whatever the value
 * it holds. Both Frostlake drivers read them so.
 */
public class VariantDecimalClientTextTest extends BaseJdbcTest {

    /** Each statement with the text its one cell reads. */
    private static final String[][] CELLS = {
        {"SELECT 0.0000001::VARIANT", "0.0000001"},
        {"SELECT TO_VARIANT(0.0000001)", "0.0000001"},
        {"SELECT 0.0000001::NUMBER(10,7)::VARIANT", "0.0000001"},
        {"SELECT 12345678901234567890.5::VARIANT", "12345678901234567890.5"},
        {"SELECT 1.50::VARIANT", "1.5"},
        {"SELECT 100.00::VARIANT", "100"},
        {"SELECT PARSE_JSON('0.0000001')", "0.0000001"},
        {"SELECT 0.5::FLOAT::VARIANT", "5.000000000000000e-01"},
        {"SELECT 1e-7::VARIANT", "0.0000001"},
        {"SELECT OBJECT_CONSTRUCT('a', 0.0000001)", "{\"a\":0.0000001}"},
        {"SELECT ARRAY_CONSTRUCT(0.0000001, 1.50)", "[0.0000001,1.5]"},
        {"SELECT TO_JSON(0.0000001::VARIANT)", "0.0000001"},
        {"SELECT (0.0000001::VARIANT)::VARCHAR", "0.0000001"},
        {"SELECT TO_JSON(12345678901234567890.5::VARIANT)", "12345678901234567890.5"},
        {"SELECT TO_JSON(1.50::VARIANT)", "1.5"},
    };

    /** Each statement with the text getObject hands back for its one cell, a String, or null for none. */
    private static final String[][] MEMBERS = {
        {"SELECT 1::VARIANT", "1"},
        {"SELECT TRUE::VARIANT", "true"},
        {"SELECT 1.5::FLOAT::VARIANT", "1.500000000000000e+00"},
        {"SELECT 'x'::VARIANT", "\"x\""},
        {"SELECT NULL::VARIANT", null},
        {"SELECT PARSE_JSON('null')", "null"},
        {"SELECT ARRAY_CONSTRUCT(1)", "[1]"},
        {"SELECT OBJECT_CONSTRUCT('a', 1)", "{\"a\":1}"},
        {"SELECT '2024-01-01'::DATE::VARIANT", "\"2024-01-01\""},
        {"SELECT 12345678901234567890::VARIANT", "12345678901234567890"},
        {"SELECT PARSE_JSON('{\"a\":1}'):a", "1"},
        {"SELECT PARSE_JSON('[1.5]')[0]", "1.5"},
    };

    private static void assertTexts(final Statement statement) throws SQLException {
        for (final String[] cell : CELLS) {
            try (ResultSet rs = statement.executeQuery(cell[0])) {
                rs.next();
                assertEquals(cell[1], rs.getString(1), cell[0]);
                assertEquals(cell[1], rs.getObject(1), cell[0]);
            }
        }
        for (final String[] member : MEMBERS) {
            try (ResultSet rs = statement.executeQuery(member[0])) {
                rs.next();
                assertEquals(member[1], rs.getObject(1), member[0]);
                assertEquals(member[1], rs.getString(1), member[0]);
                assertEquals(Types.VARCHAR, rs.getMetaData().getColumnType(1), member[0]);
                assertEquals("java.lang.String", rs.getMetaData().getColumnClassName(1), member[0]);
            }
        }
    }

    @Test
    public void aSemiStructuredCellReadsAsItsText() throws SQLException {
        assertTexts(statement);
    }

    /** The HTTP driver reads each cell as the in-process one does. */
    @Test
    public void theHttpDriverReadsTheSameText() throws IOException, SQLException {
        assumeFalse(isLiveSnowflake(), "the HTTP driver talks to an embedded server");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection c = DriverManager.getConnection("jdbc:frostlake://localhost:" + port);
             Statement s = c.createStatement()) {
            assertTexts(s);
        } finally {
            server.stop();
        }
    }
}
