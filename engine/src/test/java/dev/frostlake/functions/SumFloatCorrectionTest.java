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
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SUM over FLOATs reads its Kahan-compensated sum WITH the last compensation applied wherever it is an
 * aggregate — grouped or not, under ROLLUP and HAVING, over a VARIANT or a text — and in a window whose
 * frame is the whole partition. A running or sliding frame reads the running sum instead, a cumulative
 * RANGE frame sums a peer group at a time. The pair that tells them apart is 0.3 then 0.7 squared:
 * 0.57999999999999984901 corrected, 0.57999999999999996003 running. SUM(DISTINCT) meets its distinct values in
 * no fixed order, so either reading can come back. Every digit below is Snowflake's, read through a
 * twentieth-decimal cast.
 */
public class SumFloatCorrectionTest extends BaseDatabaseTest {

    private static final String PAIR = "(SELECT 1 AS i, 1 AS k, 0.3::FLOAT AS x UNION ALL SELECT 2, 1, 0.7::FLOAT)";
    private static final String CORRECTED = "0.57999999999999984901";
    private static final String RUNNING = "0.57999999999999996003";

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** SUM(x * x) under a window over the pair, one cell per row in i order. */
    private String window(final String over) {
        return rows("SELECT i, TO_VARCHAR((SUM(x * x) OVER (" + over + "))::NUMBER(38,20)) FROM " + PAIR
            + " ORDER BY i");
    }

