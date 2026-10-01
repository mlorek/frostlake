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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Outside a BEGIN … END block a scripting statement is a syntax error at its first word, and that word is the whole
 * report — the rest of the statement and a later statement's fault are not named; a fault earlier in the text still
 * wins. The text an EXECUTE IMMEDIATE runs is a script of its own, even while a block runs it (live-verified).
 */
public class ScriptingStatementPlacementTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String refused(final int line, final int position, final String token) {
        return "SQL compilation error:\nsyntax error line " + line + " at position " + position + " unexpected '"
            + token + "'.";
    }

    @Test
    public void everyScriptingStatementIsRefusedAtItsFirstWord() {
        final String[][] cells = {
            {"LET a := 1 x y", "LET"}, {"RETURN 1 x y", "RETURN"}, {"BREAK x y", "BREAK"},
            {"CONTINUE x y", "CONTINUE"}, {"RAISE e x", "RAISE"}, {"a := 1", "a"}, {"a := 1 x y", "a"},
            {"IF (TRUE) THEN SELECT 1; END IF", "IF"}, {"IF (TRUE) THEN SELECT 1; END IF x y", "IF"},
            {"FOR i IN 1 TO 2 DO SELECT 1; END FOR", "FOR"}, {"WHILE (TRUE) DO BREAK; END WHILE", "WHILE"},
            {"LOOP BREAK; END LOOP", "LOOP"}, {"REPEAT BREAK; UNTIL (TRUE) END REPEAT", "REPEAT"},
            {"CASE WHEN TRUE THEN SELECT 1; END CASE", "CASE"}, {"OPEN c", "OPEN"}, {"FETCH c INTO x", "FETCH"},
            {"CLOSE c", "CLOSE"}, {"AWAIT ALL", "AWAIT"}, {"ASYNC (SELECT 1)", "ASYNC"}};
        for (final String[] cell : cells) {
            assertEquals(refused(1, 0, cell[1]), refusal(cell[0]), cell[0]);
        }
        assertEquals(refused(1, 2, "LET"), refusal("  LET a := 1 x y"));
    }

    @Test
    public void aLoneKeywordIsRefusedAtItself() {
        final String[][] cells = {{"LET", "LET"}, {"LET;", "LET"}, {"IF;", "IF"}, {"WHILE", "WHILE"},
            {"LOOP", "LOOP"}, {"REPEAT", "REPEAT"}, {"OPEN", "OPEN"}, {"FETCH", "FETCH"}, {"CLOSE", "CLOSE"},
            {"AWAIT", "AWAIT"}, {"ASYNC", "ASYNC"}};
        for (final String[] cell : cells) {
            assertEquals(refused(1, 0, cell[1]), refusal(cell[0]), cell[0]);
        }
    }

    @Test
    public void theWordIsTheWholeReportAndAnEarlierFaultWins() {
        assertEquals(refused(1, 0, "LET"), refusal("LET a := 1; SELECT 1 x y"));
        assertEquals(refused(1, 0, "LET"), refusal("LET a := 1 UPDATE t SET a = b WHERE c = d e f"));
        assertEquals(refused(1, 10, "LET"), refusal("SELECT 1; LET a := 1 x y"));
        assertEquals(refused(1, 10, "IF"), refusal("SELECT 1; IF (TRUE) THEN SELECT 1; END IF"));
        assertEquals(refused(1, 11, "y"), refusal("SELECT 1 x y; LET a := 1"));
        // A scripting word straight after a statement opens none: it is the table's alias here.
        assertEquals(refused(1, 22, "x"), refusal("SELECT 1 FROM t BREAK x y"));
    }

    @Test
    public void theTextAnExecuteImmediateRunsIsAScriptOfItsOwn() {
        assertEquals(refused(1, 0, "LET"), refusal("EXECUTE IMMEDIATE 'LET a := 1 x y'"));
        assertEquals(refused(1, 0, "IF"), refusal("EXECUTE IMMEDIATE 'IF (TRUE) THEN RETURN 1; END IF'"));
        assertEquals(refused(1, 0, "FOR"), refusal("EXECUTE IMMEDIATE $$FOR i IN 1 TO 2 DO SELECT 1; END FOR$$"));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : "
            + refused(1, 0, "RETURN"), refusal("BEGIN EXECUTE IMMEDIATE 'RETURN 1'; RETURN 2; END"));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : "
            + refused(1, 0, "a"), refusal("BEGIN EXECUTE IMMEDIATE 'a := 1'; RETURN 1; END"));
    }

    @Test
    public void aBlockKeepsItsOwnScriptingStatements() {
        final ResultSet counted = engine.executeQuery(
            "EXECUTE IMMEDIATE $$ DECLARE i INT DEFAULT 0; BEGIN FOR i IN 1 TO 3 DO NULL; END FOR; RETURN i; END $$");
        assertEquals("0", String.valueOf(counted.getRows().get(0).getValues().get(0)));
        final ResultSet assigned = engine.executeQuery(
            "EXECUTE IMMEDIATE 'BEGIN LET a := 1; IF (TRUE) THEN a := 2; END IF; RETURN a; END'");
        assertEquals("2", String.valueOf(assigned.getRows().get(0).getValues().get(0)));
    }
}
