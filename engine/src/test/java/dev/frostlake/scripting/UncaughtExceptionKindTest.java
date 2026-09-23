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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * WHICH KIND an uncaught scripting exception names, and WHERE it points. Live splits the vocabulary by
 * what was RUNNING when the fault was raised:
 *
 * <pre>
 *   a fault evaluating an EXPRESSION     EXPRESSION_ERROR, at the expression's own offset
 *   a failing SQL STATEMENT              STATEMENT_ERROR, at the statement's offset
 *   an explicit RAISE                    the exception's own name, at the RAISE
 * </pre>
 *
 * <p>★ THE EXPRESSION POSITIONS ARE THE EXPRESSION'S, not the statement's: a LET initialiser anchors
 * past the {@code :=}, an assignment past its own {@code :=}, a RETURN past the keyword, and an IF
 * condition inside its parenthesis. A scalar subquery in a LET is still an expression fault.
 *
 * <p>★ THE INNER SENTENCE TRAVELS BARE — {@code TO_NUMBER('a')} carries
 * "Numeric value 'a' is not recognized" with no invented preamble around it.
 *
 * <p>★ A HANDLER STILL CATCHES EVERYTHING: WHEN OTHER sees both kinds, so the split changes only the
 * uncaught wrapper's wording.
 */
public class UncaughtExceptionKindTest extends BaseDatabaseTest {

    /** One script's refusal, or "ACCEPTED". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    @Test
    void anExpressionFaultIsExpressionErrorAtTheExpression() {
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 15 :"
                + " Division by zero",
            outcome("BEGIN\n  LET n INT := 1 / 0;\n  RETURN n;\nEND"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 15 :"
                + " Numeric value 'a' is not recognized",
            outcome("BEGIN\n  LET n INT := TO_NUMBER('a');\n  RETURN n;\nEND"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 16 :"
                + " Date 'zz' is not recognized",
            outcome("BEGIN\n  LET d DATE := 'zz'::DATE;\n  RETURN d;\nEND"));
        // A scalar SUBQUERY in a LET is still an expression fault, at the subquery's offset.
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 15 :"
                + " Division by zero",
            outcome("BEGIN\n  LET r INT := (SELECT 1/0);\n  RETURN r;\nEND"));
    }

    @Test
    void assignmentReturnAndIfAnchorOnTheirOwnExpressions() {
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 4 at position 7 :"
                + " Division by zero",
            outcome("DECLARE\n  n INT;\nBEGIN\n  n := 1 / 0;\n  RETURN n;\nEND"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 9 :"
                + " Division by zero",
            outcome("BEGIN\n  RETURN 1 / 0;\nEND"));
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 6 :"
                + " Division by zero",
            outcome("BEGIN\n  IF (1 / 0 > 0) THEN\n    RETURN 1;\n  END IF;\n  RETURN 2;\nEND"));
    }

    @Test
    void aFailingStatementStaysStatementError() {
        assertEquals(hinted("Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 2 :"
                + " SQL compilation error:|Table 'NOSUCHTABLE' does not exist or not authorized."),
            outcome("BEGIN\n  INSERT INTO nosuchtable VALUES (1);\n  RETURN 1;\nEND"));
    }

    @Test
    void aRaiseKeepsItsOwnName() {
        assertEquals("Uncaught exception of type 'MY_EX' on line 4 at position 2 : boom",
            outcome("DECLARE\n  my_ex EXCEPTION (-20001, 'boom');\nBEGIN\n  RAISE my_ex;\nEND"));
    }

    @Test
    void aHandlerStillCatchesBothKinds() {
        assertEquals("ACCEPTED",
            outcome("BEGIN\n  LET n INT := 1 / 0;\n  RETURN n;\nEXCEPTION\n  WHEN OTHER THEN\n"
                + "    RETURN SQLCODE || ':' || SQLERRM;\nEND"));
        assertEquals("ACCEPTED",
            outcome("BEGIN\n  INSERT INTO nosuchtable VALUES (1);\n  RETURN 1;\nEXCEPTION\n"
                + "  WHEN OTHER THEN\n    RETURN SQLCODE || ':' || SQLERRM;\nEND"));
    }
}
