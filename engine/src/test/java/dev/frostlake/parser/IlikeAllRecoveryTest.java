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
 * ILIKE ALL does not exist, so the ALL is refused; live's recovery then reads the bracket after it as an outer-join
 * marker's and refuses the first token inside, and where a comma inside the bracket can carry on the enclosing list the
 * first closing parenthesis left unmatched after that comma is refused as well (live-verified).
 */
public class IlikeAllRecoveryTest extends BaseDatabaseTest {

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
    public void theFirstTokenInsideTheBracketIsRefused() {
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'a%'")), refusal("SELECT 'ab' ILIKE ALL ('a%') ESCAPE '!'"));
        assertEquals(lines(at(1, 33, "ALL"), at(1, 38, "'x%'")), refusal("SELECT a FROM t WHERE 'ab' ILIKE ALL ('x%', 'a!')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'a%'")), refusal("SELECT 'ab' ILIKE ALL ('a%')"));
        assertEquals(lines(at(1, 18, "ALL")), refusal("SELECT 'ab' ILIKE ALL (+)"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'a%'")), refusal("SELECT 'ab' ILIKE ALL ('a%') FROM t"));
        assertEquals(lines(at(1, 22, "ALL"), at(1, 27, "'a%'")), refusal("SELECT 'ab' NOT ILIKE ALL ('a%')"));
        assertEquals(lines(at(1, 18, "ALL")), refusal("SELECT 'ab' ILIKE ALL 'a%'"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "SELECT")), refusal("SELECT 'ab' ILIKE ALL (SELECT 'a%')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, ")")), refusal("SELECT 'ab' ILIKE ALL ()"));
        assertEquals(lines(at(1, 33, "ALL"), at(1, 38, "'a%'")), refusal("SELECT 1 FROM t WHERE 'ab' ILIKE ALL ('a%') ESCAPE '!'"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'")), refusal("SELECT 'ab' ILIKE ALL ('x%')::VARCHAR"));
        assertEquals(lines(at(1, 33, "ALL"), at(1, 38, "'x%'")), refusal("SELECT 1 FROM t WHERE 'ab' ILIKE ALL ('x%', 'a!') AND TRUE"));
        assertEquals(lines(at(1, 45, "ALL"), at(1, 50, "'x%'")), refusal("SELECT a FROM t, (SELECT 1) WHERE 'ab' ILIKE ALL ('x%', 'a!')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'")), refusal("SELECT 'ab' ILIKE ALL ('x%' 'a!')"));
    }

    @Test
    public void aCommaThatCarriesTheListOnStacksTheUnmatchedParenthesis() {
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'"), at(1, 33, ")")), refusal("SELECT 'ab' ILIKE ALL ('x%', 'a!')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'"), at(1, 33, ")")), refusal("SELECT 'ab' ILIKE ALL ('x%', 'a!') ESCAPE '!'"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'"), at(1, 39, ")")), refusal("SELECT 'ab' ILIKE ALL ('x%', 'a!', 'b%')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'"), at(1, 33, ")")), refusal("SELECT 'ab' ILIKE ALL ('x%', 'a!'), 1"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'"), at(1, 33, ")")), refusal("SELECT 'ab' ILIKE ALL ('x%', 'a!') FROM t"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "("), at(1, 35, ")")), refusal("SELECT 'ab' ILIKE ALL (('x%'), 'a!')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "b"), at(1, 30, ")")), refusal("SELECT 'ab' ILIKE ALL (b, 'a!') FROM t"));
        assertEquals(lines(at(1, 28, "ALL"), at(1, 33, "'x%'"), at(1, 43, ")")), refusal("SELECT CASE WHEN 'ab' ILIKE ALL ('x%', 'a!') THEN 1 END"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'"), at(1, 33, ")")), refusal("SELECT 'ab' ILIKE ALL ('x%', 'a!') x"));
        assertEquals(lines(at(1, 24, "ALL"), at(1, 29, "'x%'"), at(1, 40, ")")), refusal("SELECT UPPER('ab' ILIKE ALL ('x%', 'a!'))"));
        assertEquals(lines(at(1, 22, "ALL"), at(1, 27, "'x%'"), at(1, 37, ")")), refusal("SELECT 'ab' NOT ILIKE ALL ('x%', 'a!')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'"), at(1, 39, ")")), refusal("SELECT 'ab' ILIKE ALL ('x%', 'a!', 'b%') ESCAPE '!'"));
        assertEquals(lines(at(1, 36, "ALL"), at(1, 41, "'x%'"), at(1, 51, ")")), refusal("SELECT 1 FROM t ORDER BY 'ab' ILIKE ALL ('x%', 'a!')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "1"), at(1, 34, ")")), refusal("SELECT 'ab' ILIKE ALL (1 + 2, 'a!')"));
        assertEquals(lines(at(1, 18, "ALL"), at(1, 23, "'x%'"), at(1, 33, ")")), refusal("SELECT 'ab' ILIKE ALL ('x%', 'a!') AS y"));
    }
}
