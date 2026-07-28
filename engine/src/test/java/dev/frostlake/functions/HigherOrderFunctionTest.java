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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Snowflake higher-order functions with lambda arguments: {@code TRANSFORM(<arr>, x -> …)},
 * {@code FILTER(<arr>, x -> <bool>)} and {@code REDUCE(<arr>, <init>, (acc, x) -> …)}. The lambda body is
 * applied per element with the parameter(s) bound; a lambda parameter may carry an (ignored) type and a
 * second parameter receives the element index (for TRANSFORM/FILTER).
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
        assertEquals("[2,3,4]", arr("SELECT TRANSFORM([1, 2, 3], x -> x + 1)"));
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
        assertEquals("[2,4]", arr("SELECT TRANSFORM([1, 2], a INT -> a * 2)"));
    }

    @Test
    public void lambdaOverObjectElementsWithVariantPath() {
        assertEquals("[1,2]", arr("SELECT TRANSFORM(PARSE_JSON('[{\"v\":1},{\"v\":2}]'), a -> a:v)"));
    }

    @Test
    public void higherOrderFunctionsNest() {
        assertEquals("[20,30,40]", arr("SELECT TRANSFORM(FILTER([1, 2, 3, 4], x -> x > 1), y -> y * 10)"));
    }

    @Test
    public void secondLambdaParameterIsTheIndex() {
        assertEquals("[20,30]", arr("SELECT FILTER([10, 20, 30], (x, i) -> i > 0)"));
    }

    @Test
    public void nullArrayYieldsNull() {
        assertNull(scalar("SELECT REDUCE(NULL, 0, (a, b) -> a + b)"));
    }
}
