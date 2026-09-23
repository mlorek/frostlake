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

package dev.frostlake.executor.udf;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Whether a name can be declared in JavaScript. A JavaScript handler is a function declared under its
 * routine's name, taking the routine's arguments as its parameters, so the account refuses a routine or an
 * argument whose canonical name is no identifier, or is a reserved word: {@code "delete"}, {@code "class"},
 * {@code "await"} and {@code "my fn"} are refused, while {@code "arguments"}, {@code "eval"},
 * {@code "async"}, {@code "NaN"}, {@code "a$b"} and a non-ASCII letter are declared (live-verified). An
 * unquoted name folds to upper case, and no reserved word is upper case, so only a quoted name can fail.
 */
public final class JavaScriptIdentifiers {

    /** The words no declaration may take: every reserved word of strict code, and {@code await}. */
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
        "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do",
        "else", "enum", "export", "extends", "false", "finally", "for", "function", "if", "implements", "import",
        "in", "instanceof", "interface", "let", "new", "null", "package", "private", "protected", "public",
        "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void", "while",
        "with", "yield"));

    private JavaScriptIdentifiers() {
    }

    /**
     * Whether JavaScript can declare this name: an identifier, its letters possibly written as
     * {@code \}{@code uXXXX} escapes, that spells no reserved word.
     *
     * @param name the canonical name
     * @return whether a function or a parameter can take it
     */
    public static boolean isDeclarable(final String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        final StringBuilder decoded = new StringBuilder();
        int i = 0;
        while (i < name.length()) {
            final int codePoint;
            if (name.charAt(i) == '\\') {
                final int end = escapeEnd(name, i);
                if (end < 0) {
                    return false;
                }
                codePoint = escapedCodePoint(name, i, end);
                i = end;
            } else {
                codePoint = name.codePointAt(i);
                i += Character.charCount(codePoint);
            }
            if (codePoint < 0 || !(decoded.length() == 0 ? startsIdentifier(codePoint) : continuesIdentifier(codePoint))) {
                return false;
            }
            decoded.appendCodePoint(codePoint);
        }
        return !RESERVED.contains(decoded.toString());
    }

    /** The index just past a {@code \}{@code uXXXX} or {@code \}{@code u{X…}} escape, or -1 for no escape. */
    private static int escapeEnd(final String name, final int at) {
        if (at + 1 >= name.length() || name.charAt(at + 1) != 'u') {
            return -1;
        }
        if (at + 2 < name.length() && name.charAt(at + 2) == '{') {
            final int close = name.indexOf('}', at + 3);
            return close > at + 3 ? close + 1 : -1;
        }
        return at + 6 <= name.length() ? at + 6 : -1;
    }

    /** The code point an escape names, or -1 when its digits are not hexadecimal. */
    private static int escapedCodePoint(final String name, final int at, final int end) {
        final String digits = name.charAt(at + 2) == '{' ? name.substring(at + 3, end - 1) : name.substring(at + 2, end);
        try {
            final int codePoint = Integer.parseInt(digits, 16);
            return codePoint <= Character.MAX_CODE_POINT ? codePoint : -1;
        } catch (final NumberFormatException notHexadecimal) {
            return -1;
        }
    }

    private static boolean startsIdentifier(final int codePoint) {
        return codePoint == '$' || codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint);
    }

    private static boolean continuesIdentifier(final int codePoint) {
        return codePoint == '$' || codePoint == '_' || codePoint == 0x200C || codePoint == 0x200D
            || Character.isUnicodeIdentifierPart(codePoint) && !Character.isIdentifierIgnorable(codePoint);
    }
}
