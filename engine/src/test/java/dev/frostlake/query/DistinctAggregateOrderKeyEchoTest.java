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
 * An aggregate among the ORDER BY keys of a DISTINCT query that the select list does not hold is refused with
 * the aggregate echoed as the plan holds it: its columns qualified by their relation or its alias, a column the
 * select list publishes by its bare name, a cast and a CASE in the plan's spelling. Every answer is
 * live-verified.
 */
public class DistinctAggregateOrderKeyEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void theKeyAsThePlanHoldsIt() {
        engine.execute(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(FZ.ID)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(FZ.ID + 1)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(id + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | [COUNT(*)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY COUNT(*)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(FZ.ID)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(fz.id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(F.ID)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz f GROUP BY b ORDER BY MAX(id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(FZ.ID)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(id) + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | [SUM(FZ.ID * 2)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY SUM(id * 2) DESC");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(UPPER(CAST(FZ.ID AS VARCHAR(134217728))))] is not a valid "
                + "order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(UPPER(id::VARCHAR))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(CASE_FLATTENED(CAST(B AS BOOLEAN), FZ.ID, "
                + "SYSTEM$NULL_TO_FIXED(null)))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(CASE WHEN b THEN id END)");
        assertAnswer(
            "REFUSED SQL compilation error: | [COUNT(DISTINCT FZ.ID)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY COUNT(DISTINCT id)");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz ORDER BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz ORDER BY id + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(FZ.ID)] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(\"ID\")");
    }
}
