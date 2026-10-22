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


package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeLexer;
import org.antlr.v4.runtime.Token;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The date operations a statement a block runs holds as the bare word NULL, because a variable bound into them holds
 * NULL (live-verified): a DATE plus or minus a number, a number plus a DATE and a DATE minus a DATE where a DATE
 * variable holds NULL — {@code :d + 1}, {@code 1 + :d}, {@code :d + :n}, {@code :d - :e} — the same where a number
 * variable beside a DATE holds NULL, and a date, a time or a timestamp plus or minus an INTERVAL where the variable
 * holds NULL. The NULL then reads as the bare word does: {@code SYSTEM$TYPEOF(:d + 1)} is {@code NULL[LOB]},
 * {@code :d + 1 + 1} a NUMBER(19,0) and {@code COALESCE(:d + 1, CURRENT_DATE())} a DATE. A variable written in
 * brackets of its own stands where the bare one would; an operation another operator binds closer, as {@code x - :d
 * + 1} or {@code :d + 1 * 2}, is left as written.
 */
final class NullDateOperations {

    /** The tokens an operation may follow, which bind no operand of it. */
    private static final int[] BEFORE = {
        FrostlakeLexer.LPAREN, FrostlakeLexer.COMMA, FrostlakeLexer.SELECT, FrostlakeLexer.DISTINCT,
        FrostlakeLexer.WHERE, FrostlakeLexer.AND, FrostlakeLexer.OR, FrostlakeLexer.EQ, FrostlakeLexer.NEQ,
        FrostlakeLexer.LT, FrostlakeLexer.LTE, FrostlakeLexer.GT, FrostlakeLexer.GTE, FrostlakeLexer.WHEN,
        FrostlakeLexer.THEN, FrostlakeLexer.ELSE, FrostlakeLexer.HAVING, FrostlakeLexer.QUALIFY, FrostlakeLexer.BY,
        FrostlakeLexer.ON
    };

    /** The tokens an operation may be followed by, which take the operation whole as their operand. */
    private static final int[] AFTER = {
        FrostlakeLexer.RPAREN, FrostlakeLexer.COMMA, FrostlakeLexer.PLUS, FrostlakeLexer.MINUS, FrostlakeLexer.SEMI,
        Token.EOF, FrostlakeLexer.AS, FrostlakeLexer.FROM, FrostlakeLexer.WHERE, FrostlakeLexer.AND, FrostlakeLexer.OR,
        FrostlakeLexer.EQ, FrostlakeLexer.NEQ, FrostlakeLexer.LT, FrostlakeLexer.LTE, FrostlakeLexer.GT,
        FrostlakeLexer.GTE, FrostlakeLexer.IS, FrostlakeLexer.THEN, FrostlakeLexer.ELSE, FrostlakeLexer.END,
        FrostlakeLexer.WHEN, FrostlakeLexer.IDENTIFIER, FrostlakeLexer.QUOTED_IDENTIFIER, FrostlakeLexer.GROUP,
        FrostlakeLexer.ORDER, FrostlakeLexer.HAVING, FrostlakeLexer.QUALIFY, FrostlakeLexer.LIMIT,
        FrostlakeLexer.UNION, FrostlakeLexer.EXCEPT, FrostlakeLexer.INTERSECT, FrostlakeLexer.MINUS_KW,
        FrostlakeLexer.INTO
    };

    private final List<Token> toks;
    private final Map<Integer, DateOperandKind> binds;

    private NullDateOperations(final List<Token> toks, final Map<Integer, DateOperandKind> binds) {
        this.toks = toks;
        this.binds = binds;
    }

    /**
     * The operations of a statement that fold to NULL, each by the index of its first token, with the index of its
     * last one.
     *
     * @param toks  the statement's tokens
     * @param binds the kind of each variable the statement binds that may take part, by the index of its colon
     * @return the operations
     */
    static Map<Integer, Integer> spans(final List<Token> toks, final Map<Integer, DateOperandKind> binds) {
        return new NullDateOperations(toks, binds).spans();
    }

    private Map<Integer, Integer> spans() {
        final Map<Integer, Integer> spans = new HashMap<>();
        if (binds.isEmpty()) {
            return spans;
        }
        for (int i = 1; i + 1 < toks.size(); i++) {
            final int operator = type(i);
            if (operator != FrostlakeLexer.PLUS && operator != FrostlakeLexer.MINUS) {
                continue;
            }
            final int start = operandStart(i - 1);
            final int end = operandEnd(i + 1);
            if (start < 0 || end < 0 || spans.containsKey(start)) {
                continue;
            }
            final boolean openBefore = start == 0 || oneOf(type(start - 1), BEFORE);
            if (!openBefore || !oneOf(type(end + 1), AFTER)) {
                continue;
            }
            if (folds(kindEndingAt(i - 1), operator == FrostlakeLexer.MINUS, kindStartingAt(i + 1))) {
                spans.put(start, end);
            }
        }
        return spans;
    }

