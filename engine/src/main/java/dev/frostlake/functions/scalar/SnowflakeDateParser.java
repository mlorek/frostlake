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

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a string against an explicit Snowflake date/time format model — TO_DATE / TO_TIME /
 * TO_TIMESTAMP* with a format argument — the way the account does, and refuses the way it does:
 * {@code Can't parse '<input>' as date|time|timestamp with format '<format>'}, both echoed verbatim
 * (the input with its own spaces, the format with its quotes). The TRY_ spellings turn that into
 * NULL. Formatting stays with {@link SnowflakeDateFormat}; both read the same {@link FormatPiece}s.
 *
 * <p>The account's reader is a backtracking scan, not a pattern match (live-verified throughout):
 * <ul>
 *   <li>a numeric element takes its natural width first and gives digits back when the rest cannot
 *       match — '1152020' under MMDDYYYY is November 5th 2020, and so is '2020115' under YYYYMMDD —
 *       while a four-digit year also reads three or five digits when a separator follows
 *       ('202-01-15' is 0202-01-15, '20201-01-15' is 20201-01-15); a two-digit year is 1970–2069;</li>
 *   <li>the elements match case-insensitively ('yyyy-mm-dd' is 'YYYY-MM-DD'), and so do the texts:
 *       'jAN' and 'January' under MON, 'Jan' under MMMM, 'pm';</li>
 *   <li>a missing field is 1970-01-01 00:00:00 — TO_DATE('15', 'DD') is 1970-01-15, TO_DATE('', '')
 *       is 1970-01-01, TO_TIMESTAMP('10:00:00', 'HH24:MI:SS') is on the epoch day;</li>
 *   <li>a value its field cannot hold is a refusal — month 13, February 30th, hour 24, second 60, a
 *       13 under HH12, a signed year;</li>
 *   <li>PM adds twelve to an hour below twelve under HH24 and HH12 alike, AM turns a twelve under
 *       HH12 into zero and leaves HH24 alone, and HH12 without AM/PM is the hour as written;</li>
 *   <li>FF reads any number of fraction digits and keeps the first nine — FF3 is a display width,
 *       not a parse width;</li>
 *   <li>a day name under DY is read and ignored ('Mon 2020-01-15' is a Wednesday);</li>
 *   <li>TZH / TZHTZM / TZH:TZM read a signed offset or Z; TZD reads a standard-time abbreviation;</li>
 *   <li>a space in the model is optional in the input except at the very end, where it is required;
 *       the input's own leading and trailing spaces are forgiven, inner ones are not;</li>
 *   <li>a repeated element keeps the last value.</li>
 * </ul>
 */
public final class SnowflakeDateParser {
    private static final int PIECES_CACHE_CAPACITY = 256;
    private static final Map<String, List<FormatPiece>> PIECES_CACHE = new ConcurrentHashMap<>();

    private static final String YEAR = "YEAR";
    private static final String MONTH = "MONTH";
    private static final String DAY = "DAY";
    private static final String HOUR = "HOUR";
    private static final String HOUR12 = "HOUR12";
    private static final String MINUTE = "MINUTE";
    private static final String SECOND = "SECOND";
    private static final String NANO = "NANO";
    private static final String MERIDIEM = "MERIDIEM";
    private static final String OFFSET = "OFFSET";

    // The natural width first, then shorter, then longer: '20201-01-15' is year 20201 only after the
    // four-, three-, two- and one-digit readings have failed against the separator. A year that
    // another element follows directly is four digits and nothing else: '1631711999' under YYYYMMDD
    // is refused rather than read as the year 16317119.
    private static final int[] YEAR_WIDTHS = {4, 3, 2, 1, 5, 6, 7, 8, 9};
    private static final int[] ADJACENT_YEAR_WIDTHS = {4};
    private static final int[] TWO_DIGIT_WIDTHS = {2, 1};

    private final List<FormatPiece> pieces;
    private final String input;
    private final Map<String, Integer> fields = new HashMap<>();
    private String zone;
    private TemporalAccessor result;

    private SnowflakeDateParser(final List<FormatPiece> pieces, final String input) {
        this.pieces = pieces;
        this.input = input;
    }

    /**
     * The input read against the model: a {@link LocalDateTime}, or an {@link java.time.OffsetDateTime}
     * when the model read an offset or a zone abbreviation.
     *
     * @param input the text as given, echoed verbatim in the refusal
     * @param format the Snowflake format model, echoed verbatim in the refusal
     * @param kind the word the refusal uses for the target: date, time or timestamp
     * @return the value the model read
     * @throws RuntimeException when the input does not match the model
     */
    public static TemporalAccessor parse(final String input, final String format, final String kind) {
        TemporalAccessor parsed = attempt(input, format);
        if (parsed == null && !input.trim().equals(input)) {
            parsed = attempt(input.trim(), format);
        }
        if (parsed == null) {
            throw new RuntimeException(
                "Can't parse '" + input + "' as " + kind + " with format '" + format + "'");
        }
        return parsed;
    }

    private static TemporalAccessor attempt(final String text, final String format) {
        final SnowflakeDateParser parser = new SnowflakeDateParser(piecesOf(format), text);
        return parser.parseFrom(0, 0) ? parser.result : null;
    }

    // Format strings are query constants but the scan would run per ROW; the pieces are immutable.
    private static List<FormatPiece> piecesOf(final String format) {
        final List<FormatPiece> existing = PIECES_CACHE.get(format);
        if (existing != null) {
            return existing;
        }
        final List<FormatPiece> scanned = SnowflakeDateFormat.pieces(format);
        if (PIECES_CACHE.size() >= PIECES_CACHE_CAPACITY) {
            final Iterator<String> it = PIECES_CACHE.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        PIECES_CACHE.put(format, scanned);
        return scanned;
    }

    /** Matches the pieces from {@code index} on against the input from {@code pos} on, backtracking. */
    private boolean parseFrom(final int index, final int pos) {
        if (index == pieces.size()) {
            return pos == input.length() && assemble();
        }
        final FormatPiece piece = pieces.get(index);
        if (!piece.isElement()) {
            return parseLiteral(piece.literal(), index, pos);
        }
        final String element = piece.element();
        switch (element) {
            case "YYYY":
                return parseNumber(YEAR, elementFollows(index) ? ADJACENT_YEAR_WIDTHS : YEAR_WIDTHS,
                    0, 999_999_999, index, pos);
            case "YY":
            case "Y":
                return parseTwoDigitYear(index, pos);
            case "MM":
                return parseNumber(MONTH, TWO_DIGIT_WIDTHS, 1, 12, index, pos);
            case "DD":
            case "D":
                return parseNumber(DAY, TWO_DIGIT_WIDTHS, 1, 31, index, pos);
            case "HH24":
            case "HH":
            case "H":
                return parseNumber(HOUR, TWO_DIGIT_WIDTHS, 0, 23, index, pos);
            case "HH12":
                return parseNumber(HOUR12, TWO_DIGIT_WIDTHS, 0, 12, index, pos);
            case "MI":
                return parseNumber(MINUTE, TWO_DIGIT_WIDTHS, 0, 59, index, pos);
            case "SS":
                return parseNumber(SECOND, TWO_DIGIT_WIDTHS, 0, 59, index, pos);
            case "MON":
            case "MMM":
            case "MMMM":
                return parseMonthName(index, pos);
            case "DY":
                return parseDayName(index, pos);
            case "AM":
            case "PM":
                return parseMeridiem(index, pos);
            case "TZH:TZM":
            case "TZHTZM":
            case "TZH":
                return parseOffset(index, pos);
            case "TZD":
                return parseZoneName(index, pos);
            default:
                return element.startsWith("FF") && parseFraction(index, pos);
        }
    }

    private boolean elementFollows(final int index) {
        return index + 1 < pieces.size() && pieces.get(index + 1).isElement();
    }

    private boolean parseLiteral(final String literal, final int index, final int pos) {
        int at = pos;
        for (int i = 0; i < literal.length(); i++) {
            final char expected = literal.charAt(i);
            final boolean trailing = index == pieces.size() - 1 && i == literal.length() - 1;
            if (expected == ' ' && !trailing) {
                if (at < input.length() && input.charAt(at) == ' ') {
                    at++;
                }
                continue;
            }
            if (at >= input.length() || input.charAt(at) != expected) {
                return false;
            }
            at++;
        }
        return parseFrom(index + 1, at);
    }

    private boolean parseNumber(final String key, final int[] widths, final int min, final int max,
                                final int index, final int pos) {
        final int digits = digitRun(pos);
        for (final int width : widths) {
            if (width > digits) {
                continue;
            }
            final int value = Integer.parseInt(input.substring(pos, pos + width));
            if (value >= min && value <= max && tryField(key, value, index, pos + width)) {
                return true;
            }
        }
        return false;
    }

    private boolean parseTwoDigitYear(final int index, final int pos) {
        final int digits = digitRun(pos);
        for (final int width : TWO_DIGIT_WIDTHS) {
            if (width > digits) {
                continue;
            }
            final int value = Integer.parseInt(input.substring(pos, pos + width));
            if (tryField(YEAR, value >= 70 ? 1900 + value : 2000 + value, index, pos + width)) {
                return true;
            }
        }
        return false;
    }

    private boolean parseFraction(final int index, final int pos) {
        final int digits = digitRun(pos);
        final StringBuilder nanos = new StringBuilder(input.substring(pos, pos + Math.min(digits, 9)));
        while (nanos.length() < 9) {
            nanos.append('0');
        }
        return tryField(NANO, Integer.parseInt(nanos.toString()), index, pos + digits);
    }

    private boolean parseMonthName(final int index, final int pos) {
        for (final TextStyle style : new TextStyle[] {TextStyle.FULL, TextStyle.SHORT}) {
            for (final Month month : Month.values()) {
                final String name = month.getDisplayName(style, Locale.US);
                if (input.regionMatches(true, pos, name, 0, name.length())
                        && tryField(MONTH, month.getValue(), index, pos + name.length())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean parseDayName(final int index, final int pos) {
        for (final TextStyle style : new TextStyle[] {TextStyle.FULL, TextStyle.SHORT}) {
            for (final DayOfWeek day : DayOfWeek.values()) {
                final String name = day.getDisplayName(style, Locale.US);
                if (input.regionMatches(true, pos, name, 0, name.length())
                        && parseFrom(index + 1, pos + name.length())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean parseMeridiem(final int index, final int pos) {
        if (input.regionMatches(true, pos, "AM", 0, 2)) {
            return tryField(MERIDIEM, 0, index, pos + 2);
        }
        if (input.regionMatches(true, pos, "PM", 0, 2)) {
            return tryField(MERIDIEM, 1, index, pos + 2);
        }
        return false;
    }

    private boolean parseOffset(final int index, final int pos) {
        if (pos < input.length() && (input.charAt(pos) == 'Z' || input.charAt(pos) == 'z')) {
            return tryField(OFFSET, 0, index, pos + 1);
        }
        if (pos >= input.length() || (input.charAt(pos) != '+' && input.charAt(pos) != '-')) {
            return false;
        }
        final int sign = input.charAt(pos) == '-' ? -1 : 1;
        int at = pos + 1;
        if (digitRun(at) < 2) {
            return false;
        }
        final int hours = Integer.parseInt(input.substring(at, at + 2));
        at += 2;
        int minutes = 0;
        final int afterColon = at < input.length() && input.charAt(at) == ':' ? at + 1 : at;
        if (digitRun(afterColon) >= 2) {
            minutes = Integer.parseInt(input.substring(afterColon, afterColon + 2));
            at = afterColon + 2;
        }
        if (hours > 18 || minutes > 59) {
            return false;
        }
        return tryField(OFFSET, sign * (hours * 3600 + minutes * 60), index, at);
    }

    private boolean parseZoneName(final int index, final int pos) {
        int at = pos;
        while (at < input.length() && Character.isLetter(input.charAt(at))) {
            at++;
        }
        if (at == pos) {
            return false;
        }
        final String name = input.substring(pos, at).toUpperCase(Locale.ROOT);
        final String id = "Z".equals(name) || "UTC".equals(name) || "GMT".equals(name)
            ? "UTC" : ZoneId.SHORT_IDS.get(name);
        if (id == null) {
            return false;
        }
        final String previous = zone;
        zone = id;
        if (parseFrom(index + 1, at)) {
            return true;
        }
        zone = previous;
        return false;
    }

    private boolean tryField(final String key, final int value, final int index, final int next) {
        final Integer previous = fields.put(key, value);
        if (parseFrom(index + 1, next)) {
            return true;
        }
        if (previous == null) {
            fields.remove(key);
        } else {
            fields.put(key, previous);
        }
        return false;
    }

    private int digitRun(final int pos) {
        int at = pos;
        while (at < input.length() && input.charAt(at) >= '0' && input.charAt(at) <= '9') {
            at++;
        }
        return at - pos;
    }

    /** Resolves the fields read so far, refusing a value no calendar day or clock can hold. */
    private boolean assemble() {
        final int year = fields.getOrDefault(YEAR, 1970);
        final int month = fields.getOrDefault(MONTH, 1);
        final int day = fields.getOrDefault(DAY, 1);
        final boolean twelveHour = !fields.containsKey(HOUR) && fields.containsKey(HOUR12);
        int hour = twelveHour ? fields.get(HOUR12) : fields.getOrDefault(HOUR, 0);
        final Integer meridiem = fields.get(MERIDIEM);
        if (meridiem != null && meridiem == 1 && hour < 12) {
            hour += 12;
        } else if (meridiem != null && meridiem == 0 && hour == 12 && twelveHour) {
            hour = 0;
        }
        try {
            final LocalDateTime wall = LocalDateTime.of(LocalDate.of(year, month, day),
                LocalTime.of(hour, fields.getOrDefault(MINUTE, 0), fields.getOrDefault(SECOND, 0),
                    fields.getOrDefault(NANO, 0)));
            final Integer offset = fields.get(OFFSET);
            if (offset != null) {
                result = wall.atOffset(ZoneOffset.ofTotalSeconds(offset));
            } else if (zone != null) {
                result = wall.atOffset(
                    ZoneId.of(zone).getRules().getStandardOffset(wall.toInstant(ZoneOffset.UTC)));
            } else {
                result = wall;
            }
            return true;
        } catch (final DateTimeException invalid) {
            return false;
        }
    }
}
