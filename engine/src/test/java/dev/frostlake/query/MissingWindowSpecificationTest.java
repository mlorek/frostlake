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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A WINDOW-ONLY function written with no OVER clause. Live names the missing specification; Frostlake
 * used to call the NAME unknown, which is false — the name is perfectly well known, it is the
 * specification that is absent. The registry carries no callable scalar for these, so the lookup
 * missed and the unknown-name sentence won.
 *
 * <p>★ THE EXCLUSIONS ARE THE DANGEROUS HALF, because getting them wrong refuses working SQL. SUM,
 * COUNT, AVG, MIN and MAX are aggregates as well and answer without an OVER; PERCENTILE_CONT and
 * PERCENTILE_DISC take WITHIN GROUP instead of OVER. Live accepts all seven, and they are asserted
 * here so a future widening of the name set cannot break them silently.
 *
 * <p>★ THE ECHO IS THE PLAN FORM, not the source text: the argument is QUALIFIED against its relation
 * and upper-cased, an alias is kept ({@code LEAD(T.A)}), an expression argument is printed whole
 * ({@code LEAD(GW.A + 1)}), and a literal argument stays bare ({@code NTILE(2)}).
 *
 * <p>★ IT IS RAISED WHERE THE UNKNOWN-NAME SENTENCE WAS, so everything that already outranked that
 * sentence still speaks first — in particular a bad ARGUMENT name, which both engines report as the
 * invalid identifier rather than the missing specification.
 */
