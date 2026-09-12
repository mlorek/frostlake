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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Pattern;

/**
 * Snowflake's AUTO date/time input detection: a lenient field scanner for the timestamp and date forms
 * Snowflake accepts without an explicit format model. Strict {@code java.time} ISO parsing covers only one of
 * them, so the following all used to be rejected outright:
 *
 * <ul>
 *   <li>a zone designator — {@code 2025-07-19T16:35:12.589000000Z} and the documented
 *       {@code YYYY-MM-DD"T"HH24:MI:SS.FFTZH:TZM} form {@code 2013-04-28T20:57:01.123456789+07:00}. The offset
 *       is read and DROPPED: these parse to a wall-clock {@link LocalDateTime}, which is the TIMESTAMP_NTZ
 *       semantic (the caller decides what to do about zones).</li>
 *   <li>{@code MM/DD/YYYY [HH24:MI:SS]} with a one-digit month or day, e.g. {@code 3/5/2024 12:34:59}
 *       (Snowflake's own example is {@code 2/18/2008 02:36:48}).</li>
 *   <li>{@code DD-MON-YYYY}, e.g. {@code 17-DEC-1980}.</li>
 *   <li>whitespace variation between the date and the time — Snowflake "allows some whitespace differences
 *       in order to handle variably-formatted data", so {@code 2024-03-29  15:00:11} (two blanks) is the same
 *       instant as with one. The previous implementation swapped a single blank for {@code T}, which turned a
 *       double blank into {@code TT} and could never accept any other spacing.</li>
 *   <li>a digit count that does not match the canonical width: fields are read as digit runs and then
 *       range-checked, so a zero-padded {@code 00:00:003} reads as 3 seconds while {@code 00:00:599} is
 *       still rejected.</li>
 * </ul>
 *
 * Every method returns {@code null} for text it does not recognise — it never throws — so callers can fall
 * back to their own handling (or report their own error) rather than surfacing a java.time message.
 */
public final class AutoTemporalParser {

    // Compiled once — hasOverWideIsoFields ran two String.matches (fresh Pattern compiles) per call.
    private static final Pattern DASHED_TRIPLE = Pattern.compile("\\d+-\\d+-\\d+.*");
    private static final Pattern ISO_WIDTHS = Pattern.compile(
        "\\d{1,4}-\\d{1,2}-\\d{1,2}"
        + "([ T]+\\d{1,2}:\\d{1,2}(:\\d{1,2}(\\.\\d+)?)?)?"
        + "\\s*(Z|[+-]\\d{1,2}(:?\\d{1,2})?)?");

    private AutoTemporalParser() {
    }

    private static final String[] MONTH_NAMES = {
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    };

    /** The widest digit run still read as one field; anything longer overflows a year and is not a temporal. */
    private static final int MAX_FIELD_DIGITS = 9;

    /**
     * Whether an ISO-shaped temporal STRING carries a field wider than its canonical width — a year of
     * more than 4 digits, or a month / day / hour / minute / second of more than 2. Snowflake's DML
     * write path is stricter than its CAST: live-verified on a real account,
     * {@code '9999-12-31 00:00:003'::TIMESTAMP_NTZ} is accepted (3 seconds) while INSERTing the same
     * literal into a TIMESTAMP_NTZ column fails "Timestamp '…' is not recognized" — and so do an
     * over-wide month, day, hour, minute and a 5-digit year, while the NARROWER
     * {@code '2024-1-01 00:00:3'} inserts fine. Only ISO-shaped text is judged; anything else (an epoch
     * string, {@code MM/DD/YYYY}, {@code DD-MON-YYYY}) is left to the scanner.
     */
    public static boolean hasOverWideIsoFields(final String raw) {
        if (raw == null) {
            return false;
        }
        final String text = raw.trim();
        if (!DASHED_TRIPLE.matcher(text).matches()) {
            return false;
        }
        return !ISO_WIDTHS.matcher(text).matches();
    }

    /** The value {@link #parseDateTime(String, int[])} writes when the text carries no zone at all. */
    public static final int NO_OFFSET = Integer.MIN_VALUE;

    /** Parse a date + optional time (+ optional zone offset, discarded); null if unrecognised. */
    public static LocalDateTime parseDateTime(final String raw) {
        return parseDateTime(raw, new int[1]);
    }

