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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HASH_AGG(expr) — a single order-independent 64-bit hash over the multiset of rows in each group.
 *
 * <p>The 64-bit values are Snowflake's own and are not reproduced, so every cross-engine assertion here
 * is a RELATION between two hashes. Those relations are all live-measured:
 *
 * <ul>
 *   <li>★ IT TAKES AS MANY ARGUMENTS AS ARE WRITTEN, folding every one — Frostlake read the first and
 *       silently dropped the rest, so {@code HASH_AGG(a, b)} was just {@code HASH_AGG(a)}, grouped and
 *       windowed alike.</li>
 *   <li>★ A GROUP OF ONE ROW IS NOT THE SCALAR HASH of that row. Frostlake's was, necessarily: it
 *       summed the per-row scalar hashes, so a sum of one term was that term. Live's two functions are
 *       not the same function seen through a group of one.</li>
 *   <li>DISTINCT drops repeats, and drops them by the whole argument TUPLE.</li>
 *   <li>Row order and argument SCALE are invisible; ARGUMENT order is not.</li>
 *   <li>An empty group is 0, while a group of NULLs is not — a NULL row contributes like any other.</li>
 * </ul>
 *
 * <p>NOT FIXED HERE, and each tracked on its own because neither belongs to HASH_AGG: an aggregate in a
 * query with NO FROM clause is "Unknown function" (which is what made {@code HASH_AGG(1)} look like an
 * arity problem — {@code SUM(1)} fails identically), and {@code HASH_AGG(*)} overflows the stack, as
 * {@code ARRAY_AGG(*)} does.
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
    public void emptyGroupYieldsZero() {
        // Live-verified: HASH_AGG over zero rows is 0, not NULL.
        assertEquals(0L, engine.executeQuery("SELECT HASH_AGG(v) FROM h WHERE g = 999")
            .getRows().get(0).getValue(0));
    }

    /** "true" or "false" for a comparison between two hashes. */
    private String relation(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The multi-argument fixture: one repeated row, so DISTINCT has something to drop. */
    private void multiArgumentRows() {
        engine.execute("CREATE OR REPLACE TABLE hm (id INT, a INT, b NUMBER(10,2), s VARCHAR)");
        engine.execute("INSERT INTO hm VALUES (1, 1, 2.50, 'x'), (2, 2, 3.50, 'y'),"
            + " (3, 1, 2.50, 'x')");
    }

    /** ★ A group of ONE ROW is not the scalar HASH of that row. */
    @Test
    public void agroupOfOneRowIsNotTheScalarHash() {
        multiArgumentRows();
        assertEquals("false", relation("(SELECT HASH_AGG(a) FROM hm WHERE id = 1)"
            + " = (SELECT HASH(1) FROM hm WHERE id = 1)"));
        assertEquals("false", relation("(SELECT HASH_AGG(a, b) FROM hm WHERE id = 1)"
            + " = (SELECT HASH(1, 2.50) FROM hm WHERE id = 1)"),
            "and the same holds for the multi-argument form");
    }

    /** ★ Every argument is folded, so adding one changes the answer. */
    @Test
    public void everyArgumentIsFolded() {
        multiArgumentRows();
        assertEquals("false", relation("(SELECT HASH_AGG(a, b) FROM hm)"
            + " = (SELECT HASH_AGG(a) FROM hm)"));
        assertEquals("false", relation("(SELECT HASH_AGG(a, b, s) FROM hm)"
            + " = (SELECT HASH_AGG(a, b) FROM hm)"));
        assertEquals("false", relation("(SELECT HASH_AGG(a, b) FROM hm)"
            + " = (SELECT HASH_AGG(b, a) FROM hm)"), "and their ORDER matters");
        assertEquals("true", relation("(SELECT HASH_AGG(x, y) FROM (SELECT 1 AS x, 2.50 AS y) t)"
            + " = (SELECT HASH_AGG(x, y) FROM (SELECT 1.00 AS x, 2.5 AS y) t)"),
            "while each argument's SCALE stays invisible");
    }

    /** ★ The GROUPED and WINDOWED forms both fold every argument. */
    @Test
    public void thegroupedAndWindowedFormsBothFoldThem() {
        multiArgumentRows();
        assertEquals("false",
            relation("(SELECT MAX(h) FROM (SELECT HASH_AGG(a, b) AS h FROM hm GROUP BY a) g)"
                + " = (SELECT MAX(h) FROM (SELECT HASH_AGG(a) AS h FROM hm GROUP BY a) g2)"));
        assertEquals("false",
            relation("(SELECT MAX(h) FROM (SELECT HASH_AGG(a, b) OVER () AS h FROM hm) w)"
                + " = (SELECT MAX(h) FROM (SELECT HASH_AGG(a) OVER () AS h FROM hm) w2)"));
    }

    /** DISTINCT drops a repeated row, leaving the hash of what is left. */
    @Test
    public void distinctDropsARepeatedRow() {
        multiArgumentRows();
        assertEquals("true", relation("(SELECT HASH_AGG(DISTINCT a) FROM hm)"
            + " = (SELECT HASH_AGG(a) FROM hm WHERE id IN (1, 2))"));
        assertEquals("false", relation("(SELECT HASH_AGG(a) FROM hm WHERE id IN (1, 3))"
            + " = (SELECT HASH_AGG(a) FROM hm WHERE id = 1)"),
            "and without it each occurrence still counts");
    }

    /** A group of NULLs is NOT the empty group — a NULL row contributes. */
    @Test
    public void agroupOfNullsIsNotTheEmptyGroup() {
        engine.execute("CREATE OR REPLACE TABLE hz (a INT)");
        engine.execute("CREATE OR REPLACE TABLE hnn (a INT)");
        engine.execute("INSERT INTO hnn VALUES (NULL), (NULL)");
        assertEquals("true", relation("(SELECT HASH_AGG(a) FROM hz) = 0"));
        assertEquals("false", relation("(SELECT HASH_AGG(a) FROM hnn) = 0"));
        assertEquals("false",
            relation("(SELECT HASH_AGG(a) FROM hnn) = (SELECT HASH_AGG(a) FROM hz)"));
    }
}
