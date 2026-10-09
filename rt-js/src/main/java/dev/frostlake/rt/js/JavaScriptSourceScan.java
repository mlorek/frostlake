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

import java.util.Arrays;

/**
 * A reading of a piece of JavaScript source that tells code from the text of a string, a template, a regular
 * expression or a comment, and pairs each bracket with its partner. The places an uncaught error is reported at
 * are found on it: the bracket that opens a call's arguments, the operator an expression turns on, where a
 * statement starts.
 */
final class JavaScriptSourceScan {

    private final String text;
    /** Whether each character is code: not inside a string, a template's text, a regular expression or a comment. */
    private final boolean[] code;
    /** For a closing bracket, the index of the bracket that opens it; -1 elsewhere. */
    private final int[] opener;
    /** For an opening bracket, the index of the bracket that closes it; -1 elsewhere, and for one left open. */
    private final int[] closer;

    JavaScriptSourceScan(final String text) {
        this.text = text;
        this.code = new boolean[text.length()];
        this.opener = new int[text.length()];
        this.closer = new int[text.length()];
        Arrays.fill(opener, -1);
        Arrays.fill(closer, -1);
        read();
    }

    private void read() {
        final int[] stack = new int[text.length() + 1];
        int depth = 0;
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            final char next = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
            if (c == '\'' || c == '"' || c == '`') {
                // The quotes are code, what they hold is not; a template's ${ } is read as text too.
                code[i] = true;
                int end = i + 1;
                while (end < text.length() && text.charAt(end) != c) {
                    end += text.charAt(end) == '\\' ? 2 : 1;
                }
                if (end < text.length()) {
                    code[end] = true;
                }
                i = end + 1;
            } else if (c == '/' && next == '/') {
                while (i < text.length() && text.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                final int end = text.indexOf("*/", i + 2);
                i = end < 0 ? text.length() : end + 2;
            } else if (c == '/' && startsPattern(i)) {
                // A regular expression: its slashes are code, its pattern is not.
                code[i] = true;
                int end = i + 1;
                boolean set = false;
                while (end < text.length() && text.charAt(end) != '\n' && (set || text.charAt(end) != '/')) {
                    final char p = text.charAt(end);
                    if (p == '\\') {
                        end++;
                    } else if (p == '[') {
                        set = true;
                    } else if (p == ']') {
                        set = false;
                    }
                    end++;
                }
                if (end < text.length() && text.charAt(end) == '/') {
                    code[end] = true;
                }
                i = end + 1;
            } else {
                code[i] = true;
                if (c == '(' || c == '[' || c == '{') {
                    stack[depth++] = i;
                } else if ((c == ')' || c == ']' || c == '}') && depth > 0) {
                    opener[i] = stack[--depth];
                    closer[opener[i]] = i;
                }
                i++;
            }
        }
    }

    /** Whether a slash starts a regular expression: it stands where a value is expected. */
    private boolean startsPattern(final int slash) {
        final int before = lastBefore(slash);
        if (before < 0) {
            return true;
        }
        final char c = text.charAt(before);
        if (isNamePart(c)) {
            return isWord(nameStart(before), before + 1, "return", "typeof", "case", "in", "of", "delete", "void",
                "throw", "new", "do", "else", "instanceof", "yield", "await");
        }
        return "(,=:[!&|?{};+-*%<>~^".indexOf(c) >= 0;
    }

    /** The bracket that opens the one closing at an index, or -1. */
    int opening(final int close) {
        return close >= 0 && close < opener.length ? opener[close] : -1;
    }

    /** The bracket that closes the one opening at an index, or -1 when it is left open. */
    int closing(final int open) {
        return open >= 0 && open < closer.length ? closer[open] : -1;
    }

    /** Whether the character at an index is code. */
    boolean isCode(final int index) {
        return index >= 0 && index < code.length && code[index];
    }

    static boolean isNamePart(final char c) {
        return c == '_' || c == '$' || c == '#' || Character.isLetterOrDigit(c);
    }

