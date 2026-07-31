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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure value-casting helpers extracted from {@link ExpressionEvaluatorVisitor}: SQL CAST/coercion of a
 * value to a target type, NUMBER(p,s) scale/precision enforcement, integer rounding, and hex decoding.
 */
final class ValueCaster {

    private ValueCaster() {
    }

    static Object castValue(final Object value, final String targetType) {
        if (value == null) {
            return null;
        }

        String baseType = targetType.toUpperCase();
        int parenIndex = baseType.indexOf('(');
        if (parenIndex > 0) {
            baseType = baseType.substring(0, parenIndex).trim();
        }
        baseType = normalizeTypeAlias(baseType);

        final CastTargetCategory category = CastTargetCategory.fromTypeName(baseType);
        if (category == null) {
            // Temporal targets (DATE/TIME/TIMESTAMP*) convert a string to a real LocalDate/LocalTime/
            // LocalDateTime — the same value TO_DATE/TO_TIMESTAMP yields — so e.g. '2024-01-01'::TIMESTAMP_NTZ
            // compares equal to TO_TIMESTAMP_NTZ('2024-01-01'); any other unhandled type passes through.
            return SharedFunctionHelpers.toTemporalValue(baseType, value);
        }
        switch (category) {
            case INTEGER:
                // INTEGER/INT/BIGINT are aliases of NUMBER(38,0); a fractional value is rounded
                // HALF_AWAY_FROM_ZERO (2.5->3, -2.5->-3, 3.9->4) to match Snowflake, not truncated.
                return toIntegerCast(value);

            case FLOAT:
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                try {
                    return Double.parseDouble(castSourceText(value));
                } catch (final NumberFormatException notApproximate) {
                    throw numericCastFailure(value, "REAL");
                }

            case DECIMAL:
                return applyNumberScaleAndPrecision(toBigDecimalCast(value, "FIXED"), targetType);

            case STRING: {
                // A quoted JSON string (the path-extraction form for structural-looking string values)
                // unquotes to its text; temporals render in Snowflake's default output forms.
                final String quotedText = SemiStructuredCasts.quotedJsonStringText(value);
                if (quotedText != null) {
                    return quotedText;
                }
                return SharedFunctionHelpers.textOf(value);
            }

            case BOOLEAN:
                if (value instanceof Boolean) {
                    return value;
                }
                String strVal = value.toString().trim().toUpperCase();
                return strVal.equals("TRUE") || strVal.equals("1") || strVal.equals("T");

            case ARRAY:
                // TO_ARRAY semantics: an existing array passes through, any other value becomes a
                // single-element array. Doing nothing here silently stored a bare VARCHAR in an
                // ARRAY-declared column, so ARRAY_CONTAINS and friends then matched nothing.
                return SemiStructuredCasts.toArrayText(value);

            case OBJECT:
                // An existing object passes through; anything that is not object-shaped is rejected, as
                // Snowflake rejects casting a scalar to OBJECT.
                return SemiStructuredCasts.toObjectText(value);

            case BINARY:
                // Snowflake's VARCHAR-to-BINARY cast interprets the string as hex (an illegal hex
                // string is an error); an existing binary value passes through unchanged.
                if (value instanceof BinaryValue) {
                    return value;
                }
                if (value instanceof byte[]) {
                    return BinaryValue.of((byte[]) value);
                }
                return BinaryValue.fromHex(value.toString());

            default:
                return value;
        }
    }

