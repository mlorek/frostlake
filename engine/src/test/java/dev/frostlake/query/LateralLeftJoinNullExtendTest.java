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
 * LEFT JOIN LATERAL (subquery) ON TRUE must KEEP a left row whose lateral produced no rows,
 * null-extended over the lateral's columns — it previously behaved as INNER and dropped them,
 * emptying the vendor's 12-month trend loaders. INNER and comma laterals still drop such rows.
 */
public class LateralLeftJoinNullExtendTest extends BaseDatabaseTest {

    @Test
    public void testLeftLateralKeepsUnmatchedLeftRows() {
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, l.y
            FROM (SELECT 1 AS x UNION ALL SELECT 2) t
            LEFT JOIN LATERAL (SELECT u.y FROM (SELECT 1 AS k, 10 AS y) u WHERE u.k = t.x) l ON TRUE
            ORDER BY t.x
            """);
        assertEquals(2, rs.getRows().size());
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertNull(rs.getRows().get(1).getValue(1));
    }

    @Test
    public void testInnerLateralStillDropsUnmatched() {
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, l.y
            FROM (SELECT 1 AS x UNION ALL SELECT 2) t
            INNER JOIN LATERAL (SELECT u.y FROM (SELECT 1 AS k, 10 AS y) u WHERE u.k = t.x) l ON TRUE
            """);
        assertEquals(1, rs.getRows().size());
    }

    @Test
    public void testCommaLateralStillDropsUnmatched() {
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, l.y
            FROM (SELECT 1 AS x UNION ALL SELECT 2) t,
            LATERAL (SELECT u.y FROM (SELECT 1 AS k, 10 AS y) u WHERE u.k = t.x) l
            """);
        assertEquals(1, rs.getRows().size());
    }

    @Test
    public void testAggregateOverNullExtendedLateral() {
        // The vendor trend idiom: COUNT(DISTINCT lateral column) per left row — 0 where nothing matched.
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, COUNT(DISTINCT l.y) AS c
            FROM (SELECT 1 AS x UNION ALL SELECT 2) t
            LEFT JOIN LATERAL (SELECT u.y FROM (SELECT 1 AS k, 10 AS y) u WHERE u.k = t.x) l ON TRUE
            GROUP BY t.x ORDER BY t.x
            """);
        assertEquals(2, rs.getRows().size());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(0L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }
}
