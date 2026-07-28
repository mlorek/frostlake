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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A non-aggregate SELECT must accept a SELECT alias, an arbitrary expression, or a function as an ORDER BY
 * key (Snowflake allows any in-scope expression), not only a bare column name.
 */
public class OrderByExpressionTest extends BaseDatabaseTest {

    private long a(final ResultSet rs, final int row) {
        return ((Number) rs.getRows().get(row).getValue(0)).longValue();
    }

    @Test
    public void orderByASelectAlias() {
        engine.execute("CREATE TABLE ob (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO ob VALUES (3, 1), (1, 10), (2, 3)");
        final ResultSet rs = engine.executeQuery("SELECT a AS z FROM ob ORDER BY z");
        assertEquals(1L, a(rs, 0));
        assertEquals(2L, a(rs, 1));
        assertEquals(3L, a(rs, 2));
    }

    @Test
    public void orderByAnArbitraryExpression() {
        engine.execute("CREATE TABLE ob (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO ob VALUES (3, 1), (1, 10), (2, 3)");
        // a+b = 4, 11, 5  ->  ascending 4,5,11  ->  first-column a = 3, 2, 1
        final ResultSet rs = engine.executeQuery("SELECT a, b FROM ob ORDER BY a+b");
        assertEquals(3L, a(rs, 0));
        assertEquals(2L, a(rs, 1));
        assertEquals(1L, a(rs, 2));
    }

    @Test
    public void orderByAParenthesizedExpressionDesc() {
        engine.execute("CREATE TABLE ob (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO ob VALUES (3, 1), (1, 10), (2, 3)");
        // a*2 = 6, 2, 4  ->  DESC 6,4,2  ->  a = 3, 2, 1
        final ResultSet rs = engine.executeQuery("SELECT a FROM ob ORDER BY (a*2) DESC");
        assertEquals(3L, a(rs, 0));
        assertEquals(2L, a(rs, 1));
        assertEquals(1L, a(rs, 2));
    }

    @Test
    public void orderByAFunction() {
        engine.execute("CREATE TABLE ob2 (a INTEGER)");
        engine.execute("INSERT INTO ob2 VALUES (-3), (1), (-2)");
        // ABS(a) = 3, 1, 2  ->  ascending 1,2,3  ->  a = 1, -2, -3
        final ResultSet rs = engine.executeQuery("SELECT a FROM ob2 ORDER BY ABS(a)");
        assertEquals(1L, a(rs, 0));
        assertEquals(-2L, a(rs, 1));
        assertEquals(-3L, a(rs, 2));
    }

    // ---- ORDER BY after GROUP BY on a key NOT in the SELECT list ----
    // Snowflake allows ordering by a grouped column or an aggregate that isn't selected; frostlake threw
    // "ORDER BY expression not found in SELECT list". The key is now computed over each row's group.

    @Test
    public void orderByGroupedColumnNotInSelect() {
        engine.execute("CREATE TABLE gob (grp INTEGER, k INTEGER)");
        engine.execute("INSERT INTO gob VALUES (1, 30), (2, 20), (3, 40), (4, 10)");
        // One group per (grp,k); order by k (not selected): 10→4, 20→2, 30→1, 40→3.
        final ResultSet rs = engine.executeQuery("SELECT grp FROM gob GROUP BY grp, k ORDER BY k");
        assertEquals(4L, a(rs, 0));
        assertEquals(2L, a(rs, 1));
        assertEquals(1L, a(rs, 2));
        assertEquals(3L, a(rs, 3));
    }

    @Test
    public void orderByAggregateNotInSelect() {
        engine.execute("CREATE TABLE aob (grp INTEGER, k INTEGER)");
        engine.execute("INSERT INTO aob VALUES (1, 5), (1, 30), (2, 20), (3, 40)");
        // MAX(k) per grp: 1→30, 2→20, 3→40. ORDER BY MAX(k) asc → 20(grp2), 30(grp1), 40(grp3).
        final ResultSet rs = engine.executeQuery("SELECT grp FROM aob GROUP BY grp ORDER BY MAX(k)");
        assertEquals(2L, a(rs, 0));
        assertEquals(1L, a(rs, 1));
        assertEquals(3L, a(rs, 2));
    }

    @Test
    public void orderByAggregateNotInSelectDescending() {
        engine.execute("CREATE TABLE dob (grp INTEGER, k INTEGER)");
        engine.execute("INSERT INTO dob VALUES (1, 5), (1, 30), (2, 20), (3, 40)");
        final ResultSet rs = engine.executeQuery("SELECT grp FROM dob GROUP BY grp ORDER BY MAX(k) DESC");
        assertEquals(3L, a(rs, 0));
        assertEquals(1L, a(rs, 1));
        assertEquals(2L, a(rs, 2));
    }

    @Test
    public void orderByGroupedColumnNotInSelectWithHaving() {
        engine.execute("CREATE TABLE hob (grp INTEGER, k INTEGER)");
        engine.execute("INSERT INTO hob VALUES (1, 30), (2, 20), (3, 40), (4, 10)");
        final ResultSet rs = engine.executeQuery(
            "SELECT grp FROM hob GROUP BY grp, k HAVING COUNT(*) >= 1 ORDER BY k");
        assertEquals(4L, a(rs, 0));
        assertEquals(1L, a(rs, 2));
    }

    // ---- ORDER BY a FROM column NOT in the SELECT of a WINDOW-function query ----
    // A window query projects away non-SELECT columns before ORDER BY. The unselected ORDER BY key is now
    // precomputed during the window projection (from the still-available FROM columns) and used to sort.

    @Test
    public void orderByWindowQueryColumnNotInSelect() {
        engine.execute("CREATE TABLE wob (a INTEGER, b INTEGER, rid INTEGER)");
        engine.execute("INSERT INTO wob VALUES (1, 5, 103), (2, 9, 101), (3, 3, 102)");
        // SELECT projects a + a window column; ORDER BY rid (not selected). rid order: 101(a2),102(a3),103(a1).
        final ResultSet rs = engine.executeQuery(
            "SELECT a, ROW_NUMBER() OVER (ORDER BY b) rn FROM wob ORDER BY rid");
        assertEquals(2L, a(rs, 0));
        assertEquals(3L, a(rs, 1));
        assertEquals(1L, a(rs, 2));
    }

    @Test
    public void orderByWindowWithQualifyColumnNotInSelect() {
        engine.execute("CREATE TABLE qob (cid INTEGER, fid INTEGER, rid INTEGER)");
        engine.execute("INSERT INTO qob VALUES (1, 50, 101), (1, 10, 104), (2, 30, 103)");
        // The loader shape: window in SELECT + QUALIFY (keeps latest per cid) + ORDER BY an unselected col.
        final ResultSet rs = engine.executeQuery(
            "SELECT cid FROM qob QUALIFY ROW_NUMBER() OVER (PARTITION BY cid ORDER BY fid DESC) = 1 ORDER BY rid");
        // Kept: cid1 fid50 (rid101), cid2 fid30 (rid103). ORDER BY rid → 101(cid1), 103(cid2).
        assertEquals(2, rs.getRowCount());
        assertEquals(1L, a(rs, 0));
        assertEquals(2L, a(rs, 1));
    }
}
