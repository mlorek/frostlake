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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ARRAY_TO_STRING semantics, aligned with Snowflake: a NULL array or a NULL separator returns NULL.
 *
 * <p>The two array nulls behave differently, and both are live-verified. An {@code undefined} element —
 * what a SQL NULL becomes inside {@code ARRAY_CONSTRUCT} — is ABSENT, so it joins as an empty segment
 * and keeps its separators. A JSON null is a VALUE of type {@code NULL_VALUE} with no string cast, so it
 * fails the whole call. Only TOP-LEVEL elements are cast: a null nested inside an object or an inner
 * array rides along in that element's JSON text and joins fine.
 */
public class ArrayToStringTest extends BaseDatabaseTest {

    @Test
    public void joinsElementsWithSeparator() {
        assertEquals("a,b", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', 'b'), ',')"));
        assertEquals("", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(), ',')"));
    }

    @Test
    public void nullSeparatorReturnsNull() {
        assertNull(scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', 'b'), NULL)"));
        assertNull(scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(), NULL)"));
    }

    @Test
    public void nullArrayReturnsNull() {
        assertNull(scalar("SELECT ARRAY_TO_STRING(NULL, ',')"));
    }

    @Test
    public void nullSeparatorFallsThroughCoalesce() {
        assertEquals("fallback", scalar("SELECT COALESCE(ARRAY_TO_STRING(ARRAY_CONSTRUCT(), NULL), 'fallback')"));
    }

    @Test
    public void undefinedElementsBecomeEmptyStrings() {
        // ARRAY_CONSTRUCT turns a SQL NULL into `undefined`, which joins as an empty segment.
        assertEquals("a,,b", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', NULL, 'b'), ',')"));
        assertEquals("true,1,-1.2", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(TRUE, 1, -1.2), ',')"));
        assertEquals("", scalar("SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(NULL), ',')"));
    }

    @Test
    public void jsonNullElementFailsTheCast() {
        // Live-verified: `Failed to cast variant value from array to string`, independent of the null's
        // position and of the separator.
        assertJsonNullRejected("SELECT ARRAY_TO_STRING(PARSE_JSON('[1,null,2]'), '|')");
        assertJsonNullRejected("SELECT ARRAY_TO_STRING(PARSE_JSON('[null]'), '|')");
        assertJsonNullRejected("SELECT ARRAY_TO_STRING(PARSE_JSON('[null,1]'), '|')");
        assertJsonNullRejected("SELECT ARRAY_TO_STRING(PARSE_JSON('[1,null]'), '')");
    }

    @Test
    public void nullNestedInsideAnElementStillJoins() {
        // Only the TOP-LEVEL element is cast; a nested null rides along inside that element's JSON text.
        assertEquals("{\"a\":null}", scalar("SELECT ARRAY_TO_STRING(PARSE_JSON('[{\"a\":null}]'), '|')"));
        assertEquals("[null]", scalar("SELECT ARRAY_TO_STRING(PARSE_JSON('[[null]]'), '|')"));
        assertEquals("{\"a\":1}|[2]", scalar("SELECT ARRAY_TO_STRING(PARSE_JSON('[{\"a\":1},[2]]'), '|')"));
    }

    private void assertJsonNullRejected(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage())
                .contains("Failed to cast variant value from array to string"),
            "unexpected message: " + error.getMessage());
    }

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }
}
