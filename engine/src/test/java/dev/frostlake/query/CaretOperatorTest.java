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
 * The caret between two values is an operator the account reads and never runs: a statement that writes one
 * is refused while it compiles — "Unsupported feature '^'." — before any of its names is looked up, in a
 * select list, a WHERE or HAVING clause, an ORDER BY, a view's query, an INSERT's values and under EXPLAIN,
 * and a function's expression as the function's compilation failing. A caret left without an operand is the
 * syntax error it is, at the end of the text or at the caret (live-verified).
 */
public class CaretOperatorTest extends MeasuredCellsTestSupport {

    @Test
    public void aCaretBetweenTwoValues() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2 ^ 2");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2 ^ 2 FROM nb_t");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 1 WHERE 2 ^ 2 = 4");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '-'",
                "SELECT - -1");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '+'",
                "SELECT + +1");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid function '-'",
                "SELECT - +1");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '<EOF>'.",
                "SELECT 2 ^");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '^'.",
                "SELECT ^ 2");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '^'.",
                "SELECT 2 ^ ^ 2");
        } finally {
            answer(
                "DROP FUNCTION IF EXISTS eu(INT)");
        }
    }

    @Test
    public void whereverAStatementWritesOne() {
        try {
            answer(
                "CREATE OR REPLACE TABLE nb_t (a INT)");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2 ^ 2 ^ 2");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT (2 ^ 2)");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2^2");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 'a' ^ 'b'");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2 ^ 2 FROM nosuch_table_zq7");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT nosuch_fn_zq7(2 ^ 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected '<EOF>'.",
                "SELECT 2 ^ 2 +");
            assertAnswer(
                "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '^'.",
                "SELECT 1 + ^ 2");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT a FROM nb_t WHERE a ^ 2 = 4 ORDER BY 1");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "EXPLAIN SELECT 2 ^ 2");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "CREATE OR REPLACE VIEW nb_v AS SELECT 2 ^ 2 AS c");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "INSERT INTO nb_t VALUES (2 ^ 2)");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2 ^ 2, nosuch_col_zq7 FROM nb_t");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT nosuch_col_zq7 ^ 2 FROM nb_t");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 1 ORDER BY 2 ^ 2");
            assertAnswer(
                "REFUSED Compilation of SQL UDF failed: Unsupported feature '^'.",
                "CREATE OR REPLACE FUNCTION eu2(a INT) RETURNS INT AS 'a ^ 2'");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT a FROM nb_t GROUP BY a HAVING a ^ 2 > 1");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2 ^ 2 AS c, 1/0");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT - 2 ^ 2");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2 ^ - 2");
            assertAnswer(
                "REFUSED Unsupported feature '^'.",
                "SELECT 2 ^ 2;");
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
