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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SPLIT_TO_TABLE table function
 */
public class SplitToTableTest {

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
    public void testBasicSplitWithCommaDelimiter() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'apple,banana,cherry', DELIMITER => ','))"
        );
        assertNotNull(result);
        assertEquals(3, result.getRowCount());

        // Check columns
        assertEquals(3, result.getColumns().size());
        assertEquals("SEQ", result.getColumns().get(0).getName());
        assertEquals("INDEX", result.getColumns().get(1).getName());
        assertEquals("VALUE", result.getColumns().get(2).getName());

        // Check first row
        assertEquals(0L, result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(0).getValue(1));
        assertEquals("apple", result.getRows().get(0).getValue(2));

        // Check second row
        assertEquals(1L, result.getRows().get(1).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(1));
        assertEquals("banana", result.getRows().get(1).getValue(2));

        // Check third row
        assertEquals(2L, result.getRows().get(2).getValue(0));
        assertEquals(3L, result.getRows().get(2).getValue(1));
        assertEquals("cherry", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitWithPipeDelimiter() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'red|green|blue', DELIMITER => '|'))"
        );
        assertNotNull(result);
        assertEquals(3, result.getRowCount());
        assertEquals("red", result.getRows().get(0).getValue(2));
        assertEquals("green", result.getRows().get(1).getValue(2));
        assertEquals("blue", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitWithSpaceDelimiter() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'one two three', DELIMITER => ' '))"
        );
        assertNotNull(result);
        assertEquals(3, result.getRowCount());
        assertEquals("one", result.getRows().get(0).getValue(2));
        assertEquals("two", result.getRows().get(1).getValue(2));
        assertEquals("three", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitWithMultiCharacterDelimiter() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'alpha::beta::gamma', DELIMITER => '::'))"
        );
        assertNotNull(result);
        assertEquals(3, result.getRowCount());
        assertEquals("alpha", result.getRows().get(0).getValue(2));
        assertEquals("beta", result.getRows().get(1).getValue(2));
        assertEquals("gamma", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitEmptyString() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => '', DELIMITER => ','))"
        );
        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals("", result.getRows().get(0).getValue(2));
    }

    @Test
    public void testSplitSingleValue() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'onlyOne', DELIMITER => ','))"
        );
        assertNotNull(result);
        assertEquals(1, result.getRowCount());
        assertEquals(0L, result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(0).getValue(1));
        assertEquals("onlyOne", result.getRows().get(0).getValue(2));
    }

    @Test
    public void testSplitWithTrailingDelimiter() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'a,b,c,', DELIMITER => ','))"
        );
        assertNotNull(result);
        assertEquals(4, result.getRowCount());
        assertEquals("a", result.getRows().get(0).getValue(2));
        assertEquals("b", result.getRows().get(1).getValue(2));
        assertEquals("c", result.getRows().get(2).getValue(2));
        assertEquals("", result.getRows().get(3).getValue(2));
    }

    @Test
    public void testSplitWithLeadingDelimiter() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => ',a,b,c', DELIMITER => ','))"
        );
        assertNotNull(result);
        assertEquals(4, result.getRowCount());
        assertEquals("", result.getRows().get(0).getValue(2));
        assertEquals("a", result.getRows().get(1).getValue(2));
        assertEquals("b", result.getRows().get(2).getValue(2));
        assertEquals("c", result.getRows().get(3).getValue(2));
    }

    @Test
    public void testSplitWithConsecutiveDelimiters() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'a,,b,,c', DELIMITER => ','))"
        );
        assertNotNull(result);
        assertEquals(5, result.getRowCount());
        assertEquals("a", result.getRows().get(0).getValue(2));
        assertEquals("", result.getRows().get(1).getValue(2));
        assertEquals("b", result.getRows().get(2).getValue(2));
        assertEquals("", result.getRows().get(3).getValue(2));
        assertEquals("c", result.getRows().get(4).getValue(2));
    }

    @Test
    public void testSplitWithWhereClause() {
        ResultSet result = engine.executeQuery("""
            SELECT VALUE FROM TABLE(SPLIT_TO_TABLE(STRING => 'apple,banana,cherry', DELIMITER => ','))
            WHERE INDEX > 1
            """);
        assertNotNull(result);
        assertEquals(2, result.getRowCount());
        assertEquals("banana", result.getRows().get(0).getValue(0));
        assertEquals("cherry", result.getRows().get(1).getValue(0));
    }

    @Test
    public void testSplitWithAlias() {
        ResultSet result = engine.executeQuery(
            "SELECT VALUE, INDEX FROM TABLE(SPLIT_TO_TABLE(STRING => 'x,y,z', DELIMITER => ',')) s"
        );
        assertNotNull(result);
        assertEquals(3, result.getRowCount());
        assertEquals("x", result.getRows().get(0).getValue(0));
        assertEquals(1L, result.getRows().get(0).getValue(1));
        assertEquals("y", result.getRows().get(1).getValue(0));
        assertEquals(2L, result.getRows().get(1).getValue(1));
        assertEquals("z", result.getRows().get(2).getValue(0));
        assertEquals(3L, result.getRows().get(2).getValue(1));
    }

    @Test
    public void testSplitWithOrderBy() {
        ResultSet result = engine.executeQuery("""
            SELECT VALUE FROM TABLE(SPLIT_TO_TABLE(STRING => 'zebra,apple,monkey', DELIMITER => ','))
            ORDER BY VALUE
            """);
        assertNotNull(result);
        assertEquals(3, result.getRowCount());
        assertEquals("apple", result.getRows().get(0).getValue(0));
        assertEquals("monkey", result.getRows().get(1).getValue(0));
        assertEquals("zebra", result.getRows().get(2).getValue(0));
    }

    @Test
    public void testSplitMissingStringParameter() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.executeQuery("SELECT * FROM TABLE(SPLIT_TO_TABLE(DELIMITER => ','))");
        });
        assertTrue(exception.getMessage().contains("STRING"));
    }

    @Test
    public void testSplitInvalidParameter() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            engine.executeQuery(
                "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'test', INVALID_PARAM => 'value'))"
            );
        });
        assertTrue(exception.getMessage().contains("Invalid argument"));
    }

    @Test
    public void testSplitNoDelimiterDefaultsToComma() {
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE(STRING => 'a,b,c'))"
        );
        assertNotNull(result);
        assertEquals(3, result.getRowCount());
        assertEquals("a", result.getRows().get(0).getValue(2));
        assertEquals("b", result.getRows().get(1).getValue(2));
        assertEquals("c", result.getRows().get(2).getValue(2));
    }

    @Test
    public void testSplitToTablePositionalArgs() {
        // Positional form SPLIT_TO_TABLE(string, delimiter) — no NAME => bindings.
        ResultSet result = engine.executeQuery(
            "SELECT * FROM TABLE(SPLIT_TO_TABLE('apple,banana,cherry', ','))");
        assertEquals(3, result.getRowCount());
        assertEquals("apple", result.getRows().get(0).getValue(2));
        assertEquals("banana", result.getRows().get(1).getValue(2));
        assertEquals("cherry", result.getRows().get(2).getValue(2));
    }
}
