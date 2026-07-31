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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 *
 * <p>The rule has a second, rejecting half, which a plain GROUP BY now enforces the way Snowflake does
 * (live-verified): only EARLIER aliases are visible, so naming a later item's alias is
 * {@code invalid identifier}; and because a real column outranks a same-named alias, a select item that
 * reads a column which is neither grouped nor aggregated fails to compile. Both are exercised below, next
 * to the shapes that deliberately stay permissive (star items, subqueries, GROUP BY ALL, super-groups).
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
        // CITY is both a table column and this list's alias for QTY; Snowflake resolves the COLUMN, and
        // the column is neither grouped (the key is QTY) nor aggregated — so the query does not compile
        // (live-verified). It is precisely the column-wins rule that makes this an error.
        final String message = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT qty AS city, LOWER(city) AS lc, COUNT(1) AS n "
                    + "FROM orders GROUP BY qty ORDER BY city");
            }
        }).getMessage();
        assertTrue(message.contains("SQL compilation error:"), message);
        assertTrue(message.contains("'ORDERS.CITY' in select clause is neither an aggregate "
            + "nor in the group by clause."), message);
    }

    @Test
    public void aForwardAliasReferenceIsRejected() {
        // Only EARLIER items are visible; naming a later item's alias is an unknown identifier, not a
        // NULL (live-verified).
        final String message = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT LOWER(c) AS lc, city AS c, COUNT(1) AS n FROM orders GROUP BY city");
            }
        }).getMessage();
        assertTrue(message.contains("SQL compilation error:"), message);
        assertTrue(message.contains("invalid identifier 'C'"), message);
    }

    @Test
    public void mutuallyRecursiveAliasesAreRejected() {
        // b AS a, a AS b: the FIRST item already names an alias defined later, so that is the reported
        // failure (live-verified).
        final String message = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT b AS a, a AS b, COUNT(1) AS n FROM orders GROUP BY city");
            }
        }).getMessage();
        assertTrue(message.contains("invalid identifier 'B'"), message);
    }

    @Test
    public void anItemNamingItsOwnAliasIsRejected() {
        // The degenerate self-reference: N is not a column, and its only definition is this very item.
        assertTrue(assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT n AS n, COUNT(1) AS c FROM orders GROUP BY city");
            }
        }).getMessage().contains("invalid identifier 'N'"));
    }

    // ── ungrouped columns ────────────────────────────────────────────────────

    @Test
    public void aBareUngroupedColumnIsRejected() {
        assertTrue(assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT city, qty, COUNT(1) FROM orders GROUP BY city");
            }
        }).getMessage().contains("'ORDERS.QTY' in select clause is neither an aggregate "
            + "nor in the group by clause."));
    }

    @Test
    public void anUngroupedColumnNestedInAnExpressionIsRejected() {
        assertTrue(assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT city, CASE WHEN qty > 1 THEN 'big' ELSE 'small' END, COUNT(1) "
                    + "FROM orders GROUP BY city");
            }
        }).getMessage().contains("'ORDERS.QTY' in select clause is neither an aggregate "
            + "nor in the group by clause."));
    }

    @Test
    public void anUngroupedColumnOfTheOtherSideOfAJoinIsRejected() {
        // The reported name is the column's FROM alias, the way the query itself spells it.
        engine.execute("CREATE TABLE fees (city VARCHAR, fee INTEGER)");
        engine.execute("INSERT INTO fees VALUES ('Berlin', 9)");
        assertTrue(assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                q("SELECT o.city, f.fee, COUNT(1) FROM orders o JOIN fees f ON o.city = f.city "
                    + "GROUP BY o.city");
            }
        }).getMessage().contains("'F.FEE' in select clause is neither an aggregate "
            + "nor in the group by clause."));
    }

    // ── shapes that stay legal ───────────────────────────────────────────────

    @Test
    public void aGroupedColumnMayBeReadThroughAnyExpression() {
        final ResultSet rs = q("SELECT LOWER(city) || '/' || UPPER(city) AS both, COUNT(1) AS n "
            + "FROM orders GROUP BY city ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals("berlin/BERLIN", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aGroupKeyExpressionMayBeSelectedWholesale() {
        // The key is an EXPRESSION, so its ungrouped operand is reachable only through that same
        // expression — which the SELECT list repeats verbatim.
        final ResultSet rs = q("SELECT LOWER(city) AS lc, COUNT(1) AS n "
            + "FROM orders GROUP BY LOWER(city) ORDER BY lc");
        assertEquals(2, rs.getRowCount());
        assertEquals("berlin", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void anExpressionKeyMatchesAcrossFormattingAndAnAsLessAlias() {
        // The loader shape: a multi-line CASE repeated as the grouping key on one line, carrying an
        // AS-less alias, next to an expression key whose two spellings differ only in whitespace. The
        // operands (CITY, QTY) are reachable ONLY through those expressions, so the match has to hold.
        final ResultSet rs = q("""
            SELECT COALESCE(city, '') AS c,
                   CASE
                       WHEN qty = 1 THEN 'one'
                       WHEN qty = 2 THEN 'two'
                       ELSE 'many'
                   END size_class,
                   COUNT(1) AS n
            FROM orders
            GROUP BY COALESCE(city, '') ,
                     CASE WHEN qty = 1 THEN 'one' WHEN qty = 2 THEN 'two' ELSE 'many' END
            ORDER BY c, size_class""");
        assertEquals(3, rs.getRowCount());
        assertEquals("Berlin", rs.getRows().get(0).getValue(0));
        assertEquals("one", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void anAsLessAliasIsVisibleToLaterItems() {
        final ResultSet rs = q("SELECT city c, LOWER(c) lc, COUNT(1) n FROM orders GROUP BY city ORDER BY c");
        assertEquals(2, rs.getRowCount());
        assertEquals("berlin", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void aQualifierMismatchAgainstTheGroupKeyStillResolves() {
        // GROUP BY writes the bare column, the SELECT list the alias-qualified one: the same column.
        final ResultSet rs = q("SELECT o.city, COUNT(1) AS n FROM orders o GROUP BY city ORDER BY 1");
        assertEquals(2, rs.getRowCount());
        assertEquals("Berlin", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void groupByAllAcceptsEveryItem() {
        // GROUP BY ALL derives its keys FROM the select list, so no item can be ungrouped.
        final ResultSet rs = q("SELECT city, qty, COUNT(1) AS n FROM orders GROUP BY ALL ORDER BY city, qty");
        assertEquals(3, rs.getRowCount());
        assertEquals("Berlin", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void superGroupsAcceptEveryItem() {
        // A ROLLUP / CUBE / GROUPING SETS row aggregates some dimensions away by design, so the
        // grouped-or-aggregated test does not apply to them.
        assertEquals(3, q("SELECT city, COUNT(1) AS n FROM orders GROUP BY ROLLUP(city)").getRowCount());
        assertEquals(5, q("SELECT city, qty, COUNT(1) AS n FROM orders "
            + "GROUP BY GROUPING SETS ((city), (qty))").getRowCount());
        assertEquals(9, q("SELECT city, qty, COUNT(1) AS n FROM orders "
            + "GROUP BY CUBE(city, qty)").getRowCount());   // 3 detail + 2 city + 3 qty + 1 total
    }

    @Test
    public void aStarItemIsNeverRejected() {
        // Star expansion happens after this check, so `*` is left alone rather than guessed at.
        assertEquals(3, q("SELECT *, COUNT(1) OVER () AS n FROM orders GROUP BY city, qty").getRowCount());
    }

    @Test
    public void aDatePartKeywordArgumentIsNotReadAsAColumn() {
        // DAY here is DATEADD's unit, not the table's DAY column — a bare unit word must not be
        // mistaken for an ungrouped reference.
        engine.execute("CREATE TABLE shipments (day INTEGER, sent DATE)");
        engine.execute("INSERT INTO shipments VALUES (1, '2026-01-01')");
        final ResultSet rs = q("SELECT sent, DATEADD(day, 1, sent) AS next_day, COUNT(1) AS n "
            + "FROM shipments GROUP BY sent");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
    }

    @Test
    public void aCorrelatedSubqueryItemIsNotRejected() {
        // A subquery resolves in its own scope, so its column references say nothing about this
        // query's grouping — accept rather than guess.
        engine.execute("CREATE TABLE fee_book (city VARCHAR, fee INTEGER)");
        engine.execute("INSERT INTO fee_book VALUES ('Berlin', 9), ('Oslo', 4)");
        final ResultSet rs = q("SELECT city, (SELECT MAX(fee) FROM fee_book b WHERE b.city = orders.city) "
            + "AS top_fee, COUNT(1) AS n FROM orders GROUP BY city ORDER BY city");
        assertEquals(2, rs.getRowCount());
        assertEquals(9L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
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
    public void anAggregateArgumentMayNameASiblingAlias() {
        // An alias inside an aggregate ARGUMENT means the alias's defining EXPRESSION evaluated per row,
        // not the alias's per-group value. Live-verified on a real account: the same query
        // over ('abcd'),('xy') answers 4 and 2 — exactly MAX(LENGTH(city)).
        final ResultSet rs = q("SELECT city AS c, MAX(LENGTH(c)) AS widest FROM orders GROUP BY city ORDER BY c");
        assertEquals("Berlin", rs.getRows().get(0).getValue(0));
        assertEquals(6L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals("Oslo", rs.getRows().get(1).getValue(0));
        assertEquals(4L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }
}
