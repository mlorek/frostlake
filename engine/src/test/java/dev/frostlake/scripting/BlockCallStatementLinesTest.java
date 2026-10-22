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
 * The syntax-error lines of a block's statement that opens with a call and is no whole call statement, and of one
 * opened by a word that opens no call: a quoted name, LEFT, RIGHT, IDENTIFIER, CAST, TRY_CAST and a path (all
 * live-verified).
 */
public class BlockCallStatementLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void blockCallLines() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected '::'. | syntax "
                + "error line 1 at position 19 unexpected 'RETURN'.",
            "BEGIN ABS(1)::INT; RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '+'.",
            "BEGIN ABS(1) + 1; RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 28 unexpected '1'.",
            "BEGIN SYSTEM$WAIT(0) RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'RETURN'. | "
                + "syntax error line 1 at position 28 unexpected '1'.",
            "BEGIN ABS(1); ABS(2) RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'END'.",
            "BEGIN ABS(1); RETURN 1 END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected '\"ABS\"'.",
            "BEGIN \"ABS\"(1); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'LEFT'.",
            "BEGIN LEFT('a', 1); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'TRY_CAST'. | "
                + "syntax error line 1 at position 25 unexpected ')'.",
            "BEGIN TRY_CAST('1' AS INT); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'IDENTIFIER'.",
            "BEGIN IDENTIFIER('UPPER')('a'); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '.'. | syntax "
                + "error line 1 at position 25 unexpected '.'.",
            "BEGIN TEST_DB.TEST_SCHEMA.F(1); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected '*'.",
            "BEGIN ABS(1) * 2; RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'ABS'. | syntax "
                + "error line 1 at position 16 unexpected '('.",
            "BEGIN ABS(1) ABS(2); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected '||'.",
            "BEGIN UPPER('a') || 'b'; RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'RIGHT'.",
            "BEGIN RIGHT('a', 1); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 6 unexpected 'CAST'. | syntax "
                + "error line 1 at position 21 unexpected ')'.",
            "BEGIN CAST('1' AS INT); RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 7 unexpected '.'. | syntax "
                + "error line 1 at position 9 unexpected '('.",
            "BEGIN S.F(1); RETURN 1; END;");
    }
}
