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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code IS [NOT] DISTINCT FROM} — the null-safe comparison: never UNKNOWN, treats two NULLs as
 * equal and NULL-vs-value as different. Exercised as a truth table, as a WHERE filter, and in the
 * role it exists for: joining on nullable keys.
 */
public class IsDistinctFromTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE pairs (id INTEGER, a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO pairs VALUES (1, 1, 1), (2, 1, 2), (3, 1, NULL), (4, NULL, NULL)");
    }

    @Test
    public void truthTableNeverYieldsUnknown() {
        final ResultSet rs = engine.executeQuery("""
            SELECT id,
                   IFF(a IS DISTINCT FROM b, 'T', 'F'),
                   IFF(a IS NOT DISTINCT FROM b, 'T', 'F')
            FROM pairs ORDER BY id
            """);
        assertEquals(4, rs.getRowCount());
        assertEquals("F", rs.getRows().get(0).getValue(1)); // 1 vs 1
        assertEquals("T", rs.getRows().get(1).getValue(1)); // 1 vs 2
        assertEquals("T", rs.getRows().get(2).getValue(1)); // 1 vs NULL: distinct, not UNKNOWN
        assertEquals("F", rs.getRows().get(3).getValue(1)); // NULL vs NULL: not distinct
        assertEquals("T", rs.getRows().get(3).getValue(2));
    }

    @Test
    public void whereKeepsTheNullPairUnlikePlainEquality() {
        final ResultSet distinct = engine.executeQuery(
            "SELECT id FROM pairs WHERE a IS NOT DISTINCT FROM b ORDER BY id");
        assertEquals(2, distinct.getRowCount());
        assertEquals(1L, ((Number) distinct.getRows().get(0).getValue(0)).longValue());
        assertEquals(4L, ((Number) distinct.getRows().get(1).getValue(0)).longValue());

        // plain equality drops the NULL/NULL row: UNKNOWN is not TRUE
        final ResultSet equals = engine.executeQuery(
            "SELECT id FROM pairs WHERE a = b ORDER BY id");
        assertEquals(1, equals.getRowCount());
        assertEquals(1L, ((Number) equals.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void joinOnNullableKeysMatchesNullToNull() {
        engine.execute("CREATE TABLE l (k INTEGER, lv VARCHAR)");
        engine.execute("CREATE TABLE r (k INTEGER, rv VARCHAR)");
        engine.execute("INSERT INTO l VALUES (1, 'l1'), (NULL, 'lnull')");
        engine.execute("INSERT INTO r VALUES (1, 'r1'), (NULL, 'rnull'), (2, 'r2')");
        final ResultSet rs = engine.executeQuery("""
            SELECT l.lv, r.rv
            FROM l JOIN r ON l.k IS NOT DISTINCT FROM r.k
            ORDER BY l.lv
            """);
        assertEquals(2, rs.getRowCount());
        assertEquals("l1", rs.getRows().get(0).getValue(0));
        assertEquals("r1", rs.getRows().get(0).getValue(1));
        assertEquals("lnull", rs.getRows().get(1).getValue(0));
        assertEquals("rnull", rs.getRows().get(1).getValue(1)); // NULL key met NULL key
    }

    @Test
    public void negatedAndNestedInsideBooleanLogic() {
        final ResultSet rs = engine.executeQuery("""
            SELECT id FROM pairs
            WHERE (a IS DISTINCT FROM b) AND NOT (a IS NOT DISTINCT FROM 999)
            ORDER BY id
            """);
        assertEquals(2, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }
}
