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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Window frame clauses — {@code ROWS}/{@code RANGE BETWEEN …}. Covers the Snowflake default frame (an
 * ordered aggregate is a running, peer-aware window; an unordered one spans the whole partition), explicit
 * ROWS frames (moving windows), RANGE peer semantics on tied ORDER BY values, explicit-frame FIRST_VALUE /
 * LAST_VALUE, and value-based RANGE numeric offsets (a numeric ORDER BY key is required).
 */
public class WindowFrameTest extends BaseDatabaseTest {

    private double dbl(final ResultSet rs, final int rowIdx, final int colIdx) {
        return ((Number) rs.getRows().get(rowIdx).getValue(colIdx)).doubleValue();
    }

    private void seedSingleGroup() {
        engine.execute("CREATE TABLE t (id INTEGER, val INTEGER)");
        engine.execute("INSERT INTO t VALUES (1, 10), (2, 20), (3, 30), (4, 40)");
    }

    @Test
    public void orderedAggregateDefaultsToRunningWindow() {
        seedSingleGroup();
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(val) OVER (ORDER BY id) AS s FROM t ORDER BY id");
        // Default frame UNBOUNDED PRECEDING .. CURRENT ROW ⇒ running sum: 10, 30, 60, 100.
        assertEquals(10.0, dbl(rs, 0, 1), 0.001);
        assertEquals(30.0, dbl(rs, 1, 1), 0.001);
        assertEquals(60.0, dbl(rs, 2, 1), 0.001);
        assertEquals(100.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void unorderedAggregateSpansWholePartition() {
        seedSingleGroup();
        final ResultSet rs = engine.executeQuery("SELECT id, SUM(val) OVER () AS s FROM t ORDER BY id");
        // No ORDER BY ⇒ whole-partition frame: every row sees the total 100.
        assertEquals(100.0, dbl(rs, 0, 1), 0.001);
        assertEquals(100.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void rowsPrecedingGivesMovingSum() {
        seedSingleGroup();
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(val) OVER (ORDER BY id ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS s FROM t ORDER BY id");
        // Two-row trailing window: 10, 30, 50, 70.
        assertEquals(10.0, dbl(rs, 0, 1), 0.001);
        assertEquals(30.0, dbl(rs, 1, 1), 0.001);
        assertEquals(50.0, dbl(rs, 2, 1), 0.001);
        assertEquals(70.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void rowsBetweenPrecedingAndFollowingCentersTheWindow() {
        seedSingleGroup();
        final ResultSet rs = engine.executeQuery(
            "SELECT id, AVG(val) OVER (ORDER BY id ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING) AS a FROM t ORDER BY id");
        // Centered 3-row average (clamped at the ends): 15, 20, 30, 35.
        assertEquals(15.0, dbl(rs, 0, 1), 0.001);
        assertEquals(20.0, dbl(rs, 1, 1), 0.001);
        assertEquals(30.0, dbl(rs, 2, 1), 0.001);
        assertEquals(35.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void rowsCurrentToUnboundedFollowingGivesSuffixSum() {
        seedSingleGroup();
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(val) OVER (ORDER BY id ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING) AS s FROM t ORDER BY id");
        // Suffix sums: 100, 90, 70, 40.
        assertEquals(100.0, dbl(rs, 0, 1), 0.001);
        assertEquals(90.0, dbl(rs, 1, 1), 0.001);
        assertEquals(70.0, dbl(rs, 2, 1), 0.001);
        assertEquals(40.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void singleBoundFrameEndsAtCurrentRow() {
        seedSingleGroup();
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(val) OVER (ORDER BY id ROWS UNBOUNDED PRECEDING) AS s FROM t ORDER BY id");
        // A single bound implies "… AND CURRENT ROW" ⇒ running sum: 10, 30, 60, 100.
        assertEquals(10.0, dbl(rs, 0, 1), 0.001);
        assertEquals(100.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void lastValueOverWholePartitionFrame() {
        seedSingleGroup();
        final ResultSet rs = engine.executeQuery(
            "SELECT id, LAST_VALUE(val) OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS lv "
            + "FROM t ORDER BY id");
        // With a whole-partition frame, LAST_VALUE is the last row's value (40) for every row.
        assertEquals(40.0, dbl(rs, 0, 1), 0.001);
        assertEquals(40.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void rangeDefaultGroupsPeersOnTiedOrderKey() {
        engine.execute("CREATE TABLE p (id INTEGER, k INTEGER, val INTEGER)");
        engine.execute("INSERT INTO p VALUES (1, 1, 10), (2, 1, 20), (3, 2, 30)");

        // Default RANGE frame: the two k=1 rows are peers, so both see the sum through the last peer (30);
        // the k=2 row sees everything (60).
        final ResultSet rangeRs = engine.executeQuery(
            "SELECT id, SUM(val) OVER (ORDER BY k) AS s FROM p ORDER BY id");
        assertEquals(30.0, dbl(rangeRs, 0, 1), 0.001);
        assertEquals(30.0, dbl(rangeRs, 1, 1), 0.001);
        assertEquals(60.0, dbl(rangeRs, 2, 1), 0.001);

        // ROWS is positional (ties are NOT grouped): 10, 30, 60.
        final ResultSet rowsRs = engine.executeQuery(
            "SELECT id, SUM(val) OVER (ORDER BY k ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS s FROM p ORDER BY id");
        assertEquals(10.0, dbl(rowsRs, 0, 1), 0.001);
        assertEquals(30.0, dbl(rowsRs, 1, 1), 0.001);
        assertEquals(60.0, dbl(rowsRs, 2, 1), 0.001);
    }

    /** Gapped values so a value-based RANGE differs from a positional ROWS frame. */
    private void seedGapped() {
        engine.execute("CREATE TABLE g (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO g VALUES (1, 10), (2, 20), (3, 30), (4, 100)");
    }

    @Test
    public void rangePrecedingIsValueBased() {
        seedGapped();
        // v-15..v: 10→10, 20→[5,20]=30, 30→[15,30]=50, 100→[85,100]=100 (positional ROWS would give 130 at v=100).
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(v) OVER (ORDER BY v RANGE BETWEEN 15 PRECEDING AND CURRENT ROW) AS s FROM g ORDER BY id");
        assertEquals(10.0, dbl(rs, 0, 1), 0.001);
        assertEquals(30.0, dbl(rs, 1, 1), 0.001);
        assertEquals(50.0, dbl(rs, 2, 1), 0.001);
        assertEquals(100.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void rangeFollowing() {
        seedGapped();
        // v..v+15: 10→[10,25]=30, 20→[20,35]=50, 30→[30,45]=30, 100→100.
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(v) OVER (ORDER BY v RANGE BETWEEN CURRENT ROW AND 15 FOLLOWING) AS s FROM g ORDER BY id");
        assertEquals(30.0, dbl(rs, 0, 1), 0.001);
        assertEquals(50.0, dbl(rs, 1, 1), 0.001);
        assertEquals(30.0, dbl(rs, 2, 1), 0.001);
        assertEquals(100.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void rangeBothSided() {
        seedGapped();
        // v-15..v+15: 10→[−5,25]=30, 20→[5,35]=60, 30→[15,45]=50, 100→100.
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(v) OVER (ORDER BY v RANGE BETWEEN 15 PRECEDING AND 15 FOLLOWING) AS s FROM g ORDER BY id");
        assertEquals(30.0, dbl(rs, 0, 1), 0.001);
        assertEquals(60.0, dbl(rs, 1, 1), 0.001);
        assertEquals(50.0, dbl(rs, 2, 1), 0.001);
        assertEquals(100.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void rangeDescendingFlipsThePrecedingSide() {
        seedGapped();
        // DESC sort 100,30,20,10; PRECEDING = larger values. v=20 → [20,35] = 30+20 = 50.
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(v) OVER (ORDER BY v DESC RANGE BETWEEN 15 PRECEDING AND CURRENT ROW) AS s FROM g ORDER BY id");
        assertEquals(30.0, dbl(rs, 0, 1), 0.001);   // v=10 → [10,25] = 10+20
        assertEquals(50.0, dbl(rs, 1, 1), 0.001);   // v=20 → [20,35] = 20+30
        assertEquals(30.0, dbl(rs, 2, 1), 0.001);   // v=30 → [30,45] = 30
        assertEquals(100.0, dbl(rs, 3, 1), 0.001);  // v=100
    }

    @Test
    public void rangePeersShareTheSameFrame() {
        engine.execute("CREATE TABLE p (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO p VALUES (1, 10), (2, 10), (3, 20), (4, 25)");
        // Both v=10 rows: [5,10] = 10+10 = 20. v=20: [15,20] = 20. v=25: [20,25] = 45.
        final ResultSet rs = engine.executeQuery(
            "SELECT id, SUM(v) OVER (ORDER BY v RANGE BETWEEN 5 PRECEDING AND CURRENT ROW) AS s FROM p ORDER BY id");
        assertEquals(20.0, dbl(rs, 0, 1), 0.001);
        assertEquals(20.0, dbl(rs, 1, 1), 0.001);
        assertEquals(20.0, dbl(rs, 2, 1), 0.001);
        assertEquals(45.0, dbl(rs, 3, 1), 0.001);
    }

    @Test
    public void rangeOffsetRequiresNumericOrderByColumn() {
        engine.execute("CREATE TABLE s (id INTEGER, name VARCHAR)");
        engine.execute("INSERT INTO s VALUES (1, 'a'), (2, 'b')");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT id, COUNT(*) OVER (ORDER BY name RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) AS c FROM s");
            }
        });
    }
}