    /**
     * Whether an operation folds: a NULL variable beside what makes it date arithmetic.
     *
     * @param left  the left operand's kind, or null for any other operand
     * @param minus whether the operator is a minus
     * @param right the right operand's kind, or null for any other operand
     * @return whether it folds
     */
    private static boolean folds(final DateOperandKind left, final boolean minus, final DateOperandKind right) {
        if (left == null || right == null) {
            return false;
        }
        switch (left) {
            case NULL_DATE:
                return right == DateOperandKind.NUMBER || right == DateOperandKind.NULL_NUMBER
                    || right == DateOperandKind.INTERVAL
                    || minus && (right == DateOperandKind.DATE || right == DateOperandKind.NULL_DATE);
            case DATE:
                return right == DateOperandKind.NULL_NUMBER || minus && right == DateOperandKind.NULL_DATE;
            case NUMBER:
                return !minus && right == DateOperandKind.NULL_DATE;
            case NULL_NUMBER:
                return !minus && (right == DateOperandKind.NULL_DATE || right == DateOperandKind.DATE);
            case NULL_TEMPORAL:
                return right == DateOperandKind.INTERVAL;
            case INTERVAL:
                return !minus && (right == DateOperandKind.NULL_DATE || right == DateOperandKind.NULL_TEMPORAL);
            default:
                return false;
        }
    }

    /** The index of the first token of the operand that ends at {@code end}, or -1 for no operand this reads. */
    private int operandStart(final int end) {
        if (isNumber(type(end))) {
            return end;
        }
        if (type(end) == FrostlakeLexer.STRING_LITERAL && isTypedLiteralWord(type(end - 1))) {
            return end - 1;
        }
        if (binds.containsKey(end - 1)) {
            return end - 1;
        }
        if (type(end) == FrostlakeLexer.RPAREN && binds.containsKey(end - 2) && type(end - 3) == FrostlakeLexer.LPAREN
                && (end - 3 == 0 || oneOf(type(end - 4), BEFORE))) {
            return end - 3;
        }
        return -1;
    }

    /** The index of the last token of the operand that starts at {@code start}, or -1 for no operand this reads. */
    private int operandEnd(final int start) {
        if (isNumber(type(start))) {
            return start;
        }
        if (isTypedLiteralWord(type(start)) && type(start + 1) == FrostlakeLexer.STRING_LITERAL) {
            return start + 1;
        }
        if (binds.containsKey(start)) {
            return start + 1;
        }
        if (type(start) == FrostlakeLexer.LPAREN && binds.containsKey(start + 1)
                && type(start + 3) == FrostlakeLexer.RPAREN) {
            return start + 3;
        }
        return -1;
    }

    private DateOperandKind kindEndingAt(final int end) {
        if (isNumber(type(end))) {
            return DateOperandKind.NUMBER;
        }
        if (type(end) == FrostlakeLexer.STRING_LITERAL) {
            return literalKind(type(end - 1));
        }
        return binds.containsKey(end - 1) ? binds.get(end - 1) : binds.get(end - 2);
    }

    private DateOperandKind kindStartingAt(final int start) {
        if (isNumber(type(start))) {
            return DateOperandKind.NUMBER;
        }
        if (isTypedLiteralWord(type(start))) {
            return literalKind(type(start));
        }
        return binds.containsKey(start) ? binds.get(start) : binds.get(start + 1);
    }

    private static DateOperandKind literalKind(final int word) {
        return word == FrostlakeLexer.INTERVAL ? DateOperandKind.INTERVAL
            : word == FrostlakeLexer.DATE ? DateOperandKind.DATE : null;
    }

    private static boolean isTypedLiteralWord(final int type) {
        return type == FrostlakeLexer.INTERVAL || type == FrostlakeLexer.DATE;
    }

    private static boolean isNumber(final int type) {
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL;
    }

    private static boolean oneOf(final int type, final int[] types) {
        for (final int each : types) {
            if (each == type) {
                return true;
            }
        }
        return false;
    }

    private int type(final int index) {
        return index < 0 || index >= toks.size() ? Integer.MIN_VALUE : toks.get(index).getType();
    }
}
