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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ARRAY_POSITION(value, array) — 0-based index of the first matching element, or NULL. The first
 * argument must be a VARIANT: a bare VARCHAR is rejected at compile time (numbers coerce fine).
 */
public class ArrayPositionTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void findsTextElement() {
        assertEquals(1L, scalar("SELECT ARRAY_POSITION('b'::VARIANT, ARRAY_CONSTRUCT('a', 'b', 'c'))"));
    }

    @Test
    public void findsNumericElement() {
        assertEquals(0L, scalar("SELECT ARRAY_POSITION(1, ARRAY_CONSTRUCT(1, 2, 3))"));
        assertEquals(2L, scalar("SELECT ARRAY_POSITION(3, ARRAY_CONSTRUCT(1, 2, 3))"));
    }

    @Test
    public void notFoundIsNull() {
        assertNull(scalar("SELECT ARRAY_POSITION('z'::VARIANT, ARRAY_CONSTRUCT('a', 'b', 'c'))"));
    }

    @Test
    public void nullArrayIsNull() {
        assertNull(scalar("SELECT ARRAY_POSITION('a'::VARIANT, NULL)"));
    }

    @Test
    public void bareVarcharFirstArgumentIsRejected() {
        final RuntimeException rejected = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                scalar("SELECT ARRAY_POSITION('b', ARRAY_CONSTRUCT('a', 'b', 'c'))");
            }
        });
        assertTrue(rejected.getMessage().contains("Invalid argument types for function 'ARRAY_POSITION'"),
            "unexpected: " + rejected.getMessage());
    }
}
