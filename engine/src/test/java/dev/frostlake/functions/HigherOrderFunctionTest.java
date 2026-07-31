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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Snowflake higher-order functions with lambda arguments: {@code TRANSFORM(<arr>, x -> …)},
 * {@code FILTER(<arr>, x -> <bool>)} and {@code REDUCE(<arr>, <init>, (acc, x) -> …)}. The lambda body is
 * applied per element with the parameter(s) bound as VARIANT — so arithmetic over them is DOUBLE
 * ({@code x + 1} over {@code [1,2,3]} yields {@code [2.0,3.0,4.0]}). A lambda parameter may DECLARE a
 * type, which casts the bound element and therefore changes that arithmetic; a second parameter receives
 * the element index (for TRANSFORM/FILTER).
 */
public class HigherOrderFunctionTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private String arr(final String sql) {
        final Object v = scalar(sql);
        return v == null ? null : v.toString().replaceAll("\\s", "");
    }

    @Test
    public void transformMapsEachElement() {
        assertEquals("[2.0,3.0,4.0]", arr("SELECT TRANSFORM([1, 2, 3], x -> x + 1)"));
    }

    @Test
    public void filterKeepsMatchingElements() {
        assertEquals("[3,4]", arr("SELECT FILTER([1, 2, 3, 4], x -> x > 2)"));
    }

    @Test
    public void reduceFoldsToAScalar() {
        assertEquals(10L, ((Number) scalar("SELECT REDUCE([1, 2, 3, 4], 0, (acc, x) -> acc + x)")).longValue());
    }

    @Test
    public void lambdaParameterMayCarryAType() {
        // A declared parameter type is not decoration: it CASTS the bound element, and that changes the
        // arithmetic. Live-verified on a real account — TRANSFORM([1,2], a -> a * 2) is
        // [2.0,4.0] because an untyped element binds as a VARIANT (VARIANT arithmetic is FLOAT), while
        // TRANSFORM([1,2], a INT -> a * 2) is [2,4].
        assertEquals("[2,4]", arr("SELECT TRANSFORM([1, 2], a INT -> a * 2)"));
        assertEquals("[2,4]", arr("SELECT TRANSFORM([1, 2], a NUMBER -> a * 2)"));
        assertEquals("[2.0,4.0]", arr("SELECT TRANSFORM([1, 2], a -> a * 2)"));
    }

    @Test
    public void lambdaOverObjectElementsWithVariantPath() {
        assertEquals("[1,2]", arr("SELECT TRANSFORM(PARSE_JSON('[{\"v\":1},{\"v\":2}]'), a -> a:v)"));
    }

    @Test
    public void higherOrderFunctionsNest() {
        assertEquals("[20.0,30.0,40.0]", arr("SELECT TRANSFORM(FILTER([1, 2, 3, 4], x -> x > 1), y -> y * 10)"));
    }

    @Test
    public void indexCarryingLambdasAreRejected() {
        // TRANSFORM and FILTER take a ONE-parameter lambda; the index-carrying form is not
        // Snowflake syntax (live: "Invalid argument types for function 'FILTER':
        // (ARRAY, FUNCTION(VARIANT,ANY))"). REDUCE's two-parameter lambda stays valid.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                arr("SELECT FILTER([10, 20, 30], (x, i) -> i > 0)");
            }
        });
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                arr("SELECT TRANSFORM([10, 20], (x, i) -> x + i)");
            }
        });
    }

    @Test
    public void nullArrayYieldsNull() {
        assertNull(scalar("SELECT REDUCE(NULL, 0, (a, b) -> a + b)"));
    }
}