public class MissingWindowSpecificationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT)");
        engine.execute("INSERT INTO gw VALUES (1, 10), (2, 20), (3, 30)");
    }

    private String outcome(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("accepted:");
            while (rs.next()) {
                all.append(String.valueOf(rs.getValue(0))).append(";");
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The refusal for a call echoed as {@code echo}. */
    private static String missing(final String echo) {
        return "SQL compilation error:|Missing window specification for function [" + echo + "].";
    }

    /** The ranking family, which takes no arguments at all. */
    @Test
    public void theNoArgumentRankingFunctionsNameTheMissingSpecification() {
        assertEquals(missing("ROW_NUMBER()"), outcome("SELECT ROW_NUMBER() FROM gw"));
        assertEquals(missing("RANK()"), outcome("SELECT RANK() FROM gw"));
        assertEquals(missing("DENSE_RANK()"), outcome("SELECT DENSE_RANK() FROM gw"));
        assertEquals(missing("PERCENT_RANK()"), outcome("SELECT PERCENT_RANK() FROM gw"));
        assertEquals(missing("CUME_DIST()"), outcome("SELECT CUME_DIST() FROM gw"));
    }

    /** And the ones that take arguments, whose echo qualifies them. */
    @Test
    public void theArgumentTakingWindowFunctionsDoTheSame() {
        assertEquals(missing("LAG(GW.A)"), outcome("SELECT LAG(a) FROM gw"));
        assertEquals(missing("LEAD(GW.A)"), outcome("SELECT LEAD(a) FROM gw"));
        assertEquals(missing("FIRST_VALUE(GW.A)"), outcome("SELECT FIRST_VALUE(a) FROM gw"));
        assertEquals(missing("LAST_VALUE(GW.A)"), outcome("SELECT LAST_VALUE(a) FROM gw"));
        assertEquals(missing("NTH_VALUE(GW.A, 2)"), outcome("SELECT NTH_VALUE(a, 2) FROM gw"));
        assertEquals(missing("NTILE(2)"), outcome("SELECT NTILE(2) FROM gw"));
        assertEquals(missing("RATIO_TO_REPORT(GW.A)"), outcome("SELECT RATIO_TO_REPORT(a) FROM gw"));
        assertEquals(missing("CONDITIONAL_CHANGE_EVENT(GW.A)"),
            outcome("SELECT CONDITIONAL_CHANGE_EVENT(a) FROM gw"));
    }

    /** ★ The seven names that are NOT window-only and must keep answering. */
    @Test
    public void theDualRoleAggregatesAndThePercentilesAreUntouched() {
        assertEquals("accepted:6;", outcome("SELECT SUM(a) FROM gw"));
        assertEquals("accepted:3;", outcome("SELECT COUNT(a) FROM gw"));
        assertEquals("accepted:2.000000;", outcome("SELECT AVG(a) FROM gw"));
        assertEquals("accepted:1;", outcome("SELECT MIN(a) FROM gw"));
        assertEquals("accepted:3;", outcome("SELECT MAX(a) FROM gw"));
        assertEquals("accepted:2.000;",
            outcome("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY a) FROM gw"));
        assertEquals("accepted:2;",
            outcome("SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY a) FROM gw"));
    }

    /** The echo is the resolved plan, whichever way the argument was written. */
    @Test
    public void theEchoIsThePlanForm() {
        assertEquals(missing("LEAD(GW.A)"), outcome("SELECT LEAD(gw.a) FROM gw"));
        assertEquals(missing("LEAD(GW.A + 1)"), outcome("SELECT LEAD(a + 1) FROM gw"));
        assertEquals(missing("LEAD(T.A)"), outcome("SELECT LEAD(t.a) FROM gw t"));
    }

    /** It fires from every clause the call can sit in, and from inside another call. */
    @Test
    public void everyClauseReportsIt() {
        assertEquals(missing("ROW_NUMBER()"), outcome("SELECT a FROM gw WHERE ROW_NUMBER() = 1"));
        assertEquals(missing("ROW_NUMBER()"), outcome("SELECT a FROM gw ORDER BY ROW_NUMBER()"));
        assertEquals(missing("ROW_NUMBER()"),
            outcome("SELECT a FROM gw GROUP BY a HAVING ROW_NUMBER() = 1"));
        assertEquals(missing("RATIO_TO_REPORT(GW.A)"), outcome("SELECT ABS(RATIO_TO_REPORT(a)) FROM gw"));
    }

    /** A bad ARGUMENT name outranks it, and a real window call is untouched. */
    @Test
    public void aBadArgumentNameStillSpeaksFirstAndRealWindowCallsRun() {
        assertEquals("SQL compilation error: error line 1 at position 12|invalid identifier 'NOSUCHCOL'",
            outcome("SELECT LEAD(nosuchcol) FROM gw"));
        assertEquals("SQL compilation error: error line 1 at position 12|invalid identifier 'NOSUCHCOL'",
            outcome("SELECT LEAD(nosuchcol), ROW_NUMBER() FROM gw"));
        assertEquals("accepted:1;2;3;", outcome("SELECT ROW_NUMBER() OVER (ORDER BY a) FROM gw"));
        assertEquals("accepted:1;3;6;", outcome("SELECT SUM(a) OVER (ORDER BY a) FROM gw"));
    }

    /**
     * ★ THE PREDICATE-TAKING FUNCTIONS SHOW THEIR CONVERSION. Live's plan carries the argument of
     * CONDITIONAL_TRUE_EVENT and CONDITIONAL_CHANGE_EVENT already cast to a boolean, and the echo
     * prints that cast.
     *
     * <p>★ WHAT ESCAPES THE CAST IS NOT "already a boolean" — a comparison and an IS NULL are booleans
     * too, and both are cast. It is the LOGICAL vocabulary that escapes: a BOOLEAN column however it is
     * written, a boolean literal, and NOT / AND / OR.
     *
     * <p>NOT PINNED HERE: a comparison against a literal of a different SCALE, where live converts the
     * literal as well ({@code CAST(1 AS NUMBER(10,2))}) — that is the arithmetic-operand rule, which
     * belongs to a surface of its own; and NOT's own bracketing, which live spells {@code NOT(X)}.
     */
    @Test
    public void thepredicateFunctionsEchoTheirBooleanConversion() {
        engine.execute("CREATE OR REPLACE TABLE ce (t VARCHAR(10), b BOOLEAN, i NUMBER(5,0))");
        engine.execute("INSERT INTO ce VALUES ('1', TRUE, 1)");
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST(CE.T AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(t) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST(CE.I > 1 AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(i > 1) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST(CE.B IS NULL AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(b IS NULL) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST(STARTSWITH(CE.T, '1') AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(STARTSWITH(t, '1')) FROM ce"));
        // ★ THE TWO FUNCTIONS DIFFER on a non-boolean argument: CONDITIONAL_TRUE_EVENT counts a
        // PREDICATE and converts, CONDITIONAL_CHANGE_EVENT counts a VALUE and takes any type.
        assertEquals(missing("CONDITIONAL_CHANGE_EVENT(CE.T)"),
            outcome("SELECT CONDITIONAL_CHANGE_EVENT(t) FROM ce"));
        assertEquals(missing("CONDITIONAL_CHANGE_EVENT(UPPER(CE.T))"),
            outcome("SELECT CONDITIONAL_CHANGE_EVENT(UPPER(t)) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST(UPPER(CE.T) AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(UPPER(t)) FROM ce"));
        // A predicate is spelled out by BOTH, even though it is a boolean already.
        assertEquals(missing("CONDITIONAL_CHANGE_EVENT(CAST(CE.I > 1 AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_CHANGE_EVENT(i > 1) FROM ce"));
        assertEquals(missing("CONDITIONAL_CHANGE_EVENT(CAST(CE.B IS NULL AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_CHANGE_EVENT(b IS NULL) FROM ce"));
        assertEquals(missing("CONDITIONAL_CHANGE_EVENT(CAST(STARTSWITH(CE.T, '1') AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_CHANGE_EVENT(STARTSWITH(t, '1')) FROM ce"));
    }

    /** The logical vocabulary, which live leaves uncast however it is spelled. */
    @Test
    public void alogicalArgumentIsLeftUncast() {
        engine.execute("CREATE OR REPLACE TABLE ce (t VARCHAR(10), b BOOLEAN, i NUMBER(5,0))");
        engine.execute("INSERT INTO ce VALUES ('1', TRUE, 1)");
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CE.B)"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(b) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CE.B)"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT((b)) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CE.B)"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(ce.b) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CE.B AND CE.B)"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(b AND b) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CE.B OR FALSE)"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(b OR FALSE) FROM ce"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(TRUE)"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(TRUE) FROM ce"));
    }

    /**
     * ★ NOT IS A CALL in the echo — {@code NOT(CE.B)}, no space — and what decides the BOOLEAN cast is
     * whether a PREDICATE is spelled out anywhere under the NOT / AND / OR: a bare boolean, or a NOT or
     * AND of bare booleans, prints uncast; a comparison, an IS NULL, or a NOT or AND holding one is
     * cast as a whole. Live-verified.
     */
    @Test
    public void notIsACallAndAPredicateUnderItIsCast() {
        engine.execute("CREATE OR REPLACE TABLE cn (n102 NUMBER(10,2), i NUMBER(5,0), b BOOLEAN)");
        engine.execute("INSERT INTO cn VALUES (1.5, 1, TRUE)");
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(NOT(CN.B))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(NOT b) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(NOT(NOT(CN.B)))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(NOT NOT b) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT((NOT(CN.B)) AND CN.B)"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(NOT b AND b) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CN.B OR (NOT(CN.B)))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(b OR NOT b) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(NOT(CN.B AND CN.B))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(NOT (b AND b)) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT((CN.B AND CN.B) OR CN.B)"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(b AND b OR b) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST(NOT(CN.N102 > (CAST(1 AS NUMBER(10,2)))) AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(NOT (n102 > 1)) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST(NOT(CN.B IS NULL) AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(NOT b IS NULL) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST((CN.N102 > (CAST(1 AS NUMBER(10,2)))) AND (CN.I < 2) AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(n102 > 1 AND i < 2) FROM cn"));
        assertEquals(missing("CONDITIONAL_TRUE_EVENT(CAST((NEGATE(CN.N102)) > (CAST(1 AS NUMBER(10,2))) AS BOOLEAN))"),
            outcome("SELECT CONDITIONAL_TRUE_EVENT(-n102 > 1) FROM cn"));
    }
}
