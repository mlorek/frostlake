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
 * A key that is a condition (live-verified): an ORDER BY, a GROUP BY, a window's PARTITION BY and ORDER BY, a
 * WITHIN GROUP's ORDER BY, a ROLLUP's, a CUBE's and a grouping set's member and a MATCH_RECOGNIZE's partition take
 * NOT, AND and OR as a select item does, and {@code !} as the account reads it: before EXISTS a negation, before
 * anything else a call of a function named "!". A grouped query's key that tests a subquery is refused naming the
 * test, NOT EXISTS whole.
 */
public class ConditionKeyTest extends MeasuredCellsTestSupport {

    @BeforeEach
    public void createTheTables() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE pg (x INT, y INT, b BOOLEAN)", "Table PG successfully created."},
            {"INSERT INTO pg VALUES (1, 2, TRUE)", "1"},
            {"CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)", "Table FZ successfully created."},
            {"INSERT INTO fz VALUES (5, TRUE), (7, FALSE)", "2"},
            {"CREATE OR REPLACE TABLE g (id INT)", "Table G successfully created."},
            {"INSERT INTO g VALUES (5)", "1"},
        });
    }

    @Test
    public void anOrderByKeyMayBeACondition() {
        assertCells(new String[][] {
            {"SELECT 1 FROM pg ORDER BY NOT b", "1"},
            {"SELECT 1 FROM pg ORDER BY b AND b", "1"},
            {"SELECT 1 FROM pg ORDER BY !EXISTS (SELECT 1)", "1"},
            {"SELECT 1 FROM pg ORDER BY b IS NULL", "1"},
            {"SELECT 1 FROM pg ORDER BY x = 1", "1"},
            {"SELECT id FROM fz ORDER BY NOT b, id", "5 ;; 7"},
            {"SELECT id FROM fz ORDER BY b AND id > 5, id", "5 ;; 7"},
            {"SELECT id FROM fz ORDER BY id > 5 OR b DESC, id", "5 ;; 7"},
            {"SELECT id FROM fz ORDER BY NOT EXISTS (SELECT 1 FROM g WHERE g.id = fz.id), id", "5 ;; 7"},
            {"SELECT id FROM fz ORDER BY EXISTS (SELECT 1 FROM g WHERE g.id = fz.id) AND b, id", "7 ;; 5"},
            {"SELECT id FROM fz ORDER BY NOT b DESC NULLS LAST, id", "7 ;; 5"},
            {"SELECT id FROM fz ORDER BY NOT NOT b, id", "7 ;; 5"},
            {"SELECT id FROM fz ORDER BY b OR b, b AND b", "7 ;; 5"},
            {"SELECT id FROM fz ORDER BY NOT id, id", "5 ;; 7"},
            {"SELECT id FROM fz ORDER BY NOT b ASC NULLS FIRST, id", "5 ;; 7"},
            {"SELECT id FROM fz ORDER BY NOT (b), id", "5 ;; 7"},
            {"SELECT id FROM fz ORDER BY NOT b AND id > 5, id", "5 ;; 7"},
            {"SELECT id FROM fz ORDER BY (NOT b), id", "5 ;; 7"},
        });
    }

    @Test
    public void aGroupByKeyMayBeACondition() {
        assertCells(new String[][] {
            {"SELECT 1 FROM pg GROUP BY NOT b", "1"},
            {"SELECT COUNT(*) FROM pg GROUP BY b OR b", "1"},
            {"SELECT COUNT(*) FROM fz GROUP BY NOT b", "1 ;; 1"},
            {"SELECT COUNT(*) FROM fz GROUP BY b AND b", "1 ;; 1"},
            {"SELECT NOT b, COUNT(*) FROM fz GROUP BY NOT b ORDER BY 1", "false|1 ;; true|1"},
            {"SELECT COUNT(*) FROM fz GROUP BY b OR id > 5", "2"},
            {"SELECT COUNT(*) FROM fz GROUP BY ROLLUP (NOT b) ORDER BY 1", "1 ;; 1 ;; 2"},
            {"SELECT COUNT(*) FROM fz GROUP BY CUBE (b AND b) ORDER BY 1", "1 ;; 1 ;; 2"},
            {"SELECT COUNT(*) FROM fz GROUP BY GROUPING SETS ((NOT b), ()) ORDER BY 1", "1 ;; 1 ;; 2"},
            {"SELECT COUNT(*) FROM fz GROUP BY GROUPING SETS (NOT b)", "1 ;; 1"},
            {"SELECT NOT b AS nb, COUNT(*) FROM fz GROUP BY nb ORDER BY 1", "false|1 ;; true|1"},
            {"SELECT id FROM fz GROUP BY id, NOT b ORDER BY id", "5 ;; 7"},
            {"SELECT COUNT(*) FROM fz GROUP BY NOT b, id ORDER BY 1", "1 ;; 1"},
        });
    }

    @Test
    public void aWindowOrWithinGroupKeyMayBeACondition() {
        assertCells(new String[][] {
            {"SELECT SUM(x) OVER (ORDER BY NOT b) FROM pg", "1"},
            {"SELECT id, ROW_NUMBER() OVER (ORDER BY NOT b, id) FROM fz ORDER BY id", "5|1 ;; 7|2"},
            {"SELECT id, ROW_NUMBER() OVER (PARTITION BY NOT b ORDER BY id) FROM fz ORDER BY id", "5|1 ;; 7|1"},
            {"SELECT id, ROW_NUMBER() OVER (ORDER BY b AND b, id) FROM fz ORDER BY id", "5|2 ;; 7|1"},
            {"SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY NOT b, id) FROM fz", "5,7"},
            {"SELECT ARRAY_AGG(id) WITHIN GROUP (ORDER BY b AND b, id) FROM fz", "[7,5]"},
            {"SELECT id FROM fz QUALIFY ROW_NUMBER() OVER (ORDER BY NOT b) = 1", "5"},
            {"SELECT id, SUM(id) OVER (PARTITION BY b AND id > 5) FROM fz ORDER BY id", "5|12 ;; 7|12"},
            {"SELECT id, SUM(id) OVER (PARTITION BY id, NOT b) FROM fz ORDER BY id", "5|5 ;; 7|7"},
            {"SELECT id, SUM(id) OVER (PARTITION BY (NOT b)) FROM fz ORDER BY id", "5|5 ;; 7|7"},
            {"SELECT * FROM fz MATCH_RECOGNIZE (PARTITION BY NOT b ORDER BY id MEASURES COUNT(*) AS c "
                + "ALL ROWS PER MATCH PATTERN (a) DEFINE a AS TRUE) ORDER BY id",
                "5|true|1 ;; 7|false|1"},
            {"SELECT NOT b, ROW_NUMBER() OVER (ORDER BY id) FROM fz ORDER BY 2", "false|1 ;; true|2"},
            {"SELECT ROW_NUMBER() OVER (ORDER BY id) > 1 AS late FROM fz ORDER BY 1", "false ;; true"},
        });
    }

    @Test
    public void aConditionKeyIsRefusedAsTheAccountRefusesIt() {
        assertCells(new String[][] {
            {"SELECT 1 FROM pg ORDER BY !x", "REFUSED SQL compilation error: | Unknown function \"!\"."},
            {"SELECT 1 FROM pg GROUP BY !x", "REFUSED SQL compilation error: | Unknown function \"!\"."},
            {"SELECT SUM(x) OVER (PARTITION BY !b) FROM pg",
                "REFUSED SQL compilation error: | Unknown function \"!\"."},
            {"SELECT SUM(x) OVER (ORDER BY !b) FROM pg", "REFUSED SQL compilation error: | Unknown function \"!\"."},
            {"SELECT id FROM fz GROUP BY id ORDER BY NOT EXISTS (SELECT 1 FROM g), id",
                "REFUSED SQL compilation error: | [NOT EXISTS(SELECT 1 AS \"1\" FROM G AS G)] is not a "
                + "valid order by expression"},
            {"SELECT id FROM fz GROUP BY id ORDER BY NOT (id IN (SELECT id FROM g)), id",
                "REFUSED SQL compilation error: | [ANY(SELECT G.ID AS \"ID\" FROM G AS G)] is not a valid "
                + "order by expression"},
            {"SELECT id FROM fz GROUP BY id ORDER BY EXISTS (SELECT 1 FROM g) OR EXISTS (SELECT 2 FROM "
                + "g), id",
                "REFUSED SQL compilation error: | [EXISTS(SELECT 1 AS \"1\" FROM G AS G)] is not a valid "
                + "order by expression"},
            {"SELECT id FROM fz ORDER BY NOT b + 1",
                "REFUSED SQL compilation error: error line 1 at position 33 | Invalid argument types for "
                + "function '+': (BOOLEAN, NUMBER(1,0))"},
            {"SELECT id FROM fz ORDER BY NOT, id",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ','."},
            {"SELECT id FROM fz ORDER BY b AND, id",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected ','."},
        });
    }
}
