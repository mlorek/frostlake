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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake's JSON reader refuses a raw line break inside a string, as an unterminated string: a line feed
 * reports the next line at pos 0, a carriage return its own column, in a key as in a value; a backslash
 * before a carriage return is a bad escape, while a backslash before a line feed continues the string. Every
 * other control character is kept. TRY_PARSE_JSON reads such a document as NULL and CHECK_JSON names the
 * fault. CHECK_JSON reads with PARSE_JSON's own leniency, and a numeric literal runs through its letters,
 * points and signs. Every cell is live-verified; the control characters are built with CHR().
 */
public class JsonStringLineBreakTest extends BaseDatabaseTest {

    /** The first row's first cell, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String parsed(final String document) {
        return answer("SELECT PARSE_JSON(" + document + ")");
    }

    private String checked(final String document) {
        return answer("SELECT CHECK_JSON(" + document + ")");
    }

    @Test
    public void aRawLineBreakInsideAStringIsUnterminated() {
        assertEquals("Error parsing JSON: unterminated string, line 2, pos 0", parsed("'{\"k\": \"a' || CHR(10) || 'b\"}'"));
        assertEquals("Error parsing JSON: unterminated string, pos 3", parsed("'\"a' || CHR(13) || 'b\"'"));
        assertEquals("Error parsing JSON: unterminated string, line 2, pos 0", parsed("'{\"k' || CHR(10) || 'x\": 1}'"));
        assertEquals("Error parsing JSON: unterminated string, pos 3", parsed("'\"a' || CHR(13) || CHR(10) || 'b\"'"));
        assertEquals("Error parsing JSON: unterminated string, line 3, pos 0",
            parsed("'[1,' || CHR(10) || '\"a' || CHR(10) || 'b\"]'"));
        assertEquals("Error parsing JSON: unterminated string, pos 5", parsed("'[' || CHR(13) || '\"a' || CHR(13) || 'b\"]'"));
        assertEquals("Error parsing JSON: unterminated string, line 2, pos 5",
            parsed("'{\"k\":' || CHR(10) || '  \"a' || CHR(13) || 'b\"}'"));
        assertEquals("Error parsing JSON: unterminated string, line 2, pos 0", parsed("'\"a' || CHR(10)"));
        assertEquals("Error parsing JSON: bad escape sequence in the string, pos 5", parsed("'\"a\\\\' || CHR(13) || 'b\"'"));
    }

    @Test
    public void tryParseReadsNullAndCheckNamesTheFault() {
        assertEquals("true", answer("SELECT TRY_PARSE_JSON('\"a' || CHR(10) || 'b\"') IS NULL"));
        assertEquals("true", answer("SELECT TRY_PARSE_JSON('\"a' || CHR(13) || 'b\"') IS NULL"));
        assertEquals("unterminated string, line 2, pos 0", checked("'\"a' || CHR(10) || 'b\"'"));
        assertEquals("unterminated string, pos 3", checked("'\"a' || CHR(13) || 'b\"'"));
        assertEquals("bad escape sequence in the string, pos 5", checked("'\"a\\\\' || CHR(13) || 'b\"'"));
        assertEquals("missing colon, pos 6", checked("'{\"a\" 1, \"b\":\"x' || CHR(10) || 'y\"}'"));
    }

    @Test
    public void everyOtherControlCharacterAndAContinuedLineRead() {
        assertEquals("\"a\\tb\"", answer("SELECT TO_JSON(PARSE_JSON('\"a' || CHR(9) || 'b\"'))"));
        assertEquals("[1,2]", answer("SELECT TO_JSON(PARSE_JSON('[1,' || CHR(10) || '2]'))"));
        assertEquals("\"ab\"", answer("SELECT TO_JSON(PARSE_JSON('\"a\\\\' || CHR(10) || 'b\"'))"));
        assertEquals("true", answer("SELECT CHECK_JSON('\"a\\\\' || CHR(10) || 'b\"') IS NULL"));
        assertEquals("true", answer("SELECT CHECK_JSON('\"a' || CHR(1) || 'b\"') IS NULL"));
    }

    @Test
    public void checkJsonReadsWithParseJsonsLeniency() {
        final String[] valid = {
            "'{''a'':1}'", "'01'", "'+1'", "'1.'", "'.5'", "'[1,]'", "'nan'", "'\"a\\\\d\"'", "'''abc'''", "''",
            "'NULL'", "'[1,,2]'", "'Infinity'", "'[undefined]'", "'{a:1}'", "'\"a\\\\''b\"'",
        };
        for (final String document : valid) {
            assertEquals("null", checked(document), document);
        }
        assertEquals("duplicate object attribute \"a\", pos 10", checked("'{\"a\":1,\"a\":2}'"));
    }

    @Test
    public void aNumericLiteralRunsThroughItsLettersPointsAndSigns() {
        assertEquals("Error parsing JSON: garbage in the numeric literal: 2024-01-01 , pos 11", parsed("'2024-01-01'"));
        assertEquals("Error parsing JSON: garbage in the numeric literal: 1.5.5 , pos 6", parsed("'1.5.5'"));
        assertEquals("Error parsing JSON: garbage in the numeric literal: 1- , pos 3", parsed("'1-'"));
        assertEquals("Error parsing JSON: garbage in the numeric literal: 1-2], pos 5", parsed("'[1-2]'"));
        assertEquals("Error parsing JSON: garbage in the numeric literal: 1.5e3.2 , pos 8", parsed("'1.5e3.2'"));
        assertEquals("Error parsing JSON: garbage after valid input document", parsed("'123 456'"));
        assertEquals("garbage in the numeric literal: 2024-01-01 , pos 11", checked("'2024-01-01'"));
    }
}
