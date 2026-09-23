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

package dev.frostlake.executor;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A request carrying SEVERAL statements names the one that failed; a request carrying one does not.
 *
 * <p>The account runs a multi-statement request through a helper, so a statement that fails inside one
 * comes back wrapped: the statement's own text, the line it starts on and its column within that line,
 * with the statement's own error carried inside, and the helper's name and internal stack around it. A
 * statement sent on its own fails with its own error and nothing else. A script that will not parse is
 * refused as a whole, before any of it runs, so it names no statement at all.
 */
public class MultiStatementFailureTest extends BaseDatabaseTest {

    /** The message a submission actually fails with. */
    private String messageOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    /** The account's sentence for a statement that failed inside a script of several. */
    private static String named(final String statement, final int line, final int position,
            final String inner) {
        return "JavaScript execution error: Uncaught Execution of multiple statements failed on statement"
            + " \"" + statement + "\" (at line " + line + ", position " + position + ").\n" + inner
            + " in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on statement"
            + " {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4\n"
            + "stackstrace: \nSYSTEM$MULTISTMT line: 10";
    }

    /**
     * Written out in full once, so a change to the wording has to be made here as well as where it is
     * produced. The position is the failing statement's column, counted from zero within its line.
     */
    @Test
    public void aStatementThatFailedInsideAScriptIsNamedInFull() {
        assertEquals(hinted("JavaScript execution error: Uncaught Execution of multiple statements failed on"
            + " statement \"SELECT * FROM nowhere_xyz\" (at line 1, position 52).\n"
            + "SQL compilation error:\nObject 'NOWHERE_XYZ' does not exist or not authorized."
            + " in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on statement"
            + " {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4\n"
            + "stackstrace: \nSYSTEM$MULTISTMT line: 10"),
            messageOf("CREATE TABLE p1 (n INT); INSERT INTO p1 VALUES (1); SELECT * FROM nowhere_xyz"));
    }

    /** A statement sent on its own carries its own error and nothing around it. */
    @Test
    public void aStatementSentOnItsOwnAnswersUnadorned() {
        assertEquals(hinted("SQL compilation error:\nObject 'NOWHERE_XYZ' does not exist or not authorized."),
            messageOf("SELECT * FROM nowhere_xyz"));
    }

    /** The line is the statement's own, and the position is its column within that line. */
    @Test
    public void thePositionIsTheStatementsColumnWithinItsOwnLine() {
        assertEquals(hinted(named("SELECT * FROM nowhere_xyz", 3, 2,
                "SQL compilation error:\nObject 'NOWHERE_XYZ' does not exist or not authorized.")),
            messageOf("SELECT 1;\nSELECT 2;\n  SELECT * FROM nowhere_xyz"));
    }

    /** The first statement of a script sits at column zero. */
    @Test
    public void theFirstStatementOfAScriptSitsAtColumnZero() {
        assertEquals(hinted(named("SELECT * FROM nowhere_xyz", 1, 0,
                "SQL compilation error:\nObject 'NOWHERE_XYZ' does not exist or not authorized.")),
            messageOf("SELECT * FROM nowhere_xyz; SELECT 1"));
    }

    /** A failure that is not a compilation error is carried the same way. */
    @Test
    public void aRuntimeFailureIsCarriedInsideTheSameSentence() {
        assertEquals(named("SELECT 1/0", 1, 10, "Division by zero"),
            messageOf("SELECT 1; SELECT 1/0"));
    }

    /**
     * A script whose text will not parse is refused as a whole before any of it runs, so it is an
     * ordinary syntax error naming no statement.
     */
    @Test
    public void aScriptThatWillNotParseNamesNoStatement() {
        assertEquals("SQL compilation error:\nsyntax error line 2 at position 0 unexpected 'SELEKT'.",
            messageOf("CREATE TABLE synt_t (id INTEGER);\nSELEKT * FROM synt_t"));
    }

    /** A trailing separator after the failing statement is not part of the statement it names. */
    @Test
    public void theNamedStatementExcludesItsSeparator() {
        assertEquals(hinted(named("SELECT * FROM nowhere_xyz", 1, 10,
                "SQL compilation error:\nObject 'NOWHERE_XYZ' does not exist or not authorized.")),
            messageOf("SELECT 1; SELECT * FROM nowhere_xyz;"));
    }
}
