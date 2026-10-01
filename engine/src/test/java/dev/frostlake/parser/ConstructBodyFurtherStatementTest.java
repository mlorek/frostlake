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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * An alias followed by '(' in a control construct's body, followed by further statements of that body: live has given
 * the construct up, refuses its ELSE, ELSEIF, WHEN or UNTIL, and reads its END as closing the frame around it
 * (live-verified).
 */
public class ConstructBodyFurtherStatementTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void theConstructsEndClosesTheFrameAroundIt() {
        assertEquals(lines(at(1, 34, "("), at(1, 57, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 57, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END"));
        assertEquals(lines(at(1, 35, "("), at(1, 61, "END")),
            refusal("BEGIN WHILE (TRUE) DO SELECT 1 foo (2); RETURN 1; END WHILE; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 67, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; RETURN 2; END IF; END"));
        assertEquals(lines(at(1, 24, "("), at(1, 46, "END")),
            refusal("BEGIN LOOP SELECT 1 foo (2); BREAK; END LOOP; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 57, "RETURN")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; RETURN 3; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 57, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END;"));
        assertEquals(lines(at(1, 34, "("), at(1, 59, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); LET x := 1; END IF; END"));
        assertEquals(lines(at(1, 38, "("), at(1, 57, "FOR")),
            refusal("BEGIN FOR i IN 1 TO 2 DO SELECT 1 foo (2); RETURN 1; END FOR; END"));
        assertEquals(lines(at(1, 40, "("), at(1, 68, "END")),
            refusal("BEGIN BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END; END"));
        assertEquals(lines(at(1, 39, "("), at(1, 64, "END")),
            refusal("BEGIN CASE WHEN TRUE THEN SELECT 1 foo (2); RETURN 1; END CASE; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 57, "RETURN")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; RETURN 3; END;"));
        assertEquals(lines(at(1, 39, "("), at(1, 58, "IF"), at(1, 72, "END")),
            refusal("BEGIN LOOP IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END LOOP; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 57, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END IF; END"));
        assertEquals(lines(at(1, 34, "("), at(4, 0, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2);\nRETURN 1;\nEND IF;\nEND"));
        assertEquals(lines(at(1, 35, "("), at(1, 61, "RETURN")),
            refusal("BEGIN WHILE (TRUE) DO SELECT 1 foo (2); RETURN 1; END WHILE; RETURN 2; END"));
        assertEquals(lines(at(1, 49, "("), at(1, 72, "END")),
            refusal("DECLARE x INT; BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; END IF; END"));
        assertEquals(lines(at(1, 38, "("), at(1, 57, "FOR")),
            refusal("BEGIN FOR i IN 1 TO 2 DO SELECT 1 foo (2); RETURN 1; END FOR; RETURN 3; END"));
    }

    @Test
    public void aBranchKeywordIsRefused() {
        assertEquals(lines(at(1, 34, "("), at(1, 49, "ELSE"), at(1, 61, "2")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; ELSE RETURN 2; END IF; END"));
        assertEquals(lines(at(1, 26, "("), at(1, 41, "UNTIL"), at(1, 47, "(")),
            refusal("BEGIN REPEAT SELECT 1 foo (2); RETURN 1; UNTIL (TRUE) END REPEAT; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 49, "ELSEIF"), at(1, 56, "(")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; ELSEIF (FALSE) THEN RETURN 2; END IF; END"));
        assertEquals(lines(at(1, 39, "("), at(1, 54, "WHEN"), at(1, 59, "FALSE")),
            refusal("BEGIN CASE WHEN TRUE THEN SELECT 1 foo (2); RETURN 1; WHEN FALSE THEN RETURN 2; END CASE; END"));
        assertEquals(lines(at(1, 34, "("), at(1, 49, "ELSE"), at(1, 61, "2")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; ELSE RETURN 2 + 3; END IF; END"));
    }

    @Test
    public void aNameAfterTheBracketOrAnExceptionEndsTheReport() {
        assertEquals(lines(at(1, 34, "(")), refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2) x; RETURN 1; END IF; END"));
        assertEquals(lines(at(1, 34, "(")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 foo (2); RETURN 1; EXCEPTION WHEN OTHER THEN RETURN 3; END"));
    }
}
