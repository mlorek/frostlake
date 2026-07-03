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

public class BoolXorTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void testExactlyOneTrue() {
        assertEquals(true, q("SELECT BOOLXOR(1, 0)"));
    }

    @Test
    public void testBothTrue() {
        assertEquals(false, q("SELECT BOOLXOR(1, 1)"));
    }

    @Test
    public void testBothFalse() {
        assertEquals(false, q("SELECT BOOLXOR(0, 0)"));
    }

    @Test
    public void testWithNullIsNull() {
        assertNull(q("SELECT BOOLXOR(1, NULL)"));
    }
}
