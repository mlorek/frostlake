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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A block's LET with a word where its type belongs, however short the word, is refused for the type; a LET that
 * declares a type and no value, run into the next statement, is refused at that statement's word and at the token
 * after it (all live-verified).
 */
public class LetTypeWordLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void letTypeWords() {
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.", "BEGIN LET q r; RETURN q; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'RR'.", "BEGIN LET q rr; RETURN q; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.",
            "BEGIN LET q r := 1; RETURN q; END");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.",
            "CREATE OR REPLACE TABLE t2104 (n r)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'RR'.",
            "CREATE OR REPLACE TABLE t2104 (n rr)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.", "SELECT CAST(1 AS r)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'R'.", "SELECT 1::r");
        assertAnswer("REFUSED SQL compilation error: | Unsupported data type 'RR'.", "SELECT 1::rr");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 23 unexpected 'q'.",
            "BEGIN LET q INT RETURN q; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 27 unexpected 'q'.",
            "BEGIN LET q VARCHAR RETURN q; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 23 unexpected '1'.",
            "BEGIN LET q INT RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'LET'. | syntax "
                + "error line 1 at position 20 unexpected 'r'.",
            "BEGIN LET q INT LET r INT; RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'.",
            "BEGIN LET q INT SELECT 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 28 unexpected 'q'.",
            "BEGIN LET q INT := 1 RETURN q; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 24 unexpected 'q'.",
            "BEGIN LET q := 1 RETURN q; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 33 unexpected 'q'.",
            "BEGIN LET q NUMBER(10, 2) RETURN q; END");
    }

    @Test
    public void typedLetRunIn() {
        engine.execute("CREATE OR REPLACE TABLE ct (a INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 23 unexpected 'q'.",
            "BEGIN LET q INT RETURN q + 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'LET'. | syntax "
                + "error line 1 at position 20 unexpected 'r'.",
            "BEGIN LET q INT LET r := 1; RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 24 unexpected 'END'.",
            "BEGIN LET q INT RETURN; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'LET'. | syntax "
                + "error line 1 at position 28 unexpected 'r'.",
            "BEGIN LET q VARCHAR(10) LET r INT; RETURN 1; END");
    }
}
