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

package dev.frostlake.functions;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class DateTimeFunctionsExtTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private Object q(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private long ql(final String sql) {
        return ((Number) q(sql)).longValue();
    }

    // ---- DATEADD expanded units ----
    @Test public void testDateaddDay() {
        assertTrue(q("SELECT DATEADD('day', 5, '2024-01-10')").toString().startsWith("2024-01-15"));
    }
    @Test public void testDateaddMonth() {
        assertTrue(q("SELECT DATEADD('month', 2, '2024-01-31')").toString().startsWith("2024-03"));
    }
    @Test public void testDateaddYear() {
        assertTrue(q("SELECT DATEADD('year', 1, '2024-06-15')").toString().startsWith("2025-06-15"));
    }
    @Test public void testDateaddWeek() {
        assertTrue(q("SELECT DATEADD('week', 2, '2024-01-01')").toString().startsWith("2024-01-15"));
    }
    @Test public void testDateaddHour() {
        Object r = q("SELECT DATEADD('hour', 3, '2024-01-01T10:00:00')");
        assertTrue(r.toString().contains("13:00"));
    }
    @Test public void testDateaddQuarter() {
        Object r = q("SELECT DATEADD('quarter', 1, '2024-01-01')");
        assertTrue(r.toString().startsWith("2024-04"));
    }

    // ---- DATEDIFF expanded units ----
    @Test public void testDatediffDay() {
        assertEquals(10L, ql("SELECT DATEDIFF('day', '2024-01-01', '2024-01-11')"));
    }
    @Test public void testDatediffMonth() {
        assertEquals(2L, ql("SELECT DATEDIFF('month', '2024-01-01', '2024-03-01')"));
    }
    @Test public void testDatediffYear() {
        assertEquals(3L, ql("SELECT DATEDIFF('year', '2020-01-01', '2023-01-01')"));
    }
    @Test public void testDatediffHour() {
        assertEquals(24L, ql("SELECT DATEDIFF('hour', '2024-01-01T00:00:00', '2024-01-02T00:00:00')"));
    }
    @Test public void testDatediffWeek() {
        assertEquals(2L, ql("SELECT DATEDIFF('week', '2024-01-01', '2024-01-15')"));
    }

    // ---- DATE_PART / EXTRACT ----
    @Test public void testDatePartYear() {
        assertEquals(2024L, ql("SELECT DATE_PART('year', '2024-06-15'::DATE)"));
    }
    @Test public void testDatePartMonth() {
        assertEquals(6L, ql("SELECT DATE_PART('month', '2024-06-15'::DATE)"));
    }
    @Test public void testDatePartDay() {
        assertEquals(15L, ql("SELECT DATE_PART('day', '2024-06-15'::DATE)"));
    }
    @Test public void testDatePartQuarter() {
        assertEquals(2L, ql("SELECT DATE_PART('quarter', '2024-06-15'::DATE)"));
    }
    @Test public void testDatePartHour() {
        assertEquals(10L, ql("SELECT DATE_PART('hour', '2024-06-15T10:30:45'::TIMESTAMP)"));
    }
    @Test public void testDatePartMinute() {
        assertEquals(30L, ql("SELECT DATE_PART('minute', '2024-06-15T10:30:45'::TIMESTAMP)"));
    }
    @Test public void testDatePartSecond() {
        assertEquals(45L, ql("SELECT DATE_PART('second', '2024-06-15T10:30:45'::TIMESTAMP)"));
    }
    @Test public void testExtract() {
        assertEquals(2024L, ql("SELECT EXTRACT('year', '2024-06-15'::DATE)"));
    }

    // ---- DATE_TRUNC ----
    @Test public void testDateTruncYear() {
        assertTrue(q("SELECT DATE_TRUNC('year', '2024-06-15'::DATE)").toString().startsWith("2024-01-01"));
    }
    @Test public void testDateTruncMonth() {
        assertTrue(q("SELECT DATE_TRUNC('month', '2024-06-15'::DATE)").toString().startsWith("2024-06-01"));
    }
    @Test public void testDateTruncDay() {
        assertTrue(q("SELECT DATE_TRUNC('day', '2024-06-15T10:30:00'::TIMESTAMP)").toString().startsWith("2024-06-15T00:00"));
    }
    @Test public void testDateTruncHour() {
        assertTrue(q("SELECT DATE_TRUNC('hour', '2024-06-15T10:30:45'::TIMESTAMP)").toString().contains("10:00"));
    }
    @Test public void testDateTruncQuarter() {
        assertTrue(q("SELECT DATE_TRUNC('quarter', '2024-06-15'::DATE)").toString().startsWith("2024-04-01"));
    }

    // ---- LAST_DAY ----
    @Test public void testLastDayJanuary() {
        assertEquals(LocalDate.of(2024, 1, 31), q("SELECT LAST_DAY('2024-01-15'::DATE)"));
    }
    @Test public void testLastDayFebruaryLeap() {
        assertEquals(LocalDate.of(2024, 2, 29), q("SELECT LAST_DAY('2024-02-01'::DATE)"));
    }
    @Test public void testLastDayFebruaryNonLeap() {
        assertEquals(LocalDate.of(2023, 2, 28), q("SELECT LAST_DAY('2023-02-01'::DATE)"));
    }

    // ---- NEXT_DAY / PREVIOUS_DAY ----
    @Test public void testNextDay() {
        // 2024-01-01 is a Monday; next Tuesday
        assertEquals(LocalDate.of(2024, 1, 2), q("SELECT NEXT_DAY('2024-01-01'::DATE, 'Tuesday')"));
    }
    @Test public void testPreviousDay() {
        // 2024-01-05 is a Friday; previous Monday
        assertEquals(LocalDate.of(2024, 1, 1), q("SELECT PREVIOUS_DAY('2024-01-05'::DATE, 'Monday')"));
    }

    // ---- ADD_MONTHS ----
    // A VARCHAR input is implicitly cast to TIMESTAMP_NTZ (live-verified), so the result is a
    // timestamp at midnight; only a DATE input yields a DATE (see AddMonthsTest).
    @Test public void testAddMonths() {
        assertEquals(LocalDateTime.of(2024, 3, 15, 0, 0), q("SELECT ADD_MONTHS('2024-01-15', 2)"));
    }
    @Test public void testAddMonthsNegative() {
        assertEquals(LocalDateTime.of(2023, 11, 15, 0, 0), q("SELECT ADD_MONTHS('2024-01-15', -2)"));
    }

    // ---- MONTHS_BETWEEN ----
    // VARCHAR arguments (even literals) are rejected — MONTHS_BETWEEN needs temporal inputs.
    @Test public void testMonthsBetween() {
        double v = ((Number) q("SELECT MONTHS_BETWEEN('2024-03-15'::DATE, '2024-01-15'::DATE)")).doubleValue();
        assertEquals(2.0, v, 0.01);
    }

    // ---- DATE_FROM_PARTS ----
    @Test public void testDateFromParts() {
        assertEquals(LocalDate.of(2024, 6, 15), q("SELECT DATE_FROM_PARTS(2024, 6, 15)"));
    }

    // ---- TIMESTAMP_FROM_PARTS ----
    @Test public void testTimestampFromParts() {
        Object r = q("SELECT TIMESTAMP_FROM_PARTS(2024, 6, 15, 10, 30, 45)");
        assertEquals(LocalDateTime.of(2024, 6, 15, 10, 30, 45), r);
    }

    // ---- Accessor functions ----
    @Test public void testYear() {
        assertEquals(2024L, ql("SELECT YEAR('2024-06-15'::DATE)"));
    }
    @Test public void testMonth() {
        assertEquals(6L, ql("SELECT MONTH('2024-06-15'::DATE)"));
    }
    @Test public void testDay() {
        assertEquals(15L, ql("SELECT DAY('2024-06-15'::DATE)"));
    }
    @Test public void testHour() {
        assertEquals(10L, ql("SELECT HOUR('2024-06-15T10:30:45'::TIMESTAMP)"));
    }
    @Test public void testMinute() {
        assertEquals(30L, ql("SELECT MINUTE('2024-06-15T10:30:45'::TIMESTAMP)"));
    }
    @Test public void testSecond() {
        assertEquals(45L, ql("SELECT SECOND('2024-06-15T10:30:45'::TIMESTAMP)"));
    }
    @Test public void testQuarter() {
        assertEquals(2L, ql("SELECT QUARTER('2024-06-15'::DATE)"));
        assertEquals(1L, ql("SELECT QUARTER('2024-01-01'::DATE)"));
        assertEquals(4L, ql("SELECT QUARTER('2024-12-31'::DATE)"));
    }
    @Test public void testWeekOfYear() {
        assertEquals(1L, ql("SELECT WEEKOFYEAR('2024-01-01'::DATE)"));
    }
    @Test public void testDayOfYear() {
        assertEquals(1L, ql("SELECT DAYOFYEAR('2024-01-01'::DATE)"));
        assertEquals(366L, ql("SELECT DAYOFYEAR('2024-12-31'::DATE)")); // 2024 is leap
    }

    // ---- CURRENT_TIME returns non-null ----
    @Test public void testCurrentTime() {
        assertNotNull(q("SELECT CURRENT_TIME()"));
    }
}
