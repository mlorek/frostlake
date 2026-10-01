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
 * The query's FROM or WHERE refused inside its own select list — after an operator with no right operand, or in plain
 * parentheses: live reads the clause as the query's own and refuses the first fault that reading meets (live-verified).
 */
public class MissingOperandRecoveryTest extends BaseDatabaseTest {

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
    public void afterAnOperatorTheClauseIsTheQuerysOwn() {
        assertEquals(lines(at(1, 11, "FROM"), at(1, 17, ")")), refusal("SELECT 1 * FROM t)"));
        assertEquals(lines(at(1, 11, "FROM"), at(1, 17, ")")), refusal("SELECT 1 + FROM t)"));
        assertEquals(lines(at(1, 11, "FROM"), at(1, 20, "y")), refusal("SELECT 1 * FROM t x y"));
        assertEquals(lines(at(1, 12, "FROM"), at(1, 27, "b")), refusal("SELECT 1 || FROM t WHERE a b"));
        assertEquals(lines(at(1, 11, "FROM"), at(1, 17, ")")), refusal("SELECT 1 * FROM t) x"));
        assertEquals(lines(at(1, 11, "FROM"), at(1, 23, ")")), refusal("SELECT 1 * FROM t WHERE)"));
        assertEquals(lines(at(1, 11, "FROM"), at(1, 15, "<EOF>")), refusal("SELECT 1 * FROM"));
        assertEquals(lines(at(1, 11, "WHERE"), at(1, 18, ")")), refusal("SELECT 1 * WHERE a)"));
        assertEquals(lines(at(1, 11, "FROM"), at(1, 19, "1")), refusal("SELECT 1 * FROM t, 1"));
        assertEquals(lines(at(1, 11, "FROM"), at(1, 17, ")")), refusal("SELECT 1 * FROM t)"));
        assertEquals(lines(at(1, 11, "FROM")), refusal("SELECT a * FROM t"));
    }

    @Test
    public void inPlainParenthesesTooButNotInACall() {
        assertEquals(lines(at(1, 12, "FROM"), at(1, 18, ")")), refusal("SELECT (1 * FROM t)"));
        assertEquals(lines(at(1, 12, "FROM"), at(1, 18, ")")), refusal("SELECT (1   FROM t)"));
        assertEquals(lines(at(1, 17, "FROM")), refusal("SELECT UPPER(1 * FROM t)"));
        assertEquals(lines(at(1, 12, "FROM"), at(1, 18, ")")), refusal("SELECT (1 + FROM t) x y"));
    }
}
