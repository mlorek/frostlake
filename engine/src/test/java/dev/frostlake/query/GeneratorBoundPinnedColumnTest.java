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
 * A GENERATOR bound reading a column the statistics pin to one value reads that value: a table whose rows hold
 * one value, or NULL alone, a derived table, a CTE or a view passing it on or projecting a literal, a scalar,
 * EXISTS or LATERAL subquery reading it, and the kept side of an outer join; two values, an empty table, a NULL
 * beside a value, a VALUES list and the side an outer join extends stay refused (all live-verified).
 */
public class GeneratorBoundPinnedColumnTest extends MeasuredCellsTestSupport {

    @Test
    public void pinnedColumnBounds() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g2 (n INT)");
        engine.execute("INSERT INTO g2 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE g3 (n INT)");
        engine.execute("INSERT INTO g3 VALUES (5)");
        engine.execute("INSERT INTO g3 VALUES (5)");
        engine.execute("CREATE OR REPLACE TABLE g4 (n INT)");
        engine.execute("INSERT INTO g4 VALUES (NULL)");
        engine.execute("CREATE OR REPLACE TABLE g5 (s VARCHAR)");
        engine.execute("INSERT INTO g5 VALUES ('3')");
        engine.execute("CREATE OR REPLACE TABLE g6 (n INT, m INT)");
        engine.execute("INSERT INTO g6 VALUES (2, 7)");
        engine.execute("CREATE OR REPLACE TABLE g7 (n INT)");
        engine.execute("CREATE OR REPLACE TABLE g8 (n INT)");
        engine.execute("INSERT INTO g8 VALUES (4), (NULL)");
        engine.execute("CREATE OR REPLACE TABLE g9 (n NUMBER(10,2))");
        engine.execute("INSERT INTO g9 VALUES (2.5)");
        engine.execute("CREATE OR REPLACE TABLE g10 (n INT)");
        engine.execute("INSERT INTO g10 VALUES (6), (7)");
        engine.execute("DELETE FROM g10 WHERE n = 7");
        engine.execute("CREATE OR REPLACE TABLE g11 (n INT)");
        engine.execute("INSERT INTO g11 VALUES (1), (2)");
        engine.execute("UPDATE g11 SET n = 3");
        engine.execute("CREATE OR REPLACE VIEW gv1 AS SELECT n FROM g1");
        assertAnswer("2", "SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
        assertAnswer("1", "SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => g1.n))");
        assertAnswer("2", "SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => n + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'G2.N + 1'",
            "SELECT COUNT(*) FROM g2, TABLE(GENERATOR(ROWCOUNT => g2.n + 1))");
        assertAnswer("10", "SELECT COUNT(*) FROM g3, TABLE(GENERATOR(ROWCOUNT => g3.n))");
        assertAnswer("0", "SELECT COUNT(*) FROM g4, TABLE(GENERATOR(ROWCOUNT => g4.n))");
        assertAnswer("REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter 'ROWCOUNT'",
            "SELECT COUNT(*) FROM g5, TABLE(GENERATOR(ROWCOUNT => g5.s))");
        assertAnswer("9", "SELECT COUNT(*) FROM g6, TABLE(GENERATOR(ROWCOUNT => g6.n + g6.m))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'G7.N'",
            "SELECT COUNT(*) FROM g7, TABLE(GENERATOR(ROWCOUNT => g7.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'G8.N'",
            "SELECT COUNT(*) FROM g8, TABLE(GENERATOR(ROWCOUNT => g8.n))");
        assertAnswer("2", "SELECT COUNT(*) FROM g9, TABLE(GENERATOR(ROWCOUNT => g9.n))");
        assertAnswer("6", "SELECT COUNT(*) FROM g10, TABLE(GENERATOR(ROWCOUNT => g10.n))");
        assertAnswer("6", "SELECT COUNT(*) FROM g11, TABLE(GENERATOR(ROWCOUNT => g11.n))");
        assertAnswer("2", "SELECT COUNT(*) FROM g1 x, TABLE(GENERATOR(ROWCOUNT => x.n + 1))");
        assertAnswer("4", "SELECT COUNT(*) FROM g1, g2, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
        assertAnswer("2", "SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => g1.n + 1)) WHERE g1.n > 0");
        assertAnswer("2", "SELECT COUNT(*) FROM (SELECT * FROM g1) d, TABLE(GENERATOR(ROWCOUNT => d.n + 1))");
        assertAnswer("2", "SELECT COUNT(*) FROM gv1, TABLE(GENERATOR(ROWCOUNT => gv1.n + 1))");
        assertAnswer("2", "SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => ABS(g1.n) + 1))");
        assertAnswer("2", "SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => IFF(g1.n > 0, 2, 3)))");
        assertAnswer("1 ;; 1", "SELECT g1.n FROM g1, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
        assertAnswer("2", "SELECT COUNT(*) FROM g1 CROSS JOIN TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated",
            "SELECT * FROM g1, LATERAL (SELECT COUNT(*) AS c FROM TABLE(GENERATOR(ROWCOUNT => g1.n + 1)))");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 49 | invalid identifier 'G1.N'",
            "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => g1.n)), g1");
    }

    @Test
    public void pinnedColumnsThroughRelationsAndSubqueries() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g2 (n INT)");
        engine.execute("INSERT INTO g2 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE g3 (n INT)");
        engine.execute("INSERT INTO g3 VALUES (5), (5)");
        engine.execute("CREATE OR REPLACE TABLE g12 (f FLOAT)");
        engine.execute("INSERT INTO g12 VALUES (2.5)");
        engine.execute("CREATE OR REPLACE TABLE g13 (n INT)");
        engine.execute("INSERT INTO g13 VALUES (0)");
        engine.execute("CREATE OR REPLACE TABLE g14 (n INT)");
        engine.execute("INSERT INTO g14 VALUES (-1)");
        assertAnswer("2", "SELECT (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => g1.n + 1))) FROM g1");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CORRELATION(G2.N) + 1'",
            "SELECT (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => g2.n + 1))) FROM g2");
        assertAnswer("1", "SELECT * FROM g1 WHERE EXISTS (SELECT 1 FROM TABLE(GENERATOR(ROWCOUNT => g1.n)))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'CORRELATION(G2.N) + 1'",
            "SELECT * FROM g2, LATERAL (SELECT COUNT(*) AS c FROM TABLE(GENERATOR(ROWCOUNT => g2.n + 1)))");
        assertAnswer("1|0", "SELECT * FROM g1, LATERAL (SELECT COUNT(*) AS c FROM g2 WHERE g2.n = g1.n)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 27 | SELECT with no columns",
            "SELECT * FROM g1, LATERAL (SELECT * FROM TABLE(GENERATOR(ROWCOUNT => g1.n + 1)))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM g1, LATERAL (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => g1.n + 1)))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2.5))");
        assertAnswer("2", "SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 2.7))");
        assertAnswer("2", "SELECT COUNT(*) FROM g12, TABLE(GENERATOR(ROWCOUNT => g12.f))");
        assertAnswer("0", "SELECT COUNT(*) FROM g13, TABLE(GENERATOR(ROWCOUNT => g13.n))");
        assertAnswer("REFUSED SQL compilation error: | Invalid value [-1] for function 'GENERATOR', parameter "
                + "ROWCOUNT:  must be non-negative.",
            "SELECT COUNT(*) FROM g14, TABLE(GENERATOR(ROWCOUNT => g14.n))");
        assertAnswer("2", "WITH c AS (SELECT * FROM g1) SELECT COUNT(*) FROM c, TABLE(GENERATOR(ROWCOUNT => c.n + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'V.N'",
            "SELECT COUNT(*) FROM (VALUES (3)) v(n), TABLE(GENERATOR(ROWCOUNT => v.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT * FROM g2 WHERE n = 2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'G2.N'",
            "SELECT COUNT(*) FROM g2, TABLE(GENERATOR(ROWCOUNT => g2.n)) WHERE g2.n = 2");
        assertAnswer("4", "SELECT COUNT(*) FROM g1 JOIN g2 ON g1.n < g2.n, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM g1 LEFT JOIN g2 ON g1.n = g2.n, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
        assertAnswer("10", "SELECT COUNT(*) FROM (SELECT 5 AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
    }
}
