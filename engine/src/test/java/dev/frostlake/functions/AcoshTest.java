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

/** ACOSH(x) — inverse hyperbolic cosine, ln(x + sqrt(x*x - 1)). */
public class AcoshTest extends BaseDatabaseTest {

    private double num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void acoshOfOneIsZero() {
        assertEquals(0.0, num("SELECT ACOSH(1)"), 1e-9);
    }

    @Test
    public void acoshOfTwo() {
        assertEquals(1.3169578969248166, num("SELECT ACOSH(2)"), 1e-9);
    }

    @Test
    public void acoshInvertsCosh() {
        assertEquals(2.0, num("SELECT ACOSH(COSH(2))"), 1e-9);
    }

    @Test
    public void nullIsNull() {
        assertNull(scalar("SELECT ACOSH(NULL)"));
    }
}
