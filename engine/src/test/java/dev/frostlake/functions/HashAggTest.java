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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HASH_AGG(expr) — a single order-independent 64-bit hash over the multiset of rows in each group.
 */
public class HashAggTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE h (g INTEGER, v INTEGER)");
        // Groups 1 and 2 hold the SAME multiset {1,2,3} inserted in DIFFERENT orders.
        engine.execute("INSERT INTO h VALUES (1,1),(1,2),(1,3)");
        engine.execute("INSERT INTO h VALUES (2,3),(2,1),(2,2)");
        // Group 3 is a different multiset.
        engine.execute("INSERT INTO h VALUES (3,1),(3,2),(3,4)");
    }

    @Test
    public void isOrderIndependentAcrossGroups() {
        final ResultSet rs = engine.executeQuery(
            "SELECT g, HASH_AGG(v) FROM h GROUP BY g ORDER BY g");
        assertEquals(3, rs.getRowCount());
        final Object g1 = rs.getRows().get(0).getValue(1);
        final Object g2 = rs.getRows().get(1).getValue(1);
        final Object g3 = rs.getRows().get(2).getValue(1);
        assertTrue(g1 instanceof Long, "HASH_AGG should return a Long");
        // Same multiset, different row order → identical hash.
        assertEquals(g1, g2);
        // Different multiset → (almost certainly) different hash.
        assertNotEquals(g1, g3);
    }

    @Test
    public void nullValuesAreHashedDeterministically() {
        engine.execute("CREATE TABLE hn (g INTEGER, v INTEGER)");
        engine.execute("INSERT INTO hn VALUES (1,1),(1,NULL),(1,2)");
        engine.execute("INSERT INTO hn VALUES (2,2),(2,1),(2,NULL)");   // same multiset incl. NULL
        final ResultSet rs = engine.executeQuery(
            "SELECT g, HASH_AGG(v) FROM hn GROUP BY g ORDER BY g");
        assertEquals(rs.getRows().get(0).getValue(1), rs.getRows().get(1).getValue(1));
    }

    @Test
    public void emptyGroupYieldsNull() {
        assertNull(engine.executeQuery("SELECT HASH_AGG(v) FROM h WHERE g = 999")
            .getRows().get(0).getValue(0));
    }
}
