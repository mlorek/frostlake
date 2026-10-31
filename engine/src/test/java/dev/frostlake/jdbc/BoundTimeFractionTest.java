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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A bound java.sql.Time keeps its milliseconds, at the clock its moment shows in UTC, whichever setter binds it -
 * setTime, setTime with a Calendar, setObject and setObject as Types.TIME - as the account's driver binds it
 * (live-verified); a text keeps them too, and a java.time.LocalTime is no value the driver binds.
 */
public class BoundTimeFractionTest extends BaseJdbcTest {

    /** 23:30:00.250 on 1970-01-01 in UTC. */
    private static final long MOMENT = 84_600_250L;

    private String bound(final String how) throws SQLException {
        try (PreparedStatement prepared = connection.prepareStatement("SELECT ?::TIME::VARCHAR")) {
            final Time time = new Time(MOMENT);
            switch (how) {
                case "setTime":
                    prepared.setTime(1, time);
                    break;
                case "setTimeCal":
                    prepared.setTime(1, time, Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo")));
                    break;
                case "setObject":
                    prepared.setObject(1, time);
                    break;
                case "setObjectTime":
                    prepared.setObject(1, time, Types.TIME);
                    break;
                default:
                    prepared.setString(1, "23:30:00.250");
                    break;
            }
            try (ResultSet rs = prepared.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    @Test
    public void aBoundTimeKeepsItsMilliseconds() throws SQLException {
        statement.execute("ALTER SESSION SET TIME_OUTPUT_FORMAT = 'HH24:MI:SS.FF9'");
        try {
            assertEquals("23:30:00.250000000", bound("setTime"));
            assertEquals("23:30:00.250000000", bound("setTimeCal"));
            assertEquals("23:30:00.250000000", bound("setObject"));
            assertEquals("23:30:00.250000000", bound("setObjectTime"));
            assertEquals("23:30:00.250000000", bound("setString"));
        } finally {
            statement.execute("ALTER SESSION UNSET TIME_OUTPUT_FORMAT");
        }
    }

    @Test
    public void aLocalTimeIsNoValueToBind() throws SQLException {
        try (PreparedStatement prepared = connection.prepareStatement("SELECT ?::TIME::VARCHAR")) {
            assertEquals("Data type not supported for binding: Object type: class java.time.LocalTime.",
                assertThrows(SQLException.class, new Executable() {
                    @Override
                    public void execute() throws SQLException {
                        prepared.setObject(1, LocalTime.of(23, 30, 0, 250_000_000));
                        prepared.executeQuery();
                    }
                }).getMessage());
        }
    }
}
