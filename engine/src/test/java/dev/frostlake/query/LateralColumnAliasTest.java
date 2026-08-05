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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Lateral column aliases — a later item in a SELECT list may reference an alias defined by an EARLIER item
 * in the same SELECT list (Snowflake). Resolution consults the prior aliases only after table columns, so a
 * real column of the same name still wins; forward references (to a later alias) are not allowed.
 */
public class LateralColumnAliasTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE t (x INTEGER, y INTEGER)");
        engine.execute("INSERT INTO t VALUES (10, 100)");
    }

    private ResultSet row(final String sql) {
        return engine.executeQuery(sql);
    }

    private long at(final ResultSet rs, final int col) {
        return ((Number) rs.getRows().get(0).getValue(col)).longValue();
    }

    @Test
    public void aliasReferencedInLaterExpression() {
        final ResultSet rs = row("SELECT 5 AS a, a + 1 AS b FROM t");
        assertEquals(5, at(rs, 0));
        assertEquals(6, at(rs, 1));
    }

    @Test
    public void chainedAliasReferences() {
        final ResultSet rs = row("SELECT x AS a, a * 2 AS b, b + 1 AS c FROM t");
        assertEquals(10, at(rs, 0));
        assertEquals(20, at(rs, 1));
        assertEquals(21, at(rs, 2));
    }

    @Test
    public void aliasUsedInsideFunctionCall() {
        final ResultSet rs = row("SELECT x - 30 AS a, ABS(a) AS z FROM t");
        assertEquals(-20, at(rs, 0));
        assertEquals(20, at(rs, 1));
    }

    @Test
    public void bareAliasReference() {
        final ResultSet rs = row("SELECT x AS a, a AS b FROM t");
        assertEquals(10, at(rs, 0));
        assertEquals(10, at(rs, 1));
    }

    @Test
    public void realColumnTakesPrecedenceOverSameNamedAlias() {
        // 'x' in the second item must resolve to the table column (10), not the alias x (= y = 100).
        final ResultSet rs = row("SELECT y AS x, x + 1 AS w FROM t");
        assertEquals(100, at(rs, 0));
        assertEquals(11, at(rs, 1));
    }

    @Test
    public void forwardReferenceToLaterAliasIsNotAllowed() {
        // 'a' is defined AFTER the item that references it → not resolvable (matches Snowflake).
        assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT a + 1 AS c, 5 AS a FROM t");
            }
        });
    }

    @Test
    public void lateralAliasAcrossAJoin() {
        engine.execute("CREATE TABLE m (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO m VALUES (1, 7)");
        engine.execute("CREATE TABLE n (id INTEGER, w INTEGER)");
        engine.execute("INSERT INTO n VALUES (1, 3)");
        final ResultSet rs = row("SELECT m.v + n.w AS tot, tot * 10 AS scaled FROM m JOIN n ON m.id = n.id");
        assertEquals(10, at(rs, 0));
        assertEquals(100, at(rs, 1));
    }

    @Test
    public void aWhereOnAChainedAliasOverAJoinFilters() {
        // The scoring-loader shape: aliases referencing sibling aliases, with the WHERE filtering on the
        // chained one, over a multi-table FROM. Each alias used to be evaluated in isolation — the chained
        // definition threw ("Column not found"), was nulled, and the WHERE filtered every row out.
        engine.execute("CREATE TABLE cur (k VARCHAR, score NUMBER(20,10))");
        engine.execute("CREATE TABLE prop (k VARCHAR, score NUMBER(20,10))");
        engine.execute("INSERT INTO cur VALUES ('big', 0.40), ('tiny', 0.50)");
        engine.execute("INSERT INTO prop VALUES ('big', 0.02), ('tiny', 0.4999)");
        final ResultSet rs = engine.executeQuery("""
            SELECT c.k, (p.score - c.score) AS score_diff,
                   (score_diff / NULLIFZERO(c.score) * 100) AS pct
            FROM cur c INNER JOIN prop p ON p.k = c.k
            WHERE ABS(pct) >= 1 ORDER BY c.k""");
        assertEquals(1, rs.getRowCount());
        assertEquals("big", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void aliasesDoNotLeakAcrossRows() {
        engine.execute("CREATE TABLE r (a INTEGER)");
        engine.execute("INSERT INTO r VALUES (1), (2), (3)");
        final ResultSet rs = row("SELECT a * 10 AS ten, ten + 1 AS eleven FROM r ORDER BY a");
        assertEquals(3, rs.getRowCount());
        assertEquals(10, at(rs, 0));
        assertEquals(11, at(rs, 1));
        assertEquals(20, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(21, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void starFollowedByExplicitColumnsKeepsDuplicateOutputNames() {
        // SELECT *, <more> — the star's columns come first, then the explicit items are appended, even when
        // their aliases repeat star column names. The lateral-alias machinery must not merge/confuse them.
        final ResultSet rs = engine.executeQuery(
            "WITH r AS (SELECT 1 AS a, 2 AS b) SELECT *, 3 AS b, 4 AS a FROM r");
        assertEquals(4, rs.getColumns().size());
        assertEquals("A", rs.getColumns().get(0).getName());
        assertEquals("B", rs.getColumns().get(1).getName());
        assertEquals("B", rs.getColumns().get(2).getName());
        assertEquals("A", rs.getColumns().get(3).getName());
        assertEquals(1, at(rs, 0));
        assertEquals(2, at(rs, 1));
        assertEquals(3, at(rs, 2));
        assertEquals(4, at(rs, 3));
    }

    @Test
    public void anAliasReferencedInAJoinOnCondition() {
        // Snowflake lets the ON condition reference a SELECT-list alias of the same query.
        engine.execute("CREATE TABLE stg (id VARCHAR, val VARCHAR)");
        engine.execute("CREATE TABLE map (id VARCHAR, ck NUMBER)");
        engine.execute("INSERT INTO stg VALUES ('a', 'NEW'), ('b', 'OLD_B')");
        engine.execute("INSERT INTO map SELECT 'a', HASH('OLD_A')");
        engine.execute("INSERT INTO map SELECT 'b', HASH('OLD_B')");
        final ResultSet rs = engine.executeQuery("""
            SELECT c.id, HASH(c.val) _ck
            FROM stg c INNER JOIN map m ON m.id = c.id AND m.ck != _ck""");
        assertEquals(1, rs.getRowCount(), "only the changed row's checksum differs");
        assertEquals("a", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void aChainedAliasReferencedInAJoinOnCondition() {
        // The delta-comparison shape: the ON references _ck, whose defining expression itself
        // references ANOTHER select alias (_st) — the alias closure must resolve transitively, or the
        // inner join silently returns nothing and every staged update is lost.
        engine.execute("CREATE TABLE stg (id VARCHAR, val VARCHAR, st VARCHAR)");
        engine.execute("CREATE TABLE map (id VARCHAR, ckey VARCHAR, val VARCHAR, st VARCHAR, acting BOOLEAN, ck NUMBER)");
        engine.execute("INSERT INTO stg VALUES ('a', 'new-key-a', 'Current'), ('b', 'key-b', 'Current')");
        engine.execute("INSERT INTO map SELECT 'a', 'cls-1', 'hash-key-a', 'DELETED', FALSE, HASH('cls-1', 'hash-key-a', 'DELETED', FALSE)");
        engine.execute("INSERT INTO map SELECT 'b', 'cls-1', 'key-b',      'CURRENT', FALSE, HASH('cls-1', 'key-b', 'CURRENT', FALSE)");
        final ResultSet rs = engine.executeQuery("""
            SELECT c.id, c.val, UPPER(c.st) _st,
                   HASH(m.ckey, c.val, _st, m.acting) _ck
            FROM stg c
            INNER JOIN map m
                ON m.id = c.id
                AND m.ck != _ck""");
        assertEquals(1, rs.getRowCount(), "unchanged row must recompute to its stored checksum");
        assertEquals("a", rs.getRows().get(0).getValue(0));
        assertEquals("CURRENT", rs.getRows().get(0).getValue(2));
    }

    @Test
    public void aChainedAliasFeedingALateralFlatten() {
        // The lateral-context alias resolution must also chain: arr is defined over _up, and the
        // FLATTEN consumes arr.
        engine.execute("CREATE TABLE src (s VARCHAR)");
        engine.execute("INSERT INTO src VALUES ('ab')");
        final ResultSet rs = engine.executeQuery("""
            SELECT UPPER(t.s) _up, ARRAY_CONSTRUCT(_up, t.s) arr, f.value::VARCHAR v
            FROM src t, TABLE(FLATTEN(input => arr)) f""");
        assertEquals(2, rs.getRowCount());
        assertEquals("AB", rs.getRows().get(0).getValue(2));
        assertEquals("ab", rs.getRows().get(1).getValue(2));
    }

    @Test
    public void aChainedAliasAsAWindowOrderKey() {
        // The priority-CASE shape: pri_rank is defined over two sibling aliases and used as
        // the RANK() ORDER key inside QUALIFY. Unexpanded, the key evaluated to NULL for every row, so
        // every row ranked 1 and QUALIFY filtered nothing.
        engine.execute("CREATE TABLE pri (grp VARCHAR, meta VARIANT, cls VARCHAR, score NUMBER)");
        engine.execute("INSERT INTO pri SELECT 'g1', PARSE_JSON('{\"method\":\"calculated\"}'), 'OT', 1.0");
        engine.execute("INSERT INTO pri SELECT 'g1', PARSE_JSON('{\"method\":\"calculated\"}'), 'T1', 0.4");
        engine.execute("INSERT INTO pri SELECT 'g1', PARSE_JSON('{\"method\":\"default\"}'),    'T1', 0.3");
        final ResultSet rs = engine.executeQuery("""
            SELECT grp, score,
                   meta:method AS m,
                   cls AS c,
                   CASE WHEN m = 'calculated' AND c = 'OT' THEN 1
                        WHEN m = 'calculated' AND c = 'T1' THEN 2
                        WHEN m = 'default' THEN 3 END AS pri_rank
            FROM pri
            QUALIFY RANK() OVER (PARTITION BY grp ORDER BY pri_rank ASC) = 1""");
        assertEquals(1, rs.getRowCount(), "only the best-priority row survives the QUALIFY");
        assertEquals("1", String.valueOf(rs.getRows().get(0).getValue(1)).replace(".0", ""));
    }
}
