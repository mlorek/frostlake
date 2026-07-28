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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PARSE_JSON / TRY_PARSE_JSON tolerate JSON strings that a strict parser rejects but Snowflake accepts —
 * chiefly invalid backslash escapes (e.g. a regex {@code \d}), whose backslash is kept literally so the
 * pattern survives. An over-escaped quote ({@code \'}) is dropped to a literal quote. Truly malformed JSON
 * still errors (PARSE_JSON) / yields NULL (TRY_PARSE_JSON).
 *
 * <p>Note: a Java {@code "\\d"} literal is the single-backslash SQL text {@code \d}.
 */
public class ParseJsonLenientTest extends BaseDatabaseTest {

    private Object one(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void invalidBackslashEscapeKeepsBackslash() {
        // \d is not a valid JSON escape; Snowflake keeps the backslash because the pattern is a regex.
        assertEquals("a\\d+", one("SELECT PARSE_JSON('[\"a\\d+\",\"b\"]')[0]::VARCHAR"));
    }

    @Test
    public void variousRegexEscapesParse() {
        assertNotNull(one("SELECT PARSE_JSON('[\"foo\\s*bar( baz)?\"]')"));
        assertNotNull(one("SELECT PARSE_JSON('[\"(?<!skip )target\"]')"));
        assertNotNull(one("SELECT PARSE_JSON('[\"node \\d+\",\"node (a|p)\\d+\"]')"));
    }

    @Test
    public void overEscapedQuoteDroppedToLiteralQuote() {
        assertEquals("it's", one("SELECT PARSE_JSON('[\"it\\'s\"]')[0]::VARCHAR"));
    }

    @Test
    public void validJsonUnaffected() {
        assertEquals("second entry",
            one("SELECT PARSE_JSON('[\"first entry\",\"second entry\"]')[1]::VARCHAR"));
    }

    @Test
    public void tryParseJsonKeepsBackslashAndNullsOnGarbage() {
        assertNotNull(one("SELECT TRY_PARSE_JSON('[\"a\\d+\"]')"));
        assertNull(one("SELECT TRY_PARSE_JSON('{ not json at all')"));
    }

    @Test
    public void parseJsonStillThrowsOnRealGarbage() {
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PARSE_JSON('{ not json at all')");
            }
        });
    }

    @Test
    public void queryShapeWithRegexColumns() {
        // A query shape: SELECT ... PARSE_JSON($n) ... FROM VALUES(...) with rows whose JSON column holds
        // regex arrays using invalid JSON escapes (\d, \s, lookbehind/lookahead).
        final ResultSet rs = engine.executeQuery(
            "SELECT $1, PARSE_JSON($2), PARSE_JSON($2)\n"
            + "FROM VALUES\n"
            + "  ('alpha', '[\"widget one\",\"node (a|p|m|q|f|fa|v)\\d+\",\"node \\d+\"]'),\n"
            + "  ('beta',  '[\"product two\",\"(?<!skip )target\"]'),\n"
            + "  ('gamma', '[\"foo\\s*bar( baz)?\",\"^alpha\",\"^(?!.*x.*)(.*y.*)\"]'),\n"
            + "  ('delta', '[\"first entry\",\"second entry\"]')");

        assertEquals(4, rs.getRowCount());
        for (int r = 0; r < 4; r++) {
            assertNotNull(rs.getRows().get(r).getValue(1), "PARSE_JSON of row " + r + " should parse");
        }
    }

    @Test
    public void undefinedTokenParsesAsSnowflakeTolerates() {
        // Snowflake's PARSE_JSON accepts the non-standard JavaScript `undefined` token (loaders guard
        // against ingested artifacts with `= PARSE_JSON('[undefined]')`); a strict parser rejected it and
        // killed the whole statement. Bare tokens normalize to the string "undefined"; the word inside a
        // string value stays untouched.
        assertEquals("[\"undefined\"]", String.valueOf(one("SELECT PARSE_JSON('[undefined]')")));
        assertEquals("{\"a\":\"undefined\",\"b\":1}",
            String.valueOf(one("SELECT PARSE_JSON('{\"a\":undefined,\"b\":1}')")));
        assertEquals("is undefined here",
            String.valueOf(one("SELECT PARSE_JSON('{\"m\":\"is undefined here\"}'):m::VARCHAR")));
    }
}
