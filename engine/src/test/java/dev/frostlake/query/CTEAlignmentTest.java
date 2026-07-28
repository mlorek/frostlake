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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CTE alignment with Snowflake:
 * - Column aliases in CTE definitions
 * - Recursive CTEs
 * - CTEs in UPDATE / DELETE / MERGE
 */
public class CTEAlignmentTest {

    private static final Logger logger = LoggerFactory.getLogger(CTEAlignmentTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    // ── Column aliases in CTE definitions ────────────────────────────────────

    @Test
    public void testCteColumnAliases() {
        ResultSet rs = q("""
            WITH cte(a, b) AS (
                SELECT 1, 'hello'
            )
            SELECT a, b FROM cte
            """);
        assertEquals(1, rs.getRowCount());
        assertEquals("a", rs.getColumns().get(0).getName().toLowerCase());
        assertEquals("b", rs.getColumns().get(1).getName().toLowerCase());
        logger.info("CTE column aliases work: {}", rs.getRows().get(0).getValues());
    }

    @Test
    public void testCteColumnAliasesRenameFromTable() {
        engine.execute("CREATE TABLE nums (x INTEGER, y INTEGER)");
        engine.execute("INSERT INTO nums VALUES (10, 20)");

        ResultSet rs = q("""
            WITH cte(val_a, val_b) AS (
                SELECT x, y FROM nums
            )
            SELECT val_a, val_b FROM cte
            """);
        assertEquals(1, rs.getRowCount());
        assertEquals("val_a", rs.getColumns().get(0).getName().toLowerCase());
        assertEquals(10L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testCteColumnAliasesWithAggregation() {
        engine.execute("CREATE TABLE sales (dept VARCHAR, amount DOUBLE)");
        engine.execute("INSERT INTO sales VALUES ('A', 100), ('A', 200), ('B', 300)");

        ResultSet rs = q("""
            WITH dept_totals(department, total) AS (
                SELECT dept, SUM(amount) FROM sales GROUP BY dept
            )
            SELECT department, total FROM dept_totals ORDER BY department
            """);
        assertEquals(2, rs.getRowCount());
        assertEquals("department", rs.getColumns().get(0).getName().toLowerCase());
        assertEquals("total", rs.getColumns().get(1).getName().toLowerCase());
    }

    // ── Recursive CTEs ────────────────────────────────────────────────────────

    @Test
    public void recursiveKeywordIsOptional() {
        // Snowflake: a CTE that references its own name as a table source recurses with or without
        // the RECURSIVE keyword. Requiring the keyword made a keywordless closure fail with
        // "Table does not exist" — the shape production transitive-relationship loaders use.
        ResultSet rs = q("""
            WITH counter AS (
                SELECT 1 AS n
                UNION ALL
                SELECT n + 1 FROM counter WHERE n < 5
            )
            SELECT n FROM counter ORDER BY n
            """);
        assertEquals(5, rs.getRowCount());
    }

    @Test
    public void keywordlessClosureWithCycleGuardAndSecondCte() {
        engine.execute("CREATE TABLE edges (src VARCHAR, tgt VARCHAR)");
        engine.execute("INSERT INTO edges VALUES ('a1','a2'),('a2','a3'),('a3','a4')");
        ResultSet rs = q("""
            WITH walk AS (
                SELECT src AS parent, tgt AS child, ARRAY_CONSTRUCT(src, tgt) AS vec FROM edges
                UNION ALL
                SELECT w.parent, e.tgt, ARRAY_APPEND(w.vec, e.tgt::VARIANT)
                FROM walk w JOIN edges e ON w.child = e.src
                WHERE NOT ARRAY_CONTAINS(e.tgt::VARIANT, w.vec)
            ), dedup AS (SELECT DISTINCT parent, child FROM walk)
            SELECT parent, child FROM dedup ORDER BY parent, child
            """);
        assertEquals(6, rs.getRowCount());   // full transitive closure of the 3-edge chain
    }

    @Test
    public void aCteWhoseNameMerelyAppearsInTextIsNotRecursive() {
        // Self-reference detection is by the PARSE TREE (a FROM-item named like the CTE) — a CTE
        // named like a column, function, or substring of other text must stay non-recursive.
        engine.execute("CREATE TABLE vals (r INTEGER, rr INTEGER)");
        engine.execute("INSERT INTO vals VALUES (1, 10), (2, 20)");
        ResultSet rs = q("WITH r AS (SELECT r, rr, UPPER('rush') AS s FROM vals) SELECT COUNT(*) FROM r");
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void testRecursiveCteSimpleCounter() {
        ResultSet rs = q("""
            WITH RECURSIVE counter(n) AS (
                SELECT 1
                UNION ALL
                SELECT n + 1 FROM counter WHERE n < 5
            )
            SELECT n FROM counter ORDER BY n
            """);
        assertEquals(5, rs.getRowCount());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(5L, ((Number) rs.getRows().get(4).getValue(0)).longValue());
        logger.info("Recursive CTE counter: {} rows", rs.getRowCount());
    }

    @Test
    public void testRecursiveCteHierarchy() {
        engine.execute("""
            CREATE TABLE employees (
                id INTEGER, name VARCHAR, manager_id INTEGER
            )
            """);
        engine.execute("INSERT INTO employees VALUES (1, 'Alice', NULL)");
        engine.execute("INSERT INTO employees VALUES (2, 'Bob', 1)");
        engine.execute("INSERT INTO employees VALUES (3, 'Charlie', 1)");
        engine.execute("INSERT INTO employees VALUES (4, 'Dave', 2)");

        // All employees under Alice (id=1)
        ResultSet rs = q("""
            WITH RECURSIVE org(id, name, manager_id) AS (
                SELECT id, name, manager_id FROM employees WHERE id = 1
                UNION ALL
                SELECT e.id, e.name, e.manager_id
                FROM employees e
                JOIN org ON e.manager_id = org.id
            )
            SELECT name FROM org ORDER BY id
            """);
        assertEquals(4, rs.getRowCount()); // Alice + Bob + Charlie + Dave
        logger.info("Recursive hierarchy: {} rows", rs.getRowCount());
    }

    @Test
    public void testRecursiveCteFactorial() {
        ResultSet rs = q("""
            WITH RECURSIVE fact(n, f) AS (
                SELECT 1, 1
                UNION ALL
                SELECT n + 1, f * (n + 1) FROM fact WHERE n < 6
            )
            SELECT n, f FROM fact ORDER BY n
            """);
        assertEquals(6, rs.getRowCount());
        // 6! = 720
        assertEquals(720L, ((Number) rs.getRows().get(5).getValue(1)).longValue());
    }

    @Test
    public void testRecursiveCteWithColumnAliases() {
        ResultSet rs = q("""
            WITH RECURSIVE nums(val) AS (
                SELECT 1
                UNION ALL
                SELECT val + 1 FROM nums WHERE val < 3
            )
            SELECT val FROM nums ORDER BY val
            """);
        assertEquals(3, rs.getRowCount());
        assertEquals(3L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
    }

    // ── CTEs in UPDATE ────────────────────────────────────────────────────────

    @Test
    public void testCteInUpdate() {
        engine.execute("CREATE TABLE products (id INTEGER, price DOUBLE, category VARCHAR)");
        engine.execute("INSERT INTO products VALUES (1, 100.0, 'A'), (2, 200.0, 'B'), (3, 150.0, 'A')");

        // Update prices for category A items using a CTE to identify them
        engine.execute("""
            UPDATE products SET price = price * 0.9 WHERE id IN (
            WITH discounted AS (
                SELECT id FROM products WHERE category = 'A'
            )
            SELECT id FROM discounted)
            """);

        ResultSet rs = q("SELECT price FROM products WHERE category = 'A' ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertEquals(90.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
        assertEquals(135.0, ((Number) rs.getRows().get(1).getValue(0)).doubleValue(), 0.01);
        logger.info("CTE in UPDATE: prices updated");
    }

    @Test
    public void testCteInUpdateDirect() {
        engine.execute("CREATE TABLE items (id INTEGER, active BOOLEAN, score INTEGER)");
        engine.execute("INSERT INTO items VALUES (1, true, 10), (2, false, 20), (3, true, 30)");

        engine.execute("""
            UPDATE items SET score = score + 5 WHERE id IN (
            WITH active_items AS (
                SELECT id FROM items WHERE active = true
            )
            SELECT id FROM active_items)
            """);

        ResultSet rs = q("SELECT id, score FROM items ORDER BY id");
        assertEquals(3, rs.getRowCount());
        assertEquals(15L, ((Number) rs.getRows().get(0).getValue(1)).longValue()); // was 10, +5
        assertEquals(20L, ((Number) rs.getRows().get(1).getValue(1)).longValue()); // unchanged
        assertEquals(35L, ((Number) rs.getRows().get(2).getValue(1)).longValue()); // was 30, +5
    }

    // ── CTEs in DELETE ────────────────────────────────────────────────────────

    @Test
    public void testCteInDelete() {
        engine.execute("CREATE TABLE logs (id INTEGER, level VARCHAR, message VARCHAR)");
        engine.execute("INSERT INTO logs VALUES (1, 'INFO', 'msg1'), (2, 'ERROR', 'msg2'), (3, 'INFO', 'msg3')");

        engine.execute("""
            DELETE FROM logs WHERE id IN (
            WITH to_delete AS (
                SELECT id FROM logs WHERE level = 'INFO'
            )
            SELECT id FROM to_delete)
            """);

        ResultSet rs = q("SELECT COUNT(*) FROM logs");
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        ResultSet remaining = q("SELECT level FROM logs");
        assertEquals("ERROR", remaining.getRows().get(0).getValue(0).toString());
        logger.info("CTE in DELETE: INFO logs removed");
    }

    @Test
    public void testCteInDeleteWithAggregation() {
        engine.execute("CREATE TABLE orders (id INTEGER, customer_id INTEGER, total DOUBLE)");
        engine.execute("INSERT INTO orders VALUES (1, 1, 50.0), (2, 1, 30.0), (3, 2, 200.0), (4, 2, 5.0)");

        // Use CTE to identify high-value orders to KEEP; delete the rest using NOT IN
        engine.execute("""
            DELETE FROM orders WHERE id NOT IN (
            WITH high_value AS (
                SELECT id FROM orders WHERE total >= 100.0
            )
            SELECT id FROM high_value)
            """);

        ResultSet rs = q("SELECT COUNT(*) FROM orders");
        // Only id=3 (200.0) has total >= 100; others deleted
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        ResultSet rows = q("SELECT total FROM orders");
        assertEquals(200.0, ((Number) rows.getRows().get(0).getValue(0)).doubleValue(), 0.001);
    }

    // ── CTEs in MERGE ─────────────────────────────────────────────────────────

    @Test
    public void testCteInMerge() {
        engine.execute("CREATE TABLE target (id INTEGER, val VARCHAR)");
        engine.execute("INSERT INTO target VALUES (1, 'old1'), (2, 'old2')");

        engine.execute("""
            MERGE INTO target t
            USING (
            WITH new_data AS (
                SELECT 2 AS id, 'updated2' AS val
                UNION ALL
                SELECT 3 AS id, 'new3' AS val
            )
            SELECT id, val FROM new_data) s ON t.id = s.id
            WHEN MATCHED THEN UPDATE SET val = s.val
            WHEN NOT MATCHED THEN INSERT (id, val) VALUES (s.id, s.val)
            """);

        ResultSet rs = q("SELECT id, val FROM target ORDER BY id");
        assertEquals(3, rs.getRowCount());
        assertEquals("old1",    rs.getRows().get(0).getValue(1).toString());
        assertEquals("updated2", rs.getRows().get(1).getValue(1).toString());
        assertEquals("new3",    rs.getRows().get(2).getValue(1).toString());
        logger.info("CTE in MERGE: {} rows in target", rs.getRowCount());
    }

    // ── Combined: recursive + DML ─────────────────────────────────────────────

    @Test
    public void testRecursiveCteWithInsert() {
        engine.execute("CREATE TABLE series (n INTEGER)");

        engine.execute("""
            INSERT INTO series 
            WITH RECURSIVE s(n) AS (
                SELECT 1
                UNION ALL
                SELECT n + 1 FROM s WHERE n < 5
            )
            SELECT n FROM s
            """);

        ResultSet rs = q("SELECT COUNT(*) FROM series");
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
