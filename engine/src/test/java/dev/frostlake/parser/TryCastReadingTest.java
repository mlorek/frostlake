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
 * A TRY_CAST whose construct cannot be read is refused exactly where the same CAST is: at the fault inside its
 * parentheses, with the lines live stacks after it — not at the TRY_CAST's own parenthesis (live-verified).
 */
public class TryCastReadingTest extends BaseDatabaseTest {

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
    public void aCommaIsRefusedWithTheParenthesisClosingTheTryCast() {
        assertEquals(lines(at(17, ","), at(20, ")")), refusal("SELECT TRY_CAST(1, 2) AS INT) FROM t"));
        assertEquals(lines(at(17, ","), at(20, ")")), refusal("SELECT TRY_CAST(1, 2) FROM t"));
        assertEquals(lines(at(17, ","), at(27, ")")), refusal("SELECT TRY_CAST(1, 2 AS INT)"));
        assertEquals(lines(at(17, ","), at(18, ")")), refusal("SELECT TRY_CAST(1,) FROM t"));
        assertEquals(lines(at(17, ","), at(27, ")")), refusal("SELECT TRY_CAST(1, 'x', 'y') AS INT) FROM t"));
        assertEquals(lines(at(17, ","), at(22, ")")), refusal("SELECT TRY_CAST(1, (2)) AS INT) FROM t"));
        assertEquals(lines(at(25, ","), at(30, ")")), refusal("SELECT TRY_CAST(CONCAT(1), 'x') AS INT) FROM t"));
        assertEquals(lines(at(21, ","), at(24, ")")), refusal("SELECT TRY_CAST(a + 1, 2) FROM t"));
        assertEquals(lines(at(21, ","), at(37, ")")), refusal("SELECT TRY_CAST('1.5', 'NUMBER(10,2)')"));
        assertEquals(lines(at(17, ","), at(29, ")")), refusal("SELECT TRY_CAST(a, 'x' || 'y') FROM t"));
    }

    @Test
    public void theWordIsReadInEverySpellingAndPlace() {
        assertEquals(lines(at(17, ","), at(20, ")")), refusal("SELECT try_cast(1, 2) FROM t"));
        assertEquals(lines(at(18, ","), at(21, ")")), refusal("SELECT TRY_CAST (1, 2) FROM t"));
        assertEquals(lines(at(20, ","), at(23, ")")), refusal("SELECT a, TRY_CAST(1, 2) AS INT) FROM t"));
        assertEquals(lines(at(37, ","), at(40, ")")), refusal("SELECT TRY_CAST(1 AS INT), TRY_CAST(1, 2) FROM t"));
        assertEquals(lines(at(17, ","), at(20, ")")), refusal("SELECT TRY_CAST(1, 2), TRY_CAST(3, 4) FROM t"));
        assertEquals(lines(at(17, ","), at(20, ")")), refusal("SELECT TRY_CAST(1, 2) try_cast FROM t"));
        assertEquals(lines(at(35, ","), at(38, ")")), refusal("SELECT 1 FROM t ORDER BY TRY_CAST(1, 2)"));
        assertEquals(lines(at(35, ","), at(38, ")")), refusal("SELECT 1 FROM t GROUP BY TRY_CAST(1, 2)"));
        assertEquals(lines(at(35, ","), at(38, ")")), refusal("INSERT INTO t (a) SELECT TRY_CAST(1, 2) FROM t"));
    }

    @Test
    public void aTopCountLeadsTheValue() {
        assertEquals(lines(at(23, ","), at(26, ")")), refusal("SELECT TOP 1 TRY_CAST(1, 2) FROM t"));
        assertEquals(lines(at(24, ","), at(27, ")")), refusal("SELECT TOP 10 TRY_CAST(1, 2) AS INT) FROM t"));
        assertEquals(lines(at(32, ","), at(35, ")")), refusal("SELECT DISTINCT TOP 1 TRY_CAST(1, 2) FROM t"));
    }

    @Test
    public void outsideTheSelectListTheCommaStandsAlone() {
        assertEquals(lines(at(32, ",")), refusal("SELECT 1 FROM t WHERE TRY_CAST(1, 2) = 1"));
        assertEquals(lines(at(42, ",")), refusal("SELECT a FROM t WHERE a = 1 AND TRY_CAST(b, 2) = 1"));
        assertEquals(lines(at(17, ",")), refusal("SELECT TRY_CAST(1, 2"));
    }

    @Test
    public void aMissingTypeOrOperandIsRefusedAsInACast() {
        assertEquals(lines(at(21, ")")), refusal("SELECT TRY_CAST('1.5')"));
        assertEquals(lines(at(16, ")")), refusal("SELECT TRY_CAST() FROM t"));
        assertEquals(lines(at(24, ","), at(27, ")")), refusal("SELECT TRY_CAST(1 AS INT, 2) FROM t"));
        assertEquals(lines(at(16, "<EOF>")), refusal("SELECT TRY_CAST("));
        assertEquals(lines(at(24, "<EOF>")), refusal("SELECT TRY_CAST(1 AS INT"));
    }

    @Test
    public void aFromFormInsideTheTryCastIsRefusedFirst() {
        assertEquals(lines(at(37, "FROM"), at(49, ")")),
            refusal("SELECT TRY_CAST(CONCAT(EXTRACT('wks' FROM d), 'x') AS INT) FROM t"));
        assertEquals(lines(at(30, "FROM"), at(36, ")")), refusal("SELECT TRY_CAST(EXTRACT('wks' FROM d), 1) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(32, ")")), refusal("SELECT CAST(EXTRACT('wks' FROM d), 1) FROM t"));
        assertEquals(lines(at(26, "FROM"), at(36, ")")), refusal("SELECT CAST(EXTRACT('wks' FROM 2), 1) FROM t"));
        assertEquals(lines(at(24, "FROM"), at(34, ")")), refusal("SELECT CAST(SUBSTRING(b FROM 2), 1) FROM t"));
    }
}
