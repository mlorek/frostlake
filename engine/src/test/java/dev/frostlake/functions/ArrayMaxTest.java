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

/** ARRAY_MAX(array) — largest non-null element, returned as a VARIANT (live-verified). */
public class ArrayMaxTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void largestNumericElement() {
        assertEquals("20", String.valueOf(scalar("SELECT ARRAY_MAX(ARRAY_CONSTRUCT(20, 0, 10))")));
    }

    @Test
    public void largestTextElementIgnoringNulls() {
        // The result is a VARIANT, so its raw display keeps the JSON quotes ("c") — cast it to VARCHAR to
        // compare the element itself, which reads the same whichever backend serves the query.
        assertEquals("c", String.valueOf(
            scalar("SELECT ARRAY_MAX(ARRAY_CONSTRUCT('a', NULL, 'c', 'b'))::VARCHAR")));
    }

    @Test
    public void nullInputIsNull() {
        assertNull(scalar("SELECT ARRAY_MAX(NULL)"));
    }
}
