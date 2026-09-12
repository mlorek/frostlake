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

package dev.frostlake.testkit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal JSON reader and string escaper, so the testkit needs no third-party jar. Objects parse to
 * {@link LinkedHashMap}, arrays to {@link ArrayList}, strings to String, numbers to BigDecimal,
 * true/false to Boolean and null to null.
 */
public final class Json {

    private final String text;
    private int at;

    private Json(final String text) {
        this.text = text;
    }

    /**
     * Parse one JSON document.
     *
     * @param text the document
     * @return its value tree
     */
    public static Object parse(final String text) {
        final Json reader = new Json(text);
        reader.skipWhitespace();
        final Object value = reader.value();
        reader.skipWhitespace();
        if (reader.at < reader.text.length()) {
            throw reader.error("trailing content");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(final Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(final Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    public static String getStr(final Map<String, Object> object, final String key) {
        final Object value = object == null ? null : object.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static Integer getInt(final Map<String, Object> object, final String key) {
        final Object value = object == null ? null : object.get(key);
        return value instanceof BigDecimal ? Integer.valueOf(((BigDecimal) value).intValue()) : null;
    }

    public static Boolean getBool(final Map<String, Object> object, final String key) {
        final Object value = object == null ? null : object.get(key);
        return value instanceof Boolean ? (Boolean) value : null;
    }

    public static Map<String, Object> getObj(final Map<String, Object> object, final String key) {
        return object == null ? null : obj(object.get(key));
    }

    public static List<Object> getArr(final Map<String, Object> object, final String key) {
        return object == null ? null : arr(object.get(key));
    }

    private Object value() {
        final char c = peek();
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        final Map<String, Object> members = new LinkedHashMap<>();
        at++;
        skipWhitespace();
        if (peek() == '}') {
            at++;
            return members;
        }
        while (true) {
            if (peek() != '"') {
                throw error("expected key");
            }
            final String key = string();
            skipWhitespace();
            if (peek() != ':') {
                throw error("expected ':'");
            }
            at++;
            skipWhitespace();
            members.put(key, value());
            skipWhitespace();
            final char c = peek();
            if (c == '}') {
                at++;
                return members;
            }
            if (c != ',') {
                throw error("expected ',' or '}'");
            }
            at++;
            skipWhitespace();
        }
    }

    private List<Object> array() {
        final List<Object> items = new ArrayList<>();
        at++;
        skipWhitespace();
        if (peek() == ']') {
            at++;
            return items;
        }
        while (true) {
            items.add(value());
            skipWhitespace();
            final char c = peek();
            if (c == ']') {
                at++;
                return items;
            }
            if (c != ',') {
                throw error("expected ',' or ']'");
            }
            at++;
            skipWhitespace();
        }
    }

    private String string() {
        final StringBuilder b = new StringBuilder();
        at++;
        while (true) {
            if (at >= text.length()) {
                throw error("unterminated string");
            }
            final char c = text.charAt(at++);
            if (c == '"') {
                return b.toString();
            }
            if (c != '\\') {
                b.append(c);
                continue;
            }
            final char escaped = text.charAt(at++);
            switch (escaped) {
                case '"':
                    b.append('"');
                    break;
                case '\\':
                    b.append('\\');
                    break;
                case '/':
                    b.append('/');
                    break;
                case 'n':
                    b.append('\n');
                    break;
                case 't':
                    b.append('\t');
                    break;
                case 'r':
                    b.append('\r');
                    break;
                case 'b':
                    b.append('\b');
                    break;
                case 'f':
                    b.append('\f');
                    break;
                case 'u':
                    b.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                    break;
                default:
                    throw error("bad escape \\" + escaped);
            }
        }
    }

    private BigDecimal number() {
        final int start = at;
        if (peek() == '-') {
            at++;
        }
        while (at < text.length() && "0123456789.eE+-".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        try {
            return new BigDecimal(text.substring(start, at));
        } catch (final NumberFormatException bad) {
            throw error("bad number");
        }
    }

    private void expect(final String word) {
        if (!text.startsWith(word, at)) {
            throw error("expected " + word);
        }
        at += word.length();
    }

    private char peek() {
        if (at >= text.length()) {
            throw error("unexpected end");
        }
        return text.charAt(at);
    }

    private void skipWhitespace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private IllegalArgumentException error(final String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(at, text.length()); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new IllegalArgumentException("JSON error at line " + line + ", col " + column + ": " + message);
    }

    /**
     * A JSON string literal for {@code value}, quotes included — for request bodies.
     *
     * @param value the text
     * @return its quoted, escaped spelling
     */
    public static String escape(final String value) {
        final StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }
}
