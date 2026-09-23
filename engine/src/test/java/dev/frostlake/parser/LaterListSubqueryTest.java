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
 * Only a call's FIRST argument may be a subquery written without parentheses of its own, so a SELECT or WITH after a
 * comma in a call's arguments or an IN list is refused at that keyword (live-verified). Live's recovery then drops
 * the keyword and every token that could not follow the list closed there: a comma or the closing parenthesis carries
 * on the list, a token right before that parenthesis is dropped too, and any other resuming token closes the list
 * before itself and is read as what follows it, so the first fault that reading meets is reported as well.
 */
public class LaterListSubqueryTest extends BaseDatabaseTest {

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
    public void theKeywordIsRefusedWhereverTheListStands() {
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b')"));
        assertEquals(lines(at(1, 24, "SELECT")), refusal("SELECT CONCAT('a', 'b', SELECT 'c')"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b') FROM t"));
        assertEquals(lines(at(1, 33, "SELECT")), refusal("SELECT CONCAT('a', (SELECT 'b'), SELECT 'c')"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT b)"));
        assertEquals(lines(at(1, 19, "WITH")), refusal("SELECT CONCAT('a', WITH)"));
        assertEquals(lines(at(1, 19, "WITH")), refusal("SELECT CONCAT('a', WITH x)"));
        assertEquals(lines(at(1, 27, "SELECT")), refusal("SELECT 1 WHERE CONCAT('a', SELECT 'b') = 'ab'"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT COALESCE(1, SELECT 2)"));
        assertEquals(lines(at(1, 31, "SELECT")), refusal("SELECT a FROM t WHERE a IN (1, SELECT 2)"));
        assertEquals(lines(at(1, 26, "SELECT")), refusal("SELECT ARRAY_CONSTRUCT(1, SELECT 2)"));
        assertEquals(lines(at(1, 20, "SELECT")), refusal("SELECT CONCAT(1, 2, SELECT 3, 4, 5)"));
        assertEquals(lines(at(1, 21, "SELECT")), refusal("SELECT t.CONCAT('a', SELECT 'b')"));
    }

    @Test
    public void aCommaOrTheClosingParenthesisCarriesTheListOn() {
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b', 'c')"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b' 'c')"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT DISTINCT 'b')"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT NOT 'b')"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b' x)"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b' FROM)"));
        assertEquals(lines(at(1, 19, "WITH")), refusal("SELECT CONCAT('a', WITH 'x')"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b' AS)"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b', 'c' + 1)"));
        assertEquals(lines(at(1, 19, "SELECT")), refusal("SELECT CONCAT('a', SELECT TRUE)"));
    }

    @Test
    public void anyOtherResumingTokenClosesTheListBeforeItself() {
        assertEquals(lines(at(1, 19, "WITH"), at(1, 26, "AS")), refusal("SELECT CONCAT('a', WITH x AS (SELECT 1) SELECT 'b')"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 36, ")")), refusal("SELECT CONCAT('a', SELECT 'b' FROM t)"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 31, ")")), refusal("SELECT CONCAT('a', SELECT 1 + 2)"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 34, ")")), refusal("SELECT CONCAT('a', SELECT 'b' AS x)"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 31, "SELECT")), refusal("SELECT CONCAT('a', SELECT 'b', SELECT 'c')"));
        assertEquals(lines(at(1, 20, "SELECT"), at(1, 47, ")")), refusal("SELECT CONCAT('a' , SELECT a FROM t WHERE a = 1)"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 46, ")")), refusal("SELECT CONCAT('a', SELECT 'b' UNION SELECT 'c')"));
        assertEquals(lines(at(1, 19, "WITH"), at(1, 26, "AS")), refusal("SELECT CONCAT('a', WITH x AS (SELECT 1) SELECT 'b', 'c')"));
        assertEquals(lines(at(1, 19, "WITH"), at(1, 34, "x")), refusal("SELECT CONCAT('a', WITH RECURSIVE x AS (SELECT 1) SELECT 'b')"));
        assertEquals(lines(at(1, 19, "WITH"), at(1, 26, "AS")), refusal("SELECT CONCAT('a', WITH x AS (SELECT 1))"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 36, ")")), refusal("SELECT CONCAT('a', SELECT 'b' || 'c')"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 38, ")")), refusal("SELECT CONCAT('a', SELECT 'b'::VARCHAR)"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 40, ")")), refusal("SELECT CONCAT('a', SELECT 'b' WHERE TRUE)"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 28, ")")), refusal("SELECT CONCAT('a', SELECT -1)"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 32, "y")), refusal("SELECT CONCAT('a', SELECT 'b' x y)"));
        assertEquals(lines(at(1, 19, "WITH"), at(1, 26, "AS")), refusal("SELECT CONCAT('a', WITH x AS)"));
        assertEquals(lines(at(1, 25, "SELECT"), at(1, 38, ")")), refusal("SELECT UPPER(CONCAT('a', SELECT 1 + 2))"));
        assertEquals(lines(at(1, 19, "SELECT"), at(1, 47, ")")), refusal("SELECT CONCAT('a', SELECT 'b' EXCEPT SELECT 'c')"));
    }
}
