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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Window functions and QUALIFY must resolve their PARTITION BY / ORDER BY / argument columns over the rows
 * of a JOIN. The window stage evaluates those expressions against the merged join table, so an
 * alias-qualified column (f.cid) only resolves with the join's alias context — without it every row
 * collapsed into a single partition and ROW_NUMBER() came out NULL, breaking the very common
 * "QUALIFY ROW_NUMBER() OVER (PARTITION BY key ORDER BY ts DESC) = 1" latest-row-per-key pattern.
 */
public class WindowFunctionOverJoinTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE f (cid VARCHAR, ed DATE, val INT)");
        engine.execute("CREATE OR REPLACE TABLE dim (cid VARCHAR, ind INT)");
        engine.execute("""
            INSERT INTO f VALUES
              ('c1', '2026-01-03', 10), ('c1', '2026-01-01', 7),
              ('c2', '2026-01-02', 20), ('c2', '2026-01-01', 5),
              ('c3', '2026-01-03', 30)
            """);
        engine.execute("INSERT INTO dim VALUES ('c1', 100), ('c2', 200)");
    }

    private List<String> col0(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < rs.getRowCount(); i++) {
            out.add(String.valueOf(rs.getRows().get(i).getValue(0)));
        }
        return out;
    }

    @Test
    public void qualifyRowNumberLatestPerKeyAfterLeftJoin() {
        // The latest row per key: exactly one per cid, choosing the max ed.
        final List<String> ids = col0("""
            SELECT f.cid
            FROM f LEFT JOIN dim d ON f.cid = d.cid
            QUALIFY ROW_NUMBER() OVER (PARTITION BY f.cid ORDER BY f.ed DESC) = 1
            ORDER BY f.cid
            """);
        assertEquals(List.of("c1", "c2", "c3"), ids);
    }

    @Test
    public void rowNumberInSelectAfterJoinPartitions() {
        final List<String> vals = col0("""
            SELECT f.cid || ':' || ROW_NUMBER() OVER (PARTITION BY f.cid ORDER BY f.ed DESC)
            FROM f LEFT JOIN dim d ON f.cid = d.cid
            ORDER BY 1
            """);
        assertEquals(List.of("c1:1", "c1:2", "c2:1", "c2:2", "c3:1"), vals);
    }

    @Test
    public void unqualifiedPartitionColumnResolvesAfterJoin() {
        // 'ed' is only in f, so unqualified references still resolve over the joined rows.
        final List<String> ids = col0("""
            SELECT f.cid
            FROM f LEFT JOIN dim d ON f.cid = d.cid
            QUALIFY ROW_NUMBER() OVER (PARTITION BY f.cid ORDER BY ed DESC) = 1
            ORDER BY f.cid
            """);
        assertEquals(List.of("c1", "c2", "c3"), ids);
    }

    @Test
    public void aggregateWindowPartitionsAfterJoin() {
        // SUM(val) OVER (PARTITION BY f.cid): 17 for c1 (10+7), 25 for c2 (20+5), 30 for c3.
        // val is INTEGER, so SUM is a whole number (17, not 17.0).
        final List<String> sums = col0("""
            SELECT DISTINCT SUM(f.val) OVER (PARTITION BY f.cid)
            FROM f LEFT JOIN dim d ON f.cid = d.cid
            ORDER BY 1
            """);
        assertEquals(List.of("17", "25", "30"), sums);
    }

    @Test
    public void qualifyIsUnaffectedWithoutJoin() {
        // Guard: the single-table path (no alias context) still behaves exactly as before.
        final List<String> ids = col0("""
            SELECT cid FROM f
            QUALIFY ROW_NUMBER() OVER (PARTITION BY cid ORDER BY ed DESC) = 1
            ORDER BY cid
            """);
        assertEquals(List.of("c1", "c2", "c3"), ids);
    }
}
