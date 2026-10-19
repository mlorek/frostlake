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

/**
 * Why a text is not JSON, in the words the account's JavaScript engine gives {@code JSON.parse} (live-verified):
 * the sentence, and the place in the text the sentence is about. The text is read the way that engine reads it -
 * a value, then nothing but whitespace - and the first thing it cannot take decides the sentence: an expectation
 * the reader had ({@code Expected ',' or ']' after array element}), a malformed number or string, or else the
 * unexpected character itself, echoed with the text around it.
 */
final class V8JsonFault {

    /** How much of the text an unexpected-token sentence echoes on each side of the token. */
    private static final int CONTEXT = 10;

    /** The texts a value turns into that the sentence names whole. */
    private static final String[] SPECIAL = {"[object Object]", "NaN", "Infinity", "undefined"};

    private final String text;
    private int at;
    private String message;
    private int place;

    private V8JsonFault(final String text) {
        this.text = text;
    }

    /**
     * Read a text as JSON.
     *
     * @param text the text
     * @return the fault, or null when the text is JSON
     */
    static V8JsonFault of(final String text) {
        final V8JsonFault reader = new V8JsonFault(text);
        reader.value();
        if (reader.message == null) {
            reader.whitespace();
            if (reader.at < text.length()) {
                reader.expected("Unexpected non-whitespace character after JSON");
            }
        }
        return reader.message == null ? null : reader;
    }

    /** The sentence, as the error's message. */
    String message() {
        return message;
    }

    /** The line of the text the fault is on. */
    String line() {
        int start = text.lastIndexOf('\n', place - 1) + 1;
        if (place == 0) {
            start = 0;
        }
        final int end = text.indexOf('\n', place);
        return text.substring(start, end < 0 ? text.length() : end);
    }

    /** The fault's column in its line, counted from 0. */
    int column() {
        return place == 0 ? 0 : place - (text.lastIndexOf('\n', place - 1) + 1);
    }

    private boolean failed() {
        return message != null;
    }

    private void whitespace() {
        while (at < text.length()) {
            final char c = text.charAt(at);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            at++;
        }
    }

    /** One value: the continuation of an object or an array is read by the caller of this method. */
    private void value() {
        whitespace();
        if (at >= text.length()) {
            unexpected();
            return;
        }
        final char c = text.charAt(at);
        if (c == '"') {
            string();
        } else if (c == '-' || c >= '0' && c <= '9') {
            number();
        } else if (c == '{') {
            object();
        } else if (c == '[') {
            array();
        } else if (c == 't') {
            literal("true");
        } else if (c == 'f') {
            literal("false");
        } else if (c == 'n') {
            literal("null");
        } else {
            unexpected();
        }
    }

    private void object() {
        at++;
        whitespace();
        if (at < text.length() && text.charAt(at) == '}') {
            at++;
            return;
        }
        String missingKey = "Expected property name or '}' in JSON";
        while (true) {
            whitespace();
            if (at >= text.length() || text.charAt(at) != '"') {
                expected(missingKey);
                return;
            }
            string();
            if (failed()) {
                return;
            }
            whitespace();
            if (at >= text.length() || text.charAt(at) != ':') {
                expected("Expected ':' after property name in JSON");
                return;
            }
            at++;
            value();
            if (failed()) {
                return;
            }
            whitespace();
            if (at < text.length() && text.charAt(at) == ',') {
                at++;
                missingKey = "Expected double-quoted property name in JSON";
                continue;
            }
            if (at >= text.length() || text.charAt(at) != '}') {
                expected("Expected ',' or '}' after property value in JSON");
                return;
            }
            at++;
            return;
        }
    }

    private void array() {
        at++;
        whitespace();
        if (at < text.length() && text.charAt(at) == ']') {
            at++;
            return;
        }
        while (true) {
            value();
            if (failed()) {
                return;
            }
            whitespace();
            if (at < text.length() && text.charAt(at) == ',') {
                at++;
                continue;
            }
            if (at >= text.length() || text.charAt(at) != ']') {
                expected("Expected ',' or ']' after array element in JSON");
                return;
            }
            at++;
            return;
        }
    }

