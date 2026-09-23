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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * GETVARIABLE is an OPERATOR in the plan, so every refusal that re-prints it reads it so: the arity sentences
 * infix its name between the arguments and print nothing for none, an echo holding the one-argument call prints
 * that argument alone as an operand, and a name that is no text is echoed with the cast to text the plan gives
 * it. A generator refuses its argument before the GETVARIABLE inside it. Each expected answer is the account's own.
 */
public class GetVariablePlanEchoTest extends BaseDatabaseTest {

    private static final String REFUSED = "SQL compilation error:|";
    private static final String AT_CALL = "SQL compilation error: error line 1 at position 7|";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (n INT, g VARCHAR(5), d DATE)");
        engine.execute("SET SV = 'abc'");
    }

    /** The refusal as one line, each line break as |, or ACCEPTED. */
    private String answer(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    public void theAritySentencesInfixTheNameBetweenTheArguments() {
        assertEquals(AT_CALL + "not enough arguments for function [], expected 1, got 0",
            answer("SELECT GETVARIABLE()"));
        assertEquals(AT_CALL + "too many arguments for function ['SV' GETVARIABLE 1] expected 1, got 2",
            answer("SELECT GETVARIABLE('SV', 1)"));
        assertEquals(AT_CALL + "too many arguments for function ['SV' GETVARIABLE 1 GETVARIABLE 2] expected 1, got 3",
            answer("SELECT GETVARIABLE('SV', 1, 2)"));
        assertEquals(AT_CALL + "too many arguments for function [1 GETVARIABLE 2] expected 1, got 2",
            answer("SELECT GETVARIABLE(1, 2)"));
        assertEquals(AT_CALL + "too many arguments for function [(UPPER('a')) GETVARIABLE 1] expected 1, got 2",
            answer("SELECT GETVARIABLE(UPPER('a'), 1)"));
    }

    @Test
    public void theOneArgumentCallIsItsArgumentAsAnOperand() {
        assertEquals(REFUSED + "argument 1 to function RANDOM needs to be constant, found ''SV''",
            answer("SELECT RANDOM(GETVARIABLE('SV'))"));
        assertEquals(REFUSED + "argument 1 to function RANDOM needs to be constant, found '(UPPER('sv'))'",
            answer("SELECT RANDOM(GETVARIABLE(UPPER('sv')))"));
        assertEquals(AT_CALL + "too many arguments for function [ABS('SV', 1)] expected 1, got 2",
            answer("SELECT ABS(GETVARIABLE('SV'), 1)"));
        assertEquals(AT_CALL + "too many arguments for function [ABS((UPPER('sv')), 1)] expected 1, got 2",
            answer("SELECT ABS(GETVARIABLE(UPPER('sv')), 1)"));
    }

    @Test
    public void aNameThatIsNoTextIsEchoedWithItsCastToText() {
        assertEquals(REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'CAST(1 AS VARCHAR(134217728))'",
            answer("SELECT GETVARIABLE(1)"));
        assertEquals(REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'CAST(1.5 AS VARCHAR(134217728))'",
            answer("SELECT GETVARIABLE(1.5)"));
        assertEquals(REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'CAST(TRUE AS VARCHAR(134217728))'",
            answer("SELECT GETVARIABLE(TRUE)"));
        assertEquals(REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'CAST(RT.N AS VARCHAR(134217728))'",
            answer("SELECT GETVARIABLE(n) FROM rt"));
        assertEquals(REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'CAST(RT.D AS VARCHAR(134217728))'",
            answer("SELECT GETVARIABLE(d) FROM rt"));
        assertEquals(REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'RT.G'",
            answer("SELECT GETVARIABLE(g) FROM rt"));
    }

    @Test
    public void aGeneratorRefusesItsArgumentBeforeTheVariableInsideIt() {
        assertEquals(REFUSED + "argument 1 to function RANDOM needs to be constant, found '(CAST(1 AS VARCHAR(134217728)))'",
            answer("SELECT RANDOM(GETVARIABLE(1))"));
        assertEquals(REFUSED + "argument 1 to function UNIFORM needs to be constant, found '(CAST(1 AS VARCHAR(134217728)))'",
            answer("SELECT UNIFORM(GETVARIABLE(1), 2, 3)"));
        assertEquals(REFUSED + "argument 1 to function RANDOM needs to be constant, found 'ABS(CAST((CAST(1 AS VARCHAR(134217728))) AS FLOAT))'",
            answer("SELECT RANDOM(ABS(GETVARIABLE(1)))"));
        assertEquals(AT_CALL + "too many arguments for function [RANDOM((CAST(1 AS VARCHAR(134217728))), 2)] expected 1, got 2",
            answer("SELECT RANDOM(GETVARIABLE(1), 2)"));
        assertEquals(REFUSED + "argument 0 to function GETVARIABLE needs to be constant, found 'CAST(1 AS VARCHAR(134217728))'",
            answer("SELECT UNIFORM(1, 2, GETVARIABLE(1))"), "the generated value's own slot is no constant slot");
    }
}
