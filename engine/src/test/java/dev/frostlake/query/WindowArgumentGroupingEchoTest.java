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
 * What a window call names when it reads a column that is not grouped: the argument, the PARTITION BY key or
 * the ORDER BY key as a whole, printed as the plan holds it, once it is more than the column itself — beside
 * an aggregate and under a GROUP BY alike — while a select item that is no window call names the column
 * (live-verified).
 */
public class WindowArgumentGroupingEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void anExpressionIsNamedWhole() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + 1] is not a valid group by expression",
            "SELECT MAX(id), MAX(id + 1) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(FZ.B AS NUMBER(2,0)))] is not a valid group by "
                + "expression",
            "SELECT MAX(id), MAX(id + b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [ABS(FZ.ID)] is not a valid group by expression",
            "SELECT MAX(id), MAX(ABS(id)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + 1] is not a valid group by expression",
            "SELECT MAX(id), ROW_NUMBER() OVER (ORDER BY id + 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + 1] is not a valid group by expression",
            "SELECT MAX(id), ROW_NUMBER() OVER (PARTITION BY id + 1 ORDER BY b) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), MAX(id) OVER (ORDER BY id + 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + 1] is not a valid group by expression",
            "SELECT MAX(id), LAG(id + 1) OVER (ORDER BY 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (MAX(FZ.ID))] is not a valid group by expression",
            "SELECT MAX(id), MAX(id + MAX(id)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CASE_FLATTENED(CAST(FZ.B AS BOOLEAN), FZ.ID, "
                + "SYSTEM$NULL_TO_FIXED(null))] is not a valid group by expression",
            "SELECT MAX(id), MAX(CASE WHEN b THEN id END) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(FZ.ID AS VARCHAR(134217728))] is not a valid group by "
                + "expression",
            "SELECT MAX(id), MAX(id::VARCHAR) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID * 2] is not a valid group by expression",
            "SELECT MAX(id), SUM(id * 2) OVER (PARTITION BY b) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [NEGATE(FZ.ID)] is not a valid group by expression",
            "SELECT MAX(id), MAX(-id) OVER () FROM fz");
    }

    @Test
    public void aColumnIsNamedItself() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), MAX(id) OVER (PARTITION BY b, id) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), COUNT(DISTINCT id) OVER () FROM fz");
        assertAnswer(
            "7|2",
            "SELECT MAX(id), MAX(1 + 1) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), MAX(fz.id) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [F.ID] is not a valid group by expression",
            "SELECT MAX(id), MAX(f.id) OVER () FROM fz f");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), id + 1 FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), ABS(id) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), id + b::INT FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid group by expression",
            "SELECT MAX(id), id FROM fz");
    }

    @Test
    public void underAGroupBy() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + 1] is not a valid group by expression",
            "SELECT b, MAX(id + 1) OVER () FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + 1] is not a valid group by expression",
            "SELECT b, ROW_NUMBER() OVER (ORDER BY id + 1) FROM fz GROUP BY b");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 10 | 'FZ.ID' in select clause is neither an "
                + "aggregate nor in the group by clause.",
            "SELECT b, id + 1 FROM fz GROUP BY b");
    }
}
