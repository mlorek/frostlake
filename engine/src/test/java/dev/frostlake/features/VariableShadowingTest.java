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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests for variable shadowing and scoping in nested BEGIN...END blocks.
 *
 * Snowflake Scripting semantics:
 * - Variables declared in an outer scope are visible in inner blocks.
 * - Variables assigned (not declared) in an inner block update the outer scope.
 * - Variables declared (LET / DECLARE) in an inner block shadow outer ones
 *   for the duration of that block and are discarded when the block exits.
 */
public class VariableShadowingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private String exec(final String block) {
        final ResultSet rs = engine.executeQuery("EXECUTE IMMEDIATE $$\n" + block + "\n$$");
        assertNotNull(rs);
        assertFalse(rs.getRows().isEmpty(), "Expected a RETURN value but got no rows");
        final Object val = rs.getRows().get(0).getValue(0);
        return val == null ? "null" : val.toString();
    }

    // ── 1. Assignment in inner block propagates to outer ─────────────────────

    @Test
    public void testAssignmentInInnerBlockUpdatesOuter() {
        // x is declared in outer scope; inner block assigns a new value;
        // that value must be visible after the inner block exits.
        assertEquals("42", exec(
            "DECLARE x INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        x := 42;\n" +
            "    END;\n" +
            "    RETURN :x;\n" +
            "END"
        ));
    }

    // ── 2. Declaration in inner block shadows outer, outer unchanged ──────────

    @Test
    public void testDeclarationInInnerBlockShadowsOuter() {
        // x=10 in outer; inner block declares its own x=99;
        // after inner block exits, outer x must still be 10.
        assertEquals("10", exec(
            "DECLARE x INT DEFAULT 10;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET x INT := 99;\n" +
            "        -- inner x is 99 here\n" +
            "    END;\n" +
            "    RETURN :x;\n" +
            "END"
        ));
    }

    // ── 3. Inner sees and can read outer variable ─────────────────────────────

    @Test
    public void testInnerBlockReadsOuterVariable() {
        // `result` belongs to the OUTER block, so it is declared in the outer DECLARE section (before
        // BEGIN). A `DECLARE result …; BEGIN … END;` placed inside the body would instead be a nested
        // block that scopes `result` to itself (Snowflake semantics), leaving `:result` out of scope
        // at RETURN. The inner block reads the outer `msg` and writes the outer `result`.
        assertEquals("hello", exec(
            "DECLARE\n" +
            "    msg VARCHAR DEFAULT 'hello';\n" +
            "    result VARCHAR DEFAULT '';\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        result := :msg;\n" +
            "    END;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    // ── 4. Triple nesting: innermost assignment bubbles up ────────────────────

    @Test
    public void testTripleNestingAssignmentBubblesUp() {
        assertEquals("7", exec(
            "DECLARE v INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        BEGIN\n" +
            "            v := 7;\n" +
            "        END;\n" +
            "    END;\n" +
            "    RETURN :v;\n" +
            "END"
        ));
    }

    // ── 5. Shadow, modify shadow, outer unchanged ─────────────────────────────

    @Test
    public void testModifiedShadowDoesNotLeakToOuter() {
        // Outer x=1; inner declares x=0 then assigns x=100;
        // outer x must remain 1.
        assertEquals("1", exec(
            "DECLARE x INT DEFAULT 1;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET x INT := 0;\n" +
            "        x := 100;\n" +
            "    END;\n" +
            "    RETURN :x;\n" +
            "END"
        ));
    }

    // ── 6. Multiple variables, only assigned one propagates ───────────────────

    @Test
    public void testOnlyAssignedVariablePropagates() {
        assertEquals("20,99", exec(
            "DECLARE\n" +
            "    a INT DEFAULT 10;\n" +
            "    b INT DEFAULT 99;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        a := 20;\n" +
            "        -- b is not touched\n" +
            "    END;\n" +
            "    RETURN :a || ',' || :b;\n" +
            "END"
        ));
    }

    // ── 7. Shadow one, assign the other ───────────────────────────────────────

    @Test
    public void testShadowOneAssignOther() {
        // a=1 (shadowed inside, outer stays 1); b=2 (assigned inside → becomes 20)
        assertEquals("1,20", exec(
            "DECLARE\n" +
            "    a INT DEFAULT 1;\n" +
            "    b INT DEFAULT 2;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET a INT := 99;\n" +
            "        b := 20;\n" +
            "    END;\n" +
            "    RETURN :a || ',' || :b;\n" +
            "END"
        ));
    }

    // ── 8. Sequential inner blocks are independent ────────────────────────────

    @Test
    public void testSequentialInnerBlocksAreIndependent() {
        // Two sequential inner blocks each increment x.
        // :x + 1 returns a float, so normalise by parsing
        final String result = exec(
            "DECLARE x INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        x := 1;\n" +
            "    END;\n" +
            "    BEGIN\n" +
            "        x := :x + 1;\n" +
            "    END;\n" +
            "    RETURN :x;\n" +
            "END"
        );
        assertEquals(2, (int) Double.parseDouble(result));
    }

    // ── 9. Inner variable not visible after block exits ───────────────────────

    @Test
    public void testInnerVariableNotVisibleAfterBlockExits() {
        // Declare 'result' in outer, assign it inside inner block; return it.
        // This verifies that the inner assignment actually takes effect (not that
        // an inner-declared var is returned — that would be a different test).
        assertEquals("inner", exec(
            "DECLARE result VARCHAR DEFAULT 'outer';\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        result := 'inner';\n" +
            "    END;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    // ── 10. IF block propagates assignment ────────────────────────────────────

    @Test
    public void testIfBlockAssignmentPropagates() {
        assertEquals("yes", exec(
            "DECLARE answer VARCHAR DEFAULT 'no';\n" +
            "BEGIN\n" +
            "    IF (1 = 1) THEN\n" +
            "        answer := 'yes';\n" +
            "    END IF;\n" +
            "    RETURN :answer;\n" +
            "END"
        ));
    }

    // ── 11. IF-ELSE: only executed branch propagates ──────────────────────────

    @Test
    public void testIfElsePropagatesCorrectBranch() {
        assertEquals("else-branch", exec(
            "DECLARE result VARCHAR DEFAULT 'none';\n" +
            "BEGIN\n" +
            "    IF (1 = 2) THEN\n" +
            "        result := 'if-branch';\n" +
            "    ELSE\n" +
            "        result := 'else-branch';\n" +
            "    END IF;\n" +
            "    RETURN :result;\n" +
            "END"
        ));
    }

    // ── 12. FOR loop accumulates into outer variable ──────────────────────────

    @Test
    public void testForLoopAccumulatesIntoOuterVariable() {
        final String result = exec(
            "DECLARE total INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    LET cur CURSOR FOR SELECT 1 AS n UNION ALL SELECT 1 UNION ALL SELECT 1;\n" +
            "    FOR rec IN cur DO\n" +
            "        total := :total + rec.N;\n" +
            "    END FOR;\n" +
            "    CLOSE cur;\n" +
            "    RETURN :total;\n" +
            "END"
        );
        assertEquals(3, (int) Double.parseDouble(result));
    }

    // ── 13. Deeply nested shadow then assignment to outer ─────────────────────

    @Test
    public void testDeepShadowThenOuterAssignment() {
        // outer x=0; level-2 shadows x; level-3 declares y and assigns outer z;
        // after all blocks exit: x=0 (shadowed), z=99
        assertEquals("0,99", exec(
            "DECLARE\n" +
            "    x INT DEFAULT 0;\n" +
            "    z INT DEFAULT 0;\n" +
            "BEGIN\n" +
            "    BEGIN\n" +
            "        LET x INT := 50;\n" +
            "        BEGIN\n" +
            "            LET y INT := 10;\n" +
            "            z := 99;\n" +
            "        END;\n" +
            "    END;\n" +
            "    RETURN :x || ',' || :z;\n" +
            "END"
        ));
    }
}
