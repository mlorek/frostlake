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
 * A view read AT or BEFORE a point reads every table its body reads as of the point: the tables of its FROM, of a
 * CTE, and of its subqueries too - an IN, a NOT IN, an EXISTS, a scalar subquery, a correlated one, a LATERAL
 * argument - and through a view it reads, while a subquery of a statement that reads a table AT a point reads its
 * own table now (all live-verified).
 */
public class ViewTimeTravelSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void aViewsSubqueriesReadAtTheViewsPoint() {
        engine.execute("CREATE OR REPLACE TABLE tt (id INT)");
        engine.execute("CREATE OR REPLACE TABLE uu (id INT)");
        engine.execute("INSERT INTO tt VALUES (1), (2), (3)");
        engine.execute("INSERT INTO uu VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW v_in AS SELECT id FROM tt WHERE id IN (SELECT id FROM uu)");
        engine.execute("CREATE OR REPLACE VIEW v_cor AS SELECT id, (SELECT COUNT(*) FROM uu WHERE uu.id <= tt.id) c "
            + "FROM tt");
        engine.execute("CREATE OR REPLACE VIEW v_ex AS SELECT id FROM tt WHERE EXISTS (SELECT 1 FROM uu WHERE uu.id "
            + "= tt.id)");
        engine.execute("CREATE OR REPLACE VIEW v_sc AS SELECT id, (SELECT MAX(id) FROM uu) m FROM tt");
        engine.execute("CREATE OR REPLACE VIEW v_cte AS WITH x AS (SELECT id FROM uu) SELECT tt.id FROM tt JOIN x ON "
            + "tt.id = x.id");
        engine.execute("CREATE OR REPLACE VIEW v_lat AS SELECT tt.id, f.value FROM tt, LATERAL FLATTEN(INPUT => "
            + "(SELECT ARRAY_AGG(id) FROM uu)) f");
        engine.execute("CREATE OR REPLACE VIEW v_un AS SELECT id FROM tt UNION ALL SELECT id FROM uu");
        engine.execute("CREATE OR REPLACE VIEW v_vv AS SELECT * FROM v_in");
        engine.execute("CREATE OR REPLACE VIEW v_nin AS SELECT id FROM tt WHERE id NOT IN (SELECT id FROM uu)");
        engine.execute("CREATE OR REPLACE VIEW v_any AS SELECT id FROM tt WHERE id > ANY (SELECT id FROM uu)");
        engine.execute("CREATE OR REPLACE VIEW v_dt AS SELECT d.id FROM (SELECT id FROM uu) d");
        assertAnswer("Statement executed successfully.", "SET p = CURRENT_TIMESTAMP()");
        assertAnswer("waited 2 seconds", "CALL SYSTEM$WAIT(2)");
        assertAnswer("2", "INSERT INTO uu VALUES (2), (3)");
        assertAnswer("1", "INSERT INTO tt VALUES (4)");
        assertAnswer("1", "SELECT * FROM v_in AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("1|1 ;; 2|1 ;; 3|1", "SELECT * FROM v_cor AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("1", "SELECT * FROM v_ex AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("1|1 ;; 2|1 ;; 3|1", "SELECT * FROM v_sc AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("1", "SELECT * FROM v_cte AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("3", "SELECT COUNT(*) FROM v_lat AT(TIMESTAMP => $p)");
        assertAnswer("1 ;; 1 ;; 2 ;; 3", "SELECT * FROM v_un AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("1", "SELECT * FROM v_vv AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("2 ;; 3", "SELECT * FROM v_nin AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("2 ;; 3", "SELECT * FROM v_any AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("1", "SELECT * FROM v_dt AT(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT id FROM tt AT(TIMESTAMP => $p) WHERE id IN (SELECT id FROM uu) ORDER BY 1");
        assertAnswer("1", "SELECT * FROM v_in BEFORE(TIMESTAMP => $p) ORDER BY 1");
        assertAnswer("1 ;; 2 ;; 3", "SELECT * FROM v_in ORDER BY 1");
        assertAnswer("1|3 ;; 2|3 ;; 3|3 ;; 4|3", "SELECT * FROM v_sc ORDER BY 1");
    }
}
