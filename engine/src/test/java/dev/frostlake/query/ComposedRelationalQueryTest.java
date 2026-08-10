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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Composed relational pipelines: every test runs ONE query that stacks several features —
 * joins (inner/left/full/asof), chained and recursive CTEs, LATERAL (correlated inline view and
 * FLATTEN, including {@code outer => TRUE}), window functions (rank family, frames, LAG/LEAD
 * defaults, window-over-window) and QUALIFY (inline window, select-list alias, over GROUP BY).
 *
 * <p>The point is the interaction of the stages, which no single-feature test exercises: QUALIFY
 * evaluating windows over grouped joins, FLATTEN output feeding further joins, a recursive CTE's
 * result carrying a window, deduplication via ROW_NUMBER over UNION ALL.
 *
 * <p>Fixtures are small and integer-valued so every expectation is hand-computable, and every
 * query carries a full ORDER BY so embedded and live row order agree.
 */
public class ComposedRelationalQueryTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ComposedRelationalQueryTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE customers (id INTEGER, name VARCHAR, region VARCHAR)");
        engine.execute("INSERT INTO customers VALUES"
            + " (1, 'Alice', 'EMEA'), (2, 'Bob', 'EMEA'),"
            + " (3, 'Cara', 'APAC'), (4, 'Dan', 'APAC'),"
            + " (5, 'Eve', 'AMER')"); // Eve deliberately has no orders
        engine.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER, amount INTEGER, d DATE)");
        engine.execute("INSERT INTO orders VALUES"
            + " (101, 1, 500, '2026-01-05'), (102, 1, 300, '2026-01-20'),"
            + " (103, 2, 500, '2026-02-02'), (104, 2, 400, '2026-02-10'),"
            + " (105, 3, 700, '2026-01-15'), (106, 3, 100, '2026-02-01'),"
            + " (107, 4, 200, '2026-01-10')");
        // per-customer totals: Alice 800, Bob 900, Cara 800, Dan 200, Eve —
    }

    private void createTags() {
        engine.execute("CREATE TABLE order_tags (order_id INTEGER, tags VARIANT)");
        engine.execute("INSERT INTO order_tags SELECT 101, PARSE_JSON('[\"rush\",\"gift\"]')");
        engine.execute("INSERT INTO order_tags SELECT 103, PARSE_JSON('[\"rush\"]')");
        engine.execute("INSERT INTO order_tags SELECT 105, PARSE_JSON('[\"bulk\",\"rush\"]')");
        engine.execute("INSERT INTO order_tags SELECT 107, PARSE_JSON('[\"gift\"]')");
    }

    /** Cell-by-cell row assertion: numbers compare as long, strings as text, null as SQL NULL. */
    private void assertRow(final ResultSet rs, final int row, final Object... expected) {
        for (int i = 0; i < expected.length; i++) {
            final Object actual = rs.getRows().get(row).getValue(i);
            final Object want = expected[i];
            if (want == null) {
                assertNull(actual, "row " + row + " col " + i);
            } else if (want instanceof Number) {
                assertEquals(((Number) want).longValue(), ((Number) actual).longValue(),
                    "row " + row + " col " + i);
            } else {
                assertEquals(want, String.valueOf(actual), "row " + row + " col " + i);
            }
        }
    }

    @Test
    public void chainedCtesJoinWindowThenFilterOnRank() {
        logger.info("two chained CTEs: join+group, then rank, outer filter picks the regional top");
        final ResultSet rs = engine.executeQuery("""
            WITH totals AS (
              SELECT c.region, c.name, SUM(o.amount) AS total
              FROM customers c JOIN orders o ON o.customer_id = c.id
              GROUP BY c.region, c.name
            ), ranked AS (
              SELECT region, name, total,
                     RANK() OVER (PARTITION BY region ORDER BY total DESC) AS r
              FROM totals
            )
            SELECT region, name, total FROM ranked WHERE r = 1 ORDER BY region
            """);
        assertEquals(2, rs.getRowCount());
        assertRow(rs, 0, "APAC", "Cara", 800);
        assertRow(rs, 1, "EMEA", "Bob", 900);
    }

    @Test
    public void qualifyWithInlineWindowOverGroupedJoin() {
        logger.info("QUALIFY computes RANK over SUM directly on the grouped join, no CTE");
        final ResultSet rs = engine.executeQuery("""
            SELECT c.region, c.name, SUM(o.amount) AS total
            FROM customers c JOIN orders o ON o.customer_id = c.id
            GROUP BY c.region, c.name
            QUALIFY RANK() OVER (PARTITION BY c.region ORDER BY SUM(o.amount) DESC) = 1
            ORDER BY c.region
            """);
        assertEquals(2, rs.getRowCount());
        assertRow(rs, 0, "APAC", "Cara", 800);
        assertRow(rs, 1, "EMEA", "Bob", 900);
    }

    @Test
    public void qualifyOnSelectAliasAcrossLeftJoin() {
        logger.info("QUALIFY references a select-list window alias; LEFT JOIN keeps the orderless customer");
        final ResultSet rs = engine.executeQuery("""
            SELECT c.name, COALESCE(SUM(o.amount), 0) AS total,
                   ROW_NUMBER() OVER (ORDER BY COALESCE(SUM(o.amount), 0) DESC, c.name) AS rn
            FROM customers c LEFT JOIN orders o ON o.customer_id = c.id
            GROUP BY c.name
            QUALIFY rn <= 3
            ORDER BY rn
            """);
        assertEquals(3, rs.getRowCount());
        assertRow(rs, 0, "Bob", 900, 1);
        assertRow(rs, 1, "Alice", 800, 2);
        assertRow(rs, 2, "Cara", 800, 3);
    }

    @Test
    public void lateralFlattenCteFeedsJoinsGroupingAndQualify() {
        createTags();
        logger.info("FLATTEN in a CTE, two joins on top, grouped, top tag per region via QUALIFY");
        final ResultSet rs = engine.executeQuery("""
            WITH tagged AS (
              SELECT t.order_id, f.value::VARCHAR AS tag
              FROM order_tags t, LATERAL FLATTEN(input => t.tags) f
            )
            SELECT c.region, tg.tag, COUNT(*) AS uses
            FROM tagged tg
            JOIN orders o ON o.id = tg.order_id
            JOIN customers c ON c.id = o.customer_id
            GROUP BY c.region, tg.tag
            QUALIFY ROW_NUMBER() OVER (PARTITION BY c.region ORDER BY COUNT(*) DESC, tg.tag) = 1
            ORDER BY c.region
            """);
        assertEquals(2, rs.getRowCount());
        assertRow(rs, 0, "APAC", "bulk", 1); // three APAC tags tie at 1: alphabetical wins
        assertRow(rs, 1, "EMEA", "rush", 2);
    }

    @Test
    public void lateralCorrelatedInlineViewPerRow() {
        logger.info("LATERAL inline view correlates on the outer customer row");
        final ResultSet rs = engine.executeQuery("""
            SELECT c.name, m.top_amount
            FROM customers c,
                 LATERAL (SELECT MAX(o.amount) AS top_amount FROM orders o
                          WHERE o.customer_id = c.id) m
            WHERE c.region = 'EMEA'
            ORDER BY c.name
            """);
        assertEquals(2, rs.getRowCount());
        assertRow(rs, 0, "Alice", 500);
        assertRow(rs, 1, "Bob", 500);
    }

    @Test
    public void lateralFlattenOuterAfterLeftJoinKeepsUntaggedOrders() {
        createTags();
        logger.info("LEFT JOIN to tags, then outer FLATTEN null-extends orders that have none");
        final ResultSet rs = engine.executeQuery("""
            SELECT o.id, f.value::VARCHAR AS tag
            FROM orders o
            LEFT JOIN order_tags t ON t.order_id = o.id,
            LATERAL FLATTEN(input => t.tags, outer => TRUE) f
            QUALIFY ROW_NUMBER() OVER (PARTITION BY o.id ORDER BY COALESCE(f.value::VARCHAR, '')) = 1
            ORDER BY o.id
            """);
        assertEquals(7, rs.getRowCount());
        assertRow(rs, 0, 101, "gift"); // first alphabetically of [rush, gift]
        assertRow(rs, 1, 102, (Object) null);
        assertRow(rs, 2, 103, "rush");
        assertRow(rs, 3, 104, (Object) null);
        assertRow(rs, 4, 105, "bulk");
        assertRow(rs, 5, 106, (Object) null);
        assertRow(rs, 6, 107, "gift");
    }

    @Test
    public void windowFramesOverAJoinedDailyCte() {
        logger.info("running and 2-row moving sums over a grouped join in a CTE");
        final ResultSet rs = engine.executeQuery("""
            WITH daily AS (
              SELECT o.d, SUM(o.amount) AS day_total
              FROM orders o JOIN customers c ON c.id = o.customer_id
              WHERE c.region IN ('EMEA', 'APAC')
              GROUP BY o.d
            )
            SELECT TO_CHAR(d, 'YYYY-MM-DD') AS day, day_total,
                   SUM(day_total) OVER (ORDER BY d ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running,
                   SUM(day_total) OVER (ORDER BY d ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS moving2
            FROM daily ORDER BY d
            """);
        assertEquals(7, rs.getRowCount());
        assertRow(rs, 0, "2026-01-05", 500, 500, 500);
        assertRow(rs, 1, "2026-01-10", 200, 700, 700);
        assertRow(rs, 2, "2026-01-15", 700, 1400, 900);
        assertRow(rs, 3, "2026-01-20", 300, 1700, 1000);
        assertRow(rs, 4, "2026-02-01", 100, 1800, 400);
        assertRow(rs, 5, "2026-02-02", 500, 2300, 600);
        assertRow(rs, 6, "2026-02-10", 400, 2700, 900);
    }

    @Test
    public void lagAndLeadWithDefaultsOverJoinedSeries() {
        logger.info("LAG/LEAD with explicit defaults, partitioned per customer over a join");
        final ResultSet rs = engine.executeQuery("""
            WITH seq AS (
              SELECT c.name, o.d, o.amount
              FROM orders o JOIN customers c ON c.id = o.customer_id
            )
            SELECT name, amount,
                   LAG(amount, 1, 0) OVER (PARTITION BY name ORDER BY d) AS prev,
                   LEAD(amount, 1, -1) OVER (PARTITION BY name ORDER BY d) AS next
            FROM seq WHERE name IN ('Alice', 'Bob')
            ORDER BY name, d
            """);
        assertEquals(4, rs.getRowCount());
        assertRow(rs, 0, "Alice", 500, 0, 300);
        assertRow(rs, 1, "Alice", 300, 500, -1);
        assertRow(rs, 2, "Bob", 500, 0, 400);
        assertRow(rs, 3, "Bob", 400, 500, -1);
    }

    @Test
    public void fullOuterJoinOfTwoCtesRankedByCombinedTotal() {
        logger.info("month CTEs FULL OUTER JOINed, COALESCEd, ranked by the combined total");
        final ResultSet rs = engine.executeQuery("""
            WITH jan AS (
              SELECT c.name, SUM(o.amount) AS a
              FROM customers c JOIN orders o ON o.customer_id = c.id
              WHERE o.d < '2026-02-01'::DATE GROUP BY c.name
            ), feb AS (
              SELECT c.name, SUM(o.amount) AS a
              FROM customers c JOIN orders o ON o.customer_id = c.id
              WHERE o.d >= '2026-02-01'::DATE GROUP BY c.name
            )
            SELECT COALESCE(j.name, f.name) AS name,
                   COALESCE(j.a, 0) AS jan_amt,
                   COALESCE(f.a, 0) AS feb_amt,
                   RANK() OVER (ORDER BY COALESCE(j.a, 0) + COALESCE(f.a, 0) DESC,
                                COALESCE(j.name, f.name)) AS r
            FROM jan j FULL OUTER JOIN feb f ON f.name = j.name
            ORDER BY r
            """);
        assertEquals(4, rs.getRowCount());
        assertRow(rs, 0, "Bob", 0, 900, 1);
        assertRow(rs, 1, "Alice", 800, 0, 2);
        assertRow(rs, 2, "Cara", 700, 100, 3);
        assertRow(rs, 3, "Dan", 200, 0, 4);
    }

    @Test
    public void recursiveCteJoinedRowsCarryAWindow() {
        engine.execute("CREATE TABLE org (emp VARCHAR, mgr VARCHAR)");
        engine.execute("INSERT INTO org VALUES ('root', NULL), ('b1', 'root'), ('b2', 'root'),"
            + " ('c1', 'b1'), ('c2', 'b1'), ('d1', 'c1')");
        logger.info("recursive CTE walks the hierarchy; COUNT() OVER counts peers per depth");
        final ResultSet rs = engine.executeQuery("""
            WITH RECURSIVE chain (emp, depth) AS (
              SELECT emp, 1 FROM org WHERE mgr IS NULL
              UNION ALL
              SELECT o.emp, chain.depth + 1
              FROM org o JOIN chain ON o.mgr = chain.emp
            )
            SELECT emp, depth, COUNT(*) OVER (PARTITION BY depth) AS peers
            FROM chain
            QUALIFY peers = 2
            ORDER BY depth, emp
            """);
        assertEquals(4, rs.getRowCount());
        assertRow(rs, 0, "b1", 2, 2);
        assertRow(rs, 1, "b2", 2, 2);
        assertRow(rs, 2, "c1", 3, 2);
        assertRow(rs, 3, "c2", 3, 2);
    }

    @Test
    public void asofJoinThenQualifyKeepsTheLatestTickPerSymbol() {
        engine.execute("CREATE TABLE ticks (sym VARCHAR, t INTEGER, px INTEGER)");
        engine.execute("CREATE TABLE quotes (sym VARCHAR, t INTEGER, bid INTEGER)");
        engine.execute("INSERT INTO ticks VALUES ('A', 10, 100), ('A', 20, 110), ('B', 15, 200)");
        engine.execute("INSERT INTO quotes VALUES ('A', 5, 99), ('A', 18, 108), ('B', 30, 190)");
        logger.info("ASOF JOIN matches the newest quote at-or-before each tick; QUALIFY keeps the last tick");
        final ResultSet rs = engine.executeQuery("""
            SELECT tk.sym, tk.t, tk.px, q.bid
            FROM ticks tk ASOF JOIN quotes q
                 MATCH_CONDITION (tk.t >= q.t) ON tk.sym = q.sym
            QUALIFY ROW_NUMBER() OVER (PARTITION BY tk.sym ORDER BY tk.t DESC) = 1
            ORDER BY tk.sym
            """);
        assertEquals(2, rs.getRowCount());
        assertRow(rs, 0, "A", 20, 110, 108);
        assertRow(rs, 1, "B", 15, 200, null); // B's only quote is later: null-extended, still kept
    }

    @Test
    public void existsAgainstAHavingCteCombinedWithQualify() {
        logger.info("EXISTS over a HAVING-filtered CTE narrows the join; QUALIFY picks each top order");
        final ResultSet rs = engine.executeQuery("""
            WITH busy AS (
              SELECT customer_id FROM orders GROUP BY customer_id HAVING COUNT(*) >= 2
            )
            SELECT c.name, o.amount,
                   ROW_NUMBER() OVER (PARTITION BY c.id ORDER BY o.amount DESC, o.id) AS rn
            FROM customers c JOIN orders o ON o.customer_id = c.id
            WHERE EXISTS (SELECT 1 FROM busy b WHERE b.customer_id = c.id)
            QUALIFY rn = 1
            ORDER BY c.name
            """);
        assertEquals(3, rs.getRowCount());
        assertRow(rs, 0, "Alice", 500, 1);
        assertRow(rs, 1, "Bob", 500, 1);
        assertRow(rs, 2, "Cara", 700, 1);
    }

    @Test
    public void unionAllDedupedByQualifyRowNumber() {
        logger.info("two overlapping branches UNION ALLed; ROW_NUMBER + QUALIFY dedups, keeping branch 1");
        final ResultSet rs = engine.executeQuery("""
            WITH merged AS (
              SELECT id, amount, 1 AS src FROM orders WHERE amount >= 400
              UNION ALL
              SELECT id, amount, 2 AS src FROM orders WHERE customer_id IN (1, 2)
            )
            SELECT id, amount, src FROM merged
            QUALIFY ROW_NUMBER() OVER (PARTITION BY id ORDER BY src) = 1
            ORDER BY id
            """);
        assertEquals(5, rs.getRowCount());
        assertRow(rs, 0, 101, 500, 1);
        assertRow(rs, 1, 102, 300, 2); // only in the customer branch
        assertRow(rs, 2, 103, 500, 1);
        assertRow(rs, 3, 104, 400, 1);
        assertRow(rs, 4, 105, 700, 1);
    }

    @Test
    public void windowOverWindowThroughStackedCtes() {
        logger.info("first CTE aggregates, second layers a partition total, outer ranks and QUALIFYs");
        final ResultSet rs = engine.executeQuery("""
            WITH totals AS (
              SELECT c.region, c.name, SUM(o.amount) AS total
              FROM customers c JOIN orders o ON o.customer_id = c.id
              GROUP BY c.region, c.name
            ), shared AS (
              SELECT region, name, total,
                     SUM(total) OVER (PARTITION BY region) AS region_total
              FROM totals
            )
            SELECT region, name, total, region_total,
                   RANK() OVER (PARTITION BY region ORDER BY total DESC) AS r
            FROM shared
            QUALIFY r <= 2
            ORDER BY region, r, name
            """);
        assertEquals(4, rs.getRowCount());
        assertRow(rs, 0, "APAC", "Cara", 800, 1000, 1);
        assertRow(rs, 1, "APAC", "Dan", 200, 1000, 2);
        assertRow(rs, 2, "EMEA", "Bob", 900, 1700, 1);
        assertRow(rs, 3, "EMEA", "Alice", 800, 1700, 2);
    }
}
