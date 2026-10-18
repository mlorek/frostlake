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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Snowscript body a SQL function cannot read is read as its expression, and the account reads its first bracket
 * on from the token refused there: a quantifier no predicate takes is refused with the token that opens its list,
 * a second test after a finished IS test is refused and ends the reading, and after an operator, a sign, a finished
 * IS NULL, a LIKE with its pattern or a query in parentheses the next comma or the bracket's own closing parenthesis
 * is refused; names, dots, literals, calls and a truth test ask nothing. At the frame's own level the doubled test and
 * the quantifier are refused as in the bracket. A body that reads is refused whole, its first expression the account
 * does not build in echoed as parsed, and one the account builds is created (live-verified).
 */
public class SnowscriptBodyBracketTest extends MeasuredCellsTestSupport {

    @Test
    public void theFirstBracketIsReadOn() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXISTS (SELECT 1) = "
                + "TRUE); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'EXISTS'. | syntax error "
                + "line 1 at position 46 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 32 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE ANY ('x', "
                + "'y')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 33 unexpected 'ANY'. | syntax error line 1 at position 38 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ILIKE ALL ('x', 'y') "
                + "AND EXISTS (SELECT 1)); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 35 unexpected ''x''. | syntax error line 1 at position 66 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a NOT LIKE ANY ('x', 'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NOT NULL IS "
                + "NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 36 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c + 1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 31 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS TRUE); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUBSTRING('abc' FROM 1 "
                + "FOR 2)); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'SUBSTRING'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(0x1F); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected '0x1F'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT ILIKE ALL "
                + "('x')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 34 unexpected 'ALL'. | syntax error line 1 at position 39 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL IS NOT "
                + "NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 32 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS TRUE IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (a IS NULL IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 16 unexpected 'a'. | syntax error line 1 "
                + "at position 26 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a IS NULL IS NULL; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 25 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a NOT ILIKE ALL ('x'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 27 unexpected 'ALL'. | syntax error line 1 "
                + "at position 32 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a + 1 IS NULL IS NULL; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 29 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE ANY ('x', "
                + "'y') + 1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 33 unexpected 'ANY'. | syntax error line 1 at position 38 unexpected ''x''. | syntax "
                + "error line 1 at position 51 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a, b IS NULL IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 35 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NOT DISTINCT FROM "
                + "1 IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 47 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ILIKE ALL ('x', "
                + "'y')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 35 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ILIKE ALL ('x', 'y') "
                + "AND TRUE); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 35 unexpected ''x''. | syntax error line 1 at position 53 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE ANY ('x')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 33 unexpected 'ANY'. | syntax error line 1 at position 38 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE ALL ('x', "
                + "'y') OR TRUE); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 33 unexpected 'ALL'. | syntax error line 1 at position 38 unexpected ''x''. | syntax "
                + "error line 1 at position 55 unexpected ')'."},
        });
    }

    @Test
    public void aBracketAsksForItsDelimiter() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c, 1 + 1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 34 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c, -1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 31 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 IS NULL IS "
                + "NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 36 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL IS NULL + "
                + "1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 32 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c * 2); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 31 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c || 'x'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 34 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c AND TRUE); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 36 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c, 1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXISTS (SELECT 1) IS "
                + "NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'EXISTS'. | syntax error "
                + "line 1 at position 47 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a NOT LIKE ANY ('x', 'y') + "
                + "1; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 26 unexpected 'ANY'. | syntax error line 1 "
                + "at position 31 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN (a NOT LIKE ANY ('x')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 16 unexpected 'a'. | syntax error line 1 "
                + "at position 27 unexpected 'ANY'. | syntax error line 1 at position 32 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a IS NOT NULL IS NULL; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 29 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a) IS NULL IS NULL; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 33 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c) + 1; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c + 1) + 1; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 31 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c + 1, 2); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 31 unexpected ','."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ILIKE ALL ('x') + "
                + "1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 35 unexpected ''x''. | syntax error line 1 at position 43 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a ILIKE ALL ('x', 'y'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 28 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS DISTINCT FROM 1 "
                + "IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 43 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c = 1 IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 39 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c - 1); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 31 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c, b IS NULL IS "
                + "NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 39 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a IS NULL IS NULL)); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected '('. | syntax error line 1 "
                + "at position 33 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a IS NULL IS NULL + 1; END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 25 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE ANY ('x') "
                + "IS NULL IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 33 unexpected 'ANY'. | syntax error line 1 at position 38 unexpected ''x''. | syntax "
                + "error line 1 at position 51 unexpected 'IS'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a RLIKE ANY ('x', "
                + "'y')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 30 unexpected 'ANY'. | syntax error line 1 at position 35 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT RLIKE ALL ('x', "
                + "'y')); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 34 unexpected 'ALL'. | syntax error line 1 at position 39 unexpected ''x''."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c NOT LIKE 'x'); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 40 unexpected ')'."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c IS NULL); END $$",
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position 8 "
                + "unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'. | syntax error line 1 "
                + "at position 35 unexpected ')'."},
        });
    }

    @Test
    public void aBodyThatReadsIsEchoedWhole() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A = 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a AND TRUE); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A AND TRUE)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a || 'x'); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A || 'x')"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a, 1 + 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A, 1 + 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a, 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A, 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IS NULL)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL = TRUE); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A IS NULL) = TRUE)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE 'x'); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE 'x')"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE 'x'); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A LIKE 'x'))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE ANY ('x', "
                + "'y')); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE_ANY null LIKE_ANY 'x' LIKE_ANY 'y')"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 2)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, 2))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT IN (1, 2)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A IN (1, 2)))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a BETWEEN 1 AND 2); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(BETWEEN(A, 1, 2))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((SELECT 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((SELECT 1 AS \"1\" FROM (VALUES (null)) "
                + "DUAL))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + (SELECT 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(\"+\"(A, (SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL)))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXISTS (SELECT 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(EXISTS(SELECT 1 AS \"1\" FROM (VALUES "
                + "(null)) DUAL))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(EXISTS (SELECT 1) AND "
                + "TRUE); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(EXISTS(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL), TRUE))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 + 2); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A + 1) + 2)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a * 2 IS NULL); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A * 2 IS NULL)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NULL AND a IS "
                + "NULL); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A IS NULL) AND (A IS NULL))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS DISTINCT FROM 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(EQUAL_NULL(A, 1)))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-a + 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((NEGATE(A)) + 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1 + 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1 + 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a::INT); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CAST(A AS NUMBER(38,0)))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(f(a) + 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((F(A)) + 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b + 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a RLIKE 'x'); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A RLIKE 'x')"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(CASE WHEN a THEN 1 "
                + "END); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(CASE(A, 1, null))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1) + 1; END $$",
                "REFUSED Unsupported expression for Snowscript UDF: (NOSUCH(A + 1)) + 1"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1, 2); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A + 1, 2)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a = 1 = 2); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH((A = 1) = 2)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a LIKE ANY ('x', 'y'); END $$",
                "Function EU successfully created."},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT LIKE 'x' ESCAPE "
                + "'y'); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A LIKE 'x' LIKE 'y'))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a, EXISTS (SELECT 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A, EXISTS(SELECT 1 AS \"1\" FROM "
                + "(VALUES (null)) DUAL))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT BETWEEN 1 AND "
                + "2); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(BETWEEN(A, 1, 2)))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a + 1 AND EXISTS "
                + "(SELECT 1)); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(AND(\"+\"(A, 1), EXISTS(SELECT 1 AS "
                + "\"1\" FROM (VALUES (null)) DUAL)))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a > 1); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A > 1)"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT a); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A))"},
            {"CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE ALL ('x')); END $$",
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE_ALL null LIKE_ALL 'x')"},
        });
    }
}
