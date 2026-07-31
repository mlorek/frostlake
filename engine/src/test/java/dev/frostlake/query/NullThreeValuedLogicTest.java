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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Snowflake three-valued (NULL) logic: comparisons and IN against NULL are UNKNOWN (excluded by a
 * predicate), NULL join keys do not match, and ORDER BY sorts NULLs last (ASC) / first (DESC).
 * Grouping / set-operation equality (NULL = NULL for GROUP BY, DISTINCT, UNION) is deliberately the
 * opposite and is covered by {@link QueryExecutorEdgeCaseTest}. An explicit {@code NULLS FIRST} /
 * {@code NULLS LAST} clause overrides the default placement.
 */
public class NullThreeValuedLogicTest extends BaseDatabaseTest {

    private ResultSet run(final String sql) {
        return engine.executeQuery(sql);
    }

    // x <> 'A' is UNKNOWN when x is NULL, so the NULL row is excluded (only 'B' remains).
    @Test
    public void whereNotEqualExcludesNulls() {
        engine.execute("CREATE TABLE t (x VARCHAR)");
        engine.execute("INSERT INTO t VALUES ('A'), ('B'), (NULL)");
        final ResultSet rs = run("SELECT x FROM t WHERE x <> 'A'");
        assertEquals(1, rs.getRowCount());
        assertEquals("B", rs.getRows().get(0).getValue(0));
    }

