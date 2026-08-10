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

import dev.frostlake.values.BinaryValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SharedFunctionHelpers {

    private SharedFunctionHelpers() {}

    /**
     * Divide with Snowflake's result scale: {@code max(S1, min(S1 + 6, 12))} where S1 is the dividend's
     * scale (docs: "Arithmetic operators — division"), rounding half away from zero. Integer / integer
     * therefore yields six fractional digits — {@code 1/3 = 0.333333} — matching Snowflake output.
     * Runtime values carry no declared column scale, so S1 is the value's carried scale, UNSTRIPPED —
     * {@code 10/2} keeps its six digits ({@code 5.000000}) exactly as Snowflake renders it.
     */
    public static BigDecimal divideWithSnowflakeScale(final BigDecimal dividend, final BigDecimal divisor) {
        // Snowflake: quotient scale = MIN(s1 + 6, 12) where s1 is the DIVIDEND's scale (live-verified:
        // 10/3 → NUMBER(8,6) 3.333333, 10.5/2 → NUMBER(9,7), NUMBER(20,11)/2 → scale 12). The scale is
        // the value's carried scale, unstripped — 10/2 stays 5.000000.
        final int dividendScale = Math.max(dividend.scale(), 0);
        final int resultScale = Math.max(dividendScale, Math.min(dividendScale + 6, 12));
        return dividend.divide(divisor, resultScale, RoundingMode.HALF_UP);
    }

    // One MessageDigest per (thread, algorithm): getInstance runs a JCA provider lookup, which the
    // hash functions paid per ROW. MessageDigest is stateful, so instances are never shared across
    // threads; digest() leaves the instance reset.
    private static final ThreadLocal<Map<String, MessageDigest>> DIGESTS =
        new ThreadLocal<Map<String, MessageDigest>>() {
            @Override
            protected Map<String, MessageDigest> initialValue() {
                return new HashMap<String, MessageDigest>();
            }
        };

    public static byte[] digest(final String algo, final byte[] input) {
        final Map<String, MessageDigest> byAlgo = DIGESTS.get();
        MessageDigest md = byAlgo.get(algo);
        if (md == null) {
            try {
                md = MessageDigest.getInstance(algo);
            } catch (final NoSuchAlgorithmException e) {
                throw new RuntimeException("Algorithm not available: " + algo);
            }
            byAlgo.put(algo, md);
        }
        return md.digest(input);
    }

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    public static String toHex(final byte[] bytes) {
        // Digit-table loop — String.format("%02x", b) parsed a format string PER BYTE.
        final StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(HEX_DIGITS[(b >> 4) & 0xF]).append(HEX_DIGITS[b & 0xF]);
        }
        return sb.toString();
    }

    /**
     * A temporal value as a type-preserving SQL literal ({@code '2026-01-03'::DATE}), so a value
     * inlined into regenerated SQL re-enters the engine as a temporal rather than a VARCHAR.
     * Returns null for non-temporal values.
     */
    public static String temporalSqlLiteral(final Object value) {
        if (value instanceof LocalDate) {
            return "'" + value + "'::DATE";
        }
        // ISO toString text, NOT the FF3 display form: the literal must round-trip LOSSLESSLY.
        // CURRENT_TIMESTAMP() variables carry nanosecond precision, and a value stored through this
        // literal must later compare EQUAL to the same in-memory variable (delta-watermark flows
        // re-match rows by exact equality); truncating to milliseconds silently broke that.
        if (value instanceof LocalTime) {
            return "'" + value + "'::TIME";
        }
        if (value instanceof LocalDateTime) {
            return "'" + value + "'::TIMESTAMP_NTZ";
        }
        return null;
    }

    /**
     * The byte payload a function should hash, digest or encode: a BINARY value contributes its OWN
     * bytes (never its hex rendering — {@link BinaryValue#toString()} is the display form, so
     * {@code toString().getBytes()} would silently encode the ASCII hex digits and double the length),
     * and everything else contributes the UTF-8 encoding of its Snowflake output text.
     *
     * <p>The text is {@link #textOf} rather than {@code toString()} so a TIMESTAMP contributes
     * {@code 2024-01-02 03:04:05.000} — what Snowflake hashes — instead of java.time's
     * {@code 2024-01-02T03:04:05}.
     */
    public static byte[] toUtf8(final Object v) {
        if (v instanceof BinaryValue) {
            return ((BinaryValue) v).bytes();
        }
        return textOf(v).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The 1-based position of {@code needle} in {@code haystack} at or after the 0-based
     * {@code fromIndex}, or 0 when absent — the byte-wise counterpart of {@code String.indexOf} used
     * by POSITION / CHARINDEX over BINARY values. An empty needle matches at {@code fromIndex}.
     */
    public static long indexOfBytes(final byte[] haystack, final byte[] needle, final int fromIndex) {
        final int start = Math.max(fromIndex, 0);
        for (int i = start; i <= haystack.length - needle.length; i++) {
            boolean matched = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                return i + 1L;
            }
        }
        return 0L;
    }

    /**
     * The byte payload of a BINARY-typed argument. Snowflake rejects a VARCHAR where BINARY is
     * expected (no implicit text-to-binary coercion for these functions), so anything that is not
     * an actual binary runtime value is an argument-type error.
     */
    public static byte[] binaryArgBytes(final Object value, final String functionName) {
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).bytes();
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        throw new RuntimeException("Invalid argument types for function '" + functionName
            + "': expected BINARY, got " + (value instanceof Number ? "NUMBER" : "VARCHAR"));
    }

    public static boolean isTruthy(final Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof String) {
            final String s = ((String) v).trim().toUpperCase();
            return s.equals("TRUE") || s.equals("1");
        }
        return false;
    }

    /**
     * Snowflake's default TIMESTAMP output text — {@code YYYY-MM-DD HH24:MI:SS.FF3}: a SPACE separator and
     * EXACTLY three fractional digits ({@code …:38.604}, {@code …:28.000}). {@code LocalDateTime.toString}
     * instead used a {@code T} and dropped trailing zeros, so every text rendering of a timestamp (::VARCHAR,
     * TO_VARCHAR, ||, a value embedded in a VARIANT) differed from Snowflake's.
     */
    public static String timestampText(final LocalDateTime dt) {
        return String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d",
            dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(),
            dt.getHour(), dt.getMinute(), dt.getSecond(), dt.getNano() / 1_000_000);
    }

    /** Snowflake's default TIME output text — {@code HH24:MI:SS}, no fractional part. */
    public static String timeText(final LocalTime t) {
        return String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
    }

    /**
     * A value rendered for a STRING context (::VARCHAR, TO_VARCHAR/TO_CHAR without a format, {@code ||},
     * embedding into a VARIANT): temporals use Snowflake's default output forms above, everything else its
     * ordinary text. A DATE's own text is already {@code YYYY-MM-DD}.
     */
    public static String textOf(final Object value) {
        if (value instanceof LocalDateTime) {
            return timestampText((LocalDateTime) value);
        }
        if (value instanceof LocalTime) {
            return timeText((LocalTime) value);
        }
        if (value instanceof Double || value instanceof Float) {
            return floatText(((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    /**
     * Snowflake's FLOAT-to-VARCHAR rendering: C-style {@code %.10g} — 10 significant digits, fixed or
     * scientific per the %g exponent rule, trailing fractional zeros stripped. Java's shortest-round-trip
     * {@code Double.toString} printed {@code 0.8999999761581421} where Snowflake prints
     * {@code 0.8999999762}, so every SHA2/checksum over a stringified float diverged.
     */
    public static String floatText(final double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        final String formatted = String.format(Locale.ROOT, "%.10g", d);
        final int expAt = formatted.indexOf('e');
        String mantissa = expAt < 0 ? formatted : formatted.substring(0, expAt);
        final String exponent = expAt < 0 ? "" : formatted.substring(expAt);
        if (mantissa.indexOf('.') >= 0) {
            int end = mantissa.length();
            while (end > 0 && mantissa.charAt(end - 1) == '0') {
                end--;
            }
            if (end > 0 && mantissa.charAt(end - 1) == '.') {
                end--;
            }
            mantissa = mantissa.substring(0, end);
        }
        return mantissa + exponent;
    }

    // --- shared date/datetime parsing helpers ---
    public static LocalDate toLocalDate(final Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate) return (LocalDate) v;
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).toLocalDate();
        final String s = v.toString().trim();
        try { return LocalDate.parse(s); } catch (final Exception ignored) {}
        final LocalDate scanned = AutoTemporalParser.parseDate(s);
        if (scanned != null) return scanned;
        return LocalDate.parse(s);   // not a recognised form: surface java.time's own message
    }

    public static LocalDateTime toLocalDateTime(final Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        if (v instanceof LocalDate) return ((LocalDate) v).atStartOfDay();
        final String s = v.toString().trim();
        try { return LocalDateTime.parse(s); } catch (final Exception ignored) {}
        // Snowflake's AUTO input detection covers far more than ISO-8601: a zone designator, MM/DD/YYYY,
        // DD-MON-YYYY, whitespace variation and non-canonical digit counts (see AutoTemporalParser).
        final LocalDateTime scanned = AutoTemporalParser.parseDateTime(s);
        if (scanned != null) return scanned;
        try { return LocalDate.parse(s).atStartOfDay(); } catch (final Exception ignored) {}
        throw new RuntimeException("Cannot parse date/time: " + s);
    }

    public static LocalTime toLocalTime(final Object v) {
        if (v == null) return null;
        if (v instanceof LocalTime) return (LocalTime) v;
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).toLocalTime();
        final String s = v.toString().trim();
        try { return LocalTime.parse(s); } catch (final Exception ignored) {}
        return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm"));
    }

    /**
     * Coerce a value to the temporal Java representation backing a Snowflake DATE / TIME / TIMESTAMP* type —
     * the same {@link LocalDate} / {@link LocalTime} / {@link LocalDateTime} that TO_DATE / TO_TIME /
     * TO_TIMESTAMP would produce — so a string (e.g. {@code '2024-02-09 12:24:12.000'}) written into or cast
     * to a temporal type compares equal to a computed temporal value instead of lingering as its original
     * String. Only strings and already-temporal values are converted; anything else (numbers, bytes, …) and
     * any unrecognized {@code baseTypeUpper} passes through unchanged. An unparseable string throws, so
     * TRY_CAST sees the failure (and yields NULL).
     */
    public static Object toTemporalValue(final String baseTypeUpper, final Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String || value instanceof LocalDate
                || value instanceof LocalDateTime || value instanceof LocalTime)) {
            return value;
        }
        switch (baseTypeUpper) {
            case "DATE":
                return toLocalDate(value);
            case "TIME":
                return toLocalTime(value);
            case "TIMESTAMP":
            case "TIMESTAMP_NTZ":
            case "TIMESTAMP_LTZ":
            case "TIMESTAMP_TZ":
            // Snowflake's no-underscore spellings are full aliases of the TIMESTAMP_* variations.
            case "TIMESTAMPNTZ":
            case "TIMESTAMPLTZ":
            case "TIMESTAMPTZ":
            case "DATETIME":
                return toLocalDateTime(value);
            default:
                return value;
        }
    }

    /**
     * Interpret a value as a Unix-epoch instant, picking the unit by magnitude the way Snowflake does
     * for a STRING argument containing an integer: |v| &lt; 31,536,000,000 → seconds, else milliseconds,
     * else microseconds, else nanoseconds. Returns the wall-clock time at UTC. A NUMERIC TO_TIMESTAMP
     * argument must NOT go through this detection — it is always seconds (see
     * {@link #parseTimestampWithFormatOrScale}).
     */
    public static LocalDateTime epochToLocalDateTime(final long epoch) {
        final long magnitude = Math.abs(epoch);
        final Instant instant;
        if (magnitude < 31_536_000_000L) {
            instant = Instant.ofEpochSecond(epoch);
        } else if (magnitude < 31_536_000_000_000L) {
            instant = Instant.ofEpochMilli(epoch);
        } else if (magnitude < 31_536_000_000_000_000L) {
            instant = Instant.ofEpochSecond(epoch / 1_000_000L, (epoch % 1_000_000L) * 1_000L);
        } else {
            instant = Instant.ofEpochSecond(epoch / 1_000_000_000L, epoch % 1_000_000_000L);
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * Interpret a numeric TO_TIMESTAMP argument at an explicit scale: the value counts
     * 10<sup>-scale</sup>-second units since the Unix epoch (scale 0 = seconds — Snowflake's default
     * for a NUMERIC argument, however large — 3 = milliseconds, 9 = nanoseconds). Returns the
     * wall-clock time at UTC.
     */
    public static LocalDateTime epochAtScaleToLocalDateTime(final long epoch, final int scale) {
        if (scale < 0 || scale > 9) {
            throw new RuntimeException("Invalid TO_TIMESTAMP scale: " + scale + " (expected 0 to 9)");
        }
        long unitsPerSecond = 1L;
        for (int i = 0; i < scale; i++) {
            unitsPerSecond *= 10L;
        }
        final long seconds = Math.floorDiv(epoch, unitsPerSecond);
        final long nanos = Math.floorMod(epoch, unitsPerSecond) * (1_000_000_000L / unitsPerSecond);
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds, nanos), ZoneOffset.UTC);
    }

    /**
     * Parse the TO_TIMESTAMP* argument pair (value [, format-or-scale]) to a {@link LocalDateTime},
     * faithful to live Snowflake about the epoch unit: a NUMERIC value is ALWAYS a seconds epoch —
     * TO_TIMESTAMP_NTZ(1631711999000) is year 53676, not a millisecond epoch — unless the second
     * argument is a numeric scale (TO_TIMESTAMP_NTZ(1631711999000, 3) is 2021-09-15). Everything else
     * goes through {@link #parseTimestampWithFormat}, where a STRING of digits keeps the
     * magnitude-based unit detection.
     */
    public static LocalDateTime parseTimestampWithFormatOrScale(final Object value, final Object formatOrScale) {
        if (value instanceof Number) {
            final int scale = formatOrScale instanceof Number ? ((Number) formatOrScale).intValue() : 0;
            return epochAtScaleToLocalDateTime(((Number) value).longValue(), scale);
        }
        return parseTimestampWithFormat(value, formatOrScale != null ? formatOrScale.toString() : null);
    }

    /**
     * Parse a value to a {@link LocalDateTime} for TO_TIMESTAMP: a numeric value is a Unix epoch in
     * SECONDS (magnitude-based unit detection applies only to a string of digits — live:
     * TO_TIMESTAMP_NTZ(1631711999000) is year 53676 while TO_TIMESTAMP_NTZ('1631711999000') is
     * 2021-09-15); a string is parsed with the given Snowflake format when one is supplied, a string
     * of digits as a magnitude-detected epoch ({@link #epochToLocalDateTime}), and any other string
     * flexibly via {@link #toLocalDateTime}.
     */
    public static LocalDateTime parseTimestampWithFormat(final Object value, final String format) {
        if (value instanceof Number) {
            return epochAtScaleToLocalDateTime(((Number) value).longValue(), 0);
        }
        if (format != null && !format.isEmpty()) {
            final TemporalAccessor parsed = SnowflakeDateFormat.formatterFor(format).parse(value.toString().trim());
            try {
                return LocalDateTime.from(parsed);
            } catch (final DateTimeException e) {
                // A date-only format resolves no time-of-day: default to midnight.
                return LocalDate.from(parsed).atStartOfDay();
            }
        }
        final String text = value.toString().trim();
        if (isIntegerText(text)) {
            try {
                return epochToLocalDateTime(Long.parseLong(text));
            } catch (final NumberFormatException tooLarge) {
                throw new RuntimeException("Cannot parse date/time: " + text);
            }
        }
        return toLocalDateTime(value);
    }

    /** Whether the text is an (optionally signed) run of digits — a string epoch for TO_TIMESTAMP. */
    private static boolean isIntegerText(final String s) {
        final int start = s.startsWith("-") || s.startsWith("+") ? 1 : 0;
        if (start >= s.length()) {
            return false;
        }
        for (int i = start; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    /**
     * Parse a value to a {@link LocalDate} for TO_DATE: a numeric value is a Unix epoch (its date
     * part); a string is parsed with the given Snowflake format when one is supplied, else via
     * {@link #toLocalDate}.
     */
    public static LocalDate parseDateWithFormat(final Object value, final String format) {
        if (value instanceof Number) {
            return epochToLocalDateTime(((Number) value).longValue()).toLocalDate();
        }
        // An all-digit STRING is an epoch too, and unlike a numeric argument it is legal under every
        // spelling — live-verified on a real account: TO_DATE('1631711999') and
        // TO_DATE('1631711999','AUTO') are both 2021-09-15, while TO_DATE(1631711999) is rejected.
        final String digits = value.toString().trim();
        if (digits.matches("-?\\d+")) {
            return epochToLocalDateTime(Long.parseLong(digits)).toLocalDate();
        }
        if (format != null && !format.isEmpty()) {
            return LocalDate.from(SnowflakeDateFormat.formatterFor(format).parse(digits));
        }
        return toLocalDate(value);
    }

    public static long datePart(final String unitRaw, final LocalDateTime dt) {
        final String unit = stripPluralS(unitRaw.toUpperCase());
        switch (unit) {
            case "YEAR": case "Y": case "YY": case "YYYY": case "YR": return dt.getYear();
            case "QUARTER": case "Q": case "QTR":
                return (dt.getMonthValue() - 1) / 3 + 1;
            case "MONTH": case "MM": case "MON": case "MONS": return dt.getMonthValue();
            case "WEEK": case "WK": case "WEEKOFYEAR":
                return dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case "ISOWEEK": case "WEEKISO":
                return dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case "DAY": case "DD": case "D": case "DAYOFMONTH": return dt.getDayOfMonth();
            case "DAYOFWEEK": case "DOW": case "DW": return (long) dt.getDayOfWeek().getValue() % 7;
            case "DAYOFWEEKISO": case "ISODOW": return dt.getDayOfWeek().getValue();
            case "DAYOFYEAR": case "DOY": return dt.getDayOfYear();
            case "HOUR": case "H": case "HH": case "HR": return dt.getHour();
            case "MINUTE": case "MIN": case "MI": return dt.getMinute();
            case "SECOND": case "SEC": case "S": return dt.getSecond();
            case "MILLISECOND": case "MS": case "MSEC": return dt.getNano() / 1_000_000L;
            case "MICROSECOND": case "US": case "USEC": return dt.getNano() / 1_000L;
            case "NANOSECOND": case "NS": case "NSEC": return dt.getNano();
            case "EPOCH": case "EPOCH_SECOND": return dt.toEpochSecond(ZoneOffset.UTC);
            case "EPOCH_MILLISECOND": return dt.toEpochSecond(ZoneOffset.UTC) * 1000 + dt.getNano() / 1_000_000L;
            case "EPOCH_MICROSECOND": return dt.toEpochSecond(ZoneOffset.UTC) * 1_000_000L + dt.getNano() / 1_000L;
            case "EPOCH_NANOSECOND": return dt.toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L + dt.getNano();
            default: throw new RuntimeException("Unsupported date part: " + unitRaw);
        }
    }

    public static DayOfWeek parseDayOfWeek(final String name) {
        switch (name.toUpperCase().substring(0, Math.min(3, name.length()))) {
            case "MON": return DayOfWeek.MONDAY;
            case "TUE": return DayOfWeek.TUESDAY;
            case "WED": return DayOfWeek.WEDNESDAY;
            case "THU": return DayOfWeek.THURSDAY;
            case "FRI": return DayOfWeek.FRIDAY;
            case "SAT": return DayOfWeek.SATURDAY;
            case "SUN": return DayOfWeek.SUNDAY;
            default: throw new RuntimeException("Unknown day of week: " + name);
        }
    }
    /** {@code unit.toUpperCase()} with one trailing {@code S} removed — the plural strip without a
     *  per-call regex compile. */
    public static String stripPluralS(final String upper) {
        return upper.endsWith("S") ? upper.substring(0, upper.length() - 1) : upper;
    }

}
