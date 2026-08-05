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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

public class BoolOrTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void testBothFalse() {
        assertEquals(false, q("SELECT BOOLOR(0, 0)"));
    }

    @Test
    public void testEitherTrue() {
        assertEquals(true, q("SELECT BOOLOR(1, 0)"));
        // BOOLOR takes numeric/variant truthiness, not a BOOLEAN (live-verified rejection).
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT BOOLOR(false, true)");
            }
        });
    }

    @Test
    public void testTrueWithNullIsTrue() {
        // TRUE dominates in three-valued OR, even against NULL.
        assertEquals(true, q("SELECT BOOLOR(1, NULL)"));
    }

    @Test
    public void testFalseWithNullIsNull() {
        assertNull(q("SELECT BOOLOR(0, NULL)"));
    }
}
