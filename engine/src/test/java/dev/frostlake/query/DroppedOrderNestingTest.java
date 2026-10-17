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

import org.junit.jupiter.api.Test;

/**
 * The ORDER BY of an aggregate query with no GROUP BY, or under a GROUP BY ALL, is dropped, but read first: an
 * aggregate in it over a select alias that is an aggregate nests the two, refused as {@code Aggregate functions cannot
 * be nested: [MAX(FZ.ID)] nested in [MAX(X)]}, the key printed as the plan holds it; a window over the alias nests
 * nothing; and a name nothing resolves inside a window call of the clause is an invalid identifier at the name (all
 * live-verified).
 */
public class DroppedOrderNestingTest extends MeasuredCellsTestSupport {

    @Test
    public void theDroppedClauseIsReadFirst() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"SELECT MAX(id) x FROM fz ORDER BY MAX(x / 0)",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(SCALED_ROUND_INT_DIVIDE(X, 0))]"},
            {"SELECT MAX(id) x FROM fz GROUP BY ALL ORDER BY MAX(x)",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(X)]"},
            {"SELECT MAX(id) x FROM fz GROUP BY ALL ORDER BY MAX(x / 0)",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(SCALED_ROUND_INT_DIVIDE(X, 0))]"},
            {"SELECT MAX(id) x FROM fz ORDER BY MAX(x)",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MAX(X)]"},
            {"SELECT MAX(id) x FROM fz ORDER BY MAX(x) OVER ()", "7"},
            {"SELECT MAX(id) x FROM fz ORDER BY SUM(x) OVER ()", "7"},
            {"SELECT MAX(id) x FROM fz ORDER BY MIN(x + 1)",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in "
                + "[MIN(X + 1)]"},
            {"SELECT SUM(id) s FROM fz ORDER BY COUNT(s)",
                "REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [SUM(FZ.ID)] nested in "
                + "[COUNT(S)]"},
            {"SELECT MAX(id) FROM fz ORDER BY MAX(nosuch) OVER ()",
                "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'"},
            {"SELECT MAX(id) FROM fz ORDER BY nosuch, MAX(id) OVER ()",
                "REFUSED SQL compilation error: error line 1 at position 32 | invalid identifier 'NOSUCH'"},
            {"SELECT ROW_NUMBER() OVER (ORDER BY COUNT(*)) FROM fz WHERE FALSE GROUP BY ALL", "1"},
        });
    }
}
