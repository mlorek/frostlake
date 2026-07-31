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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.NumericType;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class MonthsBetween extends BuiltInFunction {
    /** Snowflake's result type is NUMBER(27,6) — a fixed six-decimal scale, not a raw double. */
    private static final int RESULT_SCALE = 6;

    public MonthsBetween() { super("MONTHS_BETWEEN", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final LocalDate d1 = SharedFunctionHelpers.toLocalDate(args.get(0));
        final LocalDate d2 = SharedFunctionHelpers.toLocalDate(args.get(1));
        // Snowflake/Oracle formula: (y1-y2)*12 + (m1-m2) + (day1-day2)/31 — a fixed 31-day-month day
        // fraction. The fraction is dropped (a whole-number result) when the day numbers are equal or
        // both dates fall on the last day of their respective months.
        final double wholeMonths =
            (d1.getYear() - d2.getYear()) * 12.0 + (d1.getMonthValue() - d2.getMonthValue());
        final int day1 = d1.getDayOfMonth();
        final int day2 = d2.getDayOfMonth();
        final boolean bothMonthEnd = day1 == d1.lengthOfMonth() && day2 == d2.lengthOfMonth();
        if (day1 == day2 || bothMonthEnd) {
            return scaled(wholeMonths);
        }
        return scaled(wholeMonths + (day1 - day2) / 31.0);
    }

    /**
     * The result at Snowflake's NUMBER(27,6) scale (live-verified: MONTHS_BETWEEN of 2021-04-15 and
     * 2021-02-28 is 1.580645, not the raw 1.5806451612903225; a whole result renders 1.000000).
     */
    private static BigDecimal scaled(final double months) {
        return BigDecimal.valueOf(months).setScale(RESULT_SCALE, RoundingMode.HALF_UP);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