    /**
     * Parse a date + optional time + optional zone, reporting the zone's offset in MINUTES through
     * {@code offsetOut} — {@link #NO_OFFSET} when the text carries none. The scanner still yields the
     * WALL CLOCK; what the offset means is the caller's business, because it depends on the target:
     * a TIMESTAMP_NTZ drops it and a TIMESTAMP_TZ or _LTZ moves the instant by it.
     *
     * <p>The accepted spellings are narrow and were measured one at a time: {@code +HHMM},
     * {@code +HH:MM}, {@code +HH}, {@code +H} and an UPPER-CASE {@code Z}, with or without a space
     * before them. A lower-case {@code z}, a seconds field ({@code +03:00:30}), and the zone NAMES
     * {@code UTC}, {@code GMT} and {@code America/Los_Angeles} are all refused on a real account —
     * the last three despite reading like the most obvious spellings of all.
     */
    public static LocalDateTime parseDateTime(final String raw, final int[] offsetOut) {
        if (raw == null) {
            return null;
        }
        final String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        offsetOut[0] = NO_OFFSET;
        final int[] pos = {0};
        final LocalDate date = scanDate(s, pos);
        if (date == null) {
            return null;
        }
        LocalTime time = LocalTime.MIDNIGHT;
        if (skipDateTimeSeparator(s, pos) && pos[0] < s.length()) {
            time = scanTime(s, pos);
            if (time == null) {
                return null;
            }
        }
        readZone(s, pos, offsetOut);
        if (pos[0] != s.length()) {
            return null;   // trailing text that is not a zone designator
        }
        return LocalDateTime.of(date, time);
    }

    /** Parse a date, tolerating (and discarding) a trailing time and zone; null if unrecognised. */
    public static LocalDate parseDate(final String raw) {
        final LocalDateTime dt = parseDateTime(raw);
        return dt == null ? null : dt.toLocalDate();
    }

    /**
     * Scan the date part at {@code pos}: {@code YYYY-MM-DD}, {@code MM/DD/YYYY} or {@code DD-MON-YYYY}.
     * Returns null (leaving {@code pos} unspecified) when the text does not start with a date.
     */
    private static LocalDate scanDate(final String s, final int[] pos) {
        final int firstStart = pos[0];
        final int first = scanDigitRun(s, pos);
        if (first < 0 || pos[0] >= s.length()) {
            return null;
        }
        final int firstDigits = pos[0] - firstStart;
        final char sep = s.charAt(pos[0]);

        if (sep == '/') {
            // MM/DD/YYYY — month and day may be a single digit.
            pos[0]++;
            final int day = scanDigits(s, pos, 2);
            if (day < 0 || pos[0] >= s.length() || s.charAt(pos[0]) != '/') {
                return null;
            }
            pos[0]++;
            final int year = scanDigits(s, pos, 4);
            return year < 0 ? null : dateOf(year, first, day);
        }
        if (sep != '-') {
            return null;
        }
        pos[0]++;
        if (pos[0] < s.length() && isLetter(s.charAt(pos[0]))) {
            // DD-MON-YYYY
            final int month = scanMonthName(s, pos);
            if (month < 0 || pos[0] >= s.length() || s.charAt(pos[0]) != '-') {
                return null;
            }
            pos[0]++;
            final int year = scanDigits(s, pos, 4);
            return year < 0 ? null : dateOf(year, month, first);
        }
        if (firstDigits < 3) {
            return null;   // a 1-2 digit leading field with '-' is not one of the supported forms
        }
        // YYYY-MM-DD
        final int month = scanDigitRun(s, pos);
        if (month < 0 || pos[0] >= s.length() || s.charAt(pos[0]) != '-') {
            return null;
        }
        pos[0]++;
        final int day = scanDigitRun(s, pos);
        return day < 0 ? null : dateOf(first, month, day);
    }

    /** Scan {@code HH:MI[:SS[.fraction]]} at {@code pos}; null when it is not a valid time. */
    private static LocalTime scanTime(final String s, final int[] pos) {
        final int hour = scanDigitRun(s, pos);
        if (hour < 0 || pos[0] >= s.length() || s.charAt(pos[0]) != ':') {
            return null;
        }
        pos[0]++;
        final int minute = scanDigitRun(s, pos);
        if (minute < 0) {
            return null;
        }
        int second = 0;
        int nano = 0;
        if (pos[0] < s.length() && s.charAt(pos[0]) == ':') {
            pos[0]++;
            // The whole digit run, then range-checked: a zero-padded '003' is 3 seconds, '599' is rejected.
            second = scanDigitRun(s, pos);
            if (second < 0) {
                return null;
            }
            if (pos[0] < s.length() && s.charAt(pos[0]) == '.') {
                pos[0]++;
                final int fracStart = pos[0];
                final int fraction = scanDigits(s, pos, 9);
                if (fraction < 0) {
                    return null;
                }
                int scaled = fraction;
                for (int digits = pos[0] - fracStart; digits < 9; digits++) {
                    scaled *= 10;
                }
                nano = scaled;
            }
        }
        try {
            return LocalTime.of(hour, minute, second, nano);
        } catch (final DateTimeException outOfRange) {
            return null;
        }
    }

