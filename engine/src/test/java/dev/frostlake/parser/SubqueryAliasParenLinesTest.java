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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * An alias followed by '(' as the last thing in a subquery (live-verified): a derived table or an EXISTS reports the '('
 * alone, and a scalar subquery inside a call's brackets or in a later clause reports its SELECT and the first token
 * inside the bracket.
 */
public class SubqueryAliasParenLinesTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aDerivedTableOrExistsReportsTheParenthesisAlone() {
        assertEquals(lines(at(1, 28, "(")), refusal("SELECT * FROM (SELECT 1 foo (2))"));
        assertEquals(lines(at(1, 28, "(")), refusal("SELECT * FROM (SELECT 1 foo (2))"));
        assertEquals(lines(at(1, 28, "(")), refusal("SELECT * FROM (SELECT 1 foo (2)) x"));
        assertEquals(lines(at(1, 28, "(")), refusal("SELECT * FROM (SELECT 1 foo (2)) WHERE TRUE"));
        assertEquals(lines(at(1, 31, "(")), refusal("SELECT * FROM t, (SELECT 1 foo (2))"));
        assertEquals(lines(at(1, 35, "(")), refusal("SELECT * FROM t JOIN (SELECT 1 foo (2)) ON TRUE"));
        assertEquals(lines(at(1, 36, "(")), refusal("SELECT 1 WHERE EXISTS (SELECT 1 foo (2))"));
        assertEquals(lines(at(1, 28, "(")), refusal("SELECT * FROM (SELECT 1 foo ())"));
        assertEquals(lines(at(1, 35, "(")), refusal("SELECT COUNT(*) FROM (SELECT 1 foo (2))"));
        assertEquals(lines(at(1, 28, "(")), refusal("SELECT * FROM (SELECT 1 foo (2)) AS x"));
        assertEquals(lines(at(1, 36, "(")), refusal("SELECT 1 WHERE EXISTS (SELECT 1 foo (2)) AND TRUE"));
    }

    @Test
    public void aScalarSubqueryInACallOrClauseReportsItsSelectAndTheBracketsContent() {
        assertEquals(lines(at(1, 12, "SELECT"), at(1, 26, "2")), refusal("SELECT ABS((SELECT 1 foo (2)))"));
        assertEquals(lines(at(1, 16, "SELECT"), at(1, 30, "2")), refusal("SELECT 1 WHERE (SELECT 1 foo (2)) = 1"));
        assertEquals(lines(at(1, 14, "SELECT"), at(1, 30, "'b'")), refusal("SELECT UPPER((SELECT 'a' foo ('b')))"));
        assertEquals(lines(at(1, 27, "SELECT"), at(1, 41, "2")), refusal("SELECT 1 FROM t WHERE a = (SELECT 1 foo (2))"));
    }

    @Test
    public void theOtherBracketedShapesKeepTheirLines() {
        assertEquals(lines(at(1, 24, "("), at(1, 25, "2"), at(1, 27, ")")), refusal("WITH c AS (SELECT 1 foo (2)) SELECT * FROM c"));
        assertEquals(lines(at(1, 29, "("), at(1, 32, ")")), refusal("SELECT * FROM ((SELECT 1 foo (2)))"));
        assertEquals(lines(at(1, 28, "("), at(1, 46, ")")), refusal("SELECT * FROM (SELECT 1 foo (2) UNION SELECT 3)"));
    }

    @Test
    public void bareLoopConditionsKeepTheirLines() {
        assertEquals(lines(at(1, 12, "'x'"), at(1, 16, "LOOP")), refusal("BEGIN WHILE 'x' LOOP RETURN 1; END LOOP; END"));
        assertEquals(lines(at(1, 12, "'x'"), at(1, 16, "DO")), refusal("BEGIN WHILE 'x' DO RETURN 1; END WHILE; END"));
        assertEquals(lines(at(1, 12, "'x'"), at(1, 16, "LOOP")), refusal("BEGIN WHILE 'x' LOOP BREAK; END LOOP; END"));
    }
}
