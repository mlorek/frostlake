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

/**
 * FILTER and TRANSFORM answer an ARRAY like any other: it reads as its Snowflake text, a SQL NULL from the
 * lambda is an {@code undefined} element, and it types, sizes, casts and subscripts as an ARRAY. Cast to
 * VARCHAR, a FLOAT element prints in the VARIANT's exponent form. REDUCE answers its accumulator. Every cell
 * is live-verified.
 */
public class HigherOrderArrayResultTest extends BaseDatabaseTest {

    private String text(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aHigherOrderArrayReadsAsItsText() {
        assertEquals("[1,2]", text("SELECT FILTER(ARRAY_CONSTRUCT(1, NULL, 2), x -> x IS NOT NULL)"));
        assertEquals("[undefined,undefined]", text("SELECT TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> NULL)"));
        assertEquals("[]", text("SELECT FILTER(ARRAY_CONSTRUCT(), x -> TRUE)"));
        assertEquals("[\"A\"]", text("SELECT TRANSFORM(ARRAY_CONSTRUCT('a'), x -> UPPER(x))"));
        assertEquals("[{\"k\":1},{\"k\":2}]",
            text("SELECT TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> OBJECT_CONSTRUCT('k', x))"));
    }

    @Test
    public void itIsAnArrayLikeAnyOther() {
        assertEquals("ARRAY[LOB]", text("SELECT SYSTEM$TYPEOF(FILTER(ARRAY_CONSTRUCT(1, NULL, 2), x -> x IS NOT NULL))"));
        assertEquals("ARRAY[LOB]", text("SELECT SYSTEM$TYPEOF(TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> x * 10))"));
        assertEquals("ARRAY[LOB]", text("SELECT SYSTEM$TYPEOF(FILTER(NULL::ARRAY, x -> TRUE))"));
        assertEquals("ARRAY", text("SELECT TYPEOF(TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> x * 10))"));
        assertEquals("2", text("SELECT ARRAY_SIZE(TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> NULL))"));
        assertEquals("[undefined,undefined]", text("SELECT TO_JSON(TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> NULL))"));
        assertEquals("[1.000000000000000e+01,2.000000000000000e+01]",
            text("SELECT TRANSFORM(ARRAY_CONSTRUCT(1, 2), x -> x * 10)::VARCHAR"));
        assertEquals("2", text("SELECT FILTER(ARRAY_CONSTRUCT(1, 2, 3), x -> x > 1)[0]"));
        assertEquals("6", text("SELECT TO_VARCHAR(REDUCE(ARRAY_CONSTRUCT(1, 2, 3), 0, (acc, x) -> acc + x))"));
    }
}
