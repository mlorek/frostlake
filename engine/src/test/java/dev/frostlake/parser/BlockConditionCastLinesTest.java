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
 * A cast's comma opening a Snowflake Scripting IF's or WHILE's condition, and a second FROM after a bracketed FROM
 * item nothing of which was read (all live-verified).
 */
public class BlockConditionCastLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void conditionCasts() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 4 at position 12 unexpected ','. | syntax "
                + "error line 4 at position 17 unexpected '='. | syntax error line 7 at position 2 unexpected "
                + "'RETURN'.",
            "DECLARE\n  x INT;\nBEGIN\n  IF (CAST(1, 2) = 1) THEN\n    RETURN 1;\n  END IF;\n  RETURN 0;\nEND");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax "
                + "error line 1 at position 32 unexpected 'FROM'. | syntax error line 1 at position 49 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x), (2) FROM FZ) WHERE a b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 49 unexpected "
                + "'RETURN'.",
            "BEGIN IF (CAST(1, 2) = 1) THEN RETURN 1; END IF; RETURN 0; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 20 unexpected ')'.",
            "BEGIN IF (CAST(1, 2)) THEN RETURN 1; END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ','. | syntax "
                + "error line 1 at position 24 unexpected '='. | syntax error line 1 at position 32 unexpected "
                + "'RETURN'.",
            "BEGIN WHILE (CAST(1, 2) = 1) DO RETURN 1; END WHILE; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ','.",
            "BEGIN LET b := CAST(1, 2) = 1; RETURN b; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected ','. | syntax "
                + "error line 1 at position 16 unexpected ')'.",
            "SELECT CAST(1, 2) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected ','.",
            "SELECT 1 WHERE CAST(1, 2) = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='.",
            "BEGIN IF (CAST(1, 2) = 1) THEN RETURN 1; ELSE RETURN 2; END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ','. | syntax "
                + "error line 1 at position 21 unexpected '='. | syntax error line 1 at position 49 unexpected 'END'.",
            "BEGIN IF (CAST(1, 2) = 1) THEN RETURN 1; END IF; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax "
                + "error line 1 at position 32 unexpected 'FROM'.",
            "SELECT a FROM (SELECT 1 x), (2) FROM FZ) WHERE a");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax "
                + "error line 1 at position 32 unexpected 'FROM'.",
            "SELECT a FROM (SELECT 1 x), (2) FROM FZ)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax "
                + "error line 1 at position 32 unexpected 'FROM'. | syntax error line 1 at position 49 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x), (2) FROM FZ) WHERE a b c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '2'. | syntax "
                + "error line 1 at position 32 unexpected 'FROM'. | syntax error line 1 at position 52 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x), (2) FROM FZ) ORDER BY a b");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '2'. | syntax "
                + "error line 1 at position 21 unexpected 'FROM'. | syntax error line 1 at position 38 unexpected 'b'.",
            "SELECT a FROM t, (2) FROM FZ) WHERE a b");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
