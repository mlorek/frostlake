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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A numeric INPUT format model — the format argument of TO_NUMBER / TO_DECIMAL / TO_NUMERIC, TO_DOUBLE and
 * TO_DECFLOAT over a text, and of their TRY_ twins — scanned into its alternatives and checked the way the
 * account checks it, before the text is read. A model that is not one is refused naming the whole model
 * and the TARGET in the account's own word — FIXED for TO_NUMBER, REAL for TO_DOUBLE, DECFLOAT for
 * TO_DECFLOAT — and the TRY_ twins answer NULL instead (all live-verified):
 *
 * <pre>
 *   TO_NUMBER('1', '9e9')      invalid numeric format keyword: 'e9'          letters, digits, _ $ % in a run
 *   TO_NUMBER('1', '9+')       invalid character in the format string: '+'   a tab shown as '\011'
 *   TO_NUMBER('1', '9"')       missing closing " in the literal: '9"'
 *   TO_NUMBER('1', '9SS')      format element occurs more than once: 'S'
 *   TO_NUMBER('1', '9D9.9')    format element conflicts with preceding element(s): '.'
 *   TO_NUMBER('1', '9EE.9')    digit position after an exponent format element: '9EE.9'
 *   TO_NUMBER('1', '9X')       cannot mix hexadecimal and decimal format elements: '9X'
 *   TO_NUMBER('1', 'TM99')     cannot mix TM and digit-based numeric format elements: 'TM99'
 *   TO_NUMBER('1', '$')        no digit format elements in a numeric format: '$'
 *   TO_NUMBER('1', ',')        missing required input format element(s)      '' and '|9' alike
 *   TO_DOUBLE('ff', 'XX')      missing required input format element(s)      hexadecimal is FIXED's alone
 * </pre>
 *
 * <p>{@code |} separates alternatives, each checked in turn and the first that reads the text answering;
 * one trailing {@code |} is ignored. {@code AUTO} alone is an alternative reading any number, and TM9 takes
 * {@code (digits[,group])}. A text the model cannot read is refused by {@link #readOrRefuse} with the
 * text exactly as written. Scanning a model is data handling, not SQL: the model is a string VALUE.
 */
public final class NumericFormatModel {

    /** The target TO_NUMBER, TO_DECIMAL and TO_NUMERIC convert to, in the refusal's own word. */
    public static final String FIXED = "FIXED";
    /** TO_DOUBLE's target, in the refusal's own word. */
    public static final String REAL = "REAL";
    /** TO_DECFLOAT's target, in the refusal's own word. */
    public static final String DECFLOAT = "DECFLOAT";

    /** A model a text is read with, in the refusal's own word. */
    private static final String INPUT = "input";
    /** A model a number is printed with, TO_CHAR's, in the refusal's own word. */
    private static final String OUTPUT = "output";

    /** The longest exponent element, seven E's; an eighth starts another element. */
    private static final int LONGEST_EXPONENT = 7;
    /** The characters a model may carry as themselves; any other that is no element is refused. */
    private static final String LITERAL_CHARACTERS = " -/:();=";
    /** The most digits a TM9 group size is read with; a longer one cannot be a group. */
    private static final int GROUP_SIZE_DIGITS = 9;

    private final List<NumericFormatAlternative> alternatives;

    private NumericFormatModel(final List<NumericFormatAlternative> alternatives) {
        this.alternatives = alternatives;
    }

    /**
     * Scan and check a model.
     *
     * @param format the model as written
     * @param target the target the refusal names: {@link #FIXED}, {@link #REAL} or {@link #DECFLOAT}
     * @return the model
     */
    public static NumericFormatModel parse(final String format, final String target) {
        final List<String> pieces = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= format.length(); i++) {
            if (i == format.length() || format.charAt(i) == '|') {
                pieces.add(format.substring(start, i));
                start = i + 1;
            }
        }
        // One trailing '|' is ignored ('9|' reads as '9'), a second is an empty alternative ('9||' is refused).
        if (pieces.size() > 1 && pieces.get(pieces.size() - 1).isEmpty()) {
            pieces.remove(pieces.size() - 1);
        }
        final List<NumericFormatAlternative> alternatives = new ArrayList<>();
        for (final String piece : pieces) {
            alternatives.add(scan(format, piece, target, INPUT));
        }
        return new NumericFormatModel(alternatives);
    }

    /**
     * An OUTPUT model — TO_CHAR's and TO_VARCHAR's over a number — scanned whole and checked as an input
     * model is, its refusals naming the output model (live-verified): {@code TO_CHAR(1, '9Q')} is "Bad
     * output format model '9Q' for FIXED: invalid numeric format keyword: 'Q'". A model of punctuation and
     * literals alone is no refusal there — {@code TO_CHAR(1, ',')} prints the comma.
     *
     * @param format the model as written
     * @return its elements
     */
    static NumericFormatAlternative output(final String format) {
        return scan(format, format, FIXED, OUTPUT);
    }

    /**
     * A text read under a model, refused as the account refuses it when no alternative reads it — the
     * text echoed exactly as written, its spaces included: {@code TO_NUMBER(' 123 ', '99')} is
     * "Can't parse ' 123 ' as number with format '99'".
     *
     * @param text the text
     * @param format the model as written
     * @param target the target a refusal of the model names
     * @return the value the text spells
     */
    public static BigDecimal readOrRefuse(final String text, final String format, final String target) {
        final BigDecimal value = parse(format, target).read(text);
        if (value == null) {
            throw new RuntimeException("Can't parse '" + text + "' as number with format '" + format + "'");
        }
        return value;
    }

    /**
     * The value the first alternative that reads the text gives.
     *
     * @param text the text
     * @return the value, or null when no alternative reads it
     */
    public BigDecimal read(final String text) {
        for (final NumericFormatAlternative alternative : alternatives) {
            final BigDecimal value = alternative.read(text);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static NumericFormatAlternative scan(final String format, final String alternative, final String target,
                                                 final String direction) {
        if (alternative.isEmpty()) {
            throw refusal(format, target, direction, "missing required input format element(s)");
        }
        if (alternative.regionMatches(true, 0, "AUTO", 0, 4)) {
            if (alternative.length() == 4) {
                return NumericFormatAlternative.automatic();
            }
            throw refusal(format, target, direction, "bad AUTO format specification");
        }
        final List<NumericFormatElement> kinds = new ArrayList<>();
        final List<String> spellings = new ArrayList<>();
        final Map<String, String> firstOfFamily = new HashMap<>();
        final int length = alternative.length();
        boolean exponentSeen = false;
        int groupSize = 0;
        int at = 0;
        while (at < length) {
            final char c = alternative.charAt(at);
            final char upper = asciiUpper(c);
            final char next = at + 1 < length ? asciiUpper(alternative.charAt(at + 1)) : '\0';
            NumericFormatElement kind;
            int end = at + 1;
            if (c == '"') {
                final int close = alternative.indexOf('"', at + 1);
                if (close < 0) {
                    throw refusal(format, target, direction, "missing closing \" in the literal: '" + alternative + "'");
                }
                kind = NumericFormatElement.LITERAL;
                end = close + 1;
            } else if (upper == 'E' && next == 'E') {
                end = at;
                while (end < length && end - at < LONGEST_EXPONENT && asciiUpper(alternative.charAt(end)) == 'E') {
                    end++;
                }
                kind = NumericFormatElement.EXPONENT;
            } else if (upper == 'T' && next == 'M') {
                final char third = at + 2 < length ? asciiUpper(alternative.charAt(at + 2)) : '\0';
                if (third == '9') {
                    kind = NumericFormatElement.TEXT_MINIMAL_POSITIONAL;
                    end = at + 3;
                    if (end < length && alternative.charAt(end) == '(') {
                        final int[] parameters = textMinimalParameters(format, target, direction, alternative, end);
                        end = parameters[0];
                        groupSize = parameters[1];
                    }
                } else if (third == 'E') {
                    kind = NumericFormatElement.TEXT_MINIMAL_SCIENTIFIC;
                    end = at + 3;
                } else {
                    kind = NumericFormatElement.TEXT_MINIMAL;
                    end = at + 2;
                }
            } else if (upper == 'F' && (next == 'M' || next == 'X')) {
                kind = next == 'M' ? NumericFormatElement.FILL_MODE : NumericFormatElement.EXACT_MODE;
                end = at + 2;
            } else if (upper == 'M' && next == 'I') {
                kind = NumericFormatElement.MINUS;
                end = at + 2;
            } else {
                kind = singleCharacterElement(upper);
            }
            if (kind == null) {
                if (isAsciiLetter(c) || isAsciiDigit(c)) {
                    int runEnd = at + 1;
                    while (runEnd < length && isKeywordCharacter(alternative.charAt(runEnd))) {
                        runEnd++;
                    }
                    throw refusal(format, target, direction, "invalid numeric format keyword: '"
                        + alternative.substring(at, runEnd) + "'");
                }
                if (LITERAL_CHARACTERS.indexOf(c) < 0) {
                    throw refusal(format, target, direction, "invalid character in the format string: '"
                        + shownCharacter(alternative, at) + "'");
                }
                kind = NumericFormatElement.LITERAL;
            }
            final String spelling = alternative.substring(at, end);
            if (exponentSeen && placesDigit(kind)) {
                throw refusal(format, target, direction, "digit position after an exponent format element: '"
                    + alternative + "'");
            }
            requireFirstOfFamily(format, target, direction, firstOfFamily, kind, spelling);
            exponentSeen = exponentSeen || kind == NumericFormatElement.EXPONENT;
            final int last = kinds.size() - 1;
            if (kind == NumericFormatElement.LITERAL && c != '"' && last >= 0
                    && kinds.get(last) == NumericFormatElement.LITERAL && !spellings.get(last).startsWith("\"")) {
                // Adjacent literal characters are one literal, so '9  9' reads its two spaces as one run.
                spellings.set(last, spellings.get(last) + spelling);
            } else {
                kinds.add(kind);
                spellings.add(spelling);
            }
            at = end;
        }
        requireCoherent(format, target, direction, alternative, kinds, spellings);
        return new NumericFormatAlternative(kinds, spellings, groupSize);
    }

    /** The element a single character is, or null where it is none. */
    private static NumericFormatElement singleCharacterElement(final char upper) {
        switch (upper) {
            case '9':
                return NumericFormatElement.DIGIT;
            case '0':
                return NumericFormatElement.ZERO;
            case 'X':
                return NumericFormatElement.HEX;
            case ',':
            case 'G':
                return NumericFormatElement.GROUP;
            case '.':
            case 'D':
                return NumericFormatElement.DECIMAL;
            case '$':
                return NumericFormatElement.DOLLAR;
            case '%':
                return NumericFormatElement.PERCENT;
            case 'B':
                return NumericFormatElement.BLANK;
            case 'S':
                return NumericFormatElement.SIGN;
            case '_':
                return NumericFormatElement.OPTIONAL_SPACE;
            default:
                return null;
        }
    }

    /**
     * TM9's parameters, {@code (digits[,group])}, read from the opening parenthesis: a number or ALL, an
     * optional comma and a number, then the closing parenthesis — each fault in the account's own words,
     * which echo nothing: {@code TM9(6, 3)} is "TM9 requires numeric value after comma".
     *
     * @return the index past the closing parenthesis, then the group size (0 when none is given)
     */
    private static int[] textMinimalParameters(final String format, final String target, final String direction,
                                               final String alternative, final int open) {
        final int length = alternative.length();
        int at = open + 1;
        final int digitsStart = at;
        while (at < length && isAsciiDigit(alternative.charAt(at))) {
            at++;
        }
        if (at == digitsStart) {
            if (!alternative.regionMatches(true, at, "ALL", 0, 3)) {
                throw refusal(format, target, direction, "TM9 requires parameter (number or ALL) after '('");
            }
            at += 3;
        }
        int groupSize = 0;
        if (at < length && alternative.charAt(at) == ',') {
            at++;
            final int groupStart = at;
            while (at < length && isAsciiDigit(alternative.charAt(at))) {
                at++;
            }
            if (at == groupStart) {
                throw refusal(format, target, direction, "TM9 requires numeric value after comma");
            }
            groupSize = at - groupStart > GROUP_SIZE_DIGITS ? Integer.MAX_VALUE
                : Integer.parseInt(alternative.substring(groupStart, at));
        }
        if (at >= length || alternative.charAt(at) != ')') {
            throw refusal(format, target, direction, "TM9 missing closing ')'");
        }
        return new int[] {at + 1, groupSize};
    }

    /**
     * An element that may appear once is refused the second time: the same element "occurs more than once",
     * another of its family — the other decimal point, the other sign, another exponent width, another TM
     * form — "conflicts with preceding element(s)"; both echo the element as written.
     */
    private static void requireFirstOfFamily(final String format, final String target, final String direction,
                                             final Map<String, String> firstOfFamily,
                                             final NumericFormatElement kind, final String spelling) {
        final String family = familyOf(kind);
        if (family == null) {
            return;
        }
        final String identity = kind == NumericFormatElement.EXPONENT ? "E" + spelling.length()
            : kind == NumericFormatElement.DECIMAL ? spelling.toUpperCase(Locale.ROOT) : kind.name();
        final String first = firstOfFamily.putIfAbsent(family, identity);
        if (first == null) {
            return;
        }
        throw refusal(format, target, direction, (first.equals(identity) ? "format element occurs more than once: '"
            : "format element conflicts with preceding element(s): '") + spelling + "'");
    }

    /** The family an element that may appear only once belongs to, or null for a repeatable one. */
    private static String familyOf(final NumericFormatElement kind) {
        switch (kind) {
            case DECIMAL:
                return "DECIMAL";
            case SIGN:
            case MINUS:
                return "SIGN";
            case EXPONENT:
                return "EXPONENT";
            case TEXT_MINIMAL:
            case TEXT_MINIMAL_POSITIONAL:
            case TEXT_MINIMAL_SCIENTIFIC:
                return "TM";
            case DOLLAR:
                return "DOLLAR";
            case PERCENT:
                return "PERCENT";
            case BLANK:
                return "BLANK";
            default:
                return null;
        }
    }

    /**
     * The checks on a whole alternative once it is scanned: the families that do not mix, and an
     * alternative that places no digit. Punctuation and literals place nothing — {@code ','} alone is
     * "missing required input format element(s)" where {@code 'G'} alone is "no digit format elements" —
     * and a hexadecimal model is FIXED's alone, so {@code 'XX'} for REAL places nothing either.
     */
    private static void requireCoherent(final String format, final String target, final String direction,
                                        final String alternative, final List<NumericFormatElement> kinds,
                                        final List<String> spellings) {
        final boolean hexadecimal = kinds.contains(NumericFormatElement.HEX);
        final boolean textMinimal = containsTextMinimal(kinds);
        if (hexadecimal && (kinds.contains(NumericFormatElement.DIGIT) || textMinimal)) {
            throw refusal(format, target, direction, "cannot mix hexadecimal and decimal format elements: '" + alternative + "'");
        }
        if (hexadecimal && kinds.contains(NumericFormatElement.EXPONENT)) {
            throw refusal(format, target, direction, "hexadecimal exponents are not supported: '" + alternative + "'");
        }
        if (hexadecimal && kinds.contains(NumericFormatElement.GROUP)) {
            throw refusal(format, target, direction, "hexadecimal digit group separators are not supported: '"
                + alternative + "'");
        }
        if (hexadecimal && zeroAfterPoint(kinds)) {
            throw refusal(format, target, direction, "hexadecimal fractions are not supported: '" + alternative + "'");
        }
        if (textMinimal && (kinds.contains(NumericFormatElement.DIGIT) || kinds.contains(NumericFormatElement.ZERO))) {
            throw refusal(format, target, direction, "cannot mix TM and digit-based numeric format elements: '"
                + alternative + "'");
        }
        final boolean hexadecimalTarget = FIXED.equals(target);
        int elements = 0;
        int digits = 0;
        for (int i = 0; i < kinds.size(); i++) {
            final NumericFormatElement kind = kinds.get(i);
            final boolean punctuation = (kind == NumericFormatElement.GROUP || kind == NumericFormatElement.DECIMAL)
                && !isAsciiLetter(spellings.get(i).charAt(0));
            final boolean foreignHexadecimal = hexadecimal && !hexadecimalTarget
                && (kind == NumericFormatElement.HEX || kind == NumericFormatElement.ZERO);
            if (kind == NumericFormatElement.LITERAL || punctuation || foreignHexadecimal) {
                continue;
            }
            elements++;
            if (kind == NumericFormatElement.DIGIT || kind == NumericFormatElement.ZERO
                    || kind == NumericFormatElement.HEX || isTextMinimal(kind)) {
                digits++;
            }
        }
        if (elements == 0) {
            if (OUTPUT.equals(direction)) {
                return;
            }
            throw refusal(format, target, direction, "missing required input format element(s)");
        }
        if (digits == 0) {
            throw refusal(format, target, direction, "no digit format elements in a numeric format: '" + alternative + "'");
        }
    }

    /** Whether a {@code 0} follows the decimal point — a hexadecimal fraction, which '0.0X' writes. */
    private static boolean zeroAfterPoint(final List<NumericFormatElement> kinds) {
        boolean point = false;
        for (final NumericFormatElement kind : kinds) {
            if (kind == NumericFormatElement.DECIMAL) {
                point = true;
            } else if (point && kind == NumericFormatElement.ZERO) {
                return true;
            }
        }
        return false;
    }

    static boolean containsTextMinimal(final List<NumericFormatElement> kinds) {
        for (final NumericFormatElement kind : kinds) {
            if (isTextMinimal(kind)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTextMinimal(final NumericFormatElement kind) {
        return kind == NumericFormatElement.TEXT_MINIMAL || kind == NumericFormatElement.TEXT_MINIMAL_POSITIONAL
            || kind == NumericFormatElement.TEXT_MINIMAL_SCIENTIFIC;
    }

    /** The elements that place a digit, which may not follow an exponent. */
    private static boolean placesDigit(final NumericFormatElement kind) {
        return kind == NumericFormatElement.DIGIT || kind == NumericFormatElement.ZERO
            || kind == NumericFormatElement.HEX || kind == NumericFormatElement.GROUP
            || kind == NumericFormatElement.DECIMAL;
    }

    /** A character as the refusal shows it: itself when printable ASCII, else its first UTF-8 byte in octal. */
    private static String shownCharacter(final String alternative, final int index) {
        final int codePoint = alternative.codePointAt(index);
        if (codePoint > ' ' && codePoint < 0x7F) {
            return String.valueOf((char) codePoint);
        }
        final byte first = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)[0];
        return String.format(Locale.ROOT, "\\%03o", first & 0xFF);
    }

    private static boolean isKeywordCharacter(final char c) {
        return isAsciiLetter(c) || isAsciiDigit(c) || c == '_' || c == '$' || c == '%';
    }

    private static boolean isAsciiLetter(final char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    static boolean isAsciiDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    /** An ASCII letter upper-cased; every other character as it is. */
    private static char asciiUpper(final char c) {
        return c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c;
    }

    private static RuntimeException refusal(final String format, final String target, final String direction,
                                            final String detail) {
        return new RuntimeException("Bad " + direction + " format model '" + format + "' for " + target + ": " + detail);
    }
}
