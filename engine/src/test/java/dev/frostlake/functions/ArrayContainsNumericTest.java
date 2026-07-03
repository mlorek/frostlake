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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ARRAY_CONTAINS / ARRAY_REMOVE must match numeric members by value, regardless of whether the array's
 * element node and the search value node are int/long/double (a strict node-equality misses them).
 */
public class ArrayContainsNumericTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void arrayContainsFindsNumericMember() {
        assertTrue((Boolean) scalar("SELECT ARRAY_CONTAINS(2, ARRAY_CONSTRUCT(1, 2, 3))"));
        assertTrue((Boolean) scalar("SELECT ARRAY_CONTAINS(1, ARRAY_CONSTRUCT(1, 2, 3))"));
        assertTrue((Boolean) scalar("SELECT ARRAY_CONTAINS(3, ARRAY_CONSTRUCT(1, 2, 3))"));
    }

    @Test
    public void arrayContainsMissingNumericMemberIsFalse() {
        assertFalse((Boolean) scalar("SELECT ARRAY_CONTAINS(5, ARRAY_CONSTRUCT(1, 2, 3))"));
    }

    @Test
    public void arrayContainsStillMatchesText() {
        assertTrue((Boolean) scalar("SELECT ARRAY_CONTAINS('b', ARRAY_CONSTRUCT('a', 'b', 'c'))"));
        assertFalse((Boolean) scalar("SELECT ARRAY_CONTAINS('z', ARRAY_CONSTRUCT('a', 'b', 'c'))"));
    }

    @Test
    public void arrayRemoveDropsNumericMember() {
        assertEquals("[1,3]", scalar("SELECT ARRAY_REMOVE(ARRAY_CONSTRUCT(1, 2, 3), 2)").toString());
    }
}
