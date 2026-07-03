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

/** GETBIT(integer, position) — the 0/1 bit at the 0-based position counting from the LSB. */
public class GetbitTest extends BaseDatabaseTest {

    private long num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void bitZeroOfEleven() {
        // 11 = binary 1011, LSB is 1.
        assertEquals(1L, num("SELECT GETBIT(11, 0)"));
    }

    @Test
    public void bitTwoOfEleven() {
        // 11 = binary 1011, bit 2 is 0.
        assertEquals(0L, num("SELECT GETBIT(11, 2)"));
    }

    @Test
    public void positionBeyondSetBitsIsZero() {
        assertEquals(0L, num("SELECT GETBIT(11, 100)"));
    }

    @Test
    public void nullArgumentYieldsNull() {
        assertNull(scalar("SELECT GETBIT(NULL, 0)"));
        assertNull(scalar("SELECT GETBIT(11, NULL)"));
    }
}
