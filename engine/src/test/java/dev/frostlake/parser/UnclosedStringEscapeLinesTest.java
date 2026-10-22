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
 * The lines of a string the text never closes: the lexer's line at the end of the input, then each backslash told
 * at the character after it by that character's code, or at the end when nothing follows, a backslash before a
 * blank told not at all; a text ending in a told escape refuses the end of the input too, unless the string
 * follows a comma (all live-verified).
 */
public class UnclosedStringEscapeLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void unclosedStringsWithEscapes() {
        engine.execute("CREATE OR REPLACE TABLE t1 (x INT)");
        engine.execute("CREATE OR REPLACE TABLE nf_p (x INT)");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 11 near '<EOF>'.", "SELECT 'abc");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 12 near '<EOF>'. | parse error "
                + "line 1 at position 10 near '98'.",
            "SELECT 'a\\bc");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 14 near '<EOF>'. | parse error "
                + "line 1 at position 10 near '98'. | parse error line 1 at position 13 near '100'. | syntax error "
                + "line 1 at position 14 unexpected '<EOF>'.",
            "SELECT 'a\\bc\\d");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | parse error "
                + "line 1 at position 12 near '110'. | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SELECT 'abc\\n");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | parse error "
                + "line 1 at position 10 near '92'.",
            "SELECT 'a\\\\bc");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 15 near '<EOF>'. | parse error "
                + "line 1 at position 12 near '39'.",
            "SELECT 'abc\\' x");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'. | parse error "
                + "line 1 at position 12 near '92'. | syntax error line 1 at position 13 unexpected '<EOF>'.",
            "SELECT 'abc\\\\");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 12 near '<EOF>'. | parse error "
                + "line 1 at position 12 near '<EOF>'. | syntax error line 1 at position 12 unexpected '<EOF>'.",
            "SELECT 'abc\\");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 15 near '<EOF>'. | parse error "
                + "line 1 at position 15 near '<EOF>'.",
            "SELECT 1, 'abc\\");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 13 near '<EOF>'.",
            "SELECT 'abc\\ ");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 31 near '<EOF>'. | parse error "
                + "line 1 at position 31 near '<EOF>'. | syntax error line 1 at position 31 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 'abc\\");
        assertAnswer("REFUSED SQL compilation error: | parse error line 1 at position 32 near '<EOF>'. | parse error "
                + "line 1 at position 31 near '39'. | syntax error line 1 at position 32 unexpected '<EOF>'.",
            "DESCRIBE TABLE t1 (a) x = 'abc\\'");
    }
}
