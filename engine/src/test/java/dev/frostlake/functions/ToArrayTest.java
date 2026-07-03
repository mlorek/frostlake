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

/** TO_ARRAY(expr) — wraps a scalar in a singleton array; passes an existing array through. */
public class ToArrayTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void wrapsScalarInSingletonArray() {
        assertEquals("[1]", scalar("SELECT TO_ARRAY(1)").toString());
    }

    @Test
    public void wrapsStringAsSingleElement() {
        assertEquals("[\"hello\"]", scalar("SELECT TO_ARRAY('hello')").toString());
    }

    @Test
    public void existingArrayUnchanged() {
        assertEquals("[1,2,3]", scalar("SELECT TO_ARRAY(ARRAY_CONSTRUCT(1, 2, 3))").toString());
    }

    @Test
    public void nullInputIsNull() {
        assertNull(scalar("SELECT TO_ARRAY(NULL)"));
    }
}
