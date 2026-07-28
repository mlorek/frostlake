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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * INSERT(base, pos, len, insert) — Snowflake's string function: remove {@code len} characters from
 * {@code base} starting at 1-based {@code pos} and put {@code insert} in their place. {@code INSERT} is a
 * keyword (the INSERT statement) that also names this function, so the grammar allows it as a function name.
 */
public class InsertFunctionTest extends BaseDatabaseTest {

    private String insert(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void replacesCharacters() {
        // Remove 3 chars ("bcd") at position 2 and put "zzz" there.
        assertEquals("azzzef", insert("SELECT INSERT('abcdef', 2, 3, 'zzz')"));
    }

    @Test
    public void insertsWhenLengthIsZero() {
        // Length 0 inserts without removing anything.
        assertEquals("abXYZcdef", insert("SELECT INSERT('abcdef', 3, 0, 'XYZ')"));
    }

    @Test
    public void replacesTheWholeString() {
        assertEquals("X", insert("SELECT INSERT('abc', 1, 3, 'X')"));
    }

    @Test
    public void appendsWhenPositionPastEnd() {
        assertEquals("abcX", insert("SELECT INSERT('abc', 5, 0, 'X')"));
    }

    @Test
    public void nestedInsertCalls() {
        // A common pattern: build a timestamp by inserting separators.
        assertEquals("12-34:5678", insert("SELECT INSERT(INSERT('12345678', 5, 0, ':'), 3, 0, '-')"));
    }

    @Test
    public void nullBaseIsNull() {
        assertNull(engine.executeQuery("SELECT INSERT(NULL, 1, 1, 'x')").getRows().get(0).getValue(0));
    }

    @Test
    public void lowercaseName() {
        assertEquals("azzzef", insert("SELECT insert('abcdef', 2, 3, 'zzz')"));
    }
}
