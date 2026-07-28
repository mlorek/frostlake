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
 * TRY_CAST(expr AS type) converts like CAST but returns NULL for a value that cannot be converted, rather
 * than raising an error. A bare NUMBER target follows the NUMBER(38,0) default (rounds to a whole number).
 */
public class TryCastTest extends BaseDatabaseTest {

    private Object val(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private double num(final String sql) {
        return ((Number) val(sql)).doubleValue();
    }

    @Test
    public void convertsValidValues() {
        assertEquals(123L, ((Number) val("SELECT TRY_CAST('123' AS INTEGER)")).longValue());
        assertEquals("123", String.valueOf(val("SELECT TRY_CAST(123 AS VARCHAR)")));
        assertEquals(1.5, num("SELECT TRY_CAST('1.5' AS FLOAT)"), 1e-9);
    }

    @Test
    public void returnsNullOnFailure() {
        assertNull(val("SELECT TRY_CAST('abc' AS INTEGER)"));
        assertNull(val("SELECT TRY_CAST('not_a_number' AS NUMBER)"));
    }

    @Test
    public void nullInputStaysNull() {
        assertNull(val("SELECT TRY_CAST(NULL AS INTEGER)"));
    }

    @Test
    public void bareNumberRoundsToScaleZero() {
        assertEquals(406.0, num("SELECT TRY_CAST('405.958' AS NUMBER)"), 1e-9);
        assertEquals(405.96, num("SELECT TRY_CAST('405.958' AS NUMBER(10,2))"), 1e-9);
    }

    @Test
    public void usableInWhereAndProjection() {
        // A realistic pattern: TRY_CAST guards a split_part that may not be numeric.
        final Object v = val("""
            SELECT TRY_CAST(SPLIT_PART('sev:42', ':', 2) AS INTEGER)
            """);
        assertEquals(42L, ((Number) v).longValue());
    }
}
