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
import dev.frostlake.functions.scalar.SkippedWallClock;
import dev.frostlake.types.TimestampFlavours;

import java.time.LocalDateTime;
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
        final int year   = ((Number) args.get(0)).intValue();
        final int month  = ((Number) args.get(1)).intValue();
        final int day    = ((Number) args.get(2)).intValue();
        final int hour   = ((Number) args.get(3)).intValue();
        final int minute = ((Number) args.get(4)).intValue();
        final int second = ((Number) args.get(5)).intValue();
        final int nanos  = args.size() > 6 && args.get(6) != null ? ((Number) args.get(6)).intValue() : 0;
        final LocalDateTime wall = LocalDateTime.of(year, month, day, hour, minute, second, nanos);
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

    @Override
    public int getMinArgCount() { return 6; }
    @Override
    public int getMaxArgCount() { return keepsWrittenOffset ? 8 : 7; }
}
