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

/**
 * ARRAY_TO_STRING semantics, aligned with Snowflake:
 * a NULL array or a NULL separator returns NULL, and NULL elements
 * are rendered as empty strings (their separators are kept).
 */
public class ArrayToStringTest extends BaseDatabaseTest {

    @Test
    public void joinsElementsWithSeparator() {
        assertEquals("a,b", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', 'b'), ',')"));
        assertEquals("", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(), ',')"));
    }

    @Test
    public void nullSeparatorReturnsNull() {
        assertNull(scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', 'b'), NULL)"));
        assertNull(scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(), NULL)"));
    }

    @Test
    public void nullArrayReturnsNull() {
        assertNull(scalar("SELECT ARRAY_TO_STRING(NULL, ',')"));
    }

    @Test
    public void nullSeparatorFallsThroughCoalesce() {
        assertEquals("fallback", scalar("SELECT COALESCE(ARRAY_TO_STRING(ARRAY_CONSTRUCT(), NULL), 'fallback')"));
    }

    @Test
    public void nullElementsBecomeEmptyStrings() {
        assertEquals("a,,b", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', NULL, 'b'), ',')"));
        assertEquals("true,1,-1.2", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(TRUE, 1, -1.2), ',')"));
    }

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }
}
