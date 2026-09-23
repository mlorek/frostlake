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

import dev.frostlake.executor.expressions.IntervalUnit;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DateTimeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * TIME_SLICE(t, n, unit [, 'START'|'END']) — the start (default) or end of the n-unit bucket
 * containing t. Second/minute/hour/day/week buckets align on the epoch (weeks on Monday 1970-01-05
 * per Snowflake); month/quarter/year buckets count calendar months from 1970-01.
 */
public class TimeSlice extends BuiltInFunction {
    public TimeSlice() { super("TIME_SLICE", DateTimeType.TIMESTAMP_NTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null || args.get(2) == null) return null;
        final boolean dateInput = args.get(0) instanceof LocalDate;
        final LocalDateTime t = SharedFunctionHelpers.toLocalDateTime(args.get(0));
        final long n = (long) Double.parseDouble(args.get(1).toString());
        final IntervalUnit unit = DateUnitVocabulary.sliceUnit(args.get(2));
        if (unit == null) {
            throw SharedFunctionHelpers.notADateTimeComponent(args.get(2), "TIME_SLICE");
        }
        final boolean end = args.size() > 3 && args.get(3) != null
            && args.get(3).toString().equalsIgnoreCase("END");
        final LocalDateTime start;
        final LocalDateTime next;
        if (unit == IntervalUnit.MONTH || unit == IntervalUnit.QUARTER || unit == IntervalUnit.YEAR) {
            final long unitMonths = unit == IntervalUnit.MONTH ? 1 : unit == IntervalUnit.QUARTER ? 3 : 12;
            final long months = (t.getYear() - 1970) * 12L + (t.getMonthValue() - 1);
            final long slice = Math.floorDiv(months, n * unitMonths) * n * unitMonths;
            start = LocalDateTime.of(1970, 1, 1, 0, 0).plusMonths(slice);
            next = start.plusMonths(n * unitMonths);
        } else {
            final long unitSeconds;
            long anchor = 0L;
            if (unit == IntervalUnit.SECOND) unitSeconds = 1L;
            else if (unit == IntervalUnit.MINUTE) unitSeconds = 60L;
            else if (unit == IntervalUnit.HOUR) unitSeconds = 3600L;
            else if (unit == IntervalUnit.DAY) unitSeconds = 86400L;
            else {
                unitSeconds = 7L * 86400L;
                anchor = 4L * 86400L;
            }
            final long epoch = t.toEpochSecond(ZoneOffset.UTC);
            final long slice = Math.floorDiv(epoch - anchor, n * unitSeconds) * n * unitSeconds + anchor;
            start = LocalDateTime.ofEpochSecond(slice, 0, ZoneOffset.UTC);
            next = LocalDateTime.ofEpochSecond(slice + n * unitSeconds, 0, ZoneOffset.UTC);
        }
        final LocalDateTime result = end ? next : start;
        return dateInput ? result.toLocalDate() : result;
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 4; }
}
