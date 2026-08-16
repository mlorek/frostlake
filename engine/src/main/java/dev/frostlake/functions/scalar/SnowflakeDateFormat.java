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

import dev.frostlake.executor.SessionZone;
import dev.frostlake.values.TemporalText;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Formats a temporal value with a subset of Snowflake's date/time format model (TO_CHAR / TO_VARCHAR
 * with a date format). Snowflake elements are translated to a {@link DateTimeFormatter} pattern:
 * YYYY/YY/Y, MMMM/MON/MM, DD/DY/D, HH24/HH12/HH/H, MI, SS, FF[1-9], AM/PM, TZH/TZM pairs, TZD. The scan is greedy
 * longest-match at each position; any UNMATCHED character is emitted as a quoted literal, so
 * separators (-, /, :, spaces) pass through unchanged and unsupported elements degrade to literal
 * text rather than throwing. Snowflake has NO full-name 'DAY'/'MONTH' elements (live-verified on
 * DATE '2020-01-15'): 'DAY' tokenizes as D+A+Y — unpadded day-of-month, literal A, 2-digit year —
 * giving "15A20", and 'MONTH' as MON+T+H — "Jan", literal T, unpadded hour — giving "JanT0".
 * Month/day names use the English (US) locale in title case (e.g. "Jan", "January", "Wed"); the
 * format element's own case is not propagated to the output. TZH/TZM render the NTZ zero offset
 * ("Z" for the pair). Not modeled: HH is treated as 24-hour, and era / quarter / week elements.
 */
public final class SnowflakeDateFormat {

    private SnowflakeDateFormat() {
    }

    // Ordered longest-first so the greedy scan matches MMMM before MON before MM, HH24 before HH
    // before H, DD/DY before D, YYYY before YY before Y, etc.
    private static final String[][] TOKENS = {
        // The offset elements, which render the value's OWN offset — a zero one as the ISO "Z", which is
        // what a TIMESTAMP_NTZ always shows. Live: TZH:TZM over an LTZ under America/Los_Angeles is
        // -08:00, over a TIMESTAMP_TZ written +0530 it is +05:30, and over an NTZ it is Z.
        //
        // ★ A BARE TZM IS NOT AN ELEMENT. Live renders the format 'TZM' as the literal text TZM, so it
        // is absent from this table on purpose and falls through to the literal path; only the pair
        // (with or without the colon) and TZH alone are read. As Java offset patterns these also PARSE
        // the matching text, so an explicit-format TO_TIMESTAMP over an ISO string with an offset — or
        // with Z — works too.
        {"TZH:TZM", "XXX"}, {"TZHTZM", "XX"}, {"TZH", "X"},
        // ★ TZD IS THE STANDARD-TIME ABBREVIATION, NOT THE INSTANT'S (live-verified across eleven
        // session zones in January and July): an LTZ under America/Los_Angeles renders PST in July
        // too — never PDT, never BST, never a daylight name of any zone. A TIMESTAMP_TZ has no zone,
        // only its written offset, and spells it GMT±HH:MM — the zero offset included, GMT+00:00 —
        // while a value with no zone at all (NTZ, DATE) says the bare GMT. A session zone with no
        // English abbreviation (Etc/GMT+5) uses the spelled-offset form as well. The abbreviation
        // depends on the VALUE and the session, so the pattern carries a sentinel literal that
        // format() replaces after the formatter has run.
        {"TZD", "\uE000"},
        // ★ YYYY IS NO JAVA PATTERN: 'yyyy' signs a year past 9999 (+20201) where live prints the digits,
        // so the pattern carries a sentinel (YEAR_SENTINEL) that formatterFor builds as the era year.
        {"YYYY", "\uE001"}, {"HH24", "HH"}, {"HH12", "hh"}, {"MMMM", "MMMM"},
        {"FF9", "SSSSSSSSS"}, {"FF8", "SSSSSSSS"}, {"FF7", "SSSSSSS"}, {"FF6", "SSSSSS"},
        {"FF5", "SSSSS"}, {"FF4", "SSSS"}, {"FF3", "SSS"}, {"FF2", "SS"}, {"FF1", "S"},
        {"MON", "MMM"}, {"MMM", "MMM"},
        {"YY", "yy"}, {"MM", "MM"}, {"DD", "dd"}, {"DY", "EEE"}, {"HH", "HH"},
        {"MI", "mm"}, {"SS", "ss"}, {"FF", "SSSSSSSSS"}, {"AM", "a"}, {"PM", "a"},
        // Single-letter elements (live-verified): Y = 2-digit year ('Y' on 2020-01-15 → "20"),
        // D = day-of-month UNPADDED ('D' → "15"), H = hour UNPADDED (the H in 'MONTH' rendered "0"
        // for a DATE). These are what make 'DAY' and 'MONTH' come out as "15A20" / "JanT0".
        {"Y", "yy"}, {"D", "d"}, {"H", "H"},
    };

