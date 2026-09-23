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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The lines live reports around a loop's END (live-verified): a loop run without its semicolon into a CASE, a BEGIN
 * block or an exception handler, the same loop inside nested BEGIN blocks, and a BREAK or CONTINUE run into an END.
 */
public class LoopEndRecoveryTest extends BaseDatabaseTest {

    private static final String LOOP = "BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1;";

    private String refusal(final String block) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$");
            }
        }).getMessage();
    }

    private static String refused(final Object... positionsAndTokens) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (int i = 0; i < positionsAndTokens.length; i += 2) {
            message.append("\nsyntax error line 1 at position ").append(positionsAndTokens[i])
                .append(" unexpected '").append(positionsAndTokens[i + 1]).append("'.");
        }
        return message.toString();
    }

    @Test
    public void aCaseAfterTheLoopIsItsLabelSoTheTokenAfterItIsRefused() {
        assertEquals(refused(63, "WHEN", 68, "TRUE"),
            refusal(LOOP + " END WHILE CASE WHEN TRUE THEN RETURN 1; END CASE; END;"));
        assertEquals(refused(63, "x", 65, "WHEN"),
            refusal(LOOP + " END WHILE CASE x WHEN 1 THEN RETURN 1; END CASE; END;"));
        assertEquals(refused(63, "WHEN", 68, "x"),
            refusal(LOOP + " END WHILE CASE WHEN x > 1 THEN RETURN 1; END CASE; END;"));
        assertEquals(refused(63, "(", 72, "1"),
            refusal(LOOP + " END WHILE CASE (1) WHEN 1 THEN RETURN 1; END CASE; END;"));
    }

    @Test
    public void aBeginIsNoLabelSoTheLoopIsRefusedAtIt() {
        assertEquals(refused(58, "BEGIN", 71, "1"), refusal(LOOP + " END WHILE BEGIN RETURN 1; END; END;"));
        assertEquals(refused(58, "BEGIN", 68, "x"), refusal(LOOP + " END WHILE BEGIN LET x := 1; END; END;"));
    }

    @Test
    public void anExceptionHandlerAfterTheLoopIsItsLabel() {
        assertEquals(refused(68, "WHEN", 73, "OTHER"),
            refusal(LOOP + " END WHILE EXCEPTION WHEN OTHER THEN RETURN 1; END;"));
        assertEquals(refused(68, "WHEN", 73, "STATEMENT_ERROR"),
            refusal(LOOP + " END WHILE EXCEPTION WHEN STATEMENT_ERROR THEN RETURN 1; END;"));
    }

    @Test
    public void inNestedBlocksTheSecondLineIsPastTheirEnds() {
        assertEquals(refused(71, "i", 79, "END"),
            refusal("BEGIN LET i := 0; BEGIN WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; END; END;"));
        assertEquals(refused(71, "i", 79, "RETURN"),
            refusal("BEGIN LET i := 0; BEGIN WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; END; RETURN 2; END;"));
        assertEquals(refused(71, "i", 79, "LET"), refusal(
            "BEGIN LET i := 0; BEGIN WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; END; LET j := 1; RETURN j; END;"));
        assertEquals(refused(71, "i", 78, "END"),
            refusal("BEGIN LET i := 0; BEGIN WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; END END;"));
        assertEquals(refused(77, "i", 90, "END"), refusal(
            "BEGIN BEGIN BEGIN LET i := 0; WHILE (i < 3) DO i := i + 1; END WHILE RETURN i; END; END; END;"));
    }

    @Test
    public void aBreakOrContinueRunIntoAnEndTakesItAsTheLoops() {
        assertEquals(refused(18, "END", 21, ";", 33, "RETURN"),
            refusal("BEGIN LOOP BREAK END; END LOOP; RETURN 1; END;"));
        assertEquals(refused(18, "END", 21, ";", 33, "END"), refusal("BEGIN LOOP BREAK END; END LOOP; END;"));
        assertEquals(refused(18, "END", 21, ";"), refusal("BEGIN LOOP BREAK END; RETURN 1; END;"));
        assertEquals(refused(18, "END", 21, ";", 33, "RETURN"),
            refusal("BEGIN LOOP BREAK END; END LOOP; RETURN 1; RETURN 2; END;"));
        assertEquals(refused(18, "END", 22, "x", 35, "RETURN"),
            refusal("BEGIN LOOP BREAK END x; END LOOP; RETURN 1; END;"));
        assertEquals(refused(21, "END", 24, ";", 36, "RETURN"),
            refusal("BEGIN LOOP CONTINUE END; END LOOP; RETURN 1; END;"));
        assertEquals(refused(29, "END", 32, ";", 45, "RETURN"),
            refusal("BEGIN WHILE (TRUE) DO BREAK END; END WHILE; RETURN 1; END;"));
        assertEquals(refused(30, "END", 33, ";", 45, "RETURN"),
            refusal("BEGIN LOOP LET a := 1; BREAK END; END LOOP; RETURN 1; END;"));
        assertEquals(refused(18, "END"), refusal("BEGIN LOOP BREAK END LOOP; RETURN 1; END;"));
    }
}
