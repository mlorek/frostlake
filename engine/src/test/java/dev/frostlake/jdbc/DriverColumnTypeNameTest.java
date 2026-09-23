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

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ResultSetMetaData} names a column's type as Snowflake's driver does: the timestamp flavours without
 * their underscore — a TIMESTAMPTZ reported as TIMESTAMP_WITH_TIMEZONE — and every approximate number DOUBLE,
 * whatever alias declared it. Live-verified.
 */
public class DriverColumnTypeNameTest extends BaseJdbcTest {

    /** Each column's name, type code and class name, a bar between columns. */
    private String metadata(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            final ResultSetMetaData md = rs.getMetaData();
            final StringBuilder out = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (i > 1) {
                    out.append(" | ");
                }
                out.append(md.getColumnTypeName(i)).append(' ').append(md.getColumnType(i)).append(' ')
                    .append(md.getColumnClassName(i));
            }
            return out.toString();
        }
    }

    @Test
    public void theTimestampFlavoursLoseTheirUnderscore() throws SQLException {
        assertEquals("TIMESTAMPNTZ " + Types.TIMESTAMP + " java.sql.Timestamp",
            metadata("SELECT '2020-01-15 10:00:00'::TIMESTAMP_NTZ"));
        assertEquals("TIMESTAMPLTZ " + Types.TIMESTAMP + " java.sql.Timestamp",
            metadata("SELECT '2020-01-15 10:00:00'::TIMESTAMP_LTZ"));
        assertEquals("TIMESTAMPTZ " + Types.TIMESTAMP_WITH_TIMEZONE + " java.sql.Timestamp",
            metadata("SELECT '2020-01-15 10:00:00 +00:00'::TIMESTAMP_TZ"));
        assertEquals("TIMESTAMPLTZ " + Types.TIMESTAMP + " java.sql.Timestamp", metadata("SELECT CURRENT_TIMESTAMP()"));
        assertEquals("TIMESTAMPNTZ " + Types.TIMESTAMP + " java.sql.Timestamp",
            metadata("SELECT '2020-01-15 10:00:00'::TIMESTAMP"));
        assertEquals("DATE " + Types.DATE + " java.sql.Date | TIME " + Types.TIME + " java.sql.Time",
            metadata("SELECT '2020-01-15'::DATE, '10:00:00'::TIME"));
    }

    @Test
    public void everyApproximateNumberIsADouble() throws SQLException {
        assertEquals("DOUBLE " + Types.DOUBLE + " java.lang.Double", metadata("SELECT 1.5::FLOAT"));
        statement.execute("CREATE OR REPLACE TABLE t543 (a FLOAT, b FLOAT4, c FLOAT8, d REAL, e DOUBLE,"
            + " f DOUBLE PRECISION, g TIMESTAMP, h DATETIME, i TIMESTAMP_NTZ(3), j TIMESTAMP_LTZ(6), k TIMESTAMP_TZ(0))");
        final String dbl = "DOUBLE " + Types.DOUBLE + " java.lang.Double";
        final String ntz = "TIMESTAMPNTZ " + Types.TIMESTAMP + " java.sql.Timestamp";
        assertEquals(dbl + " | " + dbl + " | " + dbl + " | " + dbl + " | " + dbl + " | " + dbl + " | " + ntz + " | " + ntz
            + " | " + ntz + " | TIMESTAMPLTZ " + Types.TIMESTAMP + " java.sql.Timestamp | TIMESTAMPTZ "
            + Types.TIMESTAMP_WITH_TIMEZONE + " java.sql.Timestamp", metadata("SELECT * FROM t543"));
    }

    /** The exact numbers and the text keep their names and codes. */
    @Test
    public void theOtherFamiliesAreUnchanged() throws SQLException {
        assertEquals("NUMBER " + Types.DECIMAL + " java.math.BigDecimal | NUMBER " + Types.BIGINT + " java.lang.Long | VARCHAR "
            + Types.VARCHAR + " java.lang.String | BINARY " + Types.BINARY + " [B | BOOLEAN " + Types.BOOLEAN
            + " java.lang.Boolean", metadata("SELECT 1::NUMBER(10,2), 1, 'a', X'00', TRUE"));
    }
}
