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
 * A dollar sign and an ampersand after a value, a dollar quote left open, a dot after a number, signs and tildes
 * in a row, an unknown function before a known one's argument count, the names a refusal quotes, and a word
 * after a string in brackets (all live-verified).
 */
public class LexerOperatorLeftoverLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void lexerAndOperators() {
        engine.execute("CREATE OR REPLACE TABLE t1 (a INT, b INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '2'.",
            "SELECT 1 ! 2 ! 3");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT 1 $ 2 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'. | syntax "
                + "error line 1 at position 13 unexpected 'y'.",
            "SELECT 1 $ x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'. | syntax "
                + "error line 1 at position 18 unexpected 'y'.",
            "SELECT 1 $ 2 AS x y");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | syntax error "
                + "line 1 at position 10 unexpected '$'.",
            "SELECT 1 $$ 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '&'. | syntax "
                + "error line 1 at position 11 unexpected '&'.",
            "SELECT 1 &&& 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'. | syntax "
                + "error line 1 at position 21 unexpected 'y'.",
            "SELECT 1 & FROM t1 x y");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 1 & ABS(1, 2)");
        assertAnswer("REFUSED SQL compilation error: | invalid function '~'", "SELECT ~~1");
        assertAnswer("REFUSED SQL compilation error: | invalid function '-'", "SELECT -~1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"A$\"'",
            "SELECT a$ FROM t1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier '\"`A`\".B'",
            "SELECT `a`.b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a IN ('x, ' y) AND b = 1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 24 near '<EOF>'. | syntax error "
                + "line 1 at position 13 unexpected 'y'.",
            "SELECT ('x, 'y') FROM t1");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 25 near '59'. | syntax error "
                + "line 1 at position 27 unexpected 'SELECT'. | syntax error line 1 at position 27 unexpected "
                + "'SELECT'.",
            "SELECT 1 FROM t1 LIMIT 1e; SELECT 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '.'.",
            "SELECT .5.");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'. | syntax "
                + "error line 1 at position 18 unexpected 'y'.",
            "SELECT 1 $ 2, 3 x y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'FROM'. | syntax "
                + "error line 1 at position 21 unexpected 'y'.",
            "SELECT 1 & FROM t1 x y z");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"~\".", "SELECT ~1");
        assertAnswer("REFUSED SQL compilation error: | invalid function '+'", "SELECT +~1");
        assertAnswer("REFUSED SQL compilation error: | invalid function '~'", "SELECT ~-1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 7 | invalid identifier 'A$B'",
            "SELECT a$b FROM t1");
        assertAnswer("REFUSED SQL compilation error: | Unknown function \"&\".", "SELECT 1 & UPPER('a', 'b')");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'y'.",
            "SELECT 1 FROM t1 WHERE a IN ('x' y) AND b = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '&'. | syntax "
                + "error line 1 at position 11 unexpected '&'. | syntax error line 1 at position 12 unexpected '&'.",
            "SELECT 1 &&&& 2");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected '$'.",
            "SELECT 1 $ 2");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