    private static final char TZD_SENTINEL = '\uE000';

    /** Where a YYYY element sits in a Java pattern, built as the era year by {@link #withEraYears}. */
    private static final char YEAR_SENTINEL = '\uE001';

    /**
     * A Java pattern whose YYYY elements are built as the era year \u2014 four digits at least, never signed \u2014
     * so a year past 9999 prints its digits as live prints them: {@code TO_CHAR(d, 'YYYY')} over
     * 20201-01-15 is {@code 20201} (live-verified), where the pattern letters print {@code +20201}. A
     * sentinel never sits inside a quoted literal, so each piece between two is a pattern of its own.
     */
    private static DateTimeFormatter withEraYears(final String javaPattern) {
        final DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        int from = 0;
        int at = javaPattern.indexOf(YEAR_SENTINEL);
        while (at >= 0) {
            if (at > from) {
                builder.appendPattern(javaPattern.substring(from, at));
            }
            TemporalText.appendEraYear(builder);
            from = at + 1;
            at = javaPattern.indexOf(YEAR_SENTINEL, from);
        }
        if (from < javaPattern.length()) {
            builder.appendPattern(javaPattern.substring(from));
        }
        return builder.toFormatter(Locale.US);
    }

    public static String format(final Object value, final String snowflakeFormat) {
        final String text = formatterFor(snowflakeFormat).format(toTemporal(value));
        if (text.indexOf(TZD_SENTINEL) < 0) {
            return text;
        }
        return text.replace(String.valueOf(TZD_SENTINEL), tzdText(value));
    }

    /**
     * The TZD element's text for this value: the SESSION zone's standard abbreviation for an LTZ,
     * the value's own spelled offset for a TIMESTAMP_TZ, and GMT for a value that carries no zone.
     */
    private static String tzdText(final Object value) {
        if (value instanceof OffsetDateTime) {
            return standardShortName(SessionZone.current());
        }
        if (value instanceof ZonedDateTime) {
            final ZoneId zone = ((ZonedDateTime) value).getZone();
            if (zone instanceof ZoneOffset) {
                return spelledGmtOffset((ZoneOffset) zone);
            }
            return standardShortName(zone);
        }
        return "GMT";
    }

    /**
     * The zone's English standard-time abbreviation (PST, CET, JST — whatever the instant), with the
     * spelled GMT offset for a zone that has none, and UTC for the zero-offset zone itself.
     */
    private static String standardShortName(final ZoneId zone) {
        if (zone.normalized().equals(ZoneOffset.UTC)) {
            return "UTC";
        }
        return TimeZone.getTimeZone(zone).getDisplayName(false, TimeZone.SHORT, Locale.ENGLISH);
    }

    /** The offset spelled in the localized-GMT form, sign and minutes always written: GMT+05:30. */
    private static String spelledGmtOffset(final ZoneOffset offset) {
        final int total = offset.getTotalSeconds();
        final int magnitude = Math.abs(total);
        return String.format("GMT%s%02d:%02d", total < 0 ? "-" : "+",
            magnitude / 3600, (magnitude % 3600) / 60);
    }

    // Format strings are query constants but formatterFor runs per ROW — and
    // DateTimeFormatter.ofPattern re-parses the pattern every time. DateTimeFormatter is
    // immutable and thread-safe, so a bounded shared cache is sound.
    private static final int FORMATTER_CACHE_CAPACITY = 256;
    private static final Map<String, DateTimeFormatter> FORMATTER_CACHE = new ConcurrentHashMap<>();

