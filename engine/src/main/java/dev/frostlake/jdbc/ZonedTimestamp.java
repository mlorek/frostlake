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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * A moment as the account's driver answers {@code getTimestamp} and {@code getObject} with it: the instant a
 * TIMESTAMP_LTZ or a TIMESTAMP_TZ names — what {@code getTime} counts — printed at the wall clock of the zone it was
 * read in, the session's for a TIMESTAMP_LTZ and the value's own offset for a TIMESTAMP_TZ, and a TIME read as a
 * timestamp at that time of 1970-01-01 in UTC (live-verified). It prints as a {@link Timestamp} prints, the fraction
 * to its last digit: {@code 2024-01-02 10:11:12.345678901}, {@code 2024-07-02 10:11:12.0}.
 */
public final class ZonedTimestamp extends Timestamp {

    private static final long serialVersionUID = 1L;

    /** The zone the moment prints its wall clock in. */
    private final ZoneId zone;

    /**
     * A moment printed in a zone.
     *
     * @param instant the moment
     * @param zone    the zone its wall clock is printed in
     */
    public ZonedTimestamp(final Instant instant, final ZoneId zone) {
        super(instant.toEpochMilli());
        setNanos(instant.getNano());
        this.zone = zone;
    }

    /**
     * The zone the moment prints its wall clock in.
     *
     * @return the zone
     */
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public String toString() {
        final LocalDateTime wall = LocalDateTime.ofInstant(toInstant(), zone);
        return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d:%02d.%s", wall.getYear(), wall.getMonthValue(),
            wall.getDayOfMonth(), wall.getHour(), wall.getMinute(), wall.getSecond(), fraction(wall.getNano()));
    }

    /** A second's fraction as a Timestamp prints it: its digits without trailing zeros, and 0 for none. */
    private static String fraction(final int nanos) {
        if (nanos == 0) {
            return "0";
        }
        String digits = String.format(Locale.ROOT, "%09d", nanos);
        while (digits.endsWith("0")) {
            digits = digits.substring(0, digits.length() - 1);
        }
        return digits;
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
