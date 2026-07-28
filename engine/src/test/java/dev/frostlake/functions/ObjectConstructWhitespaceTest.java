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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A string embedded into an OBJECT/ARRAY keeps its EXACT whitespace — a script or code value
 * legitimately ends in a newline, and round-tripping it through OBJECT_CONSTRUCT must not trim it
 * (trimming silently corrupted stored code blocks so they no longer compared equal to the source).
 */
public class ObjectConstructWhitespaceTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        return result.getRows().get(0).getValue(0);
    }

    @Test
    public void trailingNewlineSurvivesObjectConstruct() {
        assertEquals("{\"code\":\"line1\\nline2\\n\"}",
            scalar("SELECT OBJECT_CONSTRUCT('code', 'line1\nline2\n') AS o"));
    }

    @Test
    public void leadingAndTrailingSpacesSurviveArrayConstruct() {
        assertEquals("[\"  padded  \"]",
            scalar("SELECT ARRAY_CONSTRUCT('  padded  ') AS a"));
    }

    @Test
    public void whitespaceSurvivesExtractionRoundTrip() {
        assertEquals("ends with newline\n",
            scalar("SELECT OBJECT_CONSTRUCT('v', 'ends with newline\n'):v::VARCHAR AS s"));
    }

    @Test
    public void presentJsonNullReEmbedsAsJsonNull() {
        // A VARIANT JSON null (the text "null" in this engine's model) embeds back as a real JSON null,
        // and the pair is KEPT — matching Snowflake's handling of a VARIANT-null value.
        assertEquals("{\"a\":null}",
            scalar("SELECT OBJECT_CONSTRUCT_KEEP_NULL('a', PARSE_JSON('{\"x\":null}'):x) AS o"));
        assertEquals("{\"a\":null}",
            scalar("SELECT OBJECT_CONSTRUCT('a', PARSE_JSON('{\"x\":null}'):x) AS o"));
        assertEquals("[null]",
            scalar("SELECT ARRAY_CONSTRUCT(PARSE_JSON('{\"x\":null}'):x) AS a"));
    }
}
