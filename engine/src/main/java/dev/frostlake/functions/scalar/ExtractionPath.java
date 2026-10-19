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

package dev.frostlake.functions.scalar;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * A path into a semi-structured value, as {@code GET_PATH} and FLATTEN's {@code PATH} read one when the
 * statement runs.
 *
 * <p>A path is a chain of steps (live-verified):
 * <pre>
 *   name            a member, written bare: letters, digits, _ $ @ and anything past ASCII, no digit first
 *   "name"          a member, quoted; \ escapes the next character and a doubled quote stands for one
 *   ['name']        the same inside brackets, where either quote opens it
 *   [3]             an element; a minus sign is read, and only -0 names an element
 * </pre>
 * A {@code .} or a {@code :} goes between two steps and may come first; a bracket and a quoted name need
 * none, so {@code a"b"} is {@code a.b}. Spaces and tabs may follow any step and fill a bracket, but only
 * ONE may follow a separator, and none may come first. Everything else is malformed: {@code a.},
 * {@code a..b}, {@code a[}, {@code a[x]}, {@code a[+1]}, {@code "a"b}, a single-quoted name outside a
 * bracket.
 *
 * <p>The stricter reading a path written as a literal is given before the statement runs is
 * {@link ExtractionPathSyntax}.
 */
public final class ExtractionPath {

    private final List<ExtractionPathStep> steps;

    ExtractionPath(final List<ExtractionPathStep> steps) {
        this.steps = steps;
    }

    /**
     * Reads a path.
     *
     * @param text the path as written
     * @return the path, or null when the text is malformed
     */
    public static ExtractionPath read(final String text) {
        return new ExtractionPathReader(text).read();
    }

    /**
     * The refusal of a path a function could not read.
     *
     * @param text     the path as written
     * @param function the function that was handed it
     * @return the sentence
     */
    public static String malformed(final String text, final String function) {
        return "Bad compound object's field path name '" + text + "' in " + function;
    }

    /**
     * What the path reaches inside a value.
     *
     * @param root the value the path starts from
     * @return the value at the path's end, or null when a step finds nothing
     */
    public JsonNode in(final JsonNode root) {
        JsonNode node = root;
        for (final ExtractionPathStep step : steps) {
            node = step.in(node);
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    /**
     * The path as FLATTEN's PATH column spells it, whatever spelling it was written in: {@code k['m']} is
     * {@code k.m} and {@code "a b".c} is {@code ['a b'].c}.
     *
     * @return the path's canonical text
     */
    public String canonical() {
        final StringBuilder path = new StringBuilder();
        for (final ExtractionPathStep step : steps) {
            step.appendTo(path);
        }
        return path.toString();
    }

    /**
     * The path of an object's member, given its object's path.
     *
     * @param parent the object's path, empty for the root
     * @param key    the member's name
     * @return the member's path
     */
    public static String member(final String parent, final String key) {
        final StringBuilder path = new StringBuilder(parent);
        appendMember(path, key);
        return path.toString();
    }

    /**
     * Adds a member's name to a path: bare after a dot when it is a plain name, between brackets and single
     * quotes when it is not — an empty name, a digit first, or any character that is no letter, digit or
     * underscore ({@code ['a b']}, {@code ['a$']}, {@code ['0']}, {@code ['']}). Inside the quotes a quote,
     * a backslash and a control character are escaped (live-verified).
     */
    static void appendMember(final StringBuilder path, final String key) {
        if (isPlainName(key)) {
            if (path.length() > 0) {
                path.append('.');
            }
            path.append(key);
            return;
        }
        path.append("['");
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            switch (c) {
                case '\'':
                    path.append("\\'");
                    break;
                case '\\':
                    path.append("\\\\");
                    break;
                case '\n':
                    path.append("\\n");
                    break;
                case '\t':
                    path.append("\\t");
                    break;
                case '\r':
                    path.append("\\r");
                    break;
                case '\b':
                    path.append("\\b");
                    break;
                case '\f':
                    path.append("\\f");
                    break;
                default:
                    path.append(c);
                    break;
            }
        }
        path.append("']");
    }

    private static boolean isPlainName(final String key) {
        if (key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            final char c = key.charAt(i);
            final boolean letter = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || c >= 0x80;
            final boolean digit = c >= '0' && c <= '9';
            if (!letter && !(digit && i > 0)) {
                return false;
            }
        }
        return true;
    }
}
