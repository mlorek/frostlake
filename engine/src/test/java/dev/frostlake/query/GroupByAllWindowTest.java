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
 * Two refusals around windows and grouping (live-verified): under GROUP BY ALL a select item computed over a
 * window, with no aggregate inside it, is no valid group by expression, named by its alias or as written; and an
 * implicitly aggregated query's ORDER BY window naming a column that does not exist is an invalid identifier
 * where the name stands.
 */
public class GroupByAllWindowTest extends MeasuredCellsTestSupport {

    @Test
    public void aWindowItemUnderGroupByAllIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"SELECT MAX(id) FROM fz ORDER BY SUM(id) OVER (PARTITION BY nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 59 | invalid identifier 'NOSUCH'"},
            {"SELECT MAX(id) FROM fz ORDER BY SUM(id) OVER (ORDER BY nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 55 | invalid identifier 'NOSUCH'"},
            {"SELECT MAX(id) FROM fz ORDER BY RANK() OVER (PARTITION BY nosuch ORDER BY id)",
                "REFUSED SQL compilation error: error line 1 at position 58 | invalid identifier 'NOSUCH'"},
            {"SELECT MAX(id) FROM fz ORDER BY SUM(id) OVER (PARTITION BY b)",
                "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression"},
            {"SELECT MAX(id) FROM fz ORDER BY SUM(nosuch) OVER (PARTITION BY id)",
                "REFUSED SQL compilation error: error line 1 at position 36 | invalid identifier 'NOSUCH'"},
            {"SELECT COUNT(*), ROW_NUMBER() OVER (ORDER BY 1) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [ROW_NUMBER() OVER (ORDER BY 1)] is not a valid group by expression"},
            {"SELECT COUNT(*), ROW_NUMBER() OVER (ORDER BY 1) FROM fz WHERE FALSE GROUP BY ALL",
                "REFUSED SQL compilation error: | [ROW_NUMBER() OVER (ORDER BY 1)] is not a valid group by expression"},
            {"SELECT COUNT(*), RANK() OVER (ORDER BY 1) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [RANK() OVER (ORDER BY 1)] is not a valid group by expression"},
            {"SELECT COUNT(*) c, ROW_NUMBER() OVER (ORDER BY 1) r FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [R] is not a valid group by expression"},
            {"SELECT COUNT(*), DENSE_RANK() OVER (ORDER BY 1) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [DENSE_RANK() OVER (ORDER BY 1)] is not a valid group by expression"},
            {"SELECT COUNT(*), ROW_NUMBER() OVER () FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | Window function type [ROW_NUMBER] requires ORDER BY in window "
                + "specification."},
            {"SELECT COUNT(*), NTILE(2) OVER (ORDER BY 1) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [NTILE(2) OVER (ORDER BY 1)] is not a valid group by expression"},
            {"SELECT COUNT(*), SUM(1) OVER () FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [SUM(1) OVER ()] is not a valid group by expression"},
            {"SELECT COUNT(*), 1 + ROW_NUMBER() OVER (ORDER BY 1) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [1 + ROW_NUMBER() OVER (ORDER BY 1)] is not a valid group by "
                + "expression"},
            {"SELECT id, ROW_NUMBER() OVER (ORDER BY id) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [ROW_NUMBER() OVER (ORDER BY ID)] is not a valid group by "
                + "expression"},
            {"SELECT id, COUNT(*), ROW_NUMBER() OVER (ORDER BY 1) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [ROW_NUMBER() OVER (ORDER BY 1)] is not a valid group by expression"},
            {"SELECT ROW_NUMBER() OVER (ORDER BY 1) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [ROW_NUMBER() OVER (ORDER BY 1)] is not a valid group by expression"},
            {"SELECT COUNT(*), ROW_NUMBER() OVER (ORDER BY 1) AS \"r\" FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [r] is not a valid group by expression"},
            {"SELECT COUNT(*), ROW_NUMBER() OVER (ORDER BY 1) FROM fz GROUP BY id", "1|1 ;; 1|2"},
            {"SELECT COUNT(*), CUME_DIST() OVER (ORDER BY 1) FROM fz GROUP BY ALL",
                "REFUSED SQL compilation error: | [CUME_DIST() OVER (ORDER BY 1)] is not a valid group by expression"},
        });
    }
}
