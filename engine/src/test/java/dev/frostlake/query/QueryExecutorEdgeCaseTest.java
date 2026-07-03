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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Previously-untested query-executor edge cases whose Snowflake-correct behavior the engine already
 * implements: multi-row scalar-subquery cardinality error, {@code > ALL} over a NULL-containing set,
 * the outer-join ON-vs-WHERE distinction, {@code GROUP BY ALL}, NULL grouping, set-operation NULL
 * de-duplication, empty-table aggregate NULLs, and {@code GROUP BY} / {@code ORDER BY} positional
 * (ordinal) references. (A companion set of edge cases that currently diverge from Snowflake is
 * tracked separately as engine defects.)
 */
public class QueryExecutorEdgeCaseTest extends BaseDatabaseTest {

    private ResultSet run(final String sql) {
        return engine.executeQuery(sql);
    }

    // A scalar subquery that returns more than one row raises an error (Snowflake single-row constraint).
    @Test
    public void scalarSubqueryMultiRowErrors() {
        engine.execute("CREATE TABLE emp (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO emp VALUES (1, 'Alice')");
        engine.execute("CREATE TABLE departments (name VARCHAR)");
        engine.execute("INSERT INTO departments VALUES ('Eng'), ('Sales'), ('HR')");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                run("SELECT name, (SELECT name FROM departments) FROM emp WHERE id = 1").getRows();
            }
        });
    }

    // > ALL over a set containing a NULL yields UNKNOWN, so no rows qualify.
    @Test
    public void greaterThanAllWithNullExcludes() {
        engine.execute("CREATE TABLE emp (sal INTEGER)");
        engine.execute("INSERT INTO emp VALUES (100), (200)");
        engine.execute("CREATE TABLE t (s INTEGER)");
        engine.execute("INSERT INTO t VALUES (150), (NULL)");
        final ResultSet rs = run("SELECT sal FROM emp WHERE sal > ALL (SELECT s FROM t)");
        assertEquals(0, rs.getRowCount());
    }

    // For an OUTER join, a predicate in ON keeps unmatched left rows (unlike the same predicate in WHERE).
    @Test
    public void leftJoinOnPredicateKeepsUnmatched() {
        engine.execute("CREATE TABLE emp (id INTEGER, dept VARCHAR)");
        engine.execute("INSERT INTO emp VALUES (1, 'X'), (2, 'Y')");
        engine.execute("CREATE TABLE d (dept VARCHAR, region VARCHAR)");
        engine.execute("INSERT INTO d VALUES ('X', 'US')");
        final ResultSet rs = run(
            "SELECT e.id FROM emp e LEFT JOIN d ON e.dept = d.dept AND d.region = 'EU'");
        assertEquals(2, rs.getRowCount());
    }

    // GROUP BY ALL groups by every non-aggregated SELECT column.
    @Test
    public void groupByAll() {
        engine.execute("CREATE TABLE t (dept VARCHAR, region VARCHAR)");
        engine.execute("INSERT INTO t VALUES ('A', 'X'), ('A', 'X'), ('B', 'Y')");
        final ResultSet rs = run("SELECT dept, region, COUNT(*) FROM t GROUP BY ALL");
        assertEquals(2, rs.getRowCount());
    }

    // GROUP BY collapses all NULL keys into a single group.
    @Test
    public void groupByNullsFormOneGroup() {
        engine.execute("CREATE TABLE t (k INTEGER)");
        engine.execute("INSERT INTO t VALUES (NULL), (NULL), (1)");
        final ResultSet rs = run("SELECT k, COUNT(*) FROM t GROUP BY k");
        assertEquals(2, rs.getRowCount());
    }

    // UNION treats NULLs as equal when de-duplicating.
    @Test
    public void unionDedupsNulls() {
        engine.execute("CREATE TABLE t1 (v INTEGER)");
        engine.execute("INSERT INTO t1 VALUES (NULL), (1)");
        engine.execute("CREATE TABLE t2 (v INTEGER)");
        engine.execute("INSERT INTO t2 VALUES (NULL), (2)");
        final ResultSet rs = run("SELECT v FROM t1 UNION SELECT v FROM t2");
        assertEquals(3, rs.getRowCount());
    }

    // Aggregates over an empty table: COUNT is 0, but SUM/MIN/MAX are NULL (not zero).
    @Test
    public void emptyTableAggregate() {
        engine.execute("CREATE TABLE t (v INTEGER)");
        final ResultSet rs = run("SELECT COUNT(*), SUM(v), MIN(v), MAX(v) FROM t");
        assertEquals(1, rs.getRowCount());
        assertEquals(0L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertNull(rs.getRows().get(0).getValue(1));
        assertNull(rs.getRows().get(0).getValue(2));
        assertNull(rs.getRows().get(0).getValue(3));
    }

    // GROUP BY <n> is a 1-based positional reference to the N-th SELECT expression.
    @Test
    public void groupByOrdinal() {
        engine.execute("CREATE TABLE t (dept VARCHAR, amt INTEGER)");
        engine.execute("INSERT INTO t VALUES ('A', 10), ('A', 20), ('B', 30)");
        final ResultSet rs = run("SELECT dept, SUM(amt) FROM t GROUP BY 1");
        assertEquals(2, rs.getRowCount());
    }

    // ORDER BY <n> is a 1-based positional reference to the N-th SELECT column.
    @Test
    public void orderByOrdinal() {
        engine.execute("CREATE TABLE t (name VARCHAR, sal INTEGER)");
        engine.execute("INSERT INTO t VALUES ('A', 100), ('B', 300), ('C', 200)");
        final ResultSet rs = run("SELECT name, sal FROM t ORDER BY 2 DESC");
        assertEquals(3, rs.getRowCount());
        assertEquals("B", rs.getRows().get(0).getValue(0));
    }

    // A self-join (same table under two aliases) must resolve each alias to its own side of the
    // join: m.name is the matched manager's name, not the employee's own name.
    @Test
    public void selfJoinResolvesAliasesIndependently() {
        engine.execute("CREATE TABLE emp (id INTEGER, name VARCHAR, mgr_id INTEGER)");
        engine.execute("INSERT INTO emp VALUES (1, 'CEO', NULL), (2, 'Alice', 1), (3, 'Bob', 1)");
        final ResultSet rs = run("SELECT e.name, m.name FROM emp e JOIN emp m ON e.mgr_id = m.id");
        assertEquals(2, rs.getRowCount());
        assertEquals("CEO", rs.getRows().get(0).getValue(1));
        assertEquals("CEO", rs.getRows().get(1).getValue(1));
    }
}
