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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import io.airlift.compress.snappy.SnappyCompressor;
import io.airlift.compress.snappy.SnappyDecompressor;

import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public final class SharedFunctionHelpers {

    private SharedFunctionHelpers() {}

    /**
     * Divide with Snowflake's result scale: {@code max(S1, min(S1 + 6, 12))} where S1 is the dividend's
     * scale (docs: "Arithmetic operators — division"), rounding half away from zero. Integer / integer
     * therefore yields six fractional digits — {@code 1/3 = 0.333333} — matching Snowflake output.
     * Runtime values carry no declared column scale, so S1 is read off the value with trailing zeros
     * stripped (a whole-number {@code Double} like {@code SUM} output renders as 1.0 but is scale 0).
     */
    public static BigDecimal divideWithSnowflakeScale(final BigDecimal dividend, final BigDecimal divisor) {
        final int dividendScale = Math.max(dividend.stripTrailingZeros().scale(), 0);
        final int resultScale = Math.max(dividendScale, Math.min(dividendScale + 6, 12));
        return dividend.divide(divisor, resultScale, RoundingMode.HALF_UP);
    }

    public static byte[] digest(final String algo, final byte[] input) {
        try {
            return MessageDigest.getInstance(algo).digest(input);
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException("Algorithm not available: " + algo);
        }
    }

    public static String toHex(final byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (final byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static byte[] toUtf8(final Object v) {
        return v.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static boolean isTruthy(final Object v) {
        if (v instanceof Boolean) return (Boolean) v;
        if (v instanceof Number) return ((Number) v).doubleValue() != 0;
        if (v instanceof String) {
            String s = ((String) v).trim().toUpperCase();
            return s.equals("TRUE") || s.equals("1");
        }
        return false;
    }

    public static String compressToBase64(final byte[] input, final String method) {
        try {
            byte[] compressed;
            String m = method.toLowerCase();
            switch (m) {
                case "deflate":
                case "raw_deflate": {
                    Deflater def = new Deflater(
                        Deflater.DEFAULT_COMPRESSION, m.equals("raw_deflate"));
                    def.setInput(input);
                    def.finish();
                    ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);
                    byte[] buf = new byte[1024];
                    while (!def.finished()) { int n = def.deflate(buf); bos.write(buf, 0, n); }
                    def.end();
                    compressed = bos.toByteArray();
                    break;
                }
                case "zlib": {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try (DeflaterOutputStream dos =
                             new DeflaterOutputStream(bos)) {
                        dos.write(input);
                    }
                    compressed = bos.toByteArray();
                    break;
                }
                case "gzip": {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try (GZIPOutputStream gos = new GZIPOutputStream(bos)) {
                        gos.write(input);
                    }
                    compressed = bos.toByteArray();
                    break;
                }
                case "snappy": {
                    final SnappyCompressor snappy = new SnappyCompressor();
                    final byte[] out = new byte[snappy.maxCompressedLength(input.length)];
                    final int n = snappy.compress(input, 0, input.length, out, 0, out.length);
                    compressed = Arrays.copyOf(out, n);
                    break;
                }
                default:
                    throw new RuntimeException("Unsupported compression method: " + method
                        + ". Supported: snappy, deflate, raw_deflate, zlib, gzip");
            }
            return Base64.getEncoder().encodeToString(compressed);
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("COMPRESS failed: " + e.getMessage());
        }
    }

    public static byte[] decompressFromBase64(final String b64Input, final String method) {
        try {
            byte[] compressed = Base64.getDecoder().decode(b64Input);
            String m = method.toLowerCase();
            switch (m) {
                case "deflate":
                case "raw_deflate": {
                    Inflater inf = new Inflater(m.equals("raw_deflate"));
                    inf.setInput(compressed);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[1024];
                    while (!inf.finished()) { int n = inf.inflate(buf); bos.write(buf, 0, n); }
                    inf.end();
                    return bos.toByteArray();
                }
                case "zlib": {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try (InflaterInputStream iis =
                             new InflaterInputStream(new ByteArrayInputStream(compressed))) {
                        byte[] buf = new byte[1024]; int n;
                        while ((n = iis.read(buf)) != -1) bos.write(buf, 0, n);
                    }
                    return bos.toByteArray();
                }
                case "gzip": {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    try (GZIPInputStream gis =
                             new GZIPInputStream(new ByteArrayInputStream(compressed))) {
                        byte[] buf = new byte[1024]; int n;
                        while ((n = gis.read(buf)) != -1) bos.write(buf, 0, n);
                    }
                    return bos.toByteArray();
                }
                case "snappy": {
                    final int length = SnappyDecompressor.getUncompressedLength(compressed, 0);
                    final byte[] out = new byte[length];
                    new SnappyDecompressor().decompress(compressed, 0, compressed.length, out, 0, length);
                    return out;
                }
                default:
                    throw new RuntimeException("Unsupported decompression method: " + method
                        + ". Supported: snappy, deflate, raw_deflate, zlib, gzip");
            }
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new RuntimeException("DECOMPRESS failed: " + e.getMessage());
        }
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
        String s = v.toString().trim();
        try { return LocalDate.parse(s); } catch (final Exception ignored) {}
        final LocalDate scanned = AutoTemporalParser.parseDate(s);
        if (scanned != null) return scanned;
        return LocalDate.parse(s);   // not a recognised form: surface java.time's own message
    }

    public static LocalDateTime toLocalDateTime(final Object v) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        if (v instanceof LocalDate) return ((LocalDate) v).atStartOfDay();
        String s = v.toString().trim();
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
     * Interpret a numeric value as a Unix-epoch instant, picking the unit by magnitude the way
     * Snowflake does: |v| &lt; 31,536,000,000 → seconds, else milliseconds, else microseconds, else
     * nanoseconds. Returns the wall-clock time at UTC.
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
     * Parse a value to a {@link LocalDateTime} for TO_TIMESTAMP: a numeric value is a Unix epoch (see
     * {@link #epochToLocalDateTime}); a string is parsed with the given Snowflake format when one is
     * supplied, otherwise flexibly via {@link #toLocalDateTime}.
     */
    public static LocalDateTime parseTimestampWithFormat(final Object value, final String format) {
        if (value instanceof Number) {
            return epochToLocalDateTime(((Number) value).longValue());
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
        return toLocalDateTime(value);
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
        if (format != null && !format.isEmpty()) {
            return LocalDate.from(SnowflakeDateFormat.formatterFor(format).parse(value.toString().trim()));
        }
        return toLocalDate(value);
    }

    public static long datePart(final String unitRaw, final LocalDateTime dt) {
        String unit = unitRaw.toUpperCase().replaceAll("S$", "");
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
}
