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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The name the account's JavaScript engine gives a callee that is no function, for the callees this engine names by
 * their value or as an intermediate value (live-verified). The account prints the callee from its source: a literal
 * array as written with no space after its commas ({@code [1,2][0]}), a sequence with its operands spaced around the
 * commas in parentheses of its own ({@code (1 , 2 , 3)}, {@code ((void 0) , 1)}), a void as {@code (void 0)}, an
 * object literal by an intermediate value for each property ({@code {(intermediate value)}.a}), a conditional as three
 * intermediate values, and an arithmetic of number literals as the number it folds to ({@code 8} for
 * {@code (2 ** 3)}). A member follows as written, one named by a text as a dotted name ({@code [1].x} for
 * {@code [1]['x']}), and a call as {@code (...)}. Comparisons and logical operators are printed in parentheses of their
 * own, unfolded. A callee without one of the shapes this engine names otherwise, or one this reading does not read,
 * is left to this engine's words.
 */
final class JavaScriptCallPrinter {

    /** The operators of more than one character, the longest first. */
    private static final String[] OPERATORS = {">>>", "===", "!==", "**", "==", "!=", "<=", ">=", "&&", "||", "??",
        "<<", ">>", "=>", "..."};

    /** Words that open what this reading does not read: functions, classes, constructions, templates. */
    private static final String[] UNREAD = {"=>", "function", "class", "new", "async", "await", "yield", "this",
        "super", "`", "..."};

    /** What the account prints for a part of an expression it does not print itself. */
    private static final String INTERMEDIATE = "(intermediate value)";

    private final List<String> tokens;
    private int at;
    /** The value of the expression read last, when it is a number or a text literal, or null. */
    private Object literal;
    /** Whether the callee holds a shape this engine names otherwise. */
    private boolean differs;

    private JavaScriptCallPrinter(final List<String> tokens) {
        this.tokens = tokens;
    }

    /**
     * The account's name for a callee, read from its source.
     *
     * @param callee the source of what a call calls, before its argument list
     * @return the name, or null where this engine's own words stand
     */
    static String printed(final String callee) {
        if (callee == null || callee.isEmpty()) {
            return null;
        }
        final List<String> tokens = tokens(callee);
        if (tokens == null || tokens.isEmpty()) {
            return null;
        }
        for (final String token : tokens) {
            for (final String word : UNREAD) {
                if (word.equals(token)) {
                    return null;
                }
            }
        }
        final JavaScriptCallPrinter printer = new JavaScriptCallPrinter(tokens);
        try {
            final String text = printer.sequence();
            return printer.at == tokens.size() && printer.differs ? text : null;
        } catch (final IllegalStateException unread) {
            return null;
        }
    }

