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
 * A stray word after a literal in a CAST's or a TRY_CAST's bracketed operand reports the word, the token after it
 * read as the cast's type, and then the token the cast cannot close at or the one past the AS and its word read as
 * an alias; a call's last argument, a single statement's USING and a dotted session variable, set or not, answer
 * as the account does (all live-verified).
 */
public class BracketedLiteralPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void castOperandsAndOtherPlaces() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'y'. | syntax error line 1 at position 19 unexpected 'z'.",
            "SELECT CAST((1 x y z) AS INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'y'. | syntax error line 1 at position 30 unexpected ')'.",
            "SELECT CAST((1 x y) AS VARCHAR)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'y'. | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT CAST((1 x y) AS INT), 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected '2'. | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT CAST((1 x 2) AS INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'y'. | syntax error line 1 at position 19 unexpected '+'.",
            "SELECT CAST((1 x y + 1) AS INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax "
                + "error line 1 at position 21 unexpected 'y'. | syntax error line 1 at position 30 unexpected ')'.",
            "SELECT 1 + CAST((1 x y) AS INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y)::INT");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "SELECT ABS((1 x y))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'USING'.",
            "SELECT * FROM T JOIN T u USING x");
        engine.execute("SET x = 1");
        assertAnswer("REFUSED SQL compilation error: error line 0 at position -1 | Session variable '$X.Y' does not "
                + "exist",
            "SELECT $x.y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax "
                + "error line 1 at position 21 unexpected 'y'. | syntax error line 1 at position 23 unexpected 'z'.",
            "SELECT TRY_CAST((1 x y z) AS INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'y'. | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT CAST((1 x y) AS INT) AS c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax "
                + "error line 1 at position 20 unexpected ')'.",
            "SELECT CAST(((1 x y)) AS INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'y'. | syntax error line 1 at position 29 unexpected '('.",
            "SELECT CAST((1 x y) AS NUMBER(5, 2))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'.",
            "SELECT CAST((1 x) AS INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'y'. | syntax error line 1 at position 26 unexpected ')'.",
            "SELECT CAST((1 x y) AS INT) FROM T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax "
                + "error line 1 at position 23 unexpected 'y'. | syntax error line 1 at position 32 unexpected ')'.",
            "SELECT UPPER(CAST((1 x y) AS INT))");
    }
}
