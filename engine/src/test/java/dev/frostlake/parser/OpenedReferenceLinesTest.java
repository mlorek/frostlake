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
 * The lines live stacks around an IDENTIFIER() reference that is not whole, by where it stands (live-verified): a
 * WHERE, HAVING, QUALIFY or ORDER BY tries the expression twice and names the word the parentheses open with twice,
 * a reference inside a select item's expression adds a backwards line naming its own '(', and a join condition is
 * refused at its ON.
 */
public class OpenedReferenceLinesTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String line(final int line, final int position, final String token) {
        return "\nsyntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    private static String refused(final String... lines) {
        final StringBuilder message = new StringBuilder("SQL compilation error:");
        for (final String each : lines) {
            message.append(each);
        }
        return message.toString();
    }

    @Test
    public void aClauseTriesTheExpressionTwice() {
        assertEquals(refused(line(1, 34, "UPPER"), line(1, 34, "UPPER")),
            refusal("SELECT 1 FROM t1 WHERE IDENTIFIER(UPPER('a')) = 1"));
        assertEquals(refused(line(1, 35, "UPPER"), line(1, 35, "UPPER")),
            refusal("SELECT 1 FROM t1 HAVING IDENTIFIER(UPPER('a')) = 1"));
        assertEquals(refused(line(1, 36, "UPPER"), line(1, 36, "UPPER")),
            refusal("SELECT 1 FROM t1 QUALIFY IDENTIFIER(UPPER('a')) = 1"));
        assertEquals(refused(line(1, 37, "UPPER"), line(1, 37, "UPPER")),
            refusal("SELECT 1 FROM t1 ORDER BY IDENTIFIER(UPPER('a'))"));
        assertEquals(refused(line(1, 38, "UPPER"), line(1, 38, "UPPER")),
            refusal("SELECT 1 FROM t1 WHERE NOT IDENTIFIER(UPPER('a')) = 1"));
        assertEquals(refused(line(1, 38, "UPPER"), line(1, 38, "UPPER")),
            refusal("SELECT 1 FROM t1 WHERE 1 = IDENTIFIER(UPPER('a'))"));
        assertEquals(refused(line(1, 34, "CONCAT"), line(1, 34, "CONCAT")),
            refusal("SELECT 1 FROM t1 WHERE IDENTIFIER(CONCAT('a', 'b')) = 1"));
    }

    @Test
    public void insideASelectItemsExpressionTheBracketFollows() {
        assertEquals(refused(line(1, 19, "UPPER"), line(1, 18, "(")),
            refusal("SELECT (IDENTIFIER(UPPER('a'))) FROM t1"));
        assertEquals(refused(line(1, 22, "UPPER"), line(1, 21, "(")),
            refusal("SELECT 1 + IDENTIFIER(UPPER('a')) FROM t1"));
        assertEquals(refused(line(1, 22, "UPPER"), line(1, 21, "(")),
            refusal("SELECT IFF(IDENTIFIER(UPPER('a')) = 1, 1, 2)"));
    }

    @Test
    public void aJoinConditionIsRefusedAtItsOn() {
        assertEquals(refused(line(1, 28, "ON")),
            refusal("SELECT 1 FROM t1 JOIN t1 t2 ON IDENTIFIER(UPPER('a')) = 1"));
    }
}
