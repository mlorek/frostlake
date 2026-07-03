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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The REGEXP_* functions with their full Snowflake argument sets — position, occurrence, regex_parameters
 * (i/m/s/e flags), and group_num — plus Snowflake replacement backreferences ({@code \1}). Previously each
 * accepted only the leading two/three arguments, REGEXP_REPLACE used Java {@code $1} backreferences, and an
 * invalid pattern was silently swallowed.
 */
public class RegexpFunctionsTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private long asLong(final String sql) {
        return ((Number) scalar(sql)).longValue();
    }

    @Test
    public void replaceBasicReplacesAllByDefault() {
        assertEquals("hXXXo", scalar("SELECT REGEXP_REPLACE('hello', 'el+', 'XXX')").toString());
        assertEquals("XcXcXc", scalar("SELECT REGEXP_REPLACE('acacac', 'a', 'X')").toString());
    }

    @Test
    public void replaceUsesSnowflakeBackreferences() {
        assertEquals("[b][a]", scalar("SELECT REGEXP_REPLACE('ab', '(a)(b)', '[\\2][\\1]')").toString());
    }

    @Test
    public void replaceOccurrenceReplacesOnlyTheNth() {
        assertEquals("aXa", scalar("SELECT REGEXP_REPLACE('aaa', 'a', 'X', 1, 2)").toString());
    }

    @Test
    public void replacePositionStartsMatchingLater() {
        // Position 3 leaves the first 'a' (index 0) untouched.
        assertEquals("aaXa", scalar("SELECT REGEXP_REPLACE('aaaa', 'a', 'X', 3, 1)").toString());
    }

    @Test
    public void replaceInvalidPatternThrows() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT REGEXP_REPLACE('x', '[', 'y')");
            }
        });
    }

    @Test
    public void substrOccurrenceAndPosition() {
        assertEquals("123", scalar("SELECT REGEXP_SUBSTR('abc123def', '[0-9]+')").toString());
        assertEquals("30", scalar("SELECT REGEXP_SUBSTR('a=20;b=30', '[0-9]+', 1, 2)").toString());
        assertEquals("30", scalar("SELECT REGEXP_SUBSTR('a=20;b=30', '[0-9]+', 5)").toString());
        assertNull(scalar("SELECT REGEXP_SUBSTR('abc', '[0-9]+')"));
    }

    @Test
    public void substrExtractsCaptureGroup() {
        // group_num implies the 'e' extract behavior.
        assertEquals("20", scalar("SELECT REGEXP_SUBSTR('a=20;b=30', '([a-z])=([0-9]+)', 1, 1, 'e', 2)").toString());
        assertEquals("a", scalar("SELECT REGEXP_SUBSTR('a=20;b=30', '([a-z])=([0-9]+)', 1, 1, 'e', 1)").toString());
    }

    @Test
    public void countPositionAndParameters() {
        assertEquals(4L, asLong("SELECT REGEXP_COUNT('aababcabc', 'a')"));
        assertEquals(2L, asLong("SELECT REGEXP_COUNT('AbaB', 'b', 1, 'i')"));   // case-insensitive → b and B
        assertEquals(2L, asLong("SELECT REGEXP_COUNT('aXaXa', 'a', 3)"));       // from index 2: 'a' at indices 2 and 4
    }

    @Test
    public void instrPositionOccurrenceAndOption() {
        assertEquals(4L, asLong("SELECT REGEXP_INSTR('hello world', 'lo')"));
        assertEquals(0L, asLong("SELECT REGEXP_INSTR('hello', '[0-9]+')"));
        // option 1 → the character just after the match ('lo' ends at index 4, so position 6).
        assertEquals(6L, asLong("SELECT REGEXP_INSTR('hello world', 'lo', 1, 1, 1)"));
    }

    @Test
    public void likeRequiresFullMatchAndHonorsFlags() {
        assertEquals(true, scalar("SELECT REGEXP_LIKE('hello123', '[a-z]+[0-9]+')"));
        assertEquals(false, scalar("SELECT REGEXP_LIKE('hello', '[0-9]+')"));
        assertEquals(true, scalar("SELECT REGEXP_LIKE('HELLO', 'hello', 'i')"));
        assertNull(scalar("SELECT REGEXP_LIKE(NULL, 'x')"));
    }
}
