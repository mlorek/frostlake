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

public class BoolAndTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void testBothTrue() {
        assertEquals(true, q("SELECT BOOLAND(1, 1)"));
    }

    @Test
    public void testEitherFalse() {
        assertEquals(false, q("SELECT BOOLAND(1, 0)"));
        // The BOOL* family is defined over NUMBER/VARCHAR/VARIANT truthiness, NOT over BOOLEAN:
        // Snowflake rejects BOOLAND(TRUE, FALSE) outright (live-verified).
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT BOOLAND(true, false)");
            }
        });
    }

    @Test
    public void testFalseWithNullIsFalse() {
        // FALSE dominates in three-valued AND, even against NULL.
        assertEquals(false, q("SELECT BOOLAND(0, NULL)"));
    }

    @Test
    public void testTrueWithNullIsNull() {
        assertNull(q("SELECT BOOLAND(1, NULL)"));
    }
}
