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
 * WHEN a refusal's plan echo qualifies a column, and when it prints it bare.
 *
 * <p>★ THE OUTPUT COLUMNS DECIDE, and nothing else does. An expression in the ORDER BY clause is
 * re-printed as it resolves in the query's OUTPUT scope — a projected column IS the column and has no
 * relation to name — while the same expression in WHERE, HAVING or GROUP BY resolves against the FROM
 * and keeps its qualifier. Both are live, side by side in the same fixture.
 *
 * <p>★ THREE HYPOTHESES DIED IN THE MEASUREMENT, and each would have been a plausible guess:
 * <ul>
 *   <li>NOT "single relation prints bare" — a JOIN's unambiguous key prints bare too when it is
 *       selected, and a single relation's key prints QUALIFIED when it is not.</li>
 *   <li>NOT "the GROUP BY keys print bare" — {@code SELECT a … GROUP BY a, b … ORDER BY … OVER
 *       (ORDER BY b)} prints GW.B: b is grouped but never projected.</li>
 *   <li>NOT "ambiguity decides" — an ambiguous bare name over a JOIN prints bare, and a name written
 *       with its qualifier keeps the qualifier however unambiguous it was.</li>
 * </ul>
 *
 * <p>The two decisive cells are the aliased and the computed select item: {@code SELECT a AS z …
 * ORDER BY … OVER (ORDER BY a)} prints GW.A because the output carries Z and not A, and
 * {@code SELECT a + 1 … OVER (ORDER BY a)} prints GW.A because the output is named "A + 1". Under any
 * rule about the relation or the grouping, both would have printed bare.
 *
 * <p>Left for its own task: a GROUP BY key that is itself a window call or an aggregate, where live
 * validates the SELECT LIST before it judges the key at all — a different sentence, not a different
 * echo.
 */
