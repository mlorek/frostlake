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
import org.junit.jupiter.api.function.Executable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A column label two columns share but for case names the first of them in every getter and in findColumn, as the
 * account's driver reads it (live-verified): {@code getDate("A")} and {@code getDate("A", cal)} read the same column,
 * with CLIENT_RESULT_COLUMN_CASE_INSENSITIVE on or off; a label no column has is "Column not found".
 */
public class CaseOnlyColumnLabelTest extends BaseJdbcTest {

    private static final String LABELS = "SELECT '2024-01-01'::DATE AS \"a\", '2025-05-05'::DATE AS \"A\", 1 AS \"n\","
        + " 2 AS \"N\", '2024-01-01 01:00:00'::TIMESTAMP_NTZ AS \"t\", '2025-05-05 05:00:00'::TIMESTAMP_NTZ AS \"T\","
        + " 'x' AS \"q\"";

    private void assertLabelsReadTheFirstColumn() throws SQLException {
        final Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        try (ResultSet rs = statement.executeQuery(LABELS)) {
            rs.next();
            assertEquals("2024-01-01", rs.getString("A"));
            assertEquals("2024-01-01", rs.getString("a"));
            assertEquals("2024-01-01", String.valueOf(rs.getObject("A")));
            assertEquals("2024-01-01", String.valueOf(rs.getDate("A")));
            assertEquals("2024-01-01", String.valueOf(rs.getDate("A", utc)));
            assertEquals("2024-01-01", String.valueOf(rs.getObject("A", java.sql.Date.class)));
            assertEquals(1, rs.findColumn("A"));
            assertEquals(1, rs.findColumn("a"));
            assertEquals(1, rs.getInt("N"));
            assertEquals(3, rs.findColumn("N"));
            assertEquals(rs.getTimestamp(5), rs.getTimestamp("T"));
            assertEquals(rs.getTimestamp(5, utc), rs.getTimestamp("T", utc));
            assertEquals(rs.getTimestamp(5), rs.getObject("T", Timestamp.class));
            assertEquals(5, rs.findColumn("T"));
            assertEquals("x", rs.getString("Q"));
            assertEquals(7, rs.findColumn("Q"));
            assertEquals("Column not found: zz", assertThrows(SQLException.class, new Executable() {
                @Override
                public void execute() throws SQLException {
                    rs.findColumn("zz");
                }
            }).getMessage());
        }
    }

    @Test
    public void aSharedLabelNamesTheFirstColumnEverywhere() throws SQLException {
        assertLabelsReadTheFirstColumn();
        statement.execute("ALTER SESSION SET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = FALSE");
        try {
            assertLabelsReadTheFirstColumn();
        } finally {
            statement.execute("ALTER SESSION SET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = TRUE");
        }
    }
}
