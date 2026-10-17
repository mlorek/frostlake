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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A FLOAT SUM over a cumulative RANGE frame adds its peer groups in turn, each group corrected within itself, and a
 * running ROWS frame adds its rows in turn, each a partial of its own: over distinct ordering keys the two are the same
 * sum, row for row, however long the partition. The cumulative AVG is that sum over the rows counted, and peer groups
 * share their group's value.
 */
public class CumulativeFloatFrameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ff AS SELECT SEQ4() AS i, MOD(SEQ4(), 7) AS k, "
            + "(SEQ4() * 0.37 + 0.1)::FLOAT * (SEQ4() * 0.37 + 0.1)::FLOAT AS f "
            + "FROM TABLE(GENERATOR(ROWCOUNT => 3000))");
    }

    private String cell(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder out = new StringBuilder();
        for (int c = 0; c < row.getValues().size(); c++) {
            out.append(c > 0 ? ", " : "").append(row.getValue(c));
        }
        return out.toString();
    }

    @Test
    public void aCumulativeRangeFrameOverDistinctKeysIsTheRunningSum() {
        assertEquals("3000, 0, 0", cell("""
            SELECT COUNT(*), COUNT_IF(s_range <> s_rows), COUNT_IF(a_range <> s_range / n)
            FROM (SELECT SUM(f) OVER (ORDER BY i) s_range,
                         SUM(f) OVER (ORDER BY i ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) s_rows,
                         AVG(f) OVER (ORDER BY i) a_range,
                         ROW_NUMBER() OVER (ORDER BY i) n
                  FROM ff)"""));
    }

    @Test
    public void aPartitionedCumulativeFrameIsTheRunningSumOfEachPartition() {
        assertEquals("3000, 0", cell("""
            SELECT COUNT(*), COUNT_IF(s_range <> s_rows)
            FROM (SELECT SUM(f) OVER (PARTITION BY k ORDER BY i) s_range,
                         SUM(f) OVER (PARTITION BY k ORDER BY i ROWS UNBOUNDED PRECEDING) s_rows
                  FROM ff)"""));
    }

    @Test
    public void peersShareTheirGroupsValue() {
        assertEquals("7, 0", cell("""
            SELECT COUNT(DISTINCT k), COUNT_IF(n > 1)
            FROM (SELECT k, COUNT(DISTINCT s) n
                  FROM (SELECT k, SUM(f) OVER (ORDER BY k) s FROM ff)
                  GROUP BY k)"""));
    }
}
