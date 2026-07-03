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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class DateDiff extends BuiltInFunction {
    public DateDiff() { super("DATEDIFF", NumericType.INTEGER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(1) == null || args.get(2) == null) return null;
        String unit = args.get(0).toString().toUpperCase().replaceAll("S$", "");
        LocalDateTime start = SharedFunctionHelpers.toLocalDateTime(args.get(1));
        LocalDateTime end   = SharedFunctionHelpers.toLocalDateTime(args.get(2));
        switch (unit) {
            case "YEAR": case "Y": case "YYYY": case "YR":
                return (long) (end.getYear() - start.getYear());
            case "QUARTER": case "Q": case "QTR":
                return (long) ((end.getYear() * 12 + end.getMonthValue()) - (start.getYear() * 12 + start.getMonthValue())) / 3;
            case "MONTH": case "MM": case "MON":
                return (long) ((end.getYear() * 12 + end.getMonthValue()) - (start.getYear() * 12 + start.getMonthValue()));
            case "WEEK": case "WK":
                return ChronoUnit.WEEKS.between(start, end);
            case "DAY": case "DD": case "D":
                return ChronoUnit.DAYS.between(start, end);
            case "HOUR": case "H": case "HH":
                return ChronoUnit.HOURS.between(start, end);
            case "MINUTE": case "MIN": case "MI":
                return ChronoUnit.MINUTES.between(start, end);
            case "SECOND": case "SEC": case "S":
                return ChronoUnit.SECONDS.between(start, end);
            case "MILLISECOND": case "MS":
                return ChronoUnit.MILLIS.between(start, end);
            case "MICROSECOND": case "US":
                return ChronoUnit.MICROS.between(start, end);
            default: throw new RuntimeException("Unsupported unit for DATEDIFF: " + unit);
        }
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 3; }
}
