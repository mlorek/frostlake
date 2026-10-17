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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A PIVOT refuses an aggregate its column's type does not take, as a grouped query does but at no position of the
 * statement: SUM and AVG over a BOOLEAN, both named SUM, and MIN or MAX over an ARRAY. The statistics and bit
 * aggregates declare the same type pivoted as grouped (live-verified).
 */
public class PivotAggregateArgumentTypeTest extends MeasuredCellsTestSupport {

    private static final String SUM_OVER_BOOLEAN = "REFUSED SQL compilation error: error line 0 at position -1"
        + " | Invalid argument types for function 'SUM': (BOOLEAN)";

    @BeforeEach
    public void flags() {
        answer("CREATE OR REPLACE TABLE flags1459 (student VARCHAR, subject VARCHAR, ok BOOLEAN, score INT,"
            + " arr ARRAY)");
        answer("INSERT INTO flags1459 SELECT 'Alice', 'Math', TRUE, 90, ARRAY_CONSTRUCT(1, 2)"
            + " UNION ALL SELECT 'Alice', 'Art', FALSE, 70, ARRAY_CONSTRUCT(3)"
            + " UNION ALL SELECT 'Bob', 'Math', TRUE, 60, ARRAY_CONSTRUCT(4)"
            + " UNION ALL SELECT 'Bob', 'Math', FALSE, 80, ARRAY_CONSTRUCT(5)");
    }

    private static String pivot(final String aggregate) {
        return "SELECT * FROM flags1459 PIVOT (" + aggregate + " FOR subject IN ('Math', 'Art')) ORDER BY 1";
    }

    private static String pivotOf(final String column, final String aggregate) {
        return "SELECT * FROM (SELECT student, subject, " + column + " FROM flags1459) PIVOT (" + aggregate
            + " FOR subject IN ('Math', 'Art')) ORDER BY 1";
    }

    private static String unsupported(final String function) {
        return "REFUSED SQL compilation error: | Function " + function + " does not support ARRAY argument type";
    }

    @Test
    public void anAggregateTheColumnsTypeDoesNotTakeIsRefused() {
        assertCells(new String[][] {
            {pivot("SUM(ok)"), SUM_OVER_BOOLEAN},
            {pivot("AVG(ok)"), SUM_OVER_BOOLEAN},
            {pivotOf("ok", "SUM(ok)"), SUM_OVER_BOOLEAN},
            {pivot("MIN(arr)"), unsupported("MIN")},
            {pivot("MAX(arr)"), unsupported("MAX")},
            {pivotOf("arr", "MIN(arr)"), unsupported("MIN")},
            {pivotOf("ok", "COUNT(ok)"), "Alice|1|1 ;; Bob|2|0"},
            {"SELECT student, SUM(ok) FROM flags1459 GROUP BY student", "REFUSED SQL compilation error: error line 1"
                + " at position 16 | Invalid argument types for function 'SUM': (BOOLEAN)"},
            {"SELECT student, MIN(arr) FROM flags1459 GROUP BY student", unsupported("MIN")},
        });
    }

    @Test
    public void theStatisticsDeclareOneTypeGroupedAndPivoted() {
        final String[][] declared = {
            {"KURTOSIS(score)", "NUMBER(38,12)"},
            {"BITAND_AGG(score)", "NUMBER(38,0)"},
            {"BITOR_AGG(score)", "NUMBER(38,0)"},
            {"BITXOR_AGG(score)", "NUMBER(38,0)"},
            {"HASH_AGG(score)", "NUMBER(19,0)"},
        };
        for (final String[] aggregate : declared) {
            assertShape("STUDENT:VARCHAR(16777216), V:" + aggregate[1],
                "SELECT student, " + aggregate[0] + " AS v FROM flags1459 GROUP BY student");
            assertShape("STUDENT:VARCHAR(16777216), 'Math':" + aggregate[1] + ", 'Art':" + aggregate[1],
                pivotOf("score", aggregate[0]));
        }
    }
}
