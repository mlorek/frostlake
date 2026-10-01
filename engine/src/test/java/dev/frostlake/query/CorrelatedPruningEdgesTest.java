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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where live answers or refuses a correlated subquery by what its plan can prune: a correlated filter the
 * relations' statistics settle, a membership limited to no row, an IN whose LIMIT stands beside a correlation
 * in the select list alone, and a table function reading the outer row. Every cell is live-verified.
 */
public class CorrelatedPruningEdgesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("CREATE OR REPLACE TABLE tt (id INT, value VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE e1 (id INT, v INT, a INT)");
        engine.execute("CREATE OR REPLACE TABLE e2 (id INT, v INT, a INT)");
        engine.execute("CREATE OR REPLACE TABLE ev (id INT, arr ARRAY)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("INSERT INTO tt VALUES (1, 'A'), (2, 'B')");
        engine.execute("INSERT INTO e1 VALUES (1, 10, 100)");
        engine.execute("INSERT INTO e2 VALUES (1, 10, 100), (2, 20, 200)");
        engine.execute("INSERT INTO ev SELECT 1, ARRAY_CONSTRUCT(1, 2) UNION ALL SELECT 2, ARRAY_CONSTRUCT(3)");
    }

    /** Every row's cells joined as the probe prints them, "(0 rows)" for none, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder rows = new StringBuilder();
            while (rs.next()) {
                rows.append(rows.length() > 0 ? " | " : "");
                for (int c = 0; c < rs.getColumns().size(); c++) {
                    rows.append(c > 0 ? ", " : "").append(String.valueOf(rs.getValue(c)));
                }
            }
            return rows.length() > 0 ? rows.toString() : "(0 rows)";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** A scalar subquery whose correlated filter the key ranges settle: FALSE reads no row, TRUE runs uncorrelated. */
    @Test
    public void aCorrelationTheStatisticsSettleLeavesNothingToPlan() {
        final String[][] cells = {
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id > fz.id) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id AND tt.id = 1) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id < fz.id) AS x FROM fz ORDER BY id",
                "Single-row subquery returns more than one row."},
            {"SELECT id, (SELECT v FROM g WHERE g.id = fz.id) AS x FROM fz ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 12"},
            {"SELECT id FROM fz WHERE id = (SELECT id FROM tt WHERE tt.id = fz.id) ORDER BY id",
                "(0 rows)"},
            {"SELECT id, (SELECT value FROM tt t2 WHERE t2.id = fz.id) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id + 0) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt WHERE fz.id = tt.id) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id AND value = 'A') AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt JOIN g ON g.id = tt.id WHERE tt.id = fz.id) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id = fz.id ORDER BY 1) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
            {"SELECT id, (SELECT value FROM tt WHERE tt.id BETWEEN fz.id AND fz.id + 1) AS x FROM fz ORDER BY id",
                "5, null | 7, null"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** LIMIT 0 folds a membership away; under IN a LIMIT refuses only beside a correlated filter. */
    @Test
    public void anInSubqueryKeepsItsLimitApartFromItsCorrelation() {
        final String[][] cells = {
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g LIMIT 0) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g LIMIT 1) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT v + fz.id FROM g LIMIT 0) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id NOT IN (SELECT v + fz.id FROM g LIMIT 0) ORDER BY id",
                "5 | 7"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) FROM g WHERE g.id = fz.id LIMIT 0) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT v - 45 FROM g WHERE g.id <= fz.id LIMIT 1) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 31"},
            {"SELECT id FROM fz WHERE id IN (SELECT id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 31"},
            {"SELECT id FROM fz WHERE id IN (SELECT id FROM g WHERE g.id = fz.id LIMIT 5) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 31"},
            {"SELECT id FROM fz WHERE id NOT IN (SELECT id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 35"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT v + fz.id FROM g LIMIT 0) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT id FROM g WHERE g.id = fz.id LIMIT 1) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 24"},
            {"SELECT id FROM fz WHERE id IN (SELECT id FROM g WHERE g.id = fz.id ORDER BY v LIMIT 1) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 31"},
            {"SELECT id FROM fz WHERE id IN (SELECT TOP 1 id FROM g WHERE g.id = fz.id) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 31"},
            {"SELECT id FROM fz WHERE id IN (SELECT id FROM g WHERE g.id = fz.id FETCH FIRST 1 ROW ONLY) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 31"},
            {"SELECT id FROM fz WHERE id IN (SELECT id FROM g WHERE g.id = fz.id LIMIT 0) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT id FROM g WHERE g.id = fz.id LIMIT 0) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g GROUP BY id LIMIT 0) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g LIMIT 0 OFFSET 1) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT v + fz.id FROM g LIMIT 1) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g LIMIT 2) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT TOP 0 v + fz.id FROM g) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT v + fz.id FROM g FETCH FIRST 0 ROWS ONLY) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE id IN (SELECT MAX(v) + fz.id FROM g WHERE g.id = 5 LIMIT 1) ORDER BY id",
                "(0 rows)"},
            {"SELECT id FROM fz WHERE EXISTS (SELECT MAX(v) + fz.id FROM g LIMIT 0) ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 24"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** FLATTEN or SPLIT_TO_TABLE over an outer column inside a subquery, over two outer rows; one row reads. */
    @Test
    public void aTableFunctionReadingTheOuterRowIsRefused() {
        final String[][] cells = {
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a, 5)))) FROM e1 E",
                "2"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a, 5)))) FROM e2 E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT id FROM e2 E WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))))",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 26"},
            {"SELECT (SELECT MAX(value) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a)))) FROM e2 E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT value FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a)))) FROM e2 E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE(E.a::VARCHAR, ','))) FROM e2 E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT id FROM e2 E WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))) f WHERE f.value = 100)",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 26"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(input => ARRAY_CONSTRUCT(E.a))) f WHERE f.value > 0) FROM e2 E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(FLATTEN(E.arr))) FROM ev E ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT id FROM ev E WHERE EXISTS (SELECT 1 FROM TABLE(FLATTEN(E.arr)) f WHERE f.value = 3)",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 26"},
            {"SELECT (SELECT ARRAY_AGG(value) FROM TABLE(FLATTEN(E.arr))) FROM ev E ORDER BY 1",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT id FROM ev E WHERE 3 IN (SELECT value FROM TABLE(FLATTEN(E.arr)))",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 32"},
            {"SELECT id, (SELECT SUM(f.value) FROM LATERAL FLATTEN(E.arr) f) FROM ev E ORDER BY id",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 12"},
            {"SELECT id FROM ev E WHERE EXISTS (SELECT 1 FROM LATERAL FLATTEN(E.arr) f WHERE f.value = 3)",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 26"},
            {"SELECT (SELECT COUNT(*) FROM TABLE(SPLIT_TO_TABLE(E.id::VARCHAR, ','))) FROM ev E",
                "SQL compilation error:|Unsupported subquery type cannot be evaluated at line 1, position 8"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }
}
