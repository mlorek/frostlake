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

public class Div0NullTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void testNormalDivision() {
        assertEquals(5, ((Number) q("SELECT DIV0NULL(10, 2)")).intValue());
    }

    @Test
    public void testDivideByZeroReturnsZero() {
        assertEquals(0, ((Number) q("SELECT DIV0NULL(10, 0)")).intValue());
    }

    @Test
    public void testNullDivisorReturnsZero() {
        // DIV0NULL additionally returns 0 when the divisor is NULL.
        assertEquals(0, ((Number) q("SELECT DIV0NULL(10, NULL)")).intValue());
    }

    @Test
    public void testNullDividendReturnsNull() {
        // A NULL dividend with a normal, non-zero divisor still yields NULL.
        assertNull(q("SELECT DIV0NULL(NULL, 5)"));
    }
}
