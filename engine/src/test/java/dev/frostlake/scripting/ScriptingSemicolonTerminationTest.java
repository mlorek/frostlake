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
 * Every statement inside a Snowflake Scripting block is TERMINATED by its semicolon, and the block
 * is refused when one is missing — live-verified at each position it can be missing from: the last
 * statement before END, a statement mid-block, a nested block's END, END IF, END LOOP, END CASE, a
 * statement inside THEN, an EXCEPTION handler's body, and every DECLARE item (the last one before
 * BEGIN included).
 *
 * <p>The refusal is a plain syntax error anchored on the token that FOLLOWS the unterminated
 * statement — {@code BEGIN SELECT 'foo' RETURN :v; END} reads as {@code SELECT 'foo' RETURN}, a
 * select with an alias, and then dies. Frostlake anchors on the same line, on the statement keyword
 * rather than inside the following expression; the refusal, not the exact character, is the cell
 * these tests pin.
 *
 * <p>The mirror image is just as measured: EXTRA semicolons after a statement are tolerated
 * ({@code RETURN 1;;}, a lone {@code ;} line between two statements or before END, the same in a
 * DECLARE section — real deployment scripts end their declarations with one), while a statement
 * list may not OPEN with one.
 */
public class ScriptingSemicolonTerminationTest extends BaseDatabaseTest {

    /** The block's result, as text. */
    private String returned(final String block) {
        final Object value = engine.executeQuery(block).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    /** A block Snowflake refuses to compile: the engine must refuse it too, as a syntax error. */
    private void assertRefused(final String block) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(block);
            }
        });
        assertTrue(String.valueOf(ex.getMessage()).contains("syntax error"),
            "expected a syntax error, got: " + ex.getMessage());
    }

    // ── the terminator is required, wherever the statement sits ──────────────────────────────────

    @Test
    public void aFullyTerminatedBlockRuns() {
        assertEquals("1", returned("""
            BEGIN
                LET x INT := 1;
                RETURN x;
            END;
            """));
    }

    @Test
    public void theLastStatementBeforeEndNeedsItsTerminator() {
        assertRefused("""
            BEGIN
                RETURN 1
            END;
            """);
    }

    @Test
    public void aStatementMidBlockNeedsItsTerminator() {
        assertRefused("""
            BEGIN
                LET x INT := 1
                RETURN x;
            END;
            """);
    }

    /** The reported shape: a trailing line comment changes nothing — the missing ';' is the cause. */
    @Test
    public void aTrailingLineCommentDoesNotStandInForTheTerminator() {
        assertRefused("""
            BEGIN
                SELECT 'foo' -- looks terminated, is not
                RETURN 1;
            END;
            """);
    }

    @Test
    public void aNestedBlocksEndNeedsItsTerminator() {
        assertRefused("""
            BEGIN
                BEGIN
                    RETURN 1;
                END
                RETURN 2;
            END;
            """);
    }

    @Test
    public void aNestedBlocksEndNeedsItsTerminatorEvenAsTheLastStatement() {
        assertRefused("""
            BEGIN
                BEGIN
                    RETURN 1;
                END
            END;
            """);
    }

    @Test
    public void endIfNeedsItsTerminator() {
        assertRefused("""
            BEGIN
                IF (1 = 1) THEN
                    RETURN 1;
                END IF
                RETURN 2;
            END;
            """);
    }

    @Test
    public void aStatementInsideThenNeedsItsTerminator() {
        assertRefused("""
            BEGIN
                IF (1 = 1) THEN
                    RETURN 1
                END IF;
                RETURN 2;
            END;
            """);
    }

    @Test
    public void endLoopNeedsItsTerminator() {
        assertRefused("""
            BEGIN
                LOOP
                    BREAK;
                END LOOP
                RETURN 2;
            END;
            """);
    }

    @Test
    public void endCaseNeedsItsTerminator() {
        assertRefused("""
            BEGIN
                CASE
                    WHEN (1 = 1) THEN
                        RETURN 1;
                END
                RETURN 2;
            END;
            """);
    }

    @Test
    public void anExceptionHandlersStatementNeedsItsTerminator() {
        assertRefused("""
            BEGIN
                RETURN 1;
            EXCEPTION
                WHEN OTHER THEN
                    RETURN 2
            END;
            """);
    }

    @Test
    public void aDeclarationNeedsItsTerminator() {
        assertRefused("""
            DECLARE
                x INT
            BEGIN
                RETURN 1;
            END;
            """);
    }

    @Test
    public void theLastDeclarationBeforeBeginNeedsItsTerminatorToo() {
        assertRefused("""
            DECLARE
                x INT;
                y INT
            BEGIN
                RETURN 1;
            END;
            """);
    }

    // ── extra semicolons after a statement are tolerated; a list may not open with one ───────────

    @Test
    public void anExtraSemicolonAfterAStatementIsTolerated() {
        assertEquals("1", returned("""
            BEGIN
                RETURN 1;;
            END;
            """));
    }

    @Test
    public void aLoneSemicolonBetweenTwoStatementsIsTolerated() {
        assertEquals("1", returned("""
            BEGIN
                LET x INT := 1;
                ;
                RETURN x;
            END;
            """));
    }

    @Test
    public void aLoneSemicolonBeforeEndIsTolerated() {
        assertEquals("1", returned("""
            BEGIN
                RETURN 1;
                ;
            END;
            """));
    }

    @Test
    public void aLoneSemicolonBeforeEndIfIsTolerated() {
        assertEquals("1", returned("""
            BEGIN
                IF (1 = 1) THEN
                    RETURN 1;
                    ;
                END IF;
                RETURN 2;
            END;
            """));
    }

    @Test
    public void straySemicolonsInTheDeclareSectionAreTolerated() {
        assertEquals("2", returned("""
            DECLARE
                x INT DEFAULT 2;;
                ;
            BEGIN
                RETURN x;
            END;
            """));
    }

    @Test
    public void aStatementListMayNotOpenWithASemicolon() {
        assertRefused("""
            BEGIN
                IF (1 = 1) THEN
                    ;
                    RETURN 1;
                END IF;
                RETURN 2;
            END;
            """);
    }

    @Test
    public void aLoopBodyMayNotOpenWithASemicolon() {
        assertRefused("""
            BEGIN
                LOOP
                    ;
                    BREAK;
                END LOOP;
                RETURN 1;
            END;
            """);
    }

    @Test
    public void aDeclareSectionMayNotOpenWithASemicolon() {
        assertRefused("""
            DECLARE
                ;
                x INT;
            BEGIN
                RETURN 1;
            END;
            """);
    }

    /** A nested block that IS terminated stays perfectly legal — the control for the refusals above. */
    @Test
    public void aTerminatedNestedBlockRuns() {
        assertEquals("1", returned("""
            BEGIN
                LET x INT := 1;
                BEGIN
                    RETURN x;
                END;
            END;
            """));
    }
}
