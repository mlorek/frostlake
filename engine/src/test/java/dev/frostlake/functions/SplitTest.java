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

/** SPLIT(string, delimiter) — splits into a JSON array of substrings (the engine's VARIANT array form). */
public class SplitTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        final Object v = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }

    @Test
    public void splitsOnComma() {
        assertEquals("[\"a\",\"b\",\"c\"]", scalar("SELECT SPLIT('a,b,c', ',')"));
    }

    @Test
    public void delimiterIsLiteralNotRegex() {
        // '.' must be treated literally, not as the regex "any char".
        assertEquals("[\"x\",\"y\"]", scalar("SELECT SPLIT('x.y', '.')"));
    }

    @Test
    public void keepsTrailingEmptyParts() {
        assertEquals("[\"a\",\"b\",\"\"]", scalar("SELECT SPLIT('a,b,', ',')"));
    }

    @Test
    public void emptyDelimiterYieldsWholeStringAsOneElement() {
        assertEquals("[\"abc\"]", scalar("SELECT SPLIT('abc', '')"));
    }

    @Test
    public void nullArgumentYieldsNull() {
        assertNull(scalar("SELECT SPLIT(NULL, ',')"));
        assertNull(scalar("SELECT SPLIT('a,b', NULL)"));
    }

    @Test
    public void multiCharacterDelimiter() {
        assertEquals("[\"a\",\"b\"]", scalar("SELECT SPLIT('a::b', '::')"));
    }
}
