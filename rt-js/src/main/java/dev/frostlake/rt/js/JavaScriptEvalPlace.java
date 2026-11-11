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
 * Where the account places the refusal of a direct {@code eval(...)}: at the last place it recorded before the call,
 * not at the {@code eval} (all live-verified, the body {@code ' return eval(…); '} and its siblings):
 *
 * <pre>
 *   eval(s)                 the name s                  eval(a.b)        the property b
 *   eval(f('1'))            the callee f                eval(s[0])       the '['
 *   eval(s + '2')           the operator                eval(a === 1 ? '1' : '2')  the condition's operator
 *                                                       eval('1'), eval(('1')), eval(`1`), eval('1' + '2'): a
 *                                                       constant records nothing, so the place around it stands:
 *   return eval('1');       the statement's start       if (eval('1'))   the statement's start
 *   x = eval('1');          the statement's start       var x = eval('1'); the eval
 *   [eval('1')], String(eval('1' + '2'))                the eval, an element's or an argument's own place
 * </pre>
 */
final class JavaScriptEvalPlace {

    private static final String OPERATORS = "+-*/%<>=!&|^";

    private JavaScriptEvalPlace() {
    }

    /**
     * The place of the refusal of the {@code eval} call at an index.
     *
     * @param scan the body's scan
     * @param body the body
     * @param eval where the {@code eval} starts
     * @return the place, the {@code eval} itself where the call is no direct call of it
     */
    static int of(final JavaScriptSourceScan scan, final String body, final int eval) {
        if (!body.startsWith("eval", eval) || !scan.isCode(eval)) {
            return eval;
        }
        final int open = scan.firstFrom(eval + 4);
        if (open >= body.length() || body.charAt(open) != '(') {
            return eval;
        }
        final int close = scan.closing(open);
        if (close < 0) {
            return eval;
        }
        final int recorded = recordedIn(scan, body, open, close);
        return recorded >= 0 ? recorded : around(scan, body, eval);
    }

    /** The place the argument between two brackets records last, or -1 for a constant, which records none. */
    private static int recordedIn(final JavaScriptSourceScan scan, final String body, final int open,
                                  final int close) {
        int last = scan.lastBefore(close);
        if (last <= open) {
            return -1;   // a string or a template: no code at all
        }
        int operator = -1;
        int operatorEnd = -2;
        boolean named = false;
        // Where the argument starts as written, a string's quote included: an operator there is a sign.
        int start = open + 1;
        while (start < close && Character.isWhitespace(body.charAt(start))) {
            start++;
        }
        for (int i = scan.firstFrom(open + 1); i < close; i = scan.firstFrom(i + 1)) {
            final char c = body.charAt(i);
            if (c == '?' && body.charAt(i + 1) != '.' && body.charAt(i + 1) != '?') {
                // A conditional records nothing of its own: its condition's place is the last.
                last = scan.lastBefore(i);
                break;
            }
            if (c == '(' || c == '[' || c == '{') {
                final int matched = scan.closing(i);
                if (matched < 0) {
                    return -1;
                }
                if (c == '(' && matched == last && !named && operator < 0) {
                    // A constant in brackets is the constant.
                    return recordedIn(scan, body, i, matched);
                }
                i = matched;
                continue;
            }
            if (OPERATORS.indexOf(c) >= 0 && i > start) {
                // An operator of several characters is placed at its first.
                if (i - 1 != operatorEnd) {
                    operator = i;
                }
                operatorEnd = i;
            } else if (JavaScriptSourceScan.isNamePart(c) && !Character.isDigit(c)) {
                named = true;
            }
        }
        if (!named) {
            return -1;   // a constant, numbers and strings alone, records nothing whatever its operators
        }
        if (operator >= 0) {
            return operator;
        }
        final char end = body.charAt(last);
        if (end == ')') {
            // A call: its callee's last name.
            final int callee = scan.lastBefore(scan.opening(last));
            return callee >= 0 && JavaScriptSourceScan.isNamePart(body.charAt(callee)) ? scan.nameStart(callee) : last;
        }
        if (end == ']') {
            return scan.opening(last);
        }
        return JavaScriptSourceScan.isNamePart(end) ? scan.nameStart(last) : -1;
    }

    /** The place recorded around a call whose argument records none. */
    private static int around(final JavaScriptSourceScan scan, final String body, final int eval) {
        final int before = scan.lastBefore(eval);
        if (before < 0) {
            return statementStart(scan, body, eval);
        }
        final char c = body.charAt(before);
        if (c == '[' || c == ',') {
            return eval;
        }
        if (c == '(') {
            final String word = scan.wordBefore(before);
            final int callee = scan.lastBefore(before);
            final boolean called = callee >= 0 && (JavaScriptSourceScan.isNamePart(body.charAt(callee))
                || body.charAt(callee) == ')' || body.charAt(callee) == ']') && !isKeyword(word);
            return called ? eval : statementStart(scan, body, eval);
        }
        if (c == '=' && (before == 0 || OPERATORS.indexOf(body.charAt(before - 1)) < 0)) {
            final int name = scan.lastBefore(before);
            final String declared = name >= 0 ? scan.wordBefore(scan.nameStart(name)) : "";
            if ("var".equals(declared) || "let".equals(declared) || "const".equals(declared)) {
                return eval;
            }
        }
        return statementStart(scan, body, eval);
    }

    private static boolean isKeyword(final String word) {
        return "if".equals(word) || "while".equals(word) || "for".equals(word) || "switch".equals(word)
            || "catch".equals(word) || "with".equals(word) || "return".equals(word) || "typeof".equals(word)
            || "void".equals(word) || "delete".equals(word) || "in".equals(word) || "of".equals(word);
    }

    /** The start of the statement that holds an index: past the last ';', '{' or '}' before it at its depth. */
    private static int statementStart(final JavaScriptSourceScan scan, final String body, final int at) {
        int i = at - 1;
        while (i >= 0) {
            if (!scan.isCode(i)) {
                i--;
                continue;
            }
            final char c = body.charAt(i);
            if (c == ';' || c == '{' || c == '}') {
                break;
            }
            if (c == ')' || c == ']') {
                final int opened = scan.opening(i);
                i = opened >= 0 ? opened - 1 : i - 1;
                continue;
            }
            i--;
        }
        return scan.firstFrom(i + 1);
    }
}
