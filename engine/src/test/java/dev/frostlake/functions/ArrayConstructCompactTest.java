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

/** ARRAY_CONSTRUCT_COMPACT(val1, …) — like ARRAY_CONSTRUCT but omitting every SQL NULL argument. */
public class ArrayConstructCompactTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void omitsNullArguments() {
        assertEquals("[1,2,\"x\"]", scalar("SELECT ARRAY_CONSTRUCT_COMPACT(1, NULL, 2, NULL, 'x')"));
    }

    @Test
    public void allNullArgumentsGiveAnEmptyArray() {
        assertEquals("[]", scalar("SELECT ARRAY_CONSTRUCT_COMPACT(NULL, NULL)"));
    }

    @Test
    public void noArgumentsGiveAnEmptyArray() {
        assertEquals("[]", scalar("SELECT ARRAY_CONSTRUCT_COMPACT()"));
    }

    @Test
    public void aNestedArrayIsEmbeddedNotStringified() {
        assertEquals("[[1,2]]", scalar("SELECT ARRAY_CONSTRUCT_COMPACT(ARRAY_CONSTRUCT(1, 2), NULL)"));
    }

    @Test
    public void aJsonNullIsAValueAndIsKept() {
        // A VARIANT JSON null is not a SQL NULL, so it survives — only SQL NULLs are dropped.
        assertEquals(2L, ((Number) engine.executeQuery(
            "SELECT ARRAY_SIZE(ARRAY_CONSTRUCT_COMPACT(PARSE_JSON('null'), 1))")
            .getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void theResultIsShorterThanTheArgumentList() {
        assertEquals(2L, ((Number) engine.executeQuery(
            "SELECT ARRAY_SIZE(ARRAY_CONSTRUCT_COMPACT(1, NULL, 2))")
            .getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void plainArrayConstructRendersNullsAsUndefined() {
        // A SQL NULL array ELEMENT is the VARIANT `undefined`, distinct from a JSON null — live-verified
        // ARRAY_CONSTRUCT(1, NULL, 2) is [1,undefined,2] while
        // ARRAY_CONSTRUCT(1, PARSE_JSON('null'), 2) keeps [1,null,2], and the two compare UNEQUAL.
        assertEquals("[1,undefined,2]", scalar("SELECT ARRAY_CONSTRUCT(1, NULL, 2)"));
        assertEquals("[1,null,2]", scalar("SELECT ARRAY_CONSTRUCT(1, PARSE_JSON('null'), 2)"));
    }

    @Test
    public void worksOverTableColumns() {
        engine.execute("CREATE TABLE parts (a VARCHAR, b VARCHAR)");
        engine.execute("INSERT INTO parts VALUES ('p', NULL)");
        assertEquals("[\"p\"]", scalar("SELECT ARRAY_CONSTRUCT_COMPACT(a, b) FROM parts"));
    }
}
