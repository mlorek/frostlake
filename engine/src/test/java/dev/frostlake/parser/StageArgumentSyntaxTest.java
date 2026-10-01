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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.expressions.LiteralExpression;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stage written bare as an expression: the first argument of a stage function takes one, and every other place
 * refuses it in the account's words — a call that does not take it by the argument sentence, positioned at that
 * call, and anywhere that is no call's argument by a syntax error at the stage.
 */
public class StageArgumentSyntaxTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String argument(final int position, final String function, final String stage, final int index) {
        return "SQL compilation error: error line 1 at position " + position + "\ninvalid argument for function ["
            + function + "] unexpected argument [" + stage + "] at position " + index + ",";
    }

    @Test
    public void aCallThatTakesNoStageRefusesItAtTheCall() {
        engine.execute("CREATE STAGE sa_st");
        engine.execute("CREATE TABLE sa_t (a INT)");
        assertEquals(argument(7, "UPPER", "@sa_st", 0), refusal("SELECT UPPER(@sa_st)"));
        assertEquals(argument(7, "UPPER", "@sa_st", 0), refusal("SELECT upper(@sa_st)"));
        assertEquals(argument(13, "UPPER", "@sa_st", 0), refusal("SELECT LOWER(UPPER(@sa_st))"));
        assertEquals(argument(11, "UPPER", "@sa_st", 0), refusal("SELECT 1 + UPPER(@sa_st)"));
        assertEquals(argument(7, "CONCAT", "@sa_st", 2), refusal("SELECT CONCAT('a', 'b', @sa_st)"));
        assertEquals(argument(7, "UPPER", "@TEST_SCHEMA.sa_st", 0), refusal("SELECT UPPER(@TEST_SCHEMA.sa_st)"));
        assertEquals(argument(7, "UPPER", "@~", 0), refusal("SELECT UPPER(@~)"));
        assertEquals(argument(7, "UPPER", "@%sa_t", 0), refusal("SELECT UPPER(@%sa_t)"));
        assertEquals(argument(7, "SYSTEM$TYPEOF", "@sa_st", 0), refusal("SELECT SYSTEM$TYPEOF(@sa_st)"));
        assertEquals(argument(7, "COUNT", "@sa_st", 0), refusal("SELECT COUNT(@sa_st) FROM sa_t"));
        assertEquals(argument(32, "UPPER", "@sa_st", 0),
            refusal("SELECT COUNT(*) FROM sa_t WHERE UPPER(@sa_st) = 'x'"));
    }

    @Test
    public void onlyTheFirstArgumentOfAStageFunctionTakesAStage() {
        engine.execute("CREATE STAGE sa_st2");
        assertEquals(argument(7, "BUILD_STAGE_FILE_URL", "@sa_st2", 1),
            refusal("SELECT BUILD_STAGE_FILE_URL(@sa_st2, @sa_st2)"));
        assertEquals(argument(7, "GET_PRESIGNED_URL", "@sa_st2", 2),
            refusal("SELECT GET_PRESIGNED_URL(@sa_st2, 'f', @sa_st2)"));
    }

    @Test
    public void aUserFunctionRefusesAStageByItsName() {
        engine.execute("CREATE STAGE sa_st3");
        engine.execute("CREATE FUNCTION sa_fx(x VARCHAR) RETURNS VARCHAR AS 'x'");
        assertEquals(argument(7, "SA_FX", "@sa_st3", 0), refusal("SELECT sa_fx(@sa_st3)"));
    }

    /** The argument is refused before any name the statement holds is resolved. */
    @Test
    public void theArgumentIsRefusedAheadOfAMissingTable() {
        assertEquals(argument(7, "UPPER", "@sa_st4", 0), refusal("SELECT UPPER(@sa_st4) FROM sa_nosuch_table"));
    }

    @Test
    public void aStageThatIsNoCallsArgumentIsASyntaxError() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 8 unexpected '@sa_st'.",
            refusal("SELECT (@sa_st)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 13 unexpected '@sa_st'.",
            refusal("SELECT 1 IN (@sa_st)"));
    }

    @Test
    public void aPathAfterTheStageIsASyntaxError() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 28 unexpected '@st/dir'.\n"
                + "syntax error line 1 at position 27 unexpected '('.",
            refusal("SELECT BUILD_STAGE_FILE_URL(@st/dir, 'f.csv')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 28 unexpected '@st/'.\n"
                + "syntax error line 1 at position 27 unexpected '('.",
            refusal("SELECT BUILD_STAGE_FILE_URL(@st/, 'f.csv')"));
    }

    @Test
    public void aBlankEndsTheStage() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 30 unexpected 'st'.\n"
                + "syntax error line 1 at position 41 unexpected ')'.",
            refusal("SELECT BUILD_STAGE_FILE_URL(@ st, 'f.csv')"));
        assertEquals("SQL compilation error:\nparse error line 1 at position 49 near '<EOF>'.\n"
                + "syntax error line 1 at position 33 unexpected 'stage'.",
            refusal("SELECT BUILD_STAGE_FILE_URL(@\"my stage\", 'f.csv')"));
    }

    /** A blank inside a quoted part ends the stage, and its closing quote opens a quoted name the lexer reads on. */
    @Test
    public void theClosingQuoteOpensANameTheLexerReadsOn() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 33 unexpected 'stage'.\n"
                + "syntax error line 1 at position 38 unexpected '\", 'a\"'.\n"
                + "parse error line 1 at position 47 near '<EOF>'.",
            refusal("SELECT BUILD_STAGE_FILE_URL(@\"my stage\", 'a\"b')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 33 unexpected 'stage'.\n"
                + "syntax error line 1 at position 38 unexpected '\", 'f.csv') /* \"'.",
            refusal("SELECT BUILD_STAGE_FILE_URL(@\"my stage\", 'f.csv') /* \" */"));
    }

    /** A stage no call takes is the text's syntax error, refused ahead of a call's argument sentence. */
    @Test
    public void aStageNoCallTakesIsRefusedFirst() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected '@sa_o'.",
            refusal("SELECT UPPER(@sa_o), (@sa_o)"));
    }

    /**
     * CALL passes a stage written bare on as the text written — a path, a user or table stage, a stage that does not
     * exist and a name of four parts alike — in a block too; anything after the stage in the argument is refused.
     */
    @Test
    public void aCallPassesAStageOnAsItsText() {
        engine.execute("CREATE TABLE sa_call_t (a INT)");
        engine.execute("CREATE PROCEDURE sa_pp(x VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN x; END; $$");
        for (final String stage : new String[] {"@sa_call_st", "@sa_call_st/dir/f.csv", "@~", "@%sa_call_t",
                "@sa_a.sa_b.sa_c.sa_d"}) {
            assertEquals(stage, String.valueOf(engine.executeQuery("CALL sa_pp(" + stage + ")").getRows().get(0)
                .getValue(0)), stage);
        }
        assertEquals("ok", String.valueOf(engine.executeQuery(
            "EXECUTE IMMEDIATE $$ BEGIN CALL sa_pp(@sa_call_st); RETURN 'ok'; END; $$").getRows().get(0).getValue(0)));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 18 unexpected '||'.",
            refusal("CALL sa_pp(@sa_st || 'x')"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 16 unexpected '@sa_st'.",
            refusal("CALL sa_pp(x => @sa_st)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 12 unexpected '@sa_st'.",
            refusal("CALL sa_pp((@sa_st))"));
    }

    /**
     * A routine's body is judged when it is created, in the frame it compiles in: a SQL UDF's expression, a table
     * function's query and a policy's body inside parentheses of their own — the first line moved by one — each
     * refusal opening "Compilation of SQL UDF failed: "; a scalar function's query or block and a procedure's block
     * as statements of their own. Nothing is created.
     */
    @Test
    public void aRoutineBodyIsJudgedWhenCreatedInItsOwnFrame() {
        engine.execute("CREATE STAGE sa_body_st");
        final String upper = "invalid argument for function [UPPER] unexpected argument [@sa_body_st] at position 0,";
        final String udf = "Compilation of SQL UDF failed: SQL compilation error: ";
        assertEquals(udf + "error line 1 at position 1\n" + upper,
            refusal("CREATE FUNCTION sa_f1() RETURNS VARCHAR AS 'UPPER(@sa_body_st)'"));
        assertEquals(udf + "error line 2 at position 1\n" + upper,
            refusal("CREATE FUNCTION sa_f2() RETURNS VARCHAR AS $$LOWER(\n UPPER(@sa_body_st))$$"));
        assertEquals(udf + "error line 1 at position 16\n" + upper,
            refusal("CREATE FUNCTION sa_f3(x VARCHAR) RETURNS VARCHAR AS 'IFF(x IS NULL, UPPER(@sa_body_st), x)'"));
        assertEquals("Compilation of SQL UDF failed: SQL compilation error:\nsyntax error line 1 at position 2"
            + " unexpected '@sa_body_st'.", refusal("CREATE FUNCTION sa_f4() RETURNS VARCHAR AS '(@sa_body_st)'"));
        assertEquals(udf + "error line 1 at position 10\n" + upper,
            refusal("CREATE FUNCTION sa_f5() RETURNS TABLE (s VARCHAR) AS $$  SELECT UPPER(@sa_body_st) $$"));
        assertEquals("SQL compilation error: error line 3 at position 1\n" + upper,
            refusal("CREATE FUNCTION sa_f6() RETURNS VARCHAR AS $$\nSELECT\n UPPER(@sa_body_st)$$"));
        assertEquals("SQL compilation error: error line 1 at position 15\n" + upper,
            refusal("CREATE FUNCTION sa_f7() RETURNS VARCHAR AS $$  BEGIN RETURN UPPER(@sa_body_st); END $$"));
        assertEquals("SQL compilation error: error line 3 at position 9\n" + upper, refusal(
            "CREATE PROCEDURE sa_p1() RETURNS VARCHAR LANGUAGE SQL AS $$\nBEGIN\n  RETURN UPPER(@sa_body_st);\nEND;\n$$"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 15 unexpected '@sa_body_st'.", refusal(
            "CREATE PROCEDURE sa_p2() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN (@sa_body_st); END; $$"));
        assertEquals(udf + "error line 1 at position 1\n" + upper,
            refusal("CREATE MASKING POLICY sa_mp AS (v VARCHAR) RETURNS VARCHAR ->\n  UPPER(@sa_body_st)"));
        assertEquals("SQL compilation error:\nUnknown function SA_F1.", refusal("SELECT sa_f1()"));
        engine.execute("CREATE FUNCTION sa_f8() RETURNS VARCHAR AS 'GET_STAGE_LOCATION(@sa_body_st)'");
        assertEquals("true", String.valueOf(engine.executeQuery(
            "SELECT sa_f8() = GET_STAGE_LOCATION(@sa_body_st)").getRows().get(0).getValue(0)).toLowerCase());
    }

    /**
     * A statement of a request carrying several is compiled on its own once the statements before it have run, so
     * its refusal counts from its own text; a stage no call takes is the whole request's syntax error, and nothing
     * runs.
     */
    @Test
    public void aStatementOfSeveralIsJudgedAfterTheOnesBeforeItRan() {
        Assumptions.assumeFalse(isLiveSnowflake(), "the live transport submits a script one statement at a time");
        engine.execute("CREATE TABLE sa_ms_t (s VARCHAR)");
        assertEquals("JavaScript execution error: Uncaught Execution of multiple statements failed on statement"
                + " \"SELECT LOWER(UPPER(@sa_m))\" (at line 2, position 0).\n"
                + argument(13, "UPPER", "@sa_m", 0)
                + " in SYSTEM$MULTISTMT at '    throw `Execution of multiple statements failed on statement"
                + " {0} (at line {1}, position {2}).`.replace('{1}', LINES[i])' position 4\n"
                + "stackstrace: \nSYSTEM$MULTISTMT line: 10",
            failure("INSERT INTO sa_ms_t VALUES ('first'); SELECT 1;\nSELECT LOWER(UPPER(@sa_m))"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM sa_ms_t").getRows().get(0)
            .getValue(0)));
        final String twice = "INSERT INTO sa_ms_t VALUES ('second'); SELECT UPPER(@sa_m), (@sa_m)";
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 61 unexpected '@sa_m'.",
            failure(twice));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT COUNT(*) FROM sa_ms_t").getRows().get(0)
            .getValue(0)));
    }

    /**
     * The argument sentence names the called function by its own name — a table function's stage by the stage's
     * name alone — at the call, except a window function that is no aggregate, which the account places nowhere.
     */
    @Test
    public void theArgumentSentenceNamesTheCallAsTheAccountDoes() {
        engine.execute("CREATE FUNCTION sa_n_fx(x VARCHAR) RETURNS VARCHAR AS 'x'");
        assertEquals(argument(20, "GET_STAGE_LOCATION", "SA_N_ST", 0),
            refusal("SELECT * FROM TABLE(GET_STAGE_LOCATION(@test_schema.sa_n_st))"));
        assertEquals(argument(20, "FLATTEN", "sa_n_st", 0), refusal("SELECT * FROM TABLE(FLATTEN(@\"sa_n_st\"))"));
        assertEquals(argument(20, "FLATTEN", "SA_N_ST", 0), refusal("SELECT * FROM TABLE(FLATTEN(@sa_n_st/x))"));
        assertEquals("SQL compilation error: error line 0 at position -1\ninvalid argument for function [LAG]"
            + " unexpected argument [@sa_n_st] at position 0,", refusal("SELECT LAG(@sa_n_st) OVER (ORDER BY 1)"));
        assertEquals(argument(7, "SUM", "@sa_n_st", 0), refusal("SELECT SUM(@sa_n_st) OVER ()"));
        assertEquals(argument(7, "COLLATE", "@sa_n_st", 0), refusal("SELECT COLLATE(@sa_n_st, 'en')"));
        assertEquals(argument(7, "COLLATE", "@sa_n_st", 1), refusal("SELECT COLLATE('x', @sa_n_st)"));
        assertEquals(argument(7, "SA_N_FX", "@sa_n_st", 0), refusal("SELECT test_schema.sa_n_fx(@sa_n_st)"));
    }

    /**
     * No text the engine reads on its own evaluates a stage where none is taken, whatever path the text arrived by;
     * a stage that is the whole text is a CALL argument read back on its own and passes.
     */
    @Test
    public void aTextReadOnItsOwnIsHeldToTheSameRules() {
        Assumptions.assumeFalse(isLiveSnowflake(), "reads the engine's own expression parser");
        final String refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                ExpressionEvaluator.parse("IFF(TRUE, UPPER(@sa_own), 'x')");
            }
        }).getMessage();
        assertEquals(argument(10, "UPPER", "@sa_own", 0), refused);
        assertTrue(assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                ExpressionEvaluator.parse("(@sa_own)");
            }
        }).getMessage().contains("unexpected '@sa_own'"));
        assertEquals("@sa_own/p", ((LiteralExpression) ExpressionEvaluator.parse("@sa_own/p")).getValue());
    }

    private String failure(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    @Test
    public void aStageFunctionTakesTheStageBare() {
        engine.execute("CREATE STAGE sa_ok");
        final String url = String.valueOf(engine.executeQuery(
            "SELECT BUILD_STAGE_FILE_URL( @sa_ok , 'f.csv')").getRows().get(0).getValue(0));
        assertTrue(url.endsWith("/api/files/TEST_DB/TEST_SCHEMA/SA_OK/f.csv"), url);
    }
}
