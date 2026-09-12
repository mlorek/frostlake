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

package dev.frostlake.functions.scalar.conversion;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * One reading of a text against one {@link NumericFormatAlternative}, in the account's LAX mode — every
 * row live-verified:
 *
 * <pre>
 *   white space          leading skipped, trailing ignored             ' 12' and '12 ' under '99' are 12
 *   9 and 0              no more digits than the model has, and each   '123' under '99' and '12' under
 *                        0 position and those after it written         '0000' are refused; '012' under '000' is 12
 *   , and G              may be left out, but fall where the model     '1234' under '9,999' is 1234 and
 *                        puts them                                     '1,23' is refused
 *   . and D              the point; no more fraction digits than the   '12.34' under '99.9' is refused;
 *                        model, each 0 position written                '1.5' under '9.90' too
 *   EE .. EEEEEEE        an exponent of one to three digits, required  '100000' under '9EEEE' is refused
 *   $  %  S              written where the model puts them             '50%' under '99%' is 0.50
 *   MI                   a sign that may be written there
 *   B                    the number may start at its point            '.5' under 'B9.9' is 0.5
 *   literals             as written; a space is one space or more      '1  2' under '9 9' is 12
 *   TM9, TME, TM, AUTO   any number: positional, scientific, either    '1e5' under 'TM9' is refused
 *   X                    hexadecimal digits, and no sign               'ff' under 'XX' is 255
 * </pre>
 *
 * <p>Without S or MI a sign may lead the text ('- 12' under '99' is -12). A run of digits the model
 * splits with elements the text need not write may read none ('1' under '9MI9' is 1), where a literal
 * between them makes both runs written ('1' under '9 9' is refused). After a text-minimal number
 * {@code $}, {@code S} and {@code MI} read nothing. FX, the exact mode, is read as the lax one.
 */
final class NumericFormatReader {

    /** The most digits a lax exponent carries. */
    private static final int EXPONENT_DIGITS = 3;

    private final String text;
    private final NumericFormatAlternative alternative;
    private final StringBuilder whole = new StringBuilder();
    private final StringBuilder fraction = new StringBuilder();
    private int pos;
    private boolean negative;
    private boolean hundredths;
    private boolean numberSeen;
    /** Whether an element the text had to write has been read since the last run of digits. */
    private boolean requiredSinceNumber;
    private int exponent;

    /**
     * @param text the text being read
     * @param alternative the alternative it is read against
     */
    NumericFormatReader(final String text, final NumericFormatAlternative alternative) {
        this.text = text;
        this.alternative = alternative;
    }

    /**
     * Read the whole text.
     *
     * @param implicitSign whether a sign may lead the text though the alternative places none
     * @return the value, or null where the text does not fit the alternative
     */
    BigDecimal read(final boolean implicitSign) {
        skipWhiteSpace();
        if (implicitSign && pos < text.length() && isSign(text.charAt(pos))) {
            negative = text.charAt(pos) == '-';
            pos++;
            skipSpaces();
        }
        final List<NumericFormatElement> kinds = alternative.kinds();
        int at = 0;
        while (at < kinds.size()) {
            if (isNumberElement(kinds.get(at))) {
                int end = at;
                while (end < kinds.size() && isNumberElement(kinds.get(end))) {
                    end++;
                }
                final List<NumericFormatElement> run = kinds.subList(at, end);
                if (!(alternative.hexadecimal() ? readHexadecimal(run) : readDecimal(run))) {
                    return null;
                }
                numberSeen = true;
                requiredSinceNumber = false;
                at = end;
                continue;
            }
            if (!readElement(kinds.get(at), alternative.spellings().get(at))) {
                return null;
            }
            at++;
        }
        skipWhiteSpace();
        if (pos != text.length() || whole.length() + fraction.length() == 0) {
            return null;
        }
        return value();
    }

    /** The elements a number is read across; a text-minimal model reads its number as one element. */
    private boolean isNumberElement(final NumericFormatElement kind) {
        if (alternative.textMinimal()) {
            return false;
        }
        if (alternative.hexadecimal()) {
            return kind == NumericFormatElement.HEX || kind == NumericFormatElement.ZERO
                || kind == NumericFormatElement.BLANK;
        }
        return kind == NumericFormatElement.DIGIT || kind == NumericFormatElement.ZERO
            || kind == NumericFormatElement.GROUP || kind == NumericFormatElement.DECIMAL
            || kind == NumericFormatElement.BLANK;
    }

