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
 * A SQL table function called once per row of the relation to its left is tagged by all of its calls: over a column
 * holding 3 and 300, {@code TABLE(t_div(ti.i))} is [SB2] and {@code t_div(tj.i)} over 3 and 70000 [SB4], a computed
 * argument by what it computes and a NULL argument adding nothing (live-verified).
 */
public class LateralCallRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void theCallsAreUnited() {
        engine.execute("CREATE OR REPLACE TABLE ti (i INT)");
        engine.execute("INSERT INTO ti VALUES (3), (300)");
        engine.execute("CREATE OR REPLACE TABLE tj (i INT)");
        engine.execute("INSERT INTO tj VALUES (3), (70000)");
        engine.execute("CREATE OR REPLACE FUNCTION t_div(n INT) RETURNS TABLE (a INT) AS 'SELECT n'");
        engine.execute("CREATE OR REPLACE FUNCTION t_two(n INT) RETURNS TABLE (a INT, b INT) AS 'SELECT n, n * 2'");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(ti.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM tj, TABLE(t_div(tj.i)) LIMIT 1", "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(ti.i + 1)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(ti.i * 1000)) LIMIT 1", "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(b) FROM ti, TABLE(t_two(ti.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_two(ti.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(5)) LIMIT 1", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(a + 1) FROM ti, TABLE(t_div(ti.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(-ti.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(ti.i)) WHERE ti.i > 100 LIMIT 1", "NUMBER(38,0)[SB2]"},
        });
    }

    @Test
    public void aNullCallAddsNothing() {
        engine.execute("CREATE OR REPLACE TABLE ti (i INT)");
        engine.execute("INSERT INTO ti VALUES (3), (300)");
        engine.execute("CREATE OR REPLACE TABLE tn (i INT)");
        engine.execute("INSERT INTO tn VALUES (3), (NULL), (300)");
        engine.execute("CREATE OR REPLACE FUNCTION t_div(n INT) RETURNS TABLE (a INT) AS 'SELECT n'");
        engine.execute("CREATE OR REPLACE FUNCTION t_two(n INT) RETURNS TABLE (a INT, b INT) AS 'SELECT n, n * 2'");
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(a) FROM tn, TABLE(t_div(tn.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM tn, TABLE(t_div(tn.i)) WHERE a IS NULL LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b) FROM ti, TABLE(t_two(ti.i)) LIMIT 1",
                "NUMBER(38,0)[SB2]|NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(MAX(a)) FROM ti, TABLE(t_div(ti.i))", "NUMBER(38,0)[SB2] ;; NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(ti.i + 70000)) LIMIT 1", "NUMBER(38,0)[SB4]"},
            {"SELECT SYSTEM$TYPEOF(x.a) FROM ti, TABLE(t_div(ti.i)) x LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT a FROM ti, TABLE(t_div(ti.i)) ORDER BY a", "3 ;; 300"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti CROSS JOIN TABLE(t_div(ti.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a * 2) FROM ti, TABLE(t_div(ti.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(ti.i)) WHERE a > 100 LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, TABLE(t_div(5 + ti.i)) LIMIT 1", "NUMBER(38,0)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(a) FROM ti, LATERAL (SELECT * FROM TABLE(t_div(ti.i))) LIMIT 1",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated"},
        });
    }
}
