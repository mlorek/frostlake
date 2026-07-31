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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake hierarchical queries — {@code [START WITH <pred>] CONNECT BY [PRIOR] c = [PRIOR] c}, the
 * {@code LEVEL} pseudo-column and {@code CONNECT_BY_ROOT <col>}.
 *
 * <p>Every expectation was probed against a live Snowflake account with this exact fixture:
 * {@code h(id,pid,nm,sal) = (1,NULL,'root',10),(2,1,'a',20),(3,1,'b',30),(4,2,'a1',40),(9,NULL,'r2',50)}
 * — two roots, one of them three levels deep. The recorded results are quoted per test.
 */
public class ConnectByTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ConnectByTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE h (id INTEGER, pid INTEGER, nm VARCHAR, sal INTEGER)");
        engine.execute("""
            INSERT INTO h VALUES
                (1, NULL, 'root', 10), (2, 1, 'a', 20), (3, 1, 'b', 30), (4, 2, 'a1', 40), (9, NULL, 'r2', 50)
            """);
    }

    private static long asLong(final Object value) {
        return ((Number) value).longValue();
    }

    @Test
    public void levelCountsTheDepthFromEachRoot() {
        // Live Snowflake: [1,root,1 | 2,a,2 | 3,b,2 | 4,a1,3 | 9,r2,1] — LEVEL is 1 at a root and grows
        // by one per step down.
        logger.info("CONNECT BY numbers each row's depth in LEVEL");
        final ResultSet rs = engine.executeQuery("""
            SELECT id, nm, LEVEL FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id ORDER BY id
            """);
        assertEquals(5, rs.getRowCount());
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(2)));
        assertEquals(2L, asLong(rs.getRows().get(1).getValue(2)));
        assertEquals(2L, asLong(rs.getRows().get(2).getValue(2)));
        assertEquals(3L, asLong(rs.getRows().get(3).getValue(2)));
        assertEquals(1L, asLong(rs.getRows().get(4).getValue(2)));
    }

    @Test
    public void connectByRootCarriesTheBranchRootValue() {
        // Live Snowflake: SELECT LEVEL, CONNECT_BY_ROOT id AS rid, id … ORDER BY rid, id →
        // [1,1,1 | 2,1,2 | 2,1,3 | 3,1,4 | 1,9,9].
        logger.info("CONNECT_BY_ROOT reports each row's branch root");
        final ResultSet rs = engine.executeQuery("""
            SELECT LEVEL, CONNECT_BY_ROOT id AS rid, id
            FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id
            ORDER BY rid, id
            """);
        assertEquals(5, rs.getRowCount());
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(1)));
        assertEquals(1L, asLong(rs.getRows().get(3).getValue(1)));
        assertEquals(3L, asLong(rs.getRows().get(3).getValue(0)));
        assertEquals(9L, asLong(rs.getRows().get(4).getValue(1)));
        assertEquals(9L, asLong(rs.getRows().get(4).getValue(2)));
    }

    @Test
    public void connectByRootWithoutAHierarchyReturnsTheColumnItself() {
        // Live Snowflake: `SELECT CONNECT_BY_ROOT nm FROM h ORDER BY 1` → [a | a1 | b | r2 | root] — with
        // no CONNECT BY clause the pseudo-column degenerates to the column's own value.
        logger.info("CONNECT_BY_ROOT without a CONNECT BY returns the plain column");
        final ResultSet rs = engine.executeQuery(
            "SELECT CONNECT_BY_ROOT nm AS test_column_alias FROM h ORDER BY 1");
        assertEquals(5, rs.getRowCount());
        assertEquals("a", rs.getRows().get(0).getValue(0));
        assertEquals("root", rs.getRows().get(4).getValue(0));
    }

    @Test
    public void withoutStartWithEveryRowSeedsItsOwnTree() {
        // Live Snowflake: `SELECT id, LEVEL FROM h CONNECT BY pid = PRIOR id ORDER BY id, LEVEL` →
        // [1,1 | 2,1 | 2,2 | 3,1 | 3,2 | 4,1 | 4,2 | 4,3 | 9,1] — nine rows, one per hierarchy position.
        logger.info("CONNECT BY without START WITH treats every row as a root");
        final ResultSet rs = engine.executeQuery("""
            SELECT id, LEVEL FROM h CONNECT BY pid = PRIOR id ORDER BY id, LEVEL
            """);
        assertEquals(9, rs.getRowCount());
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(0)));
        assertEquals(4L, asLong(rs.getRows().get(7).getValue(0)));
        assertEquals(3L, asLong(rs.getRows().get(7).getValue(1)));
    }

    @Test
    public void startWithMayFollowConnectBy() {
        // Live-verified: `CONNECT BY … START WITH …` is accepted in either order.
        logger.info("START WITH may be written after CONNECT BY");
        final ResultSet rs = engine.executeQuery("""
            SELECT id FROM h CONNECT BY pid = PRIOR id START WITH pid IS NULL ORDER BY id
            """);
        assertEquals(5, rs.getRowCount());
    }

    @Test
    public void priorMayBeWrittenOnEitherSideAndTheConditionMayConjoin() {
        // Live-verified: `CONNECT BY PRIOR id = pid` and `… AND nm <> PRIOR nm` both run and produce the
        // same five rows as the canonical spelling. A comma-separated condition list is NOT Snowflake
        // syntax, so it is not offered.
        logger.info("PRIOR works on either side and the predicate may be a conjunction");
        final ResultSet flipped = engine.executeQuery("""
            SELECT id FROM h START WITH pid IS NULL CONNECT BY PRIOR id = pid ORDER BY id
            """);
        assertEquals(5, flipped.getRowCount());
        final ResultSet conjunction = engine.executeQuery("""
            SELECT id FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id AND nm <> PRIOR nm ORDER BY id
            """);
        assertEquals(5, conjunction.getRowCount());
    }

    @Test
    public void whereFiltersTheExpandedRowsNotTheWalk() {
        // Live Snowflake: `… WHERE sal > 15 START WITH pid IS NULL CONNECT BY pid = PRIOR id` → [2|3|4|9]:
        // the root (sal = 10) is filtered out of the RESULT but its children still came through the walk.
        logger.info("WHERE applies to the expanded hierarchy, not to the walk");
        final ResultSet rs = engine.executeQuery("""
            SELECT id FROM h WHERE sal > 15 START WITH pid IS NULL CONNECT BY pid = PRIOR id ORDER BY id
            """);
        assertEquals(4, rs.getRowCount());
        assertEquals(2L, asLong(rs.getRows().get(0).getValue(0)));
        assertEquals(9L, asLong(rs.getRows().get(3).getValue(0)));
    }

    @Test
    public void starSelectsOnlyTheSourceColumns() {
        // Live Snowflake: `SELECT * FROM h START WITH … CONNECT BY …` returns h's own four columns —
        // LEVEL and CONNECT_BY_ROOT are pseudo-columns and never expand from a star.
        logger.info("SELECT * over a hierarchy shows only the source columns");
        final ResultSet rs = engine.executeQuery("""
            SELECT * FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id ORDER BY id
            """);
        assertEquals(5, rs.getRowCount());
        assertEquals(4, rs.getColumnCount());
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(0)));
        assertEquals("root", rs.getRows().get(0).getValue(2));
        assertEquals(10L, asLong(rs.getRows().get(0).getValue(3)));
    }

    @Test
    public void hierarchiesCombineWithDistinctAggregatesLimitAndSetOperations() {
        // Live-verified: DISTINCT → [1|2|3], COUNT(*) → 5, LIMIT 2 → [1|2], UNION ALL adds its own row.
        logger.info("A hierarchy behaves like an ordinary relation downstream");
        final ResultSet distinctLevels = engine.executeQuery("""
            SELECT DISTINCT LEVEL FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id ORDER BY 1
            """);
        assertEquals(3, distinctLevels.getRowCount());
        final ResultSet counted = engine.executeQuery("""
            SELECT COUNT(*) FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id
            """);
        assertEquals(5L, asLong(counted.getRows().get(0).getValue(0)));
        final ResultSet limited = engine.executeQuery("""
            SELECT id FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id ORDER BY id LIMIT 2
            """);
        assertEquals(2, limited.getRowCount());
        final ResultSet united = engine.executeQuery("""
            SELECT id FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id
            UNION ALL SELECT 99 ORDER BY id
            """);
        assertEquals(6, united.getRowCount());
    }

    @Test
    public void theSourceMayBeADerivedTableOrACte() {
        // Live-verified: both forms return [1,1 | 2,2 | 3,2 | 4,3 | 9,1].
        logger.info("CONNECT BY works over a subquery or a CTE");
        final ResultSet derived = engine.executeQuery("""
            SELECT id, LEVEL FROM (SELECT * FROM h) START WITH pid IS NULL CONNECT BY pid = PRIOR id ORDER BY id
            """);
        assertEquals(5, derived.getRowCount());
        assertEquals(3L, asLong(derived.getRows().get(3).getValue(1)));
        final ResultSet cte = engine.executeQuery("""
            WITH c AS (SELECT * FROM h)
            SELECT id, LEVEL FROM c START WITH pid IS NULL CONNECT BY pid = PRIOR id ORDER BY id
            """);
        assertEquals(5, cte.getRowCount());
        assertEquals(3L, asLong(cte.getRows().get(3).getValue(1)));
    }

    @Test
    public void aStartWithThatMatchesNothingProducesNoRows() {
        // Live Snowflake: `START WITH id = 999` → no rows.
        logger.info("A START WITH matching no row yields an empty hierarchy");
        final ResultSet rs = engine.executeQuery("""
            SELECT id FROM h START WITH id = 999 CONNECT BY pid = PRIOR id ORDER BY id
            """);
        assertEquals(0, rs.getRowCount());
    }

    @Test
    public void thePseudoColumnShadowsARealColumnNamedLevel() {
        // Live: inside a CONNECT BY query, LEVEL is the DEPTH even
        // when the source table has its own LEVEL column — for the bare reference AND the qualified
        // one alike. Outside CONNECT BY the real column keeps its values.
        logger.info("The LEVEL pseudo-column shadows a real column of the same name");
        engine.execute("CREATE TABLE lv (level INTEGER, id INTEGER)");
        engine.execute("INSERT INTO lv VALUES (7, 1)");
        final ResultSet rs = engine.executeQuery("""
            SELECT level, id FROM lv START WITH id = 1 CONNECT BY id = PRIOR id + 100
            """);
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(0)));
        final ResultSet qualified = engine.executeQuery("""
            SELECT lv.level FROM lv START WITH id = 1 CONNECT BY id = PRIOR id + 100
            """);
        assertEquals(1L, asLong(qualified.getRows().get(0).getValue(0)));
        assertEquals(7L, asLong(engine.executeQuery("SELECT level FROM lv").getRows().get(0).getValue(0)));
    }

    @Test
    public void startWithRequiresConnectByAndTheGroupingTailIsRejected() {
        logger.info("START WITH needs CONNECT BY; GROUP BY / HAVING / QUALIFY are rejected after it");
        // Live Snowflake: a lone `START WITH` is a syntax error.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT id FROM h START WITH pid IS NULL");
            }
        });
        // Live Snowflake: "syntax error … unexpected 'GROUP'" / 'HAVING' / 'QUALIFY' after a CONNECT BY.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT LEVEL, COUNT(*) FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id GROUP BY LEVEL");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT id FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id HAVING COUNT(*) > 0");
            }
        });
    }

    @Test
    public void priorOutsideAConnectByIsRejected() {
        // Live parses the stray PRIOR as an ordinary identifier (with the column as its alias) and
        // rejects it as one — "invalid identifier 'PRIOR'".
        logger.info("PRIOR is rejected outside a CONNECT BY predicate");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PRIOR id FROM h");
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("invalid identifier 'PRIOR'"));
    }

    @Test
    public void aCyclicHierarchyIsReportedRatherThanRunningForever() {
        // Snowflake performs no cycle detection — the query simply runs until something cancels it
        // (on a real account this exact statement runs for hours unless cancelled server-side;
        // the default statement timeout is two days). An in-memory
        // engine cannot hang, so the walk is depth-bounded and reports the cycle — a deliberate
        // divergence, which is exactly why this test must never reach a real account.
        Assumptions.assumeFalse(isLiveSnowflake(),
            "live Snowflake runs a cyclic CONNECT BY unboundedly; the cycle report is engine-only");
        logger.info("A cyclic hierarchy is reported instead of looping");
        engine.execute("CREATE TABLE cyc (id INTEGER, pid INTEGER)");
        engine.execute("INSERT INTO cyc VALUES (1, 2), (2, 1)");
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT id FROM cyc START WITH id = 1 CONNECT BY pid = PRIOR id");
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains("cycle"));
    }

    @Test
    public void priorAndConnectByRootStayUsableAsIdentifiers() {
        // Live-verified: `CREATE TABLE kw (prior INT)` is accepted by Snowflake (only CONNECT is reserved),
        // so the new keywords must not shadow columns of the same name.
        logger.info("PRIOR / CONNECT_BY_ROOT remain valid identifiers");
        engine.execute("CREATE TABLE kw (prior INTEGER, connect_by_root INTEGER)");
        engine.execute("INSERT INTO kw VALUES (1, 2)");
        final ResultSet rs = engine.executeQuery("SELECT prior, connect_by_root FROM kw");
        assertEquals(1, rs.getRowCount());
        assertEquals(1L, asLong(rs.getRows().get(0).getValue(0)));
        assertEquals(2L, asLong(rs.getRows().get(0).getValue(1)));
    }

    @Test
    public void priorAndConnectByRootAlsoWorkAsBareTableAliases() {
        // Live-verified per word: `SELECT prior.id FROM h prior` and `FROM h connect_by_root` both run in
        // Snowflake, while `FROM h asof` and `FROM h match_condition` are syntax errors there — so only
        // these two join the bare-alias list.
        logger.info("PRIOR / CONNECT_BY_ROOT work as un-AS'd table aliases");
        final ResultSet aliased = engine.executeQuery("SELECT prior.id FROM h prior ORDER BY prior.id");
        assertEquals(5, aliased.getRowCount());
        assertEquals(1L, asLong(aliased.getRows().get(0).getValue(0)));
        final ResultSet rootAlias = engine.executeQuery("SELECT * FROM h connect_by_root");
        assertEquals(5, rootAlias.getRowCount());
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM h asof");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM h match_condition");
            }
        });
    }

    @Test
    public void anUnaliasedConnectByRootItemIsNamedAfterItsColumn() {
        // Live-verified: `SELECT CONNECT_BY_ROOT nm …` comes back labelled NM — Snowflake names it like a
        // plain column reference rather than after the whole expression text.
        logger.info("An unaliased CONNECT_BY_ROOT column keeps the column's name");
        final ResultSet rs = engine.executeQuery("""
            SELECT CONNECT_BY_ROOT nm FROM h START WITH pid IS NULL CONNECT BY pid = PRIOR id ORDER BY 1
            """);
        assertEquals(1, rs.getColumnCount());
        assertEquals("NM", rs.getColumns().get(0).getName().toUpperCase());
    }
}