    /**
     * A {@link DateTimeFormatter} (US locale) built from a Snowflake format string, usable for both
     * formatting a temporal and parsing a string (TO_DATE / TO_TIMESTAMP with an explicit format).
     * Cached by the Snowflake format text.
     */
    public static DateTimeFormatter formatterFor(final String snowflakeFormat) {
        final DateTimeFormatter existing = FORMATTER_CACHE.get(snowflakeFormat);
        if (existing != null) {
            return existing;
        }
        final String javaPattern = toJavaPattern(snowflakeFormat);
        final DateTimeFormatter built = javaPattern.indexOf(YEAR_SENTINEL) < 0
            ? DateTimeFormatter.ofPattern(javaPattern, Locale.US) : withEraYears(javaPattern);
        if (FORMATTER_CACHE.size() >= FORMATTER_CACHE_CAPACITY) {
            final Iterator<String> it = FORMATTER_CACHE.keySet().iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            }
        }
        FORMATTER_CACHE.put(snowflakeFormat, built);
        return built;
    }

    /**
     * Whether the format string contains at least one recognized date/time element — used to decide
     * whether a plain string value should be interpreted as a temporal (a DATE/TIMESTAMP value can
     * reach a function as its ISO string) rather than a numeric or literal value.
     */
    public static boolean isDateFormat(final String format) {
        int i = 0;
        while (i < format.length()) {
            if (matchToken(format, i) != null) {
                return true;
            }
            i++;
        }
        return false;
    }

    private static TemporalAccessor toTemporal(final Object v) {
        // The zoned values keep their own offset for the TZH / TZM elements; a naive one is widened to
        // a ZERO offset rather than left without one, because the offset elements have to render
        // SOMETHING for it and live renders Z.
        if (v instanceof ZonedDateTime) return (ZonedDateTime) v;
        if (v instanceof OffsetDateTime) return (OffsetDateTime) v;
        if (v instanceof LocalDateTime) return ((LocalDateTime) v).atOffset(ZoneOffset.UTC);
        if (v instanceof LocalDate) return ((LocalDate) v).atStartOfDay().atOffset(ZoneOffset.UTC);
        if (v instanceof LocalTime) return (LocalTime) v;
        if (v instanceof Instant) return ((Instant) v).atOffset(ZoneOffset.UTC);
        return SharedFunctionHelpers.toLocalDateTime(v).atOffset(ZoneOffset.UTC);
    }

    /**
     * The format model scanned into its pieces: each recognised element as its canonical spelling,
     * every other character merged into literal runs. The parsing side ({@code SnowflakeDateParser})
     * reads the same pieces, so the two directions can never disagree about what an element is.
     *
     * <p>★ DOUBLE-QUOTED TEXT IS LITERAL AND THE QUOTES ARE CONSUMED (live-verified):
     * 'YYYY-MM-DD"T"HH24:MI' emits a bare T, elements never match inside a run ('"YYYY"' is the four
     * characters YYYY), an UNCLOSED run is literal to the end ('YYYY"T' emits ...T), and a DOUBLED
     * quote at top level is one literal quote ('YYYY""MM' emits ..."...) while adjacent runs just
     * concatenate ('"a""b"' is ab). A quoted run must match literally when parsing, too —
     * TO_DATE('2020T01T15', 'YYYY"T"MM"T"DD') runs on both engines.
     */
    static List<FormatPiece> pieces(final String format) {
        final List<FormatPiece> out = new ArrayList<>();
        final StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < format.length()) {
            if (format.charAt(i) == '"') {
                if (i + 1 < format.length() && format.charAt(i + 1) == '"') {
                    literal.append('"');
                    i += 2;
                    continue;
                }
                final int close = format.indexOf('"', i + 1);
                literal.append(close < 0 ? format.substring(i + 1) : format.substring(i + 1, close));
                i = close < 0 ? format.length() : close + 1;
                continue;
            }
            final String[] token = matchToken(format, i);
            if (token != null) {
                if (literal.length() > 0) {
                    out.add(FormatPiece.literal(literal.toString()));
                    literal.setLength(0);
                }
                out.add(FormatPiece.element(token[0]));
                i += token[0].length();
            } else {
                literal.append(format.charAt(i));
                i++;
            }
        }
        if (literal.length() > 0) {
            out.add(FormatPiece.literal(literal.toString()));
        }
        return out;
    }

    private static String toJavaPattern(final String format) {
        final StringBuilder out = new StringBuilder();
        final StringBuilder literal = new StringBuilder();
        for (final FormatPiece piece : pieces(format)) {
            if (!piece.isElement()) {
                literal.append(piece.literal());
                continue;
            }
            final String pattern = javaPatternFor(piece.element());
            // The TZD sentinel is literal TEXT, not a pattern letter — it joins the surrounding
            // literal run so the quoting stays a single well-formed span.
            if (pattern.length() == 1 && pattern.charAt(0) == TZD_SENTINEL) {
                literal.append(TZD_SENTINEL);
                continue;
            }
            flushLiteral(out, literal);
            out.append(pattern);
        }
        flushLiteral(out, literal);
        return out.toString();
    }

    /** The {@link DateTimeFormatter} pattern an element renders with. */
    private static String javaPatternFor(final String element) {
        for (final String[] token : TOKENS) {
            if (token[0].equals(element)) {
                return token[1];
            }
        }
        throw new IllegalArgumentException("Not a format element: " + element);
    }

    private static String[] matchToken(final String format, final int pos) {
        for (final String[] token : TOKENS) {
            if (format.regionMatches(true, pos, token[0], 0, token[0].length())) {
                return token;
            }
        }
        return null;
    }

    private static void flushLiteral(final StringBuilder out, final StringBuilder literal) {
        if (literal.length() > 0) {
            out.append('\'').append(literal.toString().replace("'", "''")).append('\'');
            literal.setLength(0);
        }
    }
}
