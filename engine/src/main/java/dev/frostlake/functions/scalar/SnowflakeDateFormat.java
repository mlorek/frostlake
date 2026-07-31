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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Locale;

/**
 * Formats a temporal value with a subset of Snowflake's date/time format model (TO_CHAR / TO_VARCHAR
 * with a date format). Snowflake elements are translated to a {@link DateTimeFormatter} pattern:
 * YYYY/YY/Y, MMMM/MON/MM, DD/DY/D, HH24/HH12/HH/H, MI, SS, FF[1-9], AM/PM. The scan is greedy
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
        // The engine's timestamps are all NTZ (a UTC wall clock, offset zero), and Snowflake renders the
        // zero offset of the TZH:TZM pair as the ISO "Z" (captured: TO_VARCHAR(ntz,
        // 'YYYY-MM-DDTHH24:MI:SS.FFTZH:TZM') → …589000000Z). As pattern literals these also PARSE the
        // matching text, so an explicit-format TO_TIMESTAMP over an ISO string with Z works too.
        {"TZH:TZM", "'Z'"}, {"TZH", "'+00'"}, {"TZM", "'00'"},
        {"YYYY", "yyyy"}, {"HH24", "HH"}, {"HH12", "hh"}, {"MMMM", "MMMM"},
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

    public static String format(final Object value, final String snowflakeFormat) {
        return formatterFor(snowflakeFormat).format(toTemporal(value));
    }

    /**
     * A {@link DateTimeFormatter} (US locale) built from a Snowflake format string, usable for both
     * formatting a temporal and parsing a string (TO_DATE / TO_TIMESTAMP with an explicit format).
     */
    public static DateTimeFormatter formatterFor(final String snowflakeFormat) {
        return DateTimeFormatter.ofPattern(toJavaPattern(snowflakeFormat), Locale.US);
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
        if (v instanceof LocalDateTime) return (LocalDateTime) v;
        if (v instanceof LocalDate) return ((LocalDate) v).atStartOfDay();
        if (v instanceof LocalTime) return (LocalTime) v;
        if (v instanceof Instant) return LocalDateTime.ofInstant((Instant) v, ZoneOffset.UTC);
        return SharedFunctionHelpers.toLocalDateTime(v);
    }

    private static String toJavaPattern(final String format) {
        final StringBuilder out = new StringBuilder();
        final StringBuilder literal = new StringBuilder();
        int i = 0;
        while (i < format.length()) {
            final String[] token = matchToken(format, i);
            if (token != null) {
                flushLiteral(out, literal);
                out.append(token[1]);
                i += token[0].length();
            } else {
                literal.append(format.charAt(i));
                i++;
            }
        }
        flushLiteral(out, literal);
        return out.toString();
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
