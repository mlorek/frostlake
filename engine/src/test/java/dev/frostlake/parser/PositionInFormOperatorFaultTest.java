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
 * A comparison in POSITION's needle that this parser refuses at its second half — {@code <=>} lexes as '<=' then '>' —
 * in the comma form written more than once, and in the IN form (live-verified).
 */
public class PositionInFormOperatorFaultTest extends BaseDatabaseTest {

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
    public void everyLaterSecondHalfIsRefusedToo() {
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 26, ">"), at(1, 34, ")")),
            refusal("SELECT POSITION(1 <=> 1 <=> 1, 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 32, ")")),
            refusal("SELECT POSITION(1 <=> 1 = 1, 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 26, ">"), at(1, 32, ">"), at(1, 40, ")")),
            refusal("SELECT POSITION(1 <=> 1 <=> 1 <=> 1, 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 30, ">"), at(1, 38, ")")),
            refusal("SELECT POSITION(1 <=> 1 = 1 <=> 1, 'x')"));
        assertEquals(lines(at(1, 18, "="), at(1, 24, ">"), at(1, 32, ")")),
            refusal("SELECT POSITION(1 = 1 <=> 1, 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 26, ">"), at(1, 34, ")")),
            refusal("SELECT POSITION(1 <=> 1 <=> 1, 'x') FROM t"));
    }

    @Test
    public void theInFormRefusesItsHaystack() {
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "'x'")),
            refusal("SELECT POSITION(1 <=> 1 IN 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 32, ")")),
            refusal("SELECT POSITION(1 <=> 1 IN ('x'))"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "'x'")),
            refusal("SELECT POSITION(1 <=> 1 IN 'x' || 'y')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "x"), at(1, 28, ")")),
            refusal("SELECT POSITION(1 <=> 1 IN x)"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 32, ")")),
            refusal("SELECT POSITION(1 <=> 1 IN ('x')) + 1"));
        assertEquals(lines(at(1, 24, "<="), at(1, 26, ">"), at(1, 33, "'x'"), at(1, 37, ")")),
            refusal("SELECT UPPER(POSITION(1 <=> 1 IN 'x'))"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 26, ")")), refusal("SELECT POSITION(1 <=> 1 IN)"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "'x'")),
            refusal("SELECT POSITION(1 <=> 1 IN 'x') FROM t"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 26, ">"), at(1, 33, "'x'")),
            refusal("SELECT POSITION(1 <=> 1 <=> 1 IN 'x')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 37, ")")),
            refusal("SELECT POSITION(1 <=> 1 IN ('x', 'y'))"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 39, ")")),
            refusal("SELECT POSITION(1 <=> 1 IN (SELECT 'x'))"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "x"), at(1, 29, "||")),
            refusal("SELECT POSITION(1 <=> 1 IN x || 'y')"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "x"), at(1, 28, ")")),
            refusal("SELECT POSITION(1 <=> 1 IN x) FROM t"));
        assertEquals(lines(at(1, 24, "<="), at(1, 26, ">"), at(1, 33, "x")),
            refusal("SELECT UPPER(POSITION(1 <=> 1 IN x))"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "1")), refusal("SELECT POSITION(1 <=> 1 IN 1)"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "'x'")),
            refusal("SELECT POSITION(1 <=> 1 IN 'x' 'y')"));
        assertEquals(lines(at(1, 18, "="), at(1, 25, "'x'")), refusal("SELECT POSITION(1 = 1 IN 'x')"));
        assertEquals(lines(at(1, 18, "<"), at(1, 30, ")")), refusal("SELECT POSITION(1 < 1 IN ('x'))"));
        assertEquals(lines(at(1, 24, "<="), at(1, 26, ">"), at(1, 39, ")")),
            refusal("SELECT UPPER(POSITION(1 <=> 1 IN ('x')))"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "NULL")),
            refusal("SELECT POSITION(1 <=> 1 IN NULL)"));
        assertEquals(lines(at(1, 18, "<="), at(1, 20, ">"), at(1, 27, "x"), at(1, 28, ".")),
            refusal("SELECT POSITION(1 <=> 1 IN x.y)"));
    }
}
