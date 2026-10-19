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

import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JDBC Calendar overloads as the account's driver keeps them (live-verified): a Timestamp is bound at the
 * Calendar's wall clock — in a UTC session that is the TIMESTAMP_NTZ stored, in another the moment the wall clock
 * names in UTC is shown in the session's zone — a Date is bound as the day its moment falls on in the Calendar's
 * zone, a Time at the clock its moment shows in UTC whatever the Calendar; a TIMESTAMP_NTZ read with a Calendar is
 * its wall clock in the Calendar's zone, a zoned cell its own moment, a TIME is read in the JVM's zone, and in a UTC
 * session a DATE read with the UTC Calendar is its midnight there.
 */
public class CalendarBindingTest extends BaseJdbcTest {

    @Test
    public void inAUtcSession() throws SQLException {
        assertEquals(Arrays.asList(
            "2024-01-01 00:00:00.000|2024-01-01|23:30:00|2024-01-01 00:00:00.000 Z|2024-01-01 00:00:00.000 Z",
            "2024-01-01 05:30:00.000|2024-01-02|23:30:00|2024-01-01 05:30:00.000 Z|2024-01-01 05:30:00.000 Z",
            "2024-01-01T00:00:00Z 2023-12-31T18:30:00Z 1970-01-01T14:30:00Z 2024-01-01T00:00:00Z 2024-01-01T00:00:00Z"
                + " 2024-01-01T00:00:00Z",
            "2024-01-01T05:30:00Z 2024-01-01T00:00:00Z 1970-01-01T14:30:00Z 2024-01-01T05:30:00Z 2024-01-01T05:30:00Z"
                + " 2024-01-02T00:00:00Z"),
            CalendarBindingSupport.observe(connection, "UTC"));
    }

    @Test
    public void bindingsIgnoreTheSessionInputFormats() throws SQLException {
        assertEquals("2024-03-05 10:00:00.000|2024-03-05|10:15:00",
            CalendarBindingSupport.underInputFormats(connection));
    }

    @Test
    public void inANewYorkSession() throws SQLException {
        assertEquals(Arrays.asList(
            "2023-12-31 19:00:00.000|2024-01-01|23:30:00|2023-12-31 19:00:00.000 -0500|2023-12-31 19:00:00.000 -0500",
            "2024-01-01 00:30:00.000|2024-01-02|23:30:00|2024-01-01 00:30:00.000 -0500|2024-01-01 00:30:00.000 -0500",
            "2023-12-31T19:00:00Z 2023-12-31T13:30:00Z 1970-01-01T14:30:00Z 2024-01-01T00:00:00Z 2024-01-01T00:00:00Z",
            "2024-01-01T00:30:00Z 2023-12-31T19:00:00Z 1970-01-01T14:30:00Z 2024-01-01T05:30:00Z 2024-01-01T05:30:00Z"),
            CalendarBindingSupport.withoutDateReads(CalendarBindingSupport.observe(connection, "America/New_York")));
    }
}
