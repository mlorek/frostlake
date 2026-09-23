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
 * POSITION's first argument is a value below the comparison operators, so a predicate there is a syntax error
 * at its operator, with the closing parenthesis live's recovery trips over stacked after it (live-verified).
 * Frostlake parsed the argument and refused its type instead. IS NULL, IS DISTINCT FROM and a parenthesized
 * predicate stay values; a leading NOT or EXISTS stacks POSITION's opening parenthesis instead.
 */
public class PositionNeedleSyntaxTest extends BaseDatabaseTest {

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
    public void aComparisonLevelOperatorInTheFirstArgumentIsASyntaxError() {
        assertEquals(lines(at(1, 18, "="), at(1, 26, ")")), refusal("SELECT POSITION(1 = 1, 'x')"));
        assertEquals(lines(at(1, 18, "<"), at(1, 26, ")")), refusal("SELECT POSITION(1 < 2, 'x')"));
        assertEquals(lines(at(1, 18, "!="), at(1, 27, ")")), refusal("SELECT POSITION(1 != 2, 'x')"));
        assertEquals(lines(at(1, 20, "LIKE"), at(1, 33, ")")), refusal("SELECT POSITION('a' LIKE 'a', 'x')"));
        assertEquals(lines(at(1, 20, "NOT"), at(1, 37, ")")), refusal("SELECT POSITION('a' NOT LIKE 'a', 'x')"));
        assertEquals(lines(at(1, 20, "ILIKE"), at(1, 34, ")")), refusal("SELECT POSITION('a' ILIKE 'a', 'x')"));
        assertEquals(lines(at(1, 20, "RLIKE"), at(1, 34, ")")), refusal("SELECT POSITION('a' RLIKE 'a', 'x')"));
        assertEquals(lines(at(1, 18, "BETWEEN"), at(1, 38, ")")), refusal("SELECT POSITION(1 BETWEEN 0 AND 2, 'x')"));
        assertEquals(lines(at(1, 21, "AND"), at(1, 35, ")")), refusal("SELECT POSITION(TRUE AND FALSE, 'x')"));
        assertEquals(lines(at(1, 21, "OR"), at(1, 34, ")")), refusal("SELECT POSITION(TRUE OR FALSE, 'x')"));
        assertEquals(lines(at(1, 22, "="), at(1, 30, ")")), refusal("SELECT POSITION(1 + 1 = 2, 'x')"));
        assertEquals(lines(at(1, 18, "="), at(1, 30, ")")), refusal("SELECT POSITION(1 = 1 = 1, 'x')"));
        assertEquals(lines(at(1, 18, "="), at(1, 35, ")")), refusal("SELECT POSITION(1 = 1 AND TRUE, 'x')"));
        assertEquals(lines(at(1, 26, "="), at(1, 37, ")")), refusal("SELECT POSITION(1 IS NULL = TRUE, 'x')"));
        assertEquals(lines(at(1, 18, "="), at(1, 39, ")")), refusal("SELECT POSITION(1 = ANY (SELECT 1), 'x')"));
        assertEquals(lines(at(1, 18, "="), at(1, 21, ")")), refusal("SELECT POSITION(1 = 1)"));
        assertEquals(lines(at(1, 18, "="), at(1, 29, ")")), refusal("SELECT POSITION(1 = 1, 'x', 1)"));
        assertEquals(lines(at(1, 18, "="), at(1, 26, ")")), refusal("SELECT position(1 = 1, 'x')"));
    }

    @Test
    public void theStackedParenthesisIsTheOneRecoveryTripsOver() {
        assertEquals(lines(at(1, 18, "="), at(1, 26, ")")), refusal("SELECT POSITION(1 = 1, 'x') + 1"));
        assertEquals(lines(at(1, 24, "="), at(1, 33, ")")), refusal("SELECT UPPER(POSITION(1 = 1, 'x'))"));
        assertEquals(lines(at(1, 42, "="), at(1, 50, ")")), refusal("SELECT POSITION('x', 'y') = 0, POSITION(1 = 1, 'x')"));
        assertEquals(lines(at(1, 18, "="), at(2, 0, ")")), refusal("SELECT POSITION(1 = 1, 'x'\n)"));
        assertEquals(lines(at(2, 11, "="), at(2, 19, ")")), refusal("SELECT\nPOSITION(1 = 1, 'x')"));
        assertEquals(lines(at(1, 16, "NOT"), at(1, 15, "(")), refusal("SELECT POSITION(NOT TRUE, 'x')"));
        assertEquals(lines(at(1, 16, "EXISTS"), at(1, 15, "(")), refusal("SELECT POSITION(EXISTS (SELECT 1), 'x')"));
        assertEquals(lines(at(1, 28, ","), at(1, 33, ")")), refusal("SELECT POSITION('a' IN 'abc', 'x')"));
        assertEquals(lines(at(1, 27, ","), at(1, 32, ")")), refusal("SELECT POSITION(1 IN (1, 2), 'x')"));
    }

    @Test
    public void valuesAtThatLevelStillReachTheArgumentTypes() {
        final String booleanNeedle = "SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'POSITION': (BOOLEAN, VARCHAR(1))";
        assertEquals(booleanNeedle, refusal("SELECT POSITION(NULL IS NULL, 'x')"));
        assertEquals(booleanNeedle, refusal("SELECT POSITION((1 = 1), 'x')"));
        assertEquals(booleanNeedle, refusal("SELECT POSITION(1 IS NOT DISTINCT FROM 1, 'x')"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'POSITION': (VARCHAR(1), BOOLEAN)",
            refusal("SELECT POSITION('x', 1 = 1)"));
        assertEquals(0L, ((Number) engine.executeQuery("""
            SELECT POSITION(CASE WHEN 1 = 1 THEN 'a' END, 'x') + POSITION(IFF(1 = 1, 'a', 'b'), 'x')
                + POSITION('a' || 'b', 'x')""").getRows().get(0).getValue(0)).longValue());
    }
}
