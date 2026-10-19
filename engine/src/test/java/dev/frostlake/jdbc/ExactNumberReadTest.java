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
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How the driver reads an exact number and a file listing (live-verified, against the account's own driver): a
 * NUMBER of a scale above 0 is no long nor int, whatever it holds — {@code Cannot convert value in the driver from
 * type:3 to type:long, value=3.00.} — while a scale-0 one is; a scale-0 NUMBER whose value fits a long is read as a
 * {@code Long}, in process as over HTTP, and one past it as a {@code BigDecimal}; the four columns of a LIST are
 * nullable.
 */
public class ExactNumberReadTest extends BaseJdbcTest {

    /** What one connection answers, each read as a line, so a run shows every reading that differs. */
    private static String readings(final Connection on) throws SQLException {
        final List<String> lines = new ArrayList<String>();
        try (Statement statement = on.createStatement();
             ResultSet rs = statement.executeQuery("SELECT 3.50::NUMBER(10,2) AS a, 3.00::NUMBER(10,2) AS b, "
                 + "1::NUMBER(10,0) AS c, 1::DECIMAL(10,0) AS d, 12345678901234567890::NUMBER(20,0) AS e")) {
            rs.next();
            lines.add("getLong(a) " + refusalOf(rs, 1, "long"));
            lines.add("getInt(a) " + refusalOf(rs, 1, "int"));
            lines.add("getLong(b) " + refusalOf(rs, 2, "long"));
            lines.add("getLong(c) " + refusalOf(rs, 3, "long"));
            lines.add("getBigDecimal(a) " + rs.getBigDecimal(1));
            final ResultSetMetaData metaData = rs.getMetaData();
            for (int i = 3; i <= 5; i++) {
                lines.add("getObject(" + i + ") " + rs.getObject(i).getClass().getName() + " "
                    + metaData.getColumnClassName(i));
            }
        }
        try (Statement statement = on.createStatement()) {
            statement.execute("CREATE OR REPLACE STAGE lst_st");
            try (ResultSet rs = statement.executeQuery("LIST @lst_st")) {
                final ResultSetMetaData metaData = rs.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    lines.add("LIST " + metaData.getColumnName(i).toLowerCase(Locale.ROOT) + " nullable "
                        + metaData.isNullable(i));
                }
            }
        }
        return String.join("\n", lines);
    }

    /** The refusal a getter answers over a value it cannot read, or the value it read. */
    private static String refusalOf(final ResultSet rs, final int column, final String getter) {
        try {
            return "long".equals(getter) ? String.valueOf(rs.getLong(column)) : String.valueOf(rs.getInt(column));
        } catch (final SQLException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    private static final String EXPECTED = String.join("\n",
        "getLong(a) REFUSED Cannot convert value in the driver from type:3 to type:long, value=3.50.",
        "getInt(a) REFUSED Cannot convert value in the driver from type:3 to type:int, value=3.50.",
        "getLong(b) REFUSED Cannot convert value in the driver from type:3 to type:long, value=3.00.",
        "getLong(c) 1",
        "getBigDecimal(a) 3.50",
        "getObject(3) java.lang.Long java.lang.Long",
        "getObject(4) java.lang.Long java.lang.Long",
        "getObject(5) java.math.BigDecimal java.lang.Long",
        "LIST name nullable 1",
        "LIST size nullable 1",
        "LIST md5 nullable 1",
        "LIST last_modified nullable 1");

    @Test
    public void theDriverReadsAnExactNumberAndAListingAsTheAccountsDoes() throws SQLException {
        assertEquals(EXPECTED, readings(connection));
    }

    @Test
    public void overHttpTheDriverReadsThemAlike() throws Exception {
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
            assertEquals(EXPECTED, readings(http));
        } finally {
            server.stop();
        }
    }
}
