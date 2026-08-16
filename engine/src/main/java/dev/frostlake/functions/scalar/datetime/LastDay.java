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
import dev.frostlake.types.DateTimeType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public class LastDay extends BuiltInFunction {
    public LastDay() { super("LAST_DAY", DateTimeType.DATE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final LocalDate d = SharedFunctionHelpers.toLocalDate(args.get(0));
        final String part = args.size() > 1 && args.get(1) != null
            ? args.get(1).toString().trim().toUpperCase() : "MONTH";
        switch (part) {
            case "YEAR": case "Y": case "YY": case "YYY": case "YYYY": case "YR": case "YRS":
            case "YEARS":
                return d.with(TemporalAdjusters.lastDayOfYear());
            case "QUARTER": case "Q": case "QTR": case "QTRS": case "QUARTERS":
                return lastDayOfQuarter(d);
            case "WEEK": case "W": case "WK": case "WEEKS": case "WEEKOFYEAR": case "WOY": case "WY":
                // Default WEEK_START (weeks start on Monday) → the week's last day is Sunday.
                return d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            case "MONTH": case "MM": case "MON": case "MONS": case "MONTHS":
            default:
                return d.with(TemporalAdjusters.lastDayOfMonth());
        }
    }

    /** Last calendar day of the quarter that contains {@code d} (Mar 31 / Jun 30 / Sep 30 / Dec 31). */
    private static LocalDate lastDayOfQuarter(final LocalDate d) {
        final int lastMonthOfQuarter = ((d.getMonthValue() - 1) / 3) * 3 + 3;
        return LocalDate.of(d.getYear(), lastMonthOfQuarter, 1).with(TemporalAdjusters.lastDayOfMonth());
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 2; }
}
