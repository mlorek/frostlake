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

import dev.frostlake.BaseJdbcTest;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The precision and the display size the driver reports for a date, a time and a timestamp column, whatever its
 * fractional digits (live-verified with the account's driver): DATE 10, TIME 8, TIMESTAMP_NTZ 23, and TIMESTAMP_LTZ
 * and TIMESTAMP_TZ 25, a table's column, an expression's and a bound timestamp's alike.
 */
public class TimestampColumnPrecisionTest extends BaseJdbcTest {

    /** Each column's type name, precision, scale and display size. */
    private static List<String> metrics(final ResultSet rows) throws SQLException {
        final ResultSetMetaData columns = rows.getMetaData();
        final List<String> metrics = new ArrayList<>();
        for (int i = 1; i <= columns.getColumnCount(); i++) {
            metrics.add(columns.getColumnTypeName(i) + "|" + columns.getPrecision(i) + "|" + columns.getScale(i) + "|"
                + columns.getColumnDisplaySize(i));
        }
        return metrics;
    }

    @Test
    public void aTableColumnAtEveryScale() throws SQLException {
        statement.execute("CREATE TABLE tp954 (ntz0 TIMESTAMP_NTZ(0), ntz9 TIMESTAMP_NTZ(9), ltz0 TIMESTAMP_LTZ(0), "
            + "ltz3 TIMESTAMP_LTZ(3), ltz9 TIMESTAMP_LTZ(9), tz0 TIMESTAMP_TZ(0), tz9 TIMESTAMP_TZ(9), t3 TIME(3), "
            + "d DATE)");
        try (ResultSet rows = statement.executeQuery("SELECT * FROM tp954")) {
            assertEquals(List.of("TIMESTAMPNTZ|23|0|23", "TIMESTAMPNTZ|23|9|23", "TIMESTAMPLTZ|25|0|25",
                "TIMESTAMPLTZ|25|3|25", "TIMESTAMPLTZ|25|9|25", "TIMESTAMPTZ|25|0|25", "TIMESTAMPTZ|25|9|25",
                "TIME|8|3|8", "DATE|10|0|10"), metrics(rows));
        }
    }

    @Test
    public void anExpressionAndABoundTimestamp() throws SQLException {
        try (ResultSet rows = statement.executeQuery("SELECT '2024-01-02 03:04:05'::TIMESTAMP_LTZ(3), "
                + "'2024-01-02 03:04:05'::TIMESTAMP_TZ(6), '2024-01-02 03:04:05'::TIMESTAMP_NTZ(1)")) {
            assertEquals(List.of("TIMESTAMPLTZ|25|3|25", "TIMESTAMPTZ|25|6|25", "TIMESTAMPNTZ|23|1|23"),
                metrics(rows));
        }
        try (PreparedStatement prepared = connection.prepareStatement("SELECT ?")) {
            prepared.setTimestamp(1, Timestamp.valueOf("2024-01-02 03:04:05.123456789"));
            try (ResultSet rows = prepared.executeQuery()) {
                assertEquals(List.of("TIMESTAMPLTZ|25|9|25"), metrics(rows));
            }
        }
    }
}
