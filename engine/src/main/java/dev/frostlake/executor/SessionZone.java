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

    private SessionZone() {
    }

    /** The session's zone, or UTC when nothing is pinned. */
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

    private static ZoneId resolve(final Object zoneName) {
        if (zoneName == null) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zoneName.toString().trim());
        } catch (final RuntimeException unusable) {
            return ZoneOffset.UTC;
        }
    }
}