public class PlanEchoOutputScopeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE pe (a INT, b INT)");
        engine.execute("INSERT INTO pe VALUES (1, 10), (2, 20)");
        engine.execute("CREATE OR REPLACE TABLE pf (a INT, c INT)");
        engine.execute("INSERT INTO pf VALUES (1, 100), (2, 200)");
    }

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

    private String orderByEcho(final String call) {
        return "SQL compilation error:|[" + call + "] is not a valid order by expression";
    }

    /** ★ A key the SELECT list projects prints bare, where it used to carry the relation. */
    @Test
    public void aprojectedKeyPrintsBare() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a)"));
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY A DESC NULLS FIRST)"),
            answer("SELECT a FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a DESC)"));
        assertEquals(orderByEcho("ROW_NUMBER() OVER (PARTITION BY A ORDER BY A ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a"
                + " ORDER BY ROW_NUMBER() OVER (PARTITION BY a ORDER BY a)"),
            "the PARTITION key follows the same rule");
    }

    /** ★ A key the list does NOT project keeps its qualifier — in the SAME echo. */
    @Test
    public void anUnprojectedKeyKeepsItsQualifier() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST, PE.B ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a, b)"),
            "one key of each kind, side by side");
        assertEquals(orderByEcho("LAG(PE.B) OVER (ORDER BY A ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a ORDER BY LAG(b) OVER (ORDER BY a)"),
            "and the call's ARGUMENT is not an ORDER BY key at all, so it stays qualified");
    }

    /** ★ It is the OUTPUT, not the GROUPING: a grouped column the list never projects is qualified. */
    @Test
    public void groupingIsNotWhatDecides() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY PE.B ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a, b ORDER BY ROW_NUMBER() OVER (ORDER BY b)"));
    }

    /** ★ An ALIASED item outputs its ALIAS, so the underlying column is no longer an output name. */
    @Test
    public void anAliasedItemOutputsItsAlias() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY PE.A ASC NULLS LAST)"),
            answer("SELECT a AS z FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a)"),
            "the output carries Z, so a names nothing the output has");
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY Z ASC NULLS LAST)"),
            answer("SELECT a AS z FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY z)"));
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY M ASC NULLS LAST)"),
            answer("SELECT a, MAX(b) m FROM pe GROUP BY a"
                + " ORDER BY ROW_NUMBER() OVER (ORDER BY m)"),
            "an aggregate's alias is an output name like any other");
    }

    /** ★ A COMPUTED item outputs a computed name, which no bare reference can match. */
    @Test
    public void acomputedItemOutputsAcomputedName() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY PE.A ASC NULLS LAST)"),
            answer("SELECT a + 1 FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a)"));
    }

    /** A key that is itself an expression is printed part by part, under the same rule. */
    @Test
    public void anExpressionKeyIsPrintedPartByPart() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY A + 1 ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a + 1)"));
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY ABS(A) ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY ABS(a))"));
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY SUM(PE.B) ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY SUM(b))"),
            "b is not projected, so the aggregate over it is qualified");
    }

    /** ★ A qualifier the user WROTE survives, output column or not. */
    @Test
    public void awrittenQualifierSurvives() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY PE.A ASC NULLS LAST)"),
            answer("SELECT a FROM pe GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY pe.a)"));
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY G.A ASC NULLS LAST)"),
            answer("SELECT a FROM pe g GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY g.a)"),
            "an alias qualifier is kept as the alias");
    }

    /** A qualified SELECT ITEM still outputs the bare column name. */
    @Test
    public void aqualifiedSelectItemStillOutputsTheBareName() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST)"),
            answer("SELECT pe.a FROM pe GROUP BY pe.a ORDER BY ROW_NUMBER() OVER (ORDER BY a)"));
    }

    /** ★ A STAR projects every column, and every one of them is an output name. */
    @Test
    public void astarProjectsEveryColumn() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY B ASC NULLS LAST)"),
            answer("SELECT * FROM pe GROUP BY a, b ORDER BY ROW_NUMBER() OVER (ORDER BY b)"));
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST)"),
            answer("SELECT * FROM pe GROUP BY a, b ORDER BY ROW_NUMBER() OVER (ORDER BY a)"));
    }

    /** ★ Over a JOIN it is still the output that decides, not the ambiguity. */
    @Test
    public void ajoinFollowsTheSameRule() {
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY PE.B ASC NULLS LAST)"),
            answer("SELECT pe.a FROM pe JOIN pf ON pe.a = pf.a GROUP BY pe.a"
                + " ORDER BY ROW_NUMBER() OVER (ORDER BY b)"),
            "unambiguous but unprojected — so qualified");
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST)"),
            answer("SELECT pe.a FROM pe JOIN pf ON pe.a = pf.a GROUP BY pe.a"
                + " ORDER BY ROW_NUMBER() OVER (ORDER BY a)"),
            "ambiguous but projected — so bare");
        assertEquals(orderByEcho("ROW_NUMBER() OVER (ORDER BY W.B ASC NULLS LAST)"),
            answer("SELECT w.a FROM pe w JOIN pf x ON w.a = x.a GROUP BY w.a"
                + " ORDER BY ROW_NUMBER() OVER (ORDER BY w.b)"));
    }

    /** ★ EVERY OTHER CLAUSE keeps the qualified form — the output scope is the ORDER BY's alone. */
    @Test
    public void everyOtherClauseKeepsTheQualifier() {
        assertEquals("SQL compilation error:|[ROW_NUMBER() OVER (ORDER BY PE.A ASC NULLS LAST)]"
            + " is not a valid group by expression",
            answer("SELECT a FROM pe GROUP BY a HAVING ROW_NUMBER() OVER (ORDER BY a) = 1"));
        assertEquals("SQL compilation error:|Window function"
            + " [ROW_NUMBER() OVER (ORDER BY PE.A ASC NULLS LAST)]"
            + " appears outside of SELECT, QUALIFY, and ORDER BY clauses.",
            answer("SELECT a FROM pe WHERE ROW_NUMBER() OVER (ORDER BY a) = 1"));
        assertEquals("SQL compilation error:|Window function"
            + " [ROW_NUMBER() OVER (ORDER BY PE.B ASC NULLS LAST)]"
            + " appears outside of SELECT, QUALIFY, and ORDER BY clauses.",
            answer("SELECT pe.a FROM pe JOIN pf ON pe.a = pf.a"
                + " WHERE ROW_NUMBER() OVER (ORDER BY pe.b) = 1"));
    }
}