    private static List<String> tokens(final String text) {
        final List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            int end = i + 1;
            if (Character.isDigit(c) || c == '.' && end < text.length() && Character.isDigit(text.charAt(end))) {
                while (end < text.length()
                        && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '.')) {
                    end++;
                }
            } else if (c == '\'' || c == '"') {
                while (end < text.length() && text.charAt(end) != c) {
                    if (text.charAt(end) == '\\' || text.charAt(end) == '\n') {
                        return null;
                    }
                    end++;
                }
                if (end >= text.length()) {
                    return null;
                }
                end++;
            } else if (Character.isJavaIdentifierStart(c)) {
                while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
                    end++;
                }
            } else {
                for (final String operator : OPERATORS) {
                    if (text.startsWith(operator, i)) {
                        end = i + operator.length();
                        break;
                    }
                }
            }
            out.add(text.substring(i, end));
            i = end;
        }
        return out;
    }

    /** A sequence: operands separated by commas, printed with a comma spaced between each two. */
    private String sequence() {
        final String first = conditional();
        if (!next(",")) {
            return first;
        }
        differs = true;
        final StringBuilder text = new StringBuilder("(").append(first);
        do {
            text.append(" , ").append(conditional());
        } while (next(","));
        literal = null;
        return text.append(')').toString();
    }

    /** A conditional, printed as an intermediate value for each of its three parts. */
    private String conditional() {
        final String test = binary(0);
        if (!next("?")) {
            return test;
        }
        conditional();
        expect(":");
        conditional();
        differs = true;
        literal = null;
        return INTERMEDIATE + INTERMEDIATE + INTERMEDIATE;
    }

    /** A binary operation, climbing the operators from {@code floor} up; one of two literals is folded. */
    private String binary(final int floor) {
        String left = unary();
        Object leftValue = literal;
        while (true) {
            final String operator = peek();
            final int rank = operator == null ? -1 : rank(operator);
            if (rank < 0 || rank < floor) {
                break;
            }
            at++;
            final boolean power = "**".equals(operator);
            final String right = binary(power ? rank : rank + 1);
            final Object rightValue = literal;
            differs |= power;
            final Object folded = folded(operator, leftValue, rightValue);
            if (folded != null) {
                left = shown(folded);
                leftValue = folded;
            } else {
                left = "(" + left + " " + operator + " " + right + ")";
                leftValue = null;
            }
        }
        literal = leftValue;
        return left;
    }

    private static int rank(final String operator) {
        switch (operator) {
            case "??":
            case "||":
                return 1;
            case "&&":
                return 2;
            case "|":
                return 3;
            case "^":
                return 4;
            case "&":
                return 5;
            case "==":
            case "!=":
            case "===":
            case "!==":
                return 6;
            case "<":
            case ">":
            case "<=":
            case ">=":
            case "in":
            case "instanceof":
                return 7;
            case "<<":
            case ">>":
            case ">>>":
                return 8;
            case "+":
            case "-":
                return 9;
            case "*":
            case "/":
            case "%":
                return 10;
            case "**":
                return 11;
            default:
                return -1;
        }
    }

    /** A unary operation: void and typeof printed with their word, a literal's sign or negation folded. */
    private String unary() {
        final String operator = peek();
        if ("void".equals(operator) || "typeof".equals(operator)) {
            at++;
            final String operand = unary();
            differs |= "void".equals(operator);
            literal = null;
            return "(" + operator + " " + operand + ")";
        }
        if ("!".equals(operator) || "-".equals(operator) || "+".equals(operator) || "~".equals(operator)) {
            at++;
            final String operand = unary();
            final Object value = literal;
            literal = null;
            if (value instanceof Double) {
                final double number = (Double) value;
                if ("!".equals(operator)) {
                    return String.valueOf(number == 0 || Double.isNaN(number));
                }
                final double result = "-".equals(operator) ? -number : "+".equals(operator) ? number
                    : ~toInt32(number);
                literal = result;
                return shown(result);
            }
            if (value instanceof String && "!".equals(operator)) {
                return String.valueOf(((String) value).isEmpty());
            }
            if (value != null) {
                throw new IllegalStateException("a text's sign");
            }
            return "(" + operator + operand + ")";
        }
        return postfix();
    }

    /** What an operand reads after: members and calls, in order. */
    private String postfix() {
        String text = primary();
        while (true) {
            if (next(".")) {
                text = text + "." + name();
                literal = null;
            } else if (next("[")) {
                final String key = sequence();
                final Object keyValue = literal;
                expect("]");
                text = keyValue instanceof String && isName((String) keyValue) ? text + "." + keyValue
                    : text + "[" + key + "]";
                literal = null;
            } else if (next("(")) {
                arguments();
                text = text + "(...)";
                literal = null;
            } else {
                return text;
            }
        }
    }

    private String primary() {
        final String token = take();
        if ("(".equals(token)) {
            final String inner = sequence();
            expect(")");
            return inner;
        }
        if ("[".equals(token)) {
            differs = true;
            final StringBuilder text = new StringBuilder("[");
            if (!next("]")) {
                text.append(conditional());
                while (next(",")) {
                    text.append(',').append(conditional());
                }
                expect("]");
            }
            literal = null;
            return text.append(']').toString();
        }
        if ("{".equals(token)) {
            differs = true;
            final int properties = objectProperties();
            final StringBuilder text = new StringBuilder("{");
            for (int i = 0; i < properties; i++) {
                text.append(INTERMEDIATE);
            }
            literal = null;
            return text.append('}').toString();
        }
        literal = null;
        final char first = token.charAt(0);
        if (Character.isDigit(first) || first == '.') {
            try {
                final double number = Double.parseDouble(token);
                literal = number;
                return shown(number);
            } catch (final NumberFormatException unread) {
                throw new IllegalStateException("a number this reading does not read", unread);
            }
        }
        if (first == '\'' || first == '"') {
            final String value = token.substring(1, token.length() - 1);
            literal = value;
            return shown(value);
        }
        if (isName(token)) {
            return token;
        }
        throw new IllegalStateException("unexpected " + token);
    }

    /** The properties of an object literal whose '{' was read, up to and past its '}'. */
    private int objectProperties() {
        if (next("}")) {
            return 0;
        }
        int properties = 1;
        int depth = 0;
        while (true) {
            final String token = take();
            if ("(".equals(token) || "[".equals(token) || "{".equals(token)) {
                depth++;
            } else if (")".equals(token) || "]".equals(token)) {
                depth--;
            } else if ("}".equals(token)) {
                if (depth == 0) {
                    return properties;
                }
                depth--;
            } else if (",".equals(token) && depth == 0 && !"}".equals(peek())) {
                properties++;
            }
        }
    }

    /** A call's arguments whose '(' was read, up to and past its ')'. */
    private void arguments() {
        int depth = 0;
        while (true) {
            final String token = take();
            if ("(".equals(token) || "[".equals(token) || "{".equals(token)) {
                depth++;
            } else if (")".equals(token) && depth == 0) {
                return;
            } else if (")".equals(token) || "]".equals(token) || "}".equals(token)) {
                depth--;
            }
        }
    }

    private String name() {
        final String token = take();
        if (!isName(token)) {
            throw new IllegalStateException("a member's name");
        }
        return token;
    }

    private static boolean isName(final String token) {
        if (token.isEmpty() || !Character.isJavaIdentifierStart(token.charAt(0))) {
            return false;
        }
        for (int i = 1; i < token.length(); i++) {
            if (!Character.isJavaIdentifierPart(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Two literals an operator folds, as the account's engine folds them while it reads the source, or null. */
    private static Object folded(final String operator, final Object left, final Object right) {
        if ("+".equals(operator) && (left instanceof String || right instanceof String) && left != null
                && right != null) {
            return text(left) + text(right);
        }
        if (!(left instanceof Double) || !(right instanceof Double)) {
            return null;
        }
        final double a = (Double) left;
        final double b = (Double) right;
        switch (operator) {
            case "+":
                return a + b;
            case "-":
                return a - b;
            case "*":
                return a * b;
            case "/":
                return a / b;
            case "%":
                return a % b;
            case "**":
                return Math.pow(a, b);
            case "|":
                return (double) (toInt32(a) | toInt32(b));
            case "&":
                return (double) (toInt32(a) & toInt32(b));
            case "^":
                return (double) (toInt32(a) ^ toInt32(b));
            case "<<":
                return (double) (toInt32(a) << (toInt32(b) & 31));
            case ">>":
                return (double) (toInt32(a) >> (toInt32(b) & 31));
            case ">>>":
                return (double) ((toInt32(a) & 0xFFFFFFFFL) >>> (toInt32(b) & 31));
            default:
                return null;
        }
    }

    private static int toInt32(final double number) {
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            return 0;
        }
        return (int) (long) number;
    }

    private static String text(final Object value) {
        return value instanceof Double ? number((Double) value) : (String) value;
    }

    /** A literal as the account prints it: a number as JavaScript writes it, a text in double quotes. */
    private static String shown(final Object value) {
        return value instanceof Double ? number((Double) value) : "\"" + value + "\"";
    }

    private static String number(final double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e21) {
            return new BigDecimal(value).toPlainString();
        }
        return Double.toString(value).toLowerCase(Locale.ROOT).replace("e", "e+").replace("e+-", "e-");
    }

    private String peek() {
        return at < tokens.size() ? tokens.get(at) : null;
    }

    private String take() {
        if (at >= tokens.size()) {
            throw new IllegalStateException("the end of the callee");
        }
        return tokens.get(at++);
    }

    private boolean next(final String token) {
        if (token.equals(peek())) {
            at++;
            return true;
        }
        return false;
    }

    private void expect(final String token) {
        if (!next(token)) {
            throw new IllegalStateException("a missing " + token);
        }
    }
}
