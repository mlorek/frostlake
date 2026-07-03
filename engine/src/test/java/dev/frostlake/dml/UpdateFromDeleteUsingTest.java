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
}
