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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Snowflake lateral column aliases in a SELECT list that does NOT go through {@code ProjectOperator}: a select
 * item may reference the alias of an EARLIER item of the same list, e.g.
 * {@code SELECT city AS c, LOWER(c) AS lc, COUNT(1) FROM orders GROUP BY city}.
 *
 * <p>The plain projection path always supported this. Two other paths did not, because each projects the SELECT
 * list itself instead of delegating to {@code ProjectOperator}: (1) every GROUPED path — plain GROUP BY, GROUP BY
 * ALL, positional ordinals, ROLLUP/CUBE/GROUPING SETS and implicit whole-relation aggregation — which evaluated
 * each item against a representative group row with no sibling-alias context, so the derived item silently came
 * out NULL; and (2) a WINDOWED projection, which failed the whole query with "Column not found". Both are
 * covered here, along with the precedence rule (a real column of the same name wins) and the one shape that
 * stays unsupported (an alias inside an aggregate's argument, which is a per-row context).
 */
public class GroupByLateralAliasTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE orders (city VARCHAR, qty INTEGER)");
        engine.execute("INSERT INTO orders VALUES ('Berlin', 1), ('Berlin', 2), ('Oslo', 5)");
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    // ── the four grouped paths ───────────────────────────────────────────────

    @Test
    public void plainGroupByResolvesAnEarlierAlias() {
        final ResultSet rs = q("SELECT city AS c, LOWER(c) AS lc, COUNT(1) AS n "
            + "FROM orders GROUP BY city ORDER BY c");
        assertEquals(2, rs.getRowCount());
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
        assertEquals("oslo", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void groupByAllResolvesAnEarlierAlias() {
        final ResultSet rs = q("SELECT city AS c, LOWER(c) AS lc, COUNT(1) AS n "
            + "FROM orders GROUP BY ALL ORDER BY c");
        assertEquals(2, rs.getRowCount());
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
        assertEquals("oslo", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void positionalGroupByResolvesAnEarlierAlias() {
        final ResultSet rs = q("SELECT city AS c, UPPER(c) || '!' AS shout, SUM(qty) AS total "
            + "FROM orders GROUP BY 1 ORDER BY c");
        assertEquals("BERLIN!", rs.getRows().get(0).getValue(1));
        assertEquals("OSLO!", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void implicitAggregationResolvesAnEarlierAlias() {
        final ResultSet rs = q("SELECT COUNT(1) AS n, n + 1 AS next FROM orders");
        assertEquals(1, rs.getRowCount());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(4L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    // ── what the alias may be derived from ───────────────────────────────────

    @Test
    public void aliasesChainAcrossSeveralItems() {
        final ResultSet rs = q("SELECT city AS c, LOWER(c) AS lc, lc || '#' AS tagged, COUNT(1) AS n "
            + "FROM orders GROUP BY city ORDER BY c");
        assertEquals("berlin#", rs.getRows().get(0).getValue(2));
        assertEquals("oslo#", rs.getRows().get(1).getValue(2));
    }

    @Test
    public void anItemMayBeExactlyAnEarlierAggregateAlias() {
        // The defining item is an aggregate, so no single group row could recompute it — the already
        // computed value has to be reused.
        final ResultSet rs = q("SELECT COUNT(1) AS n, n AS again, city AS c FROM orders GROUP BY city ORDER BY c");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void anItemMayBeDerivedFromAnEarlierAggregateAlias() {
        final ResultSet rs = q("SELECT city AS c, SUM(qty) AS total, total * 10 AS scaled "
            + "FROM orders GROUP BY city ORDER BY c");
        assertEquals(30L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
        assertEquals(50L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void theAliasValueIsPerGroupNotShared() {
        // A group whose derived item yields NULL must not leave a stale value behind for the next group.
        final ResultSet rs = q("SELECT city AS c, CASE WHEN c = 'Berlin' THEN c ELSE NULL END AS only_berlin, "
            + "COUNT(1) AS n FROM orders GROUP BY city ORDER BY c");
        assertEquals("Berlin", rs.getRows().get(0).getValue(1));
        assertNull(rs.getRows().get(1).getValue(1));
    }

    // ── precedence and scoping rules ─────────────────────────────────────────

    @Test
    public void aRealColumnWinsOverAnAliasOfTheSameName() {
        // CITY is both a table column and this list's alias for QTY; Snowflake resolves the column, so
        // LOWER(city) reads 'Berlin'/'Oslo' rather than the aliased quantity.
        final ResultSet rs = q("SELECT qty AS city, LOWER(city) AS lc, COUNT(1) AS n "
            + "FROM orders GROUP BY qty ORDER BY city");
        assertEquals(3, rs.getRowCount());
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void aForwardAliasReferenceDoesNotResolve() {
        // Only EARLIER items are visible, matching the ungrouped projection path.
        assertNull(q("SELECT LOWER(c) AS lc, city AS c, COUNT(1) AS n FROM orders GROUP BY city")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void mutuallyRecursiveAliasesDoNotRecurse() {
        final ResultSet rs = q("SELECT b AS a, a AS b, COUNT(1) AS n FROM orders GROUP BY city");
        assertEquals(2, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(0));
        assertNull(rs.getRows().get(0).getValue(1));
    }

    @Test
    public void aQuotedAliasResolves() {
        final ResultSet rs = q("SELECT city AS \"CityName\", LOWER(\"CityName\") AS lc, COUNT(1) AS n "
            + "FROM orders GROUP BY city ORDER BY 1");
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
    }

    // ── super-groups: a NULLed dimension must NULL its derived siblings ───────

    @Test
    public void rollupNullsADerivedAliasOnSubtotalRows() {
        // The grand-total row aggregates CITY away, so LOWER(c) must be NULL there too — not the
        // representative row's value.
        final ResultSet rs = q("SELECT city AS c, LOWER(c) AS lc, COUNT(1) AS n "
            + "FROM orders GROUP BY ROLLUP(city)");
        assertEquals(3, rs.getRowCount());
        int totals = 0;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (rs.getRows().get(i).getValue(0) == null) {
                assertNull(rs.getRows().get(i).getValue(1));
                totals++;
            } else {
                assertEquals(rs.getRows().get(i).getValue(0).toString().toLowerCase(),
                    rs.getRows().get(i).getValue(1));
            }
        }
        assertEquals(1, totals);
    }

    @Test
    public void groupingSetsNullADerivedAliasWhereItsSourceIsAggregatedAway() {
        final ResultSet rs = q("SELECT city AS c, qty AS q, LOWER(c) AS lc, COUNT(1) AS n "
            + "FROM orders GROUP BY GROUPING SETS ((city), (qty))");
        assertEquals(5, rs.getRowCount());
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (rs.getRows().get(i).getValue(0) == null) {
                assertNull(rs.getRows().get(i).getValue(2));
            } else {
                assertEquals(rs.getRows().get(i).getValue(0).toString().toLowerCase(),
                    rs.getRows().get(i).getValue(2));
            }
        }
    }

    // ── interaction with the rest of the pipeline ────────────────────────────

    @Test
    public void anEmptyGroupStillEvaluatesADerivedAlias() {
        final ResultSet rs = q("SELECT COUNT(*) AS n, n + 1 AS next FROM orders WHERE FALSE");
        assertEquals(1, rs.getRowCount());
        assertEquals(0L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void aDerivedAliasIsOrderable() {
        final ResultSet rs = q("SELECT city AS c, LOWER(c) AS lc, COUNT(1) AS n "
            + "FROM orders GROUP BY city ORDER BY lc DESC");
        assertEquals("oslo", rs.getRows().get(0).getValue(1));
        assertEquals("berlin", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void aDerivedAliasWorksOverAJoin() {
        engine.execute("CREATE TABLE couriers (city VARCHAR, fee INTEGER)");
        engine.execute("INSERT INTO couriers VALUES ('Berlin', 9)");
        final ResultSet rs = q("SELECT o.city AS c, LOWER(c) AS lc, COUNT(1) AS n "
            + "FROM orders o JOIN couriers r ON o.city = r.city GROUP BY o.city");
        assertEquals(1, rs.getRowCount());
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
    }

    // ── items that CONTAIN an aggregate but reference the alias outside it ────

    @Test
    public void aSiblingAliasResolvesInAnIffWhoseBranchesAreAggregates() {
        // The loader shape: SUM(...) AS n, then IFF(n > 0, <agg>, <agg>). The alias sits in the condition, not
        // in an aggregate's argument, so it is a plain per-group scalar there. Items containing an aggregate
        // anywhere used to take a path with no alias context at all, making the whole item NULL.
        engine.execute("CREATE TABLE deliveries (city VARCHAR, active BOOLEAN, courier VARCHAR)");
        engine.execute("INSERT INTO deliveries VALUES ('Berlin', TRUE, 'ada'), ('Berlin', TRUE, 'bo'), "
            + "('Oslo', FALSE, 'cy')");
        final ResultSet rs = q("SELECT city AS c, SUM(IFF(active, 1, 0)) AS active_count, "
            + "IFF(active_count > 0, ARRAY_UNIQUE_AGG(IFF(active, courier, NULL)), ARRAY_UNIQUE_AGG(courier)) "
            + "AS couriers FROM deliveries GROUP BY city ORDER BY c");
        assertEquals(2, rs.getRowCount());
        assertEquals("[\"ada\",\"bo\"]", rs.getRows().get(0).getValue(2).toString());
        assertEquals("[\"cy\"]", rs.getRows().get(1).getValue(2).toString());
    }

    @Test
    public void aSiblingAliasResolvesAlongsideAnAggregateInArithmetic() {
        final ResultSet rs = q("SELECT city AS c, SUM(qty) AS total, total + COUNT(1) AS combo "
            + "FROM orders GROUP BY city ORDER BY c");
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(2)).longValue());   // 3 + 2
        assertEquals(6L, ((Number) rs.getRows().get(1).getValue(2)).longValue());   // 5 + 1
    }

    @Test
    public void aRealColumnStillWinsInsideAnAggregateBearingItem() {
        // Same precedence rule as everywhere else: QTY is a real column, so it is read per row by the
        // aggregate rather than picking up the alias of the same name.
        final ResultSet rs = q("SELECT SUM(qty) AS qty, MAX(qty) + 0 AS biggest, city AS c "
            + "FROM orders GROUP BY city ORDER BY c");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());   // MAX of 1,2 — not of SUM
        assertEquals(5L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    // ── windowed projections ─────────────────────────────────────────────────

    @Test
    public void aSiblingAliasResolvesWhenTheListHasAWindowFunction() {
        // A window function makes QueryExecutor skip ProjectOperator, so the windowed projection has to offer
        // the aliases itself; it used to fail the whole query with "Column not found".
        final ResultSet rs = q("SELECT city AS c, LOWER(c) AS lc, "
            + "ROW_NUMBER() OVER (PARTITION BY city ORDER BY qty DESC) AS rn FROM orders ORDER BY c, rn");
        assertEquals(3, rs.getRowCount());
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
        assertEquals("oslo", rs.getRows().get(2).getValue(1));
    }

    @Test
    public void aliasesChainInAWindowedProjection() {
        final ResultSet rs = q("SELECT city AS c, LOWER(c) AS lc, lc || '#' AS tagged, "
            + "ROW_NUMBER() OVER (ORDER BY qty) AS rn FROM orders ORDER BY rn");
        assertEquals("berlin#", rs.getRows().get(0).getValue(2));
        assertEquals("oslo#", rs.getRows().get(2).getValue(2));
    }

    @Test
    public void qualifyFiltersOnAWindowAliasAlongsideADerivedAlias() {
        final ResultSet rs = q("SELECT city AS c, LOWER(c) AS lc, "
            + "ROW_NUMBER() OVER (PARTITION BY city ORDER BY qty DESC) AS rn "
            + "FROM orders QUALIFY rn = 1 ORDER BY c");
        assertEquals(2, rs.getRowCount());
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
        assertEquals("oslo", rs.getRows().get(1).getValue(1));
    }

    @Test
    public void anItemMayReuseAWindowFunctionsAlias() {
        // The defining item is a window function, so the value cannot be recomputed per row — it has to be
        // reused from the alias.
        final ResultSet rs = q("SELECT ROW_NUMBER() OVER (ORDER BY qty) AS rn, rn AS again FROM orders "
            + "ORDER BY rn");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(3L, ((Number) rs.getRows().get(2).getValue(1)).longValue());
    }

    @Test
    public void aRealColumnWinsOverAnAliasInAWindowedProjection() {
        final ResultSet rs = q("SELECT qty AS city, LOWER(city) AS lc, "
            + "ROW_NUMBER() OVER (ORDER BY qty) AS rn FROM orders ORDER BY rn");
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void aggregatingOverASiblingAliasStaysUnsupported() {
        // Documented limitation: the sink carries VALUES, and feeding a per-group constant into a per-row
        // aggregate argument would be wrong whenever the alias is not a group key. Resolving this needs the
        // alias's defining EXPRESSION substituted into the argument, so it stays NULL rather than wrong.
        assertNull(q("SELECT city AS c, MAX(LENGTH(c)) AS widest FROM orders GROUP BY city")
            .getRows().get(0).getValue(1));
    }
}
