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
 * A ')' that closes no group at all ends live's report of the whole input, the statements after it included
 * (live-verified).
 */
public class UnmatchedParenthesisTest extends BaseDatabaseTest {

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
    public void nothingIsReportedAfterIt() {
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1) x y FROM t"));
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1) FROM t WHERE a b"));
        assertEquals(lines(at(1, 15, ")")), refusal("SELECT a FROM t) x y"));
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1)) x y"));
        assertEquals(lines(at(1, 10, ")")), refusal("SELECT (1)) x y"));
        assertEquals(lines(at(1, 27, ")")), refusal("SELECT 1 FROM t WHERE a = 1) AND b c"));
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1) x"));
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1) 2"));
        assertEquals(lines(at(1, 15, ")")), refusal("SELECT 1 FROM t) WHERE a b"));
        assertEquals(lines(at(1, 17, ")")), refusal("SELECT UPPER('a')) x y FROM t"));
        assertEquals(lines(at(1, 15, ")")), refusal("SELECT 1 FROM d) x y FROM t"));
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1) , 2 x y"));
        assertEquals(lines(at(1, 29, ")")), refusal("SELECT 1 FROM t WHERE (a = 1)) b c"));
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1) x; SELECT 1 x y"));
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1); SELECT 2 x y"));
        assertEquals(lines(at(1, 29, ")")), refusal("SELECT 1 FROM t WHERE (a = 1)) b c; SELECT 1 x y"));
        assertEquals(lines(at(1, 8, ")")), refusal("SELECT 1) x"));
    }
}
