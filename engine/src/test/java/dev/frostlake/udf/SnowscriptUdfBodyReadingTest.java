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
 * What a Snowscript function's block may not write, told as the function is created. A sign written straight
 * after a sign is an invalid function named by the outer sign, and a caret between two values an unsupported
 * feature, before any statement or call is judged. A word before a string inside a call's parentheses — where
 * DATE, TIME and TIMESTAMP alone open a literal — and a name of three parts are no expressions of a block: the
 * body is read as an expression instead and refused in that frame, at the RETURN and at the token after the
 * first parenthesis. A membership list prints its plain members after a computed one in the list the plain
 * ones share (live-verified).
 */
public class SnowscriptUdfBodyReadingTest extends MeasuredCellsTestSupport {

    @Test
    public void bodiesTheBlockDoesNotRead() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected '0x1F'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(0x1F); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected ''a''.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch('a' 'b'); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS TRUE); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IS NOT FALSE); END "
                    + "$$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'SUBSTRING'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(SUBSTRING('abc' FROM "
                    + "1 FOR 2)); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'TIMESTAMP_LTZ'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIMESTAMP_LTZ "
                    + "'2020-01-02 03:04:05'); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'TIMESTAMP_NTZ'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIMESTAMP_NTZ "
                    + "'2020-01-02 03:04:05'); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'TIMESTAMP_TZ'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIMESTAMP_TZ "
                    + "'2020-01-02 03:04:05'); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '-'",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(- -a); END $$");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ^ 2); END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1 IN (1 + 1), 3))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 1 + 1, 3)); "
                    + "END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A)",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a); END $$");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a ^ 2; END $$");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '-'",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN - -a; END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a IS TRUE; END $$");
            assertAnswer(
                "Function EU successfully created.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN DATE '2020-01-02'; END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_DATE('2020-01-02'))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE '2020-01-02'); "
                    + "END $$");
            answer(
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIMESTAMP '2020-01-02 "
                    + "03:04:05'); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'NOT'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(NOT a IS TRUE); END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A LIKE_ANY null LIKE_ANY 'x' LIKE_ANY "
                    + "'y')",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a LIKE ANY ('x', "
                    + "'y')); END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A)",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '+'",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(+ +a); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '-'",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(- +a); END $$");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a ^ 2 ^ 3); END $$");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS eu(INT)");
        }
    }

    @Test
    public void moreSignsNamesAndLiterals() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "Function EU successfully created.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a IN (1 + 1, 2, 3 + 3); END "
                    + "$$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1 IN (1 + 1) IN (2 + 2), 3))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 1 + 1, 2 + 2,"
                    + " 3)); END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1 + 1) IN (2, 3))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1 + 1, 2, 3)); "
                    + "END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(A IN (1, 2 IN (1 + 1), 3, 4))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a IN (1, 2, 1 + 1, 3, "
                    + "4)); END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NEGATE(NEGATE(A)))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(-(-a)); END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(1 - (NEGATE(A)))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1 - -a); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '-'",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN - - -a; END $$");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a) ^ 2; END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b.c.d); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN a.b.c; END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected '1'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1, a.b.c); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'foo'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(foo 'x'); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected '1'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(1, TIMESTAMP_LTZ "
                    + "'2020-01-02 03:04:05'); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'DATE'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(DATE '2020-01-02', "
                    + "a.b.c); END $$");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN 2 ^ 2 + nosuch(a); END $$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(TO_TIME('03:04:05'))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(TIME '03:04:05'); END "
                    + "$$");
            assertAnswer(
                "REFUSED Unsupported expression for Snowscript UDF: NOSUCH(NOT(A IN (1 IN (1 + 1), 3)))",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a NOT IN (1, 1 + 1, "
                    + "3)); END $$");
            assertAnswer(
                "Function EU successfully created.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN -(- a); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '-'",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a, - - a); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN TIMESTAMP_LTZ '2020-01-02 "
                    + "03:04:05'; END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected 'a'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch(a.b, a.b.c); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 22 unexpected '('.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN nosuch((a.b.c)); END $$");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: SQL compilation error: | syntax error line 1 at position "
                    + "8 unexpected 'RETURN'. | syntax error line 1 at position 19 unexpected 'a'.",
                "CREATE OR REPLACE FUNCTION eu(a INT) RETURNS INT AS $$ BEGIN RETURN ABS(a.b.c); END $$");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS eu(INT)");
            answer(
                "DROP FUNCTION IF EXISTS eu2(INT)");
            answer(
                "DROP VIEW IF EXISTS nb_v");
        }
    }
}
