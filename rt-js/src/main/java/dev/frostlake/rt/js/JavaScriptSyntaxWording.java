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

import dev.frostlake.executor.udf.JavaScriptIdentifiers;
import dev.frostlake.metastore.model.Parameter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.Compilable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

/**
 * The sentence a JavaScript function's body that does not parse reaches SQL with, as the account words it
 * (live-verified): {@code JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in F at
 * 'return (;' position 8}, with no stack lines. The body is read as the account reads it: a scalar function's as
 * the body of a function of its parameters, whose closing brace stands on a line of its own after the body - so a
 * fault past the body's end is quoted there, {@code '}'}, at position 0, or 1 for the end of the text - and a table
 * function's as the parenthesized expression it is. The engine's sentence is given the account's words: a token
 * the parser did not expect is named by its kind ({@code Unexpected number}, {@code Unexpected string},
 * {@code Unexpected identifier 'B'}, {@code Unexpected token ';'}, {@code Unexpected end of input}); a text, a
 * number or a character no token starts is {@code Invalid or unexpected token} where it starts; and the parser's
 * other sentences read as the account's.
 */
final class JavaScriptSyntaxWording {

    private static final Pattern PLACED = Pattern.compile("<eval>:(\\d+):(\\d+) ([^\\n]*)");
    private static final Pattern FOUND = Pattern.compile("Expected .+ but found (.+)");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern PRIVATE_NAME = Pattern.compile("#[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern LET_BOUND = Pattern.compile("\\b(?:let|const)\\s+(let)\\b");

    /** The words the account's parser calls a token, not an identifier, when it did not expect one. */
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
        "break", "case", "catch", "class", "const", "continue", "debugger", "default", "delete", "do", "else",
        "enum", "export", "extends", "false", "finally", "for", "function", "if", "import", "in", "instanceof",
        "new", "null", "return", "super", "switch", "this", "throw", "true", "try", "typeof", "var", "void",
        "while", "with"));

    private JavaScriptSyntaxWording() {
    }

    /**
     * @param engine     the engine the body would run in
     * @param name       the function's name as the catalog holds it
     * @param parameters the function's parameters
     * @param body       the body as written
     * @param table      whether the function is a table function
     * @return the sentence, or null when the body parses as the account reads it
     */
    static String of(final ScriptEngine engine, final String name, final List<Parameter> parameters,
                     final String body, final boolean table) {
        if (!(engine instanceof Compilable)) {
            return null;
        }
        final String program = table ? "(" + body + ")" : header(parameters) + body + "\n}";
        try {
            ((Compilable) engine).compile(program);
            return null;
        } catch (final ScriptException unparsed) {
            final Matcher placed = PLACED.matcher(String.valueOf(unparsed.getMessage()));
            if (!placed.find()) {
                return null;
            }
            final String[] lines = program.split("\n", -1);
            final int line = Integer.parseInt(placed.group(1));
            if (line < 1 || line > lines.length || !table && line == 1) {
                return null;
            }
            final String text = lines[line - 1];
            final int column = Math.min(Integer.parseInt(placed.group(2)), text.length());
            final boolean pastBody = !table && line == lines.length;
            final String[] worded = worded(placed.group(3), text, column, pastBody);
            return "JavaScript compilation error: Uncaught SyntaxError: " + worded[0] + " in " + name + " at '"
                + text + "' position " + worded[1];
        }
    }

    /** The function a scalar body is read inside: its parameters as the handler declares them, the body below. */
    private static String header(final List<Parameter> parameters) {
        final StringBuilder declared = new StringBuilder("function f(");
        for (int i = 0; i < parameters.size(); i++) {
            final String parameterName = parameters.get(i).getName();
            declared.append(i > 0 ? ", " : "")
                .append(JavaScriptIdentifiers.isDeclarable(parameterName) ? parameterName : "__fl_p" + i);
        }
        return declared.append(") {\n").toString();
    }

    /**
     * The account's sentence and place for the parser's.
     *
     * @param said     the parser's sentence
     * @param text     the line it placed the fault on
     * @param column   the column it placed it at
     * @param pastBody whether that line is the closing brace after the body
     * @return the sentence and the position
     */
    private static String[] worded(final String said, final String text, final int column, final boolean pastBody) {
        if (said.startsWith("Missing close quote")) {
            // A text runs to the line's end, a template to the end of the whole text.
            return pastBody ? placed("Unexpected end of input", column)
                : placed("Invalid or unexpected token", quoteBefore(text, column));
        }
        if (said.startsWith("Missing digit after") || said.startsWith("Missing space after numeric literal")
                || said.startsWith("Invalid hex digit") || said.startsWith("Invalid octal digit")) {
            return placed("Invalid or unexpected token", numberStart(text, column));
        }
        if (said.startsWith("Invalid left hand side for assignment")) {
            final String after = text.substring(Math.min(text.length(), column)).replaceAll("^[^+\\-=]*", "");
            return placed(after.startsWith("++") || after.startsWith("--")
                ? "Invalid left-hand side expression in postfix operation" : "Invalid left-hand side in assignment",
                column);
        }
        if (said.startsWith("Illegal continue statement")) {
            return placed("Illegal continue statement: no surrounding iteration statement", column);
        }
        if (said.startsWith("let is disallowed as a lexically bound name")) {
            final Matcher bound = LET_BOUND.matcher(text);
            return placed("let is disallowed as a lexically bound name", bound.find() ? bound.start(1) : column);
        }
        if (said.startsWith("expected BindingIdentifier or BindingPattern")) {
            return unexpected(null, text, column);
        }
        final Matcher found = FOUND.matcher(said);
        if (found.matches() || said.startsWith("Expected an operand but found ")) {
            final String token = found.matches() ? found.group(1)
                : said.substring("Expected an operand but found ".length());
            final int invalid = invalidCharacterBefore(text, column);
            if (invalid >= 0) {
                return placed("Invalid or unexpected token", invalid);
            }
            if ("(".equals(token) && said.startsWith("Expected ident") && endsWithWord(text, column, "function")) {
                return placed("Function statements require a function name", text.lastIndexOf("function", column));
            }
            if ("/".equals(token) || "/=".equals(token)) {
                return placed("Invalid regular expression: missing /", column);
            }
            return unexpected(token, text, column);
        }
        return placed(said, column);
    }

    /** A token the parser did not expect, named by its kind as the account names it. */
    private static String[] unexpected(final String token, final String text, final int column) {
        if ("eof".equals(token)) {
            return placed("Unexpected end of input", column);
        }
        if ("private_ident".equals(token)) {
            final Matcher name = PRIVATE_NAME.matcher(text);
            final String written = name.find(column) ? name.group() : "#";
            return placed("Private field '" + written + "' must be declared in an enclosing class", 0);
        }
        final char at = column < text.length() ? text.charAt(column) : '\0';
        final char before = column > 0 ? text.charAt(column - 1) : '\0';
        if ((before == '\'' || before == '"') && opensText(text, column - 1)) {
            // The parser places a text at its first character, the account at its opening quote.
            return placed("Unexpected string", column - 1);
        }
        if (at == '\'' || at == '"') {
            return placed("Unexpected string", column);
        }
        if (at == '`') {
            return placed("Unexpected template string", column);
        }
        if (Character.isDigit(at) || at == '.' && column + 1 < text.length()
                && Character.isDigit(text.charAt(column + 1))) {
            return placed("Unexpected number", column);
        }
        final Matcher word = IDENTIFIER.matcher(text);
        if (word.find(column) && word.start() == column) {
            final String written = word.group();
            return placed(RESERVED.contains(written) ? "Unexpected token '" + written + "'"
                : "Unexpected identifier '" + written + "'", column);
        }
        return placed("Unexpected token '" + (token != null ? token : String.valueOf(at)) + "'", column);
    }

    private static String[] placed(final String sentence, final int position) {
        return new String[] {sentence, String.valueOf(Math.max(position, 0))};
    }

    /** Where the text the column falls in starts: its opening quote. */
    private static int quoteBefore(final String text, final int column) {
        for (int i = Math.min(column, text.length()) - 1; i >= 0; i--) {
            final char c = text.charAt(i);
            if (c == '\'' || c == '"') {
                return i;
            }
        }
        return column;
    }

    /** Whether the quote at the index opens a text: an even number of its kind stand unescaped before it. */
    private static boolean opensText(final String text, final int index) {
        final char quote = text.charAt(index);
        int seen = 0;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\\') {
                i++;
            } else if (text.charAt(i) == quote) {
                seen++;
            }
        }
        return seen % 2 == 0;
    }

    /** Where the number the column falls in, or ends at, starts. */
    private static int numberStart(final String text, final int column) {
        int start = Math.min(column, text.length());
        while (start > 0 && (Character.isLetterOrDigit(text.charAt(start - 1)) || text.charAt(start - 1) == '_'
                || text.charAt(start - 1) == '.')) {
            start--;
        }
        return start;
    }

    /** A character no token starts with, standing just before the column past spaces, or -1. */
    private static int invalidCharacterBefore(final String text, final int column) {
        int i = Math.min(column, text.length()) - 1;
        while (i >= 0 && text.charAt(i) == ' ') {
            i--;
        }
        return i >= 0 && text.charAt(i) == '@' ? i : -1;
    }

    /** Whether the text before the column, past spaces, ends with the word. */
    private static boolean endsWithWord(final String text, final int column, final String word) {
        return text.substring(0, Math.min(column, text.length())).trim().endsWith(word);
    }
}
