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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.executor.SessionZone;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.SnowflakeDateParser;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;

/**
 * The one conversion behind TO_TIMESTAMP and its TRY_ twins, handing back the value the call's flavour
 * holds: a TIMESTAMP_NTZ is a wall clock, a TIMESTAMP_LTZ the instant the input names shown in the
 * session's zone, and a TIMESTAMP_TZ that instant at an offset it keeps. What the input IS decides the
 * instant and, for a TZ, the offset:
 *
 * <pre>
 *   input                   TIMESTAMP_LTZ                        TIMESTAMP_TZ
 *   a number (any scale)    the epoch, in the session's zone     the epoch, at the session's offset
 *   a string of digits      the epoch, in the session's zone     the epoch, at UTC
 *   text with no offset     a wall clock in the session's zone   the same, at the session's offset
 *   text with an offset     re-expressed in the session's zone   kept as written
 * </pre>
 *
 * <p>So under America/Los_Angeles {@code TO_TIMESTAMP_TZ(1579046400)} is 2020-01-14 16:00:00 -0800 and
 * {@code TO_TIMESTAMP_TZ('1579046400')} is 2020-01-15 00:00:00 Z: one instant, two offsets. A VARIANT is
 * read by what it holds — a number as a number (seconds, never unit-detected), a string as text, the JSON
 * null as NULL — and any other variant is refused. Live-verified, every cell.
 */
public final class TimestampFlavourConversion {

    private TimestampFlavourConversion() {
    }

    /**
     * @param flavour       the flavour the call resolves to: TIMESTAMP_NTZ, TIMESTAMP_LTZ or TIMESTAMP_TZ
     * @param input         the value to convert, never SQL NULL
     * @param formatOrScale a format for text, a scale for a number, or null for neither
     * @return the value in the flavour's carrier, or null for a variant's JSON null
     */
    static Object convert(final String flavour, final Object input, final Object formatOrScale) {
        final boolean localZone = "TIMESTAMP_LTZ".equals(flavour);
        final boolean writtenOffset = "TIMESTAMP_TZ".equals(flavour);
        if (!localZone && !writtenOffset) {
            final Object ntz = variantContent(input, flavour);
            if (ntz == null) {
                return null;
            }
            if (input instanceof VariantValue && ntz instanceof Number) {
                // A VARIANT number is an epoch in SECONDS whose NTZ is the instant's wall clock in the
                // SESSION's zone, where a plain NUMBER's is the UTC wall clock: under Los Angeles,
                // TO_TIMESTAMP_NTZ(PARSE_JSON('1579046400')) is 2020-01-14 16:00:00 and
                // TO_TIMESTAMP_NTZ(1579046400) is 2020-01-15 00:00:00 (live-verified).
                return SharedFunctionHelpers.parseTimestampWithFormatOrScale(ntz, formatOrScale)
                    .atZone(ZoneOffset.UTC).withZoneSameInstant(SessionZone.current()).toLocalDateTime();
            }
            return SharedFunctionHelpers.parseTimestampWithFormatOrScale(ntz, formatOrScale);
        }
        final Object value = variantContent(input, flavour);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            final ZonedDateTime inSession = SharedFunctionHelpers
                .parseTimestampWithFormatOrScale(value, formatOrScale)
                .atZone(ZoneOffset.UTC).withZoneSameInstant(SessionZone.current());
            return writtenOffset ? inSession.withFixedOffsetZone() : inSession.toOffsetDateTime();
        }
        if (formatOrScale instanceof String && SharedFunctionHelpers.isExplicitFormat((String) formatOrScale)) {
            // The model may read an offset (TZH:TZM) or a zone name (TZD): a TIMESTAMP_TZ keeps it as
            // written and an LTZ re-expresses the instant in the session's zone; a model that reads
            // none names a wall clock in the session's zone, as the no-format reading does.
            final TemporalAccessor parsed = SnowflakeDateParser.parse(value.toString(),
                (String) formatOrScale, "timestamp");
            return writtenOffset ? SharedFunctionHelpers.toWrittenOffsetTimestamp(parsed)
                : SharedFunctionHelpers.toSessionOffsetDateTime(parsed);
        }
        return writtenOffset ? SharedFunctionHelpers.toWrittenOffsetTimestamp(value)
            : SharedFunctionHelpers.toSessionOffsetDateTime(value);
    }

    /**
     * A VARIANT cast to a timestamp flavour, which reads it exactly as the flavour's conversion function
     * does: a number is an epoch in seconds, the instant in the session's zone, and a boolean or a
     * container fails the cast (live-verified).
     *
     * @param flavour TIMESTAMP_NTZ, TIMESTAMP_LTZ or TIMESTAMP_TZ
     * @param variant the value to convert
     * @return the value in the flavour's carrier, or null for the JSON null
     */
    public static Object castVariant(final String flavour, final VariantValue variant) {
        return convert(flavour, variant, null);
    }

    /**
     * What a VARIANT holds, for a conversion: its number, its string, or null for the JSON null.
     * An object, an array or a boolean is "Failed to cast variant value true to TIMESTAMP_LTZ".
     */
    private static Object variantContent(final Object input, final String flavour) {
        if (!(input instanceof VariantValue)) {
            return input;
        }
        final VariantValue variant = (VariantValue) input;
        if (variant.isJsonNull()) {
            return null;
        }
        final JsonNode node = variant.node();
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        throw new RuntimeException("Failed to cast variant value " + variant.text() + " to " + flavour);
    }
}
