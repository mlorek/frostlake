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
package com.snowflake.snowpark_java;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.TimeZone;

/**
 * A TIMESTAMP_LTZ or TIMESTAMP_TZ cell as the driver under Snowpark hands it to a handler (live-verified). The
 * account runs the handler's JVM in the zone the session's TIMEZONE names, a zone the engine's JVM does not share, so
 * the cell carries it: a TIMESTAMP_LTZ prints its instant in that zone and a TIMESTAMP_TZ its own wall clock, and
 * {@link #toLocalDateTime()} reads the instant in the session's zone in both, as the JVM's own zone reads it on the
 * account. The zone is read by the JVM's legacy rules, as a timestamp's own text reads it. The account's class is the
 * driver's own timestamp with a zone, a class the engine does not name.
 */
final class SessionTimestamp extends Timestamp {

    private static final long serialVersionUID = 1L;

    /** The wall clock the cell prints. */
    private final LocalDateTime printed;

    /** The cell's instant read in the session's zone. */
    private final LocalDateTime local;

    private SessionTimestamp(final long epochSecond, final int nanos, final LocalDateTime printed,
                             final LocalDateTime local) {
        super(epochSecond * 1000L);
        setNanos(nanos);
        this.printed = printed;
        this.local = local;
    }

    /** The wall clock an instant shows in a zone, by the JVM's legacy rules. */
    private static LocalDateTime clockOf(final long epochSecond, final int nanos, final ZoneId zone) {
        final int offset = TimeZone.getTimeZone(zone).getOffset(epochSecond * 1000L) / 1000;
        return LocalDateTime.ofEpochSecond(epochSecond, nanos, ZoneOffset.ofTotalSeconds(offset));
    }

    /** A TIMESTAMP_LTZ's cell: its instant, printed in the session's zone. */
    static SessionTimestamp ofInstant(final Instant instant, final ZoneId zone) {
        final LocalDateTime local = clockOf(instant.getEpochSecond(), instant.getNano(), zone);
        return new SessionTimestamp(instant.getEpochSecond(), instant.getNano(), local, local);
    }

    /** A TIMESTAMP_TZ's cell: its instant, printed in its own wall clock. */
    static SessionTimestamp ofOwnClock(final Instant instant, final LocalDateTime own, final ZoneId zone) {
        return new SessionTimestamp(instant.getEpochSecond(), instant.getNano(), own,
            clockOf(instant.getEpochSecond(), instant.getNano(), zone));
    }

    @Override
    public LocalDateTime toLocalDateTime() {
        return local;
    }

    /** As a timestamp prints: {@code 2024-01-02 10:20:30.5}, the fraction's trailing zeros dropped, one digit kept. */
    @Override
    public String toString() {
        final String digits = String.format(Locale.ROOT, "%09d", printed.getNano());
        int end = digits.length();
        while (end > 1 && digits.charAt(end - 1) == '0') {
            end--;
        }
        return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d:%02d.", printed.getYear(),
            printed.getMonthValue(), printed.getDayOfMonth(), printed.getHour(), printed.getMinute(),
            printed.getSecond()) + digits.substring(0, end);
    }
}
