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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

/**
 * An ORDER BY position counts to a star's column, and over two relations sharing a name it sorts by the column at
 * that position, not by the first column of its name (documented; live-verified).
 */
public class StarOrdinalOrderTest extends BaseDatabaseTest {

    private static final String BY_B_X = "3, 3, 10, 3 | 2, 2, 20, 2 | 1, 1, 30, 1";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE oa (x INT, k INT)");
        engine.execute("CREATE TABLE ob (x INT, k INT)");
        engine.execute("INSERT INTO oa VALUES (1, 1), (2, 2), (3, 3)");
        engine.execute("INSERT INTO ob VALUES (30, 1), (20, 2), (10, 3)");
    }

    @Test
    public void aPositionSortsByTheColumnAtIt() {
        assertCells(engine, new String[][] {
            {"SELECT * FROM oa JOIN ob ON oa.k = ob.k ORDER BY 3", BY_B_X},
            {"SELECT * FROM oa JOIN ob ON oa.k = ob.k ORDER BY 3 DESC", "1, 1, 30, 1 | 2, 2, 20, 2 | 3, 3, 10, 3"},
            {"SELECT * FROM oa JOIN ob ON oa.k = ob.k ORDER BY 3 LIMIT 1", "3, 3, 10, 3"},
            {"SELECT * FROM oa JOIN ob ON oa.k = ob.k ORDER BY 4 DESC, 3", BY_B_X},
            {"SELECT * FROM oa, ob WHERE oa.k = ob.k ORDER BY 3", BY_B_X},
            {"SELECT DISTINCT * FROM oa JOIN ob ON oa.k = ob.k ORDER BY 3", BY_B_X},
            {"SELECT * FROM (SELECT * FROM oa JOIN ob ON oa.k = ob.k) ORDER BY 3", BY_B_X},
        });
    }

    @Test
    public void aPositionCountsEveryStarAndItem() {
        assertCells(engine, new String[][] {
            {"SELECT *, 1 AS one FROM oa JOIN ob ON oa.k = ob.k ORDER BY 3",
                "3, 3, 10, 3, 1 | 2, 2, 20, 2, 1 | 1, 1, 30, 1, 1"},
            {"SELECT * FROM oa JOIN ob USING (k) ORDER BY 3", "3, 3, 10 | 2, 2, 20 | 1, 1, 30"},
            {"SELECT ob.*, oa.* FROM oa JOIN ob ON oa.k = ob.k ORDER BY 3", "30, 1, 1, 1 | 20, 2, 2, 2 | 10, 3, 3, 3"},
            {"SELECT * FROM oa JOIN ob ON oa.k = ob.k JOIN oa AS oc ON oc.k = oa.k ORDER BY 5 DESC",
                "3, 3, 10, 3, 3, 3 | 2, 2, 20, 2, 2, 2 | 1, 1, 30, 1, 1, 1"},
        });
    }
}
