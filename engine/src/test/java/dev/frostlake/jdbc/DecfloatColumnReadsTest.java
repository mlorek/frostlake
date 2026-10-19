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
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A DECFLOAT column as Snowflake's JDBC driver reads it (live-verified): type code DECIMAL named DECFLOAT, precision
 * 38, scale 0, display size 40 and class {@code java.math.BigDecimal}; {@code getObject} the BigDecimal of its text —
 * 1e40 at scale -3, 1E-10 at scale 10 — {@code getString} the text itself, scientific past 38 digits; {@code getLong}
 * refused for a fraction or a number past a long; and {@code getColumns} reports DECIMAL, 38 and 0. Both transports
 * answer alike: the HTTP wire carries the DECFLOAT as its text.
 */
public class DecfloatColumnReadsTest extends BaseJdbcTest {

    private static final String VALUES = "SELECT 1.5::DECFLOAT AS a, 1::DECFLOAT / 3 AS b, 1e40::DECFLOAT AS c,"
        + " 1E-10::DECFLOAT AS d, 100::DECFLOAT AS e";

    /** Each column's label, type code, type name, precision, scale, display size and class. */
    private static String metadata(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                out.append(i > 1 ? " | " : "").append(md.getColumnLabel(i)).append(' ').append(md.getColumnType(i))
                    .append(' ').append(md.getColumnTypeName(i)).append(' ').append(md.getPrecision(i)).append(' ')
                    .append(md.getScale(i)).append(' ').append(md.getColumnDisplaySize(i)).append(' ')
                    .append(md.getColumnClassName(i));
            }
            return out.toString();
        }
    }

    /** Each cell's object, as its class, value and scale, and its text. */
    private static String reads(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            final StringBuilder out = new StringBuilder();
            rs.next();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                final Object value = rs.getObject(i);
                final BigDecimal exact = rs.getBigDecimal(i);
                out.append(i > 1 ? " | " : "").append(value.getClass().getSimpleName()).append(':').append(value)
                    .append('@').append(exact.scale()).append(' ').append(rs.getString(i));
            }
            return out.toString();
        }
    }

    /** A read of a column through getLong. */
    private static Executable longOf(final ResultSet rs, final int column) {
        return new Executable() {
            @Override
            public void execute() throws SQLException {
                rs.getLong(column);
            }
        };
    }

    private static final String METADATA = "A 3 DECFLOAT 38 0 40 java.math.BigDecimal"
        + " | B 3 DECFLOAT 38 0 40 java.math.BigDecimal | C 3 DECFLOAT 38 0 40 java.math.BigDecimal"
        + " | D 3 DECFLOAT 38 0 40 java.math.BigDecimal | E 3 DECFLOAT 38 0 40 java.math.BigDecimal";

    private static final String READS = "BigDecimal:1.5@1 1.5"
        + " | BigDecimal:0.33333333333333333333333333333333333333@38 3.3333333333333333333333333333333333333e-1"
        + " | BigDecimal:1.0000000000000000303786028427003666891E+40@-3 1.0000000000000000303786028427003666891e40"
        + " | BigDecimal:1E-10@10 0.0000000001 | BigDecimal:100@0 100";

    @Test
    public void aDecfloatColumnReadsAsTheDriverReadsIt() throws SQLException {
        assertEquals(METADATA, metadata(statement, VALUES));
        assertEquals(READS, reads(statement, VALUES));
    }

    @Test
    public void anIntegerGetterTakesOnlyAWholeNumberInRange() throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT 100::DECFLOAT AS w, 1.5::DECFLOAT AS f,"
                + " 12345678901234567890123456789012345678::DECFLOAT AS b")) {
            rs.next();
            assertEquals(100L, rs.getLong(1));
            final SQLException fraction = assertThrows(SQLException.class, longOf(rs, 2));
            assertEquals("Cannot convert value in the driver from type:3 to type:long, value=1.5.",
                fraction.getMessage());
            final SQLException past = assertThrows(SQLException.class, longOf(rs, 3));
            assertEquals("Cannot convert value in the driver from type:3 to type:long,"
                + " value=12345678901234567890123456789012345678.", past.getMessage());
        }
    }

    @Test
    public void aDecfloatTableColumnIsDescribedAndBoundExactly() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE jdd (k VARCHAR, d DECFLOAT)");
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO jdd (k, d) VALUES (?, ?)")) {
            insert.setString(1, "a");
            insert.setBigDecimal(2, new BigDecimal("12345678901234567890123456789012345678"));
            insert.executeUpdate();
            insert.setString(1, "b");
            insert.setString(2, "1.25");
            insert.executeUpdate();
        }
        final StringBuilder stored = new StringBuilder();
        try (ResultSet rs = statement.executeQuery("SELECT d::VARCHAR FROM jdd ORDER BY k")) {
            while (rs.next()) {
                stored.append(stored.length() > 0 ? " | " : "").append(rs.getString(1));
            }
        }
        assertEquals("12345678901234567890123456789012345678 | 1.25", stored.toString());
        try (ResultSet cols = connection.getMetaData().getColumns(null, null, "JDD", "D")) {
            cols.next();
            assertEquals("DECFLOAT 3 38 0", cols.getString("TYPE_NAME") + " " + cols.getInt("DATA_TYPE") + " "
                + cols.getInt("COLUMN_SIZE") + " " + cols.getInt("DECIMAL_DIGITS"));
        }
    }

    /** The HTTP transport reads the same, the wire carrying each DECFLOAT as its text. */
    @Test
    public void theHttpTransportReadsAlike() throws SQLException, IOException {
        assumeFalse(LiveSnowflake.enabled(), "the engine's own HTTP transport has no live counterpart");
        final int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        final DatabaseHttpServer server = new DatabaseHttpServer(port);
        server.start();
        try (Connection http = DriverManager.getConnection("jdbc:frostlake://localhost:" + port);
             Statement over = http.createStatement()) {
            assertEquals(METADATA, metadata(over, VALUES));
            assertEquals(READS, reads(over, VALUES));
            try (ResultSet rs = over.executeQuery("SELECT 1.5::DECFLOAT")) {
                rs.next();
                final SQLException fraction = assertThrows(SQLException.class, longOf(rs, 1));
                assertEquals("Cannot convert value in the driver from type:3 to type:long, value=1.5.",
                    fraction.getMessage());
            }
        } finally {
            server.stop();
        }
    }
}
