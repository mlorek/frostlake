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
 * A subquery in a branch the planner certainly takes is judged with the statement, whether or not a row
 * reaches it: the branch a simple CASE or a DECODE over constants takes, and the branch of a searched CASE
 * whose condition the statistics of its table settle — {@code CASE WHEN id > 0 THEN (SELECT …) ELSE 1 END}
 * over positive ids. The branch they leave out is not judged. Every cell is live-verified.
 */
public class DecidedBranchSubqueryJudgementTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
    }

    @Test
    public void aBranchAConstantOrTheStatisticsDecideIsJudged() {
        assertCells(new String[][] {
            {"SELECT id, CASE WHEN id > 0 THEN (SELECT v FROM g WHERE g.id = fz.id) ELSE 1 END AS x FROM fz "
                + "WHERE 1 = 0",
                unsupportedAt(34)},
            {"SELECT id, CASE WHEN id > 0 THEN 1 ELSE (SELECT v FROM g WHERE g.id = fz.id) END AS x FROM fz "
                + "WHERE 1 = 0",
                ""},
            {"SELECT id, CASE 1 WHEN 1 THEN (SELECT v FROM g WHERE g.id = fz.id) END AS x FROM fz WHERE 1 = 0",
                unsupportedAt(31)},
            {"SELECT id, DECODE(1, 1, (SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz WHERE 1 = 0",
                unsupportedAt(25)},
            {"SELECT id, ZEROIFNULL((SELECT v FROM g WHERE g.id = fz.id)) AS x FROM fz WHERE 1 = 0",
                unsupportedAt(22)},
        });
    }
}
