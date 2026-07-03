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
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Aggregates whose argument is a table-qualified column ({@code SUM(s.qty)}), an arithmetic expression
 * ({@code SUM(qty * 2)}), or a predicate ({@code COUNT_IF(v > 2)}) must resolve that argument rather than
 * silently collapsing to NULL (SUM/AVG/MIN/MAX/COUNT) or counting column 0 (COUNT_IF and the other
 * generic accumulators). Previously the aggregate-argument path forced every argument through a bare
 * column-index lookup, which threw "Column not found" (swallowed to NULL) or fell back to column 0.
 */
public class AggregateArgumentResolutionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sales (id NUMBER, quantity NUMBER, price NUMBER)");
        engine.execute("INSERT INTO sales VALUES (1, 10, 100), (2, 20, 200), (3, 30, 300)");
    }

    private double num(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    // ── whole-relation (implicit) aggregation over qualified columns / expressions ──

    @Test
    public void aggregateOverAliasQualifiedColumn() {
        assertEquals(60.0, num("SELECT SUM(s.quantity) FROM sales s"), 1e-9);
        assertEquals(3.0, num("SELECT COUNT(s.quantity) FROM sales s"), 1e-9);
        assertEquals(200.0, num("SELECT AVG(s.price) FROM sales s"), 1e-9);
        assertEquals(100.0, num("SELECT MIN(s.price) FROM sales s"), 1e-9);
        assertEquals(300.0, num("SELECT MAX(s.price) FROM sales s"), 1e-9);
    }

    @Test
    public void aggregateOverArithmeticExpression() {
        assertEquals(120.0, num("SELECT SUM(quantity * 2) FROM sales"), 1e-9);
        assertEquals(660.0, num("SELECT SUM(quantity + price) FROM sales"), 1e-9);
        assertEquals(220.0, num("SELECT AVG(quantity + price) FROM sales"), 1e-9);
        assertEquals(330.0, num("SELECT MAX(quantity + price) FROM sales"), 1e-9);
    }

    @Test
    public void bareColumnAggregatesStillWork() {
        // Fast path preserved — no regression on the common case.
        assertEquals(60.0, num("SELECT SUM(quantity) FROM sales"), 1e-9);
        assertEquals(3.0, num("SELECT COUNT(*) FROM sales"), 1e-9);
        assertEquals(200.0, num("SELECT AVG(price) FROM sales"), 1e-9);
    }

    // ── COUNT_IF honours its predicate ──

    @Test
    public void countIfAppliesPredicate() {
        engine.execute("CREATE TABLE vals (v NUMBER)");
        engine.execute("INSERT INTO vals VALUES (1), (2), (3), (4), (5)");
        assertEquals(3.0, num("SELECT COUNT_IF(v > 2) FROM vals"), 1e-9);
        assertEquals(0.0, num("SELECT COUNT_IF(FALSE) FROM vals"), 1e-9);
        assertEquals(5.0, num("SELECT COUNT_IF(v >= 1) FROM vals"), 1e-9);
        assertEquals(4.0, num("SELECT COUNT_IF(v <> 3) FROM vals"), 1e-9);
    }

    // ── GROUP BY with a qualified aggregate argument ──

    @Test
    public void groupedAggregateOverQualifiedColumn() {
        engine.execute("CREATE TABLE items (category VARCHAR, qty NUMBER)");
        engine.execute("INSERT INTO items VALUES ('a', 10), ('a', 20), ('b', 30)");
        final ResultSet rs = engine.executeQuery(
                "SELECT s.category, SUM(s.qty) FROM items s GROUP BY s.category ORDER BY s.category");
        final List<Row> rows = rs.getRows();
        assertEquals(2, rows.size());
        final Map<String, Double> byCat = new HashMap<>();
        for (final Row r : rows) {
            byCat.put(String.valueOf(r.getValue(0)), ((Number) r.getValue(1)).doubleValue());
        }
        assertEquals(30.0, byCat.get("a"), 1e-9);
        assertEquals(30.0, byCat.get("b"), 1e-9);
    }

    // ── ROLLUP super-aggregate over a qualified column (the reported root cause) ──

    @Test
    public void rollupSuperAggregateOverQualifiedColumn() {
        engine.execute("CREATE TABLE reg (region VARCHAR, amount NUMBER)");
        engine.execute("INSERT INTO reg VALUES ('E', 10), ('E', 20), ('W', 30)");
        final ResultSet rs = engine.executeQuery(
                "SELECT r.region, SUM(r.amount) FROM reg r GROUP BY ROLLUP(r.region)");
        double grandTotal = -1.0;
        for (final Row row : rs.getRows()) {
            if (row.getValue(0) == null) {
                grandTotal = ((Number) row.getValue(1)).doubleValue();
            }
        }
        // The grand-total row (region = NULL) must sum the qualified column, not be NULL.
        assertEquals(60.0, grandTotal, 1e-9);
    }
}
