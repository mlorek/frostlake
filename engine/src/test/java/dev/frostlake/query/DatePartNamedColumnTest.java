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
 * A column named like a date part — YEAR, MONTH, DAY — is held to the grouping like any other column, inside any
 * call: only a date function's unit slot reads the word as a unit (DATEADD's, DATEDIFF's, DATE_TRUNC's and
 * DATE_PART's first argument, LAST_DAY's second), so {@code DATEADD(day, day, d)} reads its second {@code day} as
 * the column (live-verified).
 */
public class DatePartNamedColumnTest extends BaseDatabaseTest {

    private static final String NOT_GROUPED = "SQL compilation error:|[SALES.YEAR] is not a valid group by expression";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sales (region VARCHAR, year INT, month INT, day INT, amount INT)");
        engine.execute("INSERT INTO sales VALUES ('n', 2023, 1, 5, 100), ('n', 2024, 2, 6, 200),"
            + " ('s', 2024, 3, 7, 300), ('s', 2025, 12, 8, 400)");
    }

    @Test
    public void aDatePartNamedColumnInACallIsHeldToTheGrouping() {
        assertCells(engine, new String[][] {
            {"SELECT region, SUM(amount) total, MAX(year) OVER () latest_year FROM sales GROUP BY region"
                + " ORDER BY region", NOT_GROUPED},
            {"SELECT region, ABS(year) y FROM sales GROUP BY region ORDER BY region",
                "SQL compilation error: error line 1 at position 19|'SALES.YEAR' in select clause is neither an"
                    + " aggregate nor in the group by clause."},
            {"SELECT COUNT(*) n, COALESCE(day, 0) d FROM sales",
                "SQL compilation error:|[SALES.DAY] is not a valid group by expression"},
            {"SELECT region, COUNT(*) n FROM sales GROUP BY region QUALIFY MAX(year) OVER () > 2024 ORDER BY region",
                NOT_GROUPED},
            {"SELECT region, SUM(amount) total FROM sales GROUP BY region HAVING ABS(year) > 2000 ORDER BY region",
                NOT_GROUPED},
            {"SELECT region, COALESCE(month, 0) m FROM sales GROUP BY region",
                "SQL compilation error: error line 1 at position 24|'SALES.MONTH' in select clause is neither an"
                    + " aggregate nor in the group by clause."},
        });
    }

    @Test
    public void aUnitSlotReadsTheWordAsAUnit() {
        assertCells(engine, new String[][] {
            {"SELECT region, DATEADD(day, 1, '2024-01-01'::DATE) d, COUNT(*) FROM sales GROUP BY region"
                + " ORDER BY region", "n, 2024-01-02, 2 | s, 2024-01-02, 2"},
            {"SELECT region, DATEADD(day, MAX(day), '2024-01-01'::DATE) d FROM sales GROUP BY region ORDER BY region",
                "n, 2024-01-07 | s, 2024-01-09"},
            {"SELECT region, DATE_TRUNC(month, MAX('2024-05-17'::DATE)) d FROM sales GROUP BY region ORDER BY region",
                "n, 2024-05-01 | s, 2024-05-01"},
            {"SELECT region, DATE_PART(year, '2024-05-17'::DATE) e, COUNT(*) FROM sales GROUP BY region"
                + " ORDER BY region", "n, 2024, 2 | s, 2024, 2"},
            {"SELECT region, LAST_DAY('2024-05-17'::DATE, month) e, COUNT(*) FROM sales GROUP BY region"
                + " ORDER BY region", "n, 2024-05-31, 2 | s, 2024-05-31, 2"},
            {"SELECT region, DATEDIFF(day, '2024-01-01'::DATE, '2024-02-01'::DATE) e, COUNT(*) FROM sales"
                + " GROUP BY region ORDER BY region", "n, 31, 2 | s, 31, 2"},
            {"SELECT region, MAX(year) y FROM sales GROUP BY region ORDER BY region", "n, 2024 | s, 2025"},
            {"SELECT ABS(year), COUNT(*) FROM sales GROUP BY ABS(year) ORDER BY 1", "2023, 1 | 2024, 2 | 2025, 1"},
        });
    }

    @Test
    public void theSameWordBesideTheSlotIsTheColumn() {
        assertCells(engine, new String[][] {
            {"SELECT region, DATEADD(day, day, '2024-01-01'::DATE) d FROM sales GROUP BY region ORDER BY region",
                "SQL compilation error: error line 1 at position 28|'SALES.DAY' in select clause is neither an"
                    + " aggregate nor in the group by clause."},
        });
    }
}