    // x = NULL is always UNKNOWN — no rows qualify (NULL is not "equal" to NULL in a predicate).
    @Test
    public void equalityWithNullIsUnknown() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (NULL)");
        final ResultSet rs = run("SELECT x FROM t WHERE x = NULL");
        assertEquals(0, rs.getRowCount());
    }

    // x NOT IN (1, NULL): for non-matching x the NULL makes the result UNKNOWN, so no rows qualify.
    @Test
    public void notInWithNullReturnsNoRows() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        final ResultSet rs = run("SELECT x FROM t WHERE x NOT IN (1, NULL)");
        assertEquals(0, rs.getRowCount());
    }

    // x IN (1, NULL) still matches a concrete value (x = 1); the NULL is simply never a match.
    @Test
    public void inWithNullStillMatchesConcreteValue() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        final ResultSet rs = run("SELECT x FROM t WHERE x IN (1, NULL)");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    // NULL join keys never match (a.k = b.k is UNKNOWN when either side is NULL): only 1 = 1 joins.
    @Test
    public void nullJoinKeysDoNotMatch() {
        engine.execute("CREATE TABLE a (k INTEGER)");
        engine.execute("INSERT INTO a VALUES (1), (NULL)");
        engine.execute("CREATE TABLE b (k INTEGER)");
        engine.execute("INSERT INTO b VALUES (1), (NULL)");
        final ResultSet rs = run("SELECT a.k FROM a JOIN b ON a.k = b.k");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    // ORDER BY ... ASC sorts NULLs last (Snowflake treats NULL as greater than any value).
    @Test
    public void orderByAscPutsNullsLast() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (2), (NULL), (1)");
        final ResultSet rs = run("SELECT x FROM t ORDER BY x ASC");
        assertEquals(3, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertNull(rs.getRows().get(2).getValue(0));
    }

    // A NULL inequality is UNKNOWN as well, so NOT (x < 5) does not resurrect the NULL row.
    @Test
    public void notLessThanWithNullIsUnknown() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (10), (NULL)");
        final ResultSet rs = run("SELECT x FROM t WHERE NOT (x < 5)");
        assertEquals(1, rs.getRowCount());
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    // ORDER BY ... DESC sorts NULLs first (mirror of the ASC case).
    @Test
    public void orderByDescPutsNullsFirst() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (2), (NULL), (1)");
        final ResultSet rs = run("SELECT x FROM t ORDER BY x DESC");
        assertEquals(3, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    // Explicit NULLS FIRST overrides the ASC default (which is NULLS LAST).
    @Test
    public void orderByAscNullsFirstOverride() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (2), (NULL), (1)");
        final ResultSet rs = run("SELECT x FROM t ORDER BY x ASC NULLS FIRST");
        assertEquals(3, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    // Explicit NULLS LAST overrides the DESC default (which is NULLS FIRST).
    @Test
    public void orderByDescNullsLastOverride() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (2), (NULL), (1)");
        final ResultSet rs = run("SELECT x FROM t ORDER BY x DESC NULLS LAST");
        assertEquals(3, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertNull(rs.getRows().get(2).getValue(0));
    }

    // The NULLS clause also applies on the post-GROUP BY sort path.
    @Test
    public void groupByOrderByNullsFirst() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (2), (NULL), (1), (2)");
        final ResultSet rs = run("SELECT x, COUNT(*) FROM t GROUP BY x ORDER BY x ASC NULLS FIRST");
        assertEquals(3, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    // ── three-valued AND/OR ────────────────────────────────────────────────────────────────────────
    // For plain filtering, UNKNOWN and FALSE both exclude, so the difference only shows under NOT
    // and when the boolean itself is projected.

    // NOT (UNKNOWN AND TRUE) is UNKNOWN — the NULL row must not leak back in through NOT.
    @Test
    public void notOverAndWithUnknownIsUnknown() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (NULL)");
        final ResultSet rs = run("SELECT x FROM t WHERE NOT (x = 1 AND 1 = 1)");
        assertEquals(1, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    // NOT (UNKNOWN OR UNKNOWN) is UNKNOWN as well.
    @Test
    public void notOverOrWithUnknownIsUnknown() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (NULL)");
        final ResultSet rs = run("SELECT x FROM t WHERE NOT (x = 1 OR x = 3)");
        assertEquals(1, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    // Projected: UNKNOWN AND TRUE is NULL, but FALSE dominates UNKNOWN (and TRUE dominates for OR).
    @Test
    public void projectedAndOrThreeValuedResults() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (NULL)");
        assertNull(run("SELECT (x = 1 AND 1 = 1) FROM t").getRows().get(0).getValue(0));
        assertEquals(Boolean.FALSE, run("SELECT (x = 1 AND 1 = 2) FROM t").getRows().get(0).getValue(0));
        assertNull(run("SELECT (x = 1 OR 1 = 2) FROM t").getRows().get(0).getValue(0));
        assertEquals(Boolean.TRUE, run("SELECT (x = 1 OR 1 = 1) FROM t").getRows().get(0).getValue(0));
    }

    // ── three-valued quantified comparisons (ALL / ANY) ───────────────────────────────────────────

    // x <> ALL (set with NULL) is the NOT IN twin: never TRUE, so no rows qualify.
    @Test
    public void notEqualAllWithNullIsUnknown() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        engine.execute("CREATE TABLE s (v INTEGER)");
        engine.execute("INSERT INTO s VALUES (5), (NULL)");
        final ResultSet rs = run("SELECT x FROM t WHERE x <> ALL (SELECT v FROM s)");
        assertEquals(0, rs.getRowCount());
    }

    // x = ANY still matches a concrete value — TRUE dominates the NULL member.
    @Test
    public void equalAnyWithNullStillMatchesConcrete() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        engine.execute("CREATE TABLE s (v INTEGER)");
        engine.execute("INSERT INTO s VALUES (2), (NULL)");
        final ResultSet rs = run("SELECT x FROM t WHERE x = ANY (SELECT v FROM s)");
        assertEquals(1, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    // No concrete match + a NULL member → = ANY is UNKNOWN, so NOT does not resurrect the rows.
    @Test
    public void notOverEqualAnyWithNullIsUnknown() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "live Snowflake's semi-join rewrite deviates from its own scalar 3VL here");
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (1), (2), (3)");
        engine.execute("CREATE TABLE s (v INTEGER)");
        engine.execute("INSERT INTO s VALUES (5), (NULL)");
        final ResultSet rs = run("SELECT x FROM t WHERE NOT (x = ANY (SELECT v FROM s))");
        assertEquals(0, rs.getRowCount());
    }

    // A NULL left operand makes the quantified comparison UNKNOWN for every row.
    @Test
    public void nullLeftQuantifiedIsUnknown() {
        engine.execute("CREATE TABLE t (x INTEGER)");
        engine.execute("INSERT INTO t VALUES (NULL), (5)");
        final ResultSet rs = run("SELECT x FROM t WHERE x > ALL (SELECT 1)");
        assertEquals(1, rs.getRowCount());
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    // NULLS / LAST / FIRST remain usable as ordinary column names (kept non-reserved).
    @Test
    public void nullsFirstLastUsableAsColumnNames() {
        engine.execute("CREATE TABLE k (nulls INTEGER, last VARCHAR, first INTEGER)");
        engine.execute("INSERT INTO k VALUES (1, 'a', 2)");
        final ResultSet rs = run("SELECT nulls, last, first FROM k ORDER BY nulls");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("a", rs.getRows().get(0).getValue(1));
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
    }
}
