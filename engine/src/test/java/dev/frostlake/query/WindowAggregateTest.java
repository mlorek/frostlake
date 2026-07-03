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

package dev.frostlake.query;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Window AGGREGATES — SUM/AVG/MIN/MAX with OVER. These previously returned NULL because the OVER dispatch
 * switch had cases only for ranking/value functions, so SUM/AVG/MIN/MAX fell through to the "unsupported"
 * default. Computed over the whole partition (Snowflake's default frame without ORDER BY). Data: Eng
 * (100, 90) and Sales (80, 70, 60).
 */
public class WindowAggregateTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE emp (id INTEGER, dept VARCHAR, salary INTEGER)");
        engine.execute("INSERT INTO emp VALUES (1,'Eng',100),(2,'Eng',90),(3,'Sales',80),(4,'Sales',70),(5,'Sales',60)");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static double valueForId(final ResultSet rs, final int id, final String column) {
        rs.reset();
        while (rs.next()) {
            if (((Number) rs.getValue("id")).intValue() == id) {
                return ((Number) rs.getValue(column)).doubleValue();
            }
        }
        throw new AssertionError("id not found: " + id);
    }

    @Test
    public void sumOverPartitionIsPerPartition() {
        // Previously NULL; must be the per-partition total (whole result would be 400 for everyone).
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(salary) OVER (PARTITION BY dept) AS s FROM emp");
        assertEquals(190.0, valueForId(rs, 1, "s"), "Eng partition sum 100+90");
        assertEquals(210.0, valueForId(rs, 3, "s"), "Sales partition sum 80+70+60");
    }

    @Test
    public void avgMinMaxOverPartition() {
        final ResultSet rs = engine.executeQuery("""
            SELECT id,
                   AVG(salary) OVER (PARTITION BY dept) AS a,
                   MIN(salary) OVER (PARTITION BY dept) AS mn,
                   MAX(salary) OVER (PARTITION BY dept) AS mx
            FROM emp
            """);
        assertEquals(95.0, valueForId(rs, 1, "a"), "Eng avg");
        assertEquals(70.0, valueForId(rs, 3, "a"), "Sales avg");
        assertEquals(90.0, valueForId(rs, 1, "mn"), "Eng min");
        assertEquals(100.0, valueForId(rs, 1, "mx"), "Eng max");
        assertEquals(60.0, valueForId(rs, 3, "mn"), "Sales min");
        assertEquals(80.0, valueForId(rs, 3, "mx"), "Sales max");
    }

    @Test
    public void sumWithoutPartitionIsWholeResult() {
        final ResultSet rs = engine.executeQuery("SELECT id, SUM(salary) OVER () AS s FROM emp");
        assertEquals(400.0, valueForId(rs, 1, "s"), "no PARTITION BY -> sum over all rows");
    }
}
