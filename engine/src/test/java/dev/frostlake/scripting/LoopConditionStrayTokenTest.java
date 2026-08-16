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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A WHILE or UNTIL condition closed by its own ')' and followed by a stray token. The stray token is the first
 * line; the account's recovery then takes the first word AFTER it that can be read as a name and refuses the
 * token after that one. DO is such a word, so a stray token right before DO puts the second line on the
 * statement after it.
 *
 * <pre>
 *   WHILE (TRUE) OR x DO BREAK;           'OR' then 'DO'        (x is the name)
 *   WHILE (TRUE) x DO BREAK;              'x' then 'BREAK'      (DO is the name)
 *   WHILE (TRUE) AND (FALSE) DO …         'AND' then ')'        (FALSE is the name)
 *   UNTIL (TRUE) OR x = 1 END REPEAT      'OR' then '='
 * </pre>
 */
public class LoopConditionStrayTokenTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    /** An anonymous block of one line, run through EXECUTE IMMEDIATE. */
    private static String body(final String block) {
        return "EXECUTE IMMEDIATE $$ " + block + " $$";
    }

    private static String line(final int line, final int position, final String token) {
        return "\nsyntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void whileTakesTheFirstNameAfterTheStrayToken() {
        assertEquals(refused(line(1, 20, "AND"), line(1, 30, ")")),
            refusal(body("BEGIN WHILE (TRUE) AND (FALSE) DO BREAK; END WHILE; END")));
        assertEquals(refused(line(1, 20, "OR"), line(1, 25, "DO")),
            refusal(body("BEGIN WHILE (TRUE) OR x DO BREAK; END WHILE; END")));
        assertEquals(refused(line(1, 20, "="), line(1, 27, "DO")),
            refusal(body("BEGIN WHILE (TRUE) = TRUE DO BREAK; END WHILE; END")));
        assertEquals(refused(line(1, 21, "AND"), line(1, 27, "DO")),
            refusal(body("BEGIN WHILE (1 = 1) AND a DO BREAK; END WHILE; END")));
    }

    @Test
    public void doCanBeTheName() {
        assertEquals(refused(line(1, 20, "AND"), line(1, 29, "BREAK")),
            refusal(body("BEGIN WHILE (TRUE) AND 1 DO BREAK; END WHILE; END")));
        assertEquals(refused(line(1, 20, "+"), line(1, 27, "BREAK")),
            refusal(body("BEGIN WHILE (TRUE) + 1 DO BREAK; END WHILE; END")));
        assertEquals(refused(line(1, 20, "x"), line(1, 25, "BREAK")),
            refusal(body("BEGIN WHILE (TRUE) x DO BREAK; END WHILE; END")));
        assertEquals(refused(line(1, 20, "x"), line(1, 29, "BREAK")),
            refusal(body("BEGIN WHILE (TRUE) x + 1 DO BREAK; END WHILE; END")));
        assertEquals(refused(line(1, 20, "TRUE"), line(1, 28, "BREAK")),
            refusal(body("BEGIN WHILE (TRUE) TRUE DO BREAK; END WHILE; END")));
    }

    @Test
    public void theSearchStartsPastTheStrayToken() {
        assertEquals(refused(line(1, 20, "x"), line(1, 24, "DO")),
            refusal(body("BEGIN WHILE (TRUE) x y DO BREAK; END WHILE; END")));
        assertEquals(refused(line(1, 49, "b"), line(1, 53, "DO")),
            refusal(body("DECLARE a BOOLEAN DEFAULT TRUE; BEGIN WHILE (a) b c DO BREAK; END WHILE; END")));
    }

    @Test
    public void untilBehavesAsWhile() {
        assertEquals(refused(line(1, 34, "AND"), line(1, 44, ")")),
            refusal(body("BEGIN REPEAT BREAK; UNTIL (TRUE) AND (FALSE) END REPEAT; END")));
        assertEquals(refused(line(1, 34, "OR"), line(1, 39, "=")),
            refusal(body("BEGIN REPEAT BREAK; UNTIL (TRUE) OR x = 1 END REPEAT; END")));
        assertEquals(refused(line(1, 34, "x"), line(1, 38, "=")),
            refusal(body("BEGIN REPEAT BREAK; UNTIL (TRUE) x y = 1 END REPEAT; END")));
    }

    @Test
    public void theShapeHoldsAcrossLines() {
        assertEquals(refused(line(3, 15, "AND"), line(3, 25, ")")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  WHILE (TRUE) AND (FALSE) DO\n    BREAK;\n  END WHILE;\nEND;\n$$"));
    }
}
