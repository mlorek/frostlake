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
 * A query written in parentheses is read through them: the statement's ORDER BY reads the select's FROM, a row
 * limit written at another level applies to the sorted rows, and a clause two levels write is a duplicate (all
 * live-verified).
 */
public class ParenthesizedQueryClausesTest extends MeasuredCellsTestSupport {

    @Test
    public void throughParentheses() {
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, b INT)");
        engine.execute("INSERT INTO tt VALUES (3, 4), (1, 2), (2, 9)");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT a FROM tt) ORDER BY b");
        assertAnswer("1 ;; 3 ;; 2", "((SELECT a FROM tt)) ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Duplicate ORDER BY: ORDER.",
            "(SELECT a FROM tt ORDER BY a) ORDER BY b");
        assertAnswer("1 ;; 3", "(SELECT a FROM tt LIMIT 2) ORDER BY b");
        assertAnswer("1 ;; 3", "(SELECT a FROM tt) ORDER BY b LIMIT 2");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT a AS x FROM tt) ORDER BY b");
        assertAnswer("3 ;; 2 ;; 1", "(SELECT a AS x FROM tt) ORDER BY x DESC");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT a FROM tt) ORDER BY tt.b");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT a FROM tt t) ORDER BY t.b");
        assertAnswer("REFUSED SQL compilation error: | [TT.B] is not a valid order by expression",
            "(SELECT DISTINCT a FROM tt) ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | [TT.B] is not a valid order by expression",
            "(SELECT a, COUNT(*) FROM tt GROUP BY a) ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 57 | invalid identifier 'B'",
            "(SELECT a FROM tt) UNION ALL (SELECT a FROM tt) ORDER BY b");
        assertAnswer("2 ;; 3", "(SELECT a FROM tt WHERE b > 2) ORDER BY b DESC");
        assertAnswer("3 ;; 2 ;; 1", "(SELECT a FROM tt) ORDER BY 1 DESC");
        assertAnswer("2 ;; 3 ;; 1", "(SELECT a FROM tt) ORDER BY b + 0 DESC");
        assertAnswer("1 ;; 3 ;; 2", "WITH c AS (SELECT * FROM tt) (SELECT a FROM c) ORDER BY b");
        assertAnswer("1 ;; 3 ;; 2", "(WITH c AS (SELECT * FROM tt) SELECT a FROM c) ORDER BY b");
        assertAnswer("1", "(SELECT a FROM tt) ORDER BY b FETCH FIRST 1 ROWS ONLY");
        assertAnswer("1 ;; 3 ;; 2", "SELECT * FROM ((SELECT a FROM tt) ORDER BY b)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 28 | invalid identifier 'C'",
            "(SELECT a FROM tt) ORDER BY c");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT a FROM tt) ORDER BY b NULLS FIRST, a");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT a FROM tt QUALIFY ROW_NUMBER() OVER (ORDER BY a) <= 3) ORDER BY b");
        assertAnswer("1|2 ;; 3|4 ;; 2|9", "(SELECT a, b AS a FROM tt) ORDER BY b");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT tt.* EXCLUDE b FROM tt) ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '<EOF>'.",
            "(SELECT a FROM tt) ORDER BY b OFFSET 1");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'A'",
            "(SELECT a FROM tt, tt AS u WHERE tt.a = u.a) ORDER BY u.b");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT a FROM tt) ORDER BY (SELECT MAX(b) FROM tt), b");
        assertAnswer("2 ;; 3", "(SELECT a FROM tt LIMIT 2) ORDER BY b DESC");
        assertAnswer("REFUSED SQL compilation error: | Duplicate ORDER BY: ORDER.",
            "(SELECT a FROM tt ORDER BY a LIMIT 2) ORDER BY b");
        assertAnswer("REFUSED SQL compilation error: | Duplicate LIMIT: LIMIT.", "(SELECT a FROM tt LIMIT 2) LIMIT 1");
        assertAnswer("1 ;; 2", "(SELECT a FROM tt ORDER BY a) LIMIT 2");
        assertAnswer("REFUSED SQL compilation error: | Duplicate ORDER BY: ORDER.",
            "(SELECT a FROM tt ORDER BY a DESC) ORDER BY 1");
        assertAnswer("2 ;; 3", "(SELECT a FROM tt FETCH FIRST 2 ROWS ONLY) ORDER BY b DESC");
        assertAnswer("3 ;; 2", "(SELECT a FROM tt LIMIT 2 OFFSET 1) ORDER BY b");
        assertAnswer("1", "(SELECT a FROM tt ORDER BY a) FETCH FIRST 1 ROWS ONLY");
        assertAnswer("REFUSED SQL compilation error: | Duplicate LIMIT: FETCH.",
            "(SELECT a FROM tt LIMIT 1) FETCH FIRST 1 ROWS ONLY");
        assertAnswer("REFUSED SQL compilation error: | Duplicate ORDER BY: ORDER.",
            "((SELECT a FROM tt) ORDER BY b) ORDER BY a");
        assertAnswer("2 ;; 3", "((SELECT a FROM tt) LIMIT 2) ORDER BY b DESC");
        assertAnswer("REFUSED SQL compilation error: | Duplicate ORDER BY: ORDER.",
            "(SELECT a FROM tt ORDER BY b) ORDER BY b");
        assertAnswer("3", "(SELECT a FROM tt) ORDER BY b DESC LIMIT 1 OFFSET 1");
        assertAnswer("REFUSED SQL compilation error: | Duplicate ORDER BY: ORDER.",
            "(SELECT a FROM tt WHERE a > 1 ORDER BY a) ORDER BY b");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT a FROM (SELECT * FROM tt)) ORDER BY b");
        assertAnswer("1 ;; 3 ;; 2", "(SELECT x.a FROM tt x JOIN tt y ON x.a = y.a) ORDER BY y.b");
        assertAnswer("3", "(SELECT a FROM tt ORDER BY a DESC) LIMIT 1");
        assertAnswer("REFUSED SQL compilation error: | Duplicate LIMIT: LIMIT.",
            "(SELECT a FROM tt ORDER BY a LIMIT 2) LIMIT 1");
        assertAnswer("3 ;; 2", "(SELECT a FROM tt LIMIT 2) ORDER BY 1 DESC");
        assertAnswer("2 ;; 3", "(SELECT a FROM tt ORDER BY b DESC) FETCH FIRST 2 ROWS ONLY");
    }
}