    /** The last index before {@code end} that is code and no blank, or -1. */
    int lastBefore(final int end) {
        int i = Math.min(end, text.length()) - 1;
        while (i >= 0 && (!code[i] || Character.isWhitespace(text.charAt(i)))) {
            i--;
        }
        return i;
    }

    /** The first index from {@code from} on that is code and no blank, or the text's length. */
    int firstFrom(final int from) {
        int i = Math.max(0, from);
        while (i < text.length() && (!code[i] || Character.isWhitespace(text.charAt(i)))) {
            i++;
        }
        return i;
    }

    /** The start of the name whose last character is at an index. */
    int nameStart(final int last) {
        int i = last;
        while (i > 0 && code[i - 1] && isNamePart(text.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    /** The end of the name that starts at an index: the index after its last character. */
    int nameEnd(final int start) {
        int i = start;
        while (i < text.length() && code[i] && isNamePart(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Whether the code at an index starts a name: a letter, an underscore or a dollar sign after no name part. */
    boolean startsName(final int index) {
        if (!isCode(index)) {
            return false;
        }
        final char c = text.charAt(index);
        return (c == '_' || c == '$' || c == '#' || Character.isLetter(c))
            && (index == 0 || !code[index - 1] || !isNamePart(text.charAt(index - 1)));
    }

    /** The word that ends right before an index, blanks and comments aside, or an empty text. */
    String wordBefore(final int index) {
        final int last = lastBefore(index);
        if (last < 0 || !isNamePart(text.charAt(last))) {
            return "";
        }
        return text.substring(nameStart(last), last + 1);
    }

    /** Where the {@code new} that stands right before an index starts, or -1 when none does. */
    int newBefore(final int index) {
        final int last = lastBefore(index);
        return "new".equals(wordBefore(index)) ? last - 2 : -1;
    }

    /**
     * Where the call whose arguments open at a bracket is placed: at the name written right before them, a
     * member's or a plain one; at the {@code new} a constructor's plain or dotted name follows; and at the bracket
     * itself when anything else is called - a parenthesized expression, a literal, the result of another call.
     */
    int callPlaceAt(final int open) {
        final int before = lastBefore(open);
        if (before < 0) {
            return open;
        }
        final char c = text.charAt(before);
        if (c == ')' && opening(before) >= 0) {
            final int built = newBefore(opening(before));
            return built >= 0 ? built : open;
        }
        if (!isNamePart(c)) {
            return open;
        }
        final int start = nameStart(before);
        if (Character.isDigit(text.charAt(start)) || isWord(start, before + 1, "null", "true", "false", "this")) {
            return open;
        }
        // The dotted name this one ends: a constructor's when a new stands before it.
        int chain = start;
        while (true) {
            final int dot = lastBefore(chain);
            if (dot < 0 || text.charAt(dot) != '.') {
                break;
            }
            final int receiver = lastBefore(dot);
            if (receiver < 0 || !isNamePart(text.charAt(receiver))) {
                return start;
            }
            chain = nameStart(receiver);
        }
        final int built = newBefore(chain);
        return built >= 0 ? built : start;
    }

    /**
     * Where a call is placed, for a text that is one: see {@link #callPlaceAt}.
     *
     * @return the place within the text, or 0 when the text is no call
     */
    int callPlace() {
        final int close = lastBefore(text.length());
        if (close < 0 || text.charAt(close) != ')' || opening(close) < 0) {
            return 0;
        }
        return callPlaceAt(opening(close));
    }

    /** The text that names what a call calls - {@code Math.max} - or null when it is no plain dotted name. */
    String calledName() {
        final int close = lastBefore(text.length());
        if (close < 0 || text.charAt(close) != ')' || opening(close) < 0) {
            return null;
        }
        final String callee = text.substring(0, opening(close)).trim();
        if (callee.isEmpty() || Character.isDigit(callee.charAt(0))) {
            return null;
        }
        for (int i = 0; i < callee.length(); i++) {
            final char c = callee.charAt(i);
            if (c != '.' && !isNamePart(c) || c == '#') {
                return null;
            }
        }
        return callee.endsWith(".") || callee.startsWith(".") || callee.contains("..") ? null : callee;
    }

    /** The text of a call's only argument, or null when the text is no call or the call takes several. */
    String soleArgument() {
        final int close = lastBefore(text.length());
        if (close < 0 || text.charAt(close) != ')' || opening(close) < 0) {
            return null;
        }
        final int open = opening(close);
        int i = open + 1;
        while (i < close) {
            if (code[i] && closer[i] > i) {
                i = closer[i] + 1;
                continue;
            }
            if (code[i] && text.charAt(i) == ',') {
                return null;
            }
            i++;
        }
        return text.substring(open + 1, close).trim();
    }

    boolean isWord(final int start, final int end, final String... words) {
        final String word = text.substring(start, end);
        for (final String candidate : words) {
            if (candidate.equals(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The operator a binary expression turns on: of the operators written outside every bracket, one of the
     * loosest-binding, the last of them, or the first for {@code **}, which groups to the right.
     *
     * @return the operator's index, or -1 when the text holds no binary operator
     */
    int rootOperator() {
        int best = -1;
        int bestRank = Integer.MAX_VALUE;
        int depth = 0;
        boolean operand = false;
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (!code[i]) {
                operand = true;
                i++;
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == ')' || c == ']' || c == '}') {
                depth--;
                operand = true;
                i++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (isNamePart(c) || c == '\'' || c == '"' || c == '`' || c == '.') {
                final int start = i;
                while (i < text.length() && code[i] && (isNamePart(text.charAt(i)) || text.charAt(i) == '.')) {
                    i++;
                }
                if (i == start) {
                    i++;
                }
                final String word = text.substring(start, Math.min(i, text.length()));
                final boolean keyword = "instanceof".equals(word) || "in".equals(word);
                if (keyword && depth == 0 && operand && 8 <= bestRank) {
                    best = start;
                    bestRank = 8;
                }
                operand = !keyword && !isWord(start, Math.min(i, text.length()), "typeof", "void", "delete", "new");
                continue;
            }
            final int length = operatorLength(i);
            final int rank = length == 0 ? -1 : rank(text.substring(i, i + length));
            if (rank > 0 && depth == 0 && operand && (rank < bestRank || rank == bestRank && rank != 12)) {
                best = i;
                bestRank = rank;
            }
            if (rank > 0 && operand || length == 0) {
                operand = false;
            }
            i += Math.max(1, length);
        }
        return best;
    }

    /** The length of the binary operator written at an index, or 0 when none is, or an assignment is. */
    int operatorLength(final int at) {
        final String[] operators = {">>>", "===", "!==", "**", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "??",
            "+", "-", "*", "/", "%", "<", ">", "&", "|", "^"};
        for (final String operator : operators) {
            if (text.startsWith(operator, at)) {
                // An assignment is no binary operator.
                final int after = at + operator.length();
                if (after < text.length() && text.charAt(after) == '=' && !operator.endsWith("=")) {
                    return 0;
                }
                return operator.length();
            }
        }
        return 0;
    }

    /** How loosely a binary operator binds: the lower the looser; -1 for a text that is none. */
    static int rank(final String operator) {
        switch (operator) {
            case "??":
                return 1;
            case "||":
                return 2;
            case "&&":
                return 3;
            case "|":
                return 4;
            case "^":
                return 5;
            case "&":
                return 6;
            case "==":
            case "!=":
            case "===":
            case "!==":
                return 7;
            case "<":
            case ">":
            case "<=":
            case ">=":
                return 8;
            case "<<":
            case ">>":
            case ">>>":
                return 9;
            case "+":
            case "-":
                return 10;
            case "*":
            case "/":
            case "%":
                return 11;
            case "**":
                return 12;
            default:
                return -1;
        }
    }

    /** Whether the {@code =} at an index assigns, and does nothing else: no comparison, arrow or operation. */
    boolean assignsAt(final int index) {
        if (!isCode(index) || text.charAt(index) != '=') {
            return false;
        }
        final char next = index + 1 < text.length() ? text.charAt(index + 1) : ' ';
        final char previous = index > 0 ? text.charAt(index - 1) : ' ';
        return next != '=' && next != '>' && "=!<>+-*/%&|^?".indexOf(previous) < 0;
    }

    /** The first {@code =} outside every bracket that assigns, an operation's own included, or -1. */
    int assignment() {
        int i = 0;
        while (i < text.length()) {
            if (code[i] && closer[i] > i) {
                i = closer[i] + 1;
                continue;
            }
            if (code[i] && text.charAt(i) == '=') {
                final char next = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
                final char previous = i > 0 ? text.charAt(i - 1) : ' ';
                if (next != '=' && next != '>' && previous != '=' && previous != '!' && previous != '<'
                        && previous != '>') {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    /** The brace that opens the body of the function around a place, or -1 for the handler's own body. */
    int functionAround(final int place) {
        int at = place;
        while (true) {
            final int brace = braceAround(at);
            if (brace < 0 || opensFunction(brace)) {
                return brace;
            }
            at = brace;
        }
    }

    /** The nearest brace that is open at a place, or -1. */
    int braceAround(final int place) {
        int found = -1;
        int i = 0;
        while (i < place && i < text.length()) {
            if (isCode(i) && closing(i) > i && closing(i) < place) {
                i = closing(i) + 1;
                continue;
            }
            if (isCode(i) && text.charAt(i) == '{') {
                found = i;
            }
            i++;
        }
        return found;
    }

    /** Whether a brace opens a function's body: it follows a parameter list or an arrow. */
    boolean opensFunction(final int brace) {
        final int before = lastBefore(brace);
        if (before < 0) {
            return false;
        }
        if (text.charAt(before) == '>' && before > 0 && text.charAt(before - 1) == '=') {
            return true;
        }
        if (text.charAt(before) != ')' || opening(before) < 0) {
            return false;
        }
        final String word = wordBefore(opening(before));
        return !"if".equals(word) && !"for".equals(word) && !"while".equals(word) && !"with".equals(word)
            && !"switch".equals(word) && !"catch".equals(word);
    }

    /**
     * Where the statement that holds an index starts, as the account's engine marks it: after the nearest
     * {@code ;} or block brace before it, after the header of an {@code if}, a {@code for} or a {@code while},
     * after an {@code else}, a {@code do}, an arrow or a {@code case} label; inside the header of a loop, after
     * its bracket; then past a label or an object's key, and for a declaration at the value the nearest name is
     * given. A bracketed group that ends before the index starts nothing.
     */
    int statementStart(final int index) {
        final int stop = Math.min(index, text.length());
        int start = 0;
        // The brackets open at the index, innermost last, and for an arrow's body the start it took over.
        final int[] open = new int[stop + 1];
        int depth = 0;
        final int[] arrowDepth = new int[stop + 1];
        final int[] arrowStart = new int[stop + 1];
        int arrows = 0;
        int i = 0;
        while (i < stop) {
            if (!code[i]) {
                i++;
                continue;
            }
            final char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                final int close = closer[i];
                if (close >= 0 && close < stop) {
                    if (c == '{' && endsStatement(close) || c == '(' && heads(i, "if", "for", "while", "with")) {
                        start = close + 1;
                    }
                    i = close + 1;
                    continue;
                }
                open[depth++] = i;
                if (c == '{' || c == '(' && heads(i, "for", "while")) {
                    start = i + 1;
                }
            } else if (c == ';') {
                start = i + 1;
                arrows = 0;
            } else if (c == ',') {
                if (arrows > 0 && arrowDepth[arrows - 1] == depth) {
                    start = arrowStart[--arrows];
                } else if (depth > 0 && text.charAt(open[depth - 1]) == '{' && holdsProperties(open[depth - 1])) {
                    start = i + 1;
                }
            } else if (c == '=' && i + 1 < text.length() && text.charAt(i + 1) == '>') {
                final int body = firstFrom(i + 2);
                if (body < text.length() && text.charAt(body) != '{') {
                    arrowDepth[arrows] = depth;
                    arrowStart[arrows++] = start;
                    start = i + 2;
                }
                i++;
            } else if (startsName(i)) {
                final int end = nameEnd(i);
                if (end <= stop && isWord(i, end, "else", "do")) {
                    start = end;
                } else if (end <= stop && isWord(i, end, "case")) {
                    final int colon = labelColon(end);
                    if (colon >= 0 && colon < stop) {
                        start = colon + 1;
                        i = colon;
                    }
                }
                i = Math.max(i, end - 1);
            }
            i++;
        }
        start = Math.min(firstFrom(start), stop);
        // A label, a default or an object's key: what follows its colon is what is marked.
        while (true) {
            final int colon = keyColon(start);
            if (colon < 0 || colon >= stop) {
                break;
            }
            start = Math.min(firstFrom(colon + 1), stop);
        }
        if (startsName(start) && isWord(start, nameEnd(start), "var", "let", "const")) {
            start = declaredValue(nameEnd(start), start, stop);
        }
        return start;
    }

    /** Whether the bracket at an index follows one of the given words. */
    private boolean heads(final int open, final String... words) {
        final int before = lastBefore(open);
        return before >= 0 && isNamePart(text.charAt(before)) && isWord(nameStart(before), before + 1, words);
    }

    /** Whether a closing brace ends a statement: nothing goes on with the value of what it closed. */
    private boolean endsStatement(final int close) {
        final int next = firstFrom(close + 1);
        return next >= text.length() || ")],.([?:+-*/%&|^<>=`".indexOf(text.charAt(next)) < 0;
    }

    /** Whether a brace opens an object's properties, and no block: it stands where a value is expected. */
    private boolean holdsProperties(final int open) {
        final int before = lastBefore(open);
        if (before < 0) {
            return false;
        }
        final char c = text.charAt(before);
        if (isNamePart(c)) {
            return isWord(nameStart(before), before + 1, "return", "typeof", "in", "of", "void", "new", "throw",
                "case", "yield", "await");
        }
        if (c == '>' && before > 0 && text.charAt(before - 1) == '=') {
            return false;
        }
        return "([,=:?+-*/%&|^!~<>".indexOf(c) >= 0;
    }

    /** The colon that ends the label whose expression starts at an index, a conditional's own aside, or -1. */
    private int labelColon(final int from) {
        int conditionals = 0;
        int i = from;
        while (i < text.length()) {
            if (!code[i]) {
                i++;
                continue;
            }
            final char c = text.charAt(i);
            if (closer[i] > i) {
                i = closer[i] + 1;
                continue;
            }
            if (c == ';' || c == '}') {
                return -1;
            }
            if (c == '?') {
                final char next = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
                if (next == '?' || next == '.') {
                    i++;
                } else {
                    conditionals++;
                }
            } else if (c == ':') {
                if (conditionals == 0) {
                    return i;
                }
                conditionals--;
            }
            i++;
        }
        return -1;
    }

    /** The colon after the name, text or number that starts at an index, or -1 when none follows it. */
    private int keyColon(final int start) {
        if (!isCode(start)) {
            return -1;
        }
        final char c = text.charAt(start);
        int end;
        if (c == '\'' || c == '"') {
            end = start + 1;
            while (end < text.length() && !code[end]) {
                end++;
            }
            end++;
        } else if (isNamePart(c)) {
            end = nameEnd(start);
            if (isWord(start, end, "case")) {
                return -1;
            }
        } else {
            return -1;
        }
        final int colon = firstFrom(end);
        return colon < text.length() && text.charAt(colon) == ':' ? colon : -1;
    }

    /** Where the value starts that a declaration gives the name nearest before an index, or the fallback. */
    private int declaredValue(final int from, final int fallback, final int stop) {
        int value = -1;
        int depth = 0;
        int i = from;
        while (i < stop) {
            if (!code[i]) {
                i++;
                continue;
            }
            final char c = text.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                if (closer[i] >= 0 && closer[i] < stop) {
                    i = closer[i] + 1;
                    continue;
                }
                depth++;
            } else if (depth == 0 && c == ',') {
                value = -1;
            } else if (depth == 0 && value < 0 && assignsAt(i)) {
                value = Math.min(firstFrom(i + 1), stop);
            }
            i++;
        }
        return value >= 0 ? value : fallback;
    }
}
