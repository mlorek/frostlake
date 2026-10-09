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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code JSON.parse} refuses a text in the account's words, inside the handler and out of it: the sentence tells
 * what was expected and where, by position, line and column, or quotes the token it met with the text around it.
 * Uncaught, the quoted line and the position are the JSON text's own, not the body's. Every row is
 * live-verified.
 */
public class JavaScriptJsonParseFaultTest extends BaseDatabaseTest {

    /** A text, and the message of the error {@code JSON.parse} throws for it. */
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

    /** A body, and the sentence after {@code Uncaught }. */
    private static final String[][] UNCAUGHT = {
            {"return JSON.parse('{');",
                "SyntaxError: Expected property name or '}' in JSON at position 1 (line 1 column 2) in EP at '{' position 1\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('');",
                "SyntaxError: Unexpected end of JSON input in EP at '' position 0\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('{\"a\":1,}');",
                "SyntaxError: Expected double-quoted property name in JSON at position 7 (line 1 column 8) in EP at '{\"a\":1,}' position 7\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('[1,]');",
                "SyntaxError: Unexpected token ']', \"[1,]\" is not valid JSON in EP at '[1,]' position 3\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('abc');",
                "SyntaxError: Unexpected token 'a', \"abc\" is not valid JSON in EP at 'abc' position 0\nstackstrace: \nEP line: 1"},
            {"return JSON.parse(undefined);",
                "SyntaxError: \"undefined\" is not valid JSON in EP at 'undefined' position 0\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('{\"a\" 1}');",
                "SyntaxError: Expected ':' after property name in JSON at position 5 (line 1 column 6) in EP at '{\"a\" 1}' position 5\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('\"abc');",
                "SyntaxError: Unterminated string in JSON at position 4 (line 1 column 5) in EP at '\"abc' position 4\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('{a:1}');",
                "SyntaxError: Expected property name or '}' in JSON at position 1 (line 1 column 2) in EP at '{a:1}' position 1\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('[1 2]');",
                "SyntaxError: Expected ',' or ']' after array element in JSON at position 3 (line 1 column 4) in EP at '[1 2]' position 3\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('-');",
                "SyntaxError: No number after minus sign in JSON at position 1 (line 1 column 2) in EP at '-' position 1\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('1e');",
                "SyntaxError: Exponent part is missing a number in JSON at position 2 (line 1 column 3) in EP at '1e' position 2\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('\"\\\\x\"');",
                "SyntaxError: Bad escaped character in JSON at position 2 (line 1 column 3) in EP at '\"\\x\"' position 2\nstackstrace: \nEP line: 1"},
            {"return JSON.parse('tru');",
                "SyntaxError: Unexpected end of JSON input in EP at 'tru' position 3\nstackstrace: \nEP line: 1"},
            {"var s = '{'; return JSON.parse(s);",
                "SyntaxError: Expected property name or '}' in JSON at position 1 (line 1 column 2) in EP at '{' position 1\nstackstrace: \nEP line: 1"},
            {"var s = '{\"a\":\\n 1,}'; return JSON.parse(s);",
                "SyntaxError: Expected double-quoted property name in JSON at position 9 (line 2 column 4) in EP at ' 1,}' position 3\nstackstrace: \nEP line: 1"},
            {"return JSON.parse(\n  '{');",
                "SyntaxError: Expected property name or '}' in JSON at position 1 (line 1 column 2) in EP at '{' position 1\nstackstrace: \nEP line: 1"},
    };

    /** Each body, made a handler, is refused in the given words: what follows {@code Uncaught }. */
    private void assertUncaught(final String[][] cases) {
        for (final String[] one : cases) {
            engine.execute("CREATE OR REPLACE FUNCTION ep() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$" + one[0] + "$$");
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT ep()").getRows();
                }
            }, one[0]);
            assertTrue(String.valueOf(refused.getMessage()).contains("JavaScript execution error: Uncaught " + one[1]),
                one[0] + " -> " + refused.getMessage());
        }
    }

    @Test
    public void aHandlerCatchesTheFaultInTheAccountsWords() {
        engine.execute("CREATE OR REPLACE FUNCTION jp(T VARCHAR) RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$try { JSON.parse(T); return 'parsed'; } catch (e) { return e.name + ': ' + e.message; }$$");
        for (final String[] one : FAULTS) {
            assertEquals("SyntaxError: " + one[1],
                String.valueOf(engine.executeQuery("SELECT jp($$" + one[0] + "$$)").getRows().get(0).getValue(0)),
                one[0]);
        }
        assertEquals("parsed",
            String.valueOf(engine.executeQuery("SELECT jp($$ {\"a\": [1, 2.5e3, \"x\", null, true]} $$)").getRows().get(0).getValue(0)));
    }

    @Test
    public void anUncaughtFaultQuotesTheJsonText() {
        assertUncaught(UNCAUGHT);
    }
}
