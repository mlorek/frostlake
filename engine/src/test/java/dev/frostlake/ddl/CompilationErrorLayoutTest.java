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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a compilation error's NEWLINES fall. Every assertion here compares the message with its line
 * breaks rendered visibly and NOT normalised away — which is the whole point of the file, because
 * essentially every other two-sided test collapses newlines to spaces before comparing and so cannot
 * see any of this.
 *
 * <p>THE ORDINARY LAYOUT IS THE COMMON ONE, and Frostlake had it right all along: the prefix, a
 * newline, then the detail. Twenty-one un-positioned refusals from as many different families were
 * measured in one live run — a missing relation, an unknown function, a duplicate property, an invalid
 * parameter value, an aggregate in WHERE, a set-operation refusal, a syntax error — and every one of
 * them reads that way. A POSITIONED error keeps its position clause on the first line and was already
 * right too.
 *
 * <pre>
 *   SELECT nosuchcol FROM rt   SQL compilation error: error line 1 at position 7\ninvalid identifier …
 *   SELECT i FROM nosuchtable  SQL compilation error:\nObject 'NOSUCHTABLE' does not exist …
 * </pre>
 *
 * <p>ONE SENTENCE LAYS ITSELF OUT DIFFERENTLY, and it is the reason this file exists:
 *
 * <pre>
 *   ALTER TABLE rt ALTER COLUMN f SET DATA TYPE NUMBER(10,2)
 *       SQL compilation error: cannot change column F from type FLOAT to NUMBER(10,2)\n
 * </pre>
 *
 * <p>A SPACE after the colon, and a newline CLOSING the message rather than separating it. That is a
 * property of the sentence and not of the statement: three other refusals raised by ALTER TABLE itself
 * — a column that already exists, a column that does not, a rename onto a taken name — use the
 * ordinary layout. All eight retype directions use this one.
 *
 * <p>This is invisible to a normalising test and perfectly visible to any client reading
 * {@code getMessage()} — a console, a driver, a log.
 */
