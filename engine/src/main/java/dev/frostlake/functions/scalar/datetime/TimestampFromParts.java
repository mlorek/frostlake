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

import dev.frostlake.executor.SessionZone;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.SkippedWallClock;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.TimestampFlavours;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

public class TimestampFromParts extends BuiltInFunction {
    public TimestampFromParts() { this("TIMESTAMP_FROM_PARTS"); }

    /**
     * The same construction under one of its flavoured names, which is what its declared type follows.
     *
     * @param name the registered name
     */
    public TimestampFromParts(final String name) {
        super(name, TimestampFlavours.forFunctionName(name));
        this.keepsWrittenOffset = name.contains("_TZ_") || name.startsWith("TIMESTAMPTZ");
        this.zoned = keepsWrittenOffset || name.contains("LTZ");
    }

    /** The fewest arguments of the signature that takes the parts one by one. */
    private static final int PARTS = 6;

    /** Whether the name builds a TIMESTAMP_LTZ or a TIMESTAMP_TZ, a value placed in a zone. */
    private final boolean zoned;
    /** Whether the name builds a TIMESTAMP_TZ, which keeps an offset of its own and may name its zone. */
    private final boolean keepsWrittenOffset;

    @Override
    public Object evaluate(final List<Object> args) {
        // Snowflake: NULL in, NULL out — any NULL part yields NULL (an NPE escaped before)
        for (int i = 0; i < args.size(); i++) {
            if (i < args.size() && args.get(i) == null) {
                return null;
            }
        }
        if (args.size() == 2) {
            return joined(args.get(0), args.get(1));
        }
        // Each part is read as a date's parts are: a fraction rounds half away from zero, a text is its number.
        final int year   = DateFromParts.part(args.get(0));
        final int month  = DateFromParts.part(args.get(1));
        final int day    = DateFromParts.part(args.get(2));
        final int hour   = DateFromParts.part(args.get(3));
        final int minute = DateFromParts.part(args.get(4));
        final int second = DateFromParts.part(args.get(5));
        final long nanos = args.size() > 6 && args.get(6) != null ? DateFromParts.nanosPart(args.get(6)) : 0L;
        // A part out of its range rolls over into the next one, as a date's parts do.
        final LocalDateTime wall = DateFromParts.rolledOver(year, month, day).atStartOfDay()
            .plusHours(hour).plusMinutes(minute).plusSeconds(second).plusNanos(nanos);
        if (!zoned) {
            return wall;
        }
        // The parts are a wall clock in the session's zone — or, for a TIMESTAMP_TZ, in the zone named last — and
        // one the zone's clocks skip moves forward, as a TIMESTAMP_NTZ converted to a zoned type does
        // (live-verified: TIMESTAMP_LTZ_FROM_PARTS(2024, 3, 10, 2, 30, 0) is 03:30 -0700 in Los Angeles).
        final ZoneId zone = args.size() > 7 ? ZoneId.of(String.valueOf(args.get(7))) : SessionZone.current();
        final ZonedDateTime placed = wall.atZone(zone);
        if (!keepsWrittenOffset) {
            return placed.toOffsetDateTime();
        }
        // A TIMESTAMP_TZ shows that instant at the offset the same clock written as text would take.
        final ZoneOffset skipped = SkippedWallClock.offset(wall, zone);
        return placed.toInstant().atOffset(skipped != null ? skipped : placed.getOffset()).toZonedDateTime();
    }

    /** A date and a time put together, the two-argument form of the names that build a TIMESTAMP_NTZ. */
    private static LocalDateTime joined(final Object date, final Object time) {
        final LocalDate day = SharedFunctionHelpers.toLocalDateTime(date).toLocalDate();
        final LocalTime clock = time instanceof LocalTime ? (LocalTime) time : LocalTime.parse(String.valueOf(time));
        return day.atTime(clock);
    }

    @Override
    public int getMinArgCount() { return zoned ? PARTS : 2; }

    @Override
    public String signatureName(final int count) {
        if (zoned) {
            return null;
        }
        return count == 2 ? "TIMESTAMP_NTZ_FROM_DATE_TIME" : "TIMESTAMP_NTZ_FROM_PARTS";
    }

    @Override
    public int signatureMinimum(final int count) {
        // Between a date with a time and the six parts the call is short of the parts.
        return !zoned && count > 2 && count < PARTS ? PARTS : -1;
    }

    @Override
    public boolean refusesSignatureTypes(final List<DataType> types) {
        // A date with a time, in that order; a text is read as either.
        return !zoned && types.size() == 2
            && (types.get(0) instanceof NumericType || isA(types.get(0), "TIME")
                || types.get(1) instanceof NumericType
                || types.get(1) instanceof DateTimeType && !isA(types.get(1), "TIME"));
    }

    private static boolean isA(final DataType type, final String name) {
        return type instanceof DateTimeType && name.equals(type.getName());
    }

    @Override
    public int getMaxArgCount() { return keepsWrittenOffset ? 8 : 7; }
}
