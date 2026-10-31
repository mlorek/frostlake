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
 * A comma inside a cast in a longer IF or WHILE condition: a condition ending in TRUE or FALSE reports an IF's
 * first body statement's second token and a WHILE's condition's ')', and one ending otherwise, the cast first,
 * the token after the IF's END IF and its semicolon (all live-verified).
 */
public class CastConditionCommaTest extends MeasuredCellsTestSupport {

    @Test
    public void longerConditions() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'SELECT'.",
            "BEGIN WHILE (FALSE) LOOP LET a := 1 SELECT 1; END LOOP; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'.",
            "BEGIN IF (TRUE) THEN LET a := 1 SELECT 1; END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 47 unexpected '1'.",
            "BEGIN IF (CAST(1, 2) = 1 OR FALSE) THEN RETURN 1; END IF; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 47 unexpected ''a''.",
            "BEGIN IF (CAST(1, 2) = 1 AND TRUE) THEN RETURN 'a'; END IF; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 44 unexpected 'b'.",
            "BEGIN IF (CAST(1, 2) = 1 AND TRUE) THEN LET b := 5; END IF; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 47 unexpected '1'.",
            "BEGIN IF (CAST(1, 2) = 1 AND TRUE) THEN RETURN 1; ELSE RETURN 2; END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax "
                + "error line 1 at position 24 unexpected '='. | syntax error line 1 at position 36 unexpected ')'.",
            "BEGIN WHILE (CAST(1, 2) = 1 AND TRUE) DO RETURN 1; END WHILE; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 53 unexpected "
                + "'RETURN'.",
            "BEGIN IF (CAST(1, 2) = 1 + 1) THEN RETURN 1; END IF; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '>'. | syntax error line 1 at position 59 unexpected "
                + "'RETURN'.",
            "BEGIN IF (CAST(1, 2) > 1 AND 2 > 1) THEN RETURN 1; END IF; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected ','. | syntax "
                + "error line 1 at position 25 unexpected 'AND'. | syntax error line 1 at position 47 unexpected '1'.",
            "BEGIN IF (1 = CAST(1, 2) AND TRUE) THEN RETURN 1; END IF; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 47 unexpected '1'.",
            "BEGIN IF (CAST(1, 2) = 1 AND TRUE) THEN RETURN 1; END IF; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 47 unexpected 'x'.",
            "BEGIN IF (CAST(1, 2) = 1 AND TRUE) THEN RETURN x; END IF; RETURN 0; END");
    }
}
