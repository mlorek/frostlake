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
 * A call left open with nothing inside it at the end of the input is refused at the end of input, and what follows
 * depends on where the call stands; a CAST or TRY_CAST left empty is refused at the token inside its parenthesis
 * (live-verified).
 */
public class EmptyCallEndOfInputTest extends BaseDatabaseTest {

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
    public void anEmptyCallInAClauseOrAnotherCallRepeatsTheEndOfInput() {
        assertEquals(lines(at(1, 26, "<EOF>"), at(1, 26, "<EOF>")), refusal("SELECT a FROM t WHERE ABS("));
        assertEquals(lines(at(1, 29, "<EOF>"), at(1, 29, "<EOF>")), refusal("SELECT a FROM t ORDER BY ABS("));
        assertEquals(lines(at(1, 29, "<EOF>"), at(1, 29, "<EOF>")), refusal("SELECT a FROM t GROUP BY ABS("));
        assertEquals(lines(at(1, 15, "<EOF>"), at(1, 15, "<EOF>")), refusal("SELECT ABS(ABS("));
        assertEquals(lines(at(1, 30, "<EOF>"), at(1, 30, "<EOF>")), refusal("SELECT a FROM t WHERE 1 = ABS("));
        assertEquals(lines(at(1, 27, "<EOF>"), at(1, 27, "<EOF>")), refusal("SELECT a FROM t WHERE (ABS("));
        assertEquals(lines(at(1, 17, "<EOF>"), at(1, 17, "<EOF>")), refusal("SELECT UPPER(ABS("));
        assertEquals(lines(at(1, 19, "<EOF>"), at(1, 19, "<EOF>")), refusal("SELECT ABS(ABS(ABS("));
        assertEquals(lines(at(1, 27, "<EOF>"), at(1, 27, "<EOF>")), refusal("SELECT a FROM t HAVING ABS("));
        assertEquals(lines(at(1, 28, "<EOF>"), at(1, 28, "<EOF>")), refusal("SELECT a FROM t QUALIFY ABS("));
        assertEquals(lines(at(1, 36, "<EOF>"), at(1, 36, "<EOF>")), refusal("SELECT a FROM t WHERE a = 1 AND ABS("));
        assertEquals(lines(at(1, 32, "<EOF>"), at(1, 32, "<EOF>")), refusal("SELECT a FROM t ORDER BY a, ABS("));
        assertEquals(lines(at(1, 37, "<EOF>"), at(1, 37, "<EOF>")), refusal("WITH c AS (SELECT a FROM t WHERE ABS("));
        assertEquals(lines(at(1, 26, "<EOF>"), at(1, 26, "<EOF>")), refusal("SELECT a FROM t WHERE t.f("));
        assertEquals(lines(at(1, 30, "<EOF>"), at(1, 30, "<EOF>")), refusal("SELECT a FROM t WHERE ABS(ABS("));
        assertEquals(lines(at(1, 32, "<EOF>"), at(1, 32, "<EOF>")), refusal("SELECT a FROM t WHERE UPPER(ABS("));
    }

    @Test
    public void anEmptyCallInASelectListStacksItsParenthesis() {
        assertEquals(lines(at(1, 22, "<EOF>"), at(1, 21, "("), at(1, 22, "<EOF>")), refusal("WITH c AS (SELECT ABS("));
        assertEquals(lines(at(1, 15, "<EOF>"), at(1, 14, "(")), refusal("SELECT 1 + ABS("));
        assertEquals(lines(at(1, 12, "<EOF>"), at(1, 11, "(")), refusal("SELECT (ABS("));
        assertEquals(lines(at(1, 14, "<EOF>"), at(1, 13, "(")), refusal("SELECT a, ABS("));
        assertEquals(lines(at(1, 26, "<EOF>"), at(1, 25, "(")), refusal("SELECT * FROM (SELECT ABS("));
        assertEquals(lines(at(1, 20, "<EOF>"), at(1, 19, "(")), refusal("SELECT CURRENT_DATE("));
        assertEquals(lines(at(1, 25, "<EOF>"), at(1, 24, "(")), refusal("INSERT INTO t SELECT ABS("));
        assertEquals(lines(at(1, 13, "<EOF>"), at(1, 12, "(")), refusal("SELECT COUNT("));
    }

    @Test
    public void aCallHoldingAnArgumentIsOneLine() {
        assertEquals(lines(at(1, 27, "<EOF>")), refusal("SELECT a FROM t WHERE ABS(1"));
        assertEquals(lines(at(1, 28, "<EOF>")), refusal("SELECT a FROM t WHERE ABS(1,"));
        assertEquals(lines(at(1, 27, "<EOF>")), refusal("SELECT a FROM t WHERE ABS(a"));
        assertEquals(lines(at(1, 16, "<EOF>")), refusal("SELECT ABS(ABS(1"));
        assertEquals(lines(at(1, 36, "<EOF>")), refusal("SELECT a FROM t WHERE COUNT(DISTINCT"));
        assertEquals(lines(at(1, 12, "<EOF>")), refusal("SELECT ABS(-"));
        assertEquals(lines(at(1, 30, "<EOF>")), refusal("SELECT a FROM t GROUP BY ABS(1"));
        assertEquals(lines(at(1, 12, "<EOF>")), refusal("SELECT ABS(1"));
        assertEquals(lines(at(1, 23, "<EOF>")), refusal("WITH c AS (SELECT ABS(1"));
    }

    @Test
    public void anEmptyCastIsRefusedAtTheTokenInsideIt() {
        assertEquals(lines(at(1, 12, "<EOF>")), refusal("SELECT CAST("));
        assertEquals(lines(at(1, 13, "<EOF>")), refusal("SELECT CAST(1"));
        assertEquals(lines(at(1, 16, "<EOF>")), refusal("SELECT CAST(1 AS"));
        assertEquals(lines(at(1, 20, "<EOF>")), refusal("SELECT CAST(1 AS INT"));
        assertEquals(lines(at(1, 16, "<EOF>")), refusal("SELECT TRY_CAST("));
        assertEquals(lines(at(1, 12, ")")), refusal("SELECT CAST()"));
        assertEquals(lines(at(1, 16, ")")), refusal("SELECT CAST(1 AS)"));
        assertEquals(lines(at(1, 27, "<EOF>")), refusal("SELECT a FROM t WHERE CAST("));
    }
}
