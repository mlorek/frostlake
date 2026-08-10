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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live-verified surface of parser refusals and tolerances.
 *
 * <p>A refused statement reads {@code SQL compilation error:} followed by one line per error —
 * {@code syntax error line L at position P unexpected 'TOK'.} with the token echoed verbatim
 * (including case, {@code '<EOF>'} at end of input, positions 0-based on their line), and an
 * unterminated literal instead reads {@code parse error line L at position P near '<EOF>'.}
 * positioned at the END of the input. A trailing comma is tolerated in the select list and the
 * FROM list, and refused in GROUP BY, ORDER BY, function arguments, and INSERT column lists.
 */
public class SyntaxErrorShapeTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(SyntaxErrorShapeTest.class);

    /** Runs the statement and returns the refusal it throws. */
    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
    }

    @Test
    public void testKeywordTypoAtStart() {
        final RuntimeException exception = refusal("SELEC 1");

        // The error POINT differs between the parsers here (the embedded one keeps a bare
        // identifier viable as a statement start, so it fails one token later than live); the
        // refusal shape is what this test pins.
        assertTrue(exception.getMessage().startsWith(
                "SQL compilation error:\nsyntax error line 1 at position "),
            exception.getMessage());

        logger.info("Keyword typo refused with the live shape");
    }

    @Test
    public void testMissingFromItemPointParity() {
        final RuntimeException exception = refusal("SELECT * FROM WHERE x = 1");

        // The first line matches live's only line; the embedded parser's recovery may append
        // cascade errors live does not report.
        assertTrue(exception.getMessage().startsWith(
                "SQL compilation error:\nsyntax error line 1 at position 14 unexpected 'WHERE'."),
            exception.getMessage());

        logger.info("Missing FROM item flagged at live's exact point");
    }

    @Test
    public void testTokenEchoedVerbatimCase() {
        final RuntimeException exception = refusal("SELECT COALESCE(1, from)");

        assertTrue(exception.getMessage().contains(
                "syntax error line 1 at position 19 unexpected 'from'."),
            exception.getMessage());

        logger.info("Offending token echoed verbatim, case preserved");
    }

    @Test
    public void testMidStatementTypo() {
        final RuntimeException exception = refusal("SELECT * FORM users");

        // Same error-point caveat as testKeywordTypoAtStart: only the shape is pinned.
        assertTrue(exception.getMessage().startsWith(
                "SQL compilation error:\nsyntax error line 1 at position "),
            exception.getMessage());

        logger.info("Mid-statement typo refused with the live shape");
    }

    @Test
    public void testUnexpectedEndOfInput() {
        final RuntimeException exception = refusal("SELECT (1");

        assertEquals(
            "SQL compilation error:\nsyntax error line 1 at position 9 unexpected '<EOF>'.",
            exception.getMessage());

        logger.info("End of input refused byte-identically to live");
    }

    @Test
    public void testUnterminatedString() {
        final RuntimeException exception = refusal("SELECT 'abc");

        assertTrue(exception.getMessage().startsWith("SQL compilation error:\n"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(
                "parse error line 1 at position 11 near '<EOF>'."),
            exception.getMessage());

        logger.info("Unterminated string refused with the parse-error wording");
    }

    @Test
    public void testUnterminatedStringOnSecondLine() {
        final RuntimeException exception = refusal("SELECT\n'abc");

        assertTrue(exception.getMessage().contains(
                "parse error line 2 at position 4 near '<EOF>'."),
            exception.getMessage());

        logger.info("Parse-error position is the end of the input");
    }

    @Test
    public void testFirstOfMultipleErrorsMatches() {
        final RuntimeException exception = refusal("SELECT 1 1 FROM (SELECT 1) 2 2");

        // Both sides report every error on its own line; error RECOVERY differs, so only the
        // first line is pinned.
        assertTrue(exception.getMessage().contains(
                "syntax error line 1 at position 9 unexpected '1'."),
            exception.getMessage());

        logger.info("First error of a multi-error statement matches");
    }

    @Test
    public void testTrailingCommaInSelectList() {
        final ResultSet result = engine.executeQuery("SELECT 1, 2,");

        assertEquals(1, result.getRowCount());
        assertEquals(2, result.getColumns().size());

        logger.info("Trailing comma tolerated in the select list");
    }

    @Test
    public void testTrailingCommaBeforeFrom() {
        final ResultSet result = engine.executeQuery("SELECT 1, FROM (SELECT 1)");

        assertEquals(1, result.getRowCount());

        logger.info("Trailing comma tolerated directly before FROM");
    }

    @Test
    public void testStarWithTrailingCommaBeforeFrom() {
        final ResultSet result = engine.executeQuery("SELECT *, FROM (SELECT 1)");

        assertEquals(1, result.getRowCount());
        assertEquals(1, result.getColumns().size());

        logger.info("Star with trailing comma tolerated before FROM");
    }

    @Test
    public void testTrailingCommaInCteSubquery() {
        final ResultSet result = engine.executeQuery("WITH a AS (SELECT 1,) SELECT * FROM a");

        assertEquals(1, result.getRowCount());

        logger.info("Trailing comma tolerated before a subquery's closing paren");
    }

    @Test
    public void testTrailingCommaInFromList() {
        engine.execute("CREATE TABLE t1 (a INTEGER)");
        engine.execute("INSERT INTO t1 VALUES (7)");

        final ResultSet result = engine.executeQuery("SELECT * FROM t1,");

        assertEquals(1, result.getRowCount());

        logger.info("Trailing comma tolerated in the FROM list");
    }

    @Test
    public void testTrailingCommaRefusedInGroupBy() {
        final RuntimeException exception = refusal("SELECT 1 GROUP BY 1,");

        // Live consumes the comma and flags '<EOF>' at position 20; the embedded parser abandons
        // the list at the comma and flags it at 19. Both refuse in the live shape.
        assertTrue(exception.getMessage().startsWith(
                "SQL compilation error:\nsyntax error line 1 at position "),
            exception.getMessage());

        logger.info("Trailing comma refused in GROUP BY");
    }

    @Test
    public void testTrailingCommaRefusedInOrderBy() {
        final RuntimeException exception = refusal("SELECT 1 ORDER BY 1,");

        // Same error-point caveat as the GROUP BY refusal above.
        assertTrue(exception.getMessage().startsWith(
                "SQL compilation error:\nsyntax error line 1 at position "),
            exception.getMessage());

        logger.info("Trailing comma refused in ORDER BY");
    }

    @Test
    public void testGroupByWithoutFrom() {
        final ResultSet result = engine.executeQuery("SELECT 1 GROUP BY 1");

        assertEquals(1, result.getRowCount());

        logger.info("GROUP BY tolerated without FROM");
    }

    @Test
    public void testHavingWithoutFromFilters() {
        assertEquals(1, engine.executeQuery("SELECT 1 HAVING 1 = 1").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT 1 HAVING 1 = 0").getRowCount());

        logger.info("HAVING without FROM keeps or drops the row");
    }

    @Test
    public void testWhereWithoutFromFilters() {
        assertEquals(0, engine.executeQuery("SELECT 1 WHERE 1 = 0").getRowCount());

        logger.info("WHERE without FROM drops the row");
    }

    @Test
    public void testQualifyWithoutWindowFunctionRefusedWithoutFrom() {
        final RuntimeException exception = refusal("SELECT 1 QUALIFY 1 = 1");

        assertEquals(
            "SQL compilation error: error line 1 at position 9\n"
                + "found QUALIFY clause but no window function.",
            exception.getMessage());

        logger.info("Windowless QUALIFY refused without FROM");
    }

    @Test
    public void testQualifyWithoutWindowFunctionRefusedWithFrom() {
        engine.execute("CREATE TABLE qt (a INTEGER)");
        engine.execute("INSERT INTO qt VALUES (1), (2)");

        final RuntimeException exception = refusal("SELECT a FROM qt QUALIFY a > 1");

        assertEquals(
            "SQL compilation error: error line 1 at position 17\n"
                + "found QUALIFY clause but no window function.",
            exception.getMessage());

        logger.info("Windowless QUALIFY refused with FROM");
    }

    @Test
    public void testConnectByWithoutFromRefused() {
        final RuntimeException exception = refusal("SELECT 1 CONNECT BY 1 = 1");

        assertEquals("SQL compilation error: error line 1 at position 0 missing FROM clause",
            exception.getMessage());

        logger.info("CONNECT BY without FROM refused with the missing-FROM shape");
    }

    @Test
    public void testTrailingCommaRefusedInFunctionArguments() {
        final RuntimeException exception = refusal("SELECT COALESCE(1,)");

        assertTrue(exception.getMessage().contains(
                "syntax error line 1 at position 18 unexpected ')'."),
            exception.getMessage());

        logger.info("Trailing comma refused in function arguments");
    }

    @Test
    public void testTrailingCommaRefusedInInsertColumnList() {
        final RuntimeException exception = refusal("INSERT INTO t1 (a,) VALUES (1)");

        assertTrue(exception.getMessage().contains(
                "syntax error line 1 at position 18 unexpected ')'."),
            exception.getMessage());

        logger.info("Trailing comma refused in an INSERT column list");
    }

    /**
     * A statement that RUNS OUT of input names {@code '<EOF>'}, positioned at the very end of the
     * text — never at the clause keyword that started the unfinished part. The rule holds whether
     * the input ends after a clause keyword, mid-expression, or inside an open parenthesis.
     */
    @Test
    public void testUnfinishedStatementNamesEndOfInput() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT, b INT)");

        assertEquals("SQL compilation error:\nsyntax error line 1 at position 25 unexpected '<EOF>'.",
            refusal("SELECT a FROM kw GROUP BY").getMessage());
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 25 unexpected '<EOF>'.",
            refusal("SELECT a FROM kw ORDER BY").getMessage());
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected '<EOF>'.",
            refusal("SELECT a FROM kw WHERE").getMessage());
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected '<EOF>'.",
            refusal("SELECT a FROM kw ORDER").getMessage());
        // An unclosed call is the one measured shape where live stacks a SECOND line, pointing
        // BACKWARDS at the '(' it never closed; Frostlake reports the first line only, so this cell
        // pins the shared opening rather than the whole message.
        assertTrue(refusal("SELECT MAX(").getMessage().startsWith(
            "SQL compilation error:\nsyntax error line 1 at position 11 unexpected '<EOF>'."),
            refusal("SELECT MAX(").getMessage());

        logger.info("An unfinished statement names <EOF> at the end of the input");
    }

    /**
     * Trailing JUNK after a statement that was already complete names the junk token instead. The
     * pair is what makes the rule: {@code RENAME} and {@code zz} read as aliases, so the statement
     * ended there and the word after them is the first thing nothing can consume.
     */
    @Test
    public void testTrailingJunkNamesTheJunkToken() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT, b INT)");

        assertEquals("SQL compilation error:\nsyntax error line 1 at position 38 unexpected 'FIELDS'.",
            refusal("SELECT '{}'::OBJECT(x VARCHAR) RENAME FIELDS").getMessage());
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 24 unexpected 'FIELDS'.",
            refusal("SELECT a FROM kw RENAME FIELDS").getMessage());
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 20 unexpected 'yy'.",
            refusal("SELECT a FROM kw zz yy").getMessage());

        logger.info("Trailing junk names the junk token");
    }

    /**
     * A construct-leading keyword written where a column was meant is refused ONCE, at the token the
     * construct could not swallow — not at the keyword the parser first tried and then read another
     * way. {@code CASE} is consumed as a case expression in all three, so the error moves with what
     * follows it.
     */
    @Test
    public void testConstructKeywordIsReportedOnceAtItsDeadEnd() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT, b INT)");

        assertEquals("SQL compilation error:\nsyntax error line 1 at position 11 unexpected '<EOF>'.",
            refusal("SELECT case").getMessage());
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 12 unexpected 'FROM'.",
            refusal("SELECT case FROM kw").getMessage());
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 17 unexpected 'FROM'.",
            refusal("SELECT case cast FROM kw").getMessage());

        logger.info("A construct-leading keyword is reported once, at its dead end");
    }

    /** And the words that ARE consumable stay accepted — an alias, a non-reserved keyword, a comma. */
    @Test
    public void testConsumableTrailingWordsStayAccepted() {
        engine.execute("CREATE OR REPLACE TABLE kw (a INT, b INT)");

        assertEquals(0, engine.executeQuery("SELECT a FROM kw fields").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT a FROM kw LIMIT").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT 1,").getRowCount());

        logger.info("Consumable trailing words stay accepted");
    }
}
