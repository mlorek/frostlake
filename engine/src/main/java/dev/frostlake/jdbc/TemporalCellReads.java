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
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * How the temporal getters read a cell, as the account's driver reads it: by the column's family and, where the cell
 * names no zone of its own, in the zone the session's TIMEZONE named when the statement ran (all live-verified, with
 * the JVM's zone and the session's apart).
 *
 * <pre>
 *   column   getTimestamp            getDate                 getTime                 getObject
 *   DATE     midnight, JVM zone      midnight, JVM zone      refused                 the session's midnight *
 *   TIME     1970-01-01 in UTC †     refused                 1970-01-01, JVM zone    as getTime
 *   NTZ      wall clock, JVM zone    wall clock, JVM zone    wall clock read in the  as getTimestamp
 *                                    (its time of day kept)  session's zone †
 *   LTZ, TZ  the moment †            the moment †            the moment †            as getTimestamp
 * </pre>
 *
 * <p>† A moment printed at the wall clock of its zone ({@link ZonedTimestamp}, {@link ZonedDate}, {@link
 * ZonedTime}): the session's for a TIMESTAMP_LTZ and for a TIMESTAMP_NTZ read as a time, the value's own offset for a
 * TIMESTAMP_TZ, UTC for a TIME. * The session's midnight moved on by the daylight saving the JVM's zone keeps then:
 * {@code 2024-07-02} in a UTC session, read in London, is 2024-07-02T01:00:00Z.
 *
 * <p>With a Calendar, a DATE reads as the session's midnight moved on by the Calendar's offset, and a TIMESTAMP_NTZ
 * as its wall clock in the Calendar's zone, its time of day kept by getDate too; a TIME, a TIMESTAMP_LTZ and a
 * TIMESTAMP_TZ read as they do without one.
 */
final class TemporalCellReads {

    private TemporalCellReads() {
    }

