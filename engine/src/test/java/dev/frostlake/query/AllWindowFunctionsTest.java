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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive test demonstrating all window functions together
 */
public class AllWindowFunctionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE employees (id INTEGER, name VARCHAR, department VARCHAR, salary INTEGER)");
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', 'Engineering', 100000)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 'Engineering', 95000)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 'Engineering', 95000)");
        engine.execute("INSERT INTO employees VALUES (4, 'David', 'Sales', 85000)");
        engine.execute("INSERT INTO employees VALUES (5, 'Eve', 'Sales', 90000)");
    }

    @Test
    public void testAllWindowFunctionsTogether() {
        // Query that uses all window functions
        final ResultSet result = engine.executeQuery("""
            SELECT
            name,
            department,
            salary,
            ROW_NUMBER() OVER (ORDER BY salary DESC) as row_num,
            RANK() OVER (ORDER BY salary DESC) as rank,
            DENSE_RANK() OVER (ORDER BY salary DESC) as dense_rank,
            LAG(salary) OVER (ORDER BY salary DESC) as prev_salary,
            LEAD(salary) OVER (ORDER BY salary DESC) as next_salary
            FROM employees
            """);

        assertEquals(5, result.getRowCount());

        // Find and verify Alice (highest salary - 100000)
        Row aliceRow = null;
        for (final Row r : result.getRows()) {
            if ("Alice".equals(r.getValue(0))) {
                aliceRow = r;
                break;
            }
        }
        assertNotNull(aliceRow, "Should find Alice in results");
        assertEquals(100000L, aliceRow.getValue(2));
        assertEquals(1L, aliceRow.getValue(3), "Alice row_num");
        assertEquals(1L, aliceRow.getValue(4), "Alice rank");
        assertEquals(1L, aliceRow.getValue(5), "Alice dense_rank");
        assertNull(aliceRow.getValue(6), "Alice LAG should be NULL (first in order)");
        assertEquals(95000L, aliceRow.getValue(7), "Alice LEAD should be 95000");

        // Find and verify David (lowest salary - 85000)
        Row davidRow = null;
        for (final Row r : result.getRows()) {
            if ("David".equals(r.getValue(0))) {
                davidRow = r;
                break;
            }
        }
        assertNotNull(davidRow, "Should find David in results");
        assertEquals(85000L, davidRow.getValue(2));
        assertEquals(5L, davidRow.getValue(3), "David row_num");
        assertEquals(5L, davidRow.getValue(4), "David rank");
        assertEquals(4L, davidRow.getValue(5), "David dense_rank");
        assertEquals(90000L, davidRow.getValue(6), "David LAG should be 90000");
        assertNull(davidRow.getValue(7), "David LEAD should be NULL (last in order)");
    }

    @Test
    public void testWindowFunctionsWithLagLeadOffsets() {
        // Test LAG and LEAD with different offsets
        final ResultSet result = engine.executeQuery("""
            SELECT
            name,
            salary,
            LAG(salary, 1) OVER (ORDER BY salary DESC) as lag1,
            LAG(salary, 2) OVER (ORDER BY salary DESC) as lag2,
            LEAD(salary, 1) OVER (ORDER BY salary DESC) as lead1,
            LEAD(salary, 2) OVER (ORDER BY salary DESC) as lead2
            FROM employees
            """);

        assertEquals(5, result.getRowCount());

        // Find Alice (highest salary)
        Row aliceRow = null;
        for (final Row r : result.getRows()) {
            if ("Alice".equals(r.getValue(0))) {
                aliceRow = r;
                break;
            }
        }
        assertNotNull(aliceRow);
        assertNull(aliceRow.getValue(2), "Alice LAG(1) should be NULL");
        assertNull(aliceRow.getValue(3), "Alice LAG(2) should be NULL");
        assertNotNull(aliceRow.getValue(4), "Alice LEAD(1) should not be NULL");
        assertNotNull(aliceRow.getValue(5), "Alice LEAD(2) should not be NULL");

        // Find David (lowest salary)
        Row davidRow = null;
        for (final Row r : result.getRows()) {
            if ("David".equals(r.getValue(0))) {
                davidRow = r;
                break;
            }
        }
        assertNotNull(davidRow);
        assertNotNull(davidRow.getValue(2), "David LAG(1) should not be NULL");
        assertNotNull(davidRow.getValue(3), "David LAG(2) should not be NULL");
        assertNull(davidRow.getValue(4), "David LEAD(1) should be NULL");
        assertNull(davidRow.getValue(5), "David LEAD(2) should be NULL");
    }

    @Test
    public void testWindowFunctionsWithDefaults() {
        // Test LAG and LEAD with default values
        final ResultSet result = engine.executeQuery("""
            SELECT
            name,
            salary,
            LAG(salary, 1, 0) OVER (ORDER BY salary DESC) as prev_salary,
            LEAD(salary, 1, 0) OVER (ORDER BY salary DESC) as next_salary
            FROM employees
            """);

        assertEquals(5, result.getRowCount());

        // Find Alice (first in order) - should have default 0 for LAG
        Row aliceRow = null;
        for (final Row r : result.getRows()) {
            if ("Alice".equals(r.getValue(0))) {
                aliceRow = r;
                break;
            }
        }
        assertNotNull(aliceRow);
        assertEquals(0L, aliceRow.getValue(2), "Alice LAG should be default 0");

        // Find David (last in order) - should have default 0 for LEAD
        Row davidRow = null;
        for (final Row r : result.getRows()) {
            if ("David".equals(r.getValue(0))) {
                davidRow = r;
                break;
            }
        }
        assertNotNull(davidRow);
        assertEquals(0L, davidRow.getValue(3), "David LEAD should be default 0");
    }

    @Test
    public void testCompareCurrentWithPreviousAndNext() {
        // Practical example: using LAG and LEAD with defaults
        final ResultSet result = engine.executeQuery("""
            SELECT
            name,
            salary,
            LAG(salary, 1, 999999) OVER (ORDER BY salary DESC) as prev_salary,
            LEAD(salary, 1, 0) OVER (ORDER BY salary DESC) as next_salary
            FROM employees
            """);

        assertEquals(5, result.getRowCount());

        // Find Alice - should have default 999999 for LAG
        Row aliceRow = null;
        for (final Row r : result.getRows()) {
            if ("Alice".equals(r.getValue(0))) {
                aliceRow = r;
                break;
            }
        }
        assertNotNull(aliceRow);
        assertEquals(999999L, aliceRow.getValue(2), "Alice LAG should be default 999999");

        // Find David - should have default 0 for LEAD
        Row davidRow = null;
        for (final Row r : result.getRows()) {
            if ("David".equals(r.getValue(0))) {
                davidRow = r;
                break;
            }
        }
        assertNotNull(davidRow);
        assertEquals(0L, davidRow.getValue(3), "David LEAD should be default 0");

        // Find Eve (middle row) - should have actual prev/next values
        Row eveRow = null;
        for (final Row r : result.getRows()) {
            if ("Eve".equals(r.getValue(0))) {
                eveRow = r;
                break;
            }
        }
        assertNotNull(eveRow);
        assertEquals(90000L, eveRow.getValue(1), "Eve salary");
        assertTrue((Long) eveRow.getValue(2) > 0 && (Long) eveRow.getValue(2) < 999999,
            "Eve LAG should be an actual salary value");
        assertTrue((Long) eveRow.getValue(3) > 0,
            "Eve LEAD should be an actual salary value");
    }
}
