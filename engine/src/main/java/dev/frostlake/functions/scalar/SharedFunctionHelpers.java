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

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.SessionTimestampMapping;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.expressions.IntervalUnit;
import dev.frostlake.executor.expressions.RawOverflowKind;
import dev.frostlake.executor.expressions.RawRangeOverflow;
import dev.frostlake.values.BinaryValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
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
        final BigDecimal quotient = dividend.divide(divisor, resultScale, RoundingMode.HALF_UP);
        // The quotient's raw at the derived scale must fit the signed 128-bit carrier, for DIV0 and
        // DIV0NULL exactly as for the operator: DIV0(a, 0.1) over 38 nines is refused at
        // "(38,6){nullable}, value 1e+39" (live-verified); the refusal carries the derived type and
        // prints the quotient as a double.
        if (quotient.precision() > 38 && NumericRangeRefusal.outsideSb16Window(quotient.unscaledValue())) {
            throw new RawRangeOverflow(RawOverflowKind.QUOTIENT, quotient, resultScale);
        }
        return quotient;
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
        // ★ THE ZONED CARRIERS BELONG HERE TOO, and leaving them out was not a cosmetic gap: a value
        // that reaches this method and gets no literal falls through to a QUOTED STRING, so a
        // CURRENT_TIMESTAMP() carried in a scripting variable was spliced into SQL as
        // '2026-08-18T23:12:09.762222806Z' — text. Everything downstream then read a VARCHAR:
        // TO_VARCHAR(<that>, 'fmt') became "too many arguments … expected 1, got 2" (its second
        // argument is legal only over a temporal) and EXTRACT refused a VARCHAR(16777216) argument.
        // Both sentences named types, which is what said the value had lost its own.
        if (value instanceof OffsetDateTime) {
            return "'" + value + "'::TIMESTAMP_LTZ";
        }
        if (value instanceof ZonedDateTime) {
            return "'" + value + "'::TIMESTAMP_TZ";
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
        return yearText(dt.getYear()) + String.format("-%02d-%02d %02d:%02d:%02d.%03d",
            dt.getMonthValue(), dt.getDayOfMonth(),
            dt.getHour(), dt.getMinute(), dt.getSecond(), dt.getNano() / 1_000_000);
    }

    /**
     * A YEAR as the account prints it in every text of a date or a timestamp: the year of the era, four
     * digits at least and never a sign — {@code 20201} for a year past 9999, where java.time's own
     * rendering is {@code +20201}, and {@code 0002} for the second year before the first, where it is
     * {@code -0001} (live-verified over ::VARCHAR, TO_VARCHAR, {@code ||} and the driver's text).
     *
     * @param prolepticYear the ISO year, 0 being the first year before the first
     * @return the year's text
     */
    public static String yearText(final int prolepticYear) {
        return String.format("%04d", prolepticYear > 0 ? prolepticYear : 1 - prolepticYear);
    }

    /**
     * A DATE's text inside a VARIANT: {@link #textOf}'s, except that a year before the first keeps its
     * proleptic number, unpadded — live's VARIANT of the second year BC reads {@code "-1-01-01"} where its
     * ::VARCHAR reads {@code 0002-01-01}, and one past 9999 reads {@code "20201-01-15"} (live-verified).
     *
     * @param date the date
     * @return its text as a VARIANT string holds it
     */
    public static String variantDateText(final LocalDate date) {
        if (date.getYear() > 0) {
            return textOf(date);
        }
        return date.getYear() + String.format("-%02d-%02d", date.getMonthValue(), date.getDayOfMonth());
    }

    /**
     * A zone-carrying timestamp's default output text: the wall clock followed by its OFFSET. A zero
     * offset is spelled {@code Z}, never {@code +0000} — live prints {@code 2020-01-01 18:00:00.000 Z}
     * for a TIMESTAMP_LTZ read under UTC, and the same {@code Z} for a literal written with one.
     */
    public static String offsetTimestampText(final OffsetDateTime dt) {
        // Re-expressed at the CURRENT session zone rather than printed at the offset it was stored
        // with: an LTZ is an instant, so the same stored value must read 10:00 -0800 under
        // America/Los_Angeles and 18:00 Z under UTC. Only a TIMESTAMP_LTZ reaches here today; a
        // TIMESTAMP_TZ keeps the offset it was WRITTEN with and must NOT be re-expressed, so it needs
        // its own carrier before it can share this path.
        final OffsetDateTime here = dt.toInstant().atZone(SessionZone.current()).toOffsetDateTime();
        return timestampText(here.toLocalDateTime()) + " " + offsetText(here.getOffset());
    }

    /**
     * A TIMESTAMP_TZ's output text: the wall clock it was WRITTEN with, followed by the offset it was
     * written with. Unlike {@link #offsetTimestampText} this never re-expresses — that is the whole
     * difference between the two zoned types. Live: a value written {@code 10:00:00 +0300} reads back
     * {@code 2020-01-01 10:00:00.000 +0300} under America/Los_Angeles and, unchanged, under UTC, while
     * an LTZ holding the same instant follows the session.
     *
     * @param dt the value, whose zone is the fixed offset it carries
     * @return the rendering, a zero offset spelled {@code Z}
     */
    public static String writtenOffsetTimestampText(final ZonedDateTime dt) {
        return timestampText(dt.toLocalDateTime()) + " " + offsetText(dt.getOffset());
    }

    /** {@code Z} at zero, else {@code ±HHMM} — Snowflake's offset spelling. */
    public static String offsetText(final ZoneOffset offset) {
        final int seconds = offset.getTotalSeconds();
        if (seconds == 0) {
            return "Z";
        }
        final int absolute = Math.abs(seconds);
        return String.format("%s%02d%02d", seconds < 0 ? "-" : "+",
            absolute / 3600, absolute % 3600 / 60);
    }

    /** Snowflake's default TIME output text — {@code HH24:MI:SS}, no fractional part. */
    public static String timeText(final LocalTime t) {
        return String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
    }

    /**
     * A value rendered for a STRING context (::VARCHAR, TO_VARCHAR/TO_CHAR without a format, {@code ||},
     * embedding into a VARIANT): temporals use Snowflake's default output forms above, everything else its
     * ordinary text. A DATE is {@code YYYY-MM-DD} with its era year — see {@link #yearText}.
     */
    public static String textOf(final Object value) {
        if (value instanceof LocalDate) {
            final LocalDate date = (LocalDate) value;
            return yearText(date.getYear()) + String.format("-%02d-%02d", date.getMonthValue(),
                date.getDayOfMonth());
        }
        if (value instanceof ZonedDateTime) {
            return writtenOffsetTimestampText((ZonedDateTime) value);
        }
        if (value instanceof OffsetDateTime) {
            return offsetTimestampText((OffsetDateTime) value);
        }
        if (value instanceof LocalDateTime) {
            return timestampText((LocalDateTime) value);
        }
        if (value instanceof LocalTime) {
            return timeText((LocalTime) value);
        }
        if (value instanceof Double || value instanceof Float) {
            return floatText(((Number) value).doubleValue());
        }
        if (value instanceof BigDecimal) {
            // A NUMBER prints its digits in place — 0.00000001 and a scaled zero as 0.00000000000000000000
            // (live-verified) — never java.math's scientific 1E-8 / 0E-20, which BigDecimal.toString
            // switches to once the exponent falls below -6.
            return ((BigDecimal) value).toPlainString();
        }
        return String.valueOf(value);
    }

    /**
     * Snowflake's FLOAT-to-VARCHAR rendering. Java's shortest-round-trip {@code Double.toString} printed
     * {@code 0.8999999761581421} where Snowflake prints {@code 0.8999999762}, so every SHA2/checksum over
     * a stringified float diverged.
     *
     * <p>The WIDTH is not one number. Live-verified across a magnitude ladder, the significant digits are
     * ten below 10 and then one more per decade, capped at fourteen from 1e4 up:
     *
     * <pre>
     *   1.414213562        10 sig   (and every smaller magnitude, down to 1.414213562e-16)
     *   14.142135624       11       141.421356237     12       1414.213562373    13
     *   14142.135623731    14       10686474581524.5  15       250000000000000   15 …
     * </pre>
     *
     * <p>THE CAP IS FIFTEEN, AND A LADDER BUILT ON SQRT(2) CANNOT SEE IT: that value's fifteenth
     * significant digit is a zero, so its fourteen- and fifteen-digit renderings are the same text once
     * trailing zeros are stripped. EXP(30) is what separates them — {@code 10686474581524.5} is fifteen
     * significant digits where fourteen would have dropped the {@code .5}.
     *
     * <p>The notation follows from the width for free: {@code %g} turns scientific once the exponent
     * reaches the precision, and with fifteen digits from 1e5 up that lands the plain range on an
     * exponent of −4 through 14 — {@code 2.5::FLOAT * 1e14} stays {@code 250000000000000} and 1e15 is the
     * first to leave, both live-verified.
     */
    public static String floatText(final double d) {
        if (Double.isNaN(d)) {
            return "NaN";
        }
        if (Double.isInfinite(d)) {
            return d > 0 ? "inf" : "-inf";
        }
        final String formatted =
            String.format(Locale.ROOT, "%." + significantDigitsFor(decimalExponentOf(d)) + "g", d);
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


    /** How many significant digits a FLOAT of this magnitude is rendered with: ten, then one per decade to fifteen. */
    private static int significantDigitsFor(final int exponent) {
        return Math.min(15, Math.max(10, 10 + exponent));
    }

    /** The decimal exponent of a value — {@code floor(log10(|d|))} — with zero treated as 1e0. */
    private static int decimalExponentOf(final double d) {
        if (d == 0.0) {
            return 0;
        }
        final BigDecimal exact = new BigDecimal(Math.abs(d));
        return exact.precision() - exact.scale() - 1;
    }
    // --- shared date/datetime parsing helpers ---
    public static LocalDate toLocalDate(final Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate) return (LocalDate) v;
        // A TIMESTAMP_TZ truncates to the date its OWN wall clock names, never to the UTC one.
        if (v instanceof ZonedDateTime) return ((ZonedDateTime) v).toLocalDate();
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).toLocalDate();
        final String s = v.toString().trim();
        try { return LocalDate.parse(s); } catch (final Exception ignored) {}
        final LocalDateTime epoch = digitEpochAtUtc(s);
        if (epoch != null) return epoch.toLocalDate();
        final LocalDate scanned = AutoTemporalParser.parseDate(s);
        if (scanned != null) return scanned;
        // Live names the TYPE it could not read the text as — "Date '...' is not recognized" —
        // where java.time's own message says "Text '...' could not be parsed".
        throw new RuntimeException("Date '" + s + "' is not recognized");
    }

    public static LocalDateTime toLocalDateTime(final Object v) {
        return toLocalDateTime(v, false);
    }

    /**
     * The same reader, told whether the target CARRIES a zone. A TIMESTAMP_TZ or _LTZ moves the
     * instant by the offset the text gives; a TIMESTAMP_NTZ or a DATE keeps the wall clock and drops
     * it, which is what live does — {@code '2020-01-01 10:00:00 +0300'::TIMESTAMP_NTZ} is ten o'clock
     * there, and the same text as a TIMESTAMP_TZ is seven o'clock UTC.
     *
     * <p>Frostlake used to do neither: {@code +0300} would not parse at all, and {@code +03:00} parsed
     * with the offset silently DISCARDED — so the value read back as a different instant, which is
     * worse than the refusal beside it.
     *
     * <p>The offset has nowhere to be KEPT while these are held as a naive LocalDateTime, so a
     * TIMESTAMP_TZ is normalised to UTC rather than remembering that it was written {@code +0300}.
     * Rendering it back the way live does is a separate piece of work.
     */
    public static LocalDateTime toLocalDateTime(final Object v, final boolean targetCarriesZone) {
        if (v == null) return null;
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        // A TIMESTAMP_LTZ arrives with its offset ALREADY applied, so its wall clock is the answer —
        // re-applying the offset here would move the instant a second time.
        if (v instanceof OffsetDateTime) return ((OffsetDateTime) v).toLocalDateTime();
        // ★ A TIMESTAMP_TZ cast to a TIMESTAMP_NTZ TRUNCATES rather than converting: live gives
        // 10:00:00 for '2020-01-01 10:00:00 +0300'::TIMESTAMP_TZ::TIMESTAMP_NTZ, keeping the digits.
        // The cast to a TIMESTAMP_LTZ beside it CONVERTS (23:00 -0800), so the two must not share a
        // helper — that one goes through toSessionOffsetDateTime.
        if (v instanceof ZonedDateTime) return ((ZonedDateTime) v).toLocalDateTime();
        if (v instanceof LocalDate) return ((LocalDate) v).atStartOfDay();
        final String s = v.toString().trim();
        try { return LocalDateTime.parse(s); } catch (final Exception ignored) {}
        final LocalDateTime epoch = digitEpochAtUtc(s);
        if (epoch != null) return epoch;
        // Snowflake's AUTO input detection covers far more than ISO-8601: a zone designator, MM/DD/YYYY,
        // DD-MON-YYYY, whitespace variation and non-canonical digit counts (see AutoTemporalParser).
        final int[] offset = new int[1];
        final LocalDateTime scanned = AutoTemporalParser.parseDateTime(s, offset);
        if (scanned != null) {
            return targetCarriesZone && offset[0] != AutoTemporalParser.NO_OFFSET
                ? scanned.minusMinutes(offset[0]) : scanned;
        }
        try { return LocalDate.parse(s).atStartOfDay(); } catch (final Exception ignored) {}
        throw new RuntimeException("Timestamp '" + s + "' is not recognized");
    }

    /**
     * A TIMESTAMP_LTZ value: the INSTANT the input names, expressed at the SESSION zone's offset for
     * that instant. Live proves it is an instant and not a wall clock, because the same stored value
     * re-renders when the session zone changes — {@code 2020-01-01 10:00:00 -0800} under
     * America/Los_Angeles is {@code 2020-01-01 18:00:00 Z} under UTC, digits and all.
     *
     * <p>Text WITHOUT an offset names a wall clock in the session's zone; text WITH one already fixes
     * the instant and is merely re-expressed. The offset is the zone's AT THAT INSTANT, so a January
     * value and a June value in the same zone carry different ones.
     */
    public static OffsetDateTime toSessionOffsetDateTime(final Object v) {
        if (v == null) {
            return null;
        }
        final ZoneId zone = SessionZone.current();
        // A TIMESTAMP_TZ is an instant like any other here, so becoming an LTZ re-expresses it in the
        // session's zone — the converting half of the pair the truncating cast above completes.
        if (v instanceof ZonedDateTime) {
            return ((ZonedDateTime) v).toInstant().atZone(zone).toOffsetDateTime();
        }
        if (v instanceof OffsetDateTime) {
            return ((OffsetDateTime) v).toInstant().atZone(zone).toOffsetDateTime();
        }
        if (v instanceof LocalDateTime) {
            return ((LocalDateTime) v).atZone(zone).toOffsetDateTime();
        }
        if (v instanceof LocalDate) {
            return ((LocalDate) v).atStartOfDay(zone).toOffsetDateTime();
        }
        final String s = v.toString().trim();
        final LocalDateTime epoch = digitEpochAtUtc(s);
        if (epoch != null) {
            // An epoch already fixes the instant; the session's zone only expresses it.
            return epoch.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toOffsetDateTime();
        }
        final int[] offset = new int[1];
        final LocalDateTime scanned = AutoTemporalParser.parseDateTime(s, offset);
        if (scanned == null) {
            throw new RuntimeException("Timestamp '" + s + "' is not recognized");
        }
        if (offset[0] == AutoTemporalParser.NO_OFFSET) {
            return scanned.atZone(zone).toOffsetDateTime();
        }
        return scanned.toInstant(ZoneOffset.ofTotalSeconds(offset[0] * 60))
            .atZone(zone).toOffsetDateTime();
    }

    /**
     * A TIMESTAMP_TZ value: the instant the input names, kept at the offset it was WRITTEN with.
     *
     * <p>THIS IS WHAT SEPARATES THE TWO ZONED TYPES. An LTZ is an instant that re-renders in whatever
     * zone the session currently has; a TZ remembers how it was spelled and never moves. Both agree on
     * the instant underneath — {@code 10:00 +0300} really is {@code 07:00} UTC, and comparisons,
     * ordering and EPOCH all read it that way — so the offset is presentation, and only presentation.
     *
     * <p>Text carrying NO offset takes the session zone's offset AT THAT INSTANT, and then that offset
     * is fixed: the same literal cast under America/Los_Angeles reads {@code -0800} and under UTC reads
     * {@code Z}, each staying put afterwards. The value is held as a {@link ZonedDateTime} whose zone IS
     * that offset, which is what distinguishes it from the {@link OffsetDateTime} an LTZ carries.
     *
     * @param v the text, or an already-temporal value to re-flavour
     * @return the TZ value, or null for null input
     */
    public static ZonedDateTime toWrittenOffsetTimestamp(final Object v) {
        if (v == null) {
            return null;
        }
        final ZoneId zone = SessionZone.current();
        if (v instanceof ZonedDateTime) {
            return (ZonedDateTime) v;
        }
        if (v instanceof OffsetDateTime) {
            return ((OffsetDateTime) v).toZonedDateTime().withFixedOffsetZone();
        }
        if (v instanceof LocalDateTime) {
            return ((LocalDateTime) v).atZone(zone).withFixedOffsetZone();
        }
        if (v instanceof LocalDate) {
            return ((LocalDate) v).atStartOfDay(zone).withFixedOffsetZone();
        }
        final String s = v.toString().trim();
        final LocalDateTime epoch = digitEpochAtUtc(s);
        if (epoch != null) {
            // An epoch written as TEXT stays at UTC whatever the session's zone, where the NUMBER
            // beside it takes the session's offset: '1579046400' is 00:00 Z, 1579046400 is 16:00 -0800
            // under America/Los_Angeles.
            return epoch.atZone(ZoneOffset.UTC);
        }
        final int[] offset = new int[1];
        final LocalDateTime scanned = AutoTemporalParser.parseDateTime(s, offset);
        if (scanned == null) {
            throw new RuntimeException("Timestamp '" + s + "' is not recognized");
        }
        if (offset[0] == AutoTemporalParser.NO_OFFSET) {
            return scanned.atZone(zone).withFixedOffsetZone();
        }
        return scanned.atOffset(ZoneOffset.ofTotalSeconds(offset[0] * 60)).toZonedDateTime();
    }

    /**
     * A computed timestamp given back in the FLAVOUR of the value it was computed from: an LTZ input
     * yields an LTZ result, so {@code DATEADD(day, 1, tl)} keeps its offset instead of decaying to a
     * TIMESTAMP_NTZ, and a TZ input keeps the offset it was written with. The wall clock is what the
     * caller computed; only the flavour is restored.
     */
    public static Object sameTimestampFlavour(final Object source, final LocalDateTime computed) {
        if (computed == null) {
            return computed;
        }
        if (source instanceof ZonedDateTime) {
            return computed.atZone(((ZonedDateTime) source).getOffset());
        }
        if (!(source instanceof OffsetDateTime)) {
            return computed;
        }
        return computed.atZone(SessionZone.current()).toOffsetDateTime();
    }

    public static LocalTime toLocalTime(final Object v) {
        if (v == null) return null;
        if (v instanceof LocalTime) return (LocalTime) v;
        // As for a DATE: the TZ's own wall clock, so 10:00 +0300 casts to 10:00 and not to 07:00.
        if (v instanceof ZonedDateTime) return ((ZonedDateTime) v).toLocalTime();
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).toLocalTime();
        final String s = v.toString().trim();
        try { return LocalTime.parse(s); } catch (final Exception ignored) {}
        try { return LocalTime.parse(s, DateTimeFormatter.ofPattern("HH:mm")); } catch (final Exception ignored) {}
        // Live names the TYPE it could not read the text as, exactly as the DATE and TIMESTAMP
        // readers do: 'abc'::TIME and TO_TIME('abc') are both "Time 'abc' is not recognized".
        throw new RuntimeException("Time '" + s + "' is not recognized");
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
    /**
     * A temporal value truncated to a declared fractional precision — the digits past it are
     * dropped, never rounded — for the TIME and TIMESTAMP flavours alike. A DATE, a precision of nine
     * (or none) and any non-temporal value pass through untouched.
     *
     * @param value the temporal value, in any of the Java carriers
     * @param precision the declared fractional-second digits, 0 to 9
     * @return the value at that precision
     */
    public static Object atDeclaredPrecision(final Object value, final int precision) {
        if (precision < 0 || precision >= 9) {
            return value;
        }
        final int unit = (int) Math.pow(10, 9 - precision);
        if (value instanceof LocalDateTime) {
            final LocalDateTime at = (LocalDateTime) value;
            return at.withNano(at.getNano() - at.getNano() % unit);
        }
        if (value instanceof LocalTime) {
            final LocalTime at = (LocalTime) value;
            return at.withNano(at.getNano() - at.getNano() % unit);
        }
        if (value instanceof OffsetDateTime) {
            final OffsetDateTime at = (OffsetDateTime) value;
            return at.withNano(at.getNano() - at.getNano() % unit);
        }
        if (value instanceof ZonedDateTime) {
            final ZonedDateTime at = (ZonedDateTime) value;
            return at.withNano(at.getNano() - at.getNano() % unit);
        }
        return value;
    }

    public static Object toTemporalValue(final String baseTypeUpper, final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return numericEpochTemporal(baseTypeUpper, (Number) value);
        }
        if (!(value instanceof String || value instanceof LocalDate
                || value instanceof LocalDateTime || value instanceof LocalTime
                || value instanceof OffsetDateTime || value instanceof ZonedDateTime)) {
            return value;
        }
        switch (baseTypeUpper) {
            case "DATE":
                return toLocalDate(value);
            case "TIME":
                return toLocalTime(value);
            case "TIMESTAMP_LTZ":
            case "TIMESTAMPLTZ":
                // An LTZ is an INSTANT shown in the session's zone, so it keeps an offset.
                return toSessionOffsetDateTime(value);
            case "TIMESTAMP_TZ":
            case "TIMESTAMPTZ":
                // A TZ carries a zone too, and REMEMBERS the offset it was written with rather than
                // normalising to UTC — see toWrittenOffsetTimestamp for why that is its own carrier.
                return toWrittenOffsetTimestamp(value);
            case "TIMESTAMP":
                // The bare word follows the session's mapping, so under a zoned mapping the VALUE takes
                // that flavour's carrier too — a column that declares LTZ and holds a naive wall clock,
                // or declares TZ and forgets the offset it was written with, is exactly the mismatch
                // this resolution exists to avoid.
                if ("TIMESTAMP_TZ".equals(SessionTimestampMapping.current())) {
                    return toWrittenOffsetTimestamp(value);
                }
                return SessionTimestampMapping.isZoned()
                    ? toSessionOffsetDateTime(value) : toLocalDateTime(value);
            case "TIMESTAMP_NTZ":
            // Snowflake's no-underscore spellings are full aliases of the TIMESTAMP_* variations.
            case "TIMESTAMPNTZ":
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
    public static LocalDateTime epochDecimalToLocalDateTime(final BigDecimal seconds) {
        return LocalDateTime.ofInstant(epochDecimalInstant(seconds), ZoneOffset.UTC);
    }

    /**
     * The instant {@code seconds} since the Unix epoch names, whatever its magnitude — a NUMBER is
     * never unit-detected the way a string of digits is — with the fraction kept to the nanosecond
     * and anything finer truncated; a negative fraction counts back from the whole second below it
     * ({@code -1.5} is 23:59:58.5 of the day before the epoch, live-verified).
     */
    private static Instant epochDecimalInstant(final BigDecimal seconds) {
        final BigDecimal whole = seconds.setScale(0, RoundingMode.FLOOR);
        final long nanos = seconds.subtract(whole).movePointRight(9).longValue();
        return Instant.ofEpochSecond(whole.longValueExact(), nanos);
    }

    /** The exact decimal a number spells: a BigDecimal as it is, a double by its own text. */
    private static BigDecimal exactNumber(final Number value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Double || value instanceof Float) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.valueOf(value.longValue());
    }

    /**
     * A NUMBER cast to a TIMESTAMP flavour is an epoch in SECONDS whatever its magnitude, its fraction
     * kept — {@code 1.5::TIMESTAMP_NTZ} is 1970-01-01 00:00:01.500000000 and
     * {@code 31536000000::TIMESTAMP_NTZ} is the year 2969 on the account, where only a STRING of
     * digits is unit-detected by magnitude — and the cast's declared precision never shortens the
     * value. An LTZ or a TZ is the same instant expressed in the session's zone. A DATE or a TIME
     * target has no numeric conversion at all; the number is handed back for the static rule that
     * refuses the cast.
     */
    private static Object numericEpochTemporal(final String baseTypeUpper, final Number value) {
        switch (baseTypeUpper) {
            case "TIMESTAMP_LTZ":
            case "TIMESTAMPLTZ":
                return epochDecimalInstant(exactNumber(value)).atZone(SessionZone.current()).toOffsetDateTime();
            case "TIMESTAMP_TZ":
            case "TIMESTAMPTZ":
                return epochDecimalInstant(exactNumber(value)).atZone(SessionZone.current())
                    .withFixedOffsetZone();
            case "TIMESTAMP":
                if ("TIMESTAMP_TZ".equals(SessionTimestampMapping.current())) {
                    return epochDecimalInstant(exactNumber(value)).atZone(SessionZone.current())
                        .withFixedOffsetZone();
                }
                return SessionTimestampMapping.isZoned()
                    ? epochDecimalInstant(exactNumber(value)).atZone(SessionZone.current()).toOffsetDateTime()
                    : epochDecimalToLocalDateTime(exactNumber(value));
            case "TIMESTAMP_NTZ":
            case "TIMESTAMPNTZ":
            case "DATETIME":
                return epochDecimalToLocalDateTime(exactNumber(value));
            default:
                return value;
        }
    }

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
    /**
     * Whether a TO_DATE / TO_TIME / TO_TIMESTAMP format argument names an explicit model: anything but
     * the word AUTO, in any case — the empty string included, which is a model that matches nothing,
     * so every input is refused under it (live-verified: {@code TO_DATE('2020-01-15', '')} is "Can't
     * parse '2020-01-15' as date with format ''").
     */
    public static boolean isExplicitFormat(final String format) {
        return format != null && !"AUTO".equalsIgnoreCase(format);
    }

    public static LocalDateTime parseTimestampWithFormatOrScale(final Object value, final Object formatOrScale) {
        if (value instanceof Number) {
            final int scale = formatOrScale instanceof Number ? ((Number) formatOrScale).intValue() : 0;
            if (scale < 0 || scale > 9) {
                throw new RuntimeException("Invalid TO_TIMESTAMP scale: " + scale + " (expected 0 to 9)");
            }
            // The fraction travels: TO_TIMESTAMP(1.5) is 00:00:01.500000000 and TO_TIMESTAMP(1.5, 3)
            // is 00:00:00.001500000 on the account, the digits past the ninth truncated.
            return epochDecimalToLocalDateTime(exactNumber((Number) value).movePointLeft(scale));
        }
        return parseTimestampWithFormat(value, formatOrScale != null ? formatOrScale.toString() : null);
    }

    /**
     * Parse a value to a {@link LocalDateTime} for TO_TIMESTAMP: a numeric value is a Unix epoch in
     * SECONDS (magnitude-based unit detection applies only to a string of digits — live:
     * TO_TIMESTAMP_NTZ(1631711999000) is year 53676 while TO_TIMESTAMP_NTZ('1631711999000') is
     * 2021-09-15); a string is read against an explicit Snowflake format when one is supplied — a
     * mismatch is the account's own refusal, "Can't parse '&lt;input&gt;' as timestamp with format
     * '&lt;format&gt;'" — under AUTO a string of digits is a magnitude-detected epoch
     * ({@link #epochToLocalDateTime}), and any other string is read flexibly via
     * {@link #toLocalDateTime}. An offset the model reads is dropped: this is the NTZ wall clock.
     */
    public static LocalDateTime parseTimestampWithFormat(final Object value, final String format) {
        if (value instanceof Number) {
            return epochDecimalToLocalDateTime(exactNumber((Number) value));
        }
        if (isExplicitFormat(format)) {
            return LocalDateTime.from(SnowflakeDateParser.parse(value.toString(), format, "timestamp"));
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

    /**
     * The UTC wall clock a string of digits names as an epoch, its unit picked by magnitude (see
     * {@link #epochToLocalDateTime}), or null when the text is no signed run of digits that fits a long.
     * Live reads such text this way for EVERY temporal target, a DATE included: {@code
     * '1579046400'::TIMESTAMP_NTZ} is 2020-01-15 00:00:00 and {@code '20200115'::DATE} is 1970-08-22,
     * twenty million seconds and never a date written without separators.
     */
    private static LocalDateTime digitEpochAtUtc(final String s) {
        if (!isIntegerText(s)) {
            return null;
        }
        try {
            return epochToLocalDateTime(Long.parseLong(s));
        } catch (final NumberFormatException tooLarge) {
            return null;
        }
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
     * part); a string is read against an explicit Snowflake format when one is supplied (a mismatch
     * is "Can't parse '&lt;input&gt;' as date with format '&lt;format&gt;'"), else via
     * {@link #toLocalDate}.
     */
    public static LocalDate parseDateWithFormat(final Object value, final String format) {
        if (value instanceof Number) {
            return epochToLocalDateTime(((Number) value).longValue()).toLocalDate();
        }
        // An all-digit STRING is an epoch too, and unlike a numeric argument it is legal under every
        // spelling — live-verified on a real account: TO_DATE('1631711999') and
        // TO_DATE('1631711999','AUTO') are both 2021-09-15, while TO_DATE(1631711999) is rejected.
        // An explicit model reads a string of digits as the model says — TO_DATE('20200115',
        // 'YYYYMMDD') is 2020-01-15 — so it is asked before the epoch reading.
        if (isExplicitFormat(format)) {
            return LocalDate.from(SnowflakeDateParser.parse(value.toString(), format, "date"));
        }
        final String digits = value.toString().trim();
        if (digits.matches("-?\\d+")) {
            return epochToLocalDateTime(Long.parseLong(digits)).toLocalDate();
        }
        return toLocalDate(value);
    }

    /**
     * A date/time COMPONENT of a timestamp. The vocabulary is not the one the interval functions take:
     * DATE_PART reads a NANOSECOND but refuses a MILLISECOND or a MICROSECOND — every spelling of them
     * — where DATEADD takes all three, and it reads {@code woy} where DATEADD refuses it. So the word
     * is canonicalised through the shared table and then checked against THIS function's own list.
     */
    /**
     * A component of a TIME. A time of day carries no date, so only the clock components are readable;
     * the day-or-larger units are refused before this is reached (they cannot be decided from a value,
     * because a refusal raised here would never fire over zero rows).
     *
     * @param unitRaw the component as written
     * @param time the time of day
     * @return the component's value
     */
    public static long datePart(final String unitRaw, final LocalTime time) {
        return datePart(unitRaw, time.atDate(LocalDate.of(1970, 1, 1)));
    }

    public static long datePart(final String unitRaw, final LocalDateTime dt) {
        return datePart(unitRaw, dt, "DATE_PART");
    }

    /**
     * {@link #datePart(String, LocalDateTime, String)} given the ORIGINAL value as well as its wall
     * clock, which two families of component need: the EPOCH ones read the instant, and the TIMEZONE
     * ones read the offset itself.
     *
     * @param unitRaw the component as written
     * @param dt the wall clock
     * @param source the value the wall clock came from, or null when there is none to consult
     * @param functionName the function to name in a refusal
     * @return the component's value
     */
    public static long datePart(final String unitRaw, final LocalDateTime dt,
                                final Object source, final String functionName) {
        final String unit = canonicalDateUnit(unitRaw);
        final Integer offsetSeconds = zoneComponentSeconds(source);
        if (offsetSeconds != null && ("TZH".equals(unit) || "TIMEZONE_HOUR".equals(unit))) {
            return offsetSeconds / 3600;
        }
        if (offsetSeconds != null && ("TZM".equals(unit) || "TIMEZONE_MINUTE".equals(unit))) {
            return offsetSeconds % 3600 / 60;
        }
        // ★ ONLY THE EPOCH COMPONENTS READ THE INSTANT. Every other one is a component of the wall
        // clock AS WRITTEN, so shifting the clock for all of them made a TIMESTAMP_TZ written
        // 10:00 +0530 report hour 4 and minute 30 — its UTC digits — where live reports 10 and 0.
        if (offsetSeconds != null && offsetSeconds.intValue() != 0 && unit.startsWith("EPOCH")) {
            return datePart(unitRaw, dt.minusSeconds(offsetSeconds.intValue()), functionName);
        }
        return datePart(unitRaw, dt, functionName);
    }

    /**
     * The offset in seconds that TZH / TZM report for this value, or null when the value HAS no zone
     * component and the words are refused instead.
     *
     * <p>The distinction is by KIND, not by whether an offset happens to be present: a TIMESTAMP_NTZ
     * answers zero (live: {@code DATE_PART('TZH', ntz)} is 0) while a DATE answers nothing at all and
     * gets the invalid-component refusal, which both engines already agree on. A TIMESTAMP_LTZ reports
     * the session's offset and a TIMESTAMP_TZ the one it was written with — 5 and 30 for {@code +0530}.
     *
     * <p>The EPOCH components read the same offset, for a different reason: they name the INSTANT, and
     * a wall clock plus its offset is how the instant is reached from here.
     *
     * @param source the value the component was asked of
     * @return the offset in seconds, or null when the value carries no zone component
     */
    private static Integer zoneComponentSeconds(final Object source) {
        if (source instanceof ZonedDateTime) {
            return ((ZonedDateTime) source).getOffset().getTotalSeconds();
        }
        if (source instanceof OffsetDateTime) {
            return ((OffsetDateTime) source).getOffset().getTotalSeconds();
        }
        if (source instanceof LocalDateTime || source instanceof String) {
            return 0;
        }
        return null;
    }

    /**
     * A component of a timestamp, reported for {@code functionName} when the word names none.
     *
     * <p>EXTRACT and DATE_PART share ONE vocabulary — measured word by word down the whole
     * abbreviation ladder, and identical at every rung, which is not what either function's own
     * history suggested. What differs is only the sentence: each names ITSELF as the parameter, and
     * the word is quoted exactly as it arrived. DATE_PART's bareword is upper-cased before it gets
     * here (its argument is a unit SLOT), so DATE_PART(ss, …) reports [SS] while DATE_PART('ss', …)
     * and EXTRACT(ss FROM …) both report [ss].
     */
    public static long datePart(final String unitRaw, final LocalDateTime dt, final String functionName) {
        final String unit = canonicalDateUnit(unitRaw);
        if ("MILLISECOND".equals(unit) || "MICROSECOND".equals(unit)) {
            throw invalidDatePart(unitRaw, functionName);
        }
        switch (unit) {
            case "YEAR": case "Y": case "YY": case "YYY": case "YYYY": case "YR": return dt.getYear();
            case "YEAROFWEEK": case "YEAROFWEEKISO": return dt.get(IsoFields.WEEK_BASED_YEAR);
            case "QUARTER": case "Q": case "QTR":
                return (dt.getMonthValue() - 1) / 3 + 1;
            case "MONTH": case "MM": case "MON": case "MONS": return dt.getMonthValue();
            case "WEEK": case "W": case "WY": case "WOY": case "WEEKOFYEAR": case "WEEKISO":
                return dt.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            case "DAY": case "DD": case "D": case "DAYOFMONTH": return dt.getDayOfMonth();
            case "DAYOFWEEK": case "DOW": case "DW": return (long) dt.getDayOfWeek().getValue() % 7;
            case "DAYOFWEEKISO": return dt.getDayOfWeek().getValue();
            case "DAYOFYEAR": case "DOY": return dt.getDayOfYear();
            case "HOUR": case "H": case "HH": case "HR": return dt.getHour();
            case "MINUTE": case "MIN": case "MI": return dt.getMinute();
            case "SECOND": case "SEC": case "S": return dt.getSecond();
            case "NANOSECOND": return dt.getNano();
            case "EPOCH": case "EPOCH_SECOND": return dt.toEpochSecond(ZoneOffset.UTC);
            case "EPOCH_MILLISECOND": return dt.toEpochSecond(ZoneOffset.UTC) * 1000 + dt.getNano() / 1_000_000L;
            case "EPOCH_MICROSECOND": return dt.toEpochSecond(ZoneOffset.UTC) * 1_000_000L + dt.getNano() / 1_000L;
            case "EPOCH_NANOSECOND": return dt.toEpochSecond(ZoneOffset.UTC) * 1_000_000_000L + dt.getNano();
            // A timestamp with no zone reads both as zero, which is what live answers for a
            // TIMESTAMP_NTZ — the words are components of the value, not of the session.
            case "TIMEZONE_HOUR": case "TIMEZONE_MINUTE": return 0;
            default: throw invalidDatePart(unitRaw, functionName);
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

    /**
     * A date/time unit word reduced to its full name, so a function can switch on one spelling instead
     * of the dozens the account accepts: {@code dd}, {@code d} and {@code dayofmonth} all arrive as
     * DAY, {@code hh} and {@code hr} as HOUR, {@code ms} and {@code msec} as MILLISECOND.
     *
     * <p>The alias is resolved BEFORE any plural is stripped, and that order is load-bearing:
     * {@link #stripPluralS} is blind, so it would turn {@code MS} into {@code M} — milliseconds into
     * minutes — and {@code US} and {@code NS} into words that mean nothing at all. Stripping is left
     * as the fallback for the spellings the table does not carry, which is where it was doing its work
     * before.
     *
     * @param raw the unit as written, in any case
     * @return the unit's full name, or the word as written when it names no interval unit
     */
    /** The bad-component refusal EXTRACT and DATE_PART share, each naming itself as the parameter. */
    public static RuntimeException invalidDatePart(final String unitRaw, final String functionName) {
        return new RuntimeException("SQL compilation error:\ninvalid value [" + unitRaw
            + "] for parameter '" + functionName + " date/time part'");
    }

    /**
     * The refusal for a word that is not a date/time component of the function that was given it. The
     * account quotes the word as the CALL wrote it — a bareword arrives already upper-cased, a quoted
     * literal keeps its own case — so the text is passed through rather than folded here.
     *
     * @param raw the unit word as it reached the function
     * @param functionName the function reporting it
     * @return the refusal to throw
     */
    public static RuntimeException notADateTimeComponent(final Object raw, final String functionName) {
        return new RuntimeException("SQL compilation error: ['" + raw
            + "'] is not a valid date/time component for function " + functionName + ".");
    }

    /**
     * The refusal for a unit that is not a component of the VALUE's type — a day or larger asked of a
     * TIME, which carries no date to truncate. The account spells this one differently from the
     * unit-not-known refusal beside it: the word is bracketed WITHOUT quotes, upper-cased whatever the
     * call wrote, and the sentence names the type as well as the function.
     *
     * @param raw the unit word as written
     * @param functionName the function reporting it
     * @param typeName the argument's type
     * @return the refusal to throw
     */
    public static RuntimeException notAComponentOfType(final Object raw, final String functionName,
                                                       final String typeName) {
        return new RuntimeException("SQL compilation error: [" + String.valueOf(raw).toUpperCase(Locale.ROOT)
            + "] is not a valid date/time component for function " + functionName
            + " and type " + typeName + ".");
    }

    /**
     * Whether a word names a date COMPONENT that no interval can be measured in. {@code woy} and
     * {@code weekofyear} are units for INTERVAL and readable by DATE_PART, but DATEADD, DATEDIFF and
     * DATE_TRUNC all refuse them — the one place the two vocabularies disagree.
     *
     * @param raw the unit word as written
     * @return true when an interval function must refuse it
     */
    public static boolean isComponentOnlyUnit(final Object raw) {
        final String word = String.valueOf(raw).toUpperCase(Locale.ROOT);
        return "WOY".equals(word) || "WEEKOFYEAR".equals(word);
    }

    public static String canonicalDateUnit(final Object raw) {
        final String word = String.valueOf(raw).toUpperCase(Locale.ROOT);
        final IntervalUnit named = IntervalUnit.fromSpelling(word);
        if (named != null) {
            return named.name();
        }
        // The strip is a FALLBACK for plurals the table does not carry (nsecs, msecs), and it is
        // blind — so it is held to words of three characters or more. Without that guard it invents
        // aliases live does not have: SS would become S and read as SECOND, WKS would become WK and
        // read as WEEK, and both are refused on a real account.
        final String singular = stripPluralS(word);
        if (singular.length() < 3) {
            return word;
        }
        final IntervalUnit afterPlural = IntervalUnit.fromSpelling(singular);
        return afterPlural != null ? afterPlural.name() : singular;
    }

}
