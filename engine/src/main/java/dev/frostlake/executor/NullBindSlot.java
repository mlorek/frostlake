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
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.Token;

/**
 * Where a bind stands in the statement text it is spelled into, read off the statement's tokens — the places where a
 * variable holding NULL binds otherwise than as its declared type ({@link TypedNullBind}). A bind wrapped in brackets
 * of its own, {@code (:s)}, stands where the bare bind would.
 */
final class NullBindSlot {

    /** The date and time functions an argument bound NULL makes an untyped NULL of (live-verified, one by one). */
    private static final String[] DATE_TIME_FUNCTIONS = {
        "ADD_MONTHS", "DATEADD", "DATEDIFF", "DATE_PART", "DATE_TRUNC", "DAY", "DAYOFMONTH", "DAYOFWEEK",
        "DAYOFWEEKISO", "DAYOFYEAR", "EXTRACT", "HOUR", "LAST_DAY", "MINUTE", "MONTH", "MONTHS_BETWEEN", "QUARTER",
        "SECOND", "TIMEADD", "TIMEDIFF", "TIMESTAMPADD", "TIMESTAMPDIFF", "TIME_SLICE", "TRUNC", "TRUNCATE", "WEEK",
        "WEEKISO", "WEEKOFYEAR", "YEAR", "YEAROFWEEK", "YEAROFWEEKISO"
    };

    /** The functions that read their first argument as the text it is (every argument, for CONCAT). */
    private static final String[] TEXT_FUNCTIONS = {
        "BASE64_ENCODE", "COLLATE", "CONCAT", "INITCAP", "LEFT", "LEN", "LENGTH", "LOWER", "LTRIM", "REVERSE", "RIGHT",
        "RTRIM", "SPLIT_PART", "SUBSTR", "SUBSTRING", "TRIM", "TRY_TO_NUMBER", "UPPER"
    };

    /** The functions that read a text as a FLOAT when it is their one argument. */
    private static final String[] FLOAT_READERS = {"ABS", "ROUND", "ZEROIFNULL"};

    /** The aggregates that read a text argument as the text it is. */
    private static final String[] TEXT_AGGREGATES = {"ANY_VALUE", "AVG", "MAX", "MIN", "MODE", "SUM"};

    /** The functions that settle one type for all their arguments: IFF's first is its condition. */
    private static final String[] CONDITIONALS = {"COALESCE", "GREATEST", "IFF", "IFNULL", "LEAST", "NULLIF", "NVL"};

    /** The tokens that end a select item written alone, besides an alias. */
    private static final int[] ITEM_ENDS = {
        FrostlakeLexer.COMMA, FrostlakeLexer.FROM, FrostlakeLexer.AS, FrostlakeLexer.RPAREN, FrostlakeLexer.SEMI,
        Token.EOF, FrostlakeLexer.INTO, FrostlakeLexer.WHERE, FrostlakeLexer.GROUP, FrostlakeLexer.ORDER,
        FrostlakeLexer.HAVING, FrostlakeLexer.QUALIFY, FrostlakeLexer.LIMIT, FrostlakeLexer.UNION,
        FrostlakeLexer.EXCEPT, FrostlakeLexer.MINUS_KW, FrostlakeLexer.INTERSECT, FrostlakeLexer.FETCH,
        FrostlakeLexer.OFFSET
    };

    /** The clause words a comma of a select list never stands after. */
    private static final int[] CLAUSE_WORDS = {
        FrostlakeLexer.FROM, FrostlakeLexer.WHERE, FrostlakeLexer.BY, FrostlakeLexer.HAVING, FrostlakeLexer.QUALIFY,
        FrostlakeLexer.VALUES, FrostlakeLexer.SET, FrostlakeLexer.ON, FrostlakeLexer.USING, FrostlakeLexer.LIMIT,
        FrostlakeLexer.OFFSET, FrostlakeLexer.INTO
    };

    /** Where no token stands. */
    private static final int NONE = Integer.MIN_VALUE;

