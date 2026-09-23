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
 * {@code $}, {@code S} and {@code MI} read nothing.
 *
 * <p>FX toggles the EXACT mode for the elements after it, and FM the compact one, each written again
 * toggling back. A text is read exactly as the model prints it in fill mode — also live-verified:
 *
 * <pre>
 *   white space          skipped at the start only when the model does not start with FX, and at
 *                        the end only when the mode is lax there                 ' 1 ' under 'FX9' is refused
 *   9, 0, , and $        the whole number field at its printed width: a sign position first, a
 *                        space, '+' or '-' written just before the digits, $ just after it, and
 *                        leading positions as spaces or zeros                    ' 1', '-1' and '+1' under 'FX9' are 1,
 *                                                                                '1' is refused; '  1' and ' 01'
 *                                                                                under 'FX99' are 1
 *   . and fraction       as many characters as fraction positions, trailing zeros written as
 *                        digits or spaces                                        ' 1.5 ' and ' 1.50' under 'FX9.99'
 *   EEE .. EEEEEEE       exactly as wide as the element                          ' 1.5E+0' under 'FX9.9EEEE' is refused
 *   S, MI                one character: the sign, or a space for MI              '1 ' and '1-' under 'FX9MI'
 *   X                    the positions' width, with no sign position             'FF' under 'FXXX'; ' FF' is refused
 *   TM9, TME, TM         a sign position before the number                       ' 1' under 'FXTM9'; '1' is refused
 *   literals             character by character, a space as exactly one space    '  1' under 'FX 9'
 * </pre>
 *
 * <p>In compact mode an exact number is read as a lax one, but with no white space around it:
 * '1' under 'FXFM9' is 1 and ' FF' under 'FXFMXX' is refused.
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
    /** Whether the elements being read now match exactly: FX toggles it. */
    private boolean exact;
    /** Whether the elements being read now are in fill mode: FM toggles it off and on. */
    private boolean fill = true;
    /** Whether a sign may lead the first number because the alternative places none. */
    private boolean implicitSign;
    /** Whether an exact fill-mode $ floats into the number field that follows it. */
    private boolean dollarPending;
    /** Whether an exact fill-mode S floats into the number field that follows it. */
    private boolean signPending;

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
        this.implicitSign = implicitSign;
        final List<NumericFormatElement> kinds = alternative.kinds();
        if (!startsExact(alternative)) {
            skipWhiteSpace();
            if (implicitSign && pos < text.length() && isSign(text.charAt(pos))) {
                negative = text.charAt(pos) == '-';
                pos++;
                skipSpaces();
            }
        }
        int at = 0;
        while (at < kinds.size()) {
            if (isNumberElement(kinds.get(at))) {
                int end = at;
                while (end < kinds.size() && isNumberElement(kinds.get(end))) {
                    end++;
                }
                final List<NumericFormatElement> run = kinds.subList(at, end);
                if (!readNumberRun(run)) {
                    return null;
                }
                numberSeen = true;
                requiredSinceNumber = false;
                at = end;
                continue;
            }
            final boolean numberFollows = at + 1 < kinds.size() && isNumberElement(kinds.get(at + 1));
            if (!readElement(kinds.get(at), alternative.spellings().get(at), numberFollows)) {
                return null;
            }
            at++;
        }
        if (!exact) {
            skipWhiteSpace();
        }
        if (pos != text.length() || whole.length() + fraction.length() == 0) {
            return null;
        }
        return value();
    }

    /**
     * Whether an alternative starts in the exact mode: the toggles it leads with leave FX on, so 'FX9'
     * starts exact and 'FXFX9' lax (' 1' under it is 1).
     *
     * @param alternative the alternative
     * @return whether the text is matched exactly from its first character
     */
    static boolean startsExact(final NumericFormatAlternative alternative) {
        boolean exactAtStart = false;
        for (final NumericFormatElement kind : alternative.kinds()) {
            if (kind == NumericFormatElement.EXACT_MODE) {
                exactAtStart = !exactAtStart;
            } else if (kind != NumericFormatElement.FILL_MODE) {
                break;
            }
        }
        return exactAtStart;
    }

    /** One run of number positions, in the mode the elements before it left. */
    private boolean readNumberRun(final List<NumericFormatElement> run) {
        if (!exact) {
            return alternative.hexadecimal() ? readHexadecimal(run) : readDecimal(run);
        }
        if (!fill) {
            // Compact: as lax reads it, with a sign written right before the first number.
            if (implicitSign && !numberSeen && pos < text.length() && isSign(text.charAt(pos))) {
                negative = text.charAt(pos) == '-';
                pos++;
            }
            return alternative.hexadecimal() ? readHexadecimal(run) : readDecimal(run);
        }
        return alternative.hexadecimal() ? readExactHexadecimal(run) : readExactDecimal(run);
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

    private boolean readElement(final NumericFormatElement kind, final String spelling,
                                final boolean numberFollows) {
        switch (kind) {
            case TEXT_MINIMAL:
            case TEXT_MINIMAL_POSITIONAL:
            case TEXT_MINIMAL_SCIENTIFIC:
                if (exact && fill && implicitSign && !numberSeen && !readSignPosition()) {
                    return false;
                }
                numberSeen = true;
                requiredSinceNumber = false;
                return readTextMinimal(kind);
            case EXPONENT:
                requiredSinceNumber = true;
                return exact && fill && spelling.length() > 2 ? readExactExponent(spelling.length()) : readExponent();
            case DOLLAR:
                if (numberSeen && alternative.textMinimal()) {
                    return true;
                }
                requiredSinceNumber = true;
                if (exact && fill && numberFollows) {
                    dollarPending = true;
                    return true;
                }
                if (!expect('$')) {
                    return false;
                }
                if (!exact) {
                    skipSpaces();
                }
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
                if (exact && fill) {
                    if (kind == NumericFormatElement.SIGN && numberFollows) {
                        signPending = true;
                        return true;
                    }
                    return kind == NumericFormatElement.SIGN ? readSign(true) : readSignPosition();
                }
                return readSign(kind == NumericFormatElement.SIGN);
            case OPTIONAL_SPACE:
                skipSpaces();
                return true;
            case LITERAL:
                requiredSinceNumber = true;
                return exact ? readExactLiteral(spelling) : readLiteral(spelling);
            case GROUP:
                requiredSinceNumber = true;
                return expect(',');
            case DECIMAL:
                requiredSinceNumber = true;
                return !alternative.hexadecimal() && expect('.');
            case FILL_MODE:
                fill = !fill;
                return true;
            case EXACT_MODE:
                exact = !exact;
                return true;
            default:
                // B reads nothing.
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

    /**
     * A run of decimal positions matched exactly, in fill mode: the whole number field at its printed
     * width, then the point and exactly as many characters as the fraction has positions.
     */
    private boolean readExactDecimal(final List<NumericFormatElement> run) {
        // The whole part's positions from the right: a digit (true when it is a 0) or a separator (null).
        final List<Boolean> slots = new ArrayList<>();
        int fractionPositions = 0;
        int fractionRequired = 0;
        boolean point = false;
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
                    slots.add(0, Boolean.valueOf(kind == NumericFormatElement.ZERO));
                }
            } else if (kind == NumericFormatElement.GROUP && !point) {
                slots.add(0, null);
            }
        }
        final boolean signSlot = signPending || (implicitSign && !numberSeen);
        final int end = pos + (signSlot ? 1 : 0) + (dollarPending ? 1 : 0) + slots.size();
        if (end > text.length()) {
            return false;
        }
        int at = pos;
        while (at < end && text.charAt(at) == ' ') {
            at++;
        }
        if (signSlot && at < end && isSign(text.charAt(at))) {
            negative = text.charAt(at) == '-';
            at++;
        } else if (signPending) {
            return false;
        }
        if (dollarPending) {
            if (at >= end || text.charAt(at) != '$') {
                return false;
            }
            at++;
        }
        signPending = false;
        dollarPending = false;
        final int area = end - at;
        if (area > slots.size() || (area == 0 && !slots.isEmpty())) {
            return false;
        }
        for (int fromRight = slots.size(); fromRight > area; fromRight--) {
            if (Boolean.TRUE.equals(slots.get(fromRight - 1))) {
                // A 0 position prints its digit, so it cannot stand among the leading spaces.
                return false;
            }
        }
        final StringBuilder digits = new StringBuilder();
        for (int i = at; i < end; i++) {
            final char ch = text.charAt(i);
            if (slots.get(end - i - 1) == null) {
                if (ch != ',' || digits.length() == 0) {
                    return false;
                }
            } else if (NumericFormatModel.isAsciiDigit(ch)) {
                digits.append(ch);
            } else {
                return false;
            }
        }
        whole.append(digits);
        pos = end;
        if (!point) {
            return true;
        }
        if (!expect('.') || pos + fractionPositions > text.length()) {
            return false;
        }
        final int fractionEnd = pos + fractionPositions;
        while (pos < fractionEnd && NumericFormatModel.isAsciiDigit(text.charAt(pos))) {
            fraction.append(text.charAt(pos));
            pos++;
        }
        while (pos < fractionEnd && text.charAt(pos) == ' ') {
            pos++;
        }
        return pos == fractionEnd && (fractionPositions == 0 || fraction.length() > 0)
            && fraction.length() >= fractionRequired;
    }

    /** A run of hexadecimal positions matched exactly, in fill mode: leading spaces, then the digits. */
    private boolean readExactHexadecimal(final List<NumericFormatElement> run) {
        final List<Boolean> slots = new ArrayList<>();
        for (final NumericFormatElement kind : run) {
            if (kind == NumericFormatElement.HEX || kind == NumericFormatElement.ZERO) {
                slots.add(0, Boolean.valueOf(kind == NumericFormatElement.ZERO));
            }
        }
        final int end = pos + slots.size();
        if (end > text.length()) {
            return false;
        }
        int at = pos;
        while (at < end && text.charAt(at) == ' ') {
            at++;
        }
        final int area = end - at;
        if (area == 0) {
            return false;
        }
        for (int fromRight = slots.size(); fromRight > area; fromRight--) {
            if (Boolean.TRUE.equals(slots.get(fromRight - 1))) {
                return false;
            }
        }
        for (int i = at; i < end; i++) {
            if (!isAsciiHexDigit(text.charAt(i))) {
                return false;
            }
        }
        whole.append(text, at, end);
        pos = end;
        return true;
    }

    /** An exponent as wide as its element: the letter, a sign, and the rest of the width in digits. */
    private boolean readExactExponent(final int width) {
        if (pos + width > text.length() || !isExponentLetter(text.charAt(pos)) || !isSign(text.charAt(pos + 1))) {
            return false;
        }
        final int start = pos + 2;
        for (int i = start; i < pos + width; i++) {
            if (!NumericFormatModel.isAsciiDigit(text.charAt(i))) {
                return false;
            }
        }
        final int written = Integer.parseInt(text.substring(start, pos + width));
        exponent = text.charAt(pos + 1) == '-' ? -written : written;
        pos += width;
        return true;
    }

    /** A sign position: a sign, or a space where the number is not negative. */
    private boolean readSignPosition() {
        if (pos >= text.length()) {
            return false;
        }
        final char ch = text.charAt(pos);
        if (isSign(ch)) {
            negative = ch == '-';
        } else if (ch != ' ') {
            return false;
        }
        pos++;
        return true;
    }

    /** Literal text matched character by character, a space as exactly one space. */
    private boolean readExactLiteral(final String spelling) {
        final String literal = spelling.startsWith("\"") ? spelling.substring(1, spelling.length() - 1) : spelling;
        if (!text.startsWith(literal, pos)) {
            return false;
        }
        pos += literal.length();
        return true;
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
