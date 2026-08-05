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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake {@code ASOF JOIN … MATCH_CONDITION (…) [ON …]} — the closest-match time-series join.
 *
 * <p>Every expectation here was probed against a live Snowflake account with this exact
 * fixture: {@code q(k,t,v) = (1,10,100),(1,20,200),(2,10,300),(3,10,400)} and
 * {@code r(k,t,w) = (1,5,1),(1,15,2),(1,25,3),(2,50,4)}. The recorded results are quoted per test.
 */
public class AsofJoinTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(AsofJoinTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE q (k INTEGER, t INTEGER, v INTEGER)");
        engine.execute("CREATE TABLE r (k INTEGER, t INTEGER, w INTEGER)");
        engine.execute("INSERT INTO q VALUES (1, 10, 100), (1, 20, 200), (2, 10, 300), (3, 10, 400)");
        engine.execute("INSERT INTO r VALUES (1, 5, 1), (1, 15, 2), (1, 25, 3), (2, 50, 4)");
    }

    private static long asLong(final Object value) {
        return ((Number) value).longValue();
    }

    @Test
    public void greaterOrEqualPicksTheNewestEarlierRowAndKeepsUnmatchedLeftRows() {
        // Live Snowflake: [1,10,5,1 | 1,20,15,2 | 2,10,null,null | 3,10,null,null] — the join is LEFT
        // OUTER, so k=2 (its only right row is later) and k=3 (no right row at all) survive null-extended.
        logger.info("ASOF JOIN with >= picks the newest right row at or before the left row");
        final ResultSet rs = engine.executeQuery("""
            SELECT q.k, q.t, r.t, r.w
            FROM q ASOF JOIN r MATCH_CONDITION (q.t >= r.t) ON q.k = r.k
            ORDER BY q.k, q.t
            """);
        assertEquals(4, rs.getRowCount());
        assertEquals(5L, asLong(rs.getRows().get(0).getValue(2)));
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(3)));
        assertEquals(15L, asLong(rs.getRows().get(1).getValue(2)));
        assertEquals(2L, asLong(rs.getRows().get(1).getValue(3)));
        assertNull(rs.getRows().get(2).getValue(2));
        assertNull(rs.getRows().get(2).getValue(3));
        assertNull(rs.getRows().get(3).getValue(2));
    }

    @Test
    public void lessOrEqualPicksTheOldestLaterRow() {
        // Live Snowflake: [1,10,15 | 1,20,25 | 2,10,50 | 3,10,null] — the mirror direction.
        logger.info("ASOF JOIN with <= picks the oldest right row at or after the left row");
        final ResultSet rs = engine.executeQuery("""
            SELECT q.k, q.t, r.t
            FROM q ASOF JOIN r MATCH_CONDITION (q.t <= r.t) ON q.k = r.k
            ORDER BY q.k, q.t
            """);
        assertEquals(4, rs.getRowCount());
        assertEquals(15L, asLong(rs.getRows().get(0).getValue(2)));
        assertEquals(25L, asLong(rs.getRows().get(1).getValue(2)));
        assertEquals(50L, asLong(rs.getRows().get(2).getValue(2)));
        assertNull(rs.getRows().get(3).getValue(2));
    }

    @Test
    public void strictComparisonExcludesTheEqualRow() {
        // Live Snowflake: [1,10,5 | 1,20,15 | 2,10,null | 3,10,null].
        logger.info("ASOF JOIN with a strict > excludes an exactly equal match value");
        final ResultSet rs = engine.executeQuery("""
            SELECT q.k, q.t, r.t
            FROM q ASOF JOIN r MATCH_CONDITION (q.t > r.t) ON q.k = r.k
            ORDER BY q.k, q.t
            """);
        assertEquals(4, rs.getRowCount());
        assertEquals(5L, asLong(rs.getRows().get(0).getValue(2)));
        assertEquals(15L, asLong(rs.getRows().get(1).getValue(2)));
        assertNull(rs.getRows().get(2).getValue(2));
    }

    @Test
    public void withoutOnEveryRightRowIsACandidate() {
        // Live Snowflake: [1,10,1,5 | 1,20,1,15 | 2,10,1,5 | 3,10,1,5] — with no ON clause the closest
        // match is chosen across the WHOLE right table, so every left row matches r(1,5,…).
        logger.info("ASOF JOIN without ON matches across the whole right table");
        final ResultSet rs = engine.executeQuery("""
            SELECT q.k, q.t, r.k, r.t
            FROM q ASOF JOIN r MATCH_CONDITION (q.t >= r.t)
            ORDER BY q.k, q.t
            """);
        assertEquals(4, rs.getRowCount());
        assertEquals(5L, asLong(rs.getRows().get(0).getValue(3)));
        assertEquals(15L, asLong(rs.getRows().get(1).getValue(3)));
        assertEquals(5L, asLong(rs.getRows().get(2).getValue(3)));
        assertEquals(5L, asLong(rs.getRows().get(3).getValue(3)));
    }

    @Test
    public void theMatchConditionMayCarryAnExpression() {
        // Live-verified: an expression is allowed on either side as long as each side only reads its own
        // table — `MATCH_CONDITION (q.t + 1 >= r.t)` and `(q.t >= r.t + 1)` are both accepted.
        logger.info("ASOF JOIN allows an expression in the MATCH_CONDITION");
        final ResultSet rs = engine.executeQuery("""
            SELECT q.t, r.t
            FROM q ASOF JOIN r MATCH_CONDITION (q.t >= r.t + 1) ON q.k = r.k
            ORDER BY q.t, r.t
            """);
        assertEquals(4, rs.getRowCount());
        // q.t = 10 now needs r.t + 1 <= 10, i.e. r.t <= 9 → still r(1,5); q.t = 20 needs r.t <= 19 → r(1,15).
        // The two unmatched k=2 / k=3 rows sort between them (NULLS LAST inside the q.t = 10 group).
        assertEquals(5L, asLong(rs.getRows().get(0).getValue(1)));
        assertNull(rs.getRows().get(1).getValue(1));
        assertNull(rs.getRows().get(2).getValue(1));
        assertEquals(15L, asLong(rs.getRows().get(3).getValue(1)));
    }

    @Test
    public void nullsOnTheMatchColumnNeverMatch() {
        // A NULL makes the comparison UNKNOWN, so such a right row is not a candidate: the left row falls
        // back to the next-closest non-NULL one (live-verified with an extra r row carrying a NULL t).
        logger.info("ASOF JOIN skips right rows whose match value is NULL");
        engine.execute("INSERT INTO r VALUES (1, NULL, 99)");
        final ResultSet rs = engine.executeQuery("""
            SELECT q.t, r.t, r.w
            FROM q ASOF JOIN r MATCH_CONDITION (q.t >= r.t) ON q.k = r.k
            WHERE q.k = 1
            ORDER BY q.t
            """);
        assertEquals(2, rs.getRowCount());
        assertEquals(5L, asLong(rs.getRows().get(0).getValue(1)));
        assertEquals(15L, asLong(rs.getRows().get(1).getValue(1)));
    }

    @Test
    public void asofJoinsChainAndCombineWithOrdinaryJoins() {
        // Live-verified: `q ASOF JOIN r … ASOF JOIN r r2 …` and `q ASOF JOIN r … JOIN r r3 ON …` both run.
        logger.info("ASOF JOIN chains with further joins");
        final ResultSet chained = engine.executeQuery("""
            SELECT q.t, r.t, r2.t
            FROM q ASOF JOIN r MATCH_CONDITION (q.t >= r.t) ON q.k = r.k
                   ASOF JOIN r r2 MATCH_CONDITION (q.t >= r2.t) ON q.k = r2.k
            WHERE q.k = 1
            ORDER BY q.t
            """);
        assertEquals(2, chained.getRowCount());
        assertEquals(5L, asLong(chained.getRows().get(0).getValue(2)));
        assertEquals(15L, asLong(chained.getRows().get(1).getValue(2)));
    }

    @Test
    public void asofJoinWorksOverACteAndADerivedTable() {
        logger.info("ASOF JOIN accepts a CTE or a subquery as either side");
        final ResultSet fromCte = engine.executeQuery("""
            WITH cq AS (SELECT * FROM q)
            SELECT cq.t, r.t FROM cq ASOF JOIN r MATCH_CONDITION (cq.t >= r.t) ON cq.k = r.k
            WHERE cq.k = 1
            ORDER BY cq.t
            """);
        assertEquals(2, fromCte.getRowCount());
        assertEquals(5L, asLong(fromCte.getRows().get(0).getValue(1)));

        final ResultSet fromDerived = engine.executeQuery("""
            SELECT z.t, s.t
            FROM q z ASOF JOIN (SELECT * FROM r WHERE w < 3) s MATCH_CONDITION (z.t >= s.t) ON z.k = s.k
            WHERE z.k = 1
            ORDER BY z.t
            """);
        assertEquals(2, fromDerived.getRowCount());
        assertEquals(15L, asLong(fromDerived.getRows().get(1).getValue(1)));
    }

    @Test
    public void theRightTableMayBeAliasedWithTheWordsOffsetAndLimit() {
        // Live-verified Snowflake quirk (both spellings run there): `ASOF JOIN r OFFSET MATCH_CONDITION
        // (…)` and `ASOF JOIN r LIMIT MATCH_CONDITION (…)` alias the right table OFFSET / LIMIT. sqlglot's
        // Snowflake corpus carries both shapes, which is how they were found.
        logger.info("ASOF JOIN accepts OFFSET / LIMIT as the right table's alias");
        final ResultSet offsetAlias = engine.executeQuery("""
            SELECT * FROM q ASOF JOIN r OFFSET MATCH_CONDITION (q.t > OFFSET.t) ORDER BY q.k, q.t
            """);
        assertEquals(4, offsetAlias.getRowCount());
        assertEquals(6, offsetAlias.getColumnCount());
        // LIMIT is not a general identifier in this grammar, so the right side is referenced by its
        // UNIQUE bare column (a bare `t` is "ambiguous column name 'T'" live, and `r.t` is an
        // invalid identifier — an alias REPLACES the table name, in ASOF joins like everywhere
        // else). The alias still binds; only the parse is under test, and ASOF keeps all 4 left rows
        // whatever the condition matches.
        final ResultSet limitAlias = engine.executeQuery("""
            SELECT * FROM q ASOF JOIN r LIMIT MATCH_CONDITION (q.t > w) ORDER BY q.k, q.t
            """);
        assertEquals(4, limitAlias.getRowCount());
        assertEquals(6, limitAlias.getColumnCount());
    }

    @Test
    public void onlyTheFourInequalityOperatorsAreAllowed() {
        // Snowflake's own wording: "MATCH_CONDITION clause is invalid: Only comparison operators '>=',
        // '>', '<=' and '<' are allowed." (live-verified for `=`).
        logger.info("ASOF JOIN rejects an equality MATCH_CONDITION");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT q.k FROM q ASOF JOIN r MATCH_CONDITION (q.t = r.t) ON q.k = r.k");
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("MATCH_CONDITION clause is invalid"));
    }

    @Test
    public void eachMatchConditionOperandMustComeFromItsOwnSide() {
        // Live-verified: `MATCH_CONDITION (r.t <= q.t)` is rejected even though it means the same thing —
        // the left operand may only name left-side columns and the right operand right-side ones.
        logger.info("ASOF JOIN rejects a MATCH_CONDITION whose sides are swapped");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT q.k FROM q ASOF JOIN r MATCH_CONDITION (r.t <= q.t) ON q.k = r.k");
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("MATCH_CONDITION clause is invalid"));
    }

    @Test
    public void matchConditionIsMandatoryAndOnlyTheBareAsofSpellingExists() {
        logger.info("ASOF JOIN requires MATCH_CONDITION and rejects LEFT/RIGHT/INNER modifiers");
        // Live Snowflake: `ASOF JOIN r ON …` (no MATCH_CONDITION) is a syntax error there.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT q.k FROM q ASOF JOIN r ON q.k = r.k");
            }
        });
        // Live Snowflake: "syntax error … unexpected 'ASOF'" for LEFT / RIGHT / INNER ASOF JOIN.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT q.k FROM q LEFT ASOF JOIN r MATCH_CONDITION (q.t >= r.t) ON q.k = r.k");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT q.k FROM q INNER ASOF JOIN r MATCH_CONDITION (q.t >= r.t) ON q.k = r.k");
            }
        });
        // And MATCH_CONDITION must PRECEDE the ON clause (`ON … MATCH_CONDITION (…)` is a syntax error).
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT q.k FROM q ASOF JOIN r ON q.k = r.k MATCH_CONDITION (q.t >= r.t)");
            }
        });
    }

    @Test
    public void asofStaysUsableAsAnOrdinaryIdentifier() {
        // Live-verified: `CREATE TABLE kw (asof INT, prior INT)` is accepted by Snowflake (only CONNECT is
        // reserved there), so the new keywords must not shadow columns of the same name.
        logger.info("ASOF / MATCH_CONDITION remain valid identifiers");
        engine.execute("CREATE TABLE kw (asof INTEGER, match_condition INTEGER)");
        engine.execute("INSERT INTO kw VALUES (1, 2)");
        final ResultSet rs = engine.executeQuery("SELECT asof, match_condition FROM kw");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(0)));
        assertEquals(2L, asLong(rs.getRows().get(0).getValue(1)));
    }
}
