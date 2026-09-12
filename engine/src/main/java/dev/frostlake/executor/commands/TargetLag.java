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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;

/**
 * How Snowflake stores a dynamic table's TARGET_LAG: the input is canonicalized, not echoed.
 * Live-verified — {@code '1 minutes'} reads back {@code 1 minute}, {@code '90 seconds'} reads
 * back {@code 1 minute, 30 seconds}, {@code '2 hours'} stays {@code 2 hours}, and any lag under
 * sixty seconds is refused outright, quoting the input as written.
 */
public final class TargetLag {

    private static final long MINIMUM_SECONDS = 60;

    private TargetLag() {
    }

    /**
     * The canonical spelling of a lag: total seconds decomposed into days, hours, minutes and
     * seconds, each unit singular when its count is one, joined with ", ". Input that does not
     * parse as {@code <count> <unit>} is kept as written.
     */
    /** Whether a lag is written as {@code <count> <unit>}, the only form an ALTER takes (live-verified). */
    public static boolean parses(final String input) {
        final String trimmed = input.trim();
        final int space = trimmed.indexOf(' ');
        if (space < 0) {
            return false;
        }
        try {
            Long.parseLong(trimmed.substring(0, space).trim());
        } catch (final NumberFormatException notANumber) {
            return false;
        }
        return unitSeconds(trimmed.substring(space + 1).trim()) >= 0;
    }

    public static String canonicalize(final String input) {
        final String trimmed = input.trim();
        final int space = trimmed.indexOf(' ');
        if (space < 0) {
            return input;
        }
        final long count;
        try {
            count = Long.parseLong(trimmed.substring(0, space).trim());
        } catch (final NumberFormatException notANumber) {
            return input;
        }
        final long unitSeconds = unitSeconds(trimmed.substring(space + 1).trim());
        if (unitSeconds < 0) {
            return input;
        }
        final long totalSeconds = count * unitSeconds;
        if (totalSeconds < MINIMUM_SECONDS) {
            throw new RuntimeException(SqlCompilationError.of("Invalid TARGET_LAG value '" + trimmed
                + "'. Dynamic Tables do not support lag values under " + MINIMUM_SECONDS + " second(s)."));
        }

        final StringBuilder canonical = new StringBuilder();
        long remaining = totalSeconds;
        remaining = appendUnit(canonical, remaining, 86400, "day");
        remaining = appendUnit(canonical, remaining, 3600, "hour");
        remaining = appendUnit(canonical, remaining, 60, "minute");
        appendUnit(canonical, remaining, 1, "second");
        return canonical.toString();
    }

    private static long appendUnit(final StringBuilder canonical, final long remaining,
            final long unitSeconds, final String unit) {
        final long count = remaining / unitSeconds;
        if (count == 0) {
            return remaining;
        }
        if (canonical.length() > 0) {
            canonical.append(", ");
        }
        canonical.append(count).append(' ').append(unit);
        if (count != 1) {
            canonical.append('s');
        }
        return remaining - count * unitSeconds;
    }

    /** Seconds per unit, accepting singular and plural spellings; -1 for an unknown unit. */
    private static long unitSeconds(final String unit) {
        switch (unit.toLowerCase()) {
            case "second":
            case "seconds":
                return 1;
            case "minute":
            case "minutes":
                return 60;
            case "hour":
            case "hours":
                return 3600;
            case "day":
            case "days":
                return 86400;
            default:
                return -1;
        }
    }
}