    private final List<Token> toks;
    private final int first;
    private final int last;
    /** The '(' of the call the bind is written in, or -1. */
    private final int open;

    private NullBindSlot(final List<Token> toks, final int colon) {
        this.toks = toks;
        int from = colon;
        int to = colon + 1;
        while (type(from - 1) == FrostlakeLexer.LPAREN && type(to + 1) == FrostlakeLexer.RPAREN
                && opensGroup(type(from - 2)) && !opensValuesRow(from - 1)) {
            from--;
            to++;
        }
        this.first = from;
        this.last = to;
        this.open = unmatchedOpenBefore(from - 1);
    }

    /**
     * The slot of the bind whose colon stands at {@code colon}, its name right after it.
     *
     * @param toks  the statement's tokens
     * @param colon the index of the bind's colon
     * @return the slot
     */
    static NullBindSlot at(final List<Token> toks, final int colon) {
        return new NullBindSlot(toks, colon);
    }

    /** Whether a '(' after a token of this type opens a bracket of its own rather than a call's arguments. */
    private static boolean opensGroup(final int before) {
        return before == FrostlakeLexer.LPAREN || before == FrostlakeLexer.COMMA || before == FrostlakeLexer.SELECT
            || before == FrostlakeLexer.DISTINCT;
    }

    /**
     * Whether the bind is a whole item of a VALUES row, which live binds as no type at all: {@code VALUES (:s), (1)}
     * over a NUMBER(10,2) is NUMBER(1,0) (live-verified).
     *
     * @return whether it is
     */
    boolean isValuesItem() {
        return isWholeItem() && open >= 0 && opensValuesRow(open);
    }

    /** Whether the '(' at {@code paren} opens a row of a VALUES list: VALUES before it, or a row and a comma. */
    private boolean opensValuesRow(final int paren) {
        int row = paren;
        while (row > 0) {
            final int before = type(row - 1);
            if (before == FrostlakeLexer.VALUES) {
                return true;
            }
            if (before != FrostlakeLexer.COMMA || type(row - 2) != FrostlakeLexer.RPAREN) {
                return false;
            }
            row = matchingOpen(row - 2);
        }
        return false;
    }

    /**
     * Whether the bind is a whole argument of a date or time function, over which a NULL bind is an untyped NULL:
     * {@code YEAR(:s)} over a DATE, {@code DATEADD(day, :n, d)} over a NUMBER (live-verified).
     *
     * @return whether it is
     */
    boolean isDateTimeArgument() {
        final String call = callName();
        return call != null && oneOf(call, DATE_TIME_FUNCTIONS)
            && (isWholeItem() || "EXTRACT".equals(call) && type(first - 1) == FrostlakeLexer.FROM
                && type(last + 1) == FrostlakeLexer.RPAREN);
    }

    /**
     * Whether a temporal bind is an operand of date arithmetic live folds to an untyped NULL: a {@code +} or
     * {@code -} with an INTERVAL literal, or with a number for a DATE — {@code :d + 1}, {@code 1 + :d},
     * {@code :t - INTERVAL '1 hour'}. Arithmetic live refuses, as {@code :t + 1} over a TIME, keeps the type
     * (live-verified).
     *
     * @param date whether the bind is a DATE
     * @return whether it is
     */
    boolean foldsTemporalArithmetic(final boolean date) {
        final int after = type(last + 1);
        if (after == FrostlakeLexer.PLUS || after == FrostlakeLexer.MINUS) {
            final int partner = type(last + 2);
            return partner == FrostlakeLexer.INTERVAL || date && isNumber(partner);
        }
        if (type(first - 1) == FrostlakeLexer.PLUS) {
            final int partner = type(first - 2);
            return date && isNumber(partner)
                || partner == FrostlakeLexer.STRING_LITERAL && type(first - 3) == FrostlakeLexer.INTERVAL;
        }
        return false;
    }

