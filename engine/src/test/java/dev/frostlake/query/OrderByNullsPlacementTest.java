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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * NULL placement in ORDER BY: Snowflake's DEFAULT is NULLS LAST for ASC and NULLS FIRST for DESC
 * (the opposite convention of some engines), overridable with explicit NULLS FIRST/LAST — and the
 * same default governs window ORDER BY.
 */
public class OrderByNullsPlacementTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (id INTEGER, n INTEGER)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, NULL), (3, 20)");
    }

    private void assertOrder(final String sql, final Object... nColumn) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(nColumn.length, rs.getRowCount());
        for (int i = 0; i < nColumn.length; i++) {
            final Object actual = rs.getRows().get(i).getValue(0);
            if (nColumn[i] == null) {
                assertNull(actual, "row " + i);
            } else {
                assertEquals(((Number) nColumn[i]).longValue(), ((Number) actual).longValue(),
                    "row " + i);
            }
        }
    }

    @Test
    public void ascDefaultsToNullsLast() {
        assertOrder("SELECT n FROM t ORDER BY n", 10, 20, null);
        assertOrder("SELECT n FROM t ORDER BY n ASC", 10, 20, null);
    }

    @Test
    public void descDefaultsToNullsFirst() {
        assertOrder("SELECT n FROM t ORDER BY n DESC", null, 20, 10);
    }

    @Test
    public void explicitPlacementOverridesTheDefault() {
        assertOrder("SELECT n FROM t ORDER BY n ASC NULLS FIRST", null, 10, 20);
        assertOrder("SELECT n FROM t ORDER BY n DESC NULLS LAST", 20, 10, null);
    }

    @Test
    public void windowOrderByFollowsTheSameDefaults() {
        // ASC window order: nulls last -> the NULL row ranks 3; DESC: nulls first -> it ranks 1.
        final ResultSet rs = engine.executeQuery("""
            SELECT id,
                   ROW_NUMBER() OVER (ORDER BY n) AS asc_rn,
                   ROW_NUMBER() OVER (ORDER BY n DESC) AS desc_rn
            FROM t ORDER BY id
            """);
        assertEquals(3, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(1).getValue(1)).longValue()); // NULL row
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(2).getValue(1)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(2).getValue(2)).longValue());
    }
}
