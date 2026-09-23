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
 * An operator after a finished predicate is refused ahead of a fault the parse meets further on, with the line live's
 * recovery stacks after it — and ahead of an unsupported type written after a '::' (live-verified).
 */
public class FinishedPredicateBeforeParseFaultTest extends BaseDatabaseTest {

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
    public void theOperatorComesFirst() {
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "AS")), refusal("SELECT 1 IN (1, 2)::INT AS x + 1"));
        assertEquals(lines(at(1, 19, "+"), at(1, 25, "y")), refusal("SELECT 1 IN (1, 2) + 1 x y"));
        assertEquals(lines(at(1, 19, "+"), at(1, 28, "y")), refusal("SELECT 1 IN (1, 2) + 1 AS x y"));
        assertEquals(lines(at(1, 19, "+"), at(1, 25, "y")), refusal("SELECT 1 IN (1, 2) + 1 x y z"));
        assertEquals(lines(at(1, 19, "||"), at(1, 28, "y")), refusal("SELECT 1 IN (1, 2) || 'a' x y"));
        assertEquals(lines(at(1, 17, "+"), at(1, 23, "y")), refusal("SELECT 1 IS NULL + 1 x y"));
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "x")), refusal("SELECT 1 IN (1, 2)::INT x y"));
        assertEquals(lines(at(1, 19, "+"), at(1, 32, "y")), refusal("SELECT 1 IN (1, 2) + 1 FROM t x y"));
        assertEquals(lines(at(1, 34, "+")), refusal("SELECT a FROM t WHERE a IN (1, 2) + 1 x"));
        assertEquals(lines(at(1, 19, "+"), at(1, 25, "y")), refusal("SELECT 1 IN (1, 2) + 1 x y FROM t"));
    }

    @Test
    public void aCastsUnsupportedTypeComesAfterIt() {
        assertEquals(lines(at(1, 18, "::"), at(1, 26, "+")), refusal("SELECT 1 IN (1, 2)::\"INT\" + 1"));
        assertEquals(lines(at(1, 18, "::")), refusal("SELECT 1 IN (1, 2)::\"INT\""));
        assertEquals(lines(at(1, 16, "::"), at(1, 24, "+")), refusal("SELECT 1 IS NULL::\"FOO\" + 1"));
        assertEquals(lines(at(1, 18, "::"), at(1, 24, "+")), refusal("SELECT 1 IN (1, 2)::FOO + 1"));
        assertEquals(lines(at(1, 18, "::"), at(1, 26, "x")), refusal("SELECT 1 IN (1, 2)::\"INT\" x y"));
    }
}
