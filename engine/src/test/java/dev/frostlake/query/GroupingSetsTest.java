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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GroupingSetsTest {

    private static final Logger logger = LoggerFactory.getLogger(GroupingSetsTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, amount DOUBLE)");
        engine.execute("INSERT INTO sales VALUES ('East', 'A', 100)");
        engine.execute("INSERT INTO sales VALUES ('East', 'B', 200)");
        engine.execute("INSERT INTO sales VALUES ('West', 'A', 150)");
        engine.execute("INSERT INTO sales VALUES ('West', 'B', 300)");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    private double totalAmount(final ResultSet rs) {
        double sum = 0;
        for (int i = 0; i < rs.getRowCount(); i++) {
            Object v = rs.getRows().get(i).getValue(2);
            if (v != null) sum += ((Number) v).doubleValue();
        }
        return sum;
    }

    // ── ROLLUP ────────────────────────────────────────────────────────────────

    @Test
    public void testRollupTwoColumns() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, product, SUM(amount) FROM sales GROUP BY ROLLUP(region, product) ORDER BY region, product");

        assertNotNull(rs);
        // ROLLUP(region, product) expands to:
        //   (region, product) → 4 detail rows
        //   (region)          → 2 subtotal rows (East=300, West=450)
        //   ()                → 1 grand total row (750)
        assertEquals(7, rs.getRowCount(), "ROLLUP(region,product) should produce 7 rows");

        // Grand total row: both region and product are NULL
        boolean foundGrandTotal = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            Object reg = rs.getRows().get(i).getValue(0);
            Object prod = rs.getRows().get(i).getValue(1);
            Object amt = rs.getRows().get(i).getValue(2);
            if (reg == null && prod == null) {
                foundGrandTotal = true;
                assertEquals(750.0, ((Number) amt).doubleValue(), 0.01, "Grand total should be 750");
            }
        }
        assertTrue(foundGrandTotal, "Should have grand total row");
        logger.info("ROLLUP(region,product): {} rows", rs.getRowCount());
    }

    @Test
    public void testRollupOneColumn() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, SUM(amount) FROM sales GROUP BY ROLLUP(region)");
        // ROLLUP(region) → (region), () — 2+1=3 rows
        assertEquals(3, rs.getRowCount(), "ROLLUP(region) should produce 3 rows");
    }

    @Test
    public void testRollupSubtotalsCorrect() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, product, SUM(amount) as total FROM sales GROUP BY ROLLUP(region, product)");

        // Find East subtotal: region=East, product=NULL
        double eastTotal = 0;
        for (int i = 0; i < rs.getRowCount(); i++) {
            Object reg = rs.getRows().get(i).getValue(0);
            Object prod = rs.getRows().get(i).getValue(1);
            if ("East".equals(reg) && prod == null) {
                eastTotal = ((Number) rs.getRows().get(i).getValue(2)).doubleValue();
            }
        }
        assertEquals(300.0, eastTotal, 0.01, "East subtotal should be 300");
    }

    // ── CUBE ──────────────────────────────────────────────────────────────────

    @Test
    public void testCubeTwoColumns() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, product, SUM(amount) FROM sales GROUP BY CUBE(region, product)");

        assertNotNull(rs);
        // CUBE(region, product) → (region,product), (region), (product), ()
        // = 4 + 2 + 2 + 1 = 9 rows
        assertEquals(9, rs.getRowCount(), "CUBE(region,product) should produce 9 rows");

        // Product-only subtotals: region=NULL, product=A → 100+150=250
        boolean foundProductA = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            Object reg = rs.getRows().get(i).getValue(0);
            Object prod = rs.getRows().get(i).getValue(1);
            Object amt = rs.getRows().get(i).getValue(2);
            if (reg == null && "A".equals(prod)) {
                foundProductA = true;
                assertEquals(250.0, ((Number) amt).doubleValue(), 0.01, "Product A total: 100+150=250");
            }
        }
        assertTrue(foundProductA, "Should have product-only subtotal for A");
        logger.info("CUBE(region,product): {} rows", rs.getRowCount());
    }

    @Test
    public void testCubeGrandTotal() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, product, SUM(amount) FROM sales GROUP BY CUBE(region, product)");
        boolean foundGrandTotal = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            Object reg = rs.getRows().get(i).getValue(0);
            Object prod = rs.getRows().get(i).getValue(1);
            Object amt = rs.getRows().get(i).getValue(2);
            if (reg == null && prod == null) {
                foundGrandTotal = true;
                assertEquals(750.0, ((Number) amt).doubleValue(), 0.01);
            }
        }
        assertTrue(foundGrandTotal);
    }

    // ── GROUPING SETS ─────────────────────────────────────────────────────────

    @Test
    public void testGroupingSetsExplicit() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, product, SUM(amount) FROM sales " +
            "GROUP BY GROUPING SETS((region, product), (region), ())");

        // (region,product)=4, (region)=2, ()=1 → 7 rows (same as ROLLUP)
        assertEquals(7, rs.getRowCount(), "GROUPING SETS should produce 7 rows");
    }

    @Test
    public void testGroupingSetsProductOnly() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, product, SUM(amount) FROM sales " +
            "GROUP BY GROUPING SETS((product))");

        // Only product-level grouping: A=250, B=500
        assertEquals(2, rs.getRowCount());
        double totalA = 0, totalB = 0;
        for (int i = 0; i < rs.getRowCount(); i++) {
            String prod = rs.getRows().get(i).getValue(1) != null
                ? rs.getRows().get(i).getValue(1).toString() : null;
            double amt = ((Number) rs.getRows().get(i).getValue(2)).doubleValue();
            if ("A".equals(prod)) totalA = amt;
            if ("B".equals(prod)) totalB = amt;
        }
        assertEquals(250.0, totalA, 0.01);
        assertEquals(500.0, totalB, 0.01);
    }

    @Test
    public void testGroupingSetsGrandTotalOnly() {
        ResultSet rs = engine.executeQuery(
            "SELECT SUM(amount) FROM sales GROUP BY GROUPING SETS(())");
        assertEquals(1, rs.getRowCount(), "Empty grouping set = grand total");
        assertEquals(750.0, ((Number) rs.getRows().get(0).getValue(0)).doubleValue(), 0.01);
    }

    @Test
    public void testGroupingSetsTwoDistinctSets() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, product, SUM(amount) FROM sales " +
            "GROUP BY GROUPING SETS((region), (product))");
        // (region): East=300, West=450 → 2 rows
        // (product): A=250, B=500 → 2 rows
        // Total: 4 rows
        assertEquals(4, rs.getRowCount());
    }

    // ── GROUPING() function ───────────────────────────────────────────────────

    @Test
    public void testGroupingFunction() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, SUM(amount), GROUPING(region) FROM sales GROUP BY ROLLUP(region)");

        // Detail rows: GROUPING(region)=0; grand total: GROUPING(region)=1
        int grandTotalRows = 0;
        for (int i = 0; i < rs.getRowCount(); i++) {
            Object reg = rs.getRows().get(i).getValue(0);
            long grp = ((Number) rs.getRows().get(i).getValue(2)).longValue();
            if (reg == null) {
                grandTotalRows++;
                assertEquals(1L, grp, "Grand total row: GROUPING()=1");
            } else {
                assertEquals(0L, grp, "Detail row: GROUPING()=0");
            }
        }
        assertEquals(1, grandTotalRows, "Exactly 1 grand total row");
    }

    // ── Aggregation correctness ───────────────────────────────────────────────

    @Test
    public void testRollupCountAggregation() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, COUNT(*) FROM sales GROUP BY ROLLUP(region)");
        // East=2, West=2, grand=4
        for (int i = 0; i < rs.getRowCount(); i++) {
            Object reg = rs.getRows().get(i).getValue(0);
            long cnt = ((Number) rs.getRows().get(i).getValue(1)).longValue();
            if (reg == null) assertEquals(4L, cnt, "Grand total count=4");
            else assertEquals(2L, cnt, "Per-region count=2");
        }
    }

    @Test
    public void testCubeWithHaving() {
        ResultSet rs = engine.executeQuery(
            "SELECT region, SUM(amount) FROM sales " +
            "GROUP BY CUBE(region) HAVING SUM(amount) > 400");
        // West=450, grand_total=750 both > 400; East=300 filtered out
        assertTrue(rs.getRowCount() >= 1);
        for (int i = 0; i < rs.getRowCount(); i++) {
            double amt = ((Number) rs.getRows().get(i).getValue(1)).doubleValue();
            assertTrue(amt > 400, "HAVING should filter out East=300");
        }
    }
}
