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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * What the account refuses after the quantifier of a negated LIKE, by where the predicate stands (live-verified): an
 * operation in the list - any operator but a plus - refuses the delimiter after it; a name written first in a call's
 * list refuses the token inside its parentheses in a select list, and reads on with one parenthesis fewer in a WHERE,
 * HAVING or QUALIFY, where a comma is refused too, and in an ORDER BY or a GROUP BY; a GROUP BY's or an ORDER BY's
 * later item reads as the first; a query in parentheses whose first item is a call refuses its SELECT and that call's
 * parenthesis.
 */
public class NegatedQuantifierRecoveryTest extends MeasuredCellsTestSupport {

    /** A name first in a list inside a call, and a GROUP BY's second item. */
    @Test
    public void callsAndGroupItems() {
        assertCells(new String[][] {
            {"SELECT UPPER(c NOT LIKE ANY (f(1, 2), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected '1'. | syntax "
                + "error line 1 at position 35 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY ('x' || 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 40 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f('x', 'z'), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected ''x''. | "
                + "syntax error line 1 at position 39 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (g(f('x')), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'g'. | syntax error line 1 at position 31 unexpected 'f'. | syntax "
                + "error line 1 at position 32 unexpected '('."},
            {"SELECT UPPER(c NOT LIKE ANY ('y', f('x'))) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected ''y''. | syntax error line 1 at position 41 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f('x'))) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected ''x''."},
            {"SELECT UPPER(c NOT LIKE ANY (f('x'), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected ''x''. | "
                + "syntax error line 1 at position 40 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY ('x' || 'y', 'z')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 45 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY ('x', 'y' || 'z')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 45 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq791(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x', 'y' "
                + "|| 'z'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 46 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq791(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' || "
                + "'y', 'z'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 41 unexpected ','."},
            {"CREATE OR REPLACE FUNCTION eq791(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' || "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 41 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq791(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY (f('x'), "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected 'f'."},
            {"CREATE OR REPLACE FUNCTION eq791(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY (f(1, 2), "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected 'f'."},
            {"CREATE OR REPLACE FUNCTION eq791(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' + 1); "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c, c NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected ''x''. | syntax error line 1 at position 65 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c, c NOT ILIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected 'ANY'. | syntax error "
                + "line 1 at position 58 unexpected ''x''. | syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c, c, c NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ANY'. | syntax error "
                + "line 1 at position 60 unexpected ''x''. | syntax error line 1 at position 68 unexpected ')'."},
        });
    }

    /** Operations in the list, by clause, in a function body too. */
    @Test
    public void operationsAndClauses() {
        assertCells(new String[][] {
            {"SELECT 'a' NOT LIKE ANY ('x' || 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 35 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY ('x' + 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''."},
            {"SELECT 1 WHERE 'a' NOT LIKE ANY ('x' || 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ANY'. | syntax error "
                + "line 1 at position 33 unexpected ''x''. | syntax error line 1 at position 43 unexpected ')'."},
            {"SELECT 1 WHERE 'a' NOT LIKE ANY ('x', 'y' || 'z')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ANY'. | syntax error "
                + "line 1 at position 33 unexpected ''x''. | syntax error line 1 at position 48 unexpected ')'."},
            {"SELECT 1 WHERE 'a' NOT LIKE ANY ('x' || 'y') AND 1 = 1",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ANY'. | syntax error "
                + "line 1 at position 33 unexpected ''x''. | syntax error line 1 at position 43 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' || "
                + "'y' || 'z'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 48 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' || "
                + "f('y')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 44 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY (f('x') || "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected 'f'. | syntax error line 1 at position 44 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' || "
                + "('y')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 43 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' - "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 40 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' * 2); "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 38 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' = "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 40 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' AND "
                + "TRUE); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 43 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' || "
                + "'y' + 1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 45 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x', 'y') "
                + "|| 'z'; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY "
                + "('x'::VARCHAR); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' || "
                + "'y', 'z' || 'w'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 41 unexpected ','."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN nosuch(a NOT LIKE ANY "
                + "('x' || 'y')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 at "
                + "position 33 unexpected 'ANY'. | syntax error line 1 at position 38 unexpected ''x''. | syntax error "
                + "line 1 at position 48 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq792(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN nosuch(a NOT LIKE ANY "
                + "('x' || 'y', 'z')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 at "
                + "position 33 unexpected 'ANY'. | syntax error line 1 at position 38 unexpected ''x''. | syntax error "
                + "line 1 at position 48 unexpected ','."},
            {"SELECT UPPER(c NOT LIKE ANY (f(), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 37 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f(g('x')), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected 'g'. | syntax "
                + "error line 1 at position 32 unexpected '('."},
            {"SELECT LOWER(UPPER(c NOT LIKE ANY (f('x'), 'y'))) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'ANY'. | syntax error "
                + "line 1 at position 35 unexpected 'f'. | syntax error line 1 at position 37 unexpected ''x''. | "
                + "syntax error line 1 at position 46 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE UPPER(c NOT LIKE ANY (f('x'), 'y')) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected 'f'. | syntax error line 1 at position 63 unexpected ','."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY UPPER(c NOT LIKE ANY (f('x'), 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ANY'. | syntax error "
                + "line 1 at position 60 unexpected 'f'. | syntax error line 1 at position 71 unexpected ')'."},
            {"SELECT IFF('a' NOT LIKE ANY (f('x'), 'y'), 1, 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected ''x''. | "
                + "syntax error line 1 at position 40 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f('x', 'y'), 'z'), 1) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected ''x''. | "
                + "syntax error line 1 at position 39 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (a, 'y')) FROM (SELECT 'a' c, 'b' a)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'a'. | syntax error line 1 at position 36 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f('x') || 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected ''x''. | "
                + "syntax error line 1 at position 42 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f(1, 2, 3), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected '1'. | syntax "
                + "error line 1 at position 38 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f('x')), 1) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected ''x''. | "
                + "syntax error line 1 at position 39 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f(('x')), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected '('. | syntax "
                + "error line 1 at position 42 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY (f(a.b), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected 'a'. | syntax "
                + "error line 1 at position 32 unexpected '.'."},
            {"SELECT (SELECT UPPER('a' NOT LIKE ANY (f('x'), 'y')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '('."},
            {"SELECT ARRAY_CONSTRUCT('a' NOT LIKE ANY (f(1, 2), 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'ANY'. | syntax error "
                + "line 1 at position 41 unexpected 'f'. | syntax error line 1 at position 43 unexpected '1'. | syntax "
                + "error line 1 at position 47 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c, c NOT LIKE ANY ('x')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected ''x''."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c, UPPER(c NOT LIKE ANY ('x', 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'ANY'. | syntax error "
                + "line 1 at position 63 unexpected ''x''. | syntax error line 1 at position 72 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c, c NOT LIKE ANY (f('x'), 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected 'f'. | syntax error line 1 at position 68 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY ROLLUP (c, c NOT LIKE ANY ('x', 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'ANY'. | syntax error "
                + "line 1 at position 65 unexpected ''x''. | syntax error line 1 at position 74 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY c, c NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected ''x''. | syntax error line 1 at position 65 unexpected ')'."},
            {"SELECT 'b', 'a' NOT LIKE ANY ('x', 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'ANY'. | syntax error "
                + "line 1 at position 30 unexpected ''x''. | syntax error line 1 at position 38 unexpected ')'."},
            {"SELECT UPPER(c NOT LIKE ANY ('x' || 'y', f('z'))) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 48 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c, c NOT LIKE ANY ('x' || 'y', 'z')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected ''x''. | syntax error line 1 at position 72 unexpected ')'."},
        });
    }

    /** Name-first lists inside calls by clause, operators, scalar queries. */
    @Test
    public void callsByClause() {
        assertCells(new String[][] {
            {"SELECT 1 FROM (SELECT 'a' c) WHERE UPPER(c NOT LIKE ANY (f('x'))) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected 'f'. | syntax error line 1 at position 63 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE UPPER(c NOT LIKE ANY (f('x'), 'y', 'z')) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected 'f'. | syntax error line 1 at position 63 unexpected ','."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE UPPER(c NOT LIKE ANY (f(1, 2), 'y')) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected 'f'. | syntax error line 1 at position 64 unexpected ','."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE UPPER(c NOT LIKE ANY (f('x') || 'y')) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected 'f'. | syntax error line 1 at position 70 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE UPPER(c NOT LIKE ANY (a, 'y')) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected 'ANY'. | syntax error "
                + "line 1 at position 57 unexpected 'a'. | syntax error line 1 at position 64 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE LOWER(UPPER(c NOT LIKE ANY (f('x'), 'y'))) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'ANY'. | syntax error "
                + "line 1 at position 63 unexpected 'f'. | syntax error line 1 at position 75 unexpected ')'."},
            {"SELECT 1 FROM (SELECT 'a' c) WHERE IFF(c NOT LIKE ANY (f('x'), 'y'), TRUE, FALSE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'ANY'. | syntax error "
                + "line 1 at position 55 unexpected 'f'. | syntax error line 1 at position 61 unexpected ','."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c HAVING UPPER(c NOT LIKE ANY (f('x'), 'y')) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'ANY'. | syntax error "
                + "line 1 at position 69 unexpected 'f'. | syntax error line 1 at position 75 unexpected ','."},
            {"SELECT c FROM (SELECT 'a' c) QUALIFY UPPER(c NOT LIKE ANY (f('x'), 'y')) IS NULL",
                "REFUSED SQL compilation error: | syntax error line 1 at position 54 unexpected 'ANY'. | syntax error "
                + "line 1 at position 59 unexpected 'f'. | syntax error line 1 at position 65 unexpected ','."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY UPPER(c NOT LIKE ANY (f('x')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ANY'. | syntax error "
                + "line 1 at position 60 unexpected 'f'. | syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY UPPER(c NOT LIKE ANY (f(1, 2), 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ANY'. | syntax error "
                + "line 1 at position 60 unexpected 'f'. | syntax error line 1 at position 72 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY UPPER(c NOT LIKE ANY (f('x'), 'y', 'z'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ANY'. | syntax error "
                + "line 1 at position 60 unexpected 'f'. | syntax error line 1 at position 76 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY LOWER(UPPER(c NOT LIKE ANY (f('x'), 'y')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'ANY'. | syntax error "
                + "line 1 at position 66 unexpected 'f'. | syntax error line 1 at position 78 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY UPPER(c NOT LIKE ANY (a, 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ANY'. | syntax error "
                + "line 1 at position 60 unexpected 'a'. | syntax error line 1 at position 67 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY UPPER(c NOT LIKE ANY (f('x'), 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 55 unexpected 'ANY'. | syntax error "
                + "line 1 at position 60 unexpected 'f'. | syntax error line 1 at position 71 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c, UPPER(c NOT LIKE ANY (f('x'), 'y'))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 58 unexpected 'ANY'. | syntax error "
                + "line 1 at position 63 unexpected 'f'. | syntax error line 1 at position 74 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY (a.b, 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected 'a'. | syntax error line 1 at position 26 unexpected '.'."},
            {"SELECT 'a' NOT LIKE ANY (f(), 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected 'f'. | syntax error line 1 at position 26 unexpected '('."},
            {"SELECT UPPER(c NOT LIKE ANY (f(a.b.c), 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected 'a'. | syntax "
                + "error line 1 at position 32 unexpected '.'."},
            {"SELECT UPPER(c NOT LIKE ANY (f(x, 'y'), 'z')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected 'f'. | syntax error line 1 at position 31 unexpected 'x'. | syntax "
                + "error line 1 at position 37 unexpected ')'."},
            {"SELECT (SELECT UPPER('a' NOT LIKE ANY ('x', 'y')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '('."},
            {"SELECT (SELECT LOWER(UPPER('a' NOT LIKE ANY ('x'))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '('."},
            {"SELECT (SELECT 1, UPPER('a' NOT LIKE ANY ('x')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 37 unexpected 'ANY'. | syntax error line 1 at position 42 unexpected "
                + "''x''. | syntax error line 1 at position 47 unexpected ')'."},
            {"SELECT (SELECT UPPER('a' NOT LIKE ANY (f(1, 2), 'y')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '('."},
            {"SELECT 'a' NOT LIKE ANY ('x' - 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 34 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY ('x' = 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 34 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY ('x' AND TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 37 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY ('x' / 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 32 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY ('x' OR TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 36 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY ('x' < 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 34 unexpected ')'."},
            {"SELECT 'a' NOT LIKE ANY (1 + 2)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected '1'."},
            {"SELECT 'a' NOT LIKE ANY ('x' - 1, 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'ANY'. | syntax error "
                + "line 1 at position 25 unexpected ''x''. | syntax error line 1 at position 37 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq793(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' / 2); "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 38 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq793(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' % 2); "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 38 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq793(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' OR "
                + "TRUE); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 42 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq793(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' < "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 40 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq793(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' + "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eq793(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY (1 + 2); "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected '1'."},
            {"CREATE OR REPLACE FUNCTION eq793(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY ('x' - 1); "
                + "END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''. | syntax error line 1 at position 38 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eq793(a VARCHAR) RETURNS BOOLEAN AS $$ BEGIN RETURN a NOT LIKE ANY (('x') || "
                + "'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected '('. | syntax error line 1 at position 43 unexpected ')'."},
            {"SELECT 1 WHERE 'a' NOT LIKE ANY ('x' - 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ANY'. | syntax error "
                + "line 1 at position 33 unexpected ''x''. | syntax error line 1 at position 42 unexpected ')'."},
            {"SELECT 1 WHERE 'a' NOT LIKE ANY ('x' + 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ANY'. | syntax error "
                + "line 1 at position 33 unexpected ''x''."},
            {"SELECT UPPER(c NOT LIKE ANY ('x' - 'y')) FROM (SELECT 'a' c)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'ANY'. | syntax error "
                + "line 1 at position 29 unexpected ''x''. | syntax error line 1 at position 39 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) ORDER BY c NOT LIKE ANY ('x' || 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'ANY'. | syntax error "
                + "line 1 at position 54 unexpected ''x''. | syntax error line 1 at position 64 unexpected ')'."},
            {"SELECT c FROM (SELECT 'a' c) GROUP BY c NOT LIKE ANY ('x' || 'y')",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'ANY'. | syntax error "
                + "line 1 at position 54 unexpected ''x''. | syntax error line 1 at position 64 unexpected ')'."},
        });
    }
}
