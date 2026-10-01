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

package dev.frostlake.http.rest.resource;

import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A function's or procedure's {@code {nameWithArgs}} path parameter: the routine's name and the argument types that
 * name one overload. The API writes the arguments as types ({@code foo(NUMBER,VARCHAR)}) or the way a CREATE writes
 * them ({@code foo(a number, b number)}); the list goes into the statement as written, so the second form meets the
 * statement's own refusal of a named argument. A bare name carries no argument list, which the endpoints that take a
 * routine's plain name use.
 */
public final class RoutineSignature {

    /** The words that open a data type spelled in more than one word, so they are never read as an argument name. */
    private static final String[] MULTI_WORD_TYPES = {"DOUBLE", "CHARACTER", "CHAR", "NATIONAL", "NCHAR", "LONG"};

    private final RestIdentifier name;
    private final List<String> types;

    private RoutineSignature(final RestIdentifier name, final List<String> types) {
        this.name = name;
        this.types = types;
    }

    /**
     * Reads a {@code {nameWithArgs}} parameter.
     *
     * @param text the parameter as written
     * @param what the parameter's name, for the refusal
     * @return the signature
     * @throws RestException {@code 400} when the text is not a name optionally followed by an argument list
     */
    public static RoutineSignature parse(final String text, final String what) {
        if (text == null || text.isBlank()) {
            throw RestException.badRequest("Missing " + what + ".");
        }
        final String trimmed = text.trim();
        final int open = argumentListStart(trimmed);
        if (open < 0) {
            return new RoutineSignature(RestIdentifier.parse(trimmed, what), null);
        }
        if (trimmed.charAt(trimmed.length() - 1) != ')') {
            throw RestException.badRequest("Invalid " + what + " '" + text + "': the argument list must end the name.");
        }
        final RestIdentifier routine = RestIdentifier.parse(trimmed.substring(0, open).trim(), what);
        final List<String> argumentTypes = new ArrayList<>();
        for (final String item : splitList(trimmed.substring(open + 1, trimmed.length() - 1))) {
            argumentTypes.add(checkType(item, what));
        }
        return new RoutineSignature(routine, argumentTypes);
    }

    /** The routine's name. */
    public RestIdentifier name() {
        return name;
    }

    /** Whether the parameter carried an argument list. */
    public boolean hasArguments() {
        return types != null;
    }

    /** The arguments as written, upper-cased; empty when the parameter carried no list. */
    public List<String> types() {
        return types == null ? Collections.<String>emptyList() : types;
    }

    /** The argument types as the parenthesized list a DROP, DESCRIBE or ALTER takes. */
    public String typesSql() {
        return "(" + String.join(", ", types()) + ")";
    }

    /** Where the argument list opens, past a quoted name; -1 when there is none. */
    private static int argumentListStart(final String text) {
        int i = 0;
        if (text.charAt(0) == '"') {
            i = 1;
            while (i < text.length()) {
                if (text.charAt(i) == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        i += 2;
                        continue;
                    }
                    break;
                }
                i++;
            }
        }
        return text.indexOf('(', i);
    }

    /**
     * Splits a comma-separated list at its top level, leaving commas inside parentheses (a type's precision and
     * scale) in place. An empty or blank list has no items.
     */
    public static List<String> splitList(final String list) {
        final List<String> items = new ArrayList<>();
        if (list == null || list.isBlank()) {
            return items;
        }
        int depth = 0;
        int start = 0;
        for (int i = 0; i < list.length(); i++) {
            final char c = list.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                items.add(list.substring(start, i).trim());
                start = i + 1;
            }
        }
        items.add(list.substring(start).trim());
        return items;
    }

    /** The words of an argument, split at blanks outside parentheses. */
    private static List<String> words(final String item) {
        final List<String> words = new ArrayList<>();
        final StringBuilder word = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < item.length(); i++) {
            final char c = item.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            }
            if (Character.isWhitespace(c) && depth == 0) {
                if (word.length() > 0) {
                    words.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(c);
            }
        }
        if (word.length() > 0) {
            words.add(word.toString());
        }
        return words;
    }

    /** An argument's name, or null when the argument is written as its type alone. */
    public static String nameOf(final String item) {
        final List<String> words = words(item);
        if (words.size() < 2) {
            return null;
        }
        final String first = words.get(0).toUpperCase(Locale.ROOT);
        for (final String leader : MULTI_WORD_TYPES) {
            if (leader.equals(first)) {
                return null;
            }
        }
        return words.get(0);
    }

    /** An argument's type: the argument without the name it may be written with. */
    public static String typeOf(final String item) {
        final List<String> words = words(item);
        final int from = nameOf(item) == null ? 0 : 1;
        final List<String> typeWords = new ArrayList<>();
        for (int i = from; i < words.size(); i++) {
            typeWords.add(words.get(i));
        }
        return String.join(" ", typeWords);
    }

    /**
     * Checks a data type written in a request before it goes into a statement: letters, digits, underscores, blanks,
     * and a parenthesized precision.
     *
     * @return the type, upper-cased
     * @throws RestException {@code 400} for anything else
     */
    public static String checkType(final String type, final String what) {
        if (type == null || type.isBlank()) {
            throw RestException.badRequest("Missing data type in " + what + ".");
        }
        for (int i = 0; i < type.length(); i++) {
            final char c = type.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == ' ' || c == '(' || c == ')' || c == ',')) {
                throw RestException.badRequest("Invalid data type '" + type + "' in " + what + ".");
            }
        }
        return type.trim().toUpperCase(Locale.ROOT);
    }
}
