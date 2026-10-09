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
 * Where the account's engine places a name that is not defined (live-verified, each rule). The engine marks a
 * statement where it starts, and each operation where it is written: a member at its name, an indexed read at
 * its bracket, a call at the name called, a binary or unary operation at its operator. A name that is read has a
 * place of its own, which the statement's mark overrides while nothing has been evaluated since the statement
 * started; a name that is called has none, and is placed where the operation evaluated last is.
 *
 * <p>Nothing is evaluated by the words that wait for a value ({@code return}, {@code typeof}, a prefix
 * operator, an opening parenthesis), by a name being assigned to, or by a literal the engine folds into what
 * follows it: any literal before {@code &&}, {@code ||}, {@code ??} or a conditional's {@code ?}, and a small
 * integer before {@code *}, {@code |}, {@code &} or {@code ^}.
 */
final class JavaScriptNamePlace {

    private static final int OPERATOR = 0;
    private static final int GROUP = 1;
    private static final int CALL = 2;
    private static final int INDEX = 3;
    private static final int LIST = 4;

    private static final int NO_LITERAL = 0;
    private static final int LITERAL = 1;
    private static final int SMALL_INTEGER = 2;

    private final JavaScriptSourceScan scan;
    private final String text;
    private final int statement;
    /** Whether nothing has been evaluated since the statement started. */
    private boolean waiting = true;
    /** Where the operation evaluated last is placed. */
    private int last;
    /** The operators that wait for their right operand, and the brackets still open, innermost last. */
    private final int[] at;
    private final int[] rank;
    private final int[] kind;
    private int held;

    private JavaScriptNamePlace(final JavaScriptSourceScan scan, final String text, final int statement,
                                final int name) {
        this.scan = scan;
        this.text = text;
        this.statement = statement;
        this.last = statement;
        final int room = Math.max(1, name - statement + 1);
        this.at = new int[room];
        this.rank = new int[room];
        this.kind = new int[room];
    }

    /**
     * @param scan the reading of the handler's body
     * @param text the body
     * @param name where the name that is not defined starts
     * @return where the account places it
     */
    static int of(final JavaScriptSourceScan scan, final String text, final int name) {
        final int statement = scan.statementStart(name);
        final JavaScriptNamePlace place = new JavaScriptNamePlace(scan, text, statement, name);
        place.read(name);
        final int after = scan.firstFrom(scan.nameEnd(name));
        if (after < text.length() && text.charAt(after) == '(') {
            return place.last;
        }
        return place.waiting ? statement : name;
    }

