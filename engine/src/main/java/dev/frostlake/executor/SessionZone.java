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

package dev.frostlake.executor;

import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * The zone the session's TIMEZONE parameter names, reachable from the value layer.
 *
 * <p>A TIMESTAMP_LTZ is an INSTANT, and the wall clock it shows is that instant read in the SESSION's
 * zone — so the same stored value renders {@code 2020-01-01 10:00:00 -0800} under
 * America/Los_Angeles and {@code 2020-01-01 18:00:00 Z} under UTC. Reading and rendering one
 * therefore needs the session's zone, and both happen deep inside static helpers that have no session
 * to ask.
 *
 * <p>Held in a ThreadLocal and pinned for the statement, exactly as {@link StatementClock} pins the
 * statement's instant and for the same reason: one engine serves many sessions, so this must not be a
 * process-wide setting. The default is UTC, which is what the engine used everywhere before the
 * parameter was read at all.
 */
public final class SessionZone {

    private static final ThreadLocal<ZoneId> PINNED = new ThreadLocal<ZoneId>();

    /**
     * The zone names the account knows that the JDK's zone list leaves out, each beside the zone it means: the
     * three fixed-offset abbreviations, the old name of Asia/Taipei, the zone database's placeholder and its two
     * signed names of GMT.
     */
    private static final String[][] LEGACY_ZONES = {{"EST", "-05:00"}, {"MST", "-07:00"}, {"HST", "-10:00"},
        {"ROC", "Asia/Taipei"}, {"Factory", "UTC"}, {"GMT+0", "Etc/GMT+0"}, {"GMT-0", "Etc/GMT-0"}};
    /** A family of names the JDK keeps that the account does not. */
    private static final String UNKNOWN_FAMILY = "SystemV/";

    private SessionZone() {
    }

    /** The session's zone, or UTC, the zone the account sets ({@link AccountParameters}), when nothing is pinned. */
    public static ZoneId current() {
        final ZoneId pinned = PINNED.get();
        return pinned != null ? pinned : ZoneOffset.UTC;
    }

    /**
     * Pin this statement's zone, returning what was pinned before so the caller can restore it. An
     * unusable or absent name leaves UTC rather than raising: the parameter is validated where it is
     * SET, and a rendering path is the wrong place to discover a bad one.
     */
    public static ZoneId pin(final Object zoneName) {
        final ZoneId previous = PINNED.get();
        PINNED.set(resolve(zoneName));
        return previous;
    }

    /** Put back whatever {@link #pin} displaced; a null restores the unpinned state. */
    public static void restore(final ZoneId previous) {
        if (previous == null) {
            PINNED.remove();
        } else {
            PINNED.set(previous);
        }
    }

    /**
     * Whether the account knows a time zone by this name (live-verified). The name is a zone's own, matched
     * exactly: {@code UTC}, {@code Europe/Warsaw}, {@code Etc/GMT+2}, {@code US/Pacific} and the old names the
     * zone database still links are known - {@code GMT+0} and {@code GMT-0} among them - while {@code utc}, an
     * offset ({@code +02:00}, {@code GMT+2}, {@code Z}) and an abbreviation outside the four the database keeps
     * ({@code PST}) are not.
     *
     * @param name the name as the parameter would hold it
     * @return whether a TIMEZONE parameter takes it
     */
    public static boolean knows(final String name) {
        if (name == null) {
            return false;
        }
        for (final String[] legacy : LEGACY_ZONES) {
            if (legacy[0].equals(name)) {
                return true;
            }
        }
        return !name.startsWith(UNKNOWN_FAMILY) && ZoneId.getAvailableZoneIds().contains(name);
    }

    /**
     * The zone the account knows by this name.
     *
     * @param name a zone's name, matched exactly as {@link #knows} matches it
     * @return the zone, or null when the account knows none by that name
     */
    public static ZoneId named(final String name) {
        if (!knows(name)) {
            return null;
        }
        for (final String[] legacy : LEGACY_ZONES) {
            if (legacy[0].equals(name)) {
                return ZoneId.of(legacy[1]);
            }
        }
        return ZoneId.of(name);
    }

    private static ZoneId resolve(final Object zoneName) {
        if (zoneName == null) {
            return ZoneOffset.UTC;
        }
        final String name = zoneName.toString().trim();
        try {
            for (final String[] legacy : LEGACY_ZONES) {
                if (legacy[0].equals(name)) {
                    return ZoneId.of(legacy[1]);
                }
            }
            return ZoneId.of(name);
        } catch (final RuntimeException unusable) {
            return ZoneOffset.UTC;
        }
    }
}
