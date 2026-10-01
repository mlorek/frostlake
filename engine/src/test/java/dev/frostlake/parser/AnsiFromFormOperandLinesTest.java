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
 * The lines after the FROM of a call in the refused ANSI FROM form nested in another call, by the shape of its operand:
 * a bracketed operand, an operand that runs into a comma, a FOR with no operand, and the call around it cast
 * (live-verified).
 */
public class AnsiFromFormOperandLinesTest extends BaseDatabaseTest {

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
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aBracketedOperandHasItsFirstTokenRefused() {
        assertEquals(lines(at(28, "FROM"), at(34, "d"), at(35, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM (d)), 'x') FROM t"));
        assertEquals(lines(at(28, "FROM"), at(34, "d")), refusal("SELECT CONCAT(EXTRACT('wks' FROM (d + 1)), 'x') FROM t"));
        assertEquals(lines(at(28, "FROM"), at(34, "("), at(36, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM ((d))), 'x') FROM t"));
        assertEquals(lines(at(28, "FROM"), at(34, "1"), at(42, ")")),
            refusal("SELECT CONCAT(EXTRACT('wks' FROM (1)), 'x') FROM t"));
        assertEquals(lines(at(27, "FROM"), at(33, "d"), at(34, ")")), refusal("SELECT UPPER(EXTRACT('wks' FROM (d))) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(32, "2"), at(40, ")")),
            refusal("SELECT CONCAT(SUBSTRING(b FROM (2)), 'x') FROM t"));
    }

    @Test
    public void anOperandRunIntoACommaRefusesACallsParenthesis() {
        assertEquals(lines(at(28, "FROM"), at(39, ")")), refusal("SELECT CONCAT(EXTRACT('wks' FROM d, 'x')) FROM t"));
        assertEquals(lines(at(27, "FROM"), at(36, ")")), refusal("SELECT UPPER(EXTRACT('wks' FROM d, 1)) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(35, ")")), refusal("SELECT CONCAT(EXTRACT('wks' FROM d,)) FROM t"));
        assertEquals(lines(at(28, "FROM"), at(44, ")")), refusal("SELECT CONCAT(EXTRACT('wks' FROM d, 'x', 'z')) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(36, ")")), refusal("SELECT CONCAT(SUBSTRING(b FROM 2, 1)) FROM t"));
    }

    @Test
    public void aForWithNoOperandIsReadToo() {
        assertEquals(lines(at(19, "FROM"), at(24, "2"), at(29, ")")), refusal("SELECT SUBSTRING(b FROM 2 FOR) FROM t"));
        assertEquals(lines(at(19, "FROM"), at(24, "2"), at(29, ")")), refusal("SELECT SUBSTRING(b FROM 2 FOR) x y FROM t"));
        assertEquals(lines(at(19, "FROM"), at(24, "2"), at(30, "FOR")), refusal("SELECT SUBSTRING(b FROM 2 FOR FOR) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(42, ")")), refusal("SELECT CONCAT(SUBSTRING(b FROM 2 FOR), 'x') FROM t"));
    }

    @Test
    public void aCastAroundTheCallKeepsItsClosingParenthesis() {
        assertEquals(lines(at(33, "FROM"), at(45, ")")),
            refusal("SELECT CAST(CONCAT(EXTRACT('wks' FROM d), 'x') AS INT) FROM t"));
        assertEquals(lines(at(33, "FROM"), at(50, ")")),
            refusal("SELECT CAST(CONCAT(EXTRACT('wks' FROM d), 'x', 'y') AS INT) FROM t"));
        assertEquals(lines(at(32, "FROM")), refusal("SELECT CAST(UPPER(EXTRACT('wks' FROM d)) AS INT) FROM t"));
    }
}