    /**
     * Whether a text bind stands where live reads it as the text it is, typed as declared: the whole argument of
     * SYSTEM$TYPEOF, a select item on its own in a statement that combines no queries, an operand of {@code ||} or
     * of COLLATE, the argument of a text function or of MIN, MAX and their like, or an argument of COALESCE and its
     * like beside text literals and NULLs alone — or where live reads it as a FLOAT: negated, beside a NULL in
     * arithmetic, or the one argument of ABS, ROUND or ZEROIFNULL. Anywhere a number may meet it — arithmetic, a CASE,
     * a set operation, a function reading a number — live reads the NULL as no type at all, so {@code :s + 1} over a
     * VARCHAR(10) is NUMBER(19,0) and {@code COALESCE(:s, 5)} NUMBER(1,0) (live-verified).
     *
     * @return whether it does
     */
    boolean readsAsText() {
        return isTypeOfArgument() || isSelectItem() && !combinesQueries() || isConcatOperand() || isTextArgument()
            || isAggregateArgument() || isConditionalBesideText() || type(last + 1) == FrostlakeLexer.COLLATE
            || readsAsFloat();
    }

    /** Whether a text bind is read as a FLOAT: negated, beside a NULL in arithmetic, or a float reader's argument. */
    private boolean readsAsFloat() {
        final int before = type(first - 1);
        final int after = type(last + 1);
        if (before == FrostlakeLexer.MINUS && (type(first - 2) == FrostlakeLexer.SELECT
                || type(first - 2) == FrostlakeLexer.DISTINCT || type(first - 2) == FrostlakeLexer.COMMA
                || type(first - 2) == FrostlakeLexer.LPAREN)) {
            return true;
        }
        if (isArithmetic(after) && type(last + 2) == FrostlakeLexer.NULL
                || isArithmetic(before) && type(first - 2) == FrostlakeLexer.NULL) {
            return true;
        }
        final String call = callName();
        return call != null && oneOf(call, FLOAT_READERS) && first - 1 == open && after == FrostlakeLexer.RPAREN;
    }

    private boolean isTypeOfArgument() {
        return "SYSTEM$TYPEOF".equals(callName()) && first - 1 == open && type(last + 1) == FrostlakeLexer.RPAREN;
    }

    private boolean isTextArgument() {
        final String call = callName();
        return call != null && oneOf(call, TEXT_FUNCTIONS) && isWholeItem() && (first - 1 == open || "CONCAT".equals(call));
    }

    private boolean isAggregateArgument() {
        final String call = callName();
        return call != null && oneOf(call, TEXT_AGGREGATES) && type(last + 1) == FrostlakeLexer.RPAREN
            && (first - 1 == open || first - 2 == open && type(first - 1) == FrostlakeLexer.DISTINCT);
    }

    private boolean isConcatOperand() {
        final int before = type(first - 1);
        final int after = type(last + 1);
        return (before == FrostlakeLexer.PIPE_PIPE || after == FrostlakeLexer.PIPE_PIPE)
            && !isArithmetic(before) && !isArithmetic(after);
    }

