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
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))] is not a "
                + "valid group by expression",
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

    /**
     * A BOOLEAN turned into a number is printed as the plan converts it: a column or any other value through
     * NUMBER(2,0) and then to the number asked for, a predicate as the BOOLEAN it yields and then NUMBER(2,0)
     * alone.
     */
    @Test
    public void aBooleanTurnsIntoANumberThroughNumberTwo() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE, 'x'), (7, FALSE, 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID * (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX(id * b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [(CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(5,0))) + FZ.ID]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX(b::NUMBER(5,0) + id) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX(id + b::NUMBER(38,0)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [(CAST(FZ.ID AS NUMBER(38,2))) + (CAST(CAST(FZ.B AS"
                + " NUMBER(2,0)) AS NUMBER(10,2)))] is not a valid group by expression",
            "SELECT MAX(id), MAX(id + b::NUMBER(10,2)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [ABS(CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))] is"
                + " not a valid group by expression",
            "SELECT MAX(id), MAX(ABS(b::INT)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [(CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))) +"
                + " (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))] is not a valid group by expression",
            "SELECT MAX(id), MAX(b::INT + b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(CAST(b AS INT)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(b::NUMBER) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(3,1))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(b::DECIMAL(3,1)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(TO_NUMBER(b)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.ID > 5 AS BOOLEAN) AS NUMBER(2,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX((id > 5)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.ID > 5 AS BOOLEAN) AS NUMBER(2,0)))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX(id + (id > 5)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(b::INT::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(FZ.B AS NUMBER(2,0))] is not a valid group by expression",
            "SELECT MAX(id), MAX(b::NUMBER(2,0)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [(CAST(FZ.B AS NUMBER(2,0))) + FZ.ID] is not a valid group"
                + " by expression",
            "SELECT MAX(id), MAX(b::NUMBER(2,0) + id) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID - (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX(id - b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [SCALED_ROUND_INT_DIVIDE(FZ.ID, CAST(CAST(FZ.B AS"
                + " NUMBER(2,0)) AS NUMBER(38,0)))] is not a valid group by expression",
            "SELECT MAX(id), MAX(id / b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [NEGATE(CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))] is"
                + " not a valid group by expression",
            "SELECT MAX(id), MAX(-b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [(CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))) % 2] is"
                + " not a valid group by expression",
            "SELECT MAX(id), MAX(b::INT % 2) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [IFF((CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))) > 0,"
                + " 1, 0)] is not a valid group by expression",
            "SELECT MAX(id), MAX(IFF(b::INT > 0, 1, 0)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(b::BOOLEAN::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [(FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))))"
                + " + 1] is not a valid group by expression",
            "SELECT MAX(id), MAX(id + b::INT + 1) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [(FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))))"
                + " * 2] is not a valid group by expression",
            "SELECT MAX(id), MAX((id + b::INT) * 2) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(1,0)))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX(id + b::NUMBER(1,0)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))]"
                + " is not a valid group by expression",
            "SELECT MAX(id) FROM fz QUALIFY MAX(id + b::INT) OVER () > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX(id + b::INT) OVER (ORDER BY 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), ROW_NUMBER() OVER (ORDER BY b::INT) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), ROW_NUMBER() OVER (PARTITION BY id + b::INT ORDER BY 1) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))]"
                + " is not a valid group by expression",
            "SELECT id FROM fz GROUP BY id HAVING MAX(id) > 0 QUALIFY MAX(id + b::INT) OVER () > 0");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.ID + (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))]"
                + " is not a valid group by expression",
            "SELECT id, MAX(id + b::INT) OVER () FROM fz GROUP BY id");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(5,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(TO_NUMBER(b, 5)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(10,2))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(TO_NUMBER(b, 10, 2)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(TO_DECIMAL(b)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(TO_NUMERIC(b)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(NOT(FZ.B) AS NUMBER(2,0)) AS NUMBER(38,0))] is"
                + " not a valid group by expression",
            "SELECT MAX(id), MAX((NOT b)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AND FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX((b AND b)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST((CAST(FZ.B AS BOOLEAN)) OR (FZ.ID > 5) AS"
                + " BOOLEAN) AS NUMBER(2,0))] is not a valid group by expression",
            "SELECT MAX(id), MAX((b OR id > 5)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.ID IS NULL AS BOOLEAN) AS NUMBER(2,0))] is not"
                + " a valid group by expression",
            "SELECT MAX(id), MAX((id IS NULL)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.ID IN (1, 2) AS BOOLEAN) AS NUMBER(2,0))] is"
                + " not a valid group by expression",
            "SELECT MAX(id), MAX((id IN (1, 2))::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST((FZ.ID >= 1) AND (FZ.ID <= 5) AS BOOLEAN) AS"
                + " NUMBER(2,0))] is not a valid group by expression",
            "SELECT MAX(id), MAX((id BETWEEN 1 AND 5)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(IFF(CAST(FZ.B AS BOOLEAN), TRUE, FALSE) AS"
                + " NUMBER(2,0)) AS NUMBER(38,0))] is not a valid group by expression",
            "SELECT MAX(id), MAX(IFF(b, TRUE, FALSE)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(EQUAL_NULL(FZ.ID, 5) AS BOOLEAN) AS NUMBER(2,0))]"
                + " is not a valid group by expression",
            "SELECT MAX(id), MAX(EQUAL_NULL(id, 5)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(CAST(FZ.ID <> 0 AS BOOLEAN) AS NUMBER(2,0)) AS"
                + " NUMBER(38,0))] is not a valid group by expression",
            "SELECT MAX(id), MAX(TO_BOOLEAN(id)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.ID > 5 AS BOOLEAN) AS NUMBER(2,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX((id > 5)::NUMBER(5,0)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.ID > 5 AS BOOLEAN) AS NUMBER(2,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX((id > 5)::NUMBER(2,0)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(2,1))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(b::NUMBER(2,1)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(3,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), MAX(b::NUMBER(3,0)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.S LIKE 'x' AS BOOLEAN) AS NUMBER(2,0))] is not"
                + " a valid group by expression",
            "SELECT MAX(id), MAX((s LIKE 'x')::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(NOT(FZ.ID > 5) AS BOOLEAN) AS NUMBER(2,0))] is"
                + " not a valid group by expression",
            "SELECT MAX(id), MAX((NOT id > 5)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST((FZ.ID > 5) AND (FZ.ID < 9) AS BOOLEAN) AS"
                + " NUMBER(2,0))] is not a valid group by expression",
            "SELECT MAX(id), MAX((id > 5 AND id < 9)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [(CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))) * 2] is"
                + " not a valid group by expression",
            "SELECT MAX(id), MAX(b::INT * 2) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [2 * (CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)))] is"
                + " not a valid group by expression",
            "SELECT MAX(id), MAX(2 * b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0)) AS"
                + " NUMBER(5,0))] is not a valid group by expression",
            "SELECT MAX(id), MAX(b::INT::NUMBER(5,0)) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(NVL(FZ.B, FALSE) AS NUMBER(2,0)) AS"
                + " NUMBER(38,0))] is not a valid group by expression",
            "SELECT MAX(id), MAX(NVL(b, FALSE)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.ID = FZ.ID AS BOOLEAN) AS NUMBER(2,0))] is not"
                + " a valid group by expression",
            "SELECT MAX(id), MAX((id = id)::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT MAX(id) FROM fz HAVING MAX(id) > b::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT id FROM fz GROUP BY id HAVING SUM(id) > MAX(b::INT) + b::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), SUM(b::INT) OVER () FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: | [CAST(CAST(FZ.B AS NUMBER(2,0)) AS NUMBER(38,0))] is not a"
                + " valid group by expression",
            "SELECT MAX(id), COUNT(b::INT) OVER () FROM fz");
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
