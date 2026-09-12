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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How many arguments a window function takes, and what QUALIFY says about one written with no OVER.
 *
 * <p>★ ARITY OUTRANKS THE MISSING SPECIFICATION. {@code LEAD()} is refused for its argument count
 * whether or not an OVER follows it — the count is judged first, and the refusal is POSITIONED on the
 * call where the missing-specification sentence carries no position at all.
 *
 * <p>★ THE ECHO DROPS THE WINDOW. Live prints {@code RANK(GW.A)} for a mis-counted
 * {@code RANK(a) OVER (ORDER BY a)}: the arguments are the complaint, so the specification they were
 * to be computed over is left out, and the argument is printed as the PLAN holds it — qualified and
 * upper-cased.
 *
 * <p>★ THE TWO SENTENCES ARE PUNCTUATED DIFFERENTLY, which is measured rather than tidied: a comma
 * follows the bracket in the too-few one and not in the too-many one.
 *
 * <p>★ QUALIFY SPEAKS FOR ITSELF. A call with no OVER is not a window function as far as the clause is
 * concerned, so {@code QUALIFY ROW_NUMBER() = 1} is the no-window refusal — where the same bare call
 * in the select list, in HAVING or in ORDER BY earns the missing-specification one instead.
 *
 * <p>NOT COVERED HERE: RATIO_TO_REPORT, whose arity refusal live words in SUM's name, and the implicit
 * CAST live prints inside an echoed boolean argument — both belong to surfaces of their own.
 */
