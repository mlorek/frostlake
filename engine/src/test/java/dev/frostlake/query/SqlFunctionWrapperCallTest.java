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
 * A SQL function that hands its own parameter to a query-bodied SQL function is judged by what its call passes: over
 * a constant every wrapper answers and an expression body is created, while a call that passes a row's value is
 * refused — named as the wrapper where its body is a query, as the function it calls where its body is an
 * expression (live-verified).
 */
public class SqlFunctionWrapperCallTest extends BaseDatabaseTest {

    private static final String REFUSED = "SQL compilation error:|"
        + "Unsupported subquery type cannot be evaluated inside Function object: ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE o (k INT, x INT)");
        engine.execute("INSERT INTO o VALUES (1, 15), (2, 1), (4, 0)");
        engine.execute("CREATE FUNCTION sq_where(a NUMBER) RETURNS NUMBER AS 'SELECT a + 1 WHERE a > 0'");
        engine.execute("CREATE FUNCTION w_query(a NUMBER) RETURNS NUMBER AS 'SELECT sq_where(a) * 10'");
        engine.execute("CREATE FUNCTION w_tab(a NUMBER) RETURNS TABLE (r NUMBER) AS 'SELECT sq_where(a) AS r'");
        engine.execute("CREATE FUNCTION lookup_x(p INT) RETURNS INT AS 'SELECT x FROM o WHERE k = p'");
        engine.execute("CREATE FUNCTION label_x(p INT) RETURNS VARCHAR AS "
            + "'SELECT IFF(lookup_x(p) > 5, ''big'', ''small'')'");
        engine.execute("CREATE FUNCTION w9(a NUMBER, b NUMBER) RETURNS NUMBER AS 'SELECT sq_where(b) + a'");
        engine.execute("CREATE FUNCTION w8(a NUMBER) RETURNS NUMBER AS 'SELECT COALESCE(sq_where(a), 0)'");
    }

    @Test
    public void aWrapperOverAConstantAnswers() {
        assertCells(engine, new String[][] {
            {"CREATE FUNCTION w_expr(a NUMBER) RETURNS NUMBER AS 'sq_where(a) * 10'",
                "Function W_EXPR successfully created."},
            {"CREATE FUNCTION w_iff(a NUMBER) RETURNS NUMBER AS 'IFF(a IS NULL, 0, sq_where(a))'",
                "Function W_IFF successfully created."},
            {"SELECT w_expr(5), w_iff(5), w_query(5), w9(1, 5), w8(5)", "60, 6, 60, 7, 6"},
            {"SELECT * FROM TABLE(w_tab(5))", "6"},
            {"SELECT label_x(1)", "big"},
            {"SELECT k, w9(k, 5) FROM o ORDER BY k", "1, 7 | 2, 8 | 4, 10"},
        });
    }

    @Test
    public void aWrapperPassingARowsValueIsRefusedByItsName() {
        engine.execute("CREATE FUNCTION w_expr(a NUMBER) RETURNS NUMBER AS 'sq_where(a) * 10'");
        assertCells(engine, new String[][] {
            {"SELECT w_query(o.k) FROM o", REFUSED + "W_QUERY"},
            {"SELECT k, label_x(k) FROM o ORDER BY k", REFUSED + "LABEL_X"},
            {"SELECT k, w9(5, k) FROM o ORDER BY k", REFUSED + "W9"},
            {"SELECT w_expr(k) FROM o", REFUSED + "SQ_WHERE"},
        });
    }
}
