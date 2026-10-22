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

package dev.frostlake.functions.scalar.datetime;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.WholeNumberArgument;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;

import java.time.LocalDate;
import java.util.List;

public class DateFromParts extends BuiltInFunction {
    /** The days in 400 Gregorian years. */
    private static final long DAYS_PER_ERA = 146_097L;
    /** The days from 0000-03-01 to 1970-01-01. */
    private static final long DAYS_TO_EPOCH = 719_468L;
    /** The first and the last day a date can hold. */
    private static final long MIN_EPOCH_DAY = LocalDate.MIN.toEpochDay();
    private static final long MAX_EPOCH_DAY = LocalDate.MAX.toEpochDay();

    public DateFromParts() { super("DATE_FROM_PARTS", DateTimeType.DATE); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: NULL in, NULL out — any NULL part yields NULL (an NPE escaped before)
        for (int i = 0; i < args.size(); i++) {
            if (i < args.size() && args.get(i) == null) {
                return null;
            }
        }
        return rolledOver(part(args.get(0)), part(args.get(1)), part(args.get(2)));
    }

    /**
     * A part as the whole number it is read as: a fraction rounds half away from zero — month 1.6 is February and day
     * 29.5 the 30th — and a text spelling a number is that number (live-verified). Any other kind is left to the
     * argument-type refusal.
     *
     * @param value the part's value
     * @return the whole number
     */
    static int part(final Object value) {
        // A NaN, an infinity and a number past 32 bits are refused rather than cut (WholeNumberArgument).
        return WholeNumberArgument.int32(value);
    }

    /**
     * A nanosecond part, which is read in 64 bits: 4294967296 nanoseconds are 4.294967296 seconds (live-verified).
     *
     * @param value the part's value
     * @return the whole number
     */
    static long nanosPart(final Object value) {
        return WholeNumberArgument.int64(value, WholeNumberArgument.WIDE);
    }

    /**
     * The date a year, a month and a day name, a part out of its range rolling over into the next one: month 13 is
     * the January after, month 0 the December before, day 0 the last day of the month before and the 30th of
     * February the 1st of March (live-verified). A date past the years one can hold is refused in the sentence the
     * account gives a day count it cannot hold, {@code Date <days> is out of range}.
     *
     * @param year  the year
     * @param month the month, 1 for January
     * @param day   the day of the month, 1 for the first
     * @return the date
     */
    public static LocalDate rolledOver(final int year, final int month, final int day) {
        final long epochDay = epochDay(year, month, day);
        if (epochDay < MIN_EPOCH_DAY || epochDay > MAX_EPOCH_DAY) {
            throw new RuntimeException("Date " + epochDay + " is out of range");
        }
        return LocalDate.ofEpochDay(epochDay);
    }

    /**
     * The days from 1970-01-01 to the date the parts name, the month and the day rolling over, counted in the
     * proleptic Gregorian calendar whatever the year.
     *
     * @param year  the year
     * @param month the month, 1 for January
     * @param day   the day of the month, 1 for the first
     * @return the day count
     */
    static long epochDay(final int year, final int month, final int day) {
        final long months = (long) year * 12 + month - 1;
        final long whole = Math.floorDiv(months, 12);
        final int monthOfYear = (int) Math.floorMod(months, 12) + 1;
        // Counted from March, so a leap day falls at the end of its year.
        final long marchYear = monthOfYear <= 2 ? whole - 1 : whole;
        final long era = Math.floorDiv(marchYear, 400);
        final long yearOfEra = marchYear - era * 400;
        final long dayOfYear = (153L * (monthOfYear > 2 ? monthOfYear - 3 : monthOfYear + 9) + 2) / 5;
        final long dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear;
        return era * DAYS_PER_ERA + dayOfEra - DAYS_TO_EPOCH + day - 1L;
    }

    @Override
    public String signatureName(final int count) {
        return getName();
    }

    @Override
    public boolean refusesSignatureTypes(final List<DataType> types) {
        // A BOOLEAN part is no number, refused at the call while the statement compiles (live-verified).
        for (final DataType type : types) {
            if (type instanceof BooleanType) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