    /**
     * Apply a NUMBER/DECIMAL cast's declared {@code (precision, scale)}: round to {@code scale} fractional
     * digits (HALF_UP, as Snowflake does) and reject a value whose integer part needs more than
     * {@code precision - scale} digits (Snowflake error 100039, "Numeric value out of range"). A bare NUMBER
     * with no parentheses is NUMBER(38,0) — the value is rounded to a whole number (e.g. 123.45 -> 123).
     */
    private static BigDecimal applyNumberScaleAndPrecision(final BigDecimal number, final String targetType) {
        final int open = targetType.indexOf('(');
        final int close = targetType.indexOf(')');
        if (open < 0 || close <= open) {
            return number.setScale(0, RoundingMode.HALF_UP);
        }
        final String[] parts = targetType.substring(open + 1, close).split(",");
        final int precision;
        final int scale;
        try {
            precision = Integer.parseInt(parts[0].trim());
            scale = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
        } catch (final NumberFormatException nfe) {
            return number;
        }
        final BigDecimal scaled = number.setScale(scale, RoundingMode.HALF_UP);
        // (precision - scale) is the maximum number of digits allowed left of the decimal point.
        if (scaled.precision() - scaled.scale() > precision - scale) {
            throw new RuntimeException("Numeric value '" + number.toPlainString()
                + "' is out of range for " + targetType.toUpperCase());
        }
        return scaled;
    }

    /**
     * Cast a numeric value (or numeric string) to a Snowflake INTEGER. Because INTEGER/INT/BIGINT are
     * aliases of NUMBER(38,0), a fractional input is rounded HALF_AWAY_FROM_ZERO — matching Snowflake
     * ({@code 2.5::INT}=3, {@code -2.5::INT}=-3, {@code 3.9::INT}=4) — rather than truncated toward
     * zero as {@code Number.longValue()} would. Parsed via {@code BigDecimal(String)} to avoid binary
     * floating-point drift, consistent with the NUMBER cast above.
     */
    private static long toIntegerCast(final Object value) {
        final BigDecimal decimal = value instanceof BigDecimal
            ? (BigDecimal) value
            : toBigDecimalCast(value, "FIXED");
        return decimal.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /**
     * The exact numeric value a cast reads off its source, or Snowflake's rejection when the source is not
     * a number. Snowflake reports the two source shapes DIFFERENTLY, live-verified on a real
     * account: a VARIANT that cannot become a number fails with {@code Failed to cast variant value {} to
     * FIXED} (SQLSTATE 22000, error 100071) — {@code REAL} for the approximate targets — while a plain
     * string fails with {@code Numeric value '{}' is not recognized} (SQLSTATE 22018, error 100038). Both
     * previously escaped as the raw JDK text ("Character { is neither a decimal digit number…"), which
     * named neither the value nor the target.
     */
    private static BigDecimal toBigDecimalCast(final Object value, final String family) {
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(castSourceText(value));
        } catch (final NumberFormatException notNumeric) {
            throw numericCastFailure(value, family);
        }
    }

    /**
     * The text a scalar cast parses from its source. A VARIANT holding a JSON STRING is read as that
     * string's CONTENT, so {@code PARSE_JSON('"42"')::NUMBER} is 42 on a real account (live-verified)
     * rather than a parse of the quoted form. Only a VARIANT is unwrapped: a plain VARCHAR
     * whose text happens to start with a quote is its own literal content.
     */
    private static String castSourceText(final Object value) {
        if (value instanceof VariantValue) {
            final String jsonStringContent = SemiStructuredCasts.quotedJsonStringText(value);
            if (jsonStringContent != null) {
                return jsonStringContent.trim();
            }
        }
        return value.toString().trim();
    }

    /** Snowflake's rejection of a source that cannot become the numeric {@code family} (FIXED or REAL). */
    private static RuntimeException numericCastFailure(final Object value, final String family) {
        if (value instanceof VariantValue) {
            return new RuntimeException("Failed to cast variant value "
                + ((VariantValue) value).text() + " to " + family);
        }
        return new RuntimeException("Numeric value '" + value + "' is not recognized");
    }

    /** Fold spelled-out type aliases onto their canonical names, so every downstream stage —
     *  the category classifier and the temporal by-name paths — sees one spelling. */
    private static String normalizeTypeAlias(final String baseType) {
        switch (baseType) {
            case "NVARCHAR": case "NCHAR": case "CHARACTER":
            case "CHARVARYING": case "CHARACTERVARYING": case "NCHARVARYING":
                return "VARCHAR";
            case "TIMESTAMPLTZ": case "TIMESTAMPWITHLOCALTIMEZONE":
                return "TIMESTAMP_LTZ";
            case "TIMESTAMPTZ":
                return "TIMESTAMP_TZ";
            default:
                return baseType;
        }
    }

}
