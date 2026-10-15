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
import org.junit.jupiter.api.Test;

/**
 * A query-bodied SQL function whose body the account cannot evaluate correlated is refused over a row's value however
 * the call passes it - by place, by name, or both - and whichever parameter takes it, the others holding their
 * constants; and a body that is an expression is refused for the correlated subquery it holds, named as its own
 * function (live-verified).
 */
public class SqlFunctionNamedArgumentCallTest extends BaseDatabaseTest {

    private static final String REFUSED = "SQL compilation error:|"
        + "Unsupported subquery type cannot be evaluated inside Function object: ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE o (k INT, x INT)");
        engine.execute("INSERT INTO o VALUES (1, 15), (2, 1), (4, 0)");
        engine.execute("CREATE FUNCTION nw(a NUMBER, b NUMBER) RETURNS NUMBER AS 'SELECT a + 1 WHERE a > 0'");
        engine.execute("CREATE FUNCTION nd(a NUMBER, b NUMBER DEFAULT 7) RETURNS NUMBER AS "
            + "'SELECT a + b WHERE a > 0'");
        engine.execute("CREATE FUNCTION w5(a NUMBER) RETURNS NUMBER AS '(SELECT a + 1 WHERE a > 0) * 10'");
        engine.execute("CREATE FUNCTION w6(a NUMBER) RETURNS NUMBER AS '(SELECT a + 1) * 10'");
        engine.execute("CREATE FUNCTION w7(p INT) RETURNS INT AS '(SELECT x FROM o WHERE k = p) + 0'");
        engine.execute("CREATE FUNCTION w11(a NUMBER) RETURNS NUMBER AS 'nw(b => 1, a => a) * 10'");
        engine.execute("CREATE FUNCTION w12(a NUMBER) RETURNS NUMBER AS 'SELECT nw(b => 1, a => a) * 10'");
        engine.execute("CREATE FUNCTION w13(a NUMBER) RETURNS NUMBER AS 'SELECT w5(a) + 1'");
    }

    @Test
    public void aNamedArgumentReadingTheRowIsJudgedAsAPositionalOne() {
        assertCells(engine, new String[][] {
            {"SELECT nw(o.k, 1) FROM o", REFUSED + "NW"},
            {"SELECT nw(b => 1, a => o.k) FROM o", REFUSED + "NW"},
            {"SELECT nw(a => o.k, b => 1) FROM o", REFUSED + "NW"},
            {"SELECT nw(o.k, b => 1) FROM o", REFUSED + "NW"},
            {"SELECT k, nw(b => o.k, a => 1) FROM o ORDER BY k", "1, 2 | 2, 2 | 4, 2"},
            {"SELECT nd(o.k) FROM o", REFUSED + "ND"},
            {"SELECT nd(a => o.k) FROM o", REFUSED + "ND"},
            {"SELECT k, nd(1, o.k) FROM o ORDER BY k", REFUSED + "ND"},
            {"SELECT k, nd(a => 1, b => o.k) FROM o ORDER BY k", REFUSED + "ND"},
            {"SELECT nd(a => 1, b => 2)", "3"},
        });
    }

    @Test
    public void anExpressionBodyIsJudgedForTheQueriesItHolds() {
        assertCells(engine, new String[][] {
            {"SELECT w5(5)", "60"},
            {"SELECT w5(o.k) FROM o", REFUSED + "W5"},
            {"SELECT k, w6(k) FROM o ORDER BY k", "1, 20 | 2, 30 | 4, 50"},
            {"SELECT w7(1)", "15"},
            {"SELECT w7(o.k) FROM o", REFUSED + "W7"},
            {"SELECT w11(5), w12(5)", "60, 60"},
            {"SELECT w11(o.k) FROM o", REFUSED + "NW"},
            {"SELECT w12(o.k) FROM o", REFUSED + "W12"},
            {"SELECT w13(o.k) FROM o", REFUSED + "W13"},
        });
    }
}
