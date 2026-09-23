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

import static dev.frostlake.query.QueryAnswers.assertCells;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A HAVING that writes no aggregate, in a query with no GROUP BY and no aggregate anywhere else, groups nothing:
 * it filters the rows the WHERE keeps, one row out per row in, and may read the select list's aliases as the WHERE
 * does. With no FROM the WHERE still speaks first, so a false WHERE leaves no row for the HAVING to keep. An
 * aggregate in the HAVING, the select list or the ORDER BY makes the query one group as before. Every cell is
 * live-verified.
 */
public class UngroupedHavingTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE hq (v NUMBER, s VARCHAR)");
        engine.execute("INSERT INTO hq VALUES (1, 'a'), (2, 'b'), (3, 'c')");
        engine.execute("CREATE OR REPLACE TABLE he (v NUMBER)");
    }


    @Test
    public void aFromlessHavingFiltersTheRowTheWhereKept() {
        assertCells(engine, new String[][] {
            {"SELECT 1 WHERE FALSE HAVING TRUE", "no row"},
            {"SELECT 1 WHERE TRUE HAVING TRUE", "1"},
            {"SELECT 1 WHERE NULL HAVING TRUE", "no row"},
            {"SELECT 1 WHERE FALSE HAVING FALSE", "no row"},
            {"SELECT 2 AS a WHERE a = 1 HAVING TRUE", "no row"},
            {"SELECT 1 AS a HAVING a = 1", "1"},
            {"SELECT 1 AS a HAVING a = 2", "no row"},
            {"SELECT 1 AS a, a + 1 AS b HAVING b = 2", "1, 2"},
            {"SELECT 1 AS a HAVING (SELECT 2) = 2", "1"},
            {"SELECT DISTINCT 1 AS a HAVING a = 1", "1"},
            {"SELECT 1 AS a HAVING a = 1 UNION ALL SELECT 2 HAVING FALSE", "1"},
        });
    }

    @Test
    public void aFromlessHavingThatAggregatesStillMakesOneGroup() {
        assertCells(engine, new String[][] {
            {"SELECT COUNT(*) WHERE FALSE HAVING TRUE", "0"},
            {"SELECT 1 WHERE FALSE HAVING COUNT(*) = 0", "1"},
            {"SELECT 1 WHERE FALSE HAVING COUNT(*) = 1", "no row"},
            {"SELECT 1 AS a HAVING COUNT(*) = 1 AND a = 1", "1"},
        });
    }

    @Test
    public void aFromlessHavingInsideASubqueryKeepsTheWhere() {
        assertCells(engine, new String[][] {
            {"SELECT v FROM hq WHERE EXISTS (SELECT 1 WHERE hq.v = 1 HAVING TRUE) ORDER BY v", "1"},
            {"SELECT v FROM hq WHERE EXISTS (SELECT 1 WHERE v = 1 HAVING TRUE) ORDER BY v", "1"},
            {"SELECT v FROM hq WHERE EXISTS (SELECT 1 HAVING hq.v = 2) ORDER BY v", "2"},
            {"SELECT v FROM hq WHERE v IN (SELECT 1 HAVING TRUE) ORDER BY v", "1"},
        });
    }

    @Test
    public void aFromlessHavingIsJudgedAsAWhereIs() {
        assertCells(engine, new String[][] {
            {"SELECT 1 AS a HAVING nosuch = 1", "SQL compilation error: error line 1 at position 21|invalid identifier 'NOSUCH'"},
            {"SELECT 1 AS a HAVING 1", "SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [1]"},
            {"SELECT 1 AS a HAVING a", "SQL compilation error:|Invalid data type [NUMBER(1,0)] for predicate [A]"},
            {"SELECT 1 AS a HAVING ROW_NUMBER() OVER (ORDER BY 1) = 1",
                "SQL compilation error:|Window function [ROW_NUMBER() OVER (ORDER BY 1 ASC NULLS LAST)] appears"
                    + " outside of SELECT, QUALIFY, and ORDER BY clauses."},
        });
    }

    @Test
    public void overATableItKeepsOneRowPerRow() {
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM hq HAVING TRUE", "1 | 1 | 1"},
            {"SELECT 1 FROM hq WHERE FALSE HAVING TRUE", "no row"},
            {"SELECT 1 FROM he HAVING TRUE", "no row"},
            {"SELECT v FROM hq HAVING TRUE ORDER BY v", "1 | 2 | 3"},
            {"SELECT v FROM hq HAVING v = 1", "1"},
            {"SELECT v, s FROM hq HAVING s = 'b'", "2, b"},
            {"SELECT * FROM hq HAVING v = 2", "2, b"},
            {"SELECT hq.v FROM hq HAVING hq.v = 3", "3"},
            {"SELECT v FROM hq WHERE v > 1 HAVING v < 3", "2"},
            {"SELECT v FROM hq HAVING v IN (SELECT 1)", "1"},
            {"SELECT v FROM hq HAVING (SELECT COUNT(*) FROM hq) = 3 ORDER BY v", "1 | 2 | 3"},
            {"SELECT DISTINCT v FROM hq HAVING v > 1 ORDER BY v", "2 | 3"},
            {"SELECT v FROM hq HAVING v > 1 ORDER BY v DESC", "3 | 2"},
            {"SELECT v FROM hq HAVING v > 1 ORDER BY v LIMIT 1 OFFSET 1", "3"},
            {"SELECT a.v FROM hq a JOIN hq b ON a.v = b.v HAVING b.v = 2", "2"},
            {"SELECT COUNT(*) FROM (SELECT v FROM hq HAVING v > 1)", "2"},
            {"SELECT v FROM hq HAVING v = 1 UNION ALL SELECT v FROM hq HAVING v = 2 ORDER BY 1", "1 | 2"},
            {"SELECT (SELECT v FROM hq HAVING v = 2) AS x", "2"},
        });
    }

    @Test
    public void itReadsTheSelectAliasesAsTheWhereDoes() {
        assertCells(engine, new String[][] {
            {"SELECT v AS w FROM hq HAVING w = 1", "1"},
            {"SELECT v * 2 AS w FROM hq HAVING w = 4", "4"},
            {"SELECT v, v AS w FROM hq HAVING w = 3", "3, 3"},
            // A column of the name wins over the alias, as in the WHERE.
            {"SELECT v + 10 AS v FROM hq HAVING v = 11", "no row"},
            {"SELECT v + 10 AS v FROM hq HAVING v = 1", "11"},
        });
    }

    @Test
    public void theWindowsAreComputedOverTheRowsItKept() {
        assertCells(engine, new String[][] {
            {"SELECT v, ROW_NUMBER() OVER (ORDER BY v) AS rn FROM hq HAVING v > 1 ORDER BY v", "2, 1 | 3, 2"},
            {"SELECT COUNT(*) OVER () AS c FROM hq HAVING v > 1", "2 | 2"},
            {"SELECT v FROM hq HAVING v = 1 QUALIFY ROW_NUMBER() OVER (ORDER BY v) = 1", "1"},
        });
    }

    @Test
    public void anAggregateAnywhereStillGroups() {
        assertCells(engine, new String[][] {
            {"SELECT 1 FROM hq HAVING COUNT(*) = 3", "1"},
            {"SELECT 1 FROM he HAVING COUNT(*) = 0", "1"},
            {"SELECT 1 FROM hq HAVING MAX(v) = 3", "1"},
            {"SELECT v FROM hq HAVING MAX(v) = 3", "SQL compilation error:|[HQ.V] is not a valid group by expression"},
            {"SELECT v FROM hq HAVING TRUE ORDER BY COUNT(*)",
                "SQL compilation error:|[HQ.V] is not a valid group by expression"},
        });
    }

    @Test
    public void itIsRefusedAsAWhereIsRefused() {
        assertCells(engine, new String[][] {
            {"SELECT v FROM hq HAVING nosuch = 1", "SQL compilation error: error line 1 at position 24|invalid identifier 'NOSUCH'"},
            {"SELECT v FROM hq HAVING v", "SQL compilation error:|Invalid data type [NUMBER(38,0)] for predicate [HQ.V]"},
            {"SELECT v FROM hq HAVING SUM(v) OVER () > 0",
                "SQL compilation error:|Window function [SUM(HQ.V) OVER ()] appears outside of SELECT, QUALIFY, and"
                    + " ORDER BY clauses."},
            {"SELECT v, ROW_NUMBER() OVER (ORDER BY v) AS rn FROM hq HAVING rn = 1",
                "SQL compilation error:|Window function [ROW_NUMBER() OVER (ORDER BY HQ.V ASC NULLS LAST)] appears"
                    + " outside of SELECT, QUALIFY, and ORDER BY clauses."},
            // A grouped query's HAVING keeps the grouped wording.
            {"SELECT v FROM hq GROUP BY v HAVING ROW_NUMBER() OVER (ORDER BY v) = 1",
                "SQL compilation error:|[ROW_NUMBER() OVER (ORDER BY HQ.V ASC NULLS LAST)] is not a valid group by"
                    + " expression"},
            {"SELECT COUNT(*) FROM hq HAVING SUM(v) OVER () > 0",
                "SQL compilation error:|[SUM(HQ.V) OVER ()] is not a valid group by expression"},
        });
    }
}
