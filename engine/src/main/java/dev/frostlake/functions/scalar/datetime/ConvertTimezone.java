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
import dev.frostlake.types.DateTimeType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

public class ConvertTimezone extends BuiltInFunction {
    public ConvertTimezone() { super("CONVERT_TIMEZONE", DateTimeType.TIMESTAMP_NTZ); }

    /**
     * ★ THE TWO ARITIES ARE TWO FUNCTIONS. The THREE-argument form names the source zone itself, so its
     * input is a wall clock and its answer is one — a TIMESTAMP_NTZ, which is what this declares.
     * The TWO-argument form takes an INSTANT and re-expresses it in the named zone, so its answer
     * carries that zone's offset and is a TIMESTAMP_TZ: live gives
     * {@code 2020-01-02 03:00:00.000 +0900} for an Asia/Tokyo conversion, not the session's offset.
     *
     * <p>Frostlake read the value's wall clock and re-labelled it UTC, which converted nothing at all
     * — the two-argument form handed its input straight back.
     *
     * @param args the target zone and the value, or the source zone, the target zone and the value
     * @return the converted timestamp, or null when the value is null
     */
    @Override
    public Object evaluate(final List<Object> args) {
        // A NULL in any argument answers NULL (live-verified).
        if (anyNull(args)) {
            return null;
        }
        if (args.size() == 2) {
            if (args.get(1) == null) return null;
            final ZoneId target = zone(args.get(0));
            return instantOf(args.get(1)).atZone(target).withFixedOffsetZone();
        }
        if (args.get(2) == null) return null;
        final ZoneId src = zone(args.get(0));
        final ZoneId tgt = zone(args.get(1));
        return SharedFunctionHelpers.toLocalDateTime(args.get(2)).atZone(src).withZoneSameInstant(tgt).toLocalDateTime();
    }

    /**
     * The zone an argument names. A zone goes by its own name in the zone database, matched exactly: an
     * offset ({@code +01:00}, {@code UTC+1}, {@code Z}), an abbreviation the database does not keep
     * ({@code PST}), another case ({@code utc}) and a name with a blank beside it are each "Unknown
     * timezone: 'utc'", the argument echoed as it was given (live-verified).
     *
     * @param name the argument
     * @return the zone it names
     */
    private static ZoneId zone(final Object name) {
        final ZoneId known = SessionZone.named(name.toString());
        if (known == null) {
            throw new RuntimeException("Unknown timezone: '" + name + "'");
        }
        return known;
    }

    /**
     * The instant a value names: its own when it carries an offset, and otherwise its wall clock read
     * in the SESSION's zone — live converts a TIMESTAMP_NTZ of 10:00 under America/Los_Angeles to
     * 18:00 Z, so a naive input is a local time and not a UTC one.
     *
     * @param v the value to convert
     * @return the instant it names
     */
    private static Instant instantOf(final Object v) {
        if (v instanceof ZonedDateTime) {
            return ((ZonedDateTime) v).toInstant();
        }
        if (v instanceof OffsetDateTime) {
            return ((OffsetDateTime) v).toInstant();
        }
        return SharedFunctionHelpers.toLocalDateTime(v).atZone(SessionZone.current()).toInstant();
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }
}