    /**
     * Step over the separator between the date and the time: a run of blanks, an ISO {@code T}, or both.
     * Returns false when the date is not followed by anything that can introduce a time.
     */
    private static boolean skipDateTimeSeparator(final String s, final int[] pos) {
        final int start = pos[0];
        while (pos[0] < s.length() && isBlank(s.charAt(pos[0]))) {
            pos[0]++;
        }
        if (pos[0] < s.length() && (s.charAt(pos[0]) == 'T' || s.charAt(pos[0]) == 't')) {
            pos[0]++;
            while (pos[0] < s.length() && isBlank(s.charAt(pos[0]))) {
                pos[0]++;
            }
            return true;
        }
        return pos[0] > start || pos[0] >= s.length();
    }

    /**
     * Step over a trailing zone designator, reporting its offset in minutes through {@code offsetOut}.
     * See {@link #parseDateTime(String, int[])} for the spellings, which are measured rather than
     * guessed — the zone NAMES are refused, and so is a lower-case {@code z}.
     */
    private static void readZone(final String s, final int[] pos, final int[] offsetOut) {
        final int start = pos[0];
        while (pos[0] < s.length() && isBlank(s.charAt(pos[0]))) {
            pos[0]++;
        }
        if (pos[0] >= s.length()) {
            return;
        }
        final char c = s.charAt(pos[0]);
        if (c == 'Z') {
            pos[0]++;
            offsetOut[0] = 0;
            return;
        }
        if (c != '+' && c != '-') {
            pos[0] = start;
            return;
        }
        final int[] probe = {pos[0] + 1};
        final int hours = scanDigits(s, probe, 2);
        if (hours < 0) {
            pos[0] = start;
            return;
        }
        int minutes = 0;
        if (probe[0] < s.length() && s.charAt(probe[0]) == ':') {
            probe[0]++;
            minutes = scanDigits(s, probe, 2);
            if (minutes < 0) {
                pos[0] = start;
                return;
            }
        } else if (probe[0] < s.length() && isDigit(s.charAt(probe[0]))) {
            // The colon-less +HHMM form: the two hour digits are followed straight by two more.
            minutes = scanDigits(s, probe, 2);
            if (minutes < 0) {
                pos[0] = start;
                return;
            }
        }
        pos[0] = probe[0];
        offsetOut[0] = (c == '-' ? -1 : 1) * (hours * 60 + minutes);
    }

    /**
     * Read the WHOLE digit run at {@code pos} as a number; -1 when there is no digit (or when the run is
     * too long to be a temporal field). Snowflake's AUTO detection sizes each field by its digit run and
     * then RANGE-CHECKS the value, rather than by a canonical width — live-verified on a real account
     *: {@code '9999-012-31'::DATE}, {@code '2024-01-01 001:00:00'::TIMESTAMP_NTZ},
     * {@code '2024-01-01 00:003:00'}, {@code '2024-01-01 00:00:0003'} and the six-digit year
     * {@code '999999-12-31'} are all accepted, while the OUT-OF-RANGE {@code '9999-013-31'} (month 13),
     * {@code '9999-12-032'} (day 32), {@code '2024-01-01 025:00:00'} (hour 25) and
     * {@code '2024-01-01 00:00:599'} (second 599) are rejected.
     */
    private static int scanDigitRun(final String s, final int[] pos) {
        return scanDigits(s, pos, MAX_FIELD_DIGITS);
    }

    /** Read at most {@code maxDigits} digits at {@code pos} as a number; -1 when there is no digit. */
    private static int scanDigits(final String s, final int[] pos, final int maxDigits) {
        int value = 0;
        int count = 0;
        while (pos[0] < s.length() && count < maxDigits && isDigit(s.charAt(pos[0]))) {
            value = value * 10 + (s.charAt(pos[0]) - '0');
            pos[0]++;
            count++;
        }
        return count == 0 ? -1 : value;
    }

    /** Read a three-letter month abbreviation at {@code pos}, returning 1-12; -1 when it is not one. */
    private static int scanMonthName(final String s, final int[] pos) {
        for (int m = 0; m < MONTH_NAMES.length; m++) {
            if (s.regionMatches(true, pos[0], MONTH_NAMES[m], 0, 3)) {
                pos[0] += 3;
                // Consume the rest of a spelled-out month name (DECEMBER as well as DEC).
                while (pos[0] < s.length() && isLetter(s.charAt(pos[0]))) {
                    pos[0]++;
                }
                return m + 1;
            }
        }
        return -1;
    }

    private static LocalDate dateOf(final int year, final int month, final int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (final DateTimeException outOfRange) {
            return null;
        }
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isBlank(final char c) {
        return c == ' ' || c == '\t';
    }
}
