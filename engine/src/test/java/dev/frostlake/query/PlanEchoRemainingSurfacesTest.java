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
 * Three more refusal surfaces that name an expression, now naming it from the PLAN like the rest: the
 * ROW echo of a mis-shaped tuple IN, the operand of a conversion refusal, and the offending column of
 * a DISTINCT ORDER BY key.
 *
 * <p>★ THE ORDER BY ONE WAS A REAL DEFECT, not a spelling. {@code ORDER BY UPPER(n)} was refused as
 * "[EB.UPPER] is not a valid order by expression" — naming a column no table has — because the walk
 * looking for the offending column descends the parse tree, and a function's own NAME parses as a
 * column reference perfectly well on its own. Requiring the name to be one some relation actually
 * declares steps past the syntax word and reaches the argument, where the real column is.
 *
 * <p>The fix had to keep {@code ORDER BY SUM(b) + 1} legal when SUM(b) is selected: a subexpression
 * that IS a selected output excuses everything inside it, which is why this is a guard on the walk
 * rather than a switch to walking the expression AST — that route loses the excusing check.
 */
public class PlanEchoRemainingSurfacesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE eb (bn BINARY(4), n NUMBER(10,2), g VARCHAR(10),"
            + " i INT)");
        engine.execute("INSERT INTO eb SELECT TO_BINARY('AB'), 2.50, 'zz', 3");
    }

    private String refusal(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            return "ACCEPTED: " + (rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>");
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** A conversion refusal names a CALL or an OPERATOR operand from the plan. */
    @Test
    public void aConversionRefusalNamesItsOperandFromThePlan() {
        assertEquals("SQL compilation error:|Can not convert parameter 'EB.G' of type [VARCHAR(10)]"
            + " into expected type [BINARY(4)]",
            refusal("SELECT IFF(TRUE, bn, g) FROM eb"));
        assertEquals("SQL compilation error:|Can not convert parameter 'UPPER(EB.G)' of type"
            + " [VARCHAR(30)] into expected type [BINARY(4)]",
            refusal("SELECT IFF(TRUE, bn, UPPER(g)) FROM eb"),
            "the column inside the call is qualified");
        assertEquals("SQL compilation error:|Can not convert parameter 'EB.G || 'x'' of type"
            + " [VARCHAR(11)] into expected type [BINARY(4)]",
            refusal("SELECT IFF(TRUE, bn, g || 'x') FROM eb"),
            "and an operator is qualified AND printed without its outer brackets");
    }

    /**
     * A mis-shaped tuple IN names its ROW members from the plan.
     *
     * <p>Asserted WHOLE now, prefix included: this family used to omit the
     * {@code SQL compilation error:} marker live prints, which was a separate defect with its own
     * consequence — a view over such a body was created empty rather than refused — and it is fixed.
     */
    @Test
    public void aRowEchoNamesItsMembersFromThePlan() {
        assertEquals("SQL compilation error:|Can not convert parameter 'ROW(EB.I, EB.N, EB.G)' of type"
            + " [ROW(NUMBER(38,0), NUMBER(10,2), VARCHAR(10))] into expected type"
            + " [ROW(NUMBER(38,0), NUMBER(10,2))]",
            refusal("SELECT * FROM eb WHERE (i, n) IN ((i, n, g))"));
    }

    /** ★ A DISTINCT ORDER BY key names the offending COLUMN, never a word from the syntax. */
    @Test
    public void anOrderByKeyNamesTheOffendingColumn() {
        assertEquals("SQL compilation error:|[EB.N] is not a valid order by expression",
            refusal("SELECT DISTINCT g FROM eb ORDER BY n"));
        assertEquals("SQL compilation error:|[EB.N] is not a valid order by expression",
            refusal("SELECT DISTINCT g FROM eb ORDER BY n + 1"));
        assertEquals("SQL compilation error:|[EB.N] is not a valid order by expression",
            refusal("SELECT DISTINCT g FROM eb ORDER BY UPPER(n)"),
            "the function's own name is not a column, so the walk goes past it to the argument");
    }

    /** The needs-to-be-constant echo already named its argument from the plan, and still does. */
    @Test
    public void theConstantArgumentEchoIsUnchanged() {
        assertEquals("SQL compilation error:|argument 2 to function BASE64_ENCODE needs to be"
            + " constant, found 'EB.I'",
            refusal("SELECT BASE64_ENCODE(g, i) FROM eb"));
        assertEquals("SQL compilation error:|argument 2 to function BASE64_ENCODE needs to be"
            + " constant, found 'EB.I + 1'",
            refusal("SELECT BASE64_ENCODE(g, i + 1) FROM eb"));
        assertEquals("SQL compilation error:|argument 2 to function BASE64_ENCODE needs to be"
            + " constant, found 'ABS(EB.I)'",
            refusal("SELECT BASE64_ENCODE(g, ABS(i)) FROM eb"));
    }

    /** A selected subexpression still excuses the columns inside it. */
    @Test
    public void aSelectedSubexpressionStillExcusesItsColumns() {
        assertEquals("ACCEPTED: 2.50",
            refusal("SELECT DISTINCT SUM(n) FROM eb GROUP BY g ORDER BY SUM(n) + 1"));
    }
}
