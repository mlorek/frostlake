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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A LANGUAGE SQL procedure BODY is compiled at CREATE, not first read at CALL.
 *
 * <p>★ THE COORDINATE FRAME IS THE BODY'S OWN. A refusal inside a {@code $$} body counts its lines
 * from the character after the opening quote — line 1 is the remainder of the {@code $$} line — so a
 * bad width on the body's third line reports "error line 3", wherever the CREATE statement itself
 * put that line (live-verified).
 *
 * <p>★ THE WHOLE BODY IS CHECKED, REACHABLE OR NOT: a bad width inside {@code IF (FALSE)} and one in
 * the DECLARE section are both refused at CREATE, each at its own line and offset.
 *
 * <p>★ A REFUSED CREATE LEAVES NOTHING BEHIND. Calling the procedure afterwards reports the
 * unknown-function sentence, not a stale definition.
 *
 * <p>★ A BODY THAT RUNS OUT IS REFUSED AT ITS EOF. Two BEGINs closed by one END put the syntax error
 * at the body's end-of-input — the engine has read every token and positively knows the block never
 * closed. A body whose parse fails ANYWHERE ELSE is left alone: this grammar is a subset of
 * Snowflake's, so a mid-body error may be a construct the engine does not model, and refusing those
 * at CREATE would reject schemas a real account accepts.
 *
 * <p>★ A SINGLE PLAIN STATEMENT IS A BODY, once terminated. {@code $$ SELECT 1; $$},
 * {@code 'INSERT INTO t VALUES (1);'}, {@code $$ CALL p(); $$} and {@code $$ EXECUTE IMMEDIATE
 * 'SELECT 1'; $$} are created and CALL answers NULL — the statement's own result is never the call's
 * (live-verified). Without its semicolon the body is refused at the end of the input in the frame
 * live compiles it in, wrapped in a block of its own: line (body lines + 2), position 4.
 *
 * <p>★ A BARE SCRIPTING STATEMENT IS NO BODY. LET, RETURN, IF, WHILE, FOR, LOOP, CASE, BREAK and
 * RAISE without a block are refused at the word itself, in the body's own frame; a DECLARE section
 * with no block runs out at the body's end.
 *
 * <p>★ THE FRAME FOLLOWS THE SPELLING. A width refusal counts from the BODY — the line after
 * {@code $$}, or the BEGIN line of an unquoted body — while a SYNTAX error in an unquoted body counts
 * from the STATEMENT's first line, since the whole CREATE is what the parser read (live-verified).
 *
 * <p>★ CALL RESOLVES THROUGH THE FUNCTION VOCABULARY. A missing procedure is "Unknown function
 * NAME." for a bare name and "Unknown user-defined function DB.SCHEMA.NAME." for a qualified one —
 * upper-cased, full stop, no argument signature (live-verified) — not an object-does-not-exist
 * sentence.
 */
public class ProcedureBodyCompiledAtCreateTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    @Test
    public void badWidthInBodyRefusedAtCreate() {
        assertEquals("""
            SQL compilation error: error line 3 at position 16
            Invalid character length: 0. Must be between 1 and 134,217,728.""",
            refusal("""
                CREATE OR REPLACE PROCEDURE bcp()
                RETURNS VARCHAR LANGUAGE SQL AS $$
                BEGIN
                  LET v VARCHAR(0) := 'x';
                  RETURN v;
                END;
                $$"""));
    }

    @Test
    public void refusedCreateLeavesNoProcedureBehind() {
        refusal("""
            CREATE OR REPLACE PROCEDURE bcp2()
            RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              LET v VARCHAR(0) := 'x';
              RETURN v;
            END;
            $$""");
        assertEquals("SQL compilation error:\nUnknown function BCP2.", refusal("CALL bcp2()"));
    }

    @Test
    public void unreachableBranchIsStillChecked() {
        assertEquals("""
            SQL compilation error: error line 4 at position 18
            Invalid character length: 0. Must be between 1 and 134,217,728.""",
            refusal("""
                CREATE OR REPLACE PROCEDURE bur()
                RETURNS VARCHAR LANGUAGE SQL AS $$
                BEGIN
                  IF (FALSE) THEN
                    LET v VARCHAR(0) := 'x';
                  END IF;
                  RETURN 'ok';
                END;
                $$"""));
    }

    @Test
    public void declareSectionIsChecked() {
        assertEquals("""
            SQL compilation error: error line 3 at position 12
            Invalid character length: 0. Must be between 1 and 134,217,728.""",
            refusal("""
                CREATE OR REPLACE PROCEDURE bdw()
                RETURNS VARCHAR LANGUAGE SQL AS $$
                DECLARE
                  v VARCHAR(0);
                BEGIN
                  RETURN 'ok';
                END;
                $$"""));
    }

    @Test
    public void unbalancedBodyRefusedAtItsEndOfInput() {
        assertEquals("SQL compilation error:\nsyntax error line 6 at position 0 unexpected '<EOF>'.",
            refusal("""
                CREATE OR REPLACE PROCEDURE bub()
                RETURNS VARCHAR LANGUAGE SQL AS $$
                BEGIN
                  BEGIN
                    RETURN 42;
                  END;
                $$"""));
    }

    @Test
    public void aSingleTerminatedStatementIsABodyAndCallAnswersNull() {
        engine.execute("CREATE OR REPLACE TABLE pb_t (x INT)");
        engine.execute("CREATE OR REPLACE PROCEDURE pb1() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT 1; $$");
        assertEquals("null", String.valueOf(firstCell("CALL pb1()")));
        engine.execute("CREATE OR REPLACE PROCEDURE pb2() RETURNS VARCHAR LANGUAGE SQL AS"
            + " 'INSERT INTO pb_t VALUES (1);'");
        assertEquals("null", String.valueOf(firstCell("CALL pb2()")));
        assertEquals("1", String.valueOf(firstCell("SELECT COUNT(*) FROM pb_t")));
        engine.execute("CREATE OR REPLACE PROCEDURE pb3() RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ EXECUTE IMMEDIATE 'SELECT 1'; $$");
        assertEquals("null", String.valueOf(firstCell("CALL pb3()")));
        engine.execute("CREATE OR REPLACE PROCEDURE pb4() RETURNS VARCHAR LANGUAGE SQL AS $$ CALL pb1(); $$");
        assertEquals("null", String.valueOf(firstCell("CALL pb4()")));
        engine.execute("CREATE OR REPLACE PROCEDURE pb5() RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ UPDATE pb_t SET x = 2; $$");
        assertEquals("null", String.valueOf(firstCell("CALL pb5()")));
        assertEquals("2", String.valueOf(firstCell("SELECT MAX(x) FROM pb_t")));
    }

    @Test
    public void anUnterminatedStatementIsRefusedInTheWrappedFrame() {
        final String eof = "SQL compilation error:\nsyntax error line %d at position 4 unexpected '<EOF>'.";
        assertEquals(String.format(eof, 3),
            refusal("CREATE OR REPLACE PROCEDURE pu1() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT 1 $$"));
        assertEquals(String.format(eof, 3),
            refusal("CREATE OR REPLACE PROCEDURE pu2() RETURNS VARCHAR LANGUAGE SQL AS $$SELECT 1$$"));
        assertEquals(String.format(eof, 3),
            refusal("CREATE OR REPLACE PROCEDURE pu3() RETURNS VARCHAR LANGUAGE SQL AS 'SELECT 1'"));
        assertEquals(String.format(eof, 4),
            refusal("CREATE OR REPLACE PROCEDURE pu4() RETURNS VARCHAR LANGUAGE SQL AS $$ SELECT\n 1 $$"));
        assertEquals(String.format(eof, 5),
            refusal("CREATE OR REPLACE PROCEDURE pu5() RETURNS VARCHAR LANGUAGE SQL AS $$\nSELECT 1\n$$"));
        assertEquals("SQL compilation error:\nUnknown function PU1.", refusal("CALL pu1()"));
    }

    @Test
    public void aBareScriptingStatementIsRefusedAtItsWord() {
        final String[][] bodies = {
            {"LET", "LET x := 1;"}, {"RETURN", "RETURN 'r';"}, {"IF", "IF (TRUE) THEN RETURN 1; END IF;"},
            {"WHILE", "WHILE (FALSE) DO RETURN 1; END WHILE;"}, {"FOR", "FOR i IN 1 TO 2 DO RETURN 1; END FOR;"},
            {"LOOP", "LOOP RETURN 1; END LOOP;"}, {"CASE", "CASE WHEN TRUE THEN RETURN 1; END CASE;"},
            {"BREAK", "BREAK;"}, {"RAISE", "RAISE my_ex;"}};
        for (final String[] body : bodies) {
            assertEquals("SQL compilation error:\nsyntax error line 1 at position 1 unexpected '" + body[0] + "'.",
                refusal("CREATE OR REPLACE PROCEDURE pk() RETURNS VARCHAR LANGUAGE SQL AS $$ " + body[1] + " $$"),
                body[1]);
        }
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 16 unexpected '<EOF>'.",
            refusal("CREATE OR REPLACE PROCEDURE pk() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE x INT; $$"));
        assertEquals("SQL compilation error:\nUnknown function PK.", refusal("CALL pk()"));
    }

    @Test
    public void theFrameFollowsTheBodySpelling() {
        final String width = "\nInvalid character length: 0. Must be between 1 and 134,217,728.";
        // An unquoted body counts from its BEGIN line; a quoted one from the line after $$.
        assertEquals("SQL compilation error: error line 2 at position 16" + width,
            refusal("CREATE OR REPLACE PROCEDURE pf1() RETURNS VARCHAR LANGUAGE SQL AS\nBEGIN\n"
                + "  LET v VARCHAR(0) := 'x';\n  RETURN v;\nEND;"));
        assertEquals("SQL compilation error: error line 1 at position 20" + width,
            refusal("CREATE OR REPLACE PROCEDURE pf2() RETURNS VARCHAR LANGUAGE SQL AS BEGIN"
                + " LET v VARCHAR(0) := 'x'; RETURN v; END;"));
        assertEquals("SQL compilation error: error line 2 at position 16" + width,
            refusal("CREATE OR REPLACE PROCEDURE pf3() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN\n"
                + "  LET v VARCHAR(0) := 'x';\n  RETURN v;\nEND; $$"));
        assertEquals("SQL compilation error: error line 2 at position 16" + width,
            refusal("EXECUTE IMMEDIATE 'BEGIN\n  LET v VARCHAR(0) := ''x'';\n  RETURN v;\nEND;'"));
        // A SYNTAX error in an unquoted body counts from the statement's own first line.
        assertEquals("SQL compilation error:\nsyntax error line 4 at position 21 unexpected '1'.",
            refusal("CREATE OR REPLACE PROCEDURE pf4() RETURNS VARCHAR LANGUAGE SQL AS\nBEGIN\n"
                + "  RETURN 1;\n  LET r RESULTSET := 1;\nEND;"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 66 unexpected 'SELECT'.",
            refusal("CREATE OR REPLACE PROCEDURE pf5() RETURNS VARCHAR LANGUAGE SQL AS SELECT 1;"));
    }

    @Test
    public void aBlockPlusATrailingStatementStaysFailOpen() {
        // The live harness pins MULTI_STATEMENT_COUNT to 1 for every statement, and the account's
        // statement counter reads a SQL body's top-level statements, so this CREATE cannot reach the
        // account through it; under MULTI_STATEMENT_COUNT = 0 the account creates it and CALL
        // answers 1 — the block's RETURN, the trailing SELECT never running (live-verified).
        assumeFalse(isLiveSnowflake());
        engine.execute("CREATE OR REPLACE PROCEDURE pt1() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n"
            + "  RETURN 1;\nEND;\nSELECT 1;\n$$");
        assertEquals("1", String.valueOf(firstCell("CALL pt1()")));
    }

    @Test
    public void healthyBodyCreatesAndCalls() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE bok()
            RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              RETURN 'fine';
            END;
            $$""");
        assertEquals("fine", firstCell("CALL bok()"));
    }

    @Test
    public void missingProcedureUsesTheFunctionVocabulary() {
        assertEquals("SQL compilation error:\nUnknown function NOSUCHPROC.",
            refusal("CALL nosuchproc(1)"));
        assertEquals("SQL compilation error:\nUnknown user-defined function TEST_DB.TEST_SCHEMA.NOSUCHPROC2.",
            refusal("CALL test_db.test_schema.nosuchproc2()"));
    }

    private String firstCell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }
}
