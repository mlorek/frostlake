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
 * Three boundary faults of a script, each refused by both engines and now anchored where the account
 * anchors them. Live-verified, shape by shape:
 *
 * <ul>
 *   <li>a SURPLUS END is named itself — {@code BEGIN … END; END;} is "unexpected 'END'" at the stray
 *       END's own place, after a block, after a SELECT, after an END IF or a CASE, standing alone, with
 *       or without its semicolon, and a second surplus END adds no second line;</li>
 *   <li>a block with a DECLARE section that runs out of input — more BEGINs than ENDs — is "unexpected
 *       '&lt;EOF&gt;'" at the input's own end, the anchor the undeclared shape already had, however the
 *       nesting is written and whatever the parser tripped over on the way;</li>
 *   <li>a LEADING semicolon blames the token after it, at that token's place — {@code ;SELECT 1} is
 *       "unexpected 'SELECT'" at position 1, and a newline moves the anchor to line 2 — while
 *       semicolons alone stay "Empty SQL statement." and a doubled separator between statements is
 *       accepted.</li>
 * </ul>
 *
 * <p>Frostlake used to name the semicolon after a surplus END, report two wrong lines for the
 * unbalanced declared block, and name the leading semicolon itself.
 *
 * <p>NOT COVERED: three semicolons BETWEEN statements ({@code SELECT 1;;;SELECT 2}), which the live
 * harness refuses by statement count before the account sees it; and a body given to CREATE
 * PROCEDURE, which is not compiled at CREATE at all.
 */
public class BlockBoundaryRefusalTest extends BaseDatabaseTest {

    /** The refusal with its line breaks shown as bars, or ACCEPTED with the first cell. */
    private String outcome(final String sql) {
        try {
            return "ACCEPTED " + engine.executeQuery(sql).getRows().get(0).getValue(0);
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String unexpected(final int line, final int position, final String token) {
        return "SQL compilation error:|syntax error line " + line + " at position " + position
            + " unexpected '" + token + "'.";
    }

    /** ★ The stray END is named itself, wherever it stands. */
    @Test
    public void aSurplusEndIsNamedItself() {
        assertEquals(unexpected(4, 0, "END"), outcome("BEGIN\n    RETURN 42;\nEND;\nEND;"));
        assertEquals(unexpected(4, 0, "END"), outcome("BEGIN\n    RETURN 42;\nEND;\nEND"), "with no semicolon after it");
        assertEquals(unexpected(4, 2, "END"), outcome("BEGIN\n    RETURN 42;\nEND;\n  END;"), "at its own column");
        assertEquals(unexpected(1, 22, "END"), outcome("BEGIN RETURN 42; END; END;"));
        assertEquals(unexpected(1, 22, "END"), outcome("BEGIN RETURN 42; END; END; END;"),
            "a second surplus END adds no second line");
        assertEquals(unexpected(1, 48, "END"), outcome("DECLARE x INTEGER; BEGIN x := 1; RETURN x; END; END;"));
        assertEquals(unexpected(1, 80, "END"),
            outcome("DECLARE x INTEGER; BEGIN x := 1; RETURN CASE WHEN x = 1 THEN 1 ELSE 2 END; END; END;"),
            "a CASE's END is not the block's");
        assertEquals(unexpected(1, 44, "END"), outcome("BEGIN IF (TRUE) THEN RETURN 1; END IF; END; END;"),
            "nor is an END IF");
        assertEquals(unexpected(1, 73, "END"),
            outcome("BEGIN LET i := 0; WHILE (i < 1) DO i := i + 1; END WHILE; RETURN i; END; END;"));
        assertEquals(unexpected(1, 10, "END"), outcome("SELECT 1; END;"), "after any statement");
        assertEquals(unexpected(1, 0, "END"), outcome("END;"), "and standing alone");
        assertEquals(unexpected(1, 0, "END"), outcome("END"));
    }

    /** ★ A declared block that runs out of input is anchored at the input's end. */
    @Test
    public void anUnbalancedDeclaredBlockRunsToTheEndOfItsInput() {
        assertEquals(unexpected(7, 4, "<EOF>"),
            outcome("DECLARE\n    x INTEGER;\nBEGIN\nBEGIN\n    x := 1;\n    RETURN x;\nEND;"));
        assertEquals(unexpected(8, 0, "<EOF>"),
            outcome("DECLARE\n    x INTEGER;\nBEGIN\nBEGIN\n    x := 1;\n    RETURN x;\nEND;\n"),
            "a trailing newline moves the anchor onto the next line");
        assertEquals(unexpected(1, 53, "<EOF>"), outcome("DECLARE x INTEGER; BEGIN BEGIN x := 1; RETURN x; END;"));
        assertEquals(unexpected(1, 85, "<EOF>"),
            outcome("DECLARE x INTEGER; BEGIN BEGIN x := 1; RETURN CASE WHEN x = 1 THEN 1 ELSE 2 END; END;"),
            "a CASE's END does not close the block");
        assertEquals(unexpected(1, 42, "<EOF>"), outcome("DECLARE x INTEGER; BEGIN x := 1; RETURN x;"), "no END at all");
        assertEquals(unexpected(6, 0, "<EOF>"), outcome("DECLARE\n    x INTEGER;\nBEGIN\n    x := 1;\n    RETURN x;\n"));
        assertEquals(unexpected(1, 24, "<EOF>"), outcome("DECLARE x INTEGER; BEGIN"));
        assertEquals(unexpected(1, 38, "<EOF>"), outcome("BEGIN IF (TRUE) THEN RETURN 1; END IF;"),
            "an END IF does not close the block either");
        assertEquals(unexpected(4, 4, "<EOF>"), outcome("BEGIN\nBEGIN\n    RETURN 42;\nEND;"),
            "the undeclared shape keeps its anchor");
        assertEquals("ACCEPTED 1",
            outcome("DECLARE x INTEGER; BEGIN x := 1; RETURN CASE WHEN x = 1 THEN 1 ELSE 2 END; END;"),
            "balanced, the CASE inside and all");
        assertEquals("ACCEPTED 1", outcome("BEGIN IF (TRUE) THEN RETURN 1; END IF; END;"));
        assertEquals("ACCEPTED 1", outcome("DECLARE\n    x INTEGER;\nBEGIN\n    x := 1;\n    RETURN x;\nEND;"));
    }

    /** ★ A leading semicolon blames what follows it, at its own place. */
    @Test
    public void aLeadingSemicolonBlamesTheTokenAfterIt() {
        assertEquals(unexpected(1, 1, "SELECT"), outcome(";SELECT 1"));
        assertEquals(unexpected(1, 2, "SELECT"), outcome("; SELECT 1"));
        assertEquals(unexpected(1, 2, "SELECT"), outcome(";;SELECT 1"), "however many semicolons lead");
        assertEquals(unexpected(2, 0, "SELECT"), outcome(";\nSELECT 1"));
        assertEquals(unexpected(1, 1, "BEGIN"), outcome(";BEGIN RETURN 1; END;"));
        assertEquals("SQL compilation error:|Empty SQL statement.", outcome(";"), "semicolons alone are empty");
        assertEquals("SQL compilation error:|Empty SQL statement.", outcome(";;"));
        assertEquals("ACCEPTED 1", outcome("SELECT 1;;"), "a doubled separator after a statement is fine");
        assertEquals("ACCEPTED 42", outcome("BEGIN\n    RETURN 42;\nEND;\n;"), "and so is one after a block");
    }
}