    private boolean readElement(final NumericFormatElement kind, final String spelling) {
        switch (kind) {
            case TEXT_MINIMAL:
            case TEXT_MINIMAL_POSITIONAL:
            case TEXT_MINIMAL_SCIENTIFIC:
                numberSeen = true;
                requiredSinceNumber = false;
                return readTextMinimal(kind);
            case EXPONENT:
                requiredSinceNumber = true;
                return readExponent();
            case DOLLAR:
                if (numberSeen && alternative.textMinimal()) {
                    return true;
                }
                requiredSinceNumber = true;
                if (!expect('$')) {
                    return false;
                }
                skipSpaces();
                return true;
            case PERCENT:
                hundredths = true;
                requiredSinceNumber = true;
                return expect('%');
            case SIGN:
            case MINUS:
                if (numberSeen && alternative.textMinimal()) {
                    return true;
                }
                requiredSinceNumber = requiredSinceNumber || kind == NumericFormatElement.SIGN;
                return readSign(kind == NumericFormatElement.SIGN);
            case OPTIONAL_SPACE:
                skipSpaces();
                return true;
            case LITERAL:
                requiredSinceNumber = true;
                return readLiteral(spelling);
            case GROUP:
                requiredSinceNumber = true;
                return expect(',');
            case DECIMAL:
                requiredSinceNumber = true;
                return !alternative.hexadecimal() && expect('.');
            default:
                // FM, FX and B read nothing.
                return true;
        }
    }

