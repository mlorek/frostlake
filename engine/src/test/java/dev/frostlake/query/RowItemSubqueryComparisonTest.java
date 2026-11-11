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
 * A row compared with, or tested IN, a subquery of one ROW item that reads no relation reads the subquery as the row
 * it answers once (live-verified): {@code (a, b) = (SELECT (1, 'x'))} is TRUE for (1, 'x'), the subquery stands on
 * either side, and a row of another width is refused naming the query the account plans. One that reads a relation
 * is refused its ROW item at the ROW, ahead of the comparison's types.
 */
public class RowItemSubqueryComparisonTest extends MeasuredCellsTestSupport {

    @Test
    public void aRowAgainstASubqueryOfOneRowItem() {
        engine.execute("CREATE OR REPLACE TABLE rj911 (a INT, b VARCHAR)");
        engine.execute("INSERT INTO rj911 VALUES (1, 'x'), (2, 'y')");
        assertCells(new String[][] {
            {"SELECT a FROM rj911 WHERE (a, b) = (SELECT 1, 'x')",
                "REFUSED SQL compilation error: error line 1 at position 36 | Unsupported: Scalar subquery with "
                + "multi-column SELECT clause."},
            {"SELECT a FROM rj911 WHERE (a, b) IN (SELECT 1, 'x')", "1"},
            {"SELECT a FROM rj911 WHERE (a, b) = (SELECT (1, 'x'))", "1"},
            {"SELECT a FROM rj911 WHERE (a, b) <> (SELECT (1, 'x')) ORDER BY a", "2"},
            {"SELECT a FROM rj911 WHERE (a, b) IN (SELECT (1, 'x'))", "1"},
            {"SELECT a FROM rj911 WHERE (a, b) NOT IN (SELECT (1, 'x')) ORDER BY a", "2"},
            {"SELECT a FROM rj911 WHERE (a, b) IN (SELECT (a, b) FROM rj911 WHERE a = 2)",
                "REFUSED SQL compilation error: error line 1 at position 44 | Unsupported: SELECT list element "
                + "with multiple columns."},
            {"SELECT a FROM rj911 WHERE (a, b) = (SELECT (a, b) FROM rj911 WHERE a = 2)",
                "REFUSED SQL compilation error: error line 1 at position 43 | Unsupported: SELECT list element "
                + "with multiple columns."},
            {"SELECT a FROM rj911 WHERE (SELECT (1, 'x')) = (a, b)", "1"},
            {"SELECT a FROM rj911 WHERE (a, b) = (SELECT (1, 'x', 3))",
                "REFUSED SQL compilation error: | Can not convert parameter '(SELECT ROW(1, 'x', 3) AS \"(1, 'X', "
                + "3)\" FROM (VALUES (null)) DUAL)' of type [ROW(NUMBER(1,0), VARCHAR(1), NUMBER(1,0))] into "
                + "expected type [ROW(NUMBER(38,0), VARCHAR(16777216))]"},
            {"SELECT a FROM rj911 WHERE (a, b) = (SELECT (a, b) FROM rj911)",
                "REFUSED SQL compilation error: error line 1 at position 43 | Unsupported: SELECT list element "
                + "with multiple columns."},
            {"SELECT (1, 'x') = (SELECT (1, 'x'))", "true"},
        });
    }

    @Test
    public void aRowEqualToOrInAFromlessRowSubquery() {
        engine.execute("CREATE OR REPLACE TABLE ri910 (a INT, b VARCHAR, d DATE)");
        engine.execute("INSERT INTO ri910 VALUES (1, 'x', '2024-01-01'), (2, 'y', '2024-02-01')");
        assertCells(new String[][] {
            {"SELECT a FROM ri910 WHERE (a, b) = (SELECT (1, 'x'))", "1"},
            {"SELECT a FROM ri910 WHERE (a, b) IN (SELECT (1, 'x'))", "1"},
        });
    }
}
