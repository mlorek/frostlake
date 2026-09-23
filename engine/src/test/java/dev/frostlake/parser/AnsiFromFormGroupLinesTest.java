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
 * A call in the FROM form no name accepts, standing in a select item inside parentheses or a CAST: after the FROM live
 * gives the enclosing constructs up from the inside out and reports nothing more until it has read a token again —
 * two groups around a name say nothing more, three refuse the third ')' after the FROM, and a number is passed over
 * where a name stops the passing. Back in step, a construct that cannot read the token it meets has it refused and is
 * given up from there (live-verified).
 */
public class AnsiFromFormGroupLinesTest extends BaseDatabaseTest {

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

    private static String at(final int position, final String token) {
        return at(1, position, token);
    }

    private static String at(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void groupsAroundTheCallAreGivenUpOneByOne() {
        assertEquals(lines(at(23, "FROM")), refusal("SELECT ((EXTRACT('wks' FROM d))) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(32, ")")), refusal("SELECT (((EXTRACT('wks' FROM d)))) FROM t"));
        assertEquals(lines(at(25, "FROM"), at(34, ")")), refusal("SELECT ((((EXTRACT('wks' FROM d))))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(30, ")")), refusal("SELECT ((EXTRACT('wks' FROM 2))) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(32, ")")), refusal("SELECT (((EXTRACT('wks' FROM 2)))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(32, ")")), refusal("SELECT ((EXTRACT('wks' FROM 'x'))) FROM t"));
        assertEquals(lines(at(21, "FROM"), at(28, ")")), refusal("SELECT ((SUBSTRING(b FROM 2))) FROM t"));
        assertEquals(lines(at(21, "FROM")), refusal("SELECT ((SUBSTRING(b FROM d))) FROM t"));
        assertEquals(lines(at(22, "FROM")), refusal("SELECT ((POSITION('a' FROM d))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(33, ")")), refusal("SELECT ((EXTRACT('wks' FROM NULL))) FROM t"));
        assertEquals(lines(at(23, "FROM")), refusal("SELECT ((EXTRACT('wks' FROM TRUE))) FROM t"));
    }

    @Test
    public void anOperatorCarriesTheValueOn() {
        assertEquals(lines(at(23, "FROM")), refusal("SELECT ((EXTRACT('wks' FROM d)) + 1) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(34, ")")), refusal("SELECT ((EXTRACT('wks' FROM d) + 1)) FROM t"));
        assertEquals(lines(at(27, "FROM")), refusal("SELECT (1 + (EXTRACT('wks' FROM d))) FROM t"));
        assertEquals(lines(at(23, "FROM")), refusal("SELECT ((EXTRACT('wks' FROM d))) + 1 FROM t"));
        assertEquals(lines(at(23, "FROM"), at(30, ")")), refusal("SELECT ((EXTRACT('wks' FROM 2)) + 1) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(36, ")")), refusal("SELECT (((EXTRACT('wks' FROM d)) + 1)) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(34, ")")), refusal("SELECT ((EXTRACT('wks' FROM d) * 2) + 1) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(35, ")")), refusal("SELECT ((EXTRACT('wks' FROM d)::INT)) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(31, ")")), refusal("SELECT (-(EXTRACT('wks' FROM 2))) FROM t"));
        assertEquals(lines(at(22, "FROM"), at(27, "2")), refusal("SELECT -EXTRACT('wks' FROM 2) FROM t"));
    }

    @Test
    public void theOperandIsReadUpToWhereItCanGoOn() {
        assertEquals(lines(at(23, "FROM"), at(30, "x")), refusal("SELECT ((EXTRACT('wks' FROM d x))) FROM t"));
        assertEquals(lines(at(24, "FROM")), refusal("SELECT (((EXTRACT('wks' FROM d x)))) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(33, "y")), refusal("SELECT (((EXTRACT('wks' FROM d x y)))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(29, ".")), refusal("SELECT ((EXTRACT('wks' FROM t.d))) FROM t"));
        assertEquals(lines(at(24, "FROM")), refusal("SELECT (((EXTRACT('wks' FROM t.d)))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(30, "+")), refusal("SELECT ((EXTRACT('wks' FROM d + 1))) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(35, ")")), refusal("SELECT (((EXTRACT('wks' FROM d + 1)))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(33, "(")), refusal("SELECT ((EXTRACT('wks' FROM UPPER(d)))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(29, "::")), refusal("SELECT ((EXTRACT('wks' FROM d::INT))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(30, "=")), refusal("SELECT ((EXTRACT('wks' FROM d = 1))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(34, ")")), refusal("SELECT ((EXTRACT('wks' FROM 2 = 1))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(37, ")")), refusal("SELECT ((EXTRACT('wks' FROM 2 IN (1)))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(36, ")")), refusal("SELECT ((EXTRACT('wks' FROM 'x' 'y'))) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(38, ")")), refusal("SELECT (((EXTRACT('wks' FROM 2 WHERE 1)))) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(35, ")")), refusal("SELECT (((EXTRACT('wks' FROM 2 AS x)))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(30, "AS")), refusal("SELECT ((EXTRACT('wks' FROM d AS x))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(32, ")")), refusal("SELECT ((EXTRACT('wks' FROM d, 1))) FROM t"));
    }

    @Test
    public void aForTailIsReadAndTheTokenAfterItRefused() {
        assertEquals(lines(at(21, "FROM"), at(32, "1")), refusal("SELECT ((SUBSTRING(b FROM 2 FOR 1))) FROM t"));
        assertEquals(lines(at(21, "FROM"), at(32, "1")), refusal("SELECT ((SUBSTRING(b FROM d FOR 1))) FROM t"));
        assertEquals(lines(at(22, "FROM"), at(33, "1")), refusal("SELECT (((SUBSTRING(b FROM d FOR 1)))) FROM t"));
    }

    @Test
    public void whatFollowsTheItemAndTheEndOfInput() {
        assertEquals(lines(at(23, "FROM")), refusal("SELECT ((EXTRACT('wks' FROM d))) x y FROM t"));
        assertEquals(lines(at(23, "FROM"), at(30, ")")), refusal("SELECT ((EXTRACT('wks' FROM 2))) x FROM t WHERE a = 1"));
        assertEquals(lines(at(26, "FROM"), at(33, ")")), refusal("SELECT a, ((EXTRACT('wks' FROM 2))) FROM t"));
        assertEquals(lines(at(23, "FROM"), at(30, ")")), refusal("SELECT ((EXTRACT('wks' FROM 2)))"));
        assertEquals(lines(at(23, "FROM")), refusal("SELECT ((EXTRACT('wks' FROM 2"));
        assertEquals(lines(at(23, "FROM")), refusal("SELECT ((EXTRACT('wks' FROM"));
        assertEquals(lines(at(22, "FROM"), at(26, "<EOF>")), refusal("SELECT (EXTRACT('wks' FROM"));
        assertEquals(lines(at(19, "FROM"), at(23, "<EOF>")), refusal("SELECT SUBSTRING(b FROM"));
    }

    @Test
    public void aMissingOperandLeavesTheParenthesesToTheGroups() {
        assertEquals(lines(at(23, "FROM"), at(30, ")")), refusal("SELECT ((EXTRACT('wks' FROM ))) FROM t"));
        assertEquals(lines(at(22, "FROM")), refusal("SELECT (EXTRACT('wks' FROM )) FROM t"));
        assertEquals(lines(at(21, "FROM"), at(26, ")")), refusal("SELECT EXTRACT('wks' FROM ) FROM t"));
        assertEquals(lines(at(23, "FROM")), refusal("SELECT ((EXTRACT('wks' FROM CASE))) FROM t"));
    }

    @Test
    public void aCastIsGivenUpAtItsAs() {
        assertEquals(lines(at(26, "FROM"), at(40, ")")), refusal("SELECT CAST(EXTRACT('wks' FROM 2) AS INT) FROM t"));
        assertEquals(lines(at(27, "FROM"), at(41, ")")), refusal("SELECT (CAST(EXTRACT('wks' FROM d) AS INT)) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(42, ")")), refusal("SELECT ((CAST(EXTRACT('wks' FROM d) AS INT))) FROM t"));
        assertEquals(lines(at(27, "FROM"), at(41, ")")), refusal("SELECT (CAST(EXTRACT('wks' FROM 2) AS INT)) FROM t"));
        assertEquals(lines(at(27, "FROM")), refusal("SELECT CAST((EXTRACT('wks' FROM 2)) AS INT) FROM t"));
        assertEquals(lines(at(27, "FROM"), at(42, ")")), refusal("SELECT CAST((EXTRACT('wks' FROM d)) AS INT) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(44, ")")), refusal("SELECT CAST(EXTRACT('wks' FROM 2) + 1 AS INT) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(32, ")")), refusal("SELECT CAST(EXTRACT('wks' FROM d) + 1 AS INT) FROM t"));
        assertEquals(lines(at(26, "FROM")), refusal("SELECT CAST(EXTRACT('wks' FROM 2)) FROM t"));
        assertEquals(lines(at(29, "FROM"), at(43, ")")), refusal("SELECT a, CAST(EXTRACT('wks' FROM 2) AS INT), b FROM t"));
        assertEquals(lines(at(30, "FROM"), at(44, ")")), refusal("SELECT TRY_CAST(EXTRACT('wks' FROM 2) AS INT) FROM t"));
        assertEquals(lines(at(30, "FROM"), at(36, ")")), refusal("SELECT TRY_CAST(EXTRACT('wks' FROM d) AS INT) FROM t"));
        assertEquals(lines(at(31, "FROM"), at(45, ")")), refusal("SELECT (TRY_CAST(EXTRACT('wks' FROM d) AS INT)) FROM t"));
        assertEquals(lines(at(31, "FROM")), refusal("SELECT TRY_CAST((EXTRACT('wks' FROM 2)) AS INT) FROM t"));
    }

    @Test
    public void aCastBackInStepRefusesAParenthesis() {
        assertEquals(lines(at(28, "FROM"), at(35, ")"), at(44, ")")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM 2))) AS INT) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(37, ")"), at(46, ")")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM 'x'))) AS INT) FROM t"));
        assertEquals(lines(at(32, "FROM"), at(39, ")"), at(48, ")")),
            refusal("SELECT TRY_CAST(((EXTRACT('wks' FROM 2))) AS INT) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(35, ")"), at(40, "AS")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM 2))) y AS INT) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(35, ")"), at(48, ")")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM 2))) + 1 AS INT) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(35, ")")), refusal("SELECT CAST(((EXTRACT('wks' FROM 2)))) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(35, ")")), refusal("SELECT CAST(((EXTRACT('wks' FROM 2))) AS INT"));
        assertEquals(lines(at(29, "FROM"), at(37, ")"), at(46, ")")),
            refusal("SELECT CAST((((EXTRACT('wks' FROM 2)))) AS INT) FROM t"));
        assertEquals(lines(at(31, "FROM"), at(38, ")"), at(47, ")")),
            refusal("SELECT a, CAST(((EXTRACT('wks' FROM 2))) AS INT) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(33, ")"), at(42, ")")),
            refusal("SELECT CAST(((SUBSTRING(b FROM 2))) AS INT) FROM t"));
        assertEquals(lines(at(29, "FROM"), at(36, ")"), at(37, ")")),
            refusal("SELECT (CAST(((EXTRACT('wks' FROM 2))) AS INT)) FROM t"));
        assertEquals(lines(at(29, "FROM"), at(36, ")"), at(37, ")")),
            refusal("SELECT (CAST(((EXTRACT('wks' FROM 2))) AS INT) + 1) FROM t"));
        assertEquals(lines(at(30, "FROM"), at(37, ")"), at(46, ")")),
            refusal("SELECT ((CAST(((EXTRACT('wks' FROM 2))) AS INT))) FROM t"));
        assertEquals(lines(at(31, "FROM"), at(38, ")"), at(41, "AS"), at(47, ")")),
            refusal("SELECT (((CAST(((EXTRACT('wks' FROM 2))) AS INT)))) FROM t"));
        assertEquals(lines(at(33, "FROM"), at(40, ")"), at(41, ")")),
            refusal("SELECT (TRY_CAST(((EXTRACT('wks' FROM 2))) AS INT)) FROM t"));
    }

    @Test
    public void aGroupBackInStepRefusesAsOrAName() {
        assertEquals(lines(at(28, "FROM"), at(36, "AS"), at(42, ")")),
            refusal("SELECT ((CAST(EXTRACT('wks' FROM 2) AS INT))) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(38, "AS"), at(44, ")")),
            refusal("SELECT ((CAST(EXTRACT('wks' FROM 'x') AS INT))) FROM t"));
        assertEquals(lines(at(32, "FROM"), at(40, "AS"), at(46, ")")),
            refusal("SELECT ((TRY_CAST(EXTRACT('wks' FROM 2) AS INT))) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(36, "AS"), at(42, ")")),
            refusal("SELECT ((CAST(EXTRACT('wks' FROM 2) AS INT)) + 1) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(34, "AS"), at(40, ")")),
            refusal("SELECT ((CAST(SUBSTRING(b FROM 2) AS INT))) FROM t"));
        assertEquals(lines(at(29, "FROM"), at(37, "AS"), at(43, ")")),
            refusal("SELECT (((CAST(EXTRACT('wks' FROM 2) AS INT)))) FROM t"));
        assertEquals(lines(at(30, "FROM"), at(38, "AS")),
            refusal("SELECT ((((CAST(EXTRACT('wks' FROM 2) AS INT))))) FROM t"));
        assertEquals(lines(at(31, "FROM"), at(39, "AS"), at(47, ")")),
            refusal("SELECT (((((CAST(EXTRACT('wks' FROM 2) AS INT)))))) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(36, "y"), at(38, "AS")),
            refusal("SELECT ((CAST(EXTRACT('wks' FROM 2) y AS INT))) FROM t"));
        assertEquals(lines(at(29, "FROM"), at(37, "y"), at(39, "AS")),
            refusal("SELECT (((CAST(EXTRACT('wks' FROM 2) y AS INT)))) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(36, "y")), refusal("SELECT ((CAST(EXTRACT('wks' FROM 2) y))) FROM t"));
        assertEquals(lines(at(29, "FROM"), at(37, "y"), at(40, ")")),
            refusal("SELECT (((CAST(EXTRACT('wks' FROM 2) y)))) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(36, "y"), at(38, "z")),
            refusal("SELECT ((CAST(EXTRACT('wks' FROM 2) y z))) FROM t"));
        assertEquals(lines(at(32, "FROM"), at(40, "y"), at(42, "AS")),
            refusal("SELECT ((TRY_CAST(EXTRACT('wks' FROM 2) y AS INT))) FROM t"));
    }

    @Test
    public void aCastRefusesAParenthesisAfterAnOperator() {
        assertEquals(lines(at(28, "FROM"), at(38, ")"), at(48, ")")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM d + 1))) AS INT) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(38, ")"), at(48, ")")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM d * 2))) AS INT) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(41, ")"), at(55, ")")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM d || 'x'))) AS VARCHAR) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(38, ")"), at(52, ")")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM d + 1))) + 2 AS INT) FROM t"));
        assertEquals(lines(at(32, "FROM"), at(42, ")"), at(52, ")")),
            refusal("SELECT TRY_CAST(((EXTRACT('wks' FROM d + 1))) AS INT) FROM t"));
        assertEquals(lines(at(29, "FROM"), at(39, ")"), at(40, ")")),
            refusal("SELECT (CAST(((EXTRACT('wks' FROM d + 1))) AS INT)) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(35, "y")), refusal("SELECT (((EXTRACT('wks' FROM d + 1 y)))) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(39, "y"), at(41, ")")),
            refusal("SELECT CAST(((EXTRACT('wks' FROM d + 1 y))) AS INT) FROM t"));
    }

    @Test
    public void linesKeepTheirPlaceAcrossLineBreaks() {
        assertEquals(lines(at(2, 16, "FROM"), at(2, 24, ")")),
            refusal("""
                SELECT (
                ((EXTRACT('wks' FROM 2)))) FROM t"""));
        assertEquals(lines(at(2, 16, "FROM"), at(2, 27, ")")),
            refusal("""
                SELECT (
                ((EXTRACT('wks' FROM d + 1)))) FROM t"""));
        assertEquals(lines(at(2, 16, "FROM"), at(2, 23, ")"), at(2, 32, ")")),
            refusal("""
                SELECT CAST(
                ((EXTRACT('wks' FROM 2))) AS INT) FROM t"""));
        assertEquals(lines(at(3, 23, "FROM"), at(4, 4, ")")),
            refusal("""
                SELECT (
                    (
                        (EXTRACT('wks' FROM 2))
                    )
                ) FROM t"""));
        assertEquals(lines(at(3, 20, "FROM"), at(3, 30, ")"), at(4, 10, ")")),
            refusal("""
                SELECT
                  CAST(
                    ((EXTRACT('wks' FROM d + 1)))
                    AS INT)
                FROM t"""));
        assertEquals(lines(at(3, 16, "FROM"), at(3, 24, ")")),
            refusal("""
                SELECT (

                ((EXTRACT('wks' FROM 2)))) FROM t"""));
        assertEquals(lines(at(2, 18, "FROM"), at(2, 25, ")"), at(3, 8, ")")),
            refusal("""
                SELECT CAST(
                  ((EXTRACT('wks' FROM 2)))
                  AS INT)
                FROM t"""));
        assertEquals(lines(at(2, 23, "FROM"), at(2, 30, ")"), at(2, 31, ")")),
            refusal("""
                SELECT (
                  CAST(((EXTRACT('wks' FROM 2))) AS INT)
                ) FROM t"""));
        assertEquals(lines(at(2, 21, "FROM"), at(2, 29, "AS"), at(2, 35, ")")),
            refusal("""
                SELECT ((
                  CAST(EXTRACT('wks' FROM 2) AS INT)
                )) FROM t"""));
        assertEquals(lines(at(2, 18, "FROM"), at(2, 27, ")"), at(3, 8, ")")),
            refusal("""
                SELECT TRY_CAST(
                  ((EXTRACT('wks' FROM 'x')))
                  AS INT)
                FROM t"""));
        assertEquals(lines(at(2, 19, "FROM"), at(2, 27, ")")),
            refusal("""
                SELECT
                  (((EXTRACT('wks' FROM 2))))
                FROM t"""));
        assertEquals(lines(at(2, 18, "FROM")),
            refusal("""
                SELECT
                  ((EXTRACT('wks' FROM d)))
                FROM t"""));
        assertEquals(lines(at(2, 17, "FROM")),
            refusal("""
                SELECT CAST(
                  (EXTRACT('wks' FROM 2)) AS INT) FROM t"""));
        assertEquals(lines(at(2, 14, "FROM"), at(2, 22, ")")),
            refusal("""
                SELECT (((
                EXTRACT('wks' FROM 2)))) FROM t"""));
        assertEquals(lines(at(2, 16, "FROM"), at(2, 24, ")")),
            refusal("""
                SELECT (
                ((EXTRACT('wks' FROM d)))) FROM t"""));
    }

    @Test
    public void aCastMissingItsTypeHasItsFromRefusedFirst() {
        assertEquals(lines(at(26, "FROM"), at(32, ")")), refusal("SELECT CAST(EXTRACT('wks' FROM d) AS) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(36, ")")), refusal("SELECT CAST(EXTRACT('wks' FROM 2) AS) FROM t"));
    }
}