    private void read(final int stop) {
        // Whether what was read last is a value, so that a bracket after it calls it or reads from it.
        boolean value = false;
        int literal = NO_LITERAL;
        boolean waitedBefore = false;
        int i = statement;
        while (i < stop) {
            if (!scan.isCode(i) || Character.isWhitespace(text.charAt(i))) {
                i++;
                continue;
            }
            final int literalBefore = literal;
            final boolean waitedBeforeLiteral = waitedBefore;
            literal = NO_LITERAL;
            final char c = text.charAt(i);
            final char next = i + 1 < text.length() ? text.charAt(i + 1) : ' ';
            if (c == '\'' || c == '"' || c == '`') {
                final int end = partner(i, c);
                if (end >= stop) {
                    // The name is written in a template: before the first value of one that starts with it,
                    // nothing is evaluated.
                    if (!(text.startsWith("`${", i) && text.substring(i + 3, stop).trim().isEmpty())) {
                        waiting = false;
                    }
                    return;
                }
                waitedBefore = waiting;
                literal = LITERAL;
                waiting = false;
                value = true;
                i = end + 1;
            } else if (Character.isDigit(c) || c == '.' && Character.isDigit(next)
                    || (c == '-' || c == '+') && !value && next != c && (Character.isDigit(next) || next == '.')) {
                final int digits = Character.isDigit(c) || c == '.' ? i : i + 1;
                int end = digits;
                while (end < text.length() && (JavaScriptSourceScan.isNamePart(text.charAt(end))
                        || text.charAt(end) == '.')) {
                    end++;
                }
                waitedBefore = waiting;
                literal = isSmallInteger(text.substring(digits, end)) ? SMALL_INTEGER : LITERAL;
                waiting = false;
                value = true;
                i = end;
            } else if (scan.startsName(i)) {
                final int end = scan.nameEnd(i);
                if (scan.isWord(i, end, "true", "false", "null", "undefined")) {
                    waitedBefore = waiting;
                    literal = LITERAL;
                    waiting = false;
                    value = true;
                } else if (scan.isWord(i, end, "in", "instanceof") && value) {
                    operator(i, 8, false);
                    value = false;
                } else if (scan.isWord(i, end, "function", "class")) {
                    final int past = definitionEnd(i, end, stop);
                    waiting = false;
                    if (past < 0) {
                        return;
                    }
                    value = true;
                    i = past;
                    continue;
                } else if (scan.isWord(i, end, "case")) {
                    waiting = false;
                    value = false;
                } else if (scan.isWord(i, end, "this", "super", "arguments")) {
                    waiting = false;
                    value = true;
                } else if (scan.isWord(i, end, "return", "throw", "typeof", "void", "delete", "await", "yield", "var",
                        "let", "const", "new", "if", "switch", "while", "for", "with", "catch", "of", "else", "do",
                        "in", "instanceof", "async")) {
                    value = false;
                } else {
                    final int after = scan.firstFrom(end);
                    final char follows = after < text.length() ? text.charAt(after) : ' ';
                    if (follows == '(') {
                        // A name that is called is read with no place of its own.
                        waiting = false;
                    } else if (follows != '=' || !scan.assignsAt(after) && !text.startsWith("=>", after)) {
                        if (waiting) {
                            waiting = false;
                        } else {
                            last = i;
                        }
                    }
                    value = true;
                }
                i = end;
            } else if (c == '.') {
                if (text.startsWith("...", i)) {
                    value = false;
                    i += 3;
                    continue;
                }
                final int member = scan.firstFrom(i + 1);
                if (!scan.startsName(member)) {
                    i++;
                    continue;
                }
                final int end = scan.nameEnd(member);
                if (!scan.assignsAt(scan.firstFrom(end))) {
                    waiting = false;
                    last = member;
                }
                value = true;
                i = end;
            } else if (c == '(') {
                open(i, value ? CALL : GROUP);
                value = false;
                i++;
            } else if (c == '[') {
                open(i, value ? INDEX : LIST);
                if (!value) {
                    waiting = false;
                }
                value = false;
                i++;
            } else if (c == ')' || c == ']') {
                final int opened = close();
                if (opened >= 0 && kind[opened] == CALL) {
                    waiting = false;
                    last = scan.callPlaceAt(at[opened]);
                } else if (opened >= 0 && kind[opened] == INDEX && !scan.assignsAt(scan.firstFrom(i + 1))) {
                    waiting = false;
                    last = at[opened];
                }
                value = true;
                i++;
            } else if (c == '{') {
                final int end = scan.closing(i);
                waiting = false;
                if (end < 0 || end >= stop) {
                    return;
                }
                value = true;
                i = end + 1;
            } else if (c == ',' || c == ':' || c == ';') {
                operandsDone();
                value = false;
                i++;
            } else if (c == '=' && next == '>') {
                // An arrow's body is made, and not evaluated.
                waiting = false;
                final int body = scan.firstFrom(i + 2);
                int end = body;
                if (body < text.length() && text.charAt(body) == '{') {
                    end = scan.closing(body) < 0 ? stop : scan.closing(body) + 1;
                } else {
                    while (end < stop && !(scan.isCode(end) && ",)];".indexOf(text.charAt(end)) >= 0)) {
                        end = scan.isCode(end) && scan.closing(end) > end ? scan.closing(end) + 1 : end + 1;
                    }
                }
                value = true;
                i = end;
            } else if (c == '=' && next != '=') {
                value = false;
                i++;
            } else if (c == '?' && next == '.' && !Character.isDigit(i + 2 < text.length() ? text.charAt(i + 2) : ' ')) {
                i++;
            } else if (c == '?' && next != '?') {
                if (literalBefore != NO_LITERAL) {
                    waiting = waitedBeforeLiteral;
                }
                operandsDone();
                value = false;
                i++;
            } else if ((c == '+' || c == '-') && next == c) {
                if (value) {
                    waiting = false;
                    last = i;
                } else {
                    push(i, 14, OPERATOR);
                }
                i += 2;
            } else if ((c == '+' || c == '-' || c == '~') && !value) {
                push(i, 14, OPERATOR);
                i++;
            } else if (c == '!' && next != '=') {
                value = false;
                i++;
            } else if (c == '/' && !value) {
                final int end = partner(i, '/');
                if (end >= stop) {
                    waiting = false;
                    return;
                }
                waitedBefore = waiting;
                literal = LITERAL;
                waiting = false;
                value = true;
                i = scan.nameEnd(end + 1);
            } else {
                final int length = scan.operatorLength(i);
                if (length == 0) {
                    // An operation's own assignment: its target has been read, the rest follows.
                    final int from = i;
                    while (i < stop && "+-*/%&|^<>=?".indexOf(text.charAt(i)) >= 0) {
                        i++;
                    }
                    i = Math.max(i, from + 1);
                    value = false;
                    continue;
                }
                final String sign = text.substring(i, i + length);
                final boolean decides = "&&".equals(sign) || "||".equals(sign) || "??".equals(sign);
                final boolean folds = "*".equals(sign) || "|".equals(sign) || "&".equals(sign) || "^".equals(sign);
                if (decides && literalBefore != NO_LITERAL || folds && literalBefore == SMALL_INTEGER) {
                    waiting = waitedBeforeLiteral;
                }
                final int binds = JavaScriptSourceScan.rank(sign);
                if (decides) {
                    finish(binds, false);
                } else {
                    operator(i, binds, "**".equals(sign));
                }
                value = false;
                i += length;
            }
        }
    }

