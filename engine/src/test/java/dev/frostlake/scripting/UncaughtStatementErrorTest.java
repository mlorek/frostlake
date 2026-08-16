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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An error that escapes a scripting block uncaught reads as one sentence, live-verified:
 * {@code Uncaught exception of type 'STATEMENT_ERROR' on line L at position P : <error>}, where L and
 * P name the STATEMENT that failed — however deeply it sat, in a nested block, an IF branch or a loop
 * body — and the original error follows the colon. A RAISEd exception uses its own declared name in
 * place of STATEMENT_ERROR.
 *
 * <p>Two boundaries matter as much as the sentence. A HANDLED error never gets it, and SQLERRM inside
 * the handler still reads the raw text. And a name the BLOCK owns — a {@code :variable} that was never
 * declared — is refused bare: live resolves a block's own names when it compiles the block, before any
 * statement runs, so that refusal is a compilation error rather than an uncaught exception. An
 * unresolvable COLUMN is the other side of the same line: it belongs to the statement's compilation,
 * so it IS wrapped.
 */
public class UncaughtStatementErrorTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (n INT)");
    }

    /** The message of the error {@code block} fails with. */
    private String failureOf(final String block) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(block);
            }
        }, block);
        return String.valueOf(ex.getMessage());
    }

    private void assertWrappedAt(final int line, final int position, final String inner,
                                 final String block) {
        final String message = failureOf(block);
        assertTrue(message.startsWith("Uncaught exception of type 'STATEMENT_ERROR' on line "
                + line + " at position " + position + " : "),
            "expected the uncaught sentence at " + line + ":" + position + ", got: " + message);
        assertTrue(message.contains(inner), "expected the original error in the tail, got: " + message);
    }

    @Test
    public void anUncaughtStatementErrorNamesTheStatementThatFailed() {
        assertWrappedAt(4, 4, "Division by zero", """
            BEGIN
                LET x INT := 1;
                LET y INT := 2;
                SELECT 1/0;
            END;
            """);
    }

    @Test
    public void aNestedBlocksFailingStatementNamesItself() {
        assertWrappedAt(3, 8, "Division by zero", """
            BEGIN
                BEGIN
                    SELECT 1/0;
                END;
            END;
            """);
    }

    @Test
    public void anIfBranchesFailingStatementNamesItself() {
        assertWrappedAt(3, 8, "Division by zero", """
            BEGIN
                IF (1 = 1) THEN
                    SELECT 1/0;
                END IF;
            END;
            """);
    }

    @Test
    public void aLoopBodysFailingStatementNamesItself() {
        assertWrappedAt(3, 8, "Division by zero", """
            BEGIN
                FOR i IN 1 TO 2 DO
                    SELECT 1/0;
                END FOR;
            END;
            """);
    }

    @Test
    public void anUnknownObjectIsWrappedWithItsCompilationError() {
        assertWrappedAt(2, 4, "NO_SUCH_TABLE", """
            BEGIN
                INSERT INTO no_such_table VALUES (1);
            END;
            """);
    }

    /** The statement's OWN compilation failing is still the statement failing — it is wrapped. */
    @Test
    public void anUnresolvableColumnIsWrappedToo() {
        assertWrappedAt(2, 4, "invalid identifier 'NOSUCHCOL'", """
            BEGIN
                SELECT nosuchcol FROM t;
            END;
            """);
    }

    @Test
    public void aHandledErrorIsNotWrappedAndSqlerrmKeepsTheRawText() {
        final Object handled = engine.executeQuery("""
            BEGIN
                SELECT 1/0;
                RETURN 'unreached';
            EXCEPTION
                WHEN OTHER THEN
                    RETURN SQLERRM;
            END;
            """).getRows().get(0).getValue(0);
        assertTrue(String.valueOf(handled).contains("Division by zero"),
            "SQLERRM reads the raw error, not the uncaught sentence: " + handled);
        assertTrue(!String.valueOf(handled).contains("Uncaught exception"),
            "a handled error never carries the uncaught sentence: " + handled);
    }

    /** A bare RAISE re-raises the ORIGINAL failure, and keeps that statement's position. */
    @Test
    public void aReRaiseKeepsTheOriginalStatementsPosition() {
        assertWrappedAt(2, 4, "Division by zero", """
            BEGIN
                SELECT 1/0;
            EXCEPTION
                WHEN OTHER THEN
                    RAISE;
            END;
            """);
    }

    @Test
    public void aRaisedExceptionUsesItsOwnDeclaredName() {
        final String message = failureOf("""
            DECLARE
                my_error EXCEPTION (-20001, 'boom');
            BEGIN
                RAISE my_error;
            END;
            """);
        assertEquals("Uncaught exception of type 'MY_ERROR' on line 4 at position 4 : boom", message);
    }

    /** A name the BLOCK owns is resolved when the block compiles, so its refusal is not an uncaught error. */
    @Test
    public void anUndeclaredScriptVariableIsRefusedWithoutTheUncaughtSentence() {
        final String message = failureOf("BEGIN INSERT INTO t VALUES (:nope); RETURN 'ok'; END");
        assertTrue(message.contains("invalid identifier 'NOPE'"), message);
        assertTrue(!message.contains("Uncaught exception"),
            "a block-compile refusal is reported bare: " + message);
    }

    @Test
    public void aFailingProcedureBodyNamesTheStatementInsideIt() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE boom()
            RETURNS INT
            LANGUAGE SQL
            AS $$
            BEGIN
                SELECT 1/0;
                RETURN 1;
            END;
            $$
            """);
        assertWrappedAt(3, 4, "Division by zero", "CALL boom()");
    }
}
