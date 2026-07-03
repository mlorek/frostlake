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

/** ARRAY_INSERT(array, pos, element) — 0-based insert, shifting later elements right. */
public class ArrayInsertTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void insertsAtIndex() {
        assertEquals("[0,1,9,2,3]", scalar("SELECT ARRAY_INSERT(ARRAY_CONSTRUCT(0, 1, 2, 3), 2, 9)").toString());
    }

    @Test
    public void appendsWhenPosEqualsSize() {
        assertEquals("[1,2,3,9]", scalar("SELECT ARRAY_INSERT(ARRAY_CONSTRUCT(1, 2, 3), 3, 9)").toString());
    }

    @Test
    public void negativePosCountsFromEnd() {
        // -1 inserts before the last element.
        assertEquals("[0,1,2,9,3]", scalar("SELECT ARRAY_INSERT(ARRAY_CONSTRUCT(0, 1, 2, 3), -1, 9)").toString());
    }

    @Test
    public void nullArrayIsNull() {
        assertNull(scalar("SELECT ARRAY_INSERT(NULL, 0, 9)"));
    }
}
