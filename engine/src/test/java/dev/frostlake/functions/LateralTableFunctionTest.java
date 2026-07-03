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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for LATERAL with table functions
 * Tests correlated table functions that reference columns from preceding tables
 */
public class LateralTableFunctionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testLateralWithGeneratorSimple() {
        engine.execute("CREATE TABLE numbers (n INTEGER)");
        engine.execute("INSERT INTO numbers VALUES (2)");
        engine.execute("INSERT INTO numbers VALUES (3)");

        ResultSet result = engine.executeQuery("""
            SELECT n.n, g.SEQ
            FROM numbers n, LATERAL TABLE(GENERATOR(ROWCOUNT => n.n)) g
            """);

        assertNotNull(result);
        assertEquals(5, result.getRowCount());  // 2 + 3 = 5 rows total

        // First two rows should have n=2, SEQ=0,1
        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals(0L, result.getRows().get(0).getValue(1));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(1L, result.getRows().get(1).getValue(1));

        // Next three rows should have n=3, SEQ=0,1,2
        assertEquals(3L, result.getRows().get(2).getValue(0));
        assertEquals(0L, result.getRows().get(2).getValue(1));
        assertEquals(3L, result.getRows().get(3).getValue(0));
        assertEquals(1L, result.getRows().get(3).getValue(1));
        assertEquals(3L, result.getRows().get(4).getValue(0));
        assertEquals(2L, result.getRows().get(4).getValue(1));
    }

    @Test
    public void testLateralWithSplitToTable() {
        engine.execute("CREATE TABLE data (id INTEGER, tag_list VARCHAR)");
        engine.execute("INSERT INTO data VALUES (1, 'red,blue,green')");
        engine.execute("INSERT INTO data VALUES (2, 'small,medium')");

        ResultSet result = engine.executeQuery("""
            SELECT d.id, s.VALUE as tag
            FROM data d, LATERAL TABLE(SPLIT_TO_TABLE(STRING => d.tag_list, DELIMITER => ',')) s
            ORDER BY d.id, s.SEQ
            """);

        assertNotNull(result);
        assertEquals(5, result.getRowCount());

        // ID 1 should have 3 tags
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("red", result.getRows().get(0).getValue(1));
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertEquals("blue", result.getRows().get(1).getValue(1));
        assertEquals(1L, result.getRows().get(2).getValue(0));
        assertEquals("green", result.getRows().get(2).getValue(1));

        // ID 2 should have 2 tags
        assertEquals(2L, result.getRows().get(3).getValue(0));
        assertEquals("small", result.getRows().get(3).getValue(1));
        assertEquals(2L, result.getRows().get(4).getValue(0));
        assertEquals("medium", result.getRows().get(4).getValue(1));
    }

    @Test
    public void testLateralWithFlatten() {
        engine.execute("CREATE TABLE documents (id INTEGER, json_data VARCHAR)");
        engine.execute("INSERT INTO documents VALUES (1, '{\"a\":1,\"b\":2}')");
        engine.execute("INSERT INTO documents VALUES (2, '{\"x\":10}')");

        ResultSet result = engine.executeQuery("""
            SELECT d.id, f.KEY, f.VALUE
            FROM documents d, LATERAL TABLE(FLATTEN(INPUT => d.json_data)) f
            ORDER BY d.id, f.SEQ
            """);

        assertNotNull(result);
        assertEquals(3, result.getRowCount());

        // ID 1 should have 2 keys
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertNotNull(result.getRows().get(0).getValue(1));  // KEY
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertNotNull(result.getRows().get(1).getValue(1));  // KEY

        // ID 2 should have 1 key
        assertEquals(2L, result.getRows().get(2).getValue(0));
        assertEquals("x", result.getRows().get(2).getValue(1));
    }

    @Test
    public void testLateralCrossJoinSyntax() {
        engine.execute("CREATE TABLE numbers (n INTEGER)");
        engine.execute("INSERT INTO numbers VALUES (2)");

        ResultSet result = engine.executeQuery("""
            SELECT n.n, g.SEQ
            FROM numbers n
            CROSS JOIN LATERAL TABLE(GENERATOR(ROWCOUNT => n.n)) g
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testLateralWithWhereFilter() {
        engine.execute("CREATE TABLE ranges (id INTEGER, count INTEGER)");
        engine.execute("INSERT INTO ranges VALUES (1, 5)");
        engine.execute("INSERT INTO ranges VALUES (2, 3)");

        ResultSet result = engine.executeQuery("""
            SELECT r.id, g.SEQ
            FROM ranges r, LATERAL TABLE(GENERATOR(ROWCOUNT => r.count)) g
            WHERE r.id = 1
            """);

        assertNotNull(result);
        assertEquals(5, result.getRowCount());

        // All rows should have id=1
        for (int i = 0; i < 5; i++) {
            assertEquals(1L, result.getRows().get(i).getValue(0));
            assertEquals((long) i, result.getRows().get(i).getValue(1));
        }
    }

    @Test
    public void testLateralWithMultipleRows() {
        engine.execute("CREATE TABLE items (item_id INTEGER, item_name VARCHAR, count INTEGER)");
        engine.execute("INSERT INTO items VALUES (1, 'Apple', 2)");
        engine.execute("INSERT INTO items VALUES (2, 'Banana', 3)");
        engine.execute("INSERT INTO items VALUES (3, 'Cherry', 1)");

        ResultSet result = engine.executeQuery("""
            SELECT i.item_name, g.SEQ
            FROM items i, LATERAL TABLE(GENERATOR(ROWCOUNT => i.count)) g
            ORDER BY i.item_id, g.SEQ
            """);

        assertNotNull(result);
        assertEquals(6, result.getRowCount());  // 2 + 3 + 1 = 6

        // Apple (2 rows)
        assertEquals("Apple", result.getRows().get(0).getValue(0));
        assertEquals(0L, result.getRows().get(0).getValue(1));
        assertEquals("Apple", result.getRows().get(1).getValue(0));
        assertEquals(1L, result.getRows().get(1).getValue(1));

        // Banana (3 rows)
        assertEquals("Banana", result.getRows().get(2).getValue(0));
        assertEquals(0L, result.getRows().get(2).getValue(1));
        assertEquals("Banana", result.getRows().get(3).getValue(0));
        assertEquals(1L, result.getRows().get(3).getValue(1));
        assertEquals("Banana", result.getRows().get(4).getValue(0));
        assertEquals(2L, result.getRows().get(4).getValue(1));

        // Cherry (1 row)
        assertEquals("Cherry", result.getRows().get(5).getValue(0));
        assertEquals(0L, result.getRows().get(5).getValue(1));
    }

    @Test
    public void testLateralWithAggregation() {
        engine.execute("CREATE TABLE groups (group_id INTEGER, size INTEGER)");
        engine.execute("INSERT INTO groups VALUES (1, 3)");
        engine.execute("INSERT INTO groups VALUES (2, 2)");

        ResultSet result = engine.executeQuery("""
            SELECT g.group_id, COUNT(*) as row_count
            FROM groups g, LATERAL TABLE(GENERATOR(ROWCOUNT => g.size)) gen
            GROUP BY g.group_id
            ORDER BY g.group_id
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(3L, result.getRows().get(0).getValue(1));

        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(1));
    }

    @Test
    public void testLateralWithNestedFlatten() {
        engine.execute("CREATE TABLE nested_data (id INTEGER, data VARCHAR)");
        engine.execute("INSERT INTO nested_data VALUES (1, '[1,2,3]')");

        ResultSet result = engine.executeQuery("""
            SELECT n.id, f.VALUE
            FROM nested_data n, LATERAL TABLE(FLATTEN(INPUT => n.data)) f
            """);

        assertNotNull(result);
        assertEquals(3, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertEquals(1L, result.getRows().get(2).getValue(0));
    }

    @Test
    public void testLateralWithJoinCondition() {
        engine.execute("CREATE TABLE source (id INTEGER, value VARCHAR)");
        engine.execute("INSERT INTO source VALUES (1, 'a,b')");
        engine.execute("INSERT INTO source VALUES (2, 'c,d')");

        ResultSet result = engine.executeQuery("""
            SELECT s.id, sp.VALUE
            FROM source s
            CROSS JOIN LATERAL TABLE(SPLIT_TO_TABLE(STRING => s.value, DELIMITER => ',')) sp
            WHERE s.id = 1
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("a", result.getRows().get(0).getValue(1));
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertEquals("b", result.getRows().get(1).getValue(1));
    }

    @Test
    public void testLateralWithEmptyResult() {
        engine.execute("CREATE TABLE empty_test (id INTEGER, count INTEGER)");
        engine.execute("INSERT INTO empty_test VALUES (1, 0)");
        engine.execute("INSERT INTO empty_test VALUES (2, 2)");

        ResultSet result = engine.executeQuery("""
            SELECT e.id, g.SEQ
            FROM empty_test e, LATERAL TABLE(GENERATOR(ROWCOUNT => e.count)) g
            """);

        assertNotNull(result);
        assertEquals(2, result.getRowCount());  // Only rows from id=2

        assertEquals(2L, result.getRows().get(0).getValue(0));
        assertEquals(0L, result.getRows().get(0).getValue(1));
        assertEquals(2L, result.getRows().get(1).getValue(0));
        assertEquals(1L, result.getRows().get(1).getValue(1));
    }
}
