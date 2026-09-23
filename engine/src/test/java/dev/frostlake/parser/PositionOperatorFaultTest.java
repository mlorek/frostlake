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
 * A comparison written in POSITION's first argument that this parser cannot finish — {@code 1 <=> 1} lexes as
 * {@code <=} then {@code >} — is refused at the operator first, then at the fault, then at the closing parenthesis
 * live's recovery trips over (live-verified).
 */
public class PositionOperatorFaultTest extends BaseDatabaseTest {

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
    public void theOperatorBeforeTheFaultIsRefusedFirst() {
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 28, ")")), refusal("SELECT POSITION(1 <=> 1, 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 28, ")")), refusal("SELECT POSITION(1 <=> 1, 'x')"));
        assertEquals(lines(at(1, 24, "<="), at(1, 26, ">"), at(1, 35, ")")), refusal("SELECT UPPER(POSITION(1 <=> 1, 'x'))"));
        assertEquals(lines(at(1, 20, "<="), at(1, 22, ">"), at(1, 35, ")")), refusal("SELECT POSITION('a' <=> 'b', 'x', 1)"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 21, ","), at(1, 26, ")")), refusal("SELECT POSITION(1 <=>, 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 28, ")")), refusal("SELECT POSITION(a <=> a, 'x') FROM t"));
        assertEquals(lines(at(1, 18, "<"), at(1, 20, "=>"), at(1, 29, ")")), refusal("SELECT POSITION(1 < => 1, 'x')"));
        assertEquals(lines(at(1, 18, "="), at(1, 20, ">"), at(1, 28, ")")), refusal("SELECT POSITION(1 = > 1, 'x')"));
    }

    @Test
    public void aWholeOperatorKeepsItsOwnLines() {
        assertEquals(lines(at(1, 11, ">")), refusal("SELECT 1 <=> 1"));
        assertEquals(lines(at(1, 18, "<>"), at(1, 27, ")")), refusal("SELECT POSITION(1 <> 1, 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 27, ")")), refusal("SELECT POSITION(1 <= 1, 'x')"));
        assertEquals(lines(at(1, 11, ">")), refusal("SELECT 1 <=> 1 FROM t"));
        assertEquals(lines(at(1, 18, ">="), at(1, 27, ")")), refusal("SELECT POSITION(1 >= 1, 'x')"));
    }
}
