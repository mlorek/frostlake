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

/** ATANH(x) — inverse hyperbolic tangent, 0.5 * ln((1 + x) / (1 - x)). */
public class AtanhTest extends BaseDatabaseTest {

    private double num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void atanhOfZeroIsZero() {
        assertEquals(0.0, num("SELECT ATANH(0)"), 1e-9);
    }

    @Test
    public void atanhOfHalf() {
        assertEquals(0.5493061443340549, num("SELECT ATANH(0.5)"), 1e-9);
    }

    @Test
    public void atanhIsOdd() {
        assertEquals(-0.5493061443340549, num("SELECT ATANH(-0.5)"), 1e-9);
    }

    @Test
    public void nullIsNull() {
        assertNull(scalar("SELECT ATANH(NULL)"));
    }
}
