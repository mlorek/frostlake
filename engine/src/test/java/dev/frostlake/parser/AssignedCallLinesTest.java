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
 * The syntax-error lines of a CALL written in parentheses as a declared variable's value: the procedure's name is
 * refused, then the token after the arguments' first name or bind, and nothing more where they hold none (all
 * live-verified).
 */
public class AssignedCallLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void callAsValue() {
        engine.execute("CREATE OR REPLACE PROCEDURE pn(d DATE) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'x'; "
            + "END; $$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'. | syntax "
                + "error line 1 at position 50 unexpected ')'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn(:d)); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn(1)); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'. | syntax "
                + "error line 1 at position 50 unexpected ','.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn(:d, 1)); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'. | syntax "
                + "error line 1 at position 53 unexpected ')'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn(1, :d)); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'. | syntax "
                + "error line 1 at position 49 unexpected ')'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn(d)); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn()); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn('a')); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected 'pn'. | syntax "
                + "error line 1 at position 42 unexpected ')'.",
            "DECLARE d DATE; BEGIN LET x := (CALL pn(:d)); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'. | syntax "
                + "error line 1 at position 50 unexpected ')'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn(:d) ); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'. | syntax "
                + "error line 1 at position 51 unexpected '+'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn(:d + 1)); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'pn'. | syntax "
                + "error line 1 at position 55 unexpected '('.",
            "DECLARE d DATE; BEGIN LET x VARCHAR := (CALL pn(TO_DATE(:d))); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'pn'. | syntax "
                + "error line 1 at position 46 unexpected ')'.",
            "DECLARE d DATE; BEGIN LET x INT := (CALL pn(:d)); RETURN x; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 50 unexpected 'pn'. | syntax "
                + "error line 1 at position 55 unexpected ')'.",
            "DECLARE d DATE; BEGIN LET x VARCHAR DEFAULT (CALL pn(:d)); RETURN 1; END;");
    }
}
