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
 * A scripting condition written without its parentheses, run as a block rather than created as a
 * routine. The account reads the two brackets as missing tokens: it names the condition's first token,
 * then the THEN / DO / END where the ')' belongs, and reads on as though both were written — so the
 * ELSE, ELSEIF and END IF that follow are no fault of their own, another bare condition earns its own
 * pair, and a genuine fault elsewhere in the block still adds its line.
 *
 * <pre>
 *   IF 'x' THEN … ELSE … END IF;          'x', THEN
 *   IF 'x' THEN … ELSEIF 'y' THEN …       'x', THEN, 'y', THEN
 *   IF 1 = 1) THEN …                      '1' alone — the text's own ')' closes it
 *   IF UPPER('x') THEN …                  'UPPER' alone — the bracket after it is read as the condition's
 * </pre>
 */
public class BareConditionRefusalLinesTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
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
    public void theConditionAndItsCloserAreTheTwoLines() {
        assertEquals(refused(line(1, 10, "'x'"), line(1, 14, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF 'x' THEN RETURN 1; ELSE RETURN 2; END IF; END $$"));
        assertEquals(refused(line(1, 10, "1"), line(1, 16, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF 1 = 1 THEN RETURN 1; ELSE RETURN 2; END IF; END $$"));
        assertEquals(refused(line(1, 36, "n"), line(1, 43, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ DECLARE n INT DEFAULT 11; BEGIN IF n > 10 THEN RETURN 1; "
                + "ELSE RETURN 2; END IF; END $$"));
        assertEquals(refused(line(1, 10, "'x'"), line(1, 14, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF 'x' THEN RETURN 1; END IF; END $$"));
        assertEquals(refused(line(1, 10, "NOT"), line(1, 19, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF NOT TRUE THEN RETURN 1; END IF; END $$"));
        assertEquals(refused(line(1, 10, "a"), line(1, 14, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF a.b THEN RETURN 1; END IF; END $$"));
    }

    @Test
    public void everyConditionKeywordHasItsOwnCloser() {
        assertEquals(refused(line(1, 39, "'x'"), line(1, 43, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN RETURN 1; ELSEIF 'x' THEN RETURN 3; "
                + "ELSE RETURN 2; END IF; END $$"));
        assertEquals(refused(line(1, 13, "1"), line(1, 19, "DO")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE 1 = 1 DO BREAK; END WHILE; END $$"));
        assertEquals(refused(line(1, 27, "1"), line(1, 33, "END")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN REPEAT BREAK; UNTIL 1 = 1 END REPEAT; END $$"));
        assertEquals(refused(line(1, 10, "CASE"), line(1, 39, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF CASE WHEN TRUE THEN TRUE END THEN RETURN 1; END IF; END $$"));
    }

    @Test
    public void eachBareConditionEarnsItsOwnPair() {
        assertEquals(refused(line(1, 10, "'x'"), line(1, 14, "THEN"), line(1, 36, "'y'"), line(1, 40, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF 'x' THEN RETURN 1; ELSEIF 'y' THEN RETURN 3; END IF; END $$"));
        assertEquals(refused(line(1, 10, "'x'"), line(1, 14, "THEN"), line(1, 40, "'y'"), line(1, 44, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF 'x' THEN RETURN 1; END IF; IF 'y' THEN RETURN 2; END IF; END $$"));
    }

    @Test
    public void aGenuineFaultElsewhereStillSpeaks() {
        assertEquals(refused(line(1, 10, "'x'"), line(1, 14, "THEN"), line(1, 46, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF 'x' THEN RETURN 1; END IF; RETURN 1 1; END $$"));
        assertEquals(refused(line(1, 10, "'x'"), line(1, 14, "THEN"), line(1, 43, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF 'x' THEN RETURN 1; ELSE RETURN 1 1; END IF; END $$"));
        assertEquals(refused(line(1, 13, "'x'"), line(1, 17, "DO"), line(1, 47, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN WHILE 'x' DO BREAK; END WHILE; RETURN 1 1; END $$"));
    }

    @Test
    public void theShapeHoldsAcrossLinesCaseAndNesting() {
        assertEquals(refused(line(3, 5, "'x'"), line(3, 9, "THEN")),
            refusal("EXECUTE IMMEDIATE $$\nBEGIN\n  IF 'x' THEN\n    RETURN 1;\n  END IF;\nEND;\n$$"));
        assertEquals(refused(line(1, 9, "'x'"), line(1, 13, "THEN")),
            refusal("BEGIN IF 'x' THEN RETURN 1; END IF; END"));
        assertEquals(refused(line(1, 10, "'x'"), line(1, 14, "then")),
            refusal("EXECUTE IMMEDIATE $$ begin if 'x' then return 1; end if; end $$"));
        assertEquals(refused(line(1, 15, "'x'"), line(1, 19, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN LOOP IF 'x' THEN BREAK; END IF; END LOOP; END $$"));
        assertEquals(refused(line(1, 25, "'x'"), line(1, 29, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (TRUE) THEN IF 'x' THEN RETURN 1; END IF; END IF; END $$"));
    }

    @Test
    public void aConditionClosedOrOpenedByTheTextItselfIsOneLine() {
        assertEquals(refused(line(1, 10, "1")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF 1 = 1) THEN RETURN 1; END IF; END $$"));
        assertEquals(refused(line(1, 17, "THEN")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF (1 = 1 THEN RETURN 1; END IF; END $$"));
        assertEquals(refused(line(1, 10, "UPPER")),
            refusal("EXECUTE IMMEDIATE $$ BEGIN IF UPPER('x') THEN RETURN 1; END IF; END $$"));
    }
}
