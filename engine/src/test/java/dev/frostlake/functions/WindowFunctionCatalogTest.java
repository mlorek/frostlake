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
package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * ROW_NUMBER, LAG, LEAD, FIRST_VALUE, LAST_VALUE and NTH_VALUE: what each answers in a partition, with an
 * offset, a default and IGNORE NULLS, the type each declares, and the refusal of a window with no ORDER BY
 * (live-verified).
 */
public class WindowFunctionCatalogTest extends MeasuredCellsTestSupport {

    @Test
    public void rowNumbersAndNeighbours() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        assertAnswer(
            "1|1.00|1 ;; 1|2.00|2 ;; 2|3.00|1 ;; 2|5.00|2 ;; 2|null|3",
            "SELECT g, x, ROW_NUMBER() OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS "
                + "LAST");
        assertAnswer(
            "NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(ROW_NUMBER() OVER (ORDER BY x)) FROM ta LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Window function type [ROW_NUMBER] requires ORDER BY in window "
                + "specification.",
            "SELECT ROW_NUMBER() OVER () FROM ta");
        assertAnswer(
            "REFUSED SQL compilation error: | Window function type [ROW_NUMBER] requires ORDER BY in window "
                + "specification.",
            "SELECT ROW_NUMBER() OVER (PARTITION BY g) FROM ta");
        assertAnswer(
            "REFUSED SQL compilation error: | Missing window specification for function [ROW_NUMBER()].",
            "SELECT ROW_NUMBER() FROM ta");
        assertAnswer(
            "1|1.00|null ;; 1|2.00|1.00 ;; 2|3.00|null ;; 2|5.00|3.00 ;; 2|null|5.00",
            "SELECT g, x, LAG(x) OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS LAST");
        assertAnswer(
            "1|1.00|2.00 ;; 1|2.00|null ;; 2|3.00|5.00 ;; 2|5.00|null ;; 2|null|null",
            "SELECT g, x, LEAD(x) OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS LAST");
        assertAnswer(
            "1|1.00|-1.00 ;; 1|2.00|-1.00 ;; 2|3.00|-1.00 ;; 2|5.00|-1.00 ;; 2|null|3.00",
            "SELECT g, x, LAG(x, 2, -1) OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS "
                + "LAST");
        assertAnswer(
            "1|1.00|2.00 ;; 1|2.00|0.00 ;; 2|3.00|5.00 ;; 2|5.00|null ;; 2|null|0.00",
            "SELECT g, x, LEAD(x, 1, 0) OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS "
                + "LAST");
        assertAnswer(
            "1|1.00|null ;; 1|2.00|1.00 ;; 2|3.00|null ;; 2|null|3.00 ;; 2|5.00|3.00",
            "SELECT g, x, LAG(x) IGNORE NULLS OVER (PARTITION BY g ORDER BY y NULLS LAST) FROM ta ORDER BY g, y "
                + "NULLS LAST");
        assertAnswer(
            "NUMBER(10,2)[SB2]",
            "SELECT SYSTEM$TYPEOF(LAG(x) OVER (ORDER BY x)) FROM ta LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Window function type [LAG] requires ORDER BY in window "
                + "specification.",
            "SELECT LAG(x) OVER () FROM ta");
        assertAnswer(
            "1|1.00|1.00 ;; 1|2.00|2.00 ;; 2|3.00|3.00 ;; 2|5.00|5.00 ;; 2|null|null",
            "SELECT g, x, LAG(x, 0) OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS LAST");
    }

    @Test
    public void valuesOfTheFrame() {
        answer(
            "CREATE OR REPLACE TABLE ta (g INT, x NUMBER(10,2), y NUMBER(10,2), s VARCHAR, b INT)");
        answer(
            "INSERT INTO ta VALUES (1, 1.0, 2.0, 'a', 12), (1, 2.0, 4.5, 'b', 10), (2, 3.0, 5.0, NULL, 6), (2, "
                + "NULL, 7.0, 'c', NULL), (2, 5.0, NULL, 'a', 3)");
        answer(
            "CREATE OR REPLACE TABLE te (x NUMBER(10,2), y NUMBER(10,2), b INT)");
        assertAnswer(
            "1|1.00|1.00 ;; 1|2.00|1.00 ;; 2|3.00|3.00 ;; 2|5.00|3.00 ;; 2|null|3.00",
            "SELECT g, x, FIRST_VALUE(x) OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS "
                + "LAST");
        assertAnswer(
            "1|1.00|2.00 ;; 1|2.00|2.00 ;; 2|3.00|null ;; 2|5.00|null ;; 2|null|null",
            "SELECT g, x, LAST_VALUE(x) OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS "
                + "LAST");
        assertAnswer(
            "1|1.00|2.00 ;; 1|2.00|2.00 ;; 2|3.00|5.00 ;; 2|5.00|5.00 ;; 2|null|5.00",
            "SELECT g, x, NTH_VALUE(x, 2) OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY g, x NULLS "
                + "LAST");
        assertAnswer(
            "1|1.00|1.00 ;; 1|2.00|2.00 ;; 2|3.00|3.00 ;; 2|5.00|5.00 ;; 2|null|null",
            "SELECT g, x, LAST_VALUE(x) OVER (PARTITION BY g ORDER BY x NULLS LAST ROWS BETWEEN UNBOUNDED "
                + "PRECEDING AND CURRENT ROW) FROM ta ORDER BY g, x NULLS LAST");
        assertAnswer(
            "1|1.00|2.00 ;; 1|2.00|2.00 ;; 2|3.00|5.00 ;; 2|5.00|5.00 ;; 2|null|5.00",
            "SELECT g, x, LAST_VALUE(x) IGNORE NULLS OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY "
                + "g, x NULLS LAST");
        assertAnswer(
            "1|1.00|1.00 ;; 1|2.00|1.00 ;; 2|3.00|5.00 ;; 2|5.00|5.00 ;; 2|null|5.00",
            "SELECT g, x, NTH_VALUE(x, 2) FROM LAST OVER (PARTITION BY g ORDER BY x NULLS LAST) FROM ta ORDER BY "
                + "g, x NULLS LAST");
        assertAnswer(
            "NUMBER(10,2)[SB2]",
            "SELECT SYSTEM$TYPEOF(FIRST_VALUE(x) OVER (ORDER BY x)) FROM ta LIMIT 1");
        assertAnswer(
            "REFUSED SQL compilation error: | Window function type [FIRST_VALUE] requires ORDER BY in window "
                + "specification.",
            "SELECT FIRST_VALUE(x) OVER () FROM ta");
    }
}
