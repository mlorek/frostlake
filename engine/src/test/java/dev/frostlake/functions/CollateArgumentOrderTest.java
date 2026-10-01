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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COLLATE judges its first two arguments before anything else about the call: the operand must be a string,
 * then the specification must be written as a string literal, and only a call passing both is refused for
 * its argument count, or for its specification's text. Every cell is live-verified.
 */
public class CollateArgumentOrderTest extends BaseDatabaseTest {

    private static final String NOT_A_STRING = "SQL compilation error:\nargument needs to be a string: ";
    private static final String NOT_A_LITERAL =
        "SQL compilation error:\nArgument number 2 for function 'COLLATE' needs to be a string literal.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT, s VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'x'), (2, 'y')");
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    @Test
    public void aCallOfThreeRefusesANonStringOperandFirst() {
        assertEquals(NOT_A_STRING + "'1'", refusal("SELECT COLLATE(1, 'en-ci', 'x')"));
        assertEquals(NOT_A_STRING + "'1'", refusal("SELECT COLLATE(1, UPPER('x'), 'y')"));
        assertEquals(NOT_A_STRING + "'1'", refusal("SELECT COLLATE(1, 2, 3)"));
        assertEquals(NOT_A_STRING + "'TRUE'", refusal("SELECT COLLATE(TRUE, 'en-ci', 'x')"));
        assertEquals(NOT_A_STRING + "'1.5'", refusal("SELECT COLLATE(1.5, 'en-ci', 'x')"));
        assertEquals(NOT_A_STRING + "'X'41''", refusal("SELECT COLLATE(X'41', 'en-ci', 'x')"));
        assertEquals(NOT_A_STRING + "'T.A'", refusal("SELECT COLLATE(a, 'en-ci', 'x') FROM t"));
    }

    @Test
    public void aCallOfThreeRefusesAComputedSpecificationNext() {
        assertEquals(NOT_A_LITERAL, refusal("SELECT COLLATE('a', UPPER('en-ci'), 'x')"));
        assertEquals(NOT_A_LITERAL, refusal("SELECT COLLATE('a', NULL, 'x')"));
        assertEquals(NOT_A_LITERAL, refusal("SELECT COLLATE('a', 1, 'x')"));
        assertEquals(NOT_A_LITERAL, refusal("SELECT COLLATE('a', 2, 3)"));
        assertEquals(NOT_A_LITERAL, refusal("SELECT COLLATE(s, s, 'x') FROM t"));
        assertEquals(NOT_A_LITERAL, refusal("SELECT COLLATE('a', s, 'x') FROM t"));
    }

    @Test
    public void aCallOfTwoRefusesTheOperandBeforeTheSpecification() {
        assertEquals(NOT_A_STRING + "'1'", refusal("SELECT COLLATE(1, UPPER('x'))"));
        assertEquals(NOT_A_STRING + "'1'", refusal("SELECT COLLATE(1, NULL)"));
        assertEquals(NOT_A_STRING + "'T.A'", refusal("SELECT COLLATE(a, UPPER('x')) FROM t"));
        assertEquals(NOT_A_LITERAL, refusal("SELECT COLLATE(s, UPPER('x')) FROM t"));
        assertEquals(NOT_A_LITERAL, refusal("SELECT COLLATE('a', UPPER('en-ci'))"));
    }

    @Test
    public void onlyACallPassingBothIsTooManyArguments() {
        final String three = "too many arguments for function [COLLATE('a', 'en-ci')] expected 2, got 3";
        assertTrue(refusal("SELECT COLLATE('a', 'en-ci', 'x')").contains(three));
        assertTrue(refusal("SELECT COLLATE('a', $$en-ci$$, 'x')").contains(three));
        assertTrue(refusal("SELECT COLLATE('a', 'en-ci', 1)").contains(three));
        assertTrue(refusal("SELECT COLLATE('a', 'bogus-spec', 'x')").contains(
            "too many arguments for function [COLLATE('a', 'bogus-spec')] expected 2, got 3"));
        assertTrue(refusal("SELECT COLLATE(1, 'en-ci', 'x', 'y')").contains(
            "too many arguments for function [COLLATE(1, 'en-ci', 'x', 'y')] expected 3, got 4"));
        assertTrue(refusal("SELECT COLLATE(a, s, 'x', 'y') FROM t").contains(
            "too many arguments for function [COLLATE(T.A, T.S, 'x', 'y')] expected 3, got 4"));
    }
}
