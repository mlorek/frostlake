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
 * A script's SELECT … INTO target list is refused at the comma whose continuation is no target list: a comma not
 * followed by a target, or a plain name followed by neither a comma nor a word the list may end at. A bind target
 * reads one token further, so the later word is refused instead. In a block, a handler or a control construct's body
 * the recovery goes on from that comma as its place decides (live-verified).
 */
public class IntoListLookaheadTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String refused(final int position, final String token) {
        return "SQL compilation error:\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void theCommaBeforeADeadContinuationIsRefused() {
        assertEquals(refused(15, ","), refusal("SELECT 1 INTO t, u VALUES (1)"));
        assertEquals(refused(18, ","), refusal("SELECT 1, 2 INTO t, u VALUES (1)"));
        assertEquals(refused(18, ","), refusal("SELECT 1 INTO t, u, v VALUES (1)"));
        assertEquals(refused(15, ","), refusal("SELECT 1 INTO t, u x"));
        assertEquals(refused(18, ","), refusal("SELECT 1, 2 INTO t, u x y"));
        assertEquals(refused(15, ","), refusal("SELECT 1 INTO t, u VALUES"));
        assertEquals(refused(15, ","), refusal("SELECT 1 INTO t, 1"));
        assertEquals(refused(15, ","), refusal("SELECT 1 INTO t,, u"));
    }

    @Test
    public void aSingleTargetOrABindTargetKeepsTheLaterWord() {
        assertEquals(refused(16, "VALUES"), refusal("SELECT 1 INTO t VALUES (1)"));
        assertEquals(refused(16, "x"), refusal("SELECT 1 INTO t x"));
        assertEquals(refused(21, "VALUES"), refusal("SELECT 1 INTO :t, :u VALUES (1)"));
    }

    private static String refused(final int position, final String token, final int nextPosition,
                                  final String next) {
        return refused(position, token) + "\nsyntax error line 1 at position " + nextPosition + " unexpected '"
            + next + "'.";
    }

    @Test
    public void inABlockTheRecoveryTakesTheNextNameAndEndsTheReport() {
        assertEquals(refused(21, ",", 25, "VALUES"), refusal("BEGIN SELECT 1 INTO t, u VALUES (1); END"));
        assertEquals(refused(21, ",", 25, "x"), refusal("BEGIN SELECT 1 INTO t, u x; END"));
        assertEquals(refused(24, ",", 28, "VALUES"), refusal("BEGIN SELECT 1 INTO t, u, v VALUES (1); END"));
        assertEquals(refused(24, ",", 28, "x"), refusal("BEGIN SELECT 1, 2 INTO t, u x y; END"));
        assertEquals(refused(21, ",", 27, "END"), refusal("BEGIN SELECT 1 INTO t,, u; END"));
        assertEquals(refused(21, ","), refusal("BEGIN SELECT 1 INTO t, 1; END"));
        assertEquals(refused(21, ",", 25, "VALUES"), refusal("BEGIN SELECT 1 INTO t, u VALUES (1); RETURN 1 1; END"));
        assertEquals(refused(36, ",", 40, "VALUES"),
            refusal("DECLARE x INT; BEGIN SELECT 1 INTO t, u VALUES (1); END"));
        assertEquals(refused(57, ",", 61, "VALUES"),
            refusal("BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN SELECT 1 INTO t, u VALUES (1); END"));
        assertEquals(refused(22, ",", 26, "VALUES"),
            refusal("EXECUTE IMMEDIATE $$ BEGIN SELECT 1 INTO t, u VALUES (1); END $$"));
        assertEquals("""
            SQL compilation error:
            syntax error line 2 at position 15 unexpected ','.
            syntax error line 2 at position 19 unexpected 'VALUES'.""",
            refusal("BEGIN\nSELECT 1 INTO t, u VALUES (1);\nEND"));
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aLoopWhileOrForBodyRefusesTheTokenAfterTheNameAndReadsOn() {
        assertEquals(lines(at(26, ","), at(30, "x"), at(33, "END")),
            refusal("BEGIN LOOP SELECT 1 INTO t, u x; END LOOP; END"));
        assertEquals(lines(at(26, ","), at(30, "x"), at(32, "y")),
            refusal("BEGIN LOOP SELECT 1 INTO t, u x y; END LOOP; END"));
        assertEquals(lines(at(26, ","), at(30, "x"), at(33, "BREAK")),
            refusal("BEGIN LOOP SELECT 1 INTO t, u x; BREAK; END LOOP; END"));
        assertEquals(lines(at(26, ","), at(30, "VALUES"), at(52, "END")),
            refusal("BEGIN LOOP SELECT 1 INTO t, u VALUES (1); END LOOP; END"));
        assertEquals(lines(at(26, ","), at(30, "VALUES")),
            refusal("BEGIN LOOP SELECT 1 INTO t, u VALUES (1); BREAK; END LOOP; END"));
        assertEquals(lines(at(33, ","), at(37, "VALUES"), at(59, "END")),
            refusal("BEGIN LOOP BREAK; SELECT 1 INTO t, u VALUES (1); END LOOP; END"));
        assertEquals(lines(at(26, ","), at(30, "VALUES"), at(52, "RETURN")),
            refusal("BEGIN LOOP SELECT 1 INTO t, u VALUES (1); END LOOP; RETURN 1; END"));
        assertEquals(lines(at(26, ","), at(30, "VALUES"), at(44, "END")),
            refusal("BEGIN LOOP SELECT 1 INTO t, u VALUES (1) x; END LOOP; END"));
        assertEquals(lines(at(26, ","), at(30, "+"), at(45, "END")),
            refusal("BEGIN LOOP SELECT 1 INTO t, u + 1; END LOOP; END"));
        assertEquals(lines(at(26, ","), at(42, "END")), refusal("BEGIN LOOP SELECT 1 INTO t,, u; END LOOP; END"));
        assertEquals(lines(at(26, ","), at(49, "END")),
            refusal("BEGIN LOOP SELECT 1 INTO t,, u; BREAK; END LOOP; END"));
        assertEquals(lines(at(26, ",")), refusal("BEGIN LOOP SELECT 1 INTO t, 1; END LOOP; END"));
        assertEquals(lines(at(37, ","), at(41, "x"), at(44, "END")),
            refusal("BEGIN WHILE (TRUE) DO SELECT 1 INTO t, u x; END WHILE; END"));
        assertEquals(lines(at(37, ","), at(41, "VALUES"), at(64, "END")),
            refusal("BEGIN WHILE (TRUE) DO SELECT 1 INTO t, u VALUES (1); END WHILE; END"));
        assertEquals(lines(at(37, ","), at(61, "END")),
            refusal("BEGIN WHILE (TRUE) DO SELECT 1 INTO t,, u; BREAK; END WHILE; END"));
        assertEquals(lines(at(40, ","), at(44, "x"), at(47, "END")),
            refusal("BEGIN FOR i IN 1 TO 2 DO SELECT 1 INTO t, u x; END FOR; END"));
        assertEquals(lines(at(40, ","), at(44, "VALUES")),
            refusal("BEGIN FOR i IN 1 TO 2 DO SELECT 1 INTO t, u VALUES (1); END FOR; END"));
        assertEquals(lines(at(40, ","), at(50, "FOR")),
            refusal("BEGIN FOR i IN 1 TO 2 DO SELECT 1 INTO t,, u; END FOR; END"));
        assertEquals(lines(at(40, ","), at(44, "+")),
            refusal("BEGIN FOR i IN 1 TO 2 DO SELECT 1 INTO t, u + 1; END FOR; END"));
    }

    @Test
    public void anIfCaseOrRepeatBodyPassesTheNameOver() {
        assertEquals(lines(at(36, ","), at(60, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t, u VALUES (1); END IF; END"));
        assertEquals(lines(at(36, ","), at(43, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t, u x; END IF; END"));
        assertEquals(lines(at(36, ","), at(42, "y")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t, u x y; END IF; END"));
        assertEquals(lines(at(36, ","), at(43, "RETURN")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t, u x; RETURN 1; END IF; END"));
        assertEquals(lines(at(36, ",")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t, u VALUES (1); RETURN 1; END IF; END"));
        assertEquals(lines(at(36, ","), at(50, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t,, u; END IF; END"));
        assertEquals(lines(at(36, ",")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t,, u; ELSE RETURN 1; END IF; END"));
        assertEquals(lines(at(36, ","), at(43, "ELSE")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t, u x; ELSE RETURN 1; END IF; END"));
        assertEquals(lines(at(51, ","), at(58, "END")),
            refusal("BEGIN IF (TRUE) THEN RETURN 1; ELSE SELECT 1 INTO t, u x; END IF; END"));
        assertEquals(lines(at(46, ","), at(70, "END")),
            refusal("BEGIN IF (TRUE) THEN RETURN 1; SELECT 1 INTO t, u VALUES (1); END IF; END"));
        assertEquals(lines(at(36, ","), at(53, "END")),
            refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t, u + 1; END IF; END"));
        assertEquals(lines(at(36, ",")), refusal("BEGIN IF (TRUE) THEN SELECT 1 INTO t, 1; END IF; END"));
        assertEquals(lines(at(41, ","), at(67, "END")),
            refusal("BEGIN CASE WHEN TRUE THEN SELECT 1 INTO t, u VALUES (1); END CASE; END"));
        assertEquals(lines(at(41, ","), at(48, "END")),
            refusal("BEGIN CASE WHEN TRUE THEN SELECT 1 INTO t, u x; END CASE; END"));
        assertEquals(lines(at(41, ",")),
            refusal("BEGIN CASE WHEN TRUE THEN SELECT 1 INTO t, u VALUES (1); WHEN FALSE THEN RETURN 1; END CASE; END"));
        assertEquals(lines(at(28, ","), at(35, "UNTIL")),
            refusal("BEGIN REPEAT SELECT 1 INTO t, u x; UNTIL (TRUE) END REPEAT; END"));
        assertEquals(lines(at(28, ",")),
            refusal("BEGIN REPEAT SELECT 1 INTO t, u VALUES (1); UNTIL (TRUE) END REPEAT; END"));
        assertEquals(lines(at(28, ",")), refusal("BEGIN REPEAT SELECT 1 INTO t,, u; UNTIL (TRUE) END REPEAT; END"));
    }

    @Test
    public void aNestedIfBranchWithASecondNameReadsOnPastTheIf() {
        assertEquals(lines(at(41, ","), at(56, "END")),
            refusal("BEGIN LOOP IF (TRUE) THEN SELECT 1 INTO t, u x; END IF; END LOOP; END"));
        assertEquals(lines(at(41, ","), at(58, "END")),
            refusal("BEGIN LOOP IF (TRUE) THEN SELECT 1 INTO t, u x y; END IF; END LOOP; END"));
        assertEquals(lines(at(42, ","), at(57, "END")),
            refusal("BEGIN BEGIN IF (TRUE) THEN SELECT 1 INTO t, u x; END IF; END; END"));
    }

    @Test
    public void inABlockABindTargetKeepsTheLaterWord() {
        assertEquals(refused(27, "VALUES"), refusal("BEGIN SELECT 1 INTO :t, :u VALUES (1); END"));
        assertEquals(refused(22, "VALUES"), refusal("BEGIN SELECT 1 INTO t VALUES (1); END"));
    }
}
