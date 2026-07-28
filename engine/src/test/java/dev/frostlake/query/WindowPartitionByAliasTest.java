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

/**
 * A window function's PARTITION BY / ORDER BY (in a SELECT item or inline in QUALIFY) may reference a
 * SELECT-list alias, not just a base column — Snowflake resolves the alias to its defining expression. When
 * that wasn't done, an aliased PARTITION BY collapsed every row into one partition (so a dedup
 * {@code QUALIFY ROW_NUMBER() OVER (PARTITION BY <alias>) = 1} wrongly kept a single row).
 */
public class WindowPartitionByAliasTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE t (id INTEGER, name VARCHAR, ts INTEGER)");
        // groups A/B/C; A has two rows (ts 10 and 5)
        engine.execute("INSERT INTO t VALUES (1, 'A', 10), (2, 'B', 20), (3, 'C', 30), (4, 'A', 5)");
    }

    private long count(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void partitionByPlainSelectAlias() {
        // PARTITION BY the alias `nm` (= name) → 3 partitions, so 3 rows survive the top-1 QUALIFY.
        assertEquals(3, engine.executeQuery(
            "SELECT id FROM (SELECT id, name AS nm FROM t "
            + "QUALIFY ROW_NUMBER() OVER (PARTITION BY nm ORDER BY ts DESC) = 1)").getRowCount());
    }

    @Test
    public void partitionByComputedAliasDedup() {
        // The cloud-loader shape: PARTITION BY a computed alias in an inline QUALIFY.
        assertEquals(3, engine.executeQuery(
            "SELECT id FROM (SELECT id, LOWER(name) AS lo FROM t "
            + "QUALIFY ROW_NUMBER() OVER (PARTITION BY lo ORDER BY ts DESC) = 1)").getRowCount());
    }

    @Test
    public void partitionByAliasInSelectListWindowValue() {
        // The ROW_NUMBER value itself must partition by the alias (A has 2 rows → ranks 1 and 2).
        final ResultSet rs = engine.executeQuery(
            "SELECT id, ROW_NUMBER() OVER (PARTITION BY nm ORDER BY ts) rn FROM (SELECT id, name AS nm, ts FROM t) ORDER BY id");
        // A: id4(ts5)=1, id1(ts10)=2 ; B: id2=1 ; C: id3=1
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());   // id 1
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(1)).longValue());   // id 2
        assertEquals(1L, ((Number) rs.getRows().get(2).getValue(1)).longValue());   // id 3
        assertEquals(1L, ((Number) rs.getRows().get(3).getValue(1)).longValue());   // id 4
    }

    @Test
    public void windowOrderByAlias() {
        // ORDER BY the alias `neg` in the window frame ranks by -ts (ascending -ts = descending ts).
        final ResultSet rs = engine.executeQuery(
            "SELECT id, ROW_NUMBER() OVER (ORDER BY neg) rn FROM (SELECT id, -ts AS neg FROM t) ORDER BY rn");
        // -ts: id3=-30, id2=-20, id1=-10, id4=-5 → ascending: id3,id2,id1,id4
        assertEquals(3, ((Number) rs.getRows().get(0).getValue(0)).intValue());
        assertEquals(4, ((Number) rs.getRows().get(3).getValue(0)).intValue());
    }

    @Test
    public void baseColumnTakesPrecedenceOverSameNamedAlias() {
        // 'name' in PARTITION BY resolves to the base column, not the alias that renamed a constant to `name`.
        // A/B/C base-name groups → 3 partitions (if it used the constant alias, it'd be 1).
        assertEquals(3, engine.executeQuery(
            "SELECT id FROM (SELECT id, 'X' AS name FROM t "
            + "QUALIFY ROW_NUMBER() OVER (PARTITION BY name ORDER BY id) = 1)").getRowCount());
    }

    @Test
    public void partitionByBaseColumnStillWorks() {
        // Regression: a plain base-column PARTITION BY is unaffected.
        assertEquals(3, engine.executeQuery(
            "SELECT id FROM t QUALIFY ROW_NUMBER() OVER (PARTITION BY name ORDER BY ts DESC) = 1").getRowCount());
    }
}
