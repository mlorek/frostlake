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

package dev.frostlake.procedural;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WHILE opens its body with LOOP as well as with DO, and a LOOP body closes with END LOOP — its label
 * after it, BREAK and CONTINUE within, nested in another — while END WHILE closes only a DO body and END
 * LOOP only a LOOP one. A condition without its parentheses is refused at the condition and at the LOOP,
 * as at the DO. Every cell is live-verified.
 */
public class WhileLoopFormTest extends BaseDatabaseTest {

    /** The block's first cell, or the refusal on one line. */
    private String run(final String body) {
        try {
            for (final Row row : engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$").getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String syntax(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aWhileLoopRunsItsBody() {
        final String[][] cells = {
            {"BEGIN WHILE (FALSE) LOOP BREAK; END LOOP; RETURN 1; END;", "1"},
            {"BEGIN LET i := 0; WHILE (i < 3) LOOP i := i + 1; END LOOP; RETURN i; END;", "3"},
            {"BEGIN LET i := 0; WHILE (i < 3) LOOP i := i + 1; END LOOP lbl; RETURN i; END;", "3"},
            {"BEGIN LET i := 0; WHILE (TRUE) LOOP i := i + 1; IF (i > 2) THEN BREAK; END IF; END LOOP; RETURN i; END;", "3"},
            {"BEGIN LET i := 0; LET s := 0; WHILE (i < 5) LOOP i := i + 1; IF (i = 3) THEN CONTINUE; END IF; s := s + i;"
                + " END LOOP; RETURN s; END;", "12"},
            {"BEGIN LET i := 0; WHILE (i < 3) LOOP i := i + 1; BREAK lbl; END LOOP lbl; RETURN i; END;", "1"},
            {"BEGIN LET i := 0; WHILE (i < 2) LOOP LET j := 0; WHILE (j < 2) LOOP j := j + 1; END LOOP; i := i + 1;"
                + " END LOOP; RETURN i; END;", "2"},
            {"BEGIN WHILE (1) LOOP BREAK; END LOOP; RETURN 1; END;", "1"},
            {"BEGIN LET i := 0; WHILE (i < 3) LOOP i := i + 1; END LOOP; END;", "null"},
            {"BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE; RETURN i; END;", "3"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], run(cell[0]), cell[0]);
        }
    }

    @Test
    public void eachOpeningKeepsItsOwnEnd() {
        final String error = "SQL compilation error:|";
        assertEquals(error + syntax(54, "WHILE"), run("BEGIN LET i := 0; WHILE (i < 3) LOOP i := i + 1; END WHILE; RETURN i; END;"));
        assertEquals(error + syntax(52, "LOOP"), run("BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END LOOP; RETURN i; END;"));
        assertEquals(error + syntax(25, "i") + "|" + syntax(31, "LOOP"),
            run("BEGIN LET i := 0; WHILE i < 3 LOOP i := i + 1; END LOOP; RETURN i; END;"));
        assertEquals(error + syntax(25, "TRUE") + "|" + syntax(30, "LOOP"),
            run("BEGIN LET i := 0; WHILE TRUE LOOP i := i + 1; END LOOP; RETURN i; END;"));
        assertEquals(error + syntax(13, "'x'") + "|" + syntax(17, "LOOP"), run("BEGIN WHILE 'x' LOOP BREAK; END LOOP; RETURN 1; END;"));
        assertEquals(error + syntax(25, "i") + "|" + syntax(31, "DO"),
            run("BEGIN LET i := 0; WHILE i < 3 DO i := i + 1; END WHILE; RETURN i; END;"));
        assertEquals("SQL compilation error: error line 1 at position 26|invalid identifier 'NOSUCH'",
            run("BEGIN LET i := 0; WHILE (nosuch) LOOP i := i + 1; END LOOP; RETURN i; END;"));
    }
}
