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
 * A SQL table function joined with an ON condition reads the left side as the correlated subquery its body is: an
 * outer join, LEFT or FULL, is refused as a subquery the account cannot evaluate, while a join that reads nothing of
 * the left side, a CROSS join and a comma item answer as joins do (live-verified).
 */
public class SqlTableFunctionOnJoinTest extends MeasuredCellsTestSupport {

    @Test
    public void anOuterJoinOfASqlTableFunctionIsRefused() {
        engine.execute("CREATE OR REPLACE FUNCTION ptf(p NUMBER) RETURNS TABLE (x VARCHAR) AS $$ SELECT "
            + "TO_VARCHAR(p) $$");
        engine.execute("CREATE OR REPLACE FUNCTION ptf2(p NUMBER) RETURNS TABLE (x NUMBER) AS $$ SELECT p UNION ALL "
            + "SELECT p + 1 $$");
        engine.execute("CREATE OR REPLACE TABLE tt (d NUMBER(10,2), n NUMBER(38,0))");
        engine.execute("INSERT INTO tt VALUES (1.5, 2), (2.5, 3)");
        assertCells(new String[][] {
            {"SELECT x FROM tt LEFT JOIN LATERAL ptf(tt.n) ON TRUE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT x FROM tt FULL JOIN TABLE(ptf(tt.n)) ON TRUE",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
            {"SELECT x FROM tt JOIN TABLE(ptf(1)) ON TRUE ORDER BY 1", "1 ;; 1"},
            {"SELECT x FROM tt LEFT JOIN TABLE(ptf(1)) ON TRUE ORDER BY 1", "1 ;; 1"},
            {"SELECT x FROM tt CROSS JOIN TABLE(ptf(tt.n)) ORDER BY 1", "2 ;; 3"},
            {"SELECT x FROM tt, TABLE(ptf(tt.n)) ORDER BY 1", "2 ;; 3"},
            {"SELECT x FROM tt JOIN TABLE(ptf(tt.n)) f USING (x)",
                "REFUSED SQL compilation error: | Invalid identifier X"},
            {"SELECT x FROM tt LEFT JOIN TABLE(ptf(tt.n)) f ON f.x = TO_VARCHAR(tt.n)",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
        });
    }
}