    /** The quote or slash that ends the text opened at an index: the next one that is code. */
    private int partner(final int from, final char c) {
        int end = from + 1;
        while (end < text.length() && !(scan.isCode(end) && text.charAt(end) == c)) {
            end++;
        }
        return end;
    }

    /** Whether a number's text is an integer the engine keeps small: below two to the thirtieth. */
    private static boolean isSmallInteger(final String number) {
        if (number.isEmpty() || number.length() > 10) {
            return false;
        }
        for (int i = 0; i < number.length(); i++) {
            if (!Character.isDigit(number.charAt(i))) {
                return false;
            }
        }
        return Long.parseLong(number) < (1L << 30);
    }

    /** The index after a function's or a class's definition, or -1 when it does not end before the name. */
    private int definitionEnd(final int from, final int wordEnd, final int stop) {
        int body = wordEnd;
        if (scan.isWord(from, wordEnd, "function")) {
            while (body < stop && !(scan.isCode(body) && text.charAt(body) == '(')) {
                body++;
            }
            if (body >= stop || scan.closing(body) < 0) {
                return -1;
            }
            body = scan.closing(body) + 1;
        }
        while (body < stop && !(scan.isCode(body) && text.charAt(body) == '{')) {
            body++;
        }
        final int end = body < stop ? scan.closing(body) : -1;
        return end < 0 || end >= stop ? -1 : end + 1;
    }

    private void push(final int place, final int binds, final int what) {
        at[held] = place;
        rank[held] = binds;
        kind[held] = what;
        held++;
    }

    private void open(final int bracket, final int what) {
        push(bracket, 0, what);
    }

    /** An operator has been read: those before it that bind as tightly have their operands, and are evaluated. */
    private void operator(final int place, final int binds, final boolean rightFirst) {
        finish(binds, rightFirst);
        push(place, binds, OPERATOR);
    }

    private void finish(final int binds, final boolean rightFirst) {
        while (held > 0 && kind[held - 1] == OPERATOR
                && (rank[held - 1] > binds || rank[held - 1] == binds && !rightFirst)) {
            held--;
            if (!waiting) {
                last = at[held];
            }
        }
    }

    /** What stands before a separator is complete: every operator since the innermost open bracket is evaluated. */
    private void operandsDone() {
        finish(0, false);
    }

    /** A bracket closes: its operators are evaluated; the index it was opened under is returned, or -1. */
    private int close() {
        operandsDone();
        if (held == 0) {
            return -1;
        }
        held--;
        return held;
    }
}
