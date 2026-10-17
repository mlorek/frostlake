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
 * A CONNECT BY query's pseudo-columns stand only where its select list reads them (live-verified): LEVEL inside an
 * aggregate, in the WHERE or in the ORDER BY is an invalid identifier, a window anywhere is refused as a recursive
 * CTE's, CONNECT_BY_ROOT inside a call is a syntax error and SYS_CONNECT_BY_PATH inside one an unknown function,
 * while LEVEL, CONNECT_BY_ROOT and SYS_CONNECT_BY_PATH as select items and a derived table reading LEVEL answer.
 */
public class ConnectByPseudoColumnPlacementTest extends MeasuredCellsTestSupport {

    @Test
    public void aPseudoColumnStandsOnlyWhereTheSelectListReadsIt() {
        engine.execute("CREATE OR REPLACE TABLE e (id INT, mgr INT, w NUMBER(6,2))");
        engine.execute("INSERT INTO e VALUES (1, NULL, 1), (2, 1, 2), (3, 1, 3), (4, 2, 3)");
        assertCells(new String[][] {
            {"SELECT MAX(LEVEL) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'LEVEL'"},
            {"SELECT SUM(LEVEL + 0) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'LEVEL'"},
            {"SELECT COUNT(LEVEL) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 13 | invalid identifier 'LEVEL'"},
            {"SELECT COUNT(DISTINCT LEVEL) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 22 | invalid identifier 'LEVEL'"},
            {"SELECT ARRAY_AGG(LEVEL) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 17 | invalid identifier 'LEVEL'"},
            {"SELECT AVG(LEVEL) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'LEVEL'"},
            {"SELECT MAX(e.level) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'E.LEVEL'"},
            {"SELECT COUNT(*) FROM e WHERE LEVEL > 1 START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 29 | invalid identifier 'LEVEL'"},
            {"SELECT id, SUM(LEVEL) OVER () s FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term."},
            {"SELECT id, ROW_NUMBER() OVER (ORDER BY id) r FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: | Window functions are not allowed in a CTEs recursive term."},
            {"SELECT MAX(CONNECT_BY_ROOT id) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'id'. | syntax "
                + "error line 1 at position 27 unexpected 'id'."},
            {"SELECT SYSTEM$TYPEOF(CONNECT_BY_ROOT w) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'w'. | syntax "
                + "error line 1 at position 37 unexpected 'w'."},
            {"SELECT MAX(SYS_CONNECT_BY_PATH(id, '/')) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: | Unknown function SYS_CONNECT_BY_PATH."},
            {"SELECT LEVEL, LEVEL + 1 FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id ORDER BY 1, 2",
                "1|2 ;; 2|3 ;; 2|3 ;; 3|4"},
            {"SELECT AVG(w) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id", "2.25000000"},
            {"SELECT MAX(lv) FROM (SELECT LEVEL lv FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id)", "3"},
            {"SELECT COUNT(*) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id", "4"},
            {"SELECT DISTINCT SYSTEM$TYPEOF(LEVEL) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "NUMBER(38,0)[SB16]"},
            {"SELECT id FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id ORDER BY LEVEL, id",
                "REFUSED SQL compilation error: error line 1 at position 75 | invalid identifier 'LEVEL'"},
            {"SELECT id FROM e WHERE w > 1 START WITH mgr IS NULL CONNECT BY mgr = PRIOR id ORDER BY id",
                "2 ;; 3 ;; 4"},
            {"SELECT id, CONNECT_BY_ROOT id r, SYS_CONNECT_BY_PATH(id, '/') p FROM e START WITH mgr IS NULL "
                + "CONNECT BY mgr = PRIOR id ORDER BY id",
                "1|1|/1 ;; 2|1|/1/2 ;; 3|1|/1/3 ;; 4|1|/1/2/4"},
            {"SELECT id, LEVEL * 2 FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id ORDER BY id",
                "1|2 ;; 2|4 ;; 3|4 ;; 4|6"},
            {"SELECT id FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id QUALIFY LEVEL > 1 ORDER BY id",
                "REFUSED SQL compilation error: | syntax error line 1 at position 66 unexpected 'QUALIFY'."},
            {"SELECT MAX(LEVEL) m FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: error line 1 at position 11 | invalid identifier 'LEVEL'"},
            {"SELECT UPPER(CONNECT_BY_ROOT id) FROM e START WITH mgr IS NULL CONNECT BY mgr = PRIOR id",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'id'. | syntax "
                + "error line 1 at position 29 unexpected 'id'."},
        });
    }
}
