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
 * REGEXP_SUBSTR_ALL(subject, pattern [, position [, occurrence [, parameters [, group_num]]]]) — a VARIANT
 * ARRAY of all matches (or capture-group matches). REGEXP_EXTRACT_ALL is an exact alias.
 */
public class RegexpSubstrAllTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void returnsAllMatchesCaseInsensitive() {
        assertEquals("[\"a1\",\"a2\",\"a3\",\"a4\",\"A5\",\"a6\"]",
            scalar("SELECT REGEXP_SUBSTR_ALL('a1_a2a3_a4A5a6', 'a[0-9]', 1, 1, 'i')"));
    }

    @Test
    public void extractsCaptureGroupFromEachMatch() {
        assertEquals("[\"1\",\"2\"]",
            scalar("SELECT REGEXP_SUBSTR_ALL('x=1,y=2', '([a-z])=([0-9])', 1, 1, 'e', 2)"));
    }

    @Test
    public void noMatchYieldsEmptyArray() {
        assertEquals("[]", scalar("SELECT REGEXP_SUBSTR_ALL('abc', '[0-9]+')"));
    }

    @Test
    public void nullArgumentYieldsNull() {
        assertNull(scalar("SELECT REGEXP_SUBSTR_ALL(NULL, 'a')"));
    }

    @Test
    public void regexpExtractAllIsAnAlias() {
        assertEquals("[\"a1\",\"b2\"]", scalar("SELECT REGEXP_EXTRACT_ALL('a1b2', '[a-z][0-9]')"));
    }
}
