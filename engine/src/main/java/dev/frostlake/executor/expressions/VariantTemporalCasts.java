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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.SessionTimestampMapping;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A VARIANT converted to a DATE, a TIME, a timestamp, a number or a BOOLEAN. A DATE, a TIME or a timestamp
 * the VARIANT holds converts only within its own family — a DATE to a DATE, a TIME to a TIME, a timestamp to
 * any timestamp — and a BINARY to none of them. Any other pair, and any VARIANT a temporal conversion cannot
 * read, fails naming the variant by its JSON text (live-verified):
 *
 * <pre>
 *   TO_VARIANT(d)::TIME                     Failed to cast variant value "2024-01-15" to TIME
 *   TO_VARIANT(ts)::DATE                    Failed to cast variant value "2024-01-15 10:00:00.000" to DATE
 *   TO_VARIANT(d)::NUMBER                   Failed to cast variant value "2024-01-15" to FIXED
 *   TO_TIMESTAMP_LTZ(TO_VARIANT(d))         Failed to cast variant value "2024-01-15" to TIMESTAMP_LTZ
 *   PARSE_JSON('"abc"')::DATE               Failed to cast variant value "abc" to DATE
 *   TO_VARIANT(TO_BINARY('AB', 'HEX'))::DATE  Failed to cast variant value "AB" to DATE
 * </pre>
 */
final class VariantTemporalCasts {

    /** The one-argument conversions that read a VARIANT as a cast into their family does. */
    private static final Map<String, String> CONVERSION_TARGETS = new HashMap<String, String>();
    static {
        CONVERSION_TARGETS.put("TO_DATE", "DATE");
        CONVERSION_TARGETS.put("TO_TIME", "TIME");
        CONVERSION_TARGETS.put("TIME", "TIME");
        CONVERSION_TARGETS.put("TO_TIMESTAMP", "TIMESTAMP");
        CONVERSION_TARGETS.put("TO_TIMESTAMP_NTZ", "TIMESTAMP_NTZ");
        CONVERSION_TARGETS.put("TO_TIMESTAMP_LTZ", "TIMESTAMP_LTZ");
        CONVERSION_TARGETS.put("TO_TIMESTAMP_TZ", "TIMESTAMP_TZ");
    }

    private VariantTemporalCasts() {
    }

    /**
     * The type a conversion function names when it fails over a VARIANT.
     *
     * @param funcName the function, upper-cased
     * @return DATE, TIME or the timestamp flavour, or null for a function outside the family
     */
    static String conversionTarget(final String funcName) {
        final String target = CONVERSION_TARGETS.get(funcName);
        return "TIMESTAMP".equals(target) ? SessionTimestampMapping.current() : target;
    }

    /**
     * The type a cast out of a VARIANT names when it fails.
     *
     * @param kind          the cast target's conversion family, NUMBER, FLOAT, BOOLEAN, DATE, TIME or TIMESTAMP
     * @param writtenTarget the target as written, which picks the timestamp flavour
     * @return DATE, TIME, the timestamp flavour, FIXED, REAL or BOOLEAN, or null for a target outside those
     */
    static String castTarget(final String kind, final String writtenTarget) {
        if (kind == null) {
            return null;
        }
        switch (kind) {
            case "DATE":
            case "TIME":
            case "BOOLEAN":
                return kind;
            case "NUMBER":
                return "FIXED";
            case "FLOAT":
                return "REAL";
            case "TIMESTAMP":
                return timestampFlavour(writtenTarget);
            default:
                return null;
        }
    }

    /** Whether the target is a temporal one, whose every failure over a VARIANT is the variant's sentence. */
    static boolean isTemporal(final String target) {
        return "DATE".equals(target) || "TIME".equals(target) || target.startsWith("TIMESTAMP");
    }

    /**
     * Refuses a DATE, a TIME, a timestamp or a BINARY held in the VARIANT that the target's family does not take.
     *
     * @param value  the VARIANT's value
     * @param target the type the conversion names
     */
    static void requireReachable(final Object value, final String target) {
        final boolean reachable;
        if (value instanceof LocalDate) {
            reachable = "DATE".equals(target);
        } else if (value instanceof LocalTime) {
            reachable = "TIME".equals(target);
        } else if (SharedFunctionHelpers.isNativeTemporal(value)) {
            reachable = target.startsWith("TIMESTAMP");
        } else {
            reachable = !(value instanceof BinaryValue);
        }
        if (!reachable) {
            throw failure(value, target);
        }
    }

    /**
     * Whether a cast to text reads a VARIANT holding a date or time whose own text is not the VARIANT's: one
     * before the first year, which the VARIANT spells with its signed proleptic year on every path out of it —
     * {@code TO_VARIANT(ts)::VARCHAR}, {@code ::STRING}, {@code CAST(… AS VARCHAR(40))} alike read
     * {@code -1-01-15 10:00:00.000} where {@code ts::VARCHAR} reads {@code 0002-01-15 10:00:00.000}
     * (live-verified). Any later year reads the same either way.
     *
     * @param value  the value the VARIANT holds
     * @param source the cast operand's static type
     * @param target the cast's target type
     * @return whether the cast reads {@link #temporalText}
     */
    static boolean readsVariantTemporalText(final Object value, final DataType source, final DataType target) {
        return source instanceof VariantType && target instanceof StringType && !(target instanceof UuidType)
            && SharedFunctionHelpers.isNativeTemporal(value)
            && !SharedFunctionHelpers.variantTemporalText(value).equals(SharedFunctionHelpers.textOf(value));
    }

    /**
     * The text a conversion to text reads out of a VARIANT holding a date or time.
     *
     * @param value the date or time
     * @return its text as the VARIANT holds it
     */
    static String temporalText(final Object value) {
        return SharedFunctionHelpers.variantTemporalText(value);
    }

    /**
     * The failure, naming the VARIANT by its JSON text.
     *
     * @param value  the VARIANT's value
     * @param target the type the conversion names
     * @return the refusal
     */
    static RuntimeException failure(final Object value, final String target) {
        return new RuntimeException("Failed to cast variant value " + jsonText(value) + " to " + target);
    }

    private static String jsonText(final Object value) {
        if (value instanceof VariantValue) {
            return VariantJsonText.clientTextOf((VariantValue) value);
        }
        if (SharedFunctionHelpers.isNativeTemporal(value)) {
            return VariantJsonText.unwrappedStringText(SharedFunctionHelpers.variantTemporalText(value));
        }
        if (value instanceof BinaryValue) {
            return VariantJsonText.unwrappedStringText(((BinaryValue) value).toHex());
        }
        if (value instanceof String) {
            return VariantJsonText.unwrappedStringText((String) value);
        }
        return String.valueOf(value);
    }

    private static String timestampFlavour(final String writtenTarget) {
        final int paren = writtenTarget.indexOf('(');
        final String base = (paren > 0 ? writtenTarget.substring(0, paren) : writtenTarget)
            .trim().toUpperCase(Locale.ROOT);
        switch (base) {
            case "TIMESTAMP_LTZ":
            case "TIMESTAMPLTZ":
                return "TIMESTAMP_LTZ";
            case "TIMESTAMP_TZ":
            case "TIMESTAMPTZ":
                return "TIMESTAMP_TZ";
            case "TIMESTAMP":
                return SessionTimestampMapping.current();
            default:
                return "TIMESTAMP_NTZ";
        }
    }
}
