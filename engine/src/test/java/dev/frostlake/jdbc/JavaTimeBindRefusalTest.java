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

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The driver binds no java.time value, as the account's driver binds none (live-verified): a LocalDate, a
 * LocalDateTime, a LocalTime, an OffsetDateTime and an Instant are each refused with
 * {@code Data type not supported for binding: Object type: class java.time.LocalDate.}, whatever the column and the
 * session's input formats, while a java.sql.Date binds as a typed value the session's DATE_INPUT_FORMAT does not read.
 */
public class JavaTimeBindRefusalTest extends BaseJdbcTest {

    /** What binding a value into a column answers: the column's text read back, or the refusal. */
    private String bind(final String column, final Object value) {
        try {
            statement.execute("DELETE FROM jtb");
            try (PreparedStatement insert =
                     connection.prepareStatement("INSERT INTO jtb (" + column + ") VALUES (?)")) {
                insert.setObject(1, value);
                insert.executeUpdate();
            }
            try (ResultSet rs = statement.executeQuery("SELECT TO_VARCHAR(" + column + ") FROM jtb")) {
                return rs.next() ? String.valueOf(rs.getString(1)) : "no row";
            }
        } catch (final SQLException refused) {
            return "REFUSED " + refused.getMessage();
        }
    }

    @Test
    public void aJavaTimeValueIsRefusedAndASqlDateBinds() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE jtb (d DATE, ts TIMESTAMP_NTZ, t TIME, z TIMESTAMP_TZ, v VARCHAR)");
        final List<String> answers = new ArrayList<String>();
        answers.add(bind("d", LocalDate.of(2024, 3, 5)));
        answers.add(bind("ts", LocalDateTime.of(2024, 3, 5, 6, 7, 8)));
        answers.add(bind("t", LocalTime.of(6, 7, 8)));
        answers.add(bind("z", OffsetDateTime.of(2024, 3, 5, 6, 7, 8, 0, ZoneOffset.ofHours(2))));
        answers.add(bind("z", Instant.parse("2024-03-05T06:07:08Z")));
        answers.add(bind("v", LocalDate.of(2024, 3, 5)));
        statement.execute("ALTER SESSION SET DATE_INPUT_FORMAT = 'DD/MM/YYYY'");
        try {
            answers.add(bind("d", LocalDate.of(2024, 3, 5)));
            answers.add(bind("d", Date.valueOf(LocalDate.of(2024, 3, 5))));
        } finally {
            statement.execute("ALTER SESSION UNSET DATE_INPUT_FORMAT");
        }
        final String refused = "REFUSED Data type not supported for binding: Object type: class java.time.";
        assertEquals(String.join("\n", refused + "LocalDate.", refused + "LocalDateTime.", refused + "LocalTime.",
            refused + "OffsetDateTime.", refused + "Instant.", refused + "LocalDate.", refused + "LocalDate.",
            "2024-03-05"), String.join("\n", answers));
    }
}
