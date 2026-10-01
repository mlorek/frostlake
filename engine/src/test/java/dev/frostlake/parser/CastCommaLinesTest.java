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
 * A comma directly inside a CAST's or TRY_CAST's parentheses is refused, and in a select list live stacks the
 * parenthesis closing the cast and nothing more; elsewhere the comma stands alone (live-verified).
 */
public class CastCommaLinesTest extends BaseDatabaseTest {

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
    public void inASelectListTheCastsParenthesisFollows() {
        assertEquals(lines(at(13, ","), at(23, ")")), refusal("SELECT CAST(1, 2 AS INT)"));
        assertEquals(lines(at(13, ","), at(16, ")")), refusal("SELECT CAST(1, 2) FROM t"));
        assertEquals(lines(at(13, ","), at(23, ")")), refusal("SELECT CAST(1, 'x', 'y') AS INT) FROM t"));
        assertEquals(lines(at(13, ","), at(16, ")")), refusal("SELECT CAST(1, 2) AS INT) x y FROM t"));
        assertEquals(lines(at(13, ","), at(18, ")")), refusal("SELECT CAST(1, (2)) AS INT) FROM t"));
        assertEquals(lines(at(13, ","), at(14, ")")), refusal("SELECT CAST(1,) FROM t"));
        assertEquals(lines(at(21, ","), at(26, ")")), refusal("SELECT CAST(CONCAT(1), 'x') AS INT) FROM t"));
    }

    @Test
    public void aTryCastReadsItsCommaTheSameWay() {
        assertEquals(lines(at(24, ","), at(27, ")")), refusal("SELECT TRY_CAST(1 AS INT, 2) FROM t"));
        assertEquals(lines(at(17, ","), at(20, ")")), refusal("SELECT TRY_CAST(1, 2) AS INT) x y FROM t"));
        assertEquals(lines(at(17, ","), at(22, ")")), refusal("SELECT TRY_CAST(1, (2)) AS INT) FROM t"));
    }

    @Test
    public void elsewhereOrAtTheEndTheCommaStandsAlone() {
        assertEquals(lines(at(13, ",")), refusal("SELECT CAST(1, 2"));
        assertEquals(lines(at(28, ",")), refusal("SELECT 1 FROM t WHERE CAST(1, 2) = 1"));
    }
}
