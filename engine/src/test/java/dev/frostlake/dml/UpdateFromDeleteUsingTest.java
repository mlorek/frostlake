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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UPDATE … FROM &lt;source&gt; and DELETE … USING &lt;source&gt; — Snowflake's join-update / join-delete, where
 * the target table is joined with one or more source tables on the WHERE predicate. SET expressions and the
 * predicate resolve qualified references to both the target and the source(s).
 */
public class UpdateFromDeleteUsingTest extends BaseDatabaseTest {

    private long longAt(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    private String stringAt(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0).toString();
    }

    private long count(final String table) {
        return longAt("SELECT COUNT(*) FROM " + table);
    }

    // ── UPDATE … FROM ────────────────────────────────────────────────────────────────────────────────

    @Test
    public void updateFromCopiesMatchingSourceValues() {
        engine.execute("CREATE TABLE tgt (id INTEGER, amount INTEGER)");
        engine.execute("INSERT INTO tgt VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute("CREATE TABLE src (id INTEGER, amount INTEGER)");
        engine.execute("INSERT INTO src VALUES (1, 100), (2, 200)");

        engine.execute("UPDATE tgt SET amount = src.amount FROM src WHERE tgt.id = src.id");

        assertEquals(100, longAt("SELECT amount FROM tgt WHERE id = 1"));
        assertEquals(200, longAt("SELECT amount FROM tgt WHERE id = 2"));
        // Row 3 has no matching source row → left unchanged.
        assertEquals(30, longAt("SELECT amount FROM tgt WHERE id = 3"));
    }

    @Test
    public void updateFromEvaluatesExpressionOverBothTables() {
        engine.execute("CREATE TABLE acct (id INTEGER, balance INTEGER)");
        engine.execute("INSERT INTO acct VALUES (1, 100), (2, 50)");
        engine.execute("CREATE TABLE adj (id INTEGER, delta INTEGER)");
        engine.execute("INSERT INTO adj VALUES (1, 5), (2, 7)");

        engine.execute("UPDATE acct SET balance = acct.balance + adj.delta FROM adj WHERE acct.id = adj.id");

        assertEquals(105, longAt("SELECT balance FROM acct WHERE id = 1"));
        assertEquals(57, longAt("SELECT balance FROM acct WHERE id = 2"));
    }

    @Test
    public void updateFromHonorsSourceAlias() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'old'), (2, 'old')");
        engine.execute("CREATE TABLE s (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO s VALUES (1, 'Alice'), (2, 'Bob')");

        engine.execute("UPDATE t SET name = src.name FROM s AS src WHERE t.id = src.id");

