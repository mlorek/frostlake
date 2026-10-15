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
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public class DateFromParts extends BuiltInFunction {
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
        final BigDecimal exact;
        if (value instanceof String) {
            try {
                exact = new BigDecimal(((String) value).trim());
            } catch (final NumberFormatException notANumber) {
                throw new RuntimeException("Numeric value '" + value + "' is not recognized");
            }
        } else if (value instanceof BigDecimal || value instanceof Double || value instanceof Float) {
            exact = new BigDecimal(value.toString());
        } else {
            return ((Number) value).intValue();
        }
        return exact.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /**
     * The date a year, a month and a day name, a part out of its range rolling over into the next one: month 13 is
     * the January after, month 0 the December before, day 0 the last day of the month before and the 30th of
     * February the 1st of March (live-verified).
     *
     * @param year  the year
     * @param month the month, 1 for January
     * @param day   the day of the month, 1 for the first
     * @return the date
     */
    public static LocalDate rolledOver(final int year, final int month, final int day) {
        return LocalDate.of(year, 1, 1).plusMonths(month - 1L).plusDays(day - 1L);
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
