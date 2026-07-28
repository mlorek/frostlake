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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConditionalFunctionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Create test table
        engine.execute("CREATE TABLE test_data (id INTEGER, name VARCHAR, value NUMBER, flag BOOLEAN)");
        engine.execute("INSERT INTO test_data VALUES (1, 'Alice', 100, true)");
        engine.execute("INSERT INTO test_data VALUES (2, 'Bob', 200, false)");
        engine.execute("INSERT INTO test_data VALUES (3, 'Charlie', 100, true)");
        engine.execute("INSERT INTO test_data VALUES (4, NULL, NULL, NULL)");
        engine.execute("INSERT INTO test_data VALUES (5, 'Eve', 300, false)");
    }

    // NULLIF Tests

    @Test
    public void testNullIfEqual() {
        ResultSet result = engine.executeQuery(
            "SELECT name, NULLIF(value, 100) as adjusted_value FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // value is 100, equals 100, so NULLIF returns NULL
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("adjusted_value")));
    }

    @Test
    public void testNullIfNotEqual() {
        ResultSet result = engine.executeQuery(
            "SELECT name, NULLIF(value, 100) as adjusted_value FROM test_data WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        // value is 200, not equal to 100, so NULLIF returns 200
        assertEquals(200, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("adjusted_value"))).intValue());
    }

    @Test
    public void testNullIfWithNull() {
        ResultSet result = engine.executeQuery(
            "SELECT name, NULLIF(value, 100) as adjusted_value FROM test_data WHERE id = 4"
        );
        assertEquals(1, result.getRowCount());
        // value is NULL, NULLIF returns NULL (first argument)
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("adjusted_value")));
    }

    @Test
    public void testNullIfStrings() {
        ResultSet result = engine.executeQuery(
            "SELECT id, NULLIF(name, 'Bob') as filtered_name FROM test_data WHERE id IN (1, 2)"
        );
        assertEquals(2, result.getRowCount());
        // Alice != Bob, returns Alice
        assertEquals("Alice", result.getRows().get(0).getValue(result.getColumnIndex("filtered_name")));
        // Bob == Bob, returns NULL
        assertNull(result.getRows().get(1).getValue(result.getColumnIndex("filtered_name")));
    }

    // IFF Tests

    @Test
    public void testIffTrue() {
        ResultSet result = engine.executeQuery(
            "SELECT name, IFF(flag, 'Active', 'Inactive') as status FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("Active", result.getRows().get(0).getValue(result.getColumnIndex("status")));
    }

    @Test
    public void testIffFalse() {
        ResultSet result = engine.executeQuery(
            "SELECT name, IFF(flag, 'Active', 'Inactive') as status FROM test_data WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("Inactive", result.getRows().get(0).getValue(result.getColumnIndex("status")));
    }

    @Test
    public void testIffWithNull() {
        ResultSet result = engine.executeQuery(
            "SELECT name, IFF(flag, 'Active', 'Inactive') as status FROM test_data WHERE id = 4"
        );
        assertEquals(1, result.getRowCount());
        // NULL condition is falsy, returns false branch
        assertEquals("Inactive", result.getRows().get(0).getValue(result.getColumnIndex("status")));
    }

    @Test
    public void testIffWithComparison() {
        ResultSet result = engine.executeQuery(
            "SELECT name, IFF(value > 150, 'High', 'Low') as category FROM test_data WHERE id IN (1, 2)"
        );
        assertEquals(2, result.getRowCount());
        assertEquals("Low", result.getRows().get(0).getValue(result.getColumnIndex("category")));
        assertEquals("High", result.getRows().get(1).getValue(result.getColumnIndex("category")));
    }

    @Test
    public void testIffWithNullValues() {
        ResultSet result = engine.executeQuery(
            "SELECT name, IFF(value > 150, value, NULL) as high_values FROM test_data WHERE id IN (1, 2, 5)"
        );
        assertEquals(3, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("high_values")));
        assertEquals(200, ((Number) result.getRows().get(1).getValue(result.getColumnIndex("high_values"))).intValue());
        assertEquals(300, ((Number) result.getRows().get(2).getValue(result.getColumnIndex("high_values"))).intValue());
    }

    // GREATEST Tests

    @Test
    public void testGreatestTwoValues() {
        ResultSet result = engine.executeQuery(
            "SELECT GREATEST(100, 200) as max_val FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(200, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("max_val"))).intValue());
    }

    @Test
    public void testGreatestMultipleValues() {
        ResultSet result = engine.executeQuery(
            "SELECT GREATEST(50, 200, 75, 300, 150) as max_val FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(300, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("max_val"))).intValue());
    }

    @Test
    public void testGreatestWithColumns() {
        ResultSet result = engine.executeQuery(
            "SELECT name, GREATEST(value, 150) as max_val FROM test_data WHERE id IN (1, 2)"
        );
        assertEquals(2, result.getRowCount());
        // Alice: GREATEST(100, 150) = 150
        assertEquals(150, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("max_val"))).intValue());
        // Bob: GREATEST(200, 150) = 200
        assertEquals(200, ((Number) result.getRows().get(1).getValue(result.getColumnIndex("max_val"))).intValue());
    }

    @Test
    public void testGreatestWithNull() {
        // Snowflake: GREATEST returns NULL when ANY argument is NULL; GREATEST_IGNORE_NULLS skips.
        ResultSet result = engine.executeQuery(
            "SELECT GREATEST(100, NULL, 200) as max_val, GREATEST_IGNORE_NULLS(100, NULL, 200) as max_skip FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("max_val")));
        assertEquals(200, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("max_skip"))).intValue());
    }

    @Test
    public void testGreatestStrings() {
        ResultSet result = engine.executeQuery(
            "SELECT GREATEST('Alice', 'Bob', 'Charlie') as max_name FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // Lexicographic comparison: Charlie > Bob > Alice
        assertEquals("Charlie", result.getRows().get(0).getValue(result.getColumnIndex("max_name")));
    }

    // LEAST Tests

    @Test
    public void testLeastTwoValues() {
        ResultSet result = engine.executeQuery(
            "SELECT LEAST(100, 200) as min_val FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(100, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("min_val"))).intValue());
    }

    @Test
    public void testLeastMultipleValues() {
        ResultSet result = engine.executeQuery(
            "SELECT LEAST(50, 200, 75, 300, 25) as min_val FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals(25, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("min_val"))).intValue());
    }

    @Test
    public void testLeastWithColumns() {
        ResultSet result = engine.executeQuery(
            "SELECT name, LEAST(value, 150) as min_val FROM test_data WHERE id IN (1, 2)"
        );
        assertEquals(2, result.getRowCount());
        // Alice: LEAST(100, 150) = 100
        assertEquals(100, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("min_val"))).intValue());
        // Bob: LEAST(200, 150) = 150
        assertEquals(150, ((Number) result.getRows().get(1).getValue(result.getColumnIndex("min_val"))).intValue());
    }

    @Test
    public void testLeastWithNull() {
        // Snowflake: LEAST returns NULL when ANY argument is NULL — the loader idiom
        // LEAST(MAX(conf), 100) must stay NULL for an all-NULL group; LEAST_IGNORE_NULLS skips.
        ResultSet result = engine.executeQuery(
            "SELECT LEAST(100, NULL, 200) as min_val, LEAST_IGNORE_NULLS(100, NULL, 200) as min_skip FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("min_val")));
        assertEquals(100, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("min_skip"))).intValue());
    }

    @Test
    public void testLeastStrings() {
        ResultSet result = engine.executeQuery(
            "SELECT LEAST('Alice', 'Bob', 'Charlie') as min_name FROM test_data WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        // Lexicographic comparison: Alice < Bob < Charlie
        assertEquals("Alice", result.getRows().get(0).getValue(result.getColumnIndex("min_name")));
    }

    // Combined Tests

    @Test
    public void testCombinedFunctions() {
        ResultSet result = engine.executeQuery(
            "SELECT name, " +
            "  IFF(value > 150, GREATEST(value, 250), LEAST(value, 50)) as adjusted " +
            "FROM test_data WHERE id IN (1, 2, 5)"
        );
        assertEquals(3, result.getRowCount());
        assertEquals(50, ((Number) result.getRows().get(0).getValue(result.getColumnIndex("adjusted"))).intValue());
        assertEquals(250, ((Number) result.getRows().get(1).getValue(result.getColumnIndex("adjusted"))).intValue());
        assertEquals(300, ((Number) result.getRows().get(2).getValue(result.getColumnIndex("adjusted"))).intValue());
    }

    @Test
    public void testNullIfWithGreatest() {
        ResultSet result = engine.executeQuery(
            "SELECT name, NULLIF(GREATEST(value, 100), 100) as result FROM test_data WHERE id IN (1, 2)"
        );
        assertEquals(2, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("result")));
        assertEquals(200, ((Number) result.getRows().get(1).getValue(result.getColumnIndex("result"))).intValue());
    }
}