    /** Whether the bind is an argument of a conditional whose other value arguments are text literals or NULLs. */
    private boolean isConditionalBesideText() {
        final String call = callName();
        if (call == null || !oneOf(call, CONDITIONALS) || !isWholeItem()) {
            return false;
        }
        final int close = matchingClose(open);
        int argument = 0;
        int start = open + 1;
        int depth = 0;
        for (int i = open + 1; i <= close; i++) {
            final int t = type(i);
            if (t == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (t == FrostlakeLexer.RPAREN && i < close) {
                depth--;
            } else if (depth == 0 && (t == FrostlakeLexer.COMMA || i == close)) {
                final boolean holdsBind = start <= first && last < i;
                if ("IFF".equals(call) && argument == 0) {
                    if (holdsBind) {
                        return false;
                    }
                } else if (!holdsBind && (i - start != 1 || !isTextOrNull(type(start)))) {
                    return false;
                }
                argument++;
                start = i + 1;
            }
        }
        return close > open;
    }

    /** Whether the bind is a select item written alone: after SELECT or a select list's comma, before its end. */
    private boolean isSelectItem() {
        final int after = type(last + 1);
        if (!oneOf(after, ITEM_ENDS) && after != FrostlakeLexer.IDENTIFIER
                && after != FrostlakeLexer.QUOTED_IDENTIFIER) {
            return false;
        }
        final int before = type(first - 1);
        if (before == FrostlakeLexer.SELECT || before == FrostlakeLexer.DISTINCT) {
            return true;
        }
        if (before != FrostlakeLexer.COMMA) {
            return false;
        }
        int depth = 0;
        for (int i = first - 2; i >= 0; i--) {
            final int t = type(i);
            if (t == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (t == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return false;
                }
                depth--;
            } else if (depth == 0 && t == FrostlakeLexer.SELECT) {
                return true;
            } else if (depth == 0 && oneOf(t, CLAUSE_WORDS)) {
                return false;
            }
        }
        return false;
    }

    /** Whether the statement combines queries, so a select item may meet a number in another branch. */
    private boolean combinesQueries() {
        for (final Token token : toks) {
            final int t = token.getType();
            if (t == FrostlakeLexer.UNION || t == FrostlakeLexer.EXCEPT || t == FrostlakeLexer.MINUS_KW
                    || t == FrostlakeLexer.INTERSECT) {
                return true;
            }
        }
        return false;
    }

    /** Whether the bind is a whole item of a bracketed list: after its '(' or a comma, before a comma or its ')'. */
    private boolean isWholeItem() {
        final int before = type(first - 1);
        final int after = type(last + 1);
        return (before == FrostlakeLexer.LPAREN || before == FrostlakeLexer.COMMA)
            && (after == FrostlakeLexer.COMMA || after == FrostlakeLexer.RPAREN);
    }

    /** The upper-case name of the call the bind is written in, or null outside every call. */
    private String callName() {
        if (open < 1 || !SqlTokens.isWord(toks.get(open - 1))) {
            return null;
        }
        return toks.get(open - 1).getText().toUpperCase(Locale.ROOT);
    }

    /** The index of the '(' left open before index {@code from}, reading back, or -1. */
    private int unmatchedOpenBefore(final int from) {
        int depth = 0;
        for (int i = from; i >= 0; i--) {
            final int t = type(i);
            if (t == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (t == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return -1;
    }

    /** The index of the '(' the ')' at {@code close} closes, or -1. */
    private int matchingOpen(final int close) {
        return unmatchedOpenBefore(close - 1);
    }

    /** The index of the ')' closing the '(' at {@code opening}, or the last token's index. */
    private int matchingClose(final int opening) {
        int depth = 0;
        for (int i = opening + 1; i < toks.size(); i++) {
            final int t = type(i);
            if (t == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (t == FrostlakeLexer.RPAREN) {
                if (depth == 0) {
                    return i;
                }
                depth--;
            }
        }
        return toks.size() - 1;
    }

    private int type(final int index) {
        return index < 0 || index >= toks.size() ? NONE : toks.get(index).getType();
    }

    private static boolean isNumber(final int type) {
        return type == FrostlakeLexer.INTEGER_LITERAL || type == FrostlakeLexer.FLOAT_LITERAL;
    }

    private static boolean isTextOrNull(final int type) {
        return type == FrostlakeLexer.STRING_LITERAL || type == FrostlakeLexer.DOLLAR_QUOTED_STRING
            || type == FrostlakeLexer.NULL;
    }

    private static boolean isArithmetic(final int type) {
        return type == FrostlakeLexer.PLUS || type == FrostlakeLexer.MINUS || type == FrostlakeLexer.STAR
            || type == FrostlakeLexer.SLASH || type == FrostlakeLexer.PERCENT;
    }

    private static boolean oneOf(final String word, final String[] words) {
        for (final String each : words) {
            if (each.equals(word)) {
                return true;
            }
        }
        return false;
    }

    private static boolean oneOf(final int type, final int[] types) {
        for (final int each : types) {
            if (each == type) {
                return true;
            }
        }
        return false;
    }
}
