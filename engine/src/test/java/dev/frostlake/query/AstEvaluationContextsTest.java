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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Expression evaluation across the contexts that were re-routed through the AST evaluator
 * (multi-table JOIN conditions, HAVING by-alias / multi-condition, QUALIFY multi-condition,
 * correlated subqueries with AND, BETWEEN/LIKE followed by a top-level AND). These exercise the
 * alias-aware multi-table resolution, the HAVING/QUALIFY result context, and the boolean-tier
 * grammar — the parts most likely to regress if expression evaluation changes.
 */
public class AstEvaluationContextsTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE emp (id INT, name VARCHAR, dept VARCHAR, salary INT)");
        engine.execute("INSERT INTO emp VALUES (1,'Alice','Eng',100),(2,'Bob','Eng',200),"
            + "(3,'Carol','Sales',150),(4,'Dave','Sales',50),(5,'Eve','HR',300)");
        engine.execute("CREATE TABLE dept (dept VARCHAR, budget INT)");
        engine.execute("INSERT INTO dept VALUES ('Eng',1000),('Sales',500),('HR',300)");
        engine.execute("CREATE TABLE j1 (id INT, k INT)");
        engine.execute("INSERT INTO j1 VALUES (1,10),(2,20)");
        engine.execute("CREATE TABLE j2 (id INT, k INT)");
        engine.execute("INSERT INTO j2 VALUES (1,10),(2,99)");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static long n(final Object v) {
        return ((Number) v).longValue();
    }

    // --- JOIN ON: multi-condition AND over same-named columns (a.id=b.id AND a.k=b.k) ---

    @Test
    public void testJoinOnMultipleSameNamedColumns() {
        ResultSet rs = engine.executeQuery(
            "SELECT j1.id FROM j1 JOIN j2 ON j1.id = j2.id AND j1.k = j2.k");
        // (1,10)=(1,10) matches; (2,20) vs (2,99) does not — so exactly one row, id=1.
        assertEquals(1, rs.getRows().size());
        assertEquals(1L, n(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void testJoinArithmeticProjectionWithAliases() {
        ResultSet rs = engine.executeQuery(
            "SELECT e.salary + d.budget FROM emp e JOIN dept d ON e.dept = d.dept WHERE e.id = 1");
        assertEquals(1, rs.getRows().size());
        assertEquals(1100L, n(rs.getRows().get(0).getValue(0))); // Alice 100 + Eng 1000
    }

    // --- HAVING: aggregate referenced by alias, and multi-condition AND ---

    @Test
    public void testHavingByCountAlias() {
        ResultSet rs = engine.executeQuery(
            "SELECT dept, COUNT(*) AS cnt FROM emp GROUP BY dept HAVING cnt >= 2");
        Set<String> depts = new HashSet<>();
        for (final Row r : rs.getRows()) {
            depts.add(String.valueOf(r.getValue(0)));
        }
        assertEquals(new HashSet<>(Arrays.asList("Eng", "Sales")), depts); // HR has 1, excluded
    }

    @Test
    public void testHavingMultiConditionByAliases() {
        ResultSet rs = engine.executeQuery(
            "SELECT dept, COUNT(*) AS cnt, SUM(salary) AS total FROM emp GROUP BY dept "
            + "HAVING cnt >= 2 AND total > 250");
        // Eng: cnt=2,total=300 (passes); Sales: cnt=2,total=200 (fails); HR: cnt=1 (fails) -> Eng only
        assertEquals(1, rs.getRows().size());
        assertEquals("Eng", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    // --- QUALIFY: multi-condition over a window-function alias ---

    @Test
    public void testQualifyMultiCondition() {
        ResultSet rs = engine.executeQuery(
            "SELECT name, ROW_NUMBER() OVER (ORDER BY salary DESC) AS rn FROM emp "
            + "QUALIFY rn >= 2 AND rn <= 3");
        // salary desc: Eve(1),Bob(2),Carol(3),Alice(4),Dave(5); rn in [2,3] -> Bob, Carol
        Set<String> names = new HashSet<>();
        for (final Row r : rs.getRows()) {
            names.add(String.valueOf(r.getValue(0)));
        }
        assertEquals(new HashSet<>(Arrays.asList("Bob", "Carol")), names);
    }

    // --- Correlated scalar subquery with AND in its WHERE ---

    @Test
    public void testCorrelatedScalarSubqueryWithAnd() {
        ResultSet rs = engine.executeQuery(
            "SELECT (SELECT COUNT(*) FROM emp e2 WHERE e2.dept = dept.dept AND e2.salary >= 100) AS c "
            + "FROM dept ORDER BY budget DESC");
        // dept by budget desc: Eng(1000), Sales(500), HR(300).
        // count of emp in that dept with salary>=100: Eng=Alice,Bob=2; Sales=Carol=1; HR=Eve=1.
        assertEquals(2L, n(rs.getRows().get(0).getValue(0))); // Eng
        assertEquals(1L, n(rs.getRows().get(1).getValue(0))); // Sales
    }

    // --- BETWEEN / LIKE followed by a top-level AND, over columns ---

    @Test
    public void testBetweenThenAndOnColumns() {
        ResultSet rs = engine.executeQuery(
            "SELECT name FROM emp WHERE salary BETWEEN 100 AND 250 AND dept = 'Eng'");
        Set<String> names = new HashSet<>();
        for (final Row r : rs.getRows()) {
            names.add(String.valueOf(r.getValue(0)));
        }
        assertEquals(new HashSet<>(Arrays.asList("Alice", "Bob")), names);
    }

    @Test
    public void testLikeThenAndOnColumns() {
        ResultSet rs = engine.executeQuery(
            "SELECT name FROM emp WHERE name LIKE 'A%' AND salary < 200");
        assertEquals(1, rs.getRows().size());
        assertEquals("Alice", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    // --- DML: expression evaluation in UPDATE SET (scalar subquery) and DELETE WHERE (EXISTS) ---

    @Test
    public void testUpdateSetWithScalarSubquery() {
        engine.execute("CREATE TABLE upd_t (id INT, v INT)");
        engine.execute("INSERT INTO upd_t VALUES (1, 10), (2, 20), (3, 30)");
        engine.execute("UPDATE upd_t SET v = (SELECT MAX(v) FROM upd_t) WHERE id = 1");
        ResultSet rs = engine.executeQuery("SELECT v FROM upd_t WHERE id = 1");
        assertEquals(30L, n(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void testDeleteWithCorrelatedNotExists() {
        engine.execute("CREATE TABLE del_parent (id INT)");
        engine.execute("INSERT INTO del_parent VALUES (1), (2), (3)");
        engine.execute("CREATE TABLE del_child (pid INT)");
        engine.execute("INSERT INTO del_child VALUES (1), (3)");
        engine.execute("DELETE FROM del_parent WHERE NOT EXISTS "
            + "(SELECT 1 FROM del_child WHERE del_child.pid = del_parent.id)");
        ResultSet rs = engine.executeQuery("SELECT id FROM del_parent ORDER BY id");
        Set<String> ids = new HashSet<>();
        for (final Row r : rs.getRows()) {
            ids.add(String.valueOf(n(r.getValue(0))));
        }
        assertEquals(new HashSet<>(Arrays.asList("1", "3")), ids); // id=2 (no child) deleted
    }

    // --- Array element access (ArrayAccessExpression) ---

    @Test
    public void testArrayElementAccess() {
        ResultSet rs = engine.executeQuery("SELECT ARRAY_CONSTRUCT(10, 20, 30)[1]");
        assertEquals(20L, n(rs.getRows().get(0).getValue(0)));
    }
}
