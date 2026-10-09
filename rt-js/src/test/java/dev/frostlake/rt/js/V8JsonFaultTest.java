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

package dev.frostlake.rt.js;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A text that is not JSON is told as the account's engine tells it: the sentence, and the line and column. */
public class V8JsonFaultTest {

    /** A text, and the sentence for it. */
    private static final String[][] FAULTS = {
            {"{", "Expected property name or '}' in JSON at position 1 (line 1 column 2)"},
            {"", "Unexpected end of JSON input"},
            {"[1,]", "Unexpected token ']', \"[1,]\" is not valid JSON"},
            {"{\"a\":1,}", "Expected double-quoted property name in JSON at position 7 (line 1 column 8)"},
            {"abc", "Unexpected token 'a', \"abc\" is not valid JSON"},
            {"abcdefghijklmnopqrstuvwxyz0123456789", "Unexpected token 'a', \"abcdefghij\"... is not valid JSON"},
            {"[1,2,3,4,5,6,7,8,9,10,11,x,13,14,15,16,17,18,19,20]", "Unexpected token 'x', ...\"8,9,10,11,x,13,14,15\"... is not valid JSON"},
            {"[1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,x]", "Unexpected token 'x', ...\",17,18,19,x]\" is not valid JSON"},
            {"01", "Unexpected number in JSON at position 1 (line 1 column 2)"},
            {"1.", "Unterminated fractional number in JSON at position 2 (line 1 column 3)"},
            {"\"a\nb\"", "Bad control character in string literal in JSON at position 2 (line 1 column 3)"},
            {"\"\\u12g\"", "Bad Unicode escape in JSON at position 5 (line 1 column 6)"},
            {"trx", "Unexpected token 'x', \"trx\" is not valid JSON"},
            {"nul", "Unexpected end of JSON input"},
            {"{\"a\":1 \"b\":2}", "Expected ',' or '}' after property value in JSON at position 7 (line 1 column 8)"},
            {"[\"a\" \"b\"]", "Expected ',' or ']' after array element in JSON at position 5 (line 1 column 6)"},
            {"{\"a\":}", "Unexpected token '}', \"{\"a\":}\" is not valid JSON"},
            {"NaN", "\"NaN\" is not valid JSON"},
            {"Infinity", "\"Infinity\" is not valid JSON"},
            {"[1,2", "Expected ',' or ']' after array element in JSON at position 4 (line 1 column 5)"},
            {"{\"a\":1", "Expected ',' or '}' after property value in JSON at position 6 (line 1 column 7)"},
            {"{\"a\"", "Expected ':' after property name in JSON at position 4 (line 1 column 5)"},
            {"  ", "Unexpected end of JSON input"},
            {"'a'", "Unexpected token ''', \"'a'\" is not valid JSON"},
            {"1 ]", "Unexpected non-whitespace character after JSON at position 2 (line 1 column 3)"},
            {"+1", "Unexpected token '+', \"+1\" is not valid JSON"},
            {".5", "Unexpected token '.', \".5\" is not valid JSON"},
            {"\"\t\"", "Bad control character in string literal in JSON at position 1 (line 1 column 2)"},
            {"1e+", "Exponent part is missing a number in JSON at position 3 (line 1 column 4)"},
            {"-x", "No number after minus sign in JSON at position 1 (line 1 column 2)"},
            {"{1:2}", "Expected property name or '}' in JSON at position 1 (line 1 column 2)"},
            {"[,1]", "Unexpected token ',', \"[,1]\" is not valid JSON"},
            {"{,}", "Expected property name or '}' in JSON at position 1 (line 1 column 2)"},
            {"true false", "Unexpected non-whitespace character after JSON at position 5 (line 1 column 6)"},
    };

    @Test
    public void aFaultIsToldInTheAccountsWords() {
        for (final String[] one : FAULTS) {
            final V8JsonFault fault = V8JsonFault.of(one[0]);
            assertNotNull(fault, one[0]);
            assertEquals(one[1], fault.message(), one[0]);
        }
    }

    @Test
    public void aTextThatIsJsonHasNoFault() {
        assertNull(V8JsonFault.of("{\"a\": [1, -2.5e3, \"x\\n\\u0041\", null, true, false, {}]}"));
        assertNull(V8JsonFault.of(" 12 "));
        assertNull(V8JsonFault.of("\"text\""));
        assertNull(V8JsonFault.of("[]"));
    }

    @Test
    public void aFaultKnowsTheLineItIsOnAndTheColumnInIt() {
        final V8JsonFault first = V8JsonFault.of("{");
        assertEquals("{", first.line());
        assertEquals(1, first.column());
        final V8JsonFault third = V8JsonFault.of("[1,\n2,\n x]");
        assertEquals(" x]", third.line());
        assertEquals(1, third.column());
        final V8JsonFault empty = V8JsonFault.of("");
        assertEquals("", empty.line());
        assertEquals(0, empty.column());
        final V8JsonFault word = V8JsonFault.of("abc");
        assertEquals("abc", word.line());
        assertEquals(0, word.column());
    }
}
