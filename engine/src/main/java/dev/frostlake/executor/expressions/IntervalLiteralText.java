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

import dev.frostlake.types.IntervalField;
import dev.frostlake.types.IntervalQualifier;

import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A unit-suffixed interval literal's text read by its qualifier's fields, refused in the account's words
 * when it does not fit them. The text is read when a row reaches the literal — over an empty table, or in a
 * branch no row takes, a text that does not fit is never refused (live-verified). Surrounding spaces are
 * ignored and a sign may lead; then each qualifier takes one shape:
 *
 * <pre>
 *   DAY / HOUR / MINUTE     D                 YEAR / MONTH      Y
 *   SECOND                  S[.F]             YEAR TO MONTH     Y-M
 *   DAY TO HOUR             D H               HOUR TO MINUTE    H:M
 *   DAY TO MINUTE           D H:M             HOUR TO SECOND    H:M:S[.F]
 *   DAY TO SECOND           D H:M:S[.F]       MINUTE TO SECOND  M:S[.F]
 * </pre>
 *
 * <p>A text of another shape is "is invalid, expected format is …"; a leading field of more DIGITS than its
 * precision, a later field of more than two, or a fraction of more digits than the fractional precision is
 * the precision sentence — {@code '099'} is too wide for a DAY(2) — and a fraction at a fractional precision
 * of zero is the shape sentence again. An hour past 23, a minute or second past 59 or a month past 11 after
 * the leading field is the range sentence. The two families word the precision sentence differently: a
 * day-time one reads "is '…' invalid", a year-month one "'…' is invalid".
 */
final class IntervalLiteralText {

    private static final int FRACTION_DIGITS = 9;
    private static final int FIELD_DIGITS = 2;
    private static final int HOURS_PER_DAY = 24;
    private static final int MINUTES_PER_HOUR = 60;
    private static final int MONTHS_PER_YEAR = 12;

    private static final Pattern ONE = Pattern.compile("(\\d+)");
    private static final Pattern TWO_SPACED = Pattern.compile("(\\d+) (\\d+)");
    private static final Pattern THREE_SPACED = Pattern.compile("(\\d+) (\\d+):(\\d+)");
    private static final Pattern FOUR_SPACED = Pattern.compile("(\\d+) (\\d+):(\\d+):(\\d+)(?:\\.(\\d+))?");
    private static final Pattern FOUR_SPACED_WHOLE = Pattern.compile("(\\d+) (\\d+):(\\d+):(\\d+)");
    private static final Pattern TWO_COLON = Pattern.compile("(\\d+):(\\d+)");
    private static final Pattern THREE_COLON = Pattern.compile("(\\d+):(\\d+):(\\d+)(?:\\.(\\d+))?");
    private static final Pattern THREE_COLON_WHOLE = Pattern.compile("(\\d+):(\\d+):(\\d+)");
    private static final Pattern TWO_COLON_FRACTION = Pattern.compile("(\\d+):(\\d+)(?:\\.(\\d+))?");
    private static final Pattern SECONDS = Pattern.compile("(\\d+)(?:\\.(\\d+))?");
    private static final Pattern YEARS_MONTHS = Pattern.compile("(\\d+)-(\\d+)");

    private static final String DAY_TIME_FORMATS = "expected format is '<sign>D(p) HH24:MM:SS.F(fsp)' for subtype "
        + "DAY TO SECOND, '<sign>D(p) HH24:MM' for subtype DAY TO MINUTE, '<sign>D(p) HH24' for subtype DAY TO HOUR, "
        + "'<sign>D(p)' for subtype DAY, '<sign>H(p):MM:SS.F(fsp)' for subtype HOUR TO SECOND, '<sign>H(p):MM' for "
        + "subtype HOUR TO MINUTE, '<sign>H(p)' for subtype HOUR, '<sign>M(p):SS.F(fsp)' for subtype MINUTE TO "
        + "SECOND, '<sign>M(p)' for subtype MINUTE, or '<sign>S(p).F(fsp)' for subtype SECOND";
    private static final String YEAR_MONTH_FORMATS = "expected format is '<sign>Y(p)-MM' for subtype YEAR TO MONTH, "
        + "'<sign>Y(p)' for subtype YEAR, or '<sign>M(p)' for subtype MONTH";

    private IntervalLiteralText() {
    }

