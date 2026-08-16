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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A declared TYPE inside a scripting block, judged while the block COMPILES rather than while it runs.
 *
 * <p>★ THE WRAPPER WAS THE TELL. The sentence and its position were already right; Frostlake reached
 * them while EXECUTING the DECLARE, so the scripting layer saw an exception escaping a statement and
 * dressed it as one — "Uncaught exception of type 'STATEMENT_ERROR' … : &lt;the right sentence&gt;". Live
 * never gets there, because the block does not compile and so no statement has run.
 *
 * <p>★ THE WHOLE BLOCK IS CHECKED, REACHABLE OR NOT. A bad width inside {@code IF (FALSE)} is still
 * refused, at its own line and offset — which is what makes this a walk over the parse tree rather than
 * a check on the statement about to execute.
 *
 * <p>★ AN EXCEPTION HANDLER CANNOT CATCH IT. Nothing has run when it is raised, so there is no
 * statement to have failed and nothing to hand a handler.
 *
 * <p>★ IT IS THE TYPES ONLY, and the contrast is measured: an unknown COLUMN in an unreachable branch
 * is ACCEPTED on both engines. The name half and the type half of the compile pass are not the same
 * rule, and treating them as one would refuse a block live accepts.
 *
 * <p>★ WHERE THE PASS RUNS MATTERS. It sits beside the name validator, BEFORE the block's scope is
 * entered: a refusal thrown after {@code enterBlock()} would skip the finally that unwinds it and leave
 * the executor believing it were still inside a block, which breaks every later block in the session.
 */
public class BlockCompilesBeforeRunningTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE bc (i INT)");
        engine.execute("INSERT INTO bc VALUES (1)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            String value = "ACCEPTED";
            while (rs.next()) {
                value = "ACCEPTED: " + String.valueOf(rs.getValue(0));
            }
            return value;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String badWidthAt(final int line, final int position) {
        return "SQL compilation error: error line " + line + " at position " + position
            + "|Invalid character length: 0. Must be between 1 and 134,217,728.";
    }

    /** ★ The headline: a compile refusal, with NO uncaught-exception wrapper around it. */
    @Test
    public void abadWidthIsACompileRefusalNotAStatementFailure() {
        assertEquals(badWidthAt(2, 16),
            answer("BEGIN\n  LET v VARCHAR(0) := 'x';\n  RETURN v;\nEND"));
    }

    /** ★ A branch that never runs is checked too, at its own line and offset. */
    @Test
    public void anunreachableBranchIsCheckedAsWell() {
        assertEquals(badWidthAt(3, 18),
            answer("BEGIN\n  IF (FALSE) THEN\n    LET v VARCHAR(0) := 'x';\n  END IF;\n"
                + "  RETURN 1;\nEND"));
    }

    /** ★ A handler cannot catch it — nothing has run to fail. */
    @Test
    public void anexceptionHandlerCannotCatchIt() {
        assertEquals(badWidthAt(3, 18),
            answer("BEGIN\n  BEGIN\n    LET v VARCHAR(0) := 'x';\n  EXCEPTION WHEN OTHER THEN\n"
                + "    RETURN 'caught';\n  END;\n  RETURN 'fell through';\nEND"));
    }

    /** A type written inside a CURSOR's query is judged in the same pass. */
    @Test
    public void acursorsQueryIsCheckedToo() {
        assertEquals(badWidthAt(2, 44),
            answer("BEGIN\n  LET c CURSOR FOR SELECT CAST(1 AS VARCHAR(0)) FROM bc;\n  RETURN 1;\nEND"));
    }

    /** ★ THE CONTRAST: the NAME half does not reach into a subquery, on either engine. */
    @Test
    public void anunknownColumnInAnUnreachableBranchIsAccepted() {
        assertEquals("ACCEPTED: 1",
            answer("BEGIN\n  IF (FALSE) THEN\n    LET n INT := (SELECT nosuchcol FROM bc);\n"
                + "  END IF;\n  RETURN 1;\nEND"));
    }

    /** A valid block still runs and returns — the pass must not disturb execution. */
    @Test
    public void avalidBlockIsUntouched() {
        assertEquals("ACCEPTED: x", answer("BEGIN\n  LET v VARCHAR(5) := 'x';\n  RETURN v;\nEND"));
    }
}
