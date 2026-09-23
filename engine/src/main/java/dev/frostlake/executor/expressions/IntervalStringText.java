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

package dev.frostlake.executor.expressions;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The quoted-string interval literal's text, {@code INTERVAL '1 day, 2 hours'}, read as the little language
 * it is on the account: parts separated by commas, each an optional sign, a number and an optional unit word.
 * The text is read while the statement compiles, and a token out of place is a syntax error counted inside
 * the TEXT — its line and its position from 0 on that line, {@code '<EOF>'} at its end — before any unit is
 * looked at (all live-verified):
 *
 * <pre>
 *   '1 day 2 hours'     syntax error line 1 at position 6 unexpected '2'.
 *   '1 day hours'       … position 6 unexpected 'hours'.       '1 day -2 hours'  … position 6 unexpected '-'.
 *   'day'               … position 0 unexpected 'day'.          ''                … position 0 unexpected '&lt;EOF&gt;'.
 *   '1 days,'           … position 7 unexpected '&lt;EOF&gt;'.        '1.5.5 days'      … position 0 unexpected '1.5.5'.
 *   '1 select'          … position 2 unexpected 'select'.       '1 ''day'''       … position 2 unexpected ''''.
 * </pre>
 *
 * <p>A number may carry a fraction or an exponent ({@code '.5'}, {@code '5.'}, {@code '1e2'}), and its amount is
 * rounded half away from zero to a whole count of its unit: {@code '1.5 days'} is two days, {@code '1.5'} two
 * seconds. The text takes the statement's comments: {@code --} and {@code //} to the end of the line, and a block
 * comment anywhere; a block comment left open, like a double-quoted word left open, is "parse error line 1 at
 * position 17 near '&lt;EOF&gt;'." at the text's end. A reserved word of the statement's own ({@code select},
 * {@code from}, {@code null} and the rest of {@code KEYWORDS}) is no unit but a token out of place, and a quote is
 * echoed doubled. A unit word the account does not know is "&lt;word&gt; is not recognized as a date type." on one
 * line, the word as written — a quoted one with its quotes. An amount its unit cannot count is refused only when
 * a row reaches the literal (see {@link #requireRepresentable}).
 */
public final class IntervalStringText {

    private static final Pattern NUMBER = Pattern.compile("(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    /** The words the text refuses where it expects a unit, measured one by one; any other word is a unit name. */
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "ALL", "ALTER", "AND", "ANY", "AS", "BETWEEN", "BY", "CHECK", "COLUMN", "CONNECT", "CREATE", "CURRENT",
        "DELETE", "DISTINCT", "DROP", "ELSE", "EXISTS", "FOLLOWING", "FOR", "FROM", "GRANT", "GROUP", "HAVING",
        "ILIKE", "IN", "INCREMENT", "INSERT", "INTERSECT", "INTO", "IS", "LIKE", "MINUS", "NOT", "NULL", "OF", "ON",
        "OR", "ORDER", "QUALIFY", "REGEXP", "REVOKE", "RLIKE", "ROW", "ROWS", "SAMPLE", "SELECT", "SET", "SOME",
        "START", "TABLE", "TABLESAMPLE", "THEN", "TO", "TRIGGER", "UNION", "UNIQUE", "UPDATE", "VALUES", "WHENEVER",
        "WHERE", "WITH"));

    /** The count a day, week, month, quarter, year or hour amount stays under, and the type it is refused as. */
    private static final double NARROW_LIMIT = 1e9;
    private static final String NARROW_TYPE = "FIXED[SB4](9,0)";
    /** The count a minute, second or sub-second amount stays under, and the type it is refused as. */
    private static final double WIDE_LIMIT = 1e18;
    private static final String WIDE_TYPE = "FIXED[SB8](18,0)";

    private static final int SIGN = 0;
    private static final int NUMBER_TOKEN = 1;
    private static final int WORD = 2;
    private static final int COMMA = 3;
    private static final int OTHER = 4;
    private static final int END = 5;
    private static final int KEYWORD = 6;

    private final String text;
    private int index;
    private int line = 1;
    private int column;

    /** The tokens, the last one END: each one's kind, its text, and where it starts. */
    private final List<Integer> kinds = new ArrayList<>();
    private final List<String> tokens = new ArrayList<>();
    private final List<Integer> lines = new ArrayList<>();
    private final List<Integer> columns = new ArrayList<>();
    /** The line a comment or a quoted word left open at the end of the text adds, or null. */
    private String unclosed;

    private IntervalStringText(final String text) {
        this.text = text;
        while (true) {
            final int kind = next();
            if (kind == END) {
                break;
            }
        }
    }

    /**
     * The literal's parts as the chain date arithmetic applies in order, refused when the text does not read.
     * Each part keeps its amount as written, its sign included, and its unit word as written, which is how a
     * refusal names the literal: {@code INTERVAL_LITERAL('hours', '1.5')}.
     *
     * @param text the literal's text, unquoted
     * @return the first part, the rest chained behind it
     */
    public static IntervalExpression chain(final String text) {
        final List<BigDecimal> amounts = new ArrayList<>();
        final List<String> units = new ArrayList<>();
        final List<String> written = new ArrayList<>();
        new IntervalStringText(text).read(amounts, units, written);
        final List<IntervalUnit> resolved = new ArrayList<>();
        for (final String unit : units) {
            resolved.add(unitOf(unit));
        }
        IntervalExpression chain = null;
        for (int i = amounts.size() - 1; i >= 0; i--) {
            final BigDecimal amount = amounts.get(i);
            // A count past a long is kept whole: it is refused as out of range once a row reads it.
            final LiteralExpression count = amount.toBigInteger().bitLength() < Long.SIZE
                ? new LiteralExpression(Long.valueOf(amount.longValueExact()), LiteralType.INTEGER)
                : new LiteralExpression(amount, LiteralType.DECIMAL);
            chain = new IntervalExpression(count, resolved.get(i), chain);
            chain.recordWritten(written.get(i), units.get(i));
        }
        chain.markUnitInString();
        return chain;
    }

    /**
     * Refuse a text that does not read — a token out of place, a comment or a quoted word left open — while the
     * statement parses, ahead of every other refusal; the unit words are judged later, with the statement's names.
     *
     * @param text the literal's text, unquoted
     */
    public static void requireWellFormed(final String text) {
        new IntervalStringText(text).read(new ArrayList<BigDecimal>(), new ArrayList<String>(),
            new ArrayList<String>());
    }

    /**
     * Refuse, as a row reads it, an amount its unit cannot count: a day, week, month, quarter, year or hour amount
     * under a billion, a minute, second or sub-second one under 10^18, each compared as a double and spelled the
     * way C's {@code %g} spells it — "Number out of representable range: type FIXED[SB4](9,0){not null}, value
     * 1e+09" (live-verified; an empty table answers no row).
     *
     * @param amount the part's count
     * @param unit   the part's unit
     */
    public static void requireRepresentable(final Object amount, final IntervalUnit unit) {
        if (!(amount instanceof Number)) {
            return;
        }
        final double value = ((Number) amount).doubleValue();
        final boolean narrow = unit.isWholeDay() || unit == IntervalUnit.HOUR;
        if (Math.abs(value) < (narrow ? NARROW_LIMIT : WIDE_LIMIT)) {
            return;
        }
        throw new RuntimeException("Number out of representable range: type " + (narrow ? NARROW_TYPE : WIDE_TYPE)
            + "{not null}, value " + shortest(value));
    }

    /** A double as C's {@code %g} prints it: six significant digits, trailing zeros dropped. */
    private static String shortest(final double value) {
        final String printed = String.format(Locale.ROOT, "%.6g", value);
        final int exponent = printed.indexOf('e');
        String mantissa = exponent < 0 ? printed : printed.substring(0, exponent);
        if (mantissa.indexOf('.') >= 0) {
            while (mantissa.endsWith("0")) {
                mantissa = mantissa.substring(0, mantissa.length() - 1);
            }
            if (mantissa.endsWith(".")) {
                mantissa = mantissa.substring(0, mantissa.length() - 1);
            }
        }
        return exponent < 0 ? mantissa : mantissa + printed.substring(exponent);
    }

    /** Reads the parts: each one's amount, rounded, its unit word or null, and its amount as written. */
    private void read(final List<BigDecimal> amounts, final List<String> units, final List<String> written) {
        int at = 0;
        while (true) {
            boolean negative = false;
            String sign = "";
            if (kinds.get(at) == SIGN) {
                negative = "-".equals(tokens.get(at));
                sign = tokens.get(at);
                at++;
            }
            if (kinds.get(at) != NUMBER_TOKEN || !NUMBER.matcher(tokens.get(at)).matches()) {
                throw unexpected(at, false);
            }
            final BigDecimal amount = new BigDecimal(tokens.get(at));
            amounts.add((negative ? amount.negate() : amount).setScale(0, RoundingMode.HALF_UP));
            written.add(sign + tokens.get(at));
            at++;
            if (kinds.get(at) == WORD) {
                units.add(tokens.get(at));
                at++;
            } else if (kinds.get(at) == KEYWORD) {
                throw unexpected(at, false);
            } else {
                units.add(null);
            }
            if (kinds.get(at) == END) {
                break;
            }
            if (kinds.get(at) != COMMA) {
                throw unexpected(at, true);
            }
            at++;
        }
        if (unclosed != null) {
            throw new RuntimeException("SQL compilation error:\n" + unclosed);
        }
    }

    /** The unit a word names, SECOND for none, refused in the account's words when it names none. */
    private static IntervalUnit unitOf(final String word) {
        if (word == null) {
            return IntervalUnit.SECOND;
        }
        final IntervalUnit unit = IntervalUnit.fromSpelling(word);
        if (unit == null) {
            throw new RuntimeException("SQL compilation error: " + word + " is not recognized as a date type.");
        }
        return unit;
    }

    /**
     * The refusal of the token at {@code at}. A comment or a quoted word left open adds its own line: after this
     * one, or before it when reading on to it came first — the refused token is the text's end, or a token where
     * a comma was expected with the open comment straight after it (live-verified).
     */
    private RuntimeException unexpected(final int at, final boolean commaExpected) {
        final String syntax = "syntax error line " + lines.get(at) + " at position " + columns.get(at)
            + " unexpected '" + (kinds.get(at) == END ? "<EOF>" : tokens.get(at).replace("'", "''")) + "'.";
        if (unclosed == null) {
            return new RuntimeException("SQL compilation error:\n" + syntax);
        }
        final boolean readOnFirst = kinds.get(at) == END
            || commaExpected && kinds.get(at) != KEYWORD && kinds.get(at + 1) == END;
        return new RuntimeException("SQL compilation error:\n"
            + (readOnFirst ? unclosed + "\n" + syntax : syntax + "\n" + unclosed));
    }

    /** Reads the next token, skipping blanks and comments, and returns its kind. */
    private int next() {
        skipBlanks();
        final int tokenLine = line;
        final int tokenColumn = column;
        final int start = index;
        int kind;
        if (unclosed != null || index >= text.length()) {
            kind = END;
        } else {
            kind = scan(text.charAt(index));
        }
        if (unclosed != null) {
            // What was left open runs to the end of the text, where the reading stops.
            kind = END;
        }
        kinds.add(Integer.valueOf(kind));
        tokens.add(kind == END ? "" : text.substring(start, index));
        lines.add(Integer.valueOf(kind == END ? line : tokenLine));
        columns.add(Integer.valueOf(kind == END ? column : tokenColumn));
        return kind;
    }

    /** Consumes the token starting with {@code first} and returns its kind. */
    private int scan(final char first) {
        if (first == '+' || first == '-') {
            step();
            return SIGN;
        }
        if (first == ',') {
            step();
            return COMMA;
        }
        if (Character.isDigit(first) || first == '.') {
            while (index < text.length() && (Character.isDigit(text.charAt(index)) || text.charAt(index) == '.')) {
                step();
            }
            if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E') && exponentAhead()) {
                step();
                if (text.charAt(index) == '+' || text.charAt(index) == '-') {
                    step();
                }
                while (index < text.length() && Character.isDigit(text.charAt(index))) {
                    step();
                }
            }
            return NUMBER_TOKEN;
        }
        if (Character.isLetter(first) || first == '_') {
            final int start = index;
            while (index < text.length() && (Character.isLetterOrDigit(text.charAt(index))
                    || text.charAt(index) == '_' || text.charAt(index) == '$')) {
                step();
            }
            return KEYWORDS.contains(text.substring(start, index).toUpperCase(Locale.ROOT)) ? KEYWORD : WORD;
        }
        if (first == '"' && index + 1 < text.length()) {
            // A double-quoted word; one never closed runs to the end of the text.
            step();
            while (index < text.length() && text.charAt(index) != '"') {
                step();
            }
            if (index >= text.length()) {
                leftOpen();
                return END;
            }
            step();
            return WORD;
        }
        if (first == '\'' && index + 1 < text.length() && text.charAt(index + 1) == '\'') {
            step();
        }
        step();
        return OTHER;
    }

    /** Whether an exponent's digits follow the {@code e} at the current index. */
    private boolean exponentAhead() {
        int ahead = index + 1;
        if (ahead < text.length() && (text.charAt(ahead) == '+' || text.charAt(ahead) == '-')) {
            ahead++;
        }
        return ahead < text.length() && Character.isDigit(text.charAt(ahead));
    }

    /** Skips blanks and comments; a block comment never closed runs to the end of the text. */
    private void skipBlanks() {
        while (index < text.length()) {
            final char here = text.charAt(index);
            final char after = index + 1 < text.length() ? text.charAt(index + 1) : ' ';
            if (Character.isWhitespace(here)) {
                step();
            } else if (here == '-' && after == '-' || here == '/' && after == '/') {
                while (index < text.length() && text.charAt(index) != '\n') {
                    step();
                }
            } else if (here == '/' && after == '*') {
                step();
                step();
                while (index < text.length() && !(text.charAt(index) == '*' && index + 1 < text.length()
                        && text.charAt(index + 1) == '/')) {
                    step();
                }
                if (index >= text.length()) {
                    leftOpen();
                    return;
                }
                step();
                step();
            } else {
                return;
            }
        }
    }

    /** Notes that a comment or a quoted word runs past the end of the text, which is where it is refused. */
    private void leftOpen() {
        while (index < text.length()) {
            step();
        }
        unclosed = "parse error line " + line + " at position " + column + " near '<EOF>'.";
    }

    private void step() {
        if (text.charAt(index) == '\n') {
            line++;
            column = 0;
        } else {
            column++;
        }
        index++;
    }
}
