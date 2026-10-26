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

package dev.frostlake.jdbc;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/**
 * A moment as the account's driver answers {@code getDate} with it over a TIMESTAMP_LTZ or a TIMESTAMP_TZ: the
 * instant itself — what {@code getTime} counts, its time of day included — printed as the date it falls on in the
 * zone it was read in, the session's for a TIMESTAMP_LTZ and the value's own offset for a TIMESTAMP_TZ
 * (live-verified).
 */
public final class ZonedDate extends Date {

    private static final long serialVersionUID = 1L;

    /** The zone the moment prints its date in. */
    private final ZoneId zone;

    /**
     * A moment printed as its date in a zone.
     *
     * @param instant the moment
     * @param zone    the zone its date is printed in
     */
    public ZonedDate(final Instant instant, final ZoneId zone) {
        super(instant.toEpochMilli());
        this.zone = zone;
    }

    /**
     * The zone the moment prints its date in.
     *
     * @return the zone
     */
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public String toString() {
        final LocalDate day = Instant.ofEpochMilli(getTime()).atZone(zone).toLocalDate();
        return String.format(Locale.ROOT, "%04d-%02d-%02d", day.getYear(), day.getMonthValue(), day.getDayOfMonth());
    }

    @Override
    public boolean equals(final Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
