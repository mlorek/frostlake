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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two silent-corruption shapes surfaced by loader logs:
 * (1) a SELECT-list alias referenced NESTED inside a GROUP BY expression (GROUP BY CASE WHEN
 *     n_members = 1 ...) failed to resolve, so every such row collapsed into one error-key group;
 * (2) a window PARTITION BY / ORDER BY key that IS a raw aggregate select item
 *     (ROW_NUMBER() OVER (ORDER BY SUM(x) DESC) in a grouped query) evaluated to NULL, so the
 *     ranking silently followed input order.
 */
public class GroupByAliasAndWindowKeyTest extends BaseDatabaseTest {

    @BeforeEach
    public void setUpData() {
        engine.execute("CREATE TABLE acct (tag VARCHAR, members NUMBER, pairs VARIANT)");
        engine.execute("""
            INSERT INTO acct
            SELECT 'human', 1, PARSE_JSON('[1,2]') UNION ALL
            SELECT 'human', 1, PARSE_JSON('[1,2]') UNION ALL
            SELECT 'human', 3, PARSE_JSON('[1]')   UNION ALL
            SELECT 'svc',   1, PARSE_JSON('[1]')""");
    }

    @Test
    public void aliasNestedInsideAGroupByExpressionResolves() {
        final ResultSet rs = engine.executeQuery("""
            SELECT tag, members AS n_members,
                   CASE WHEN tag = 'human' AND n_members = 1 THEN 'own' ELSE 'other' END AS klass,
                   COUNT(*) AS cnt
            FROM acct
            GROUP BY tag, n_members, CASE WHEN tag = 'human' AND n_members = 1 THEN 'own' ELSE 'other' END
            ORDER BY tag, n_members""");
        assertEquals(3, rs.getRowCount());
        final Row first = rs.getRows().get(0);
        assertEquals("own", first.getValue(2));
        assertEquals(2L, ((Number) first.getValue(3)).longValue());
    }

    @Test
    public void aliasInsideAFunctionInGroupByResolves() {
        final ResultSet rs = engine.executeQuery("""
            SELECT pairs AS pre_pairs, ARRAY_SIZE(pre_pairs) AS sz, COUNT(*) AS cnt
            FROM acct GROUP BY pre_pairs, ARRAY_SIZE(pre_pairs) ORDER BY sz""");
        assertEquals(2, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void windowOrderByRawAggregateRanksByTheAggregate() {
        // OVER (ORDER BY SUM(members) DESC) must rank by the computed aggregate, exactly like the
        // aliased form OVER (ORDER BY tot DESC).
        final ResultSet rs = engine.executeQuery("""
            SELECT tag, SUM(members) AS tot,
                   ROW_NUMBER() OVER (ORDER BY SUM(members) DESC) AS rn
            FROM acct GROUP BY tag ORDER BY rn""");
        assertEquals(2, rs.getRowCount());
        assertEquals("human", rs.getRows().get(0).getValue(0));
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
        assertEquals("svc", rs.getRows().get(1).getValue(0));
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void windowPartitionByRawAggregateGroupsByTheAggregate() {
        engine.execute("CREATE TABLE t2 (grp VARCHAR, v NUMBER)");
        engine.execute("""
            INSERT INTO t2
            SELECT 'a', 10 UNION ALL SELECT 'b', 10 UNION ALL SELECT 'c', 20""");
        // Partitions formed by SUM(v): {a, b} (10) and {c} (20).
        final ResultSet rs = engine.executeQuery("""
            SELECT grp, SUM(v) AS tot,
                   COUNT(grp) OVER (PARTITION BY SUM(v)) AS same_tot_groups
            FROM t2 GROUP BY grp ORDER BY grp""");
        assertEquals(3, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(2).getValue(2)).longValue());
    }

    @Test
    public void groupByAllSeesAggregatesThroughSiblingAliases() {
        // count(*) AS n, CASE WHEN n = 1 ... — the CASE references the aggregate via its alias and is
        // therefore NOT a group key under GROUP BY ALL (Snowflake classification). Misclassifying it
        // as a key failed its evaluation and collapsed all rows into one error-key group.
        engine.execute("CREATE TABLE logins (dev VARCHAR, tag VARCHAR, login VARCHAR)");
        engine.execute("""
            INSERT INTO logins
            SELECT 'dev1','human','u1' UNION ALL
            SELECT 'dev2','human','u2' UNION ALL
            SELECT 'dev2','human','u3' UNION ALL
            SELECT 'dev3','svc','u4'""");
        final ResultSet rs = engine.executeQuery("""
            SELECT dev, tag,
                   count(*) as n_members,
                   CASE WHEN tag = 'human' AND n_members = 1 THEN 'owner' ELSE tag END as owner_tag
            FROM logins GROUP BY ALL ORDER BY dev""");
        assertEquals(3, rs.getRowCount());
        assertEquals("owner", rs.getRows().get(0).getValue(3));
        assertEquals("human", rs.getRows().get(1).getValue(3));
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void groupByAllExpandsNonAggregateAliasChains() {
        engine.execute("CREATE TABLE srcfiles (fname VARCHAR)");
        engine.execute("""
            INSERT INTO srcfiles
            SELECT 'a-1.json' UNION ALL SELECT 'a-1.json' UNION ALL SELECT 'b-2.json'""");
        final ResultSet rs = engine.executeQuery("""
            SELECT SPLIT(fname, '-') as fname_split,
                   fname_split[1]::VARCHAR as suffix,
                   count(*) as cnt
            FROM srcfiles GROUP BY ALL ORDER BY suffix""");
        assertEquals(2, rs.getRowCount());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void luminRankingShapeEndToEnd() {
        // The loader shape: alias-of-aggregate keys plus an expression over a group-key alias in the
        // same OVER clause, with a WITHIN GROUP aggregate as a sibling item.
        engine.execute("CREATE TABLE metric (grp VARCHAR, sub VARCHAR, item VARCHAR, m NUMBER, sev NUMBER)");
        engine.execute("""
            INSERT INTO metric
            SELECT 'g1','a:1','d1',10,3 UNION ALL SELECT 'g1','a:1','d2',5,2 UNION ALL
            SELECT 'g1','b:2','d3',30,1 UNION ALL SELECT 'g1','c:3','d4',20,2 UNION ALL
            SELECT 'g2','a:1','d5',7,1""");
        final ResultSet rs = engine.executeQuery("""
            SELECT grp, sub AS proposed,
                   ARRAY_AGG(DISTINCT item) WITHIN GROUP (ORDER BY item ASC) AS targeted,
                   SUM(m) AS ranking_metric,
                   SUM(sev) AS pasl_sum,
                   ROW_NUMBER() OVER (
                       PARTITION BY grp
                       ORDER BY ranking_metric DESC, pasl_sum DESC, try_cast(split_part(proposed, ':', 2) as number) desc
                   ) AS rnk
            FROM metric GROUP BY grp, sub ORDER BY grp, rnk""");
        assertEquals(4, rs.getRowCount());
        assertEquals("b:2", rs.getRows().get(0).getValue(1));
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(5)).longValue());
        assertEquals("c:3", rs.getRows().get(1).getValue(1));
        assertEquals("a:1", rs.getRows().get(2).getValue(1));
        assertEquals(3L, ((Number) rs.getRows().get(2).getValue(5)).longValue());
    }
}
