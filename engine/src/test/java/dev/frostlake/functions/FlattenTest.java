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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for FLATTEN table function
 */
public class FlattenTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void testFlattenSimpleObject() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '{\"name\":\"John\",\"age\":30}'))"
        );

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        // Check columns
        assertEquals(6, result.getColumns().size());
        assertEquals("SEQ", result.getColumns().get(0).getName());
        assertEquals("KEY", result.getColumns().get(1).getName());
        assertEquals("PATH", result.getColumns().get(2).getName());
        assertEquals("INDEX", result.getColumns().get(3).getName());
        assertEquals("VALUE", result.getColumns().get(4).getName());
        assertEquals("THIS", result.getColumns().get(5).getName());

        // Check first row
        assertEquals(0L, result.getRows().get(0).getValue(0));  // SEQ
        assertTrue(result.getRows().get(0).getValue(1) != null);  // KEY (age or name)
        assertNotNull(result.getRows().get(0).getValue(2));  // PATH
        assertNull(result.getRows().get(0).getValue(3));  // INDEX (null for objects)
        assertNotNull(result.getRows().get(0).getValue(4));  // VALUE
    }

    @Test
    public void testFlattenSimpleArray() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '[1,2,3]'))"
        );

        assertNotNull(result);
        assertEquals(3, result.getRowCount());

        // Check that each row has INDEX but no KEY
        for (int i = 0; i < 3; i++) {
            assertEquals((long) i, result.getRows().get(i).getValue(0));  // SEQ
            assertNull(result.getRows().get(i).getValue(1));  // KEY (null for arrays)
            assertNotNull(result.getRows().get(i).getValue(2));  // PATH
            assertEquals((long) i, result.getRows().get(i).getValue(3));  // INDEX
            assertNotNull(result.getRows().get(i).getValue(4));  // VALUE
        }
    }

    @Test
    public void testFlattenNestedObject() {
        String json = "{\"person\":{\"name\":\"Alice\",\"address\":{\"city\":\"NYC\"}}}";
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '" + json + "'))"
        );

        assertNotNull(result);
        assertEquals(1, result.getRowCount());  // Only top-level without RECURSIVE

        // Should have one key "person"
        assertEquals("person", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testFlattenRecursive() {
        String json = "{\"person\":{\"name\":\"Alice\",\"age\":25}}";
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '" + json + "', RECURSIVE => 'true'))"
        );

        assertNotNull(result);
        assertTrue(result.getRowCount() >= 3);  // person + name + age
    }

    @Test
    public void testFlattenArrayOfObjects() {
        String json = "[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]";
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '" + json + "'))"
        );

        assertNotNull(result);
        assertEquals(2, result.getRowCount());

        // Both should have INDEX but no KEY
        assertNull(result.getRows().get(0).getValue(1));
        assertEquals(0L, result.getRows().get(0).getValue(3));
        assertNull(result.getRows().get(1).getValue(1));
        assertEquals(1L, result.getRows().get(1).getValue(3));
    }

    @Test
    public void testFlattenModeObject() {
        String json = "{\"a\":1,\"b\":[2,3]}";
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '" + json + "', MODE => 'OBJECT'))"
        );

        assertNotNull(result);
        assertEquals(2, result.getRowCount());  // Only object keys, not array elements

        // Both rows should have KEY
        assertNotNull(result.getRows().get(0).getValue(1));
        assertNotNull(result.getRows().get(1).getValue(1));
    }

    @Test
    public void testFlattenModeArray() {
        String json = "{\"arr\":[1,2,3]}";
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '" + json + "', MODE => 'ARRAY', RECURSIVE => 'true'))"
        );

        assertNotNull(result);
        assertEquals(3, result.getRowCount());  // Only array elements

        // All rows should have INDEX
        for (int i = 0; i < 3; i++) {
            assertNotNull(result.getRows().get(i).getValue(3));
        }
    }

    @Test
    public void testFlattenEmptyObject() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '{}'))"
        );

        assertNotNull(result);
        assertEquals(0, result.getRowCount());  // Empty object generates no rows
    }

    @Test
    public void testFlattenEmptyObjectOuter() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '{}', OUTER => 'true'))"
        );

        assertNotNull(result);
        assertEquals(1, result.getRowCount());  // OUTER generates one null row

        assertNull(result.getRows().get(0).getValue(1));  // KEY
        assertNull(result.getRows().get(0).getValue(4));  // VALUE
    }

    @Test
    public void testFlattenEmptyArray() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN(INPUT => '[]'))"
        );

        assertNotNull(result);
        assertEquals(0, result.getRowCount());
    }

    @Test
    public void testFlattenWithWhereClause() {
        String json = "[{\"id\":1},{\"id\":2},{\"id\":3}]";
        ResultSet result = engine.executeQuery(
            "SELECT VALUE FROM TABLE(FLATTEN(INPUT => '" + json + "')) WHERE INDEX < 2"
        );

        assertNotNull(result);
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void testFlattenMissingInput() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("SELECT * FROM TABLE(FLATTEN(MODE => 'OBJECT'))");
        });
        assertTrue(exception.getMessage().contains("INPUT"));
    }

    @Test
    public void testFlattenInvalidMode() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("SELECT * FROM TABLE(FLATTEN(INPUT => '[]', MODE => 'INVALID'))");
        });
        assertTrue(exception.getMessage().contains("MODE"));
    }

    @Test
    public void testFlattenWithOrderBy() {
        ResultSet result = engine.executeQuery(
            "SELECT SEQ, VALUE FROM TABLE(FLATTEN(INPUT => '[3,1,2]')) ORDER BY VALUE"
        );

        assertNotNull(result);
        assertEquals(3, result.getRowCount());
        // Values should be ordered: 1, 2, 3
    }

    @Test
    public void testFlattenPositionalArg() {
        // Positional form FLATTEN(input) — the first argument is INPUT.
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(FLATTEN('{\"name\":\"John\",\"age\":30}'))");
        assertNotNull(result);
        assertEquals(2, result.getRowCount());
    }

    @Test
    public void scalarValuesKeepTheirTypes() {
        // Snowflake's FLATTEN VALUE is a typed VARIANT: a JSON boolean/number element must stay
        // Boolean/Number, not become the string "true"/"5" (which re-quotes on re-aggregation).
        final dev.frostlake.storage.ResultSet rs = engine.executeQuery("""
            SELECT f.value FROM (SELECT 1 AS i),
            LATERAL FLATTEN(input => PARSE_JSON('[true, 5, "s"]')) f ORDER BY f.index""");
        org.junit.jupiter.api.Assertions.assertEquals(Boolean.TRUE, rs.getRows().get(0).getValue(0));
        org.junit.jupiter.api.Assertions.assertEquals(5L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        org.junit.jupiter.api.Assertions.assertEquals("s", rs.getRows().get(2).getValue(0));
    }
}
