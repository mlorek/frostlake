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
 * A semi-structured value's JSON text escapes a control character, and DEL, with lower-case hexadecimal
 * digits; the five with a short form keep it, and nothing above DEL is escaped. The rule holds for a value's
 * text, TO_JSON, an object's keys and an extracted member. Every cell is live-verified.
 */
public class VariantStringEscapeTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** TO_JSON of a string holding the character, then an array's text holding it, bracketed together. */
    private String text(final int code) {
        return scalar("SELECT '[' || TO_JSON(TO_VARIANT('a' || CHR(" + code + ") || 'b')) || ', ' || ARRAY_CONSTRUCT('a' "
            + "|| CHR(" + code + "))::VARCHAR || ']'");
    }

    @Test
    public void controlCharactersAndDelEscapeInLowerCase() {
        assertEquals("[\"a\\u0000b\", [\"a\\u0000\"]]", text(0));
        assertEquals("[\"a\\u0001b\", [\"a\\u0001\"]]", text(1));
        assertEquals("[\"a\\bb\", [\"a\\b\"]]", text(8));
        assertEquals("[\"a\\tb\", [\"a\\t\"]]", text(9));
        assertEquals("[\"a\\nb\", [\"a\\n\"]]", text(10));
        assertEquals("[\"a\\u000bb\", [\"a\\u000b\"]]", text(11));
        assertEquals("[\"a\\fb\", [\"a\\f\"]]", text(12));
        assertEquals("[\"a\\rb\", [\"a\\r\"]]", text(13));
        assertEquals("[\"a\\u001bb\", [\"a\\u001b\"]]", text(27));
        assertEquals("[\"a\\u001fb\", [\"a\\u001f\"]]", text(31));
        assertEquals("[\"a\\u007fb\", [\"a\\u007f\"]]", text(127));
    }

    @Test
    public void nothingAboveDelIsEscaped() {
        for (final int code : new int[] {128, 159, 160, 233, 8232, 65279}) {
            assertEquals("true", scalar("SELECT (TO_JSON(TO_VARIANT('a' || CHR(" + code + ") || 'b')) = '\"a' || CHR("
                + code + ") || 'b\"')::VARCHAR"), "character " + code);
        }
    }

    @Test
    public void keysMembersAndParsedTextFollowTheRule() {
        assertEquals("{\"k\\u000b\":\"v\\u001f\"}",
            scalar("SELECT TO_VARCHAR(OBJECT_CONSTRUCT('k' || CHR(11), 'v' || CHR(31)))"));
        assertEquals("{\"k\\u000b\":1.500000000000000e+00}",
            scalar("SELECT TO_JSON(OBJECT_CONSTRUCT('k' || CHR(11), 1.5::FLOAT))"));
        assertEquals("\"a\\u000bb\"", scalar("SELECT TO_JSON(PARSE_JSON('\"a\\\\u000Bb\"'))"));
        assertEquals("[\"\\u007f\"]", scalar("SELECT TO_JSON(PARSE_JSON('[\"\\\\u007F\"]'))"));
        assertEquals("{\"b\":\"x\\u000by\"}",
            scalar("SELECT JSON_EXTRACT_PATH_TEXT('{\"a\":{\"b\":\"x' || CHR(11) || 'y\"}}', 'a')"));
        assertEquals("{\"b\":\"x\\u007fy\"}",
            scalar("SELECT TO_JSON(GET_PATH(PARSE_JSON('{\"a\":{\"b\":\"x' || CHR(127) || 'y\"}}'), 'a'))"));
    }
}
