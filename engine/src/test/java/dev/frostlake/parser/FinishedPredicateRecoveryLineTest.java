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
 * The line live's recovery stacks after refusing an operator whose left operand is a finished predicate
 * (live-verified). In a select item the operator is dropped and the next name is read as the item's alias, so the
 * first token that cannot follow an alias is refused too; a call, a CASE and parentheses give their own line; a WHERE,
 * HAVING or ORDER BY, and anything after EXISTS, stack nothing.
 */
public class FinishedPredicateRecoveryLineTest extends BaseDatabaseTest {

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
    public void insideASelectItemTheNameAfterTheOperatorIsReadAsAnAlias() {
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "+")), refusal("SELECT 1 IN (1, 2)::INT + 1"));
        assertEquals(lines(at(1, 18, "::"), at(1, 23, "::")), refusal("SELECT 1 IN (1, 2)::INT::VARCHAR"));
        assertEquals(lines(at(1, 28, "IS"), at(1, 45, "3")), refusal("SELECT 1 IS DISTINCT FROM 2 IS DISTINCT FROM 3"));
        assertEquals(lines(at(1, 18, "::"), at(1, 26, "(")), refusal("SELECT 1 IN (1, 2)::NUMBER(10, 2) + 1"));
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "x")), refusal("SELECT 1 IN (1, 2)::INT x"));
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "+")), refusal("SELECT 1 IN (1, 2)::INT + 1 FROM t"));
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "+")), refusal("SELECT 'a' IS NULL::INT + 1"));
        assertEquals(lines(at(1, 30, "::"), at(1, 36, "+")), refusal("SELECT 'a' LIKE 'a' ESCAPE '!'::INT + 1"));
        assertEquals(lines(at(1, 28, "IS"), at(1, 45, "3")), refusal("SELECT 1 IS DISTINCT FROM 2 IS DISTINCT FROM 3 FROM t"));
        assertEquals(lines(at(1, 18, "::"), at(1, 23, "::")), refusal("SELECT 1 IN (1, 2)::INT::VARCHAR::INT"));
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "+")), refusal("SELECT 1 IN (1, 2)::INT + 1 + 2"));
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "(")), refusal("SELECT 1 IN (1, 2)::INT (1)"));
        assertEquals(lines(at(1, 28, "IS"), at(1, 45, "3")), refusal("SELECT 1 IS DISTINCT FROM 2 IS DISTINCT FROM 3 + 1"));
        assertEquals(lines(at(1, 28, "IS")), refusal("SELECT 1 IS DISTINCT FROM 2 IS DISTINCT FROM t.a"));
        assertEquals(lines(at(1, 18, "::"), at(1, 27, "(")), refusal("SELECT 'a' IS NULL::VARCHAR(10) || 'b'"));
    }

    @Test
    public void aCallACaseOrParenthesesGiveTheirOwnLine() {
        assertEquals(lines(at(1, 28, "::"), at(1, 34, "THEN")), refusal("SELECT CASE WHEN 1 IN (1, 2)::INT THEN 1 END"));
        assertEquals(lines(at(1, 27, "::"), at(1, 35, ")")), refusal("SELECT COALESCE('a' IS NULL::INT, 1)"));
        assertEquals(lines(at(1, 19, "::"), at(1, 28, ")")), refusal("SELECT (1 IN (1, 2)::INT + 1)"));
        assertEquals(lines(at(1, 24, "::"), at(1, 34, "||")), refusal("SELECT UPPER(1 IN (1, 2)::VARCHAR || 'x')"));
        assertEquals(lines(at(1, 27, "::")), refusal("SELECT COALESCE('a' IS NULL::INT)"));
        assertEquals(lines(at(1, 28, "::"), at(1, 34, "THEN")), refusal("SELECT CASE WHEN 1 IN (1, 2)::INT THEN 1 ELSE 2 END"));
        assertEquals(lines(at(1, 34, "::"), at(1, 43, ")")), refusal("SELECT * FROM t WHERE (a IN (1, 2)::INT + 1) = 1"));
        assertEquals(lines(at(1, 27, "::"), at(1, 35, ")")), refusal("SELECT COALESCE('a' IS NULL::INT, 1) FROM t"));
        assertEquals(lines(at(1, 30, "::")), refusal("SELECT COALESCE(1, 'a' IS NULL::INT)"));
        assertEquals(lines(at(1, 28, "::"), at(1, 34, "THEN")), refusal("SELECT CASE WHEN 1 IN (1, 2)::INT THEN 1 END FROM t"));
    }

    @Test
    public void nothingIsStackedWhereNoAliasIsRead() {
        assertEquals(lines(at(1, 18, "::")), refusal("SELECT 1 IN (1, 2)::INT, 2"));
        assertEquals(lines(at(1, 18, "::")), refusal("SELECT 1 IN (1, 2)::INT FROM t"));
        assertEquals(lines(at(1, 19, "||")), refusal("SELECT 'a' IS NULL || '' || ''"));
        assertEquals(lines(at(1, 19, "+")), refusal("SELECT 1 IN (1, 2) + 1 + 1"));
        assertEquals(lines(at(1, 18, "[")), refusal("SELECT 1 IN (1, 2)[0][1]"));
        assertEquals(lines(at(1, 25, "=")), refusal("SELECT EXISTS (SELECT 1) = TRUE = TRUE"));
        assertEquals(lines(at(1, 19, "IS")), refusal("SELECT 'a' IS NULL IS NULL IS NULL"));
        assertEquals(lines(at(1, 19, "||")), refusal("SELECT 'a' IS NULL || 'b' + 1"));
        assertEquals(lines(at(1, 15, "[")), refusal("SELECT 1 IN (1)[0] + 1"));
        assertEquals(lines(at(1, 33, "::")), refusal("SELECT a FROM t WHERE a IN (1, 2)::INT + 1"));
        assertEquals(lines(at(1, 33, "::")), refusal("SELECT a FROM t WHERE a IN (1, 2)::INT GROUP BY a"));
        assertEquals(lines(at(1, 36, "::")), refusal("SELECT a FROM t ORDER BY a IN (1, 2)::INT + 1"));
        assertEquals(lines(at(1, 45, "::")), refusal("SELECT a FROM t GROUP BY a HAVING a IN (1, 2)::INT + 1"));
        assertEquals(lines(at(1, 24, "::")), refusal("SELECT EXISTS (SELECT 1)::INT + 1"));
        assertEquals(lines(at(1, 28, "IS")), refusal("SELECT 1 IS DISTINCT FROM 2 IS NULL"));
        assertEquals(lines(at(1, 19, "||")), refusal("SELECT 1 IN (1, 2) || 'x' AS y FROM t"));
        assertEquals(lines(at(1, 18, "::")), refusal("SELECT 1 IN (1, 2)::INT WHERE TRUE"));
    }
}
