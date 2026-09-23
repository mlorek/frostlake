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
 * A '(' right after a finished value holding anything but the outer-join marker: live refuses the token after it and
 * resumes the expression at the first token that could follow the value (live-verified).
 */
public class OuterJoinMarkerRecoveryTest extends BaseDatabaseTest {

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
    public void theExpressionResumesAfterTheBracket() {
        assertEquals(lines(at(1, 20, "1"), at(1, 26, ")")), refusal("SELECT CONCAT('a') (1) + 2)"));
        assertEquals(lines(at(1, 20, "1"), at(1, 25, "y")), refusal("SELECT CONCAT('a') (1) x y"));
        assertEquals(lines(at(1, 20, "1"), at(1, 29, "y")), refusal("SELECT CONCAT('a') (1) + 2 x y"));
        assertEquals(lines(at(1, 20, "1"), at(1, 29, ")")), refusal("SELECT CONCAT('a') (1) FROM t)"));
        assertEquals(lines(at(1, 20, "1"), at(1, 24, ")")), refusal("SELECT CONCAT('a') (1, 2) + 3)"));
        assertEquals(lines(at(1, 12, "1"), at(1, 18, ")")), refusal("SELECT (1) (1) + 2)"));
        assertEquals(lines(at(1, 12, "1"), at(1, 18, ")")), refusal("SELECT 'x' (1) + 2)"));
        assertEquals(lines(at(1, 10, "1"), at(1, 16, ")")), refusal("SELECT 1 (1) + 2)"));
        assertEquals(lines(at(1, 20, "1")), refusal("SELECT CONCAT('a') (1)) + 2"));
        assertEquals(lines(at(1, 20, "1")), refusal("SELECT CONCAT('a') (1) x"));
        assertEquals(lines(at(1, 20, "1"), at(1, 32, "y")), refusal("SELECT CONCAT('a') (1) FROM t x y"));
        assertEquals(lines(at(1, 20, "1")), refusal("SELECT CONCAT('a') (1)"));
        assertEquals(lines(at(1, 20, "1")), refusal("SELECT CONCAT('a') (1) + 2"));
    }

    @Test
    public void everyBracketTheValueStoodInIsGivenUp() {
        assertEquals(lines(at(1, 26, "1"), at(1, 32, ")")), refusal("SELECT UPPER(CONCAT('a') (1) + 2)"));
        assertEquals(lines(at(1, 23, "1"), at(1, 29, ")")), refusal("SELECT 1, CONCAT('a') (1) + 2)"));
        assertEquals(lines(at(1, 35, "1"), at(1, 41, ")")), refusal("SELECT 1 FROM t WHERE CONCAT('a') (1) + 2)"));
        assertEquals(lines(at(1, 20, "1"), at(1, 26, ")")), refusal("SELECT CONCAT('a') (1) + 2) FROM t"));
        assertEquals(lines(at(1, 20, "1"), at(1, 32, "y")), refusal("SELECT CONCAT('a') (1) || 'b' x y"));
        assertEquals(lines(at(1, 20, "1"), at(1, 28, "y")), refusal("SELECT CONCAT('a') (1) AS x y"));
        assertEquals(lines(at(1, 20, "1"), at(1, 31, "b")), refusal("SELECT CONCAT('a') (1) WHERE a b"));
        assertEquals(lines(at(1, 20, "1"), at(1, 29, "y")), refusal("SELECT CONCAT('a') (1) , 2 x y"));
        assertEquals(lines(at(1, 20, "1"), at(1, 28, "x")), refusal("SELECT CONCAT('a') (1)::INT x y"));
        assertEquals(lines(at(1, 20, "1"), at(1, 26, ")")), refusal("SELECT CONCAT('a') (1) - 2)"));
    }

    @Test
    public void theMarkerItselfIsReadAsWritten() {
        assertEquals(lines(at(1, 22, "1")), refusal("SELECT CONCAT('a') (+ 1)"));
    }
}
