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
 * An operator after an EXISTS inside a call's arguments: the operator is refused, as anywhere, and the call's
 * own ')' is then one too many — of calls written inside each other the outermost's — while outside a call
 * nothing follows the operator's line (live-verified).
 */
public class ExistsCastInCallLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theCallsClosingBracketIsStacked() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        answer(
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 53 unexpected '::'. | syntax error "
                + "line 1 at position 58 unexpected ')'.",
            "SELECT f1(EXISTS (SELECT 1 FROM g WHERE g.id > fz.id)::INT) FROM fz ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '::'. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT f1(EXISTS (SELECT 1 FROM g)::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '::'. | syntax error "
                + "line 1 at position 40 unexpected ')'.",
            "SELECT ABS(EXISTS (SELECT 1 FROM g)::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '::'.",
            "SELECT EXISTS (SELECT 1 FROM g)::INT");
        assertAnswer(
            "1",
            "SELECT (EXISTS (SELECT 1 FROM g))::INT");
        assertAnswer(
            "2",
            "SELECT f1((EXISTS (SELECT 1 FROM g))::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '::'. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "SELECT f1(EXISTS (SELECT 1 FROM g)::INT, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected '::'. | syntax error "
                + "line 1 at position 42 unexpected ')'.",
            "SELECT f1(1, EXISTS (SELECT 1 FROM g)::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected '::'. | syntax error "
                + "line 1 at position 48 unexpected ')'.",
            "SELECT COALESCE(EXISTS (SELECT 1 FROM g)::INT, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '::'. | syntax error "
                + "line 1 at position 44 unexpected ')'.",
            "SELECT f1(ABS(EXISTS (SELECT 1 FROM g)::INT))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'F1': (BOOLEAN)",
            "SELECT f1(EXISTS (SELECT 1 FROM g) AND TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '::'. | syntax error "
                + "line 1 at position 43 unexpected ')'.",
            "SELECT f1(NOT EXISTS (SELECT 1 FROM g)::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '::'. | syntax error "
                + "line 1 at position 47 unexpected ')'.",
            "SELECT 1 WHERE f1(EXISTS (SELECT 1 FROM g)::INT) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '::'. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT f1(EXISTS (SELECT 1 FROM g)::INT); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '::'.",
            "SELECT f1(1 IN (SELECT 1 FROM g)::INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected '::'.",
            "SELECT EXISTS (SELECT 1 FROM g)::INT x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '::'. | syntax error "
                + "line 1 at position 39 unexpected ')'.",
            "SELECT f1(EXISTS (SELECT 1 FROM g)::INT) x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected '='. | syntax error "
                + "line 1 at position 41 unexpected ')'.",
            "SELECT f1(EXISTS (SELECT 1 FROM g) = TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected '::'. | syntax error "
                + "line 1 at position 44 unexpected ')'.",
            "SELECT ABS(f1(EXISTS (SELECT 1 FROM g)::INT))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected '::'. | syntax error "
                + "line 1 at position 43 unexpected ')'.",
            "SELECT f1(EXISTS (SELECT 1 FROM g)::INT + 1)");
        assertAnswer(
            "F1 successfully dropped.",
            "DROP FUNCTION IF EXISTS f1(INT)");
    }
}
