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
 * Oracle legacy {@code (+)} outer-join operator (Snowflake compatibility): {@code a.x = b.y(+)} in the WHERE
 * of a comma join means {@code a LEFT OUTER JOIN b ON a.x = b.y} — the table carrying {@code (+)} is the
 * null-supplying side. Conjuncts with {@code (+)} form the ON condition; the rest stay as a residual WHERE.
 */
public class OracleOuterJoinTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE emp (id INTEGER, name VARCHAR, dept_id INTEGER)");
        engine.execute("INSERT INTO emp VALUES (1, 'Alice', 10), (2, 'Bob', 20), (3, 'Carol', 99)");
        engine.execute("CREATE TABLE dept (id INTEGER, dname VARCHAR)");
        engine.execute("INSERT INTO dept VALUES (10, 'Eng'), (20, 'Sales')");
    }

    @Test
    public void plusOnRightKeepsAllLeftRows() {
        // e.dept_id = d.id(+)  ==  emp LEFT JOIN dept — Carol (dept 99) is kept with a NULL dept.
        final ResultSet rs = engine.executeQuery(
            "SELECT e.name, d.dname FROM emp e, dept d WHERE e.dept_id = d.id(+) ORDER BY e.name");
        assertEquals(3, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
        assertEquals("Eng", rs.getRows().get(0).getValue(1).toString());
        assertEquals("Carol", rs.getRows().get(2).getValue(0).toString());
        assertNull(rs.getRows().get(2).getValue(1));
    }

    @Test
    public void plusMatchesEquivalentLeftJoin() {
        final ResultSet plus = engine.executeQuery(
            "SELECT e.name, d.dname FROM emp e, dept d WHERE e.dept_id = d.id(+) ORDER BY e.name");
        final ResultSet lj = engine.executeQuery(
            "SELECT e.name, d.dname FROM emp e LEFT JOIN dept d ON e.dept_id = d.id ORDER BY e.name");
        assertEquals(lj.getRowCount(), plus.getRowCount());
        for (int i = 0; i < plus.getRowCount(); i++) {
            assertEquals(lj.getRows().get(i).getValues(), plus.getRows().get(i).getValues());
        }
    }

    @Test
    public void plusOnLeftKeepsAllRightRows() {
        // e.dept_id(+) = d.id  keeps all dept rows; both depts have employees, so 2 rows.
        final ResultSet rs = engine.executeQuery(
            "SELECT e.name, d.dname FROM emp e, dept d WHERE e.dept_id(+) = d.id ORDER BY d.dname");
        assertEquals(2, rs.getRowCount());
    }

    @Test
    public void residualPredicateIsAppliedAfterTheOuterJoin() {
        // The non-(+) conjunct e.name <> 'Bob' is a residual WHERE (Bob filtered); Carol's null row survives.
        final ResultSet rs = engine.executeQuery(
            "SELECT e.name, d.dname FROM emp e, dept d WHERE e.dept_id = d.id(+) AND e.name <> 'Bob' ORDER BY e.name");
        assertEquals(2, rs.getRowCount());
        assertEquals("Alice", rs.getRows().get(0).getValue(0).toString());
        assertEquals("Carol", rs.getRows().get(1).getValue(0).toString());
        assertNull(rs.getRows().get(1).getValue(1));
    }

    @Test
    public void twoKeyPlusJoin() {
        engine.execute("CREATE TABLE t (c INTEGER, k INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 10, 'x'), (1, 20, 'y')");
        engine.execute("CREATE TABLE m (c INTEGER, k INTEGER, w VARCHAR)");
        engine.execute("INSERT INTO m VALUES (1, 10, 'M')");
        final ResultSet rs = engine.executeQuery(
            "SELECT t.v, m.w FROM t, m WHERE t.c = m.c(+) AND t.k = m.k(+) ORDER BY t.k");
        assertEquals(2, rs.getRowCount());
        assertEquals("M", rs.getRows().get(0).getValue(1).toString());   // k=10 matches
        assertNull(rs.getRows().get(1).getValue(1));                     // k=20 no match
    }

    @Test
    public void plusInUpdateFromUpdatesUnmatchedTargetRows() {
        // UPDATE dest FROM mac WHERE dest.k = mac.k(+): a target LEFT JOIN source — every target row is
        // updated, with the source columns NULL when it has no match (so row 3 becomes 'Deleted').
        engine.execute("CREATE TABLE dest (k INTEGER, state VARCHAR)");
        engine.execute("INSERT INTO dest VALUES (1, '?'), (2, '?'), (3, '?')");
        engine.execute("CREATE TABLE mac (k INTEGER, cluster VARCHAR)");
        engine.execute("INSERT INTO mac VALUES (1, 'C1'), (2, 'C2')");

        engine.execute("UPDATE dest SET state = IFF(mac.cluster IS NULL, 'Deleted', 'Current') "
            + "FROM mac WHERE dest.k = mac.k(+)");

        final ResultSet rs = engine.executeQuery("SELECT k, state FROM dest ORDER BY k");
        assertEquals("Current", rs.getRows().get(0).getValue(1).toString());
        assertEquals("Current", rs.getRows().get(1).getValue(1).toString());
        assertEquals("Deleted", rs.getRows().get(2).getValue(1).toString());   // unmatched → updated with NULL source
    }
}
