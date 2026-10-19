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

package dev.frostlake.functions.scalar;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.zone.ZoneOffsetTransition;

/**
 * The offset a wall clock written as TEXT is read with when the session zone's clocks skip it. The digits are kept
 * as written and take one of the two offsets the change stands between, never moved forward the way a
 * TIMESTAMP_NTZ converted to a zoned type is (all live-verified, zone by zone):
 *
 * <pre>
 *   America/Los_Angeles   2024-03-10 02:00:00   -0800   the gap's first second keeps the offset before
 *                         2024-03-10 02:00:01   -0700   and from its second second on, the offset after
 *                         2024-03-10 02:30:00   -0700   so the instant is 09:30 UTC, 01:30 -0800 as an LTZ
 *   Atlantic/Azores       2024-03-31 00:30:00   Z       the same west of UTC, whatever the offset after is
 *   Europe/London         2024-03-31 01:30:00   Z       at UTC and east of it, the offset before throughout
 *   Asia/Tehran           2022-03-22 00:30:00   +0330
 *   Australia/Sydney      2024-10-06 02:30:00   +1000
 * </pre>
 *
 * <p>Both halves are one reading: the clock is looked up as if it were UTC, moved by the offset found there, and
 * looked up again, the instant of the change itself counting as before it.
 */
public final class SkippedWallClock {

    private SkippedWallClock() {
    }

    /**
     * @param wall the wall clock as written
     * @param zone the session's zone
     * @return the offset the clock is read with, or null when the zone's clocks do not skip it
     */
    public static ZoneOffset offset(final LocalDateTime wall, final ZoneId zone) {
        final ZoneOffsetTransition change = zone.getRules().getTransition(wall);
        if (change == null || !change.isGap()) {
            return null;
        }
        final long changesAt = change.getInstant().getEpochSecond();
        final long asUtc = wall.truncatedTo(ChronoUnit.SECONDS).toEpochSecond(ZoneOffset.UTC);
        final ZoneOffset first = asUtc < changesAt ? change.getOffsetBefore() : change.getOffsetAfter();
        return asUtc - first.getTotalSeconds() <= changesAt ? change.getOffsetBefore() : change.getOffsetAfter();
    }
}
