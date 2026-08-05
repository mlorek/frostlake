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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WindowFunctionsExtTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sales (id INTEGER, dept VARCHAR, amount DOUBLE)");
        engine.execute("INSERT INTO sales VALUES (1, 'A', 100)");
        engine.execute("INSERT INTO sales VALUES (2, 'A', 200)");
        engine.execute("INSERT INTO sales VALUES (3, 'A', 300)");
        engine.execute("INSERT INTO sales VALUES (4, 'B', 150)");
        engine.execute("INSERT INTO sales VALUES (5, 'B', 150)");
        engine.execute("INSERT INTO sales VALUES (6, 'B', 400)");
    }

    // ── NTILE ──────────────────────────────────────────────────────────────────

    @Test
    public void testNtile2() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, NTILE(2) OVER (ORDER BY id) AS bucket FROM sales ORDER BY id");
        assertEquals(6, rs.getRowCount());
        // rows 1-3 → bucket 1, rows 4-6 → bucket 2
        assertEquals(1L, rs.getRows().get(0).getValue(1));
        assertEquals(1L, rs.getRows().get(2).getValue(1));
        assertEquals(2L, rs.getRows().get(3).getValue(1));
        assertEquals(2L, rs.getRows().get(5).getValue(1));
    }

    @Test
    public void testNtile3() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, NTILE(3) OVER (ORDER BY id) AS bucket FROM sales ORDER BY id");
        assertEquals(6, rs.getRowCount());
        assertEquals(1L, rs.getRows().get(0).getValue(1));
        assertEquals(1L, rs.getRows().get(1).getValue(1));
        assertEquals(2L, rs.getRows().get(2).getValue(1));
        assertEquals(3L, rs.getRows().get(4).getValue(1));
    }

    // ── PERCENT_RANK ───────────────────────────────────────────────────────────

    @Test
    public void testPercentRank() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, amount, PERCENT_RANK() OVER (ORDER BY amount) AS pr FROM sales ORDER BY amount");
        assertEquals(6, rs.getRowCount());
        assertEquals(0.0, ((Number) rs.getRows().get(0).getValue(2)).doubleValue(), 0.001);
        assertEquals(1.0, ((Number) rs.getRows().get(5).getValue(2)).doubleValue(), 0.001);
    }

    @Test
    public void testPercentRankSingleRow() {
        engine.execute("CREATE TABLE one_row (v INTEGER)");
        engine.execute("INSERT INTO one_row VALUES (42)");
        ResultSet rs = engine.executeQuery("SELECT v, PERCENT_RANK() OVER (ORDER BY v) AS pr FROM one_row");
        assertEquals(0.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.001);
    }

    // ── CUME_DIST ──────────────────────────────────────────────────────────────

    @Test
    public void testCumeDist() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, CUME_DIST() OVER (ORDER BY id) AS cd FROM sales ORDER BY id");
        assertEquals(6, rs.getRowCount());
        // Last row is always 1.0
        assertEquals(1.0, ((Number) rs.getRows().get(5).getValue(1)).doubleValue(), 0.001);
        // First row: 1/6
        assertEquals(1.0 / 6, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.01);
    }

    @Test
    public void testCumeDistWithTies() {
        // rows 4 and 5 both have amount=150 → same cume_dist
        ResultSet rs = engine.executeQuery(
            "SELECT id, amount, CUME_DIST() OVER (ORDER BY amount) AS cd FROM sales ORDER BY amount, id");
        assertEquals(6, rs.getRowCount());
        double cd4 = ((Number) rs.getRows().get(1).getValue(2)).doubleValue();
        double cd5 = ((Number) rs.getRows().get(2).getValue(2)).doubleValue();
        assertEquals(cd4, cd5, 0.001);
    }

    // ── RATIO_TO_REPORT ────────────────────────────────────────────────────────

    @Test
    public void testRatioToReport() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, RATIO_TO_REPORT(amount) OVER () AS ratio FROM sales ORDER BY id");
        assertEquals(6, rs.getRowCount());
        // Total = 100+200+300+150+150+400 = 1300
        double total = 1300.0;
        assertEquals(100.0 / total, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.001);
        assertEquals(400.0 / total, ((Number) rs.getRows().get(5).getValue(1)).doubleValue(), 0.001);
        // All ratios sum to 1
        double sum = 0;
        for (int i = 0; i < rs.getRowCount(); i++) sum += ((Number) rs.getRows().get(i).getValue(1)).doubleValue();
        assertEquals(1.0, sum, 0.001);
    }

    // ── FIRST_VALUE ────────────────────────────────────────────────────────────

    @Test
    public void testFirstValue() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, FIRST_VALUE(amount) OVER (ORDER BY id) AS fv FROM sales ORDER BY id");
        assertEquals(6, rs.getRowCount());
        // FIRST_VALUE with default frame (UNBOUNDED PRECEDING to CURRENT ROW) = first in frame
        // For row 1 the first value in the frame is 100 (itself)
        assertEquals(100.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.001);
        // For all rows ordered by id, first value in frame is always 100 (row 1)
        assertEquals(100.0, ((Number) rs.getRows().get(3).getValue(1)).doubleValue(), 0.001);
    }

    // ── LAST_VALUE ─────────────────────────────────────────────────────────────

    @Test
    public void testLastValue() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, LAST_VALUE(amount) OVER (ORDER BY id) AS lv FROM sales ORDER BY id");
        assertEquals(6, rs.getRowCount());
        // Snowflake's default frame for LAST_VALUE is the WHOLE partition (RANGE BETWEEN UNBOUNDED
        // PRECEDING AND UNBOUNDED FOLLOWING), so every row sees the partition's last amount (400).
        assertEquals(400.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue(), 0.001);
        assertEquals(400.0, ((Number) rs.getRows().get(2).getValue(1)).doubleValue(), 0.001);
    }

    // ── NTH_VALUE ──────────────────────────────────────────────────────────────

    @Test
    public void testNthValue2() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, NTH_VALUE(amount, 2) OVER (ORDER BY id) AS nv FROM sales ORDER BY id");
        assertEquals(6, rs.getRowCount());
        // 2nd value in partition ordered by id is 200 (id=2)
        assertEquals(200.0, ((Number) rs.getRows().get(1).getValue(1)).doubleValue(), 0.001);
    }

    @Test
    public void testNthValueOutOfRange() {
        ResultSet rs = engine.executeQuery(
            "SELECT id, NTH_VALUE(amount, 99) OVER (ORDER BY id) AS nv FROM sales ORDER BY id");
        assertEquals(6, rs.getRowCount());
        assertNull(rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testNthValue1IsSameAsFirstValue() {
        ResultSet rs1 = engine.executeQuery(
            "SELECT id, NTH_VALUE(amount, 1) OVER (ORDER BY id) AS v FROM sales ORDER BY id");
        ResultSet rs2 = engine.executeQuery(
            "SELECT id, FIRST_VALUE(amount) OVER (ORDER BY id) AS v FROM sales ORDER BY id");
        for (int i = 0; i < rs1.getRowCount(); i++) {
            assertEquals(
                ((Number) rs1.getRows().get(i).getValue(1)).doubleValue(),
                ((Number) rs2.getRows().get(i).getValue(1)).doubleValue(), 0.001);
        }
    }
}
