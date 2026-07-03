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

package dev.frostlake.expressions;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntervalExpressionTest extends BaseJdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(IntervalExpressionTest.class);

    @Test
    public void testIntervalDays() throws SQLException {
        logger.info("Testing INTERVAL '10' DAYS");

        ResultSet rs = statement.executeQuery("SELECT INTERVAL '10' DAYS");
        rs.next();
        String result = rs.getString(1);
        assertNotNull(result);
        assertTrue(result.contains("10"));
        assertTrue(result.toUpperCase().contains("DAY"));
    }

    @Test
    public void testIntervalDay() throws SQLException {
        logger.info("Testing INTERVAL '1' DAY");

        ResultSet rs = statement.executeQuery("SELECT INTERVAL '1' DAY");
        rs.next();
        String result = rs.getString(1);
        assertNotNull(result);
        assertTrue(result.contains("1"));
        assertTrue(result.toUpperCase().contains("DAY"));
    }

    @Test
    public void testIntervalHours() throws SQLException {
        logger.info("Testing INTERVAL '5' HOURS");

        ResultSet rs = statement.executeQuery("SELECT INTERVAL '5' HOURS");
        rs.next();
        String result = rs.getString(1);
        assertNotNull(result);
        assertTrue(result.contains("5"));
        assertTrue(result.toUpperCase().contains("HOUR"));
    }

    @Test
    public void testIntervalMinutes() throws SQLException {
        logger.info("Testing INTERVAL '30' MINUTES");

        ResultSet rs = statement.executeQuery("SELECT INTERVAL '30' MINUTES");
        rs.next();
        String result = rs.getString(1);
        assertNotNull(result);
        assertTrue(result.contains("30"));
        assertTrue(result.toUpperCase().contains("MINUTE"));
    }

    @Test
    public void testIntervalSeconds() throws SQLException {
        logger.info("Testing INTERVAL '45' SECONDS");

        ResultSet rs = statement.executeQuery("SELECT INTERVAL '45' SECONDS");
        rs.next();
        String result = rs.getString(1);
        assertNotNull(result);
        assertTrue(result.contains("45"));
        assertTrue(result.toUpperCase().contains("SECOND"));
    }

    @Test
    public void testIntervalMonths() throws SQLException {
        logger.info("Testing INTERVAL '3' MONTHS");

        ResultSet rs = statement.executeQuery("SELECT INTERVAL '3' MONTHS");
        rs.next();
        String result = rs.getString(1);
        assertNotNull(result);
        assertTrue(result.contains("3"));
        assertTrue(result.toUpperCase().contains("MONTH"));
    }

    @Test
    public void testIntervalYears() throws SQLException {
        logger.info("Testing INTERVAL '2' YEARS");

        ResultSet rs = statement.executeQuery("SELECT INTERVAL '2' YEARS");
        rs.next();
        String result = rs.getString(1);
        assertNotNull(result);
        assertTrue(result.contains("2"));
        assertTrue(result.toUpperCase().contains("YEAR"));
    }

    @Test
    public void testIntervalWithNumericValue() throws SQLException {
        logger.info("Testing INTERVAL with numeric value");

        ResultSet rs = statement.executeQuery("SELECT INTERVAL 7 DAYS");
        rs.next();
        String result = rs.getString(1);
        assertNotNull(result);
        assertTrue(result.contains("7"));
        assertTrue(result.toUpperCase().contains("DAY"));
    }

    @Test
    public void testIntervalInTable() throws SQLException {
        logger.info("Testing INTERVAL in table query");

        statement.execute("CREATE TABLE events (id INTEGER, event_name STRING, duration_days INTEGER)");
        statement.execute("INSERT INTO events VALUES (1, 'Conference', 3)");
        statement.execute("INSERT INTO events VALUES (2, 'Workshop', 1)");

        ResultSet rs = statement.executeQuery("SELECT event_name, INTERVAL duration_days DAYS FROM events ORDER BY id");

        rs.next();
        assertEquals("Conference", rs.getString(1));
        String interval1 = rs.getString(2);
        assertTrue(interval1.contains("3"));
        assertTrue(interval1.toUpperCase().contains("DAY"));

        rs.next();
        assertEquals("Workshop", rs.getString(1));
        String interval2 = rs.getString(2);
        assertTrue(interval2.contains("1"));
        assertTrue(interval2.toUpperCase().contains("DAY"));
    }

    @Test
    public void testIntervalSingularUnits() throws SQLException {
        logger.info("Testing INTERVAL with singular units");

        ResultSet rs1 = statement.executeQuery("SELECT INTERVAL '1' YEAR");
        rs1.next();
        String result1 = rs1.getString(1);
        assertTrue(result1.toUpperCase().contains("YEAR"));

        ResultSet rs2 = statement.executeQuery("SELECT INTERVAL '1' MONTH");
        rs2.next();
        String result2 = rs2.getString(1);
        assertTrue(result2.toUpperCase().contains("MONTH"));

        ResultSet rs3 = statement.executeQuery("SELECT INTERVAL '1' HOUR");
        rs3.next();
        String result3 = rs3.getString(1);
        assertTrue(result3.toUpperCase().contains("HOUR"));

        ResultSet rs4 = statement.executeQuery("SELECT INTERVAL '1' MINUTE");
        rs4.next();
        String result4 = rs4.getString(1);
        assertTrue(result4.toUpperCase().contains("MINUTE"));

        ResultSet rs5 = statement.executeQuery("SELECT INTERVAL '1' SECOND");
        rs5.next();
        String result5 = rs5.getString(1);
        assertTrue(result5.toUpperCase().contains("SECOND"));
    }
}
