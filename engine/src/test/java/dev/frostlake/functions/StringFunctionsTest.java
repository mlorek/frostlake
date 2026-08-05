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
import static org.junit.jupiter.api.Assertions.assertNull;

public class StringFunctionsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        // Create test table
        engine.execute("CREATE TABLE test_strings (id INTEGER, text VARCHAR)");
        engine.execute("INSERT INTO test_strings VALUES (1, '  hello  ')");
        engine.execute("INSERT INTO test_strings VALUES (2, 'world')");
        engine.execute("INSERT INTO test_strings VALUES (3, 'SNOWFLAKE')");
        engine.execute("INSERT INTO test_strings VALUES (4, 'test data')");
        engine.execute("INSERT INTO test_strings VALUES (5, NULL)");
    }

    // LTRIM Tests

    @Test
    public void testLTrim() {
        ResultSet result = engine.executeQuery(
            "SELECT LTRIM(text) as trimmed FROM test_strings WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("hello  ", result.getRows().get(0).getValue(result.getColumnIndex("trimmed")));
    }

    @Test
    public void testLTrimNoSpaces() {
        ResultSet result = engine.executeQuery(
            "SELECT LTRIM(text) as trimmed FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("world", result.getRows().get(0).getValue(result.getColumnIndex("trimmed")));
    }

    @Test
    public void testLTrimNull() {
        ResultSet result = engine.executeQuery(
            "SELECT LTRIM(text) as trimmed FROM test_strings WHERE id = 5"
        );
        assertEquals(1, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("trimmed")));
    }

    // RTRIM Tests

    @Test
    public void testRTrim() {
        ResultSet result = engine.executeQuery(
            "SELECT RTRIM(text) as trimmed FROM test_strings WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("  hello", result.getRows().get(0).getValue(result.getColumnIndex("trimmed")));
    }

    @Test
    public void testRTrimNoSpaces() {
        ResultSet result = engine.executeQuery(
            "SELECT RTRIM(text) as trimmed FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("world", result.getRows().get(0).getValue(result.getColumnIndex("trimmed")));
    }

    // REVERSE Tests

    @Test
    public void testReverse() {
        ResultSet result = engine.executeQuery(
            "SELECT REVERSE(text) as reversed FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("dlrow", result.getRows().get(0).getValue(result.getColumnIndex("reversed")));
    }

    @Test
    public void testReverseNull() {
        ResultSet result = engine.executeQuery(
            "SELECT REVERSE(text) as reversed FROM test_strings WHERE id = 5"
        );
        assertEquals(1, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("reversed")));
    }

    // INITCAP Tests

    @Test
    public void testInitCap() {
        ResultSet result = engine.executeQuery(
            "SELECT INITCAP(text) as capitalized FROM test_strings WHERE id = 4"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("Test Data", result.getRows().get(0).getValue(result.getColumnIndex("capitalized")));
    }

    @Test
    public void testInitCapAllCaps() {
        ResultSet result = engine.executeQuery(
            "SELECT INITCAP(text) as capitalized FROM test_strings WHERE id = 3"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("Snowflake", result.getRows().get(0).getValue(result.getColumnIndex("capitalized")));
    }

    @Test
    public void testInitCapNull() {
        ResultSet result = engine.executeQuery(
            "SELECT INITCAP(text) as capitalized FROM test_strings WHERE id = 5"
        );
        assertEquals(1, result.getRowCount());
        assertNull(result.getRows().get(0).getValue(result.getColumnIndex("capitalized")));
    }

    // LPAD Tests

    @Test
    public void testLPad() {
        ResultSet result = engine.executeQuery(
            "SELECT LPAD(text, 10) as padded FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("     world", result.getRows().get(0).getValue(result.getColumnIndex("padded")));
    }

    @Test
    public void testLPadWithChar() {
        ResultSet result = engine.executeQuery(
            "SELECT LPAD(text, 10, '*') as padded FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("*****world", result.getRows().get(0).getValue(result.getColumnIndex("padded")));
    }

    @Test
    public void testLPadTruncatesLongerInput() {
        ResultSet result = engine.executeQuery(
            "SELECT LPAD(text, 3, '*') as padded FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        // String is longer than target: Snowflake truncates to the target length (live-verified).
        assertEquals("wor", result.getRows().get(0).getValue(result.getColumnIndex("padded")));
    }

    @Test
    public void testLPadWithString() {
        ResultSet result = engine.executeQuery(
            "SELECT LPAD(text, 10, 'ab') as padded FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("ababaworld", result.getRows().get(0).getValue(result.getColumnIndex("padded")));
    }

    // RPAD Tests

    @Test
    public void testRPad() {
        ResultSet result = engine.executeQuery(
            "SELECT RPAD(text, 10) as padded FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("world     ", result.getRows().get(0).getValue(result.getColumnIndex("padded")));
    }

    @Test
    public void testRPadWithChar() {
        ResultSet result = engine.executeQuery(
            "SELECT RPAD(text, 10, '*') as padded FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("world*****", result.getRows().get(0).getValue(result.getColumnIndex("padded")));
    }

    @Test
    public void testRPadTruncatesLongerInput() {
        ResultSet result = engine.executeQuery(
            "SELECT RPAD(text, 3, '*') as padded FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        // String is longer than target: Snowflake truncates to the target length (live-verified).
        assertEquals("wor", result.getRows().get(0).getValue(result.getColumnIndex("padded")));
    }

    @Test
    public void testRPadWithString() {
        ResultSet result = engine.executeQuery(
            "SELECT RPAD(text, 10, 'ab') as padded FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("worldababa", result.getRows().get(0).getValue(result.getColumnIndex("padded")));
    }

    // Combined Tests

    @Test
    public void testTrimCombined() {
        ResultSet result = engine.executeQuery(
            "SELECT LTRIM(RTRIM(text)) as trimmed FROM test_strings WHERE id = 1"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("hello", result.getRows().get(0).getValue(result.getColumnIndex("trimmed")));
    }

    @Test
    public void testReverseUpper() {
        ResultSet result = engine.executeQuery(
            "SELECT REVERSE(UPPER(text)) as result FROM test_strings WHERE id = 2"
        );
        assertEquals(1, result.getRowCount());
        assertEquals("DLROW", result.getRows().get(0).getValue(result.getColumnIndex("result")));
    }
}
