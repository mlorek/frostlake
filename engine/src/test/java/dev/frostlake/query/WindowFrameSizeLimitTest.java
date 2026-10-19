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
 * A ROWS frame of more than a thousand rows is refused while the statement compiles, whatever rows it reads: {@code
 * Unsupported window frame: window frame size of 1001 rows exceeds limit (1000 rows)}, the size counting both bounds
 * and the rows between, at the frame's first word, or at the OVER where the frame ends FOLLOWING. A frame of a
 * thousand rows runs, an UNBOUNDED bound has no size, a RANGE frame no limit, and ARRAY_AGG takes any frame; a frame
 * with no ORDER BY is refused for that first (live-verified).
 */
public class WindowFrameSizeLimitTest extends MeasuredCellsTestSupport {

    @Test
    public void aFrameOfMoreThanAThousandRowsIsRefused() {
        engine.execute("CREATE OR REPLACE TABLE ev (v INT, d DATE)");
        engine.execute("INSERT INTO ev VALUES (1, '2024-01-01'), (2, '2024-01-02'), (3, '2024-01-03')");
        assertCells(new String[][] {
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 99999999999 PRECEDING AND CURRENT ROW) AS s FROM "
                + "ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unsupported window frame: window "
                + "frame size of 100000000000 rows exceeds limit (1000 rows)"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 999 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER BY v",
                "1|1 ;; 2|3 ;; 3|6"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 500 PRECEDING AND 499 FOLLOWING) AS s FROM ev ORDER BY v",
                "1|6 ;; 2|6 ;; 3|6"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 500 PRECEDING AND 500 FOLLOWING) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 17 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND 1 PRECEDING) AS s FROM ev ORDER BY v",
                "1|null ;; 2|1 ;; 3|3"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1001 PRECEDING AND 1 PRECEDING) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1001 PRECEDING AND 2 PRECEDING) AS s FROM ev ORDER BY v",
                "1|null ;; 2|null ;; 3|1"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND 999 FOLLOWING) AS s FROM ev ORDER BY v",
                "1|6 ;; 2|5 ;; 3|3"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN CURRENT ROW AND 1000 FOLLOWING) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 17 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 1000 FOLLOWING) AS s FROM ev ORDER BY v",
                "1|5 ;; 2|3 ;; 3|null"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 FOLLOWING AND 1001 FOLLOWING) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 17 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS 999 PRECEDING) AS s FROM ev ORDER BY v", "1|1 ;; 2|3 ;; 3|6"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS 1000 PRECEDING) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN UNBOUNDED PRECEDING AND 1000 FOLLOWING) AS s FROM "
                + "ev ORDER BY v",
                "1|6 ;; 2|6 ;; 3|6"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND UNBOUNDED FOLLOWING) AS s FROM "
                + "ev ORDER BY v",
                "1|6 ;; 2|6 ;; 3|6"},
            {"SELECT v, SUM(v) OVER (ORDER BY v RANGE BETWEEN 100000 PRECEDING AND CURRENT ROW) AS s FROM ev "
                + "ORDER BY v",
                "1|1 ;; 2|3 ;; 3|6"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 2000 PRECEDING AND 2000 PRECEDING) AS s FROM ev "
                + "ORDER BY v",
                "1|null ;; 2|null ;; 3|null"},
            {"SELECT v, SUM(v) OVER (ORDER BY v ROWS BETWEEN 99999999999 PRECEDING AND 99999999999 FOLLOWING) "
                + "AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 17 | Unsupported window frame: window "
                + "frame size of 199999999999 rows exceeds limit (1000 rows)"},
            {"SELECT v, AVG(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, MIN(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, MAX(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 34 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, COUNT(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) AS s FROM ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 36 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, FIRST_VALUE(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) AS s FROM "
                + "ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 42 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, LAST_VALUE(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) AS s FROM "
                + "ev ORDER BY v",
                "REFUSED SQL compilation error: error line 1 at position 41 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT v, ARRAY_AGG(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) AS s FROM ev "
                + "ORDER BY v",
                "1|[1] ;; 2|[1,2] ;; 3|[1,2,3]"},
            {"SELECT SUM(v) OVER (PARTITION BY v ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: error line 1 at position 46 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1 PRECEDING AND CURRENT ROW), SUM(v) OVER (ORDER BY v "
                + "ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: error line 1 at position 98 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT SUM(v) OVER (ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: error line 1 at position 14 | Window frame requires an ORDER BY "
                + "clause."},
            {"SELECT SUM(v) OVER (ORDER BY v\n  ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) FROM ev",
                "REFUSED SQL compilation error: error line 2 at position 2 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT 1 FROM ev QUALIFY SUM(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) > 0",
                "REFUSED SQL compilation error: error line 1 at position 49 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
            {"SELECT SUM(v) OVER (ORDER BY v ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW) FROM ev WHERE FALSE",
                "REFUSED SQL compilation error: error line 1 at position 31 | Unsupported window frame: window "
                + "frame size of 1001 rows exceeds limit (1000 rows)"},
        });
    }
}
