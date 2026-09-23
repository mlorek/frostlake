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

/**
 * Where a unit-suffixed interval literal becomes a VALUE. {@code INTERVAL '1' DAY} evaluates to a
 * {@link DayTimeIntervalLiteral} and {@code INTERVAL '1-2' YEAR TO MONTH} to a {@link YearMonthIntervalLiteral},
 * which compare and deduplicate by span and apply to a DATE or a timestamp as the typed interval they are: a
 * day-time one moves it by its exact duration — a TIMESTAMP_LTZ plus {@code INTERVAL '1' DAY} across a
 * daylight-saving change is 24 hours later — and a year-month one by calendar months (live-verified). The
 * quoted-string form ({@code INTERVAL '1 day, 2 hours'}) stays the chain of parts date arithmetic applies one
 * by one, a day there being a calendar day.
 */
final class IntervalLiterals {

    /** The digits a plain count may have and still be a {@code long}. */
    private static final int COUNT_DIGITS = 18;

    private IntervalLiterals() {
    }

    /**
     * The value a unit-suffixed literal stands for, its text read now — so a text that does not fit its
     * qualifier is refused when, and only when, a row reaches the literal.
     *
     * @param literal the literal
     * @return a day-time or year-month interval value
     */
    static Object valueOf(final IntervalLiteralSpec literal) {
        final Object read = literal.readValue();
        if (read != null) {
            return read;
        }
        final Object value = literal.isDayTime()
            ? new DayTimeIntervalLiteral(IntervalLiteralText.nanos(literal), literal.getQualifier(),
                literal.getFractionalPrecision())
            : new YearMonthIntervalLiteral(IntervalLiteralText.months(literal), literal.getQualifier());
        literal.keepValue(value);
        return value;
    }

    /**
     * Whether a literal's text is a plain count, spelled exactly as the number prints: no sign, no space and no
     * leading zero.
     *
     * @param text the literal's text
     * @return whether it is one
     */
    static boolean isPlainCount(final String text) {
        if (text.isEmpty() || text.length() > COUNT_DIGITS || text.length() > 1 && text.charAt(0) == '0') {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * The unit a literal's leading field names.
     *
     * @param literal the literal
     * @return its unit
     */
    static IntervalUnit leadingUnit(final IntervalLiteralSpec literal) {
        return IntervalUnit.valueOf(IntervalField.leadingOf(literal.getQualifier()).name());
    }
}
