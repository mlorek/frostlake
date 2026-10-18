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
 * UNION [ALL] BY NAME in a chain (live-verified): a later positional UNION, EXCEPT or MINUS arm is held to the
 * merged layout's width, and a narrower one is refused naming its branch as the arms up to the last BY NAME count
 * as one; an INTERSECT arm is held to its own run's first arm; a quoted name matches case-sensitively, so "a"
 * and A are two columns; and ORDER BY and LIMIT read the folded column types.
 */
public class UnionByNameChainTest extends MeasuredCellsTestSupport {

    @Test
    public void aPositionalArmIsHeldToTheMergedLayout() {
        assertCells(new String[][] {
            {"SELECT 1 a UNION ALL BY NAME SELECT 2 b UNION ALL SELECT 3",
                "REFUSED SQL compilation error: | invalid number of result columns for set operator input branches, "
                + "expected 2, got 1 in branch 2"},
            {"SELECT b FROM (SELECT 1 a UNION ALL BY NAME SELECT 2 b UNION ALL SELECT 3)",
                "REFUSED SQL compilation error: | invalid number of result columns for set operator input branches, "
                + "expected 2, got 1 in branch 2"},
            {"SELECT 1 a UNION ALL BY NAME SELECT 2 b UNION ALL SELECT 3, 4", "1|null ;; null|2 ;; 3|4"},
            {"(SELECT 1 a UNION ALL BY NAME SELECT 2 b) UNION ALL SELECT 3, 4", "1|null ;; null|2 ;; 3|4"},
            {"SELECT 2 b UNION ALL BY NAME SELECT 1 a EXCEPT SELECT 1",
                "REFUSED SQL compilation error: | invalid number of result columns for set operator input branches, "
                + "expected 2, got 1 in branch 2"},
            {"SELECT 2 b UNION ALL BY NAME SELECT 1 a EXCEPT SELECT 2, NULL", "null|1"},
        });
    }

    @Test
    public void theBranchCountsTheByNameArmsAsOne() {
        assertCells(new String[][] {
            {"SELECT 1 a UNION ALL BY NAME SELECT 2 b UNION ALL SELECT 3, 4 UNION ALL SELECT 5",
                "REFUSED SQL compilation error: | invalid number of result columns for set operator input branches, "
                + "expected 2, got 1 in branch 3"},
            {"SELECT 1 a, 2 b UNION ALL SELECT 3, 4 UNION ALL BY NAME SELECT 5 c UNION ALL SELECT 6",
                "REFUSED SQL compilation error: | invalid number of result columns for set operator input branches, "
                + "expected 3, got 1 in branch 2"},
            {"SELECT 1 a UNION ALL SELECT 2, 3 UNION ALL BY NAME SELECT 4 b",
                "REFUSED SQL compilation error: | invalid number of result columns for set operator input branches, "
                + "expected 1, got 2 in branch 2"},
            {"SELECT 1 a UNION ALL BY NAME SELECT 2 b INTERSECT SELECT 3", "1|null"},
            {"SELECT 1 a UNION ALL BY NAME SELECT 2 b UNION SELECT 1, NULL", "1|null ;; null|2"},
            {"SELECT 1 a UNION ALL BY NAME SELECT 2 b MINUS SELECT 3",
                "REFUSED SQL compilation error: | invalid number of result columns for set operator input branches, "
                + "expected 2, got 1 in branch 2"},
            {"SELECT 1 a UNION ALL BY NAME SELECT 2 b UNION ALL SELECT 3, 4 UNION ALL BY NAME SELECT 5 c",
                "1|null|null ;; null|2|null ;; 3|4|null ;; null|null|5"},
            {"SELECT 1 a UNION ALL BY NAME SELECT 2 b UNION ALL SELECT 3, 4 UNION ALL BY NAME SELECT 5 c UNION ALL "
                + "SELECT 6",
                "REFUSED SQL compilation error: | invalid number of result columns for set operator input branches, "
                + "expected 3, got 1 in branch 2"},
        });
    }

    @Test
    public void aQuotedNameMatchesCaseSensitively() {
        assertCells(new String[][] {
            {"SELECT 1 \"a\", 2 a UNION ALL BY NAME SELECT 3 A", "1|2 ;; null|3"},
            {"SELECT 1 \"a\" UNION ALL BY NAME SELECT 2 a", "1|null ;; null|2"},
            {"SELECT * FROM (SELECT 1 \"a\" UNION ALL BY NAME SELECT 2 a) ORDER BY 1", "1|null ;; null|2"},
        });
    }

    @Test
    public void theResultSortsInTheFoldedType() {
        assertCells(new String[][] {
            {"SELECT column1 a FROM VALUES ('10'), ('9'), ('100') UNION ALL BY NAME SELECT 50 a ORDER BY a",
                "9.00000 ;; 10.00000 ;; 50.00000 ;; 100.00000"},
            {"SELECT column1 a FROM VALUES ('10'), ('9'), ('100') UNION ALL SELECT 50 a ORDER BY a",
                "9.00000 ;; 10.00000 ;; 50.00000 ;; 100.00000"},
            {"SELECT column1 a FROM VALUES ('10'), ('9'), ('100') UNION ALL BY NAME SELECT 50 a ORDER BY a DESC LIMIT "
                + "1",
                "100.00000"},
        });
    }
}
