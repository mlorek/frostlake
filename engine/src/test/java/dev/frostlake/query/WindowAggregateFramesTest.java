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

import static dev.frostlake.query.QueryAnswers.assertCells;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * A window aggregate answers each row's frame — the whole partition, a running frame, a moving ROWS frame, a RANGE
 * frame by an offset — as it always did, NULLs, NaN and an infinite FLOAT sum included (live-verified), and reads a
 * large partition's frames without adding each frame up again for every row: the whole-partition, running and RANGE
 * frames of 20 000 rows take time that grows with the rows, not with their square.
 */
public class WindowAggregateFramesTest extends BaseDatabaseTest {

    private static final String BIG = "CREATE TABLE big AS SELECT SEQ4() AS i, MOD(SEQ4() * 7, 100) + 1 AS v,"
        + " MOD(SEQ4(), 50) AS p FROM TABLE(GENERATOR(ROWCOUNT => %d))";

    @Test
    public void everyFrameAnswersAsBefore() {
        engine.execute(String.format(BIG, 2000));
        assertCells(engine, new String[][] {
            {"SELECT SUM(s) FROM (SELECT SUM(v) OVER () s FROM big)", "202000000"},
            {"SELECT SUM(s) FROM (SELECT SUM(v) OVER (ORDER BY i) s FROM big)", "100826000"},
            {"SELECT SUM(s) FROM (SELECT MEDIAN(v) OVER (PARTITION BY p) s FROM big)", "101000.000"},
            {"SELECT SUM(s) FROM (SELECT SUM(v) OVER (ORDER BY v RANGE BETWEEN 5 PRECEDING AND 5 FOLLOWING) s"
                + " FROM big)", "21614000"},
            {"SELECT SUM(s), COUNT(s) FROM (SELECT AVG(v) OVER (PARTITION BY p ORDER BY i) s FROM big)",
                "100504.065344, 2000"},
            {"SELECT SUM(s) FROM (SELECT MIN(v) OVER (PARTITION BY p ORDER BY i ROWS BETWEEN UNBOUNDED PRECEDING"
                + " AND CURRENT ROW) s FROM big)", "52050"},
            {"SELECT SUM(s) FROM (SELECT MAX(v) OVER (PARTITION BY p ORDER BY i ROWS BETWEEN CURRENT ROW AND"
                + " UNBOUNDED FOLLOWING) s FROM big)", "149950"},
            {"SELECT SUM(s) FROM (SELECT COUNT(v) OVER (ORDER BY v RANGE BETWEEN 10 PRECEDING AND CURRENT ROW) s"
                + " FROM big)", "418000"},
            {"SELECT SUM(s) FROM (SELECT SUM(v) OVER (PARTITION BY p ORDER BY v RANGE BETWEEN CURRENT ROW AND"
                + " 3 FOLLOWING) s FROM big)", "2020000"},
            {"SELECT SUM(s) FROM (SELECT AVG(v) OVER (ORDER BY i ROWS BETWEEN 10 PRECEDING AND 10 FOLLOWING) s"
                + " FROM big)", "101005.073"},
            {"SELECT SUM(s) FROM (SELECT SUM(v) OVER (PARTITION BY p ORDER BY v) s FROM big)", "2530000"},
            {"SELECT SUM(s) FROM (SELECT COUNT(DISTINCT v) OVER (PARTITION BY p) s FROM big)", "4000"},
        });
    }

    @Test
    public void nullsNanAndAnInfiniteSumKeepTheirAnswers() {
        engine.execute("CREATE TABLE wn (g INT, o INT, x FLOAT, y INT)");
        engine.execute("INSERT INTO wn VALUES (1, 1, 1.5, NULL), (1, 2, NULL, 3), (1, 3, 2.5, 4), (1, 4, 'NaN', 5),"
            + " (2, 1, NULL, NULL), (2, 2, 1e308, 1), (2, 3, 1e308, 2)");
        assertCells(engine, new String[][] {
            {"SELECT g, o, SUM(x) OVER (PARTITION BY g ORDER BY o) s, AVG(y) OVER (PARTITION BY g ORDER BY o) a,"
                + " MIN(y) OVER (PARTITION BY g) mn, MAX(x) OVER (PARTITION BY g ORDER BY o ROWS BETWEEN 1 PRECEDING"
                + " AND CURRENT ROW) mx FROM wn ORDER BY g, o",
                "1, 1, 1.5, null, 3, 1.5 | 1, 2, 1.5, 3.000000, 3, 1.5 | 1, 3, 4.0, 3.500000, 3, 2.5"
                    + " | 1, 4, NaN, 4.000000, 3, NaN | 2, 1, null, null, 1, null | 2, 2, 1.0E308, 1.000000, 1, 1.0E308"
                    + " | 2, 3, Infinity, 1.500000, 1, 1.0E308"},
            {"SELECT g, o, SUM(y) OVER (PARTITION BY g ORDER BY o RANGE BETWEEN 1 PRECEDING AND 1 FOLLOWING) s,"
                + " COUNT(x) OVER (PARTITION BY g ORDER BY o RANGE BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING) c"
                + " FROM wn ORDER BY g, o",
                "1, 1, 3, 0 | 1, 2, 7, 1 | 1, 3, 12, 1 | 1, 4, 9, 2 | 2, 1, 1, 0 | 2, 2, 3, 0 | 2, 3, 3, 1"},
            {"SELECT g, o, SUM(y) OVER (PARTITION BY g ORDER BY o DESC RANGE BETWEEN 1 PRECEDING AND CURRENT ROW) s"
                + " FROM wn ORDER BY g, o", "1, 1, 3 | 1, 2, 7 | 1, 3, 9 | 1, 4, 5 | 2, 1, 1 | 2, 2, 3 | 2, 3, 2"},
            {"SELECT g, o, SUM(y) OVER (PARTITION BY g ORDER BY y NULLS FIRST RANGE BETWEEN 1 PRECEDING AND CURRENT"
                + " ROW) s FROM wn ORDER BY g, o",
                "1, 1, null | 1, 2, 3 | 1, 3, 7 | 1, 4, 9 | 2, 1, null | 2, 2, 1 | 2, 3, 3"},
        });
    }

    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void aLargePartitionIsReadOnce() {
        assumeFalse(isLiveSnowflake(), "the time is the embedded engine's own");
        engine.execute(String.format(BIG, 20000));
        assertCells(engine, new String[][] {
            {"SELECT SUM(s) FROM (SELECT SUM(v) OVER () s FROM big)", "20200000000"},
            {"SELECT SUM(s) FROM (SELECT SUM(v) OVER (ORDER BY i) s FROM big)", "10098260000"},
            {"SELECT SUM(s) FROM (SELECT MEDIAN(v) OVER (PARTITION BY p) s FROM big)", "1010000.000"},
            {"SELECT SUM(s) FROM (SELECT SUM(v) OVER (ORDER BY v RANGE BETWEEN 5 PRECEDING AND 5 FOLLOWING) s"
                + " FROM big)", "2161400000"},
            {"SELECT SUM(s) FROM (SELECT COUNT(v) OVER (ORDER BY i) s FROM big)", "200010000"},
        });
    }
}
