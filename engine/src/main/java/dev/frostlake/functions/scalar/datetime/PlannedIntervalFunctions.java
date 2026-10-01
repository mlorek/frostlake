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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Every internal name DATEADD and DATEDIFF are planned as that a call may also use directly — the account answers
 * exactly these and refuses the rest as unknown functions:
 *
 * <pre>
 *   DATE_ADD&lt;UNITS&gt;TODATE        YEARS QUARTERS MONTHS WEEKS DAYS HOURS MINUTES SECONDS
 *   DATE_ADD&lt;UNITS&gt;TOTIMESTAMP   the same, and MILLIS MICROS NANOS
 *   DATE_ADD&lt;UNITS&gt;TOTIME        HOURS MINUTES SECONDS MILLIS MICROS NANOS
 *   DATE_DIFFDATEIN&lt;UNITS&gt;       YEARS QUARTERS MONTHS WEEKS DAYS HOURS MINUTES SECONDS MILLISECONDS MICROSECONDS NANOSECONDS
 *   DATE_DIFFTIMESTAMPIN&lt;UNITS&gt;  the same
 *   DATE_DIFFTIMEIN&lt;UNITS&gt;       HOURS MINUTES SECONDS MILLISECONDS MICROSECONDS NANOSECONDS
 * </pre>
 */
public final class PlannedIntervalFunctions {

    /** Every unit. */
    private static final List<IntervalUnit> ALL_UNITS = Arrays.asList(IntervalUnit.values());

    /** The units a DATE is shifted by: down to seconds. */
    private static final List<IntervalUnit> DATE_SHIFT_UNITS = Arrays.asList(IntervalUnit.YEAR, IntervalUnit.QUARTER,
        IntervalUnit.MONTH, IntervalUnit.WEEK, IntervalUnit.DAY, IntervalUnit.HOUR, IntervalUnit.MINUTE,
        IntervalUnit.SECOND);

    /** The units a TIME is shifted or measured by: from hours down. */
    private static final List<IntervalUnit> TIME_UNITS = Arrays.asList(IntervalUnit.HOUR, IntervalUnit.MINUTE,
        IntervalUnit.SECOND, IntervalUnit.MILLISECOND, IntervalUnit.MICROSECOND, IntervalUnit.NANOSECOND);

    private PlannedIntervalFunctions() {
    }

    /**
     * One function per internal name.
     *
     * @return the shifts, then the differences
     */
    public static List<BuiltInFunction> all() {
        final List<BuiltInFunction> functions = new ArrayList<BuiltInFunction>();
        for (final IntervalUnit unit : DATE_SHIFT_UNITS) {
            functions.add(new PlannedDateAdd(unit, DateDifferenceWidths.DATE));
        }
        for (final IntervalUnit unit : ALL_UNITS) {
            functions.add(new PlannedDateAdd(unit, DateDifferenceWidths.TIMESTAMP));
        }
        for (final IntervalUnit unit : TIME_UNITS) {
            functions.add(new PlannedDateAdd(unit, DateDifferenceWidths.TIME));
        }
        for (final IntervalUnit unit : ALL_UNITS) {
            functions.add(new PlannedDateDiff(DateDifferenceWidths.DATE, unit));
        }
        for (final IntervalUnit unit : ALL_UNITS) {
            functions.add(new PlannedDateDiff(DateDifferenceWidths.TIMESTAMP, unit));
        }
        for (final IntervalUnit unit : TIME_UNITS) {
            functions.add(new PlannedDateDiff(DateDifferenceWidths.TIME, unit));
        }
        return functions;
    }
}
