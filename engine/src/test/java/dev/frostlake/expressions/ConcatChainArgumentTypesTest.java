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
package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A chain of {@code ||} is one call over all its operands. The engine judged each {@code ||} by itself, so
 * {@code 'x' || (1 = 1) || 'y'} was refused at the first operator over two types, where the account refuses it once
 * over all three, at the operator the chain's tree hangs from: the last one of a plain chain, the outer one of a
 * parenthesized chain. Each operand is listed with its own type, a number and a date included, not with the type
 * of the text built so far. CONCAT_WS is refused in CONCAT's name with its separator between the values, and a
 * predicate separator is refused once there are two values for it to stand between. All live-verified.
 */
public class ConcatChainArgumentTypesTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private static String concatRefusal(final int line, final int position, final String types) {
        return "SQL compilation error: error line " + line + " at position " + position
            + " Invalid argument types for function '||': (" + types + ")";
    }

    @Test
    public void aChainIsRefusedOnceOverAllItsOperands() {
        assertEquals(concatRefusal(1, 22, "VARCHAR(1), BOOLEAN, VARCHAR(1)"), refusal("SELECT 'x' || (1 = 1) || 'y'"));
        assertEquals(concatRefusal(1, 19, "VARCHAR(1), VARCHAR(2), BOOLEAN"), refusal("SELECT 'x' || 'yy' || (1 = 1)"));
        assertEquals(concatRefusal(1, 22, "BOOLEAN, VARCHAR(1), VARCHAR(2)"), refusal("SELECT (1 = 1) || 'x' || 'yy'"));
        assertEquals(concatRefusal(1, 39, "VARCHAR(1), VARCHAR(2), VARCHAR(3), BOOLEAN, VARCHAR(4)"),
            refusal("SELECT 'a' || 'bb' || 'ccc' || (1 = 1) || 'dddd'"));
        assertEquals(concatRefusal(1, 16, "VARCHAR(1), BOOLEAN, VARCHAR(1)"), refusal("SELECT ''||(1=1)||''"));
        assertEquals(concatRefusal(1, 11, "VARCHAR(1), BOOLEAN"), refusal("SELECT 'x' || (1 = 1)"));
    }

    @Test
    public void theRefusalPointsAtTheOperatorTheChainHangsFrom() {
        assertEquals(concatRefusal(1, 24, "VARCHAR(1), BOOLEAN, VARCHAR(1)"),
            refusal("SELECT ('x' || (1 = 1)) || 'y'"));
        assertEquals(concatRefusal(1, 11, "VARCHAR(1), BOOLEAN, VARCHAR(1)"),
            refusal("SELECT 'x' || ((1 = 1) || 'y')"));
        assertEquals(concatRefusal(1, 28, "VARCHAR(1), BOOLEAN, VARCHAR(1)"),
            refusal("SELECT UPPER('x' || (1 = 1) || 'y')"));
        assertEquals(concatRefusal(2, 9, "VARCHAR(1), BOOLEAN, VARCHAR(1)"),
            refusal("SELECT 'x' ||\n (1 = 1) ||\n 'y'"));
    }

    @Test
    public void eachOperandIsListedWithItsOwnType() {
        assertEquals(concatRefusal(1, 29, "VARCHAR(1), DATE, BOOLEAN"),
            refusal("SELECT 'x' || CURRENT_DATE() || (1 = 1)"));
        assertEquals(concatRefusal(1, 16, "VARCHAR(1), NUMBER(1,0), BOOLEAN"), refusal("SELECT 'x' || 1 || (1 = 1)"));
        assertEquals(concatRefusal(1, 19, "VARCHAR(1), NULL, BOOLEAN"), refusal("SELECT 'x' || NULL || (1 = 1)"));
    }

    @Test
    public void aChainWithoutAPredicateConcatenates() {
        assertEquals("xy1", String.valueOf(engine.executeQuery("SELECT 'x' || 'y' || 1").getRows().get(0).getValue(0)));
        assertEquals("xtruey", String.valueOf(engine.executeQuery("SELECT 'x' || TRUE || 'y'").getRows().get(0)
            .getValue(0)), "a BOOLEAN value is read as its text; only a predicate is refused");
    }

    @Test
    public void concatWsIsRefusedInConcatsNameWithItsSeparatorBetweenTheValues() {
        final String at = "SQL compilation error: error line 1 at position 7 Invalid argument types for function 'CONCAT': (";
        assertEquals(at + "BOOLEAN, VARCHAR(1), VARCHAR(1))", refusal("SELECT CONCAT_WS(',', 1 = 1, 'a')"));
        assertEquals(at + "VARCHAR(1), VARCHAR(1), BOOLEAN)", refusal("SELECT CONCAT_WS(',', 'a', 1 = 1)"));
        assertEquals(at + "VARCHAR(1), BOOLEAN, VARCHAR(1))", refusal("SELECT CONCAT_WS(1 = 1, 'a', 'b')"));
        assertEquals(at + "BOOLEAN)", refusal("SELECT CONCAT_WS(',', 1 = 1)"));
        assertEquals("a", String.valueOf(engine.executeQuery("SELECT CONCAT_WS(1 = 1, 'a')").getRows().get(0).getValue(0)),
            "with one value the separator is never read");
    }
}
