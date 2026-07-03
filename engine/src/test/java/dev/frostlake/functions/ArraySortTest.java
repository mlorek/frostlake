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

/** ARRAY_SORT(array [, sort_ascending [, nulls_first]]). */
public class ArraySortTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void ascendingByDefault() {
        assertEquals("[1,2,3]", scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(3, 1, 2))").toString());
    }

    @Test
    public void descendingWhenNotAscending() {
        assertEquals("[3,2,1]", scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(3, 1, 2), FALSE)").toString());
    }

    @Test
    public void nullsLastWhenAscending() {
        // Default nulls_first = FALSE for ascending order, so SQL NULLs sort to the end.
        assertEquals("[1,2,null]", scalar("SELECT ARRAY_SORT(ARRAY_CONSTRUCT(2, NULL, 1))").toString());
    }

    @Test
    public void nullInputIsNull() {
        assertNull(scalar("SELECT ARRAY_SORT(NULL)"));
    }
}
