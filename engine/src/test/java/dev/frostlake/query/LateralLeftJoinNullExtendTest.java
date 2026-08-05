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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code LEFT JOIN LATERAL (<correlated subquery>) … ON TRUE} null-extends the left rows whose lateral
 * produced nothing — live-verified over real tables and CTEs. The predicate-free spellings (comma
 * lateral, {@code CROSS JOIN LATERAL}) instead DROP those rows.
 *
 * <p>The sources here are real tables on purpose. Correlating a lateral into a {@code UNION ALL} derived
 * table is a separate Snowflake limitation ("Unsupported subquery type cannot be evaluated") that has
 * nothing to do with the lateral join itself, and using that shape would test the limitation rather than
 * the join. A lateral TABLE FUNCTION with an ON clause IS genuinely rejected — that belongs to
 * {@code SnowflakeStrictnessRulesTest}, not here.
 */
public class LateralLeftJoinNullExtendTest extends BaseDatabaseTest {

    @BeforeEach
    public void createSources() {
        engine.execute("CREATE TABLE lat_left (x INTEGER)");
        engine.execute("INSERT INTO lat_left VALUES (1), (2)");
        engine.execute("CREATE TABLE lat_right (k INTEGER, y INTEGER)");
        engine.execute("INSERT INTO lat_right VALUES (1, 10)");
    }

    @Test
    public void leftJoinLateralNullExtendsUnmatchedRows() {
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, l.y
            FROM lat_left t
            LEFT JOIN LATERAL (SELECT r.y FROM lat_right r WHERE r.k = t.x) l ON TRUE
            ORDER BY t.x
            """);
        assertEquals(2, rs.getRows().size());
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertNull(rs.getRows().get(1).getValue(1), "the unmatched left row must be null-extended");
    }

    @Test
    public void leftJoinLateralOverACteNullExtends() {
        final ResultSet rs = engine.executeQuery("""
            WITH tt AS (SELECT x FROM lat_left)
            SELECT tt.x, l.y
            FROM tt
            LEFT JOIN LATERAL (SELECT r.y FROM lat_right r WHERE r.k = tt.x) l ON TRUE
            ORDER BY tt.x
            """);
        assertEquals(2, rs.getRows().size());
        assertNull(rs.getRows().get(1).getValue(1));
    }

    @Test
    public void aggregateOverANullExtendedLateralCountsOnlyMatches() {
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, COUNT(DISTINCT l.y) AS c
            FROM lat_left t
            LEFT JOIN LATERAL (SELECT r.y FROM lat_right r WHERE r.k = t.x) l ON TRUE
            GROUP BY t.x ORDER BY t.x
            """);
        assertEquals(2, rs.getRows().size());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(0L, ((Number) rs.getRows().get(1).getValue(1)).longValue(),
            "COUNT(DISTINCT …) over the null-extended side is 0, not 1");
    }

    @Test
    public void innerJoinLateralKeepsOnlyMatches() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "INNER JOIN LATERAL ... ON TRUE aborts live with 'SQL execution internal error' (it raises an "
            + "incident) rather than failing compilation — an account-side defect, not a rejection rule");
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, l.y
            FROM lat_left t
            INNER JOIN LATERAL (SELECT r.y FROM lat_right r WHERE r.k = t.x) l ON TRUE
            """);
        assertEquals(1, rs.getRows().size());
    }

    @Test
    public void commaLateralDropsUnmatched() {
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, l.y
            FROM lat_left t,
            LATERAL (SELECT r.y FROM lat_right r WHERE r.k = t.x) l
            """);
        assertEquals(1, rs.getRows().size());
    }

    @Test
    public void crossJoinLateralDropsUnmatched() {
        final ResultSet rs = engine.executeQuery("""
            SELECT t.x, l.y
            FROM lat_left t
            CROSS JOIN LATERAL (SELECT r.y FROM lat_right r WHERE r.k = t.x) l
            """);
        assertEquals(1, rs.getRows().size());
    }
}