    private void string() {
        at++;
        while (true) {
            if (at >= text.length()) {
                expected("Unterminated string in JSON");
                return;
            }
            final char c = text.charAt(at);
            if (c == '"') {
                at++;
                return;
            }
            if (c == '\\') {
                at++;
                final char escaped = at < text.length() ? text.charAt(at) : 0;
                if (escaped == 'u') {
                    for (int i = 0; i < 4; i++) {
                        at++;
                        if (at >= text.length() || Character.digit(text.charAt(at), 16) < 0) {
                            expected("Bad Unicode escape in JSON");
                            return;
                        }
                    }
                } else if ("\"\\/bfnrt".indexOf(escaped) < 0 || at >= text.length()) {
                    expected("Bad escaped character in JSON");
                    return;
                }
                at++;
            } else if (c < 0x20) {
                expected("Bad control character in string literal in JSON");
                return;
            } else {
                at++;
            }
        }
    }

    private void number() {
        if (text.charAt(at) == '-') {
            at++;
        }
        if (at < text.length() && text.charAt(at) == '0') {
            at++;
            if (at < text.length() && isDigit(text.charAt(at))) {
                expected("Unexpected number in JSON");
                return;
            }
        } else {
            final int digits = at;
            while (at < text.length() && isDigit(text.charAt(at))) {
                at++;
            }
            if (at == digits) {
                expected("No number after minus sign in JSON");
                return;
            }
        }
        if (at < text.length() && text.charAt(at) == '.') {
            at++;
            if (at >= text.length() || !isDigit(text.charAt(at))) {
                expected("Unterminated fractional number in JSON");
                return;
            }
            while (at < text.length() && isDigit(text.charAt(at))) {
                at++;
            }
        }
        if (at < text.length() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
            at++;
            if (at < text.length() && (text.charAt(at) == '-' || text.charAt(at) == '+')) {
                at++;
            }
            if (at >= text.length() || !isDigit(text.charAt(at))) {
                expected("Exponent part is missing a number in JSON");
                return;
            }
            while (at < text.length() && isDigit(text.charAt(at))) {
                at++;
            }
        }
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    private void literal(final String word) {
        for (int i = 0; i < word.length(); i++) {
            if (at >= text.length() || text.charAt(at) != word.charAt(i)) {
                unexpected();
                return;
            }
            at++;
        }
    }

    /** A fault the reader names by what it expected, with its position, line and column. */
    private void expected(final String sentence) {
        place = at;
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < at; i++) {
            final char c = text.charAt(i);
            if (c == '\n' || c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n')) {
                line++;
                lineStart = i + 1;
            }
        }
        message = sentence + " at position " + at + " (line " + line + " column " + (at - lineStart + 1) + ")";
    }

    /** A fault at a character no value starts with, or at the end of the text. */
    private void unexpected() {
        place = at;
        if (at >= text.length()) {
            message = "Unexpected end of JSON input";
            return;
        }
        for (final String special : SPECIAL) {
            if (special.equals(text)) {
                message = "\"" + text + "\" is not valid JSON";
                return;
            }
        }
        final String token = "Unexpected token '" + text.charAt(at) + "', ";
        if (text.length() < CONTEXT * 2 + 1) {
            message = token + "\"" + text + "\" is not valid JSON";
        } else if (at < CONTEXT) {
            message = token + "\"" + text.substring(0, at + CONTEXT) + "\"... is not valid JSON";
        } else if (at < text.length() - CONTEXT) {
            message = token + "...\"" + text.substring(at - CONTEXT, at + CONTEXT) + "\"... is not valid JSON";
        } else {
            message = token + "...\"" + text.substring(at - CONTEXT) + "\" is not valid JSON";
        }
    }
}