    /**
     * A cell as {@code getTimestamp} reads it.
     *
     * @param cell    the cell, not null
     * @param kind    the column's family
     * @param session the session's zone
     * @return the timestamp
     */
    static Timestamp timestamp(final Object cell, final TemporalColumnKind kind, final ZoneId session) {
        if (kind == TemporalColumnKind.TIME) {
            return new ZonedTimestamp(LocalDate.EPOCH.atTime(timeOf(cell)).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        }
        if (isZoned(kind)) {
            final OffsetDateTime moment = momentOf(cell, session);
            return new ZonedTimestamp(moment.toInstant(), moment.getOffset());
        }
        return JdbcMarshaling.toTimestamp(cell, kind == TemporalColumnKind.DATE ? Types.DATE : Types.TIMESTAMP);
    }

    /**
     * A cell as {@code getDate} reads it.
     *
     * @param cell    the cell, not null
     * @param kind    the column's family
     * @param session the session's zone
     * @return the date
     */
    static Date date(final Object cell, final TemporalColumnKind kind, final ZoneId session) {
        if (kind == TemporalColumnKind.NTZ) {
            return new Date(Timestamp.valueOf(wallClockOf(cell)).getTime());
        }
        if (isZoned(kind)) {
            final OffsetDateTime moment = momentOf(cell, session);
            return new ZonedDate(moment.toInstant(), moment.getOffset());
        }
        return JdbcMarshaling.toDate(cell);
    }

    /**
     * A cell as {@code getTime} reads it.
     *
     * @param cell       the cell, not null
     * @param kind       the column's family
     * @param session    the session's zone
     * @param columnType the column's JDBC type
     * @return the time
     */
    static Time time(final Object cell, final TemporalColumnKind kind, final ZoneId session, final int columnType) {
        if (kind == TemporalColumnKind.NTZ) {
            return new ZonedTime(wallClockOf(cell).atZone(session).toInstant(), session);
        }
        if (isZoned(kind)) {
            final OffsetDateTime moment = momentOf(cell, session);
            return new ZonedTime(moment.toInstant(), moment.getOffset());
        }
        return JdbcMarshaling.toTime(cell, columnType);
    }

    /**
     * A cell as {@code getObject} reads it, where its family reads otherwise than the transport's object does.
     *
     * @param cell    the cell, not null
     * @param kind    the column's family, or null
     * @param session the session's zone
     * @return the object, or null where the transport's object stands
     */
    static Object object(final Object cell, final TemporalColumnKind kind, final ZoneId session) {
        if (kind == TemporalColumnKind.DATE) {
            final long midnight = sessionMidnight(cell, session);
            final long saving = ZoneId.systemDefault().getRules().getDaylightSavings(Instant.ofEpochMilli(midnight))
                .toMillis();
            return new Date(midnight + saving);
        }
        return isZoned(kind) ? timestamp(cell, kind, session) : null;
    }

    /**
     * A cell as {@code getTimestamp} reads it with a Calendar.
     *
     * @param cell    the cell, not null
     * @param kind    the column's family
     * @param session the session's zone
     * @param cal     the Calendar
     * @return the timestamp
     */
    static Timestamp timestamp(final Object cell, final TemporalColumnKind kind, final ZoneId session,
                               final Calendar cal) {
        if (kind == TemporalColumnKind.DATE) {
            return new Timestamp(calendarMidnight(cell, session, cal));
        }
        return kind == TemporalColumnKind.NTZ ? JdbcMarshaling.toTimestamp(cell, cal) : timestamp(cell, kind, session);
    }

    /**
     * A cell as {@code getDate} reads it with a Calendar.
     *
     * @param cell    the cell, not null
     * @param kind    the column's family
     * @param session the session's zone
     * @param cal     the Calendar
     * @return the date
     */
    static Date date(final Object cell, final TemporalColumnKind kind, final ZoneId session, final Calendar cal) {
        if (kind == TemporalColumnKind.DATE) {
            return new Date(calendarMidnight(cell, session, cal));
        }
        if (kind == TemporalColumnKind.NTZ) {
            final LocalDateTime wall = wallClockOf(cell);
            return new Date(JdbcMarshaling.wallClockMillis(cal, wall) + wall.getNano() / 1_000_000L);
        }
        return date(cell, kind, session);
    }

    /** Whether a family's cells name a moment: a TIMESTAMP_LTZ's and a TIMESTAMP_TZ's. */
    private static boolean isZoned(final TemporalColumnKind kind) {
        return kind == TemporalColumnKind.LTZ || kind == TemporalColumnKind.TZ;
    }

    /** A DATE's midnight in the session's zone, moved on by the Calendar's offset then. */
    private static long calendarMidnight(final Object cell, final ZoneId session, final Calendar cal) {
        final long midnight = sessionMidnight(cell, session);
        return midnight + cal.getTimeZone().getOffset(midnight);
    }

    /** A DATE's midnight in the session's zone, its day counted as java.sql counts one: in the Julian calendar
     *  before 1582. */
    private static long sessionMidnight(final Object cell, final ZoneId session) {
        return JdbcMarshaling.wallClockMillis(Calendar.getInstance(TimeZone.getTimeZone(session)),
            dateOf(cell).atStartOfDay());
    }

    /** The moment a TIMESTAMP_LTZ or a TIMESTAMP_TZ cell names, with the offset it shows. */
    private static OffsetDateTime momentOf(final Object cell, final ZoneId session) {
        if (cell instanceof OffsetDateTime) {
            return (OffsetDateTime) cell;
        }
        if (cell instanceof ZonedDateTime) {
            return ((ZonedDateTime) cell).toOffsetDateTime();
        }
        if (cell instanceof Instant) {
            return ((Instant) cell).atZone(session).toOffsetDateTime();
        }
        if (cell instanceof LocalDateTime) {
            return ((LocalDateTime) cell).atZone(session).toOffsetDateTime();
        }
        final String text = cell.toString().trim();
        final int space = text.lastIndexOf(' ');
        final LocalDateTime wall = JdbcMarshaling.parseDateTime(text);
        if (space > 0 && JdbcMarshaling.isNumericOffset(text.substring(space + 1))) {
            return wall.atOffset(ZoneOffset.of(text.substring(space + 1)));
        }
        return wall.atZone(session).toOffsetDateTime();
    }

    /** The wall clock a TIMESTAMP_NTZ cell holds. */
    private static LocalDateTime wallClockOf(final Object cell) {
        return cell instanceof LocalDateTime ? (LocalDateTime) cell : JdbcMarshaling.parseDateTime(cell.toString());
    }

    /** The day a DATE cell holds. */
    private static LocalDate dateOf(final Object cell) {
        return cell instanceof LocalDate ? (LocalDate) cell : JdbcMarshaling.parseDate(cell.toString());
    }

    /** The time of day a TIME cell holds. */
    private static LocalTime timeOf(final Object cell) {
        return cell instanceof LocalTime ? (LocalTime) cell : LocalTime.parse(cell.toString().trim());
    }
}
