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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A window function may appear nested inside a larger expression (e.g. {@code revenue - LAG(revenue)
 * OVER (...)}, {@code 100 * RATIO_TO_REPORT(x) OVER (...)}), not only as a whole SELECT item. Previously
 * this threw "window function (OVER) not ported", because the surrounding expression was AST-evaluated
 * without the window path being triggered.
 */
public class WindowInExpressionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sales (yr NUMBER, revenue NUMBER)");
        engine.execute("INSERT INTO sales VALUES (2020, 100), (2021, 150), (2022, 250)");
    }

    private List<Row> rows(final String sql) {
        return engine.executeQuery(sql).getRows();
    }

    @Test
    public void windowMinusLagIsPerRow() {
        // revenue - LAG(revenue,1,0) OVER (ORDER BY yr) → [100, 50, 100].
        final List<Row> r = rows(
            "SELECT yr, revenue - LAG(revenue, 1, 0) OVER (ORDER BY yr) AS diff FROM sales ORDER BY yr");
        assertEquals(3, r.size());
        assertEquals(100.0, ((Number) r.get(0).getValue(1)).doubleValue(), 1e-9);
        assertEquals(50.0, ((Number) r.get(1).getValue(1)).doubleValue(), 1e-9);
        assertEquals(100.0, ((Number) r.get(2).getValue(1)).doubleValue(), 1e-9);
    }

    @Test
    public void scaledRatioToReport() {
        // 100 * RATIO_TO_REPORT(revenue) OVER () → [20, 30, 50] (revenue / 500 * 100).
        final List<Row> r = rows(
            "SELECT yr, 100 * RATIO_TO_REPORT(revenue) OVER () AS pct FROM sales ORDER BY yr");
        assertEquals(20.0, ((Number) r.get(0).getValue(1)).doubleValue(), 1e-9);
        assertEquals(30.0, ((Number) r.get(1).getValue(1)).doubleValue(), 1e-9);
        assertEquals(50.0, ((Number) r.get(2).getValue(1)).doubleValue(), 1e-9);
    }

    @Test
    public void bareWindowStillWorks() {
        // Regression: an entire SELECT item that IS a window function still resolves.
        final List<Row> r = rows("SELECT yr, ROW_NUMBER() OVER (ORDER BY yr) AS rn FROM sales ORDER BY yr");
        assertEquals(1L, ((Number) r.get(0).getValue(1)).longValue());
        assertEquals(2L, ((Number) r.get(1).getValue(1)).longValue());
        assertEquals(3L, ((Number) r.get(2).getValue(1)).longValue());
    }

    @Test
    public void windowInsideFunctionCall() {
        // ABS(LAG(revenue,1,0) OVER (ORDER BY yr) - revenue) → [100, 50, 100].
        final List<Row> r = rows(
            "SELECT yr, ABS(LAG(revenue, 1, 0) OVER (ORDER BY yr) - revenue) AS d FROM sales ORDER BY yr");
        assertEquals(100.0, ((Number) r.get(0).getValue(1)).doubleValue(), 1e-9);
        assertEquals(50.0, ((Number) r.get(1).getValue(1)).doubleValue(), 1e-9);
        assertEquals(100.0, ((Number) r.get(2).getValue(1)).doubleValue(), 1e-9);
    }
}
