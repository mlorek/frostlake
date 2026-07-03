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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Procedural (scripting) expression evaluation now goes through the same AST operator semantics as
 * row expressions. These lock that in — especially boolean OR, which previously mis-evaluated as
 * string concatenation in the hand-rolled procedural evaluator.
 */
public class ProceduralExpressionEvalTest {

    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** Run "BEGIN IF (<cond>) THEN RETURN 1; END IF; RETURN 0; END" and return the int result. */
    private static int ifReturns(final String condition) {
        ResultSet rs = engine.executeQuery(
            "BEGIN\n"
            + "    IF (" + condition + ") THEN\n"
            + "        RETURN 1;\n"
            + "    END IF;\n"
            + "    RETURN 0;\n"
            + "END");
        return ((Number) rs.getRows().get(0).getValue(0)).intValue();
    }

    @Test
    public void testOrTrue() {
        assertEquals(1, ifReturns("1 = 2 OR 1 = 1")); // false OR true -> true
    }

    @Test
    public void testOrFalse() {
        // The regression guard: with the old hand-rolled evaluator, OR fell through to string
        // concatenation, so "false OR false" was a non-empty string (truthy) — now it is false.
        assertEquals(0, ifReturns("1 = 2 OR 1 = 3"));
    }

    @Test
    public void testAnd() {
        assertEquals(1, ifReturns("1 = 1 AND 2 = 2"));
        assertEquals(0, ifReturns("1 = 1 AND 2 = 3"));
    }

    @Test
    public void testNot() {
        assertEquals(1, ifReturns("NOT (1 = 2)"));
        assertEquals(0, ifReturns("NOT (1 = 1)"));
    }

    @Test
    public void testComparisonAndArithmeticInCondition() {
        assertEquals(1, ifReturns("2 + 3 = 5"));
        assertEquals(1, ifReturns("10 - 4 > 5"));
    }

    @Test
    public void testUnaryPlusAndMinusInCondition() {
        // Unary minus maps to the shared NEGATE operator; unary plus is folded to the identity at build time
        // (it no longer creates a UnaryExpression). Both must still evaluate correctly, including nested as the
        // operand of a binary operator.
        assertEquals(1, ifReturns("-3 < 0"));
        assertEquals(1, ifReturns("+3 = 3"));
        assertEquals(1, ifReturns("10 + -4 = 6"));
    }
}