    /** A run of decimal digit positions, group separators and the point. */
    private boolean readDecimal(final List<NumericFormatElement> run) {
        int wholePositions = 0;
        int firstZero = -1;
        int fractionPositions = 0;
        int fractionRequired = 0;
        boolean point = false;
        final List<Integer> groupMarks = new ArrayList<>();
        for (final NumericFormatElement kind : run) {
            if (kind == NumericFormatElement.DECIMAL) {
                point = true;
            } else if (kind == NumericFormatElement.DIGIT || kind == NumericFormatElement.ZERO) {
                if (point) {
                    fractionPositions++;
                    if (kind == NumericFormatElement.ZERO) {
                        fractionRequired = fractionPositions;
                    }
                } else {
                    if (kind == NumericFormatElement.ZERO && firstZero < 0) {
                        firstZero = wholePositions;
                    }
                    wholePositions++;
                }
            } else if (kind == NumericFormatElement.GROUP && !point) {
                groupMarks.add(Integer.valueOf(wholePositions));
            }
        }
        final int wholeRequired = firstZero < 0 ? 0 : wholePositions - firstZero;
        // Each separator by the digit positions to its right, which is where a written one must fall.
        final List<Integer> separators = new ArrayList<>();
        for (final Integer mark : groupMarks) {
            separators.add(Integer.valueOf(wholePositions - mark.intValue()));
        }
        final StringBuilder digits = new StringBuilder();
        final List<Integer> commas = new ArrayList<>();
        while (pos < text.length()) {
            final char ch = text.charAt(pos);
            if (NumericFormatModel.isAsciiDigit(ch)) {
                digits.append(ch);
            } else if (ch == ',' && !separators.isEmpty()) {
                commas.add(Integer.valueOf(digits.length()));
            } else {
                break;
            }
            pos++;
        }
        if (!commas.isEmpty()) {
            if (commas.get(0).intValue() == 0 || commas.get(commas.size() - 1).intValue() == digits.length()) {
                return false;
            }
            for (final Integer comma : commas) {
                if (!separators.remove(Integer.valueOf(digits.length() - comma.intValue()))) {
                    return false;
                }
            }
        }
        // The first run may be empty only under B; a later one when nothing written separates it.
        final boolean mayBeEmpty = numberSeen ? !requiredSinceNumber : alternative.blank();
        if (digits.length() > wholePositions || digits.length() < wholeRequired
                || (digits.length() == 0 && !mayBeEmpty)) {
            return false;
        }
        whole.append(digits);
        if (!point) {
            return true;
        }
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            final int start = pos;
            while (pos < text.length() && NumericFormatModel.isAsciiDigit(text.charAt(pos))) {
                pos++;
            }
            fraction.append(text, start, pos);
            final int count = pos - start;
            return count <= fractionPositions && count >= fractionRequired;
        }
        return fractionRequired == 0;
    }

    /** A run of hexadecimal digit positions. */
    private boolean readHexadecimal(final List<NumericFormatElement> run) {
        int positions = 0;
        int firstZero = -1;
        for (final NumericFormatElement kind : run) {
            if (kind == NumericFormatElement.HEX || kind == NumericFormatElement.ZERO) {
                if (kind == NumericFormatElement.ZERO && firstZero < 0) {
                    firstZero = positions;
                }
                positions++;
            }
        }
        final int required = firstZero < 0 ? 0 : positions - firstZero;
        final int start = pos;
        while (pos < text.length() && isAsciiHexDigit(text.charAt(pos))) {
            pos++;
        }
        final int count = pos - start;
        if (count == 0 || count > positions || count < required) {
            return false;
        }
        whole.append(text, start, pos);
        return true;
    }

    /** A text-minimal number: digits, an optional fraction, and the exponent the form asks for. */
    private boolean readTextMinimal(final NumericFormatElement kind) {
        final int groupSize = alternative.groupSize();
        final StringBuilder digits = new StringBuilder();
        final List<Integer> commas = new ArrayList<>();
        while (pos < text.length()) {
            final char ch = text.charAt(pos);
            if (NumericFormatModel.isAsciiDigit(ch)) {
                digits.append(ch);
            } else if (ch == ',' && groupSize > 0) {
                commas.add(Integer.valueOf(digits.length()));
            } else {
                break;
            }
            pos++;
        }
        if (digits.length() == 0) {
            return false;
        }
        for (final Integer comma : commas) {
            final int at = comma.intValue();
            if (at == 0 || at == digits.length() || (digits.length() - at) % groupSize != 0) {
                return false;
            }
        }
        whole.append(digits);
        if (pos < text.length() && text.charAt(pos) == '.') {
            pos++;
            final int start = pos;
            while (pos < text.length() && NumericFormatModel.isAsciiDigit(text.charAt(pos))) {
                pos++;
            }
            fraction.append(text, start, pos);
        }
        if (kind == NumericFormatElement.TEXT_MINIMAL_POSITIONAL) {
            return true;
        }
        if (pos < text.length() && isExponentLetter(text.charAt(pos))) {
            return readExponent();
        }
        return kind == NumericFormatElement.TEXT_MINIMAL;
    }

    /** An exponent: E or e, an optional sign, then one to three digits. */
    private boolean readExponent() {
        if (pos >= text.length() || !isExponentLetter(text.charAt(pos))) {
            return false;
        }
        int at = pos + 1;
        boolean minus = false;
        if (at < text.length() && isSign(text.charAt(at))) {
            minus = text.charAt(at) == '-';
            at++;
        }
        final int start = at;
        while (at < text.length() && at - start < EXPONENT_DIGITS && NumericFormatModel.isAsciiDigit(text.charAt(at))) {
            at++;
        }
        if (at == start) {
            return false;
        }
        final int written = Integer.parseInt(text.substring(start, at));
        exponent = minus ? -written : written;
        pos = at;
        return true;
    }

    private boolean readSign(final boolean required) {
        if (pos < text.length() && isSign(text.charAt(pos))) {
            negative = text.charAt(pos) == '-';
            pos++;
            if (!numberSeen) {
                skipSpaces();
            }
            return true;
        }
        return !required;
    }

    /** Literal text, matched as written, each run of spaces in it by one space or more. */
    private boolean readLiteral(final String spelling) {
        final String literal = spelling.startsWith("\"") ? spelling.substring(1, spelling.length() - 1) : spelling;
        int at = 0;
        while (at < literal.length()) {
            if (literal.charAt(at) == ' ') {
                final int start = pos;
                skipSpaces();
                if (pos == start) {
                    return false;
                }
                while (at < literal.length() && literal.charAt(at) == ' ') {
                    at++;
                }
                continue;
            }
            if (pos >= text.length() || text.charAt(pos) != literal.charAt(at)) {
                return false;
            }
            pos++;
            at++;
        }
        return true;
    }

    private boolean expect(final char expected) {
        if (pos < text.length() && text.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private void skipSpaces() {
        while (pos < text.length() && text.charAt(pos) == ' ') {
            pos++;
        }
    }

    /** Spaces, tabs, line breaks, form feeds and vertical tabs. */
    private void skipWhiteSpace() {
        while (pos < text.length() && text.charAt(pos) <= ' ') {
            pos++;
        }
    }

    private BigDecimal value() {
        BigDecimal value;
        if (alternative.hexadecimal()) {
            value = new BigDecimal(new BigInteger(whole.toString(), 16));
        } else {
            value = new BigDecimal((whole.length() == 0 ? "0" : whole.toString())
                + (fraction.length() == 0 ? "" : "." + fraction));
        }
        if (exponent != 0) {
            value = value.scaleByPowerOfTen(exponent);
        }
        if (hundredths) {
            value = value.movePointLeft(2);
        }
        return negative ? value.negate() : value;
    }

    private static boolean isSign(final char c) {
        return c == '+' || c == '-';
    }

    private static boolean isExponentLetter(final char c) {
        return c == 'e' || c == 'E';
    }

    private static boolean isAsciiHexDigit(final char c) {
        return NumericFormatModel.isAsciiDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