        assertEquals("Alice", stringAt("SELECT name FROM t WHERE id = 1"));
        assertEquals("Bob", stringAt("SELECT name FROM t WHERE id = 2"));
    }

    @Test
    public void updateFromTwoSourcesJoinsThroughBoth() {
        engine.execute("CREATE TABLE t (id INTEGER, label VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, '?'), (2, '?')");
        engine.execute("CREATE TABLE a (id INTEGER, code INTEGER)");
        engine.execute("INSERT INTO a VALUES (1, 10), (2, 20)");
        engine.execute("CREATE TABLE b (code INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO b VALUES (10, 'TEN'), (20, 'TWENTY')");

        engine.execute("UPDATE t SET label = b.name FROM a, b WHERE t.id = a.id AND a.code = b.code");

        assertEquals("TEN", stringAt("SELECT label FROM t WHERE id = 1"));
        assertEquals("TWENTY", stringAt("SELECT label FROM t WHERE id = 2"));
    }

    @Test
    public void updateFromRollsBackWithTransaction() {
        engine.execute("CREATE TABLE tgt (id INTEGER, amount INTEGER)");
        engine.execute("INSERT INTO tgt VALUES (1, 10)");
        engine.execute("CREATE TABLE src (id INTEGER, amount INTEGER)");
        engine.execute("INSERT INTO src VALUES (1, 999)");

        engine.execute("BEGIN");
        engine.execute("UPDATE tgt SET amount = src.amount FROM src WHERE tgt.id = src.id");
        engine.execute("ROLLBACK");

        // The join-update participates in the deferred write set, so ROLLBACK discards it.
        assertEquals(10, longAt("SELECT amount FROM tgt WHERE id = 1"));
    }

    // ── DELETE … USING ───────────────────────────────────────────────────────────────────────────────

    @Test
    public void deleteUsingRemovesMatchingRows() {
        engine.execute("CREATE TABLE tgt (id INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO tgt VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        engine.execute("CREATE TABLE bad (id INTEGER)");
        engine.execute("INSERT INTO bad VALUES (1), (3)");

        engine.execute("DELETE FROM tgt USING bad WHERE tgt.id = bad.id");

        assertEquals(1, count("tgt"));
        assertEquals(2, longAt("SELECT id FROM tgt"));   // only row 2 survives
    }

    @Test
    public void deleteUsingHonorsSourceAliasAndPredicate() {
        engine.execute("CREATE TABLE orders (id INTEGER, region VARCHAR)");
        engine.execute("INSERT INTO orders VALUES (1, 'EAST'), (2, 'WEST'), (3, 'EAST')");
        engine.execute("CREATE TABLE removals (region VARCHAR)");
        engine.execute("INSERT INTO removals VALUES ('EAST')");

        engine.execute("DELETE FROM orders USING removals AS p WHERE orders.region = p.region");

        // Both EAST orders are removed; the WEST order remains.
        assertEquals(1, count("orders"));
        assertEquals("WEST", stringAt("SELECT region FROM orders"));
    }

    @Test
    public void deleteUsingWithNoMatchesKeepsAllRows() {
        engine.execute("CREATE TABLE tgt (id INTEGER)");
        engine.execute("INSERT INTO tgt VALUES (1), (2)");
        engine.execute("CREATE TABLE other (id INTEGER)");
        engine.execute("INSERT INTO other VALUES (8), (9)");

        engine.execute("DELETE FROM tgt USING other WHERE tgt.id = other.id");

        assertEquals(2, count("tgt"));
    }

    // ── target ALIAS + columns whose names collide with the source ─────────────────────────────────────
    // Regression: the target alias must be registered so the predicate can tell it apart from the source.
    // Previously wcs.cid = d.cid resolved both sides to the target's column, matching every row and
    // deleting/updating them all.

    @Test
    public void deleteUsingTargetAliasWithCollidingColumnNames() {
        engine.execute("CREATE TABLE t (cid INTEGER, ak INTEGER)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute("CREATE TABLE src (cid INTEGER, ak INTEGER)");
        engine.execute("INSERT INTO src VALUES (2, 20), (3, 30)");

        engine.execute("DELETE FROM t wcs USING src d WHERE wcs.cid = d.cid AND wcs.ak = d.ak");

        // Only rows present in src (by cid,ak) are removed; row (1,10) survives.
        assertEquals(1, count("t"));
        assertEquals(1, longAt("SELECT cid FROM t"));
    }

    @Test
    public void deleteUsingTargetAliasDoesNotMatchOnNonMatchingKey() {
        engine.execute("CREATE TABLE t (cid INTEGER, ak INTEGER)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20)");
        engine.execute("CREATE TABLE src (cid INTEGER, ak INTEGER)");
        engine.execute("INSERT INTO src VALUES (2, 999)");   // same cid, different ak → no full-key match

        engine.execute("DELETE FROM t wcs USING src d WHERE wcs.cid = d.cid AND wcs.ak = d.ak");

        assertEquals(2, count("t"), "no row matches the full (cid,ak) key, so nothing is deleted");
    }

    // ── a JOIN in the FROM / USING source ──────────────────────────────────────────────────────────────

    @Test
    public void updateFromInnerJoinInSource() {
        engine.execute("CREATE TABLE t (id INTEGER, label VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, '?'), (2, '?'), (3, '?')");
        engine.execute("CREATE TABLE amap (id INTEGER, code INTEGER)");
        engine.execute("INSERT INTO amap VALUES (1, 10), (2, 20)");
        engine.execute("CREATE TABLE bname (code INTEGER, nm VARCHAR)");
        engine.execute("INSERT INTO bname VALUES (10, 'TEN'), (20, 'TWENTY')");

        engine.execute("UPDATE t SET label = b.nm FROM amap a JOIN bname b ON a.code = b.code WHERE t.id = a.id");

        assertEquals("TEN", stringAt("SELECT label FROM t WHERE id = 1"));
        assertEquals("TWENTY", stringAt("SELECT label FROM t WHERE id = 2"));
        assertEquals("?", stringAt("SELECT label FROM t WHERE id = 3"));   // no join row → unchanged
    }

    @Test
    public void updateFromLeftJoinKeepsUnmatchedOuterRows() {
        engine.execute("CREATE TABLE t (id INTEGER, nm VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'x'), (2, 'x'), (3, 'x')");
        engine.execute("CREATE TABLE l (id INTEGER, k INTEGER)");
        engine.execute("INSERT INTO l VALUES (1, 100), (2, 200), (3, 300)");
        engine.execute("CREATE TABLE rmap (k INTEGER, nm VARCHAR)");
        engine.execute("INSERT INTO rmap VALUES (100, 'A')");   // only k=100 has a name

        engine.execute("UPDATE t SET nm = COALESCE(r.nm, 'NONE') FROM l LEFT JOIN rmap r ON l.k = r.k WHERE t.id = l.id");

        // The LEFT join keeps the unmatched l rows (r.nm NULL) → COALESCE gives 'NONE'.
        assertEquals("A", stringAt("SELECT nm FROM t WHERE id = 1"));
        assertEquals("NONE", stringAt("SELECT nm FROM t WHERE id = 2"));
        assertEquals("NONE", stringAt("SELECT nm FROM t WHERE id = 3"));
    }

    @Test
    public void deleteUsingJoinInSource() {
        engine.execute("CREATE TABLE d1 (id INTEGER)");
        engine.execute("INSERT INTO d1 VALUES (1), (2), (3), (4)");
        engine.execute("CREATE TABLE j1 (id INTEGER, gid INTEGER)");
        engine.execute("INSERT INTO j1 VALUES (1, 7), (2, 8), (3, 9)");
        engine.execute("CREATE TABLE j2 (gid INTEGER)");
        engine.execute("INSERT INTO j2 VALUES (7), (9)");   // groups 7,9 → j1 ids 1,3

        engine.execute("DELETE FROM d1 USING j1 JOIN j2 ON j1.gid = j2.gid WHERE d1.id = j1.id");

        assertEquals(2, count("d1"));
        assertEquals(2, longAt("SELECT id FROM d1 ORDER BY id"));   // 2 and 4 remain
    }

    @Test
    public void updateFromTargetAliasWithCollidingColumnNames() {
        engine.execute("CREATE TABLE t (cid INTEGER, ak INTEGER)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute("CREATE TABLE src (cid INTEGER, ak INTEGER)");
        engine.execute("INSERT INTO src VALUES (2, 999), (3, 888)");

        engine.execute("UPDATE t tgt SET tgt.ak = src.ak FROM src WHERE tgt.cid = src.cid");

        assertEquals(10, longAt("SELECT ak FROM t WHERE cid = 1"));    // no source match → unchanged
        assertEquals(999, longAt("SELECT ak FROM t WHERE cid = 2"));
        assertEquals(888, longAt("SELECT ak FROM t WHERE cid = 3"));
    }
}
