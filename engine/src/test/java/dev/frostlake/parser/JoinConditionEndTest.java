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
 * The input ending inside a join's ON or USING condition is refused at the ON or the USING itself, and so is a
 * statement its semicolon ends there, which ends the report; a MERGE's ON and a later clause are not such a condition
 * (live-verified).
 */
public class JoinConditionEndTest extends BaseDatabaseTest {

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
    public void theKeywordIsRefused() {
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON ABS(1"));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON a = 1 AND ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON (b"));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON a ="));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON"));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON ABS(1) ="));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON a = ABS("));
        assertEquals(lines(at(1, 25, "USING")), refusal("SELECT a FROM t JOIN t u USING (a"));
        assertEquals(lines(at(1, 47, "ON")), refusal("SELECT a FROM t JOIN t u ON t.a = u.a JOIN t v ON ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON UPPER(ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON ABS(1, 2"));
        assertEquals(lines(at(1, 30, "ON")), refusal("SELECT a FROM t LEFT JOIN t u ON ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON ABS(1) = ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON a = 1 AND"));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON a IN (1"));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON ABS(1"));
        assertEquals(lines(at(1, 36, "ON")), refusal("SELECT a FROM t JOIN (SELECT 1 x) u ON ABS("));
        assertEquals(lines(at(1, 40, "ON")), refusal("SELECT a FROM (SELECT a FROM t JOIN t u ON ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON a IN (SELECT a FROM t WHERE ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON (SELECT ABS("));
        assertEquals(lines(at(1, 25, "ON")), refusal("SELECT a FROM t JOIN t u ON ABS(1) AND ABS(\n"));
    }

    @Test
    public void aStatementsSemicolonEndsTheConditionAsTheInputsEndDoes() {
        assertEquals(lines(at(1, 23, "ON")), refusal("SELECT 1 FROM t JOIN u ON;"));
        assertEquals(lines(at(1, 23, "ON")), refusal("SELECT 1 FROM t JOIN u ON a =;"));
        assertEquals(lines(at(1, 23, "ON")), refusal("SELECT 1 FROM t JOIN u ON; SELECT 1 x y"));
        assertEquals(lines(at(1, 23, "ON")), refusal("SELECT 1 FROM t JOIN u ON a =; SELECT 1 x y"));
        assertEquals(lines(at(1, 23, "ON")), refusal("SELECT 1 FROM t JOIN u ON a = 1 AND; SELECT 1 x y"));
        assertEquals(lines(at(1, 23, "ON")), refusal("SELECT 1 FROM t JOIN u ON; SELECT 2; SELECT 3 x y"));
        assertEquals(lines(at(1, 34, "ON")), refusal("SELECT 1 FROM t LEFT OUTER JOIN u ON a =; SELECT 1 x y"));
        assertEquals(lines(at(1, 39, "ON")), refusal("SELECT 1 FROM t JOIN u ON a = b JOIN v ON; SELECT 1 x y"));
        assertEquals(lines(at(1, 29, "ON")), refusal("BEGIN SELECT 1 FROM t JOIN u ON; END"));
        assertEquals(lines(at(1, 29, "ON")), refusal("BEGIN SELECT 1 FROM t JOIN u ON; RETURN 1; END"));
    }

    @Test
    public void aLaterStatementEndingInAConditionIsRefusedAtItsKeywordAndEndsTheReport() {
        assertEquals(lines(at(1, 10, ";"), at(1, 35, "ON")),
            refusal("SELECT 1 +; SELECT 1 FROM t JOIN u ON; SELECT 1 x y"));
        assertEquals(lines(at(1, 10, ";"), at(1, 35, "ON")),
            refusal("DROP TABLE; SELECT 1 FROM t JOIN u ON a =; SELECT 1 x y"));
        assertEquals(lines(at(1, 10, ";"), at(1, 35, "ON")), refusal("SELECT 1 +; SELECT 1 FROM t JOIN u ON"));
        assertEquals(lines(at(1, 10, ";"), at(1, 35, "ON")), refusal("DROP TABLE; SELECT 1 FROM t JOIN u ON a ="));
    }

    @Test
    public void aFinishedConditionIsNoneOfThat() {
        assertEquals(lines(at(1, 28, "b")), refusal("SELECT 1 FROM t JOIN u ON a b; SELECT 1 x y"));
        assertEquals(lines(at(1, 37, ";"), at(1, 50, "y")),
            refusal("SELECT 1 FROM t JOIN u ON a = b WHERE; SELECT 1 x y"));
    }

    @Test
    public void aLaterClauseIsNoCondition() {
        assertEquals(lines(at(1, 31, "<EOF>"), at(1, 31, "<EOF>")), refusal("SELECT a FROM t, t u WHERE ABS("));
        assertEquals(lines(at(1, 43, "<EOF>"), at(1, 43, "<EOF>")),
            refusal("SELECT a FROM t NATURAL JOIN t u WHERE ABS("));
        assertEquals(lines(at(1, 43, "<EOF>"), at(1, 43, "<EOF>")),
            refusal("SELECT a FROM t JOIN t u ON TRUE WHERE ABS("));
    }
}
