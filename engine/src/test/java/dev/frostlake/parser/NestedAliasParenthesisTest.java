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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A select item's ALIAS followed by '(' inside a bracketed query. In a SELECT list the account names the SELECT
 * that opens every enclosing subquery, outermost first, and then the '(' — for a whole item, an operand, an IN
 * list and a CASE branch alike. In a CTE whose body ends with the bracket it names the '(', the first token
 * inside it that is not another '(', and the CTE's own ')', which it no longer expects; an empty bracket is the
 * '(' alone. Either way nothing after that is reported, in the statement or after it.
 *
 * <pre>
 *   SELECT (SELECT 1 foo (2))                  'SELECT' at 8, '(' at 21
 *   SELECT (SELECT (SELECT 1 foo (2)))         'SELECT' at 8, 'SELECT' at 16, '(' at 29
 *   WITH c AS (SELECT 1 foo (2)) SELECT …      '(' at 24, '2' at 25, ')' at 27
 *   WITH c AS (SELECT 1 foo ()) SELECT …       '(' at 24
 * </pre>
 */
public class NestedAliasParenthesisTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int position, final String token) {
        return "\nsyntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void aSubqueryInTheSelectListNamesItsSelectThenTheBracket() {
        assertEquals(refused(line(8, "SELECT"), line(21, "(")), refusal("SELECT (SELECT 1 foo (2))"));
        assertEquals(refused(line(8, "SELECT"), line(21, "(")), refusal("SELECT (SELECT 1 foo (2) FROM t)"));
        assertEquals(refused(line(8, "SELECT"), line(26, "(")), refusal("SELECT (SELECT 'a' AS foo ('x')), 3"));
        assertEquals(refused(line(11, "SELECT"), line(24, "(")), refusal("SELECT 1, (SELECT 2 bar (3))"));
        assertEquals(refused(line(8, "SELECT"), line(21, "(")), refusal("SELECT (SELECT 1 foo (2)) AS z FROM t"));
        assertEquals(refused(line(11, "SELECT"), line(24, "(")), refusal("SELECT a, (SELECT 1 foo (2)) FROM t"));
    }

    @Test
    public void anOperandAnInListOrACaseBranchAlike() {
        assertEquals(refused(line(12, "SELECT"), line(25, "(")), refusal("SELECT 1 + (SELECT 1 foo (2)) + 1"));
        assertEquals(refused(line(12, "SELECT"), line(25, "(")), refusal("SELECT 1 * (SELECT 2 bar (3))"));
        assertEquals(refused(line(9, "SELECT"), line(22, "(")), refusal("SELECT -(SELECT 1 foo (2))"));
        assertEquals(refused(line(13, "SELECT"), line(26, "(")), refusal("SELECT 1 IN (SELECT 1 foo (2))"));
        assertEquals(refused(line(15, "SELECT"), line(30, "(")), refusal("SELECT 'x' IN (SELECT 'a' foo ('b'))"));
        assertEquals(refused(line(19, "SELECT"), line(34, "(")), refusal("SELECT 'x' NOT IN (SELECT 'a' foo ('b'))"));
        assertEquals(refused(line(28, "SELECT"), line(41, "(")),
            refusal("SELECT CASE WHEN TRUE THEN (SELECT 1 foo (2)) END"));
    }

    @Test
    public void everyEnclosingSubqueryIsNamed() {
        assertEquals(refused(line(8, "SELECT"), line(16, "SELECT"), line(29, "(")),
            refusal("SELECT (SELECT (SELECT 1 foo (2)))"));
        assertEquals(refused(line(8, "SELECT"), line(16, "SELECT"), line(29, "(")),
            refusal("SELECT (SELECT (SELECT 1 foo (2)) + 1)"));
    }

    @Test
    public void aLaterFaultAddsNothing() {
        assertEquals(refused(line(8, "SELECT"), line(21, "(")), refusal("SELECT (SELECT 1 foo (2)) FROM t WHERE 1 1"));
        final String cte = refused(line(24, "("), line(25, "2"), line(27, ")"));
        assertEquals(cte, refusal("WITH c AS (SELECT 1 foo (2)) SELECT * FROM c WHERE 1 1"));
        assertEquals(cte, refusal("WITH c AS (SELECT 1 foo (2)) SELECT * FROM c WHERE (1 1)"));
        assertEquals(cte, refusal("WITH c AS (SELECT 1 foo (2)) SELECT * FROM c; SELECT 1 1"));
    }

    @Test
    public void aCteNamesTheBracketItsFirstTokenAndItsOwnClose() {
        assertEquals(refused(line(24, "("), line(25, "2"), line(27, ")")),
            refusal("WITH c AS (SELECT 1 foo (2)) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(25, "'x'"), line(29, ")")),
            refusal("WITH c AS (SELECT 1 foo ('x')) SELECT * FROM c"));
        assertEquals(refused(line(29, "("), line(30, "'x'"), line(34, ")")),
            refusal("WITH c AS (SELECT 'a' AS foo ('x')) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(25, "a"), line(27, ")")),
            refusal("WITH c AS (SELECT 1 foo (a)) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(25, "x"), line(29, ")")),
            refusal("WITH c AS (SELECT 1 foo (x.y)) SELECT * FROM c"));
        assertEquals(refused(line(32, "("), line(33, "3"), line(35, ")")),
            refusal("WITH c AS (SELECT 1 AS k, 2 foo (3)) SELECT * FROM c"));
        assertEquals(refused(line(28, "("), line(29, "2"), line(31, ")")),
            refusal("WITH c (k) AS (SELECT 1 foo (2)) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(25, "2"), line(27, ")")),
            refusal("WITH c AS (SELECT 1 foo (2)), d AS (SELECT 2) SELECT * FROM c"));
        assertEquals(refused(line(26, "("), line(27, "b"), line(29, ")")),
            refusal("WITH c AS (SELECT 'a' foo (b)), d AS (SELECT 1) SELECT * FROM d"));
    }

    @Test
    public void onlyTheFirstTokenInsideTheBracketIsNamed() {
        assertEquals(refused(line(24, "("), line(25, "2"), line(30, ")")),
            refusal("WITH c AS (SELECT 1 foo (2, 3)) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(25, "2"), line(30, ")")),
            refusal("WITH c AS (SELECT 1 foo (2, a)) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(25, "2"), line(31, ")")),
            refusal("WITH c AS (SELECT 1 foo (2 + 3)) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(25, "'x'"), line(36, ")")),
            refusal("WITH c AS (SELECT 1 foo ('x' || 'y')) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(26, "2"), line(29, ")")),
            refusal("WITH c AS (SELECT 1 foo ((2))) SELECT * FROM c"));
        assertEquals(refused(line(24, "("), line(27, "a"), line(31, ")")),
            refusal("WITH c AS (SELECT 1 foo (((a)))) SELECT * FROM c"));
    }

    @Test
    public void anEmptyBracketIsTheBracketAlone() {
        assertEquals(refused(line(24, "(")), refusal("WITH c AS (SELECT 1 foo ()) SELECT * FROM c"));
        assertEquals(refused(line(29, "(")), refusal("WITH c AS (SELECT 'a' AS foo ()) SELECT * FROM c"));
    }
}