public class CompilationErrorLayoutTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (i INT, f FLOAT, s VARCHAR(9), n NUMBER(10,2),"
            + " d DATE, b BOOLEAN)");
        // One row, because the RUNTIME failures below only fire once a row reaches them — over an
        // empty table a division by zero is simply never performed.
        engine.execute("INSERT INTO rt SELECT 1, 1.5, 'x', 1.00, '2020-01-01', TRUE");
    }

    /** The refusal with every newline rendered VISIBLY, so no comparison can normalise it away. */
    private String layoutOf(final String sql) {
        try {
            engine.execute(sql);
            return "<accepted>";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\r", "\\r").replace("\n", "\\n");
        }
    }

    /** The RETYPE sentence: a space after the colon, and a newline closing the message. */
    @Test
    public void theRetypeSentenceClosesWithItsNewline() {
        assertEquals("SQL compilation error: cannot change column F from type FLOAT"
            + " to NUMBER(10,2)\\n",
            layoutOf("ALTER TABLE rt ALTER COLUMN f SET DATA TYPE NUMBER(10,2)"));
        assertEquals("SQL compilation error: cannot change column N from type NUMBER(10,2)"
            + " to FLOAT\\n",
            layoutOf("ALTER TABLE rt ALTER COLUMN n SET DATA TYPE FLOAT"));
        assertEquals("SQL compilation error: cannot change column I from type NUMBER(38,0)"
            + " to VARCHAR(20)\\n",
            layoutOf("ALTER TABLE rt ALTER COLUMN i SET DATA TYPE VARCHAR(20)"));
        assertEquals("SQL compilation error: cannot change column D from type DATE"
            + " to VARCHAR(20)\\n",
            layoutOf("ALTER TABLE rt ALTER COLUMN d SET DATA TYPE VARCHAR(20)"));
        assertEquals("SQL compilation error: cannot change column S from type VARCHAR(9)"
            + " to VARCHAR(3) because reducing the byte-length of a varchar is not supported.\\n",
            layoutOf("ALTER TABLE rt ALTER COLUMN s SET DATA TYPE VARCHAR(3)"),
            "the reason clause sits INSIDE the sentence, before the closing newline");
        assertEquals("SQL compilation error: cannot change column F from type FLOAT"
            + " to NUMBER(10,2)\\n",
            layoutOf("ALTER TABLE rt ALTER COLUMN f TYPE NUMBER(10,2)"),
            "the short spelling of the same statement");
    }

    /**
     * The scale clause is for two EXACT numbers only. A FLOAT on either side is refused with no reason
     * at all, though its scale differs too — the approximate family's scale is a placeholder rather
     * than a decision, and live does not talk about it.
     */
    @Test
    public void theScaleClauseIsForExactNumbersOnly() {
        engine.execute("ALTER TABLE rt ALTER COLUMN n SET DATA TYPE NUMBER(5,2)");
        assertEquals("SQL compilation error: cannot change column N from type NUMBER(5,2)"
            + " to NUMBER(10,4) because changing the scale of a number is not supported.\\n",
            layoutOf("ALTER TABLE rt ALTER COLUMN n SET DATA TYPE NUMBER(10,4)"));
        assertEquals("SQL compilation error: cannot change column F from type FLOAT"
            + " to NUMBER(10,2)\\n",
            layoutOf("ALTER TABLE rt ALTER COLUMN f SET DATA TYPE NUMBER(10,2)"),
            "a FLOAT beside a NUMBER gets no reason, though the scales differ");
    }

    /** Its own statement's OTHER refusals use the ordinary layout, which is what makes it the sentence's. */
    @Test
    public void theSameStatementsOtherRefusalsAreOrdinary() {
        assertEquals("SQL compilation error:\\ncolumn 'I' already exists",
            layoutOf("ALTER TABLE rt ADD COLUMN i INT"));
        assertEquals("SQL compilation error:\\ncolumn 'NOSUCHCOL' does not exist",
            layoutOf("ALTER TABLE rt DROP COLUMN nosuchcol"));
        assertEquals("SQL compilation error:\\nObject 'RT.F' already exists.",
            layoutOf("ALTER TABLE rt RENAME COLUMN i TO f"));
        assertEquals("SQL compilation error:\\nTable 'TEST_DB.TEST_SCHEMA.NOSUCHTABLE'"
            + " does not exist or not authorized.",
            layoutOf("ALTER TABLE nosuchtable ADD COLUMN x INT"));
    }

    /** And so does every other family — the layout the common builder produces. */
    @Test
    public void theOrdinaryLayoutIsTheCommonOne() {
        assertEquals("SQL compilation error:\\nObject 'NOSUCHTABLE' does not exist or not authorized.",
            layoutOf("SELECT i FROM nosuchtable"));
        assertEquals("SQL compilation error:\\nUnknown function NOSUCHFN.",
            layoutOf("SELECT nosuchfn(i) FROM rt"));
        assertEquals("SQL compilation error:\\nInvalid aggregate function in where clause [SUM(RT.I)]",
            layoutOf("SELECT i FROM rt WHERE SUM(i) > 1"));
        assertEquals("SQL compilation error:\\n[RT.F] is not a valid order by expression",
            layoutOf("SELECT i FROM rt GROUP BY i ORDER BY f"));
        assertEquals("SQL compilation error:\\nduplicate property 'COMMENT';",
            layoutOf("CREATE OR REPLACE TABLE rt2 (i INT) COMMENT = 'a' COMMENT = 'b'"));
        assertEquals("SQL compilation error:\\ninvalid value [Nowhere/Nothing] for parameter 'TIMEZONE'",
            layoutOf("ALTER SESSION SET TIMEZONE = 'Nowhere/Nothing'"));
        assertEquals("SQL compilation error:\\nsyntax error line 1 at position 22 unexpected '<EOF>'.",
            layoutOf("SELECT i FROM rt WHERE"),
            "a SYNTAX error spells its position inside the detail, on the second line");
    }

    /** A POSITIONED error keeps its position on the first line, and closes without a newline. */
    @Test
    public void aPositionedErrorKeepsItsPositionOnTheFirstLine() {
        assertEquals("SQL compilation error: error line 1 at position 7\\ninvalid identifier 'NOSUCHCOL'",
            layoutOf("SELECT nosuchcol FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 10"
            + "\\n'RT.F' in select clause is neither an aggregate nor in the group by clause.",
            layoutOf("SELECT i, f FROM rt GROUP BY i"));
        assertEquals("SQL compilation error: error line 1 at position 28\\ninvalid identifier 'NOSUCHCOL'",
            layoutOf("ALTER TABLE rt ALTER COLUMN nosuchcol SET DATA TYPE INT"),
            "even inside the statement whose retype sentence lays itself out the other way");
    }

    /** A RUNTIME failure carries no prefix and no newline of its own. */
    @Test
    public void aRuntimeFailureCarriesNoPrefix() {
        assertEquals("Division by zero", layoutOf("SELECT i / 0 FROM rt"));
        assertEquals("Numeric value 'abc' is not recognized", layoutOf("SELECT 'abc'::INT FROM rt"));
        assertEquals("Date 'zz' is not recognized", layoutOf("SELECT 'zz'::DATE FROM rt"));
    }
}
