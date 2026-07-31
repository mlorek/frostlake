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

/** ARRAY_MIN(array) — smallest non-null element, returned as a VARIANT (live-verified). */
public class ArrayMinTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void smallestNumericElement() {
        assertEquals("0", String.valueOf(scalar("SELECT ARRAY_MIN(ARRAY_CONSTRUCT(20, 0, 10))")));
    }

    @Test
    public void ignoresNullElements() {
        assertEquals("0", String.valueOf(scalar("SELECT ARRAY_MIN(ARRAY_CONSTRUCT(20, NULL, 0, 10))")));
    }

    @Test
    public void allNullIsNull() {
        assertNull(scalar("SELECT ARRAY_MIN(ARRAY_CONSTRUCT(NULL, NULL))"));
    }

    @Test
    public void nullInputIsNull() {
        assertNull(scalar("SELECT ARRAY_MIN(NULL)"));
    }
}
