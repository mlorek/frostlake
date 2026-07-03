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

/** ASINH(x) — inverse hyperbolic sine, ln(x + sqrt(x*x + 1)). */
public class AsinhTest extends BaseDatabaseTest {

    private double num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void asinhOfZeroIsZero() {
        assertEquals(0.0, num("SELECT ASINH(0)"), 1e-9);
    }

    @Test
    public void asinhOfOne() {
        assertEquals(0.8813735870195429, num("SELECT ASINH(1)"), 1e-9);
    }

    @Test
    public void asinhInvertsSinh() {
        assertEquals(-1.5, num("SELECT ASINH(SINH(-1.5))"), 1e-9);
    }

    @Test
    public void nullIsNull() {
        assertNull(scalar("SELECT ASINH(NULL)"));
    }
}
