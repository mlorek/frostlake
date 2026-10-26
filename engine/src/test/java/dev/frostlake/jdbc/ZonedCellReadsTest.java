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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * A temporal cell read through the driver as the account's driver reads it (see {@link TemporalCellReads}): a
 * TIMESTAMP_LTZ's or a TIMESTAMP_TZ's moment printed in its zone, a TIMESTAMP_NTZ's getDate keeping its time of day
 * and its getTime read in the session's zone, a TIME's getTimestamp in UTC, and a DATE's getObject and Calendar reads
 * at the session's midnight (all live-verified). The answers are the account's ({@link ZonedCellReadsSupport}), the
 * JVM on the zone they were measured in.
 */
public class ZonedCellReadsTest extends BaseJdbcTest {

    private static TimeZone jvmZone;

    @BeforeAll
    public static void pinJvmZone() {
        jvmZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(ZonedCellReadsSupport.JVM_ZONE));
    }

    @AfterAll
    public static void restoreJvmZone() {
        TimeZone.setDefault(jvmZone);
    }

    @Test
    public void aLosAngelesSessionReadsEachCell() throws SQLException {
        statement.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        ZonedCellReadsSupport.table(statement);
        final List<String> answers = new ArrayList<String>();
        ZonedCellReadsSupport.read(connection, null, answers);
        ZonedCellReadsSupport.assertAnswers(ZonedCellReadsSupport.LOS_ANGELES, answers);
    }

    @Test
    public void aLosAngelesSessionReadsEachCellWithACalendar() throws SQLException {
        statement.execute("ALTER SESSION SET TIMEZONE = 'America/Los_Angeles'");
        ZonedCellReadsSupport.table(statement);
        final List<String> answers = new ArrayList<String>();
        ZonedCellReadsSupport.read(connection, ZonedCellReadsSupport.calendar("Asia/Kolkata"), answers);
        ZonedCellReadsSupport.read(connection, ZonedCellReadsSupport.calendar("UTC"), answers);
        ZonedCellReadsSupport.assertAnswers(ZonedCellReadsSupport.LOS_ANGELES_CALENDARS, answers);
    }

    @Test
    public void utcAndTokyoSessionsReadEachCell() throws SQLException {
        statement.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        ZonedCellReadsSupport.table(statement);
        final List<String> answers = new ArrayList<String>();
        ZonedCellReadsSupport.read(connection, null, answers);
        ZonedCellReadsSupport.read(connection, ZonedCellReadsSupport.calendar("Asia/Kolkata"), answers);
        statement.execute("ALTER SESSION SET TIMEZONE = 'Asia/Tokyo'");
        ZonedCellReadsSupport.read(connection, null, answers);
        ZonedCellReadsSupport.read(connection, ZonedCellReadsSupport.calendar("America/New_York"), answers);
        ZonedCellReadsSupport.assertAnswers(ZonedCellReadsSupport.UTC_AND_TOKYO, answers);
    }
}
