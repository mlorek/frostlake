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
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * TO_CHAR / TO_VARCHAR of a number under a numeric format model, printed element by element from the
 * model {@link NumericFormatModel} scans, which also refuses a model that is none in the account's words
 * (live-verified throughout).
 *
 * <p><b>Fixed position.</b> Each {@code 9} or {@code 0} is a digit: a leading zero prints as a space under
 * {@code 9} and as itself from the first {@code 0} on, a whole part of zero prints one {@code 0} (a space
 * after {@code B}), and a trailing fraction zero under {@code 9} prints as a space. The value rounds half
 * up to the fraction's digits, and a whole part wider than the model prints every digit as {@code #}. A
 * group separator prints once a digit before it has; {@code .} and {@code D} print the point; {@code %}
 * prints itself after the value is multiplied by 100; a literal prints as written; {@code B}, {@code FX},
 * {@code FM} and {@code _} print nothing. The sign — a space or {@code -}, or {@code +} / {@code -} from a
 * leading {@code S} — floats to just before the first digit printed, or the point when {@code B} blanks
 * the whole part, and a leading {@code $} floats with it; an {@code S} after a digit and an {@code MI} print
 * where they stand instead, and then no sign floats.
 *
 * <p><b>Exponents.</b> {@code EE} to {@code EEEEEEE} normalise the value so its whole part fills the
 * model's whole digits with a first digit of 1 to 9. {@code EE} prints the least exponent without a plus
 * sign, padded to five characters; the longer ones print a sign and that many digits less two, {@code #}
 * when the exponent needs more. The letter's case is the element's.
 *
 * <p><b>Hexadecimal.</b> {@code X} is a hexadecimal digit, upper- or lower-case as written; a {@code 0}
 * among them prints zeros in the case of the {@code X} after it. No sign floats: a negative value prints
 * its two's complement across the digits unless an {@code S} or an {@code MI} prints its sign.
 *
 * <p><b>Text minimal.</b> {@code TM9} prints the value's own digits, {@code TME} them in scientific
 * notation ({@code 1.2345E4}), and {@code TM} a FLOAT in whichever is shorter and any other number as
 * {@code TM9} does — never with padding.
 *
 * <p><b>Fill mode.</b> {@code FM} removes every space a numeric element printed; a literal keeps its own.
 */
public final class NumericOutputFormat {

    /** The width {@code EE}'s exponent is padded to: its longest spelling, E-200. */
    private static final int VARIABLE_EXPONENT_WIDTH = 5;

    private static final BigInteger SIXTEEN = BigInteger.valueOf(16);

    private NumericOutputFormat() {
    }

    /**
     * A number printed under an output model.
     *
     * @param number the value: an exact number, or a FLOAT's double
     * @param model the model as written
     * @return the text
     */
    public static String format(final Number number, final String model) {
        if (model.isEmpty()) {
            return "";
        }
        final NumericFormatAlternative scanned = NumericFormatModel.output(model);
        if (scanned.textMinimal()) {
            return textMinimal(number, scanned);
        }
        return scanned.hexadecimal() ? hexadecimal(number, scanned) : positional(number, scanned);
    }

    private static String positional(final Number number, final NumericFormatAlternative model) {
        final List<NumericFormatElement> kinds = model.kinds();
        final List<String> spellings = model.spellings();
        if (firstDigitElement(kinds) == kinds.size()) {
            // A model of punctuation and literals alone prints them as written, and no sign.
            final NumericOutputLine line = new NumericOutputLine();
            for (int i = 0; i < kinds.size(); i++) {
                addFixed(line, kinds.get(i) == NumericFormatElement.GROUP ? NumericFormatElement.LITERAL : kinds.get(i),
                    spellings.get(i), false, false, false);
            }
            return line.text(false);
        }
        final int point = kinds.indexOf(NumericFormatElement.DECIMAL);
        int wholePositions = 0;
        int fractionPositions = 0;
        for (int i = 0; i < kinds.size(); i++) {
            if (isDigit(kinds.get(i))) {
                if (point < 0 || i < point) {
                    wholePositions++;
                } else {
                    fractionPositions++;
                }
            }
        }
        BigDecimal value = decimalOf(number);
        if (value != null && kinds.contains(NumericFormatElement.PERCENT)) {
            value = value.movePointRight(2);
        }
        int exponent = 0;
        if (value != null && kinds.contains(NumericFormatElement.EXPONENT) && value.signum() != 0) {
            final int wholeDigits = Math.max(wholePositions, 1);
            exponent = value.precision() - value.scale() - wholeDigits;
            BigDecimal mantissa = value.movePointLeft(exponent).setScale(fractionPositions, RoundingMode.HALF_UP);
            if (mantissa.abs().compareTo(BigDecimal.ONE.movePointRight(wholeDigits)) >= 0) {
                exponent++;
                mantissa = value.movePointLeft(exponent).setScale(fractionPositions, RoundingMode.HALF_UP);
            }
            value = mantissa;
        }
        final BigDecimal rounded = value == null ? null : value.setScale(fractionPositions, RoundingMode.HALF_UP);
        final boolean negative = rounded != null && rounded.signum() < 0;
        String whole = "0";
        String fraction = "";
        boolean overflow = rounded == null;
        if (rounded != null) {
            final BigDecimal magnitude = rounded.abs();
            whole = magnitude.setScale(0, RoundingMode.DOWN).toPlainString();
            final String plain = magnitude.toPlainString();
            fraction = fractionPositions == 0 ? "" : plain.substring(plain.indexOf('.') + 1);
            overflow = !"0".equals(whole) && whole.length() > wholePositions;
        }
        final boolean[] blankFraction = blankTrailingZeros(kinds, point, fraction, fractionPositions);
        final boolean zeroWhole = "0".equals(whole);
        final boolean blankZero = kinds.contains(NumericFormatElement.BLANK);
        final int firstDigit = firstDigitElement(kinds);
        final int signAt = kinds.indexOf(NumericFormatElement.SIGN);
        final boolean leadingSign = signAt >= 0 && signAt < firstDigit;
        final boolean floatingSign = leadingSign || signAt < 0 && !kinds.contains(NumericFormatElement.MINUS);
        final int dollarAt = kinds.indexOf(NumericFormatElement.DOLLAR);
        final boolean leadingDollar = dollarAt >= 0 && dollarAt < firstDigit;

        final NumericOutputLine line = new NumericOutputLine();
        int wholeIndex = 0;
        int fractionIndex = 0;
        boolean zeroFilling = false;
        boolean digitShown = false;
        int anchor = -1;
        int afterLastDigit = 0;
        for (int i = 0; i < kinds.size(); i++) {
            final NumericFormatElement kind = kinds.get(i);
            final String spelling = spellings.get(i);
            if (isDigit(kind)) {
                final String digit;
                if (point < 0 || i < point) {
                    zeroFilling = zeroFilling || kind == NumericFormatElement.ZERO;
                    digit = overflow ? "#" : wholeDigit(whole, wholeIndex, wholePositions, zeroWhole, zeroFilling, blankZero);
                    wholeIndex++;
                    digitShown = digitShown || !" ".equals(digit);
                } else {
                    digit = overflow ? "#" : blankFraction[fractionIndex] ? " "
                        : String.valueOf(fraction.charAt(fractionIndex));
                    fractionIndex++;
                }
                if (anchor < 0 && !" ".equals(digit)) {
                    anchor = line.size();
                }
                line.add(digit);
                afterLastDigit = line.size();
            } else if (kind == NumericFormatElement.DECIMAL) {
                if (anchor < 0) {
                    anchor = line.size();
                }
                line.add(".");
            } else if (kind == NumericFormatElement.GROUP) {
                line.add(digitShown ? "," : " ");
            } else if (kind == NumericFormatElement.EXPONENT) {
                line.add(exponentText(spelling, exponent, rounded == null));
            } else {
                addFixed(line, kind, spelling, negative, leadingSign, leadingDollar);
            }
        }
        final String floating = (floatingSign ? negative ? "-" : leadingSign ? "+" : " " : "")
            + (leadingDollar ? "$" : "");
        if (!floating.isEmpty()) {
            line.insert(anchor >= 0 ? anchor : afterLastDigit, floating);
        }
        return line.text(kinds.contains(NumericFormatElement.FILL_MODE));
    }

    /** A whole digit position's character: the value's digit, or a leading zero as a space or a zero. */
    private static String wholeDigit(final String whole, final int index, final int positions, final boolean zeroWhole,
                                     final boolean zeroFilling, final boolean blankZero) {
        if (zeroWhole && index == positions - 1) {
            return blankZero && !zeroFilling ? " " : "0";
        }
        final int offset = positions - whole.length();
        if (!zeroWhole && index >= offset) {
            return String.valueOf(whole.charAt(index - offset));
        }
        return zeroFilling ? "0" : " ";
    }

    /** Which fraction digits print as spaces: the trailing zeros under a {@code 9}, up to the first other. */
    private static boolean[] blankTrailingZeros(final List<NumericFormatElement> kinds, final int point,
                                                final String fraction, final int positions) {
        final boolean[] blank = new boolean[positions];
        if (point < 0 || fraction.length() < positions) {
            return blank;
        }
        int position = positions - 1;
        for (int i = kinds.size() - 1; i > point && position >= 0; i--) {
            final NumericFormatElement kind = kinds.get(i);
            if (!isDigit(kind)) {
                continue;
            }
            if (kind != NumericFormatElement.DIGIT || fraction.charAt(position) != '0') {
                break;
            }
            blank[position] = true;
            position--;
        }
        return blank;
    }

    private static String exponentText(final String spelling, final int exponent, final boolean unknown) {
        final char letter = spelling.charAt(0);
        if (spelling.length() == 2) {
            final StringBuilder variable = new StringBuilder().append(letter).append(unknown ? "#" : Integer.toString(exponent));
            while (variable.length() < VARIABLE_EXPONENT_WIDTH) {
                variable.append(' ');
            }
            return variable.toString();
        }
        final int digits = spelling.length() - 2;
        final String magnitude = Integer.toString(Math.abs(exponent));
        final StringBuilder fixed = new StringBuilder().append(letter).append(exponent < 0 ? '-' : '+');
        if (unknown || magnitude.length() > digits) {
            for (int i = 0; i < digits; i++) {
                fixed.append('#');
            }
            return fixed.toString();
        }
        for (int i = magnitude.length(); i < digits; i++) {
            fixed.append('0');
        }
        return fixed.append(magnitude).toString();
    }

    private static String hexadecimal(final Number number, final NumericFormatAlternative model) {
        final List<NumericFormatElement> kinds = model.kinds();
        final List<String> spellings = model.spellings();
        int positions = 0;
        for (final NumericFormatElement kind : kinds) {
            if (isDigit(kind)) {
                positions++;
            }
        }
        final int signAt = kinds.indexOf(NumericFormatElement.SIGN);
        final boolean explicitSign = signAt >= 0 || kinds.contains(NumericFormatElement.MINUS);
        final int firstDigit = firstDigitElement(kinds);
        final boolean leadingSign = signAt >= 0 && signAt < firstDigit;
        final int dollarAt = kinds.indexOf(NumericFormatElement.DOLLAR);
        final boolean leadingDollar = dollarAt >= 0 && dollarAt < firstDigit;
        final BigDecimal value = decimalOf(number);
        boolean negative = false;
        String digits = null;
        if (value != null) {
            BigInteger shown = value.setScale(0, RoundingMode.HALF_UP).toBigInteger();
            negative = shown.signum() < 0;
            shown = negative && !explicitSign ? shown.add(SIXTEEN.pow(positions)) : shown.abs();
            final String text = shown.toString(16);
            digits = shown.signum() < 0 || text.length() > positions ? null : text;
        }
        final NumericOutputLine line = new NumericOutputLine();
        int position = 0;
        boolean zeroFilling = false;
        int anchor = -1;
        int afterLastDigit = 0;
        for (int i = 0; i < kinds.size(); i++) {
            final NumericFormatElement kind = kinds.get(i);
            if (isDigit(kind)) {
                zeroFilling = zeroFilling || kind == NumericFormatElement.ZERO;
                String digit;
                if (digits == null) {
                    digit = "#";
                } else if (position >= positions - digits.length()) {
                    digit = String.valueOf(digits.charAt(position - (positions - digits.length())));
                } else {
                    digit = zeroFilling ? "0" : " ";
                }
                digit = lowerCaseAt(kinds, spellings, i) ? digit : digit.toUpperCase(Locale.ROOT);
                if (anchor < 0 && !" ".equals(digit)) {
                    anchor = line.size();
                }
                line.add(digit);
                afterLastDigit = line.size();
                position++;
            } else {
                addFixed(line, kind, spellings.get(i), negative, leadingSign, leadingDollar);
            }
        }
        final String floating = (leadingSign ? negative ? "-" : "+" : "") + (leadingDollar ? "$" : "");
        if (!floating.isEmpty()) {
            line.insert(anchor >= 0 ? anchor : afterLastDigit, floating);
        }
        return line.text(kinds.contains(NumericFormatElement.FILL_MODE));
    }

    /** Whether a hexadecimal position prints lower case: an {@code x}, or a {@code 0} before one. */
    private static boolean lowerCaseAt(final List<NumericFormatElement> kinds, final List<String> spellings,
                                       final int index) {
        for (int i = index; i < kinds.size(); i++) {
            if (kinds.get(i) == NumericFormatElement.HEX) {
                return "x".equals(spellings.get(i));
            }
        }
        return false;
    }

    private static String textMinimal(final Number number, final NumericFormatAlternative model) {
        final List<NumericFormatElement> kinds = model.kinds();
        final List<String> spellings = model.spellings();
        BigDecimal value = decimalOf(number);
        if (value == null) {
            return number.toString();
        }
        if (kinds.contains(NumericFormatElement.PERCENT)) {
            value = value.movePointRight(2);
        }
        final boolean negative = value.signum() < 0;
        final boolean explicitSign = kinds.contains(NumericFormatElement.SIGN)
            || kinds.contains(NumericFormatElement.MINUS);
        final BigDecimal magnitude = value.abs();
        final NumericOutputLine line = new NumericOutputLine();
        for (int i = 0; i < kinds.size(); i++) {
            final NumericFormatElement kind = kinds.get(i);
            final String spelling = spellings.get(i);
            if (kind == NumericFormatElement.TEXT_MINIMAL_POSITIONAL) {
                line.add((negative && !explicitSign ? "-" : "") + positionalText(magnitude));
            } else if (kind == NumericFormatElement.TEXT_MINIMAL_SCIENTIFIC) {
                line.add((negative && !explicitSign ? "-" : "") + scientificText(magnitude, spelling.charAt(2)));
            } else if (kind == NumericFormatElement.TEXT_MINIMAL) {
                final String positional = positionalText(magnitude);
                final String scientific = scientificText(magnitude, 'E');
                final boolean shorter = (number instanceof Double || number instanceof Float)
                    && scientific.length() < positional.length();
                line.add((negative && !explicitSign ? "-" : "") + (shorter ? scientific : positional));
            } else if (kind == NumericFormatElement.MINUS) {
                line.add(negative ? "-" : "");
            } else {
                addFixed(line, kind, spelling, negative, false, false);
            }
        }
        return line.text(false);
    }

    /** The value's own digits, without trailing fraction zeros. */
    private static String positionalText(final BigDecimal magnitude) {
        return magnitude.signum() == 0 ? "0" : magnitude.stripTrailingZeros().toPlainString();
    }

    /** The value's own digits in scientific notation: one whole digit, then the exponent without a plus sign. */
    private static String scientificText(final BigDecimal magnitude, final char letter) {
        if (magnitude.signum() == 0) {
            return "0" + letter + "0";
        }
        final BigDecimal stripped = magnitude.stripTrailingZeros();
        final String digits = stripped.unscaledValue().toString();
        final int exponent = digits.length() - 1 - stripped.scale();
        return digits.charAt(0) + (digits.length() > 1 ? "." + digits.substring(1) : "") + letter + exponent;
    }

    /** The elements that print where they stand whatever the digits are. */
    private static void addFixed(final NumericOutputLine line, final NumericFormatElement kind, final String spelling,
                                 final boolean negative, final boolean leadingSign, final boolean leadingDollar) {
        if (kind == NumericFormatElement.LITERAL) {
            line.addLiteral(spelling.startsWith("\"") ? spelling.substring(1, spelling.length() - 1) : spelling);
        } else if (kind == NumericFormatElement.DOLLAR && !leadingDollar) {
            line.add("$");
        } else if (kind == NumericFormatElement.SIGN && !leadingSign) {
            line.add(negative ? "-" : "+");
        } else if (kind == NumericFormatElement.MINUS) {
            line.add(negative ? "-" : " ");
        } else if (kind == NumericFormatElement.PERCENT) {
            line.add("%");
        } else if (kind == NumericFormatElement.DECIMAL) {
            line.add(".");
        }
    }

    private static int firstDigitElement(final List<NumericFormatElement> kinds) {
        for (int i = 0; i < kinds.size(); i++) {
            if (isDigit(kinds.get(i))) {
                return i;
            }
        }
        return kinds.size();
    }

    private static boolean isDigit(final NumericFormatElement kind) {
        return kind == NumericFormatElement.DIGIT || kind == NumericFormatElement.ZERO
            || kind == NumericFormatElement.HEX;
    }

    /** The value as an exact decimal, or null for a FLOAT that is not a finite number. */
    private static BigDecimal decimalOf(final Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        if (number instanceof Double || number instanceof Float) {
            final double d = number.doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? null : new BigDecimal(Double.toString(d));
        }
        return new BigDecimal(number.toString());
    }
}
