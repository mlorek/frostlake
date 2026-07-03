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
 * Window functions with PARTITION BY: results must be computed per partition, not across all rows.
 *
 * <p>Each assertion is deliberately partition-sensitive — it yields a different answer if PARTITION BY
 * is ignored (the previous behaviour, which treated the whole result as a single partition). Data: 2
 * Engineering rows (salaries 100, 90) and 3 Sales rows (80, 70, 60).
 */
public class WindowPartitionByTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE emp (id INTEGER, dept VARCHAR, salary INTEGER)");
        engine.execute("INSERT INTO emp VALUES (1, 'Eng', 100)");
        engine.execute("INSERT INTO emp VALUES (2, 'Eng', 90)");
        engine.execute("INSERT INTO emp VALUES (3, 'Sales', 80)");
        engine.execute("INSERT INTO emp VALUES (4, 'Sales', 70)");
        engine.execute("INSERT INTO emp VALUES (5, 'Sales', 60)");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static long valueForId(final ResultSet rs, final int id, final String column) {
        rs.reset();
        while (rs.next()) {
            if (((Number) rs.getValue("id")).intValue() == id) {
                return ((Number) rs.getValue(column)).longValue();
            }
        }
        throw new AssertionError("id not found: " + id);
    }

    @Test
    public void testRowNumberValuesRestartPerPartition() {
        // The decisive check: id=3 (Sales, salary 80) is the 3rd-highest salary overall but the TOP of
        // the Sales partition, so its per-partition ROW_NUMBER is 1 (it would be 3 if PARTITION BY were
        // ignored). id=5 (Sales, lowest) is 3rd within Sales.
        ResultSet result = engine.executeQuery("""
            SELECT id, ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn
            FROM emp ORDER BY id
            """);
        assertEquals(2L, valueForId(result, 2, "rn"), "Eng second-highest -> rn 2");
        assertEquals(1L, valueForId(result, 3, "rn"), "Sales highest -> rn restarts at 1");
        assertEquals(3L, valueForId(result, 5, "rn"), "Sales lowest -> rn 3 within Sales");
    }

    @Test
    public void testRowNumberFirstPerPartition() {
        // ROW_NUMBER = 1 once per partition -> one Eng + one Sales = 2 rows (whole-result would give 1).
        ResultSet result = engine.executeQuery("""
            SELECT id, ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn
            FROM emp
            QUALIFY rn = 1
            """);
        assertEquals(2, result.getRowCount(), "ROW_NUMBER restarts at 1 in each partition");
    }

    @Test
    public void testTopTwoPerPartition() {
        // Top 2 per partition: Eng has 2, Sales top 2 -> 4 rows (whole-result would give 2).
        ResultSet result = engine.executeQuery("""
            SELECT id, ROW_NUMBER() OVER (PARTITION BY dept ORDER BY salary DESC) AS rn
            FROM emp
            QUALIFY rn <= 2
            """);
        assertEquals(4, result.getRowCount(), "Top 2 per partition across 2 partitions");
    }

    @Test
    public void testRankPerPartition() {
        // RANK = 1 once per partition -> 2 rows (whole-result would give 1).
        ResultSet result = engine.executeQuery("""
            SELECT id, RANK() OVER (PARTITION BY dept ORDER BY salary DESC) AS rk
            FROM emp
            QUALIFY rk = 1
            """);
        assertEquals(2, result.getRowCount(), "RANK restarts in each partition");
    }

    @Test
    public void testMultiColumnPartition() {
        // PARTITION BY (dept, salary): every (dept, salary) pair is unique, so each row is its own
        // partition and ROW_NUMBER is always 1 -> all 5 rows qualify.
        ResultSet result = engine.executeQuery("""
            SELECT id, ROW_NUMBER() OVER (PARTITION BY dept, salary ORDER BY id) AS rn
            FROM emp
            QUALIFY rn = 1
            """);
        assertEquals(5, result.getRowCount(), "Each (dept, salary) pair is its own partition");
    }

    @Test
    public void testNoPartitionUnaffected() {
        // No PARTITION BY -> single partition (all rows): top 2 overall = 2 rows.
        ResultSet result = engine.executeQuery("""
            SELECT id, ROW_NUMBER() OVER (ORDER BY salary DESC) AS rn
            FROM emp
            QUALIFY rn <= 2
            """);
        assertEquals(2, result.getRowCount(), "Without PARTITION BY all rows form one partition");
    }
}