public class WindowFunctionArityTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE gw (a NUMBER(10,2), b VARCHAR(10))");
        engine.execute("INSERT INTO gw VALUES (1.00, 'x'), (2.00, 'y')");
    }

    /** One statement's refusal, with its line break shown as a bar. */
    private String refusal(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return "ACCEPTED " + String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String tooFew(final String echo, final int expected, final int got) {
        return "SQL compilation error: error line 1 at position 7|not enough arguments for function ["
            + echo + "], expected " + expected + ", got " + got;
    }

    private String tooMany(final String echo, final int expected, final int got) {
        return "SQL compilation error: error line 1 at position 7|too many arguments for function ["
            + echo + "] expected " + expected + ", got " + got;
    }

    @Test
    void aCallWithTooFewArgumentsIsRefusedAtItsOwnPosition() {
        assertEquals(tooFew("LEAD()", 1, 0), refusal("SELECT LEAD() FROM gw"));
        assertEquals(tooFew("LAG()", 1, 0), refusal("SELECT LAG() FROM gw"));
        assertEquals(tooFew("NTILE()", 1, 0), refusal("SELECT NTILE() FROM gw"));
        assertEquals(tooFew("FIRST_VALUE()", 1, 0), refusal("SELECT FIRST_VALUE() FROM gw"));
        assertEquals(tooFew("NTH_VALUE(GW.A)", 2, 1), refusal("SELECT NTH_VALUE(a) FROM gw"));
    }

    @Test
    void aCallWithTooManyIsTheOtherSentence() {
        assertEquals(tooMany("ROW_NUMBER(GW.A)", 0, 1), refusal("SELECT ROW_NUMBER(a) FROM gw"));
        assertEquals(tooMany("LEAD(GW.A, 1, 2, 3)", 3, 4), refusal("SELECT LEAD(a, 1, 2, 3) FROM gw"));
    }

    @Test
    void theCountIsJudgedWithAWindowSpecificationToo() {
        assertEquals(tooFew("LEAD()", 1, 0), refusal("SELECT LEAD() OVER (ORDER BY a) FROM gw"));
        assertEquals(tooFew("LAST_VALUE()", 1, 0),
            refusal("SELECT LAST_VALUE() OVER (ORDER BY a) FROM gw"));
        assertEquals(tooFew("CONDITIONAL_TRUE_EVENT()", 1, 0),
            refusal("SELECT CONDITIONAL_TRUE_EVENT() OVER (ORDER BY a) FROM gw"));
        // …and the echo drops the specification it was going to be computed over.
        assertEquals(tooMany("RANK(GW.A)", 0, 1), refusal("SELECT RANK(a) OVER (ORDER BY a) FROM gw"));
        assertEquals(tooMany("DENSE_RANK(GW.A)", 0, 1),
            refusal("SELECT DENSE_RANK(a) OVER (ORDER BY a) FROM gw"));
        assertEquals(tooMany("PERCENT_RANK(GW.A)", 0, 1),
            refusal("SELECT PERCENT_RANK(a) OVER (ORDER BY a) FROM gw"));
        assertEquals(tooMany("CUME_DIST(GW.A)", 0, 1),
            refusal("SELECT CUME_DIST(a) OVER (ORDER BY a) FROM gw"));
        assertEquals(tooMany("NTILE(2, 3)", 1, 2),
            refusal("SELECT NTILE(2, 3) OVER (ORDER BY a) FROM gw"));
        assertEquals(tooMany("FIRST_VALUE(GW.A, 1)", 1, 2),
            refusal("SELECT FIRST_VALUE(a, 1) OVER (ORDER BY a) FROM gw"));
        assertEquals(tooMany("NTH_VALUE(GW.A, 1, 2)", 2, 3),
            refusal("SELECT NTH_VALUE(a, 1, 2) OVER (ORDER BY a) FROM gw"));
    }

    @Test
    void aCountWithinTheBoundsIsUntouched() {
        assertEquals("ACCEPTED 1", refusal("SELECT ROW_NUMBER() OVER (ORDER BY a) FROM gw"));
        assertEquals("ACCEPTED 2.00", refusal("SELECT LEAD(a) OVER (ORDER BY a) FROM gw"));
        assertEquals("ACCEPTED 1.00", refusal("SELECT NTH_VALUE(a, 1) OVER (ORDER BY a) FROM gw"));
        assertEquals("ACCEPTED 1", refusal("SELECT NTILE(2) OVER (ORDER BY a) FROM gw"));
    }

    @Test
    void qualifyAnswersForItselfWhereTheOtherClausesDoNot() {
        assertEquals("SQL compilation error: error line 1 at position 17|"
                + "found QUALIFY clause but no window function.",
            refusal("SELECT a FROM gw QUALIFY ROW_NUMBER() = 1"));
        assertEquals("SQL compilation error: error line 1 at position 28|"
                + "found QUALIFY clause but no window function.",
            refusal("SELECT a FROM gw GROUP BY a QUALIFY ROW_NUMBER() = 1"));
        assertEquals("SQL compilation error: error line 1 at position 17|"
                + "found QUALIFY clause but no window function.",
            refusal("SELECT a FROM gw QUALIFY a = 1"));
        assertEquals("ACCEPTED 1.00",
            refusal("SELECT a FROM gw QUALIFY ROW_NUMBER() OVER (ORDER BY a) = 1"));
    }

    @Test
    void everyOtherClauseKeepsTheMissingSpecification() {
        final String missing = "SQL compilation error:|"
            + "Missing window specification for function [ROW_NUMBER()].";
        assertEquals(missing, refusal("SELECT ROW_NUMBER() FROM gw"));
        assertEquals(missing, refusal("SELECT a, ROW_NUMBER() FROM gw GROUP BY a"));
        assertEquals(missing, refusal("SELECT a FROM gw GROUP BY a HAVING ROW_NUMBER() > 0"));
        assertEquals(missing, refusal("SELECT a FROM gw ORDER BY ROW_NUMBER()"));
    }

    @Test
    void aNameNothingResolvesIsStillUnknown() {
        assertEquals("SQL compilation error:|Unknown function NO_SUCH_FN.",
            refusal("SELECT NO_SUCH_FN(a) FROM gw"));
        assertEquals("SQL compilation error:|Unknown function NO_SUCH_FN.",
            refusal("SELECT a FROM gw QUALIFY NO_SUCH_FN(a) = 1"));
    }
}
