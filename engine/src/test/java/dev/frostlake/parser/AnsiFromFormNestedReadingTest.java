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
 * A call in the FROM form no name accepts, standing inside another call, inside parentheses of its own or in a WHERE:
 * live refuses the FROM and then reads the call's closing parenthesis as closing the bracket around it, and a FOR tail
 * is refused after its FOR. In a WHERE a CAST around the call reads on from its AS instead (live-verified).
 */
public class AnsiFromFormNestedReadingTest extends BaseDatabaseTest {

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
    public void insideAnotherCallTheCallsParenthesisClosesThatCall() {
        assertEquals(lines(at(1, 28, "FROM"), at(1, 40, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 40, ")")), refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x')"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 45, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x', 'y') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 40, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x') + 1 FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 40, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x'), 2 FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 42, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 1 + 2) FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 47, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), UPPER('x')) FROM t"));
        assertEquals(lines(at(1, 27, "FROM"), at(1, 39, ")")),
            refusal("SELECT UPPER(EXTRACT('wks' FROM d), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 47, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d) || 'y', 'x') FROM t"));
        assertEquals(lines(at(1, 35, "FROM"), at(1, 53, ")")),
            refusal("SELECT CONCAT(CONCAT(EXTRACT('wks' FROM d), 'x'), 'y') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 40, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x') AS y FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 38, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), d) FROM t"));
        assertEquals(lines(at(1, 30, "FROM"), at(1, 40, ")")),
            refusal("SELECT COALESCE(EXTRACT('wks' FROM d), 1) FROM t"));
        assertEquals(lines(at(1, 31, "FROM"), at(1, 41, ")")),
            refusal("SELECT IFF(TRUE, EXTRACT('wks' FROM d), 1) FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 47, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x' || 'y') FROM t"));
        assertEquals(lines(at(1, 37, "FROM"), at(1, 50, ")")),
            refusal("SELECT ARRAY_CONSTRUCT(EXTRACT('wks' FROM d), 1, 2) FROM t"));
        assertEquals(lines(at(1, 33, "FROM"), at(1, 45, ")")),
            refusal("SELECT CONCAT('x', EXTRACT('wks' FROM d), 'y') FROM t"));
        assertEquals(lines(at(1, 26, "FROM"), at(1, 36, ")")), refusal("SELECT LEFT(EXTRACT('wks' FROM d), 2) FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 39, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), -1) FROM t"));
        assertEquals(lines(at(1, 34, "FROM"), at(1, 47, ")")),
            refusal("SELECT CONCAT(UPPER(EXTRACT('wks' FROM d)), 'x') FROM t"));
        assertEquals(lines(at(1, 34, "FROM"), at(1, 47, ")")),
            refusal("SELECT UPPER(CONCAT(EXTRACT('wks' FROM d), 'x')) FROM t"));
        assertEquals(lines(at(1, 40, "FROM"), at(1, 53, ")")),
            refusal("SELECT CONCAT(CONCAT('x', EXTRACT('wks' FROM d)), 'y') FROM t"));
        assertEquals(lines(at(1, 35, "FROM"), at(1, 48, ")")),
            refusal("SELECT CONCAT(CONCAT(EXTRACT('wks' FROM d), 'x')) FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 40, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x') || 'z' FROM t"));
        assertEquals(lines(at(1, 25, "FROM"), at(1, 38, ")")),
            refusal("SELECT IFF(EXTRACT('wks' FROM d), 1, 2) FROM t"));
    }

    @Test
    public void aFaultRightAfterTheEnclosingCallIsSilent() {
        assertEquals(lines(at(1, 33, "FROM")), refusal("SELECT CONCAT('x', EXTRACT('wks' FROM d)) FROM t"));
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d)) FROM t"));
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d)) || 'x' FROM t"));
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d)), 'x' FROM t"));
        assertEquals(lines(at(1, 33, "FROM"), at(1, 41, ")")),
            refusal("SELECT UPPER(UPPER(EXTRACT('wks' FROM d))) FROM t"));
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d)) x FROM t"));
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d)) FROM t WHERE TRUE"));
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d)), 1 FROM t"));
    }

    @Test
    public void parenthesesOfItsOwnInACallAreDroppedWithIt() {
        assertEquals(lines(at(1, 29, "FROM"), at(1, 36, ")")),
            refusal("SELECT CONCAT((EXTRACT('wks' FROM d)), 'x') FROM t"));
        assertEquals(lines(at(1, 29, "FROM"), at(1, 36, ")")),
            refusal("SELECT CONCAT((EXTRACT('wks' FROM d))) FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 35, ")")), refusal("SELECT UPPER((EXTRACT('wks' FROM d))) FROM t"));
        assertEquals(lines(at(1, 29, "FROM"), at(1, 36, ")")),
            refusal("SELECT CONCAT((EXTRACT('wks' FROM d)), 'x', 'y') FROM t"));
        assertEquals(lines(at(1, 30, "FROM"), at(1, 38, ")")),
            refusal("SELECT CONCAT(((EXTRACT('wks' FROM d))), 'x') FROM t"));
        assertEquals(lines(at(1, 29, "FROM"), at(1, 40, ")")),
            refusal("SELECT CONCAT((EXTRACT('wks' FROM d) + 1), 'x') FROM t"));
    }

    @Test
    public void aNestedOperandIsOneToken() {
        assertEquals(lines(at(1, 26, "FROM"), at(1, 38, ")")),
            refusal("SELECT CONCAT(SUBSTRING(b FROM 2), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 35, "+")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d + 1), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 42, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM 'x'), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 34, ".")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d.e), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 35, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM -1), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 35, "x")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d x), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 38, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM 2 + 1), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 38, "(")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM UPPER(d)), 'x') FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 35, "||")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d || 'x'), 'x') FROM t"));
    }

    @Test
    public void aForTailIsRefusedAfterItsFor() {
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 30, "1")),
            refusal("SELECT SUBSTRING(b FROM 2 FOR 1) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 30, "1")),
            refusal("SELECT SUBSTRING(b FROM 2 FOR 1)"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 30, "1")), refusal("SELECT SUBSTRING(b FROM d FOR 1) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 30, "1")),
            refusal("SELECT SUBSTRING(b FROM 2 FOR 1 FOR 2) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 30, "b")),
            refusal("SELECT SUBSTRING(b FROM 2 FOR b) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 25, "2"), at(1, 32, "1")),
            refusal("SELECT SUBSTRING(b FROM (2) FOR 1) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 30, "1")),
            refusal("SELECT SUBSTRING(b FROM 2 FOR 1 + 1) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 34, "1")),
            refusal("SELECT SUBSTRING(b FROM 2 + 1 FOR 1) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "-"), at(1, 31, "1")),
            refusal("SELECT SUBSTRING(b FROM -2 FOR 1) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 30, "'x'")),
            refusal("SELECT SUBSTRING(b FROM 2 FOR 'x') FROM t"));
        assertEquals(lines(at(1, 16, "FROM"), at(1, 21, "2"), at(1, 27, "1")),
            refusal("SELECT TRIM(' ' FROM 2 FOR 1) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 26, "2"), at(1, 32, "1")),
            refusal("SELECT EXTRACT('wks' FROM 2 FOR 1) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 30, "d")),
            refusal("SELECT SUBSTRING(b FROM 2 FOR d) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2"), at(1, 30, "1")),
            refusal("SELECT SUBSTRING(b FROM 2 FOR 1) x FROM t"));
        assertEquals(lines(at(1, 26, "FROM"), at(1, 37, "1")),
            refusal("SELECT CONCAT(SUBSTRING(b FROM 2 FOR 1), 'x') FROM t"));
        assertEquals(lines(at(1, 25, "FROM"), at(1, 36, "1")),
            refusal("SELECT UPPER(SUBSTRING(b FROM 2 FOR 1)) FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 39, "1")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d FOR 1), 'x') FROM t"));
    }

    @Test
    public void inAWhereTheCallsParenthesisClosesItsBracket() {
        assertEquals(lines(at(1, 43, "FROM"), at(1, 50, ",")),
            refusal("SELECT 1 FROM t WHERE CONCAT(EXTRACT('wks' FROM d), 'x') = 'a'"));
        assertEquals(lines(at(1, 42, "FROM"), at(1, 49, ",")),
            refusal("SELECT 1 FROM t WHERE UPPER(EXTRACT('wks' FROM d), 'x') = 'a'"));
        assertEquals(lines(at(1, 43, "FROM"), at(1, 50, ",")),
            refusal("SELECT 1 FROM t WHERE CONCAT(EXTRACT('wks' FROM d), 'x', 'y') = 'a'"));
        assertEquals(lines(at(1, 48, "FROM"), at(1, 55, ")")),
            refusal("SELECT 1 FROM t WHERE CONCAT('x', EXTRACT('wks' FROM d)) = 'a'"));
        assertEquals(lines(at(1, 37, "FROM"), at(1, 44, ")")),
            refusal("SELECT 1 FROM t WHERE (EXTRACT('wks' FROM d)) = 1"));
        assertEquals(lines(at(1, 44, "FROM"), at(1, 52, ",")),
            refusal("SELECT 1 FROM t WHERE CONCAT((EXTRACT('wks' FROM d)), 'x') = 'a'"));
        assertEquals(lines(at(1, 43, "FROM"), at(1, 54, ",")),
            refusal("SELECT 1 FROM t WHERE CONCAT(EXTRACT('wks' FROM d + 1), 'x') = 'a'"));
        assertEquals(lines(at(1, 42, "FROM"), at(1, 49, ")")),
            refusal("SELECT 1 FROM t WHERE UPPER(EXTRACT('wks' FROM d)) = 'a'"));
        assertEquals(lines(at(1, 37, "FROM"), at(1, 48, ")")),
            refusal("SELECT 1 FROM t WHERE (EXTRACT('wks' FROM d) + 1) = 1"));
    }

    @Test
    public void inAWhereACastReadsOnFromItsAs() {
        assertEquals(lines(at(1, 41, "FROM")), refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1"));
        assertEquals(lines(at(1, 41, "FROM")), refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM 2) AS INT) = 1"));
        assertEquals(lines(at(1, 45, "FROM")),
            refusal("SELECT a FROM t WHERE TRY_CAST(EXTRACT('wks' FROM d) AS INT) = 1"));
        assertEquals(lines(at(1, 45, "FROM")),
            refusal("SELECT a FROM t WHERE TRY_CAST(EXTRACT('wks' FROM 2) AS INT) = 1"));
        assertEquals(lines(at(1, 43, "FROM")),
            refusal("SELECT a FROM t WHERE TRY_CAST(SUBSTRING(b FROM 2) AS INT) = 1"));
        assertEquals(lines(at(1, 55, "FROM")),
            refusal("SELECT a FROM t WHERE a = 1 AND TRY_CAST(EXTRACT('wks' FROM d) AS INT) = 1"));
        assertEquals(lines(at(1, 46, "FROM")),
            refusal("SELECT a FROM t WHERE (TRY_CAST(EXTRACT('wks' FROM d) AS INT)) = 1"));
        assertEquals(lines(at(1, 51, "FROM")),
            refusal("SELECT a FROM t WHERE UPPER(TRY_CAST(EXTRACT('wks' FROM d) AS INT)) = 1"));
        assertEquals(lines(at(1, 42, "FROM")),
            refusal("SELECT a FROM t WHERE (CAST(EXTRACT('wks' FROM d) AS INT)) = 1"));
        assertEquals(lines(at(1, 43, "FROM")),
            refusal("SELECT a FROM t WHERE ((CAST(EXTRACT('wks' FROM d) AS INT))) = 1"));
        assertEquals(lines(at(1, 41, "FROM")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) + 1 AS INT) = 1"));
        assertEquals(lines(at(1, 45, "FROM")),
            refusal("SELECT a FROM t WHERE TRY_CAST(EXTRACT('wks' FROM d) AS INT) IS NULL"));
        assertEquals(lines(at(1, 45, "FROM")),
            refusal("SELECT a FROM t WHERE NOT CAST(EXTRACT('wks' FROM d) AS BOOLEAN)"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 56, "x")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT x) = 1"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 56, "=")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT = 1"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 72, "x")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1 ORDER BY a x y"));
        assertEquals(lines(at(1, 45, "FROM"), at(1, 76, "x")),
            refusal("SELECT a FROM t WHERE TRY_CAST(EXTRACT('wks' FROM d) AS INT) = 1 GROUP BY a x"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 71, "x")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1 QUALIFY a x"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 78, "y")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1 UNION SELECT 1 x y"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 73, "y")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1; SELECT 1 x y"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 71, ",")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS INT) = 1 AND CAST(1, 2) = 1"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 47, "d"), at(1, 74, "x")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM (d)) AS INT) = 1 ORDER BY a x"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 52, "d")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM UPPER(d)) AS INT) = 1"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 47, "(")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM ((d))) AS INT) = 1"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 47, "1")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM (1)) AS INT) = 1"));
        assertEquals(lines(at(1, 39, "FROM"), at(1, 45, "2")),
            refusal("SELECT a FROM t WHERE CAST(SUBSTRING(b FROM (2)) AS INT) = 1"));
        assertEquals(lines(at(1, 41, "FROM"), at(1, 51, ")")),
            refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d) AS) = 1"));
        assertEquals(lines(at(1, 45, "FROM"), at(1, 55, ")")),
            refusal("SELECT a FROM t WHERE TRY_CAST(EXTRACT('wks' FROM d) AS) = 1"));
        assertEquals(lines(at(1, 41, "FROM")), refusal("SELECT a FROM t WHERE CAST(EXTRACT('wks' FROM d))"));
        assertEquals(lines(at(1, 42, "FROM"), at(1, 49, ")")),
            refusal("SELECT a FROM t WHERE CAST((EXTRACT('wks' FROM d)) AS INT) = 1"));
        assertEquals(lines(at(1, 48, "FROM"), at(1, 55, ",")),
            refusal("SELECT a FROM t WHERE CAST(CONCAT(EXTRACT('wks' FROM d), 'x') AS INT) = 1"));
        assertEquals(lines(at(2, 16, "FROM"), at(4, 11, "x")),
            refusal("""
                SELECT a FROM t WHERE TRY_CAST(
                  EXTRACT('wks' FROM d)
                  AS INT) = 1
                ORDER BY a x"""));
        assertEquals(lines(at(3, 29, "FROM")),
            refusal("""
                SELECT a
                FROM t
                WHERE TRY_CAST(EXTRACT('wks' FROM d) AS INT) = 1"""));
    }

    @Test
    public void aLaterParseFaultDoesNotHideTheFromForm() {
        assertEquals(lines(at(1, 21, "FROM"), at(1, 29, "1")), refusal("SELECT EXTRACT('wks' FROM d, 1) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 30, ")")), refusal("SELECT EXTRACT('wks' FROM d, e) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 29, "1")), refusal("SELECT EXTRACT('wks' FROM d, 1)"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 29, "1")), refusal("SELECT EXTRACT('wks' FROM d, 1, 2) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 28, ")")), refusal("SELECT EXTRACT('wks' FROM d,) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) x y FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) x y"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) AS x y FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) x y z FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2")), refusal("SELECT SUBSTRING(b FROM 2) x y FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) x, a b c FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) x FROM t y z"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) FROM t y z"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) x 1 FROM t"));
        assertEquals(lines(at(1, 28, "FROM")), refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x' FROM t"));
        assertEquals(lines(at(1, 28, "FROM")), refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x'"));
        assertEquals(lines(at(1, 28, "FROM")), refusal("SELECT CONCAT(EXTRACT('wks' FROM d)"));
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d)"));
        assertEquals(lines(at(1, 21, "FROM")), refusal("SELECT EXTRACT('wks' FROM d"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 29, "1")), refusal("SELECT EXTRACT('wks' FROM d, 1"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 40, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x')) FROM t"));
        assertEquals(lines(at(1, 28, "FROM"), at(1, 40, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM d), 'x'), 'y') FROM t"));
    }
}
