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
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.values.VariantValue;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A shift DATEADD is planned as, callable by its own name: {@code DATE_ADD<UNITS>TO<KIND>(amount, value)} —
 * {@code DATE_ADDMONTHSTODATE(1, d)} is {@code DATEADD(month, 1, d)} kept a DATE. The value is moved to the kind
 * first, and the shift is DATEADD's own: an amount rounded half away from zero, a month end clamped, a TIME
 * wrapping within its day.
 *
 * <ul>
 *   <li>{@code …TODATE} answers a DATE. A timestamp or a text is read as its date; a sub-day unit shifts the
 *       date's midnight and keeps the day it lands in, so 30 hours is the next day and -1 hour the day
 *       before.</li>
 *   <li>{@code …TOTIMESTAMP} answers the value's timestamp flavour at nine digits, a DATE or a text moving to a
 *       TIMESTAMP_NTZ.</li>
 *   <li>{@code …TOTIME} answers a TIME: a timestamp's time of day, a text read as a time.</li>
 * </ul>
 *
 * <p>The units are the plural words, MILLIS, MICROS and NANOS for the three smallest; a DATE takes the units
 * down to SECONDS, a TIME those from HOURS down. Like the other internal names these are left out of
 * {@code SHOW FUNCTIONS}.
 */
public class PlannedDateAdd extends BuiltInFunction {

    private final IntervalUnit unit;
    private final String kind;
    private final DateAdd shift = new DateAdd();

    /**
     * The shift by one unit into one kind.
     *
     * @param unit the unit shifted by
     * @param kind DATE, TIMESTAMP or TIME
     */
    public PlannedDateAdd(final IntervalUnit unit, final String kind) {
        super("DATE_ADD" + unitName(unit) + "TO" + kind, returnType(kind));
        this.unit = unit;
        this.kind = kind;
    }

    /**
     * The kind the value is moved to.
     *
     * @return DATE, TIMESTAMP or TIME
     */
    public String kind() {
        return kind;
    }

    /**
     * The type a call answers over a value of the given type: the value's own timestamp flavour for a
     * TIMESTAMP_TZ or TIMESTAMP_LTZ shifted as a timestamp, the declared type otherwise.
     *
     * @param valueType the value's type, or null when it has none
     * @return the call's type
     */
    public DataType resultType(final DataType valueType) {
        if (DateDifferenceWidths.TIMESTAMP.equals(kind) && valueType instanceof DateTimeType
                && ((DateTimeType) valueType).hasTimeZone()) {
            return new DateTimeType(valueType.getName().toUpperCase(Locale.ROOT), 9, true);
        }
        return getReturnType();
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object amount = plain(args.get(0));
        final Object value = plain(args.get(1));
        if (amount == null || value == null) {
            return null;
        }
        final Object moved;
        if (DateDifferenceWidths.DATE.equals(kind)) {
            moved = SharedFunctionHelpers.toLocalDate(value);
        } else if (DateDifferenceWidths.TIME.equals(kind)) {
            moved = SharedFunctionHelpers.toLocalTime(value);
        } else {
            moved = value instanceof ZonedDateTime || value instanceof OffsetDateTime ? value
                : SharedFunctionHelpers.toLocalDateTime(value);
        }
        final Object shifted = shift.evaluate(Arrays.asList((Object) unit.name(), amount, moved));
        if (DateDifferenceWidths.DATE.equals(kind) && !(shifted instanceof LocalDate)) {
            return SharedFunctionHelpers.toLocalDate(shifted);
        }
        return shifted;
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }

    /** A unit as a planned shift names it: plural, and MILLIS, MICROS and NANOS for the three smallest. */
    static String unitName(final IntervalUnit unit) {
        switch (unit) {
            case MILLISECOND:
                return "MILLIS";
            case MICROSECOND:
                return "MICROS";
            case NANOSECOND:
                return "NANOS";
            default:
                return unit.name() + "S";
        }
    }

    private static DataType returnType(final String kind) {
        if (DateDifferenceWidths.DATE.equals(kind)) {
            return DateTimeType.DATE;
        }
        return DateDifferenceWidths.TIME.equals(kind) ? DateTimeType.TIME : DateTimeType.TIMESTAMP_NTZ;
    }

    /** A VARIANT holding a text, read as that text; anything else as it is. */
    static Object plain(final Object value) {
        if (value instanceof VariantValue && ((VariantValue) value).node().isTextual()) {
            return ((VariantValue) value).node().asText();
        }
        if (value instanceof VariantValue && ((VariantValue) value).node().isNull()) {
            return null;
        }
        return value;
    }
}
