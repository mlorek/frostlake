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
 * An IN list this parser cannot read is refused where live refuses it: at the first token the list cannot take, or at
 * the SELECT of a subquery in it that cannot be read, not at the IN (live-verified).
 */
public class InListFaultLinesTest extends BaseDatabaseTest {

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
    public void theListIsReadAndItsFirstFaultIsRefused() {
        assertEquals(lines(at(1, 28, "<EOF>")), refusal("SELECT a FROM t WHERE a IN ("));
        assertEquals(lines(at(1, 35, "<EOF>"), at(1, 35, "<EOF>")), refusal("SELECT a FROM t WHERE a IN (1, ABS("));
        assertEquals(lines(at(1, 29, "<EOF>")), refusal("SELECT a FROM t WHERE a IN (1"));
        assertEquals(lines(at(1, 30, "<EOF>")), refusal("SELECT a FROM t WHERE a IN (1,"));
        assertEquals(lines(at(1, 30, "2")), refusal("SELECT a FROM t WHERE a IN (1 2)"));
        assertEquals(lines(at(1, 26, "<EOF>")), refusal("SELECT a FROM t WHERE a IN"));
        assertEquals(lines(at(1, 32, "<EOF>")), refusal("SELECT a FROM t WHERE a NOT IN ("));
        assertEquals(lines(at(1, 13, "<EOF>")), refusal("SELECT a IN ("));
        assertEquals(lines(at(1, 28, ")")), refusal("SELECT a FROM t WHERE a IN ()"));
        assertEquals(lines(at(1, 32, "<EOF>"), at(1, 32, "<EOF>")), refusal("SELECT a FROM t WHERE a IN (ABS("));
        assertEquals(lines(at(1, 32, "<EOF>")), refusal("SELECT a FROM t WHERE a IN (1, 2"));
    }

    @Test
    public void aSubqueryInTheListIsRefusedAtItsKeyword() {
        assertEquals(lines(at(1, 28, "SELECT"), at(1, 44, ")")), refusal("SELECT a FROM t WHERE a IN (SELECT 1 foo (2))"));
        assertEquals(lines(at(1, 28, "SELECT")), refusal("SELECT a FROM t WHERE a IN (SELECT"));
        assertEquals(lines(at(1, 28, "SELECT")), refusal("SELECT a FROM t WHERE a IN (SELECT 1 FROM)"));
    }
}
