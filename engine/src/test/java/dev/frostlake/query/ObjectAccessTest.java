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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for object literal access operator ':'
 * Used to access properties of JSON/VARIANT objects
 * Example: SELECT data:name, data:address:city FROM table
 */
public class ObjectAccessTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ObjectAccessTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE json_data (id INTEGER, data VARCHAR)");
        engine.execute("""
            INSERT INTO json_data VALUES (1, '{"name": "Alice", "age": 30, "address": {"city": "NYC", "zip": "10001"}}')
            """);
        engine.execute("""
            INSERT INTO json_data VALUES (2, '{"name": "Bob", "age": 25, "address": {"city": "LA", "zip": "90001"}}')
            """);
    }

    @Test
    public void testSimplePropertyAccess() {
        logger.info("Testing simple property access with :");

        ResultSet result = engine.executeQuery("SELECT data:name FROM json_data WHERE id = 1");

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("Alice", result.getRows().get(0).getValue(0));
    }

    @Test
    public void testMultiplePropertyAccess() {
        logger.info("Testing multiple properties with :");

        ResultSet result = engine.executeQuery("""
            SELECT data:name as name, data:age as age FROM json_data WHERE id = 1
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("Alice", result.getRows().get(0).getValue(0));
        assertEquals(30L, result.getRows().get(0).getValue(1));
    }

    @Test
    public void testNestedPropertyAccess() {
        logger.info("Testing nested property access with :");

        ResultSet result = engine.executeQuery("""
            SELECT data:address:city FROM json_data WHERE id = 1
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("NYC", result.getRows().get(0).getValue(0));
    }

    @Test
    public void testPropertyAccessInWhere() {
        logger.info("Testing property access in WHERE clause");

        ResultSet result = engine.executeQuery("""
            SELECT id, data:name FROM json_data WHERE data:age > 28
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals(1L, result.getRows().get(0).getValue(0));
        assertEquals("Alice", result.getRows().get(0).getValue(1));
    }

    @Test
    public void testPropertyAccessWithAlias() {
        logger.info("Testing property access with table alias");

        ResultSet result = engine.executeQuery("""
            SELECT j.data:name, j.data:address:city
            FROM json_data j
            WHERE j.id = 2
            """);

        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("Bob", result.getRows().get(0).getValue(0));
        assertEquals("LA", result.getRows().get(0).getValue(1));
    }
}
