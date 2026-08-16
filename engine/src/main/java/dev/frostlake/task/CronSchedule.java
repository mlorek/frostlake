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

package dev.frostlake.task;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A task's {@code USING CRON <minute> <hour> <day-of-month> <month> <day-of-week> <time zone>}
 * schedule, parsed once and asked for its next fire time on the calendar.
 *
 * <p>The grammar is the subset a real account accepts, field by field (live-verified): numbers,
 * {@code *}, lists, ranges, and steps — {@code *}{@code /n}, {@code a-b/n}, and {@code a/n}, which
 * runs from {@code a} to the field's maximum; month names JAN–DEC and day names SUN–SAT in any
 * case; {@code L} in the day-of-month field (the month's last day, also as a list item); and
 * {@code nL} or {@code FRIL} in the day-of-week field (the month's last such weekday). Day of week
 * is 0–6. Refused, all with the one invalid-schedule sentence: {@code ?}, {@code #}, {@code W}, a
 * bare day-of-week {@code L}, {@code L-3}, a day-of-week 7, a step of 0, a reversed range, an empty
 * list item, an out-of-range value, and any field count but five.
 *
 * <p>★ DAY OF MONTH AND DAY OF WEEK ARE OR-ED WHEN BOTH ARE RESTRICTED: {@code 0 0 1 * MON} fires on
 * the 1st AND on every Monday, and a stepped {@code *}{@code /2} counts as restricted there. When
 * either field is a bare {@code *}, the other alone decides.
 *
 * <p>★ THE LAST TOKEN IS ALWAYS THE ZONE, which is why five fields and no zone are refused as an
 * unrecognized zone named {@code "*"}. It must be a region name the TIMEZONE parameter accepts —
 * {@code UTC}, {@code Etc/GMT+5}, {@code America/Los_Angeles}, in any case — while an offset such
 * as {@code +01:00} or an abbreviation such as {@code PST} is refused with a sentence naming it.
 */
public final class CronSchedule {

    /** The account's one sentence for every malformed schedule. */
    public static final String INVALID_SCHEDULE_MESSAGE =
        "Invalid schedule was specified. Please refer to the docs on what constitutes a valid schedule.";

    /** The sentence for a well-formed expression that names no instant at all, such as February 31st. */
    public static final String NEVER_FIRES_MESSAGE =
        "No valid time could be found under the schedule and/or interval specified";

    private static final String[] MONTH_NAMES = {
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"
    };

    private static final String[] DAY_NAMES = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};

    /**
     * How far ahead the next fire is searched for. Eight years reaches the next February 29th from
     * any date, so a schedule that finds nothing in that window can never fire.
     */
    private static final int SEARCH_DAYS = 8 * 366;

    /** The region time zones keyed upper-case, so a zone matches without regard to case. */
    private static final Map<String, String> ZONES_BY_UPPER_NAME = new HashMap<>();

    static {
        for (final String id : ZoneId.getAvailableZoneIds()) {
            ZONES_BY_UPPER_NAME.put(id.toUpperCase(Locale.ROOT), id);
        }
    }

    private final boolean[] minutes = new boolean[60];
    private final boolean[] hours = new boolean[24];
    private final boolean[] daysOfMonth = new boolean[32];
    private final boolean[] months = new boolean[13];
    private final boolean[] daysOfWeek = new boolean[7];
    private final boolean[] lastWeekdaysOfMonth = new boolean[7];
    private boolean lastDayOfMonth;
    private final boolean dayOfMonthRestricted;
    private final boolean dayOfWeekRestricted;
    private final ZoneId zone;

    private CronSchedule(final List<String> fields, final ZoneId zone) {
        this.zone = zone;
        fill(fields.get(0), 0, 59, null, 0, minutes);
        fill(fields.get(1), 0, 23, null, 0, hours);
        parseDaysOfMonth(fields.get(2));
        fill(fields.get(3), 1, 12, MONTH_NAMES, 1, months);
        parseDaysOfWeek(fields.get(4));
        this.dayOfMonthRestricted = !"*".equals(fields.get(2));
        this.dayOfWeekRestricted = !"*".equals(fields.get(4));
    }

    /**
     * Parse a whole {@code USING CRON …} schedule, refusing with the account's sentences.
     *
     * @param schedule the SCHEDULE text, {@code USING CRON} included
     * @return the parsed schedule
     */
    public static CronSchedule parse(final String schedule) {
        final List<String> tokens = new ArrayList<>();
        for (final String token : (schedule == null ? "" : schedule).trim().split("\\s+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        if (tokens.size() < 3 || !"USING".equalsIgnoreCase(tokens.get(0))
                || !"CRON".equalsIgnoreCase(tokens.get(1))) {
            throw new RuntimeException(INVALID_SCHEDULE_MESSAGE);
        }
        final String zoneToken = tokens.get(tokens.size() - 1);
        final String zoneId = ZONES_BY_UPPER_NAME.get(zoneToken.toUpperCase(Locale.ROOT));
        if (zoneId == null) {
            throw new RuntimeException("Invalid schedule was specified. \"" + zoneToken
                + "\" is not a recognized time zone. Please specify time zones accepted by the"
                + " TIMEZONE parameter.");
        }
        if (tokens.size() != 8) {
            throw new RuntimeException(INVALID_SCHEDULE_MESSAGE);
        }
        return new CronSchedule(tokens.subList(2, 7), ZoneId.of(zoneId));
    }

    /** The zone the fields are read in. */
    public ZoneId zone() {
        return zone;
    }

    /**
     * The first whole minute strictly after {@code from} that the schedule names, read in the
     * schedule's own zone.
     *
     * @param from the instant to search after
     * @return the next fire, or null when none exists within the search window — which means none
     *     ever will
     */
    public ZonedDateTime nextFireAfter(final ZonedDateTime from) {
        final LocalDateTime start = from.withZoneSameInstant(zone).toLocalDateTime()
            .truncatedTo(ChronoUnit.MINUTES).plusMinutes(1);
        LocalDate date = start.toLocalDate();
        final LocalDate limit = date.plusDays(SEARCH_DAYS);
        boolean firstDay = true;
        while (!date.isAfter(limit)) {
            if (dayMatches(date)) {
                for (int hour = firstDay ? start.getHour() : 0; hour < 24; hour++) {
                    if (!hours[hour]) {
                        continue;
                    }
                    final int firstMinute = firstDay && hour == start.getHour() ? start.getMinute() : 0;
                    for (int minute = firstMinute; minute < 60; minute++) {
                        if (!minutes[minute]) {
                            continue;
                        }
                        final ZonedDateTime candidate = ZonedDateTime.of(date, LocalTime.of(hour, minute), zone);
                        if (candidate.isAfter(from)) {
                            return candidate;
                        }
                    }
                }
            }
            date = date.plusDays(1);
            firstDay = false;
        }
        return null;
    }

    private boolean dayMatches(final LocalDate date) {
        if (!months[date.getMonthValue()]) {
            return false;
        }
        final boolean byDayOfMonth = daysOfMonth[date.getDayOfMonth()]
            || (lastDayOfMonth && date.getDayOfMonth() == date.lengthOfMonth());
        final int dayOfWeek = date.getDayOfWeek().getValue() % 7;
        final boolean byDayOfWeek = daysOfWeek[dayOfWeek]
            || (lastWeekdaysOfMonth[dayOfWeek] && date.getDayOfMonth() + 7 > date.lengthOfMonth());
        if (dayOfMonthRestricted && dayOfWeekRestricted) {
            return byDayOfMonth || byDayOfWeek;
        }
        if (dayOfMonthRestricted) {
            return byDayOfMonth;
        }
        if (dayOfWeekRestricted) {
            return byDayOfWeek;
        }
        return true;
    }

    /** Day of month: the ordinary items plus {@code L}, the month's last day, as a list item. */
    private void parseDaysOfMonth(final String field) {
        for (final String element : elements(field)) {
            if ("L".equalsIgnoreCase(element)) {
                lastDayOfMonth = true;
            } else {
                apply(element, 1, 31, null, 0, daysOfMonth);
            }
        }
    }

    /**
     * Day of week: the ordinary items plus {@code nL}, the month's last such weekday, written with a
     * single day number or name ({@code 5L}, {@code FRIL}). A bare {@code L} names no weekday.
     */
    private void parseDaysOfWeek(final String field) {
        for (final String element : elements(field)) {
            if (element.length() > 1 && Character.toUpperCase(element.charAt(element.length() - 1)) == 'L') {
                lastWeekdaysOfMonth[value(element.substring(0, element.length() - 1), 0, 6, DAY_NAMES, 0)] = true;
            } else {
                apply(element, 0, 6, DAY_NAMES, 0, daysOfWeek);
            }
        }
    }

    private static void fill(final String field, final int min, final int max, final String[] names,
                             final int nameBase, final boolean[] into) {
        for (final String element : elements(field)) {
            apply(element, min, max, names, nameBase, into);
        }
    }

    /** The comma-separated items of one field; an empty item — a doubled or trailing comma — refuses. */
    private static List<String> elements(final String field) {
        final List<String> out = new ArrayList<>();
        for (final String element : field.split(",", -1)) {
            if (element.isEmpty()) {
                throw invalid();
            }
            out.add(element);
        }
        return out;
    }

    /** One item: {@code *}, a value or {@code a-b}, any of them with {@code /step}. */
    private static void apply(final String element, final int min, final int max, final String[] names,
                              final int nameBase, final boolean[] into) {
        final int slash = element.indexOf('/');
        final String range = slash < 0 ? element : element.substring(0, slash);
        final int step = slash < 0 ? 1 : step(element.substring(slash + 1));
        final int from;
        final int to;
        if ("*".equals(range)) {
            from = min;
            to = max;
        } else {
            final int dash = range.indexOf('-');
            if (dash >= 0) {
                from = value(range.substring(0, dash), min, max, names, nameBase);
                to = value(range.substring(dash + 1), min, max, names, nameBase);
                if (from > to) {
                    throw invalid();
                }
            } else {
                from = value(range, min, max, names, nameBase);
                to = slash < 0 ? from : max;
            }
        }
        for (int v = from; v <= to; v += step) {
            into[v] = true;
        }
    }

    private static int step(final String text) {
        if (!text.matches("[0-9]{1,9}")) {
            throw invalid();
        }
        final int step = Integer.parseInt(text);
        if (step < 1) {
            throw invalid();
        }
        return step;
    }

    private static int value(final String text, final int min, final int max, final String[] names,
                             final int nameBase) {
        if (text.matches("[0-9]{1,9}")) {
            final int v = Integer.parseInt(text);
            if (v < min || v > max) {
                throw invalid();
            }
            return v;
        }
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                if (names[i].equalsIgnoreCase(text)) {
                    return i + nameBase;
                }
            }
        }
        throw invalid();
    }

    private static RuntimeException invalid() {
        return new RuntimeException(INVALID_SCHEDULE_MESSAGE);
    }
}