    /**
     * A day-time literal's span.
     *
     * @param literal a day-time literal
     * @return its signed nanoseconds
     */
    static BigInteger nanos(final IntervalLiteralSpec literal) {
        final String text = literal.getText();
        final String trimmed = text.trim();
        final boolean negative = trimmed.startsWith("-");
        final String digits = negative || trimmed.startsWith("+") ? trimmed.substring(1) : trimmed;
        final boolean fraction = literal.getFractionalPrecision() > 0;
        final IntervalQualifier qualifier = literal.getQualifier();
        final Matcher parts;
        switch (qualifier) {
            case DAY:
            case HOUR:
            case MINUTE:
                parts = ONE.matcher(digits);
                break;
            case SECOND:
                parts = (fraction ? SECONDS : ONE).matcher(digits);
                break;
            case DAY_TO_HOUR:
                parts = TWO_SPACED.matcher(digits);
                break;
            case DAY_TO_MINUTE:
                parts = THREE_SPACED.matcher(digits);
                break;
            case DAY_TO_SECOND:
                parts = (fraction ? FOUR_SPACED : FOUR_SPACED_WHOLE).matcher(digits);
                break;
            case HOUR_TO_MINUTE:
                parts = TWO_COLON.matcher(digits);
                break;
            case HOUR_TO_SECOND:
                parts = (fraction ? THREE_COLON : THREE_COLON_WHOLE).matcher(digits);
                break;
            default:
                parts = (fraction ? TWO_COLON_FRACTION : TWO_COLON).matcher(digits);
                break;
        }
        if (!parts.matches()) {
            throw new RuntimeException("Day-Time Interval '" + text + "' is invalid, " + DAY_TIME_FORMATS);
        }
        final IntervalField lead = IntervalField.leadingOf(qualifier);
        final IntervalField trail = IntervalField.trailingOf(qualifier);
        final int fields = trail.ordinal() - lead.ordinal() + 1;
        final String written = parts.groupCount() > fields ? parts.group(fields + 1) : null;
        if (parts.group(1).length() > literal.getLeadingPrecision()
                || written != null && written.length() > literal.getFractionalPrecision()) {
            throw new RuntimeException("Day-Time Interval is '" + text + "' invalid, value of leading or fractional"
                + " second field is greater than specified precision/fsp");
        }
        for (int i = 2; i <= fields; i++) {
            if (parts.group(i).length() > FIELD_DIGITS) {
                throw new RuntimeException("Day-Time Interval is '" + text + "' invalid, value of leading or"
                    + " fractional second field is greater than specified precision/fsp");
            }
        }
        BigInteger nanos = BigInteger.ZERO;
        for (int i = 1; i <= fields; i++) {
            final IntervalField field = IntervalField.values()[lead.ordinal() + i - 1];
            final long amount = Long.parseLong(parts.group(i));
            if (i > 1 && amount >= (field == IntervalField.HOUR ? HOURS_PER_DAY : MINUTES_PER_HOUR)) {
                throw new RuntimeException("Day-Time Interval '" + text + "' is invalid, required that 0 <= HOUR <= 23,"
                    + " 0 <= MINUTE <= 59, 0 <= SECOND <= 59");
            }
            nanos = nanos.add(new BigInteger(parts.group(i)).multiply(BigInteger.valueOf(field.storageUnits())));
        }
        if (written != null) {
            nanos = nanos.add(new BigInteger((written + "000000000").substring(0, FRACTION_DIGITS)));
        }
        return negative ? nanos.negate() : nanos;
    }

    /**
     * A year-month literal's span.
     *
     * @param literal a year-month literal
     * @return its signed months
     */
    static long months(final IntervalLiteralSpec literal) {
        final String text = literal.getText();
        final String trimmed = text.trim();
        final boolean negative = trimmed.startsWith("-");
        final String digits = negative || trimmed.startsWith("+") ? trimmed.substring(1) : trimmed;
        final boolean range = literal.getQualifier() == IntervalQualifier.YEAR_TO_MONTH;
        final Matcher parts = (range ? YEARS_MONTHS : ONE).matcher(digits);
        if (!parts.matches()) {
            throw new RuntimeException("Year-Month Interval '" + text + "' is invalid, " + YEAR_MONTH_FORMATS);
        }
        if (parts.group(1).length() > literal.getLeadingPrecision()
                || range && parts.group(2).length() > FIELD_DIGITS) {
            throw new RuntimeException("Year-Month Interval '" + text + "' is invalid, value of leading field is"
                + " greater than specified precision");
        }
        final long leading = Long.parseLong(parts.group(1));
        final long months;
        if (range) {
            final long month = Long.parseLong(parts.group(2));
            if (month >= MONTHS_PER_YEAR) {
                throw new RuntimeException("Year-Month Interval '" + text + "' is invalid, required that"
                    + " 0 <= MONTH <= 11");
            }
            months = leading * MONTHS_PER_YEAR + month;
        } else {
            months = literal.getQualifier() == IntervalQualifier.YEAR ? leading * MONTHS_PER_YEAR : leading;
        }
        return negative ? -months : months;
    }
}