    @Test
    public void theAggregateReadsTheCorrectedSum() {
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM " + PAIR));
        assertEquals(RUNNING, rows("SELECT TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM "
            + "(SELECT 0.7::FLOAT AS x UNION ALL SELECT 0.3::FLOAT)"));
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM "
            + "(SELECT -0.3::FLOAT AS x UNION ALL SELECT 0.7::FLOAT)"));
        assertEquals("0.82999999999999984901", rows("SELECT TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM "
            + "(SELECT 0.3::FLOAT AS x UNION ALL SELECT 0.7::FLOAT UNION ALL SELECT 0.5::FLOAT)"));
        assertEquals("false", rows("SELECT (SUM(x * x) = 0.58::FLOAT)::VARCHAR FROM " + PAIR));
        assertEquals("0.58", rows("SELECT TO_VARCHAR(SUM(x * x)) FROM " + PAIR));
    }

    @Test
    public void nullsTakeNoPartWhereverTheyStand() {
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM "
            + "(SELECT 0.3::FLOAT AS x UNION ALL SELECT NULL UNION ALL SELECT 0.7::FLOAT)"));
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM "
            + "(SELECT NULL::FLOAT AS x UNION ALL SELECT 0.3::FLOAT UNION ALL SELECT 0.7::FLOAT)"));
    }

    @Test
    public void everyAggregateSpellingReadsTheSame() {
        assertEquals("1, 1.00000000000000000000, " + CORRECTED + " | 2, 0.59999999999999997780, 0.14000000000000001332",
            rows("SELECT k, TO_VARCHAR(SUM(x)::NUMBER(38,20)), TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM "
                + "(SELECT 1 AS k, 0.3::FLOAT AS x UNION ALL SELECT 2, 0.1::FLOAT UNION ALL SELECT 1, 0.7::FLOAT "
                + "UNION ALL SELECT 2, 0.2::FLOAT UNION ALL SELECT 2, 0.3::FLOAT) GROUP BY k ORDER BY k"));
        assertEquals("1, " + CORRECTED + " | 2, 0.01000000000000000194 | null, 0.58999999999999985789",
            rows("SELECT k, TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM (SELECT 1 AS k, 0.3::FLOAT AS x "
                + "UNION ALL SELECT 1, 0.7::FLOAT UNION ALL SELECT 2, 0.1::FLOAT) GROUP BY ROLLUP (k) "
                + "ORDER BY k NULLS LAST"));
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(SUM(x * x)::NUMBER(38,20)) FROM " + PAIR
            + " HAVING SUM(x * x) < 1"));
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(SUM(TO_VARIANT(x * x))::NUMBER(38,20)) FROM " + PAIR));
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(SUM(v)::NUMBER(38,20)) FROM (SELECT PARSE_JSON('0.09') AS v "
            + "UNION ALL SELECT PARSE_JSON('0.48999999999999994'))"));
        assertEquals(CORRECTED, rows("SELECT TO_VARCHAR(SUM(s)::NUMBER(38,20)) FROM (SELECT '0.09' AS s "
            + "UNION ALL SELECT '0.48999999999999994')"));
        engine.execute("CREATE OR REPLACE TABLE sfc (i INT, x FLOAT)");
        engine.execute("INSERT INTO sfc VALUES (1, 0.3), (2, 0.7)");
        assertEquals(CORRECTED + ", 1.00000000000000000000",
            rows("SELECT TO_VARCHAR(SUM(x * x)::NUMBER(38,20)), TO_VARCHAR(SUM(x)::NUMBER(38,20)) FROM sfc"));
    }

    /** The distinct values come in no fixed order, so either reading of the pair is Snowflake's answer. */
    @Test
    public void distinctAnswersEitherReading() {
        final String distinct = rows("SELECT TO_VARCHAR(SUM(DISTINCT x * x)::NUMBER(38,20)) FROM "
            + "(SELECT 0.3::FLOAT AS x UNION ALL SELECT 0.7::FLOAT UNION ALL SELECT 0.3::FLOAT)");
        assertTrue(RUNNING.equals(distinct) || CORRECTED.equals(distinct), distinct);
    }

    /** Where the correction changes nothing the answers stay what they were: ten tenths are exactly one. */
    @Test
    public void tenthsAndCancellationsAreUnchanged() {
        final String[][] tenths = {{"6", "0.60000000000000008882"}, {"7", "0.70000000000000006661"},
            {"10", "1.00000000000000000000"}, {"12", "1.20000000000000017764"}, {"1000", "100.00000000000000000000"}};
        for (final String[] cell : tenths) {
            assertEquals(cell[1], rows("SELECT TO_VARCHAR(SUM(x)::NUMBER(38,20)) FROM (SELECT 0.1::FLOAT AS x "
                + "FROM TABLE(GENERATOR(ROWCOUNT => " + cell[0] + ")))"), cell[0] + " tenths");
        }
        assertEquals("2.00000000000000000000", rows("SELECT TO_VARCHAR(SUM(x)::NUMBER(38,20)) FROM "
            + "(SELECT 1e16::FLOAT AS x UNION ALL SELECT 1::FLOAT UNION ALL SELECT 1::FLOAT UNION ALL SELECT -1e16::FLOAT)"));
        assertEquals("0.00000000000000000000", rows("SELECT TO_VARCHAR(SUM(x)::NUMBER(38,20)) FROM "
            + "(SELECT 1e16::FLOAT AS x UNION ALL SELECT 1::FLOAT UNION ALL SELECT -1e16::FLOAT)"));
    }

    @Test
    public void aWholePartitionWindowReadsTheCorrectedSum() {
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED, window(""));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED, window("PARTITION BY k"));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED,
            window("ORDER BY i ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING"));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED,
            window("ORDER BY i RANGE BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING"));
    }

    @Test
    public void aRunningOrSlidingFrameReadsTheRunningSum() {
        assertEquals("1, 0.08999999999999999667 | 2, " + RUNNING, window("ORDER BY i"));
        assertEquals("1, 0.08999999999999999667 | 2, " + RUNNING,
            window("ORDER BY i ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"));
        assertEquals("1, 0.08999999999999999667 | 2, " + RUNNING,
            window("ORDER BY k ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW"));
        assertEquals("1, " + RUNNING + " | 2, 0.48999999999999993561",
            window("ORDER BY i ROWS BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING"));
        assertEquals("1, " + RUNNING + " | 2, " + RUNNING, window("ORDER BY i ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING"));
        assertEquals("1, " + RUNNING + " | 2, " + RUNNING, window("ORDER BY k RANGE BETWEEN CURRENT ROW AND CURRENT ROW"));
        assertEquals("1, 0.08999999999999999667, 0.58999999999999985789 | 2, " + RUNNING + ", 0.58999999999999985789"
                + " | 3, 0.58999999999999985789, 0.58999999999999985789",
            rows("SELECT i, TO_VARCHAR((SUM(x * x) OVER (ORDER BY i ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW))"
                + "::NUMBER(38,20)), TO_VARCHAR((SUM(x * x) OVER ())::NUMBER(38,20)) FROM (SELECT 1 AS i, 0.3::FLOAT AS x "
                + "UNION ALL SELECT 2, 0.7::FLOAT UNION ALL SELECT 3, 0.1::FLOAT) ORDER BY i"));
    }

    /** A cumulative RANGE frame adds a peer group at a time, each group's sum corrected. */
    @Test
    public void aCumulativeRangeFrameSumsAPeerGroupAtATime() {
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED, window("ORDER BY k"));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED,
            window("ORDER BY k RANGE BETWEEN CURRENT ROW AND UNBOUNDED FOLLOWING"));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED + " | 3, 0.58999999999999985789",
            rows("SELECT i, TO_VARCHAR((SUM(x * x) OVER (ORDER BY k))::NUMBER(38,20)) FROM (SELECT 1 AS i, 1 AS k, "
                + "0.3::FLOAT AS x UNION ALL SELECT 2, 1, 0.7::FLOAT UNION ALL SELECT 3, 2, 0.1::FLOAT) ORDER BY i"));
        assertEquals("1, 0.01000000000000000194 | 2, 0.58999999999999985789 | 3, 0.58999999999999985789",
            rows("SELECT i, TO_VARCHAR((SUM(x * x) OVER (ORDER BY k RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW))"
                + "::NUMBER(38,20)) FROM (SELECT 1 AS i, 1 AS k, 0.1::FLOAT AS x UNION ALL SELECT 2, 2, 0.3::FLOAT "
                + "UNION ALL SELECT 3, 2, 0.7::FLOAT) ORDER BY i"));
        assertEquals("1, " + CORRECTED + " | 2, " + CORRECTED + " | 3, 1.15999999999999969802 | 4, 1.15999999999999969802",
            rows("SELECT i, TO_VARCHAR((SUM(x * x) OVER (ORDER BY k))::NUMBER(38,20)) FROM (SELECT 1 AS i, 1 AS k, "
                + "0.3::FLOAT AS x UNION ALL SELECT 2, 1, 0.7::FLOAT UNION ALL SELECT 3, 2, 0.3::FLOAT "
                + "UNION ALL SELECT 4, 2, 0.7::FLOAT) ORDER BY i"));
    }
}
