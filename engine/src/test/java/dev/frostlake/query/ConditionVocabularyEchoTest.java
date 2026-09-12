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
 * How a refusal names IFF, an arithmetic rescale and the other calls it re-prints from the plan. IFF's
 * condition is a row-index boolean — {@code CAST(RT.B AS BOOLEAN)}, {@code BOOLEAN_TO_ROWINDEX(RT.B)} in
 * an invalid-type sentence — unless it is a predicate, and its branches meet in one type. An arithmetic
 * operand's rescale keeps two integer digits; a star call is a bare operand; a string is re-printed with
 * its quotes escaped; an invalid-type sentence spells LEFT, RIGHT and a path as the plan does and a list
 * as a chain of memberships. Each expected answer is the account's own.
 */
public class ConditionVocabularyEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE rt (g VARCHAR(10), n NUMBER(5,0), v VARIANT, b BOOLEAN)");
        engine.execute("CREATE TABLE fam (g VARCHAR(10), n NUMBER(5,0), v VARIANT)");
        engine.execute("CREATE TABLE dig (n1 NUMBER(1,0), n2 NUMBER(2,0), d1 NUMBER(2,1))");
    }

    /** The answer as one line: the rows a query returns, or its refusal with each line break as |. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** In the plan IFF's condition is cast to BOOLEAN unless it is a predicate. */
    @Test
    public void iffCastsAConditionThatIsNoPredicate() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(RT.B AS BOOLEAN), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(TRUE AS BOOLEAN), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(TRUE, 1, 2), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(null AS BOOLEAN), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(NULL, 1, 2), 1)"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(NOT(RT.B) AS BOOLEAN), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(NOT b, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(RT.B AND RT.B AS BOOLEAN), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b AND b, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(RT.B OR RT.B AS BOOLEAN), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b OR b, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(RT.N > 1, 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(n > 1, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(RT.B IS NULL, 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b IS NULL, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(RT.B IS NOT NULL, 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b IS NOT NULL, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(RT.G LIKE 'a%', 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(g LIKE 'a%', 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(RT.G IN ('a'), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(g IN ('a'), 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF((RT.N >= 1) AND (RT.N <= 2), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(n BETWEEN 1 AND 2, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(NOT(RT.N > 1), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(NOT n > 1, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF((RT.N > 1) AND (RT.N < 5), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(n > 1 AND n < 5, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF((RT.N > 1) AND (CAST(RT.B AS BOOLEAN)), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(n > 1 AND b, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF((RT.N > 1) AND (RT.N < 5) AND (CAST(RT.B AS BOOLEAN)), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(n > 1 AND n < 5 AND b, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF((CAST(RT.B AS BOOLEAN)) AND (CAST(RT.B AS BOOLEAN)) AND (RT.N > 1), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b AND b AND n > 1, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF((CAST(RT.B AS BOOLEAN)) AND (RT.N > 1) AND (CAST(RT.B AS BOOLEAN)), 1, 2), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b AND n > 1 AND b, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER((IFF(CAST(RT.B AS BOOLEAN), 1, 2)) + 1, 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b, 1, 2) + 1, 1) FROM rt"));
        assertEquals("SQL compilation error:|Can not convert parameter 'IFF(CAST(RT.B AS BOOLEAN), 1, 2)' of type [NUMBER(1,0)] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN IFF(b, 1, 2) THEN 1 END FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for function 'IFF': (VARCHAR(10), NUMBER(1,0), NUMBER(1,0))",
            answer("SELECT IFF(g, 1, 2) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 13|Invalid argument types for function 'IFF': (VARCHAR(10), NUMBER(1,0), NUMBER(1,0))",
            answer("SELECT UPPER(IFF(g, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 13|Invalid argument types for function 'IFF': (VARIANT, NUMBER(1,0), NUMBER(1,0))",
            answer("SELECT UPPER(IFF(v, 1, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 13|Invalid argument types for function 'IFF': (NUMBER(5,0), NUMBER(1,0), NUMBER(1,0))",
            answer("SELECT UPPER(IFF(n, 1, 2), 1) FROM rt"));
    }

    /** IFF's branches meet in one type: the lower scale rescaled, an untyped NULL typed. */
    @Test
    public void iffsBranchesMeetInOneType() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(RT.B AS BOOLEAN), 1.5, CAST(2 AS NUMBER(2,1))), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b, 1.5, 2), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(RT.B AS BOOLEAN), 'a', SYSTEM$NULL_TO_TEXT(null)), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b, 'a', NULL), 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(IFF(CAST(RT.B AS BOOLEAN), SYSTEM$NULL_TO_FIXED(null), 1), 1)] expected 1, got 2",
            answer("SELECT UPPER(IFF(b, NULL, 1), 1) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B), 1.5, FIXED_TO_FIXED(2 AS NUMBER(2,1)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, 1.5, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B), FIXED_TO_FIXED(2 AS NUMBER(2,1)[UNKNOWN]), 1.5) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, 2, 1.5) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B), 1.5, FIXED_TO_FIXED(RT.N AS NUMBER(6,1)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, 1.5, n) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B), FIXED_TO_FIXED(22 AS NUMBER(3,1)[UNKNOWN]), 1.5) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, 22, 1.5) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B), RT.N, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, n, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B), 'a', 'bc') IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, 'a', 'bc') IS NULL AS DATE) FROM rt"));
    }

    /** In an invalid-type sentence a condition that is no predicate is BOOLEAN_TO_ROWINDEX. */
    @Test
    public void anInvalidTypeSentenceReadsTheConditionAsARowIndex() {
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(TRUE), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(TRUE, 1, 2) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(NULL), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(NULL, 1, 2) AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE(IFF(BOOLEAN_TO_ROWINDEX(RT.B), 1, 2))] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE(IFF(b, 1, 2)) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B), 1, 2) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, 1, 2) IS NULL AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(NOT(RT.B)), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(NOT b, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B AND RT.B), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b AND b, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(RT.B OR RT.B), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b OR b, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(RT.N > 1, 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(n > 1, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(RT.B IS NULL, 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b IS NULL, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(RT.G = 'x', 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(g = 'x', 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(RT.B = TRUE, 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b = TRUE, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(RT.G LIKE 'a%', 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(g LIKE 'a%', 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF((RT.N >= 1) AND (RT.N <= 2), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(n BETWEEN 1 AND 2, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(NOT(RT.N > 1), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(NOT n > 1, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF((RT.N > 1) AND (RT.N < 5), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(n > 1 AND n < 5, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF((RT.N > 1) AND (BOOLEAN_TO_ROWINDEX(RT.B)), 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(n > 1 AND b, 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST((IFF(BOOLEAN_TO_ROWINDEX(RT.B), 1, 2)) + 1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(b, 1, 2) + 1 AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(BOOLEAN_TO_ROWINDEX(\"values\".B), \"values\".D, \"values\".D) AS NUMBER(38,0))] for parameter 'TO_NUMBER'",
            answer("SELECT CAST(IFF(b, d, d) AS NUMBER) FROM (SELECT b, CURRENT_DATE() AS d FROM rt)"));
    }

    /** An invalid-type sentence spells a list as a chain of memberships. */
    @Test
    public void anInvalidTypeSentenceSpellsAListAsAChainOfMemberships() {
        assertEquals("SQL compilation error:|invalid type [TO_DATE('b' IN 'a')] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE('b' IN ('a'))"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE('b' IN 'a' IN 'b')] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE('b' IN ('a', 'b'))"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(RT.G IN 'a', 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(g IN ('a'), 1, 2) AS DATE) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(IFF(RT.G IN 'a' IN 'b', 1, 2) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(IFF(g IN ('a', 'b'), 1, 2) AS DATE) FROM rt"));
    }

    /** An arithmetic operand's rescale keeps two integer digits; a comparison's keeps its own. */
    @Test
    public void anArithmeticRescaleKeepsTwoIntegerDigits() {
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 + 0.5)::DATE"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER((CAST(2 AS NUMBER(3,1))) + 0.5, 1)] expected 1, got 2",
            answer("SELECT UPPER(2 + 0.5, 1)"));
        assertEquals("SQL compilation error:|Can not convert parameter '(CAST(2 AS NUMBER(3,1))) + 0.5' of type [NUMBER(4,1)] into expected type [BOOLEAN]",
            answer("SELECT CASE WHEN 2 + 0.5 THEN 1 END"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(0 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (0 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(5 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (5 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(9 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (9 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(10 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (10 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(22 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (22 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(99 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (99 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(100 AS NUMBER(4,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (100 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(123 AS NUMBER(4,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (123 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(-2 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (-2 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST(0.5 + (FIXED_TO_FIXED(2 AS NUMBER(3,1)[UNKNOWN])) AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (0.5 + 2)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(4,2)[UNKNOWN])) + 0.55 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 + 0.55)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(5,3)[UNKNOWN])) + 0.125 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 + 0.125)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(1 AS NUMBER(4,2)[UNKNOWN])) + 0.25 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (1 + 0.25)::DATE"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER((CAST(1 AS NUMBER(4,2))) + 0.25, 1)] expected 1, got 2",
            answer("SELECT UPPER(1 + 0.25, 1)"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(3,1)[UNKNOWN])) + 1.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 + 1.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(3,1)[UNKNOWN])) + 10.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 + 10.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(0.5 AS NUMBER(4,2)[UNKNOWN])) + 0.25 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (0.5 + 0.25)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(1.5 AS NUMBER(4,2)[UNKNOWN])) + 0.25 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (1.5 + 0.25)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(12.5 AS NUMBER(4,2)[UNKNOWN])) + 0.25 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (12.5 + 0.25)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(1.5 AS NUMBER(5,3)[UNKNOWN])) + 0.255 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (1.5 + 0.255)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST(0.5 + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (0.5 + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(3,1)[UNKNOWN])) - 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 - 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(3,1)[UNKNOWN])) % 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 % 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(2,1)[UNKNOWN])) > 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 > 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED((FIXED_TO_FIXED(2 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS NUMBER(5,2)[UNKNOWN])) + 0.25 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 + 0.5 + 0.25)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(identity(2) AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2::NUMBER(1,0) + 0.5)::DATE"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(RT.N AS NUMBER(6,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (n + 0.5)::DATE FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(DIG.N1 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (n1 + 0.5)::DATE FROM dig"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(DIG.N2 AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (n2 + 0.5)::DATE FROM dig"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(DIG.N1 AS NUMBER(4,2)[UNKNOWN])) + 0.25 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (n1 + 0.25)::DATE FROM dig"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(DIG.N1 AS NUMBER(3,1)[UNKNOWN])) + 1.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (n1 + 1.5)::DATE FROM dig"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(NEGATE(DIG.N1) AS NUMBER(3,1)[UNKNOWN])) + 0.5 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (-n1 + 0.5)::DATE FROM dig"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(DIG.N1 AS NUMBER(3,1)[UNKNOWN])) + DIG.D1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (n1 + d1)::DATE FROM dig"));
        assertEquals("SQL compilation error:|invalid type [CAST((FIXED_TO_FIXED(2 AS NUMBER(3,1)[UNKNOWN])) + DIG.D1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 + d1)::DATE FROM dig"));
        assertEquals("SQL compilation error:|invalid type [CAST(2 + DIG.N1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT (2 + n1)::DATE FROM dig"));
    }

    /** An invalid-type sentence spells LEFT, RIGHT and a path as the plan does, TO_VARIANT as written. */
    @Test
    public void anInvalidTypeSentenceSpellsLeftRightAndPathsAsThePlanDoes() {
        assertEquals("SQL compilation error:|invalid type [CAST(SUBSTR(FAM.G, 1, 2) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(LEFT(g, 2) IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(RIGHT2(FAM.G, 2) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(RIGHT(g, 2) IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(GET(FAM.V, 'a') IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(GET(v, 'a') IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(GET(FAM.V, 'a') IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(v:a IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(GET(FAM.V, 'a') IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(v['a'] IS NULL AS DATE) FROM fam"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_VARIANT(1) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(TO_VARIANT(1) IS NULL AS DATE)"));
        assertEquals("SQL compilation error:|invalid type [CAST(TO_VARIANT(FAM.N) IS NULL AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(TO_VARIANT(n) IS NULL AS DATE) FROM fam"));
    }

    /** A star call is a bare operand; a string is re-printed with its quote doubled. */
    @Test
    public void aStarCallIsABareOperandAndAQuoteIsDoubled() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(COUNT(*) + 1, 1)] expected 1, got 2",
            answer("SELECT UPPER(COUNT(*) + 1, 1) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER(COUNT(*) > 1, 1)] expected 1, got 2",
            answer("SELECT UPPER(COUNT(*) > 1, 1) FROM rt"));
        assertEquals("SQL compilation error:|invalid type [CAST(COUNT(*) + 1 AS DATE)] for parameter 'TO_DATE'",
            answer("SELECT CAST(COUNT(*) + 1 AS DATE) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [UPPER('it''s', 1)] expected 1, got 2",
            answer("SELECT UPPER('it''s', 1)"));
        assertEquals("SQL compilation error:|invalid type [TO_DATE('it''s' = 'x')] for parameter 'TO_DATE'",
            answer("SELECT TO_DATE('it''s' = 'x')"));
    }
}
