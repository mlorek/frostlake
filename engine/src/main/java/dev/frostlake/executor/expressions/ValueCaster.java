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

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.SessionInputFormats;
import dev.frostlake.executor.SessionTimestampMapping;
import dev.frostlake.executor.SignedStorageWidth;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.conversion.TimestampFlavourConversion;
import dev.frostlake.functions.scalar.conversion.ToUuid;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.DayTimeInterval;
import dev.frostlake.values.DecfloatRangeException;
import dev.frostlake.values.DecfloatTextNode;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.HexDoubleText;
import dev.frostlake.values.NonFiniteDoubles;
import dev.frostlake.values.VariantBooleans;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.YearMonthInterval;

import tools.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Set;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure value-casting helpers extracted from {@link ExpressionEvaluatorVisitor}: SQL CAST/coercion of a
 * value to a target type, NUMBER(p,s) scale/precision enforcement, integer rounding, and hex decoding.
 */
public final class ValueCaster {

    /** The widest NUMBER, which an unparameterised exact target is. */
    private static final int MAX_NUMBER_PRECISION = 38;

    private ValueCaster() {
    }

    public static Object castValue(final Object value, final String targetType) {
        return castValue(value, targetType, false);
    }

    /**
     * A cast whose source's NULLABILITY is known — the out-of-representable-range refusal spells it into
     * the type it prints, and only the caller holding the source EXPRESSION can tell a nullable column
     * from a literal. Every other caller reads as not-null, which is what live answers for one.
     */
    public static Object castValue(final Object value, final String targetType, final boolean nullableSource) {
        return castValue(value, targetType, nullableSource, 0);
    }

    /**
     * A cast whose source the plan knows no interval of — a recursive term's column of its own CTE — so that an
     * out-of-range number is tagged by the width the caller gives and printed at the target's scale: {@code (n *
     * 3)::NUMBER(2,1)} in such a term is refused as {@code FIXED[SB16](2,1)…, value 15.0} (live-verified).
     *
     * @param value           the value
     * @param targetType      the type cast to, as written
     * @param nullableSource  whether the source may be NULL
     * @param sourcePrecision the precision the source is tagged by, or 0 where the plan knows its interval
     * @return the value cast
     */
    public static Object castValue(final Object value, final String targetType, final boolean nullableSource,
                                   final int sourcePrecision) {
        if (value == null) {
            return null;
        }

        String baseType = targetType.toUpperCase(Locale.ROOT);
        final int parenIndex = baseType.indexOf('(');
        if (parenIndex > 0) {
            baseType = baseType.substring(0, parenIndex).trim();
        }
        baseType = normalizeTypeAlias(baseType);

        // Only the TEMPORAL targets need the variant unwrapped here: the numeric and string
        // ones read their source themselves, and the numeric refusal names the VARIANT's own
        // JSON text — which unwrapping first would have thrown away.
        final Object unwrapped = variantCastSource(value, baseType);
        final CastTargetCategory category = CastTargetCategory.fromTypeName(baseType);
        if ((value instanceof DayTimeInterval || value instanceof YearMonthInterval)
                && (category == CastTargetCategory.INTEGER || category == CastTargetCategory.DECIMAL)) {
            return intervalNumber(value, IntervalText.amount(value, IntervalText.ownQualifier(value)), targetType,
                category, nullableSource);
        }
        if (category == null) {
            // Temporal targets (DATE/TIME/TIMESTAMP*) convert a string to a real LocalDate/LocalTime/
            // LocalDateTime — the same value TO_DATE/TO_TIMESTAMP yields — so e.g. '2024-01-01'::TIMESTAMP_NTZ
            // compares equal to TO_TIMESTAMP_NTZ('2024-01-01'); any other unhandled type passes through.
            // A declared precision truncates the fraction of a text, temporal or VARIANT source, as a column
            // of that type does: '12:34:56.987654321'::TIMESTAMP_NTZ(2) keeps .98. A NUMBER keeps every
            // digit it carries, its scale becoming the type's precision: 2.5::TIMESTAMP_NTZ(0) is 2.5
            // seconds past the epoch, a TIMESTAMP_NTZ(1) (live-verified).
            final Object temporal = SharedFunctionHelpers.toTemporalValue(baseType, unwrapped);
            return unwrapped instanceof Number ? temporal
                : SharedFunctionHelpers.atDeclaredPrecision(temporal, declaredFractionalPrecision(targetType));
        }
        switch (category) {
            case INTEGER:
                // INTEGER/INT/BIGINT are aliases of NUMBER(38,0); a fractional value is rounded
                // HALF_AWAY_FROM_ZERO (2.5->3, -2.5->-3, 3.9->4) to match Snowflake, not truncated.
                rejectNonFinite(value, targetType, nullableSource);
                return toIntegerCast(value, nullableSource);

            case FLOAT:
                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }
                if (variantBooleanNumber(value) != null) {
                    return Double.valueOf(variantBooleanNumber(value).doubleValue());
                }
                try {
                    final Double nonFinite = NonFiniteDoubles.parseForCast(castSourceText(value));
                    if (nonFinite != null) {
                        return nonFinite;
                    }
                    return Double.parseDouble(castSourceText(value));
                } catch (final NumberFormatException notApproximate) {
                    final Double hex = HexDoubleText.withoutExponent(castSourceText(value), true);
                    if (hex != null) {
                        return hex;
                    }
                    throw numericCastFailure(value, "REAL");
                }

            case DECFLOAT:
                // A decimal of 38 significant digits, a FLOAT taken at its exact binary value (see Decfloats).
                if (value instanceof Number) {
                    return Decfloats.of((Number) value, "TO_DECFLOAT");
                }
                if (variantBooleanNumber(value) != null) {
                    return Decfloats.of(variantBooleanNumber(value), "TO_DECFLOAT");
                }
                try {
                    return Decfloats.held(toBigDecimalCast(value, "DECFLOAT"));
                } catch (final DecfloatRangeException outOfRange) {
                    // A text past DECFLOAT's range spells no number it holds: '1e20000'::DECFLOAT (live-verified).
                    throw new RuntimeException("Numeric value '" + SharedFunctionHelpers.textOf(value)
                        + "' is not recognized");
                }

            case DECIMAL:
                rejectNonFinite(value, targetType, nullableSource);
                return applyNumberScaleAndPrecision(toBigDecimalCast(value, "FIXED"), targetType,
                    value, nullableSource, sourcePrecision);

            case UUID:
                // The canonical lower-case text, and the conversion's own sentence for anything else,
                // which TRY_CAST turns into NULL (live-verified).
                return ToUuid.canonical(SharedFunctionHelpers.textOf(value));

            case STRING: {
                // A quoted JSON string (the path-extraction form for structural-looking string values)
                // unquotes to its text; temporals render in Snowflake's default output forms.
                final String quotedText = SemiStructuredCasts.quotedJsonStringText(value);
                if (quotedText != null) {
                    return quotedText;
                }
                // A container's text is its CONVERSION text — the same one TO_JSON produces, which
                // rewrites every DOUBLE and leaves the display's own rendering alone; a bare double
                // is the one value whose string conversion is its display spelling instead.
                final String convertedJson = VariantJsonText.stringConversionTextOf(value);
                if (convertedJson != null) {
                    return convertedJson;
                }
                return SharedFunctionHelpers.textOf(value);
            }

            case BOOLEAN:
                if (value instanceof Boolean) {
                    return value;
                }
                if (value instanceof VariantValue) {
                    // A variant converts only when it holds a boolean (or a boolean's spelling) — see
                    // VariantBooleans; its number is no boolean, unlike a SQL NUMBER below.
                    return VariantBooleans.convert((VariantValue) value);
                }
                // A NUMBER is true unless it is zero, at any scale: 1.00 is true (live-verified), where
                // reading its text against "1" said false.
                if (value instanceof BigDecimal) {
                    return ((BigDecimal) value).signum() != 0;
                }
                if (value instanceof Number) {
                    return ((Number) value).doubleValue() != 0.0;
                }
                // A text is read STRICTLY, as TO_BOOLEAN reads it: 'yes' / 'y' / 'on' / 't' / 'true' /
                // '1' and their negatives, any case, trimmed — and anything else, '2' and '' included,
                // is "Boolean value 'x' is not recognized" (live-verified for the cast and for the
                // conversion alike). The cast used to answer FALSE for 'yes' and for 'x'.
                return ExpressionArithmetic.strictBooleanOrNull(value.toString());

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
                // Snowflake's VARCHAR-to-BINARY cast reads the string as the session's BINARY_INPUT_FORMAT says,
                // hex unless set (a string that format refuses is an error); an existing binary value passes
                // through unchanged.
                if (value instanceof BinaryValue) {
                    return value;
                }
                if (value instanceof byte[]) {
                    return BinaryValue.of((byte[]) value);
                }
                return SessionInputFormats.binary(value.toString());

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
    /**
     * The fractional-second digits a TIME or TIMESTAMP target type declares — {@code TIMESTAMP_NTZ(3)} is 3 — or
     * 9 when it declares none or is not one of those families.
     */
    private static int declaredFractionalPrecision(final String targetType) {
        final String type = targetType.toUpperCase(Locale.ROOT);
        final int open = type.indexOf('(');
        final int close = type.indexOf(')');
        if (!type.startsWith("TIME") || open < 0 || close <= open) {
            return 9;
        }
        try {
            return Integer.parseInt(type.substring(open + 1, close).trim());
        } catch (final NumberFormatException notADigitCount) {
            return 9;
        }
    }

    private static BigDecimal applyNumberScaleAndPrecision(final BigDecimal number, final String targetType,
                                                           final Object source, final boolean nullableSource,
                                                           final int sourcePrecision) {
        final int open = targetType.indexOf('(');
        final int close = targetType.indexOf(')');
        if (open < 0 || close <= open) {
            // A bare NUMBER is NUMBER(38,0), and a value past it is refused as that one would refuse it —
            // from EVERY source, each in its own sentence: a FLOAT, a VARIANT number or a DECFLOAT of 10^38
            // used to pass unchecked, so a NUMBER(38,0) held thirty-nine digits that the next arithmetic
            // then refused.
            if (NumericRangeRefusal.exceeds(number, MAX_NUMBER_PRECISION, 0)) {
                throw new RuntimeException(rangeRefusal(number, source, MAX_NUMBER_PRECISION, 0, nullableSource,
                    sourcePrecision));
            }
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
            throw new RuntimeException(rangeRefusal(number, source, precision, scale, nullableSource,
                sourcePrecision));
        }
        return scaled;
    }

    /**
     * An interval as the exact number a cast asks for — its span in units of its type's TRAILING field, so a
     * TIMESTAMP difference casts to its seconds and {@code INTERVAL '1' DAY} to 1 — rounded half away from
     * zero to the target's scale: {@code +0 00:00:00.600000000} is 1 as a NUMBER, 0.6 as a NUMBER(10,1) and
     * 0.600000000 as a NUMBER(38,9), and minus half a second is -1 (live-verified). A value past the
     * target's digits is the interval's own sentence, naming the target's storage class and printing the
     * interval: "Interval out of representable range, type: FIXED[SB2](3,0){not null} value: +1
     * 01:00:00.000000000".
     */
    private static Object intervalNumber(final Object interval, final BigDecimal amount, final String targetType,
                                         final CastTargetCategory category, final boolean nullableSource) {
        int precision = MAX_NUMBER_PRECISION;
        int scale = 0;
        final int open = targetType.indexOf('(');
        final int close = targetType.indexOf(')');
        if (category == CastTargetCategory.DECIMAL && open >= 0 && close > open) {
            final String[] parts = targetType.substring(open + 1, close).split(",");
            try {
                precision = Integer.parseInt(parts[0].trim());
                scale = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            } catch (final NumberFormatException unparameterised) {
                precision = MAX_NUMBER_PRECISION;
                scale = 0;
            }
        }
        final BigDecimal scaled = amount.setScale(scale, RoundingMode.HALF_UP);
        if (scaled.precision() - scaled.scale() > precision - scale) {
            throw new RuntimeException("Interval out of representable range, type: FIXED["
                + SignedStorageWidth.tagOfPrecision(precision) + "](" + precision + "," + scale + "){"
                + (nullableSource ? "nullable" : "not null") + "} value: " + interval);
        }
        return category == CastTargetCategory.INTEGER ? Long.valueOf(scaled.longValueExact()) : scaled;
    }

    /**
     * A NaN or an infinity cast to an exact number, refused naming the target's width and the source's nullability
     * (see {@link NonFiniteCastRefusal}): {@code f::NUMBER(10,2)} over a nullable FLOAT column holding NaN is
     * "Number out of representable range: type FIXED[SB8](10,2){nullable}, value nan" (live-verified).
     */
    private static void rejectNonFinite(final Object value, final String targetType, final boolean nullableSource) {
        final NumericType declared = TypeInferencer.numberTargetWithParameters(targetType);
        final boolean sized = declared != null && declared.getPrecision() > 0;
        final RuntimeException nonFinite = NonFiniteCastRefusal.refusalFor(value, "FIXED",
            sized ? declared.getPrecision() : MAX_NUMBER_PRECISION, sized ? Math.max(0, declared.getScale()) : 0,
            nullableSource);
        if (nonFinite != null) {
            throw nonFinite;
        }
    }

    /**
     * Which of the three out-of-range sentences a source earns. A STRING never reaches the typed one —
     * live names the text and stops — and a VARIANT reaches a typed sentence with no type in it. An
     * APPROXIMATE source (a FLOAT) has no unscaled integer to measure, so its class comes from the
     * target's own precision rather than from the value.
     */
    private static String rangeRefusal(final BigDecimal number, final Object source, final int precision,
                                       final int scale, final boolean nullableSource, final int sourcePrecision) {
        if (source instanceof CharSequence) {
            // Two sentences, and which one depends on whether ANY number could have held the value:
            // one that overflows only this target is out of range, one that overflows them all was
            // never recognised as a number.
            return NumericRangeRefusal.pastEveryNumber(number, scale)
                ? NumericRangeRefusal.unreadableText(source)
                : NumericRangeRefusal.unconvertibleText(source);
        }
        if (source instanceof VariantValue) {
            // A double the VARIANT holds is printed as the VARIANT prints it, 1.2345678e+39 (live-verified).
            final JsonNode held = ((VariantValue) source).node();
            return held != null && held.isFloatingPointNumber() && !held.isBigDecimal()
                ? NumericRangeRefusal.untypedText(NumericRangeRefusal.variantDoubleText(held.doubleValue()))
                : NumericRangeRefusal.untyped(number);
        }
        if (source instanceof Double || source instanceof Float) {
            // A FLOAT is printed as a double, six significant digits: 1e+39, 1.23457e+39, 123457 (live-verified).
            return NumericRangeRefusal.typedDouble(SignedStorageWidth.tagOfPrecision(precision), precision, scale,
                nullableSource, number);
        }
        if (sourcePrecision > 0) {
            return NumericRangeRefusal.typedText(SignedStorageWidth.tagOfPrecision(sourcePrecision), precision, scale,
                nullableSource, number.setScale(scale, RoundingMode.HALF_UP).toPlainString());
        }
        return NumericRangeRefusal.typed(SignedStorageWidth.tagOf(number, scale), precision, scale, nullableSource,
            number);
    }

    /**
     * Cast a numeric value (or numeric string) to a Snowflake INTEGER. Because INTEGER/INT/BIGINT are
     * aliases of NUMBER(38,0), a fractional input is rounded HALF_AWAY_FROM_ZERO — matching Snowflake
     * ({@code 2.5::INT}=3, {@code -2.5::INT}=-3, {@code 3.9::INT}=4) — rather than truncated toward
     * zero as {@code Number.longValue()} would. Parsed via {@code BigDecimal(String)} to avoid binary
     * floating-point drift, consistent with the NUMBER cast above.
     *
     * <p>Being NUMBER(38,0), the cast keeps all thirty-eight digits and refuses past them exactly as a bare
     * NUMBER does: a value that fits 64 bits stays a {@code Long}, a wider one is the whole BigDecimal.
     * {@code longValue()} used to keep only the low 64 bits, so {@code 99999999999999999999::INT} was
     * 7766279631452241919 and {@code 9223372036854775808::INT} came back negative.
     */
    private static Object toIntegerCast(final Object value, final boolean nullableSource) {
        final BigDecimal decimal = value instanceof BigDecimal
            ? (BigDecimal) value
            : toBigDecimalCast(value, "FIXED");
        final BigDecimal whole = applyNumberScaleAndPrecision(decimal, "NUMBER", value, nullableSource, 0);
        return whole.unscaledValue().bitLength() < Long.SIZE ? (Object) whole.longValueExact() : whole;
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
        // A non-finite has no fixed-point form and is refused BEFORE any parse is attempted — the
        // parse would otherwise escape as the raw JDK text, which names neither the value nor a type.
        final RuntimeException nonFinite = NonFiniteCastRefusal.refusalFor(value, family);
        if (nonFinite != null) {
            throw nonFinite;
        }
        if (value instanceof Double || value instanceof Float) {
            // The EXACT binary value, never its shortest decimal spelling: (0.1::FLOAT)::NUMBER(38,20)
            // is 0.10000000000000000555 on the account and (1e37::FLOAT)::NUMBER(38,0) is
            // 9999999999999999538762658202121142272 — the digits of the double itself.
            return new BigDecimal(((Number) value).doubleValue());
        }
        if (value instanceof VariantValue) {
            final JsonNode held = ((VariantValue) value).node();
            if (held != null && held.isFloatingPointNumber() && !held.isBigDecimal()) {
                // A VARIANT's double too: PARSE_JSON('2.675e0')::NUMBER(10,2) is 2.67 and PARSE_JSON('1e38')::INT is
                // 99999999999999997748809823456034029568 (live-verified).
                return new BigDecimal(held.doubleValue());
            }
        }
        if (value instanceof Number) {
            return DecimalText.parse(value.toString());
        }
        // A SQL BOOLEAN casts to an EXACT number — TRUE::NUMBER is 1 (live-verified) — while the
        // APPROXIMATE cast of the same value is a compile-time conversion refusal, checked upstream.
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (variantBooleanNumber(value) != null) {
            return new BigDecimal(variantBooleanNumber(value).intValue());
        }
        try {
            return DecimalText.parse(castSourceText(value));
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
    /**
     * The number a VARIANT holding a JSON BOOLEAN converts to — 1 for true, 0 for false, null when the
     * value is not one. Live converts it on both numeric paths ({@code PARSE_JSON('true')::FLOAT} is 1.0
     * and {@code ::NUMBER(3,0)} is 1) though the SQL BOOLEAN itself has no such cast; the difference is
     * the VARIANT, which carries its member through the numeric conversion rather than refusing it.
     *
     * @param value the cast's source
     * @return 1 or 0, or null when the source is not a variant boolean
     */
    private static Integer variantBooleanNumber(final Object value) {
        if (!(value instanceof VariantValue)) {
            return null;
        }
        final String text = ((VariantValue) value).text();
        if ("true".equals(text)) {
            return Integer.valueOf(1);
        }
        return "false".equals(text) ? Integer.valueOf(0) : null;
    }

    private static String castSourceText(final Object value) {
        if (value instanceof VariantValue) {
            final String jsonStringContent = SemiStructuredCasts.quotedJsonStringText(value);
            if (jsonStringContent != null) {
                return jsonStringContent.trim();
            }
        }
        return value.toString().trim();
    }


    /**
     * A VARIANT source prepared for the cast it is about to feed.
     *
     * <p>A VARIANT holding a JSON STRING is its CONTENT, so {@code PARSE_JSON('"2020-01-01"')::DATE} is
     * that date and not a parse of the quoted form — the same unwrapping the numeric and string targets
     * already did, extended to the temporal ones, which is why a folded DATE column used to hand back
     * {@code "2020-01-01"} with its quotes attached.
     *
     * <p>A VARIANT holding anything else is REFUSED for the targets it cannot become:
     * {@code Failed to cast variant value 1 to DATE}. Frostlake passed the raw value through instead,
     * so a DATE-declared column answered a number and nothing marked it.
     *
     * @param value the cast's source
     * @param baseType the canonical target type name
     * @return the value the cast should read, unwrapped where the target calls for it
     */
    private static Object variantCastSource(final Object value, final String baseType) {
        if (!(value instanceof VariantValue)) {
            return value;
        }
        if (DecfloatTextNode.holds(((VariantValue) value).node())) {
            return decfloatMember((VariantValue) value, baseType);
        }
        final String jsonStringContent = SemiStructuredCasts.quotedJsonStringText(value);
        if (jsonStringContent != null) {
            return jsonStringContent;
        }
        if (VARIANT_ONLY_FROM_TEXT.contains(baseType)) {
            throw new RuntimeException("Failed to cast variant value "
                + ((VariantValue) value).text() + " to " + baseType);
        }
        final String flavour = timestampFlavour(baseType);
        if (flavour != null) {
            // A VARIANT number is an epoch in SECONDS, the instant in the session's zone, and a boolean
            // or a container fails the cast, as the TO_TIMESTAMP conversions read them (live-verified).
            return TimestampFlavourConversion.castVariant(flavour, (VariantValue) value);
        }
        return value;
    }

    /**
     * A DECFLOAT member cast out of its VARIANT: to a DECFLOAT it is the number it holds, to text its text, and a
     * NUMBER, a FLOAT or a BOOLEAN it does not become — {@code TO_VARIANT(1.5::DECFLOAT)::NUMBER(10,2)} is
     * {@code Failed to cast variant value "1.5" to FIXED}, {@code ::FLOAT} the same to REAL (live-verified).
     */
    private static Object decfloatMember(final VariantValue value, final String baseType) {
        final CastTargetCategory category = CastTargetCategory.fromTypeName(baseType);
        if (category == CastTargetCategory.DECFLOAT) {
            return DecfloatTextNode.valueOf(value.node());
        }
        final String family = category == CastTargetCategory.INTEGER || category == CastTargetCategory.DECIMAL
            ? "FIXED" : category == CastTargetCategory.FLOAT ? "REAL"
            : category == CastTargetCategory.BOOLEAN ? "BOOLEAN" : null;
        if (family != null) {
            throw new RuntimeException("Failed to cast variant value " + value.text() + " to " + family);
        }
        return value.node().asString();
    }

    /** The timestamp flavour a cast target names, the bare word by the session's mapping, or null. */
    private static String timestampFlavour(final String baseType) {
        switch (baseType) {
            case "TIMESTAMP_NTZ": case "TIMESTAMPNTZ": case "DATETIME":
                return "TIMESTAMP_NTZ";
            case "TIMESTAMP_LTZ": case "TIMESTAMPLTZ":
                return "TIMESTAMP_LTZ";
            case "TIMESTAMP_TZ": case "TIMESTAMPTZ":
                return "TIMESTAMP_TZ";
            case "TIMESTAMP":
                return SessionTimestampMapping.current();
            default:
                return null;
        }
    }

    /**
     * The targets a VARIANT reaches only from a JSON STRING — a number is not a date (live-verified).
     * TIMESTAMP is NOT here: a VARIANT number casts to one, as epoch seconds. Nor is OBJECT, whose
     * own shape test decides it and which is refused where that test lives.
     */
    private static final Set<String> VARIANT_ONLY_FROM_TEXT = Set.of("DATE", "TIME");

    /**
     * Snowflake's rejection of a source that cannot become the numeric {@code family} (FIXED or REAL). A
     * text is echoed TRIMMED, as live echoes it: {@code ' '::NUMBER} is "Numeric value '' is not
     * recognized" and {@code '  abc  '::DOUBLE} names 'abc'.
     */
    private static RuntimeException numericCastFailure(final Object value, final String family) {
        if (value instanceof VariantValue) {
            return new RuntimeException("Failed to cast variant value "
                + ((VariantValue) value).text() + " to " + family);
        }
        return new RuntimeException("Numeric value '" + castSourceText(value) + "' is not recognized");
    }

    /** Fold spelled-out type aliases onto their canonical names, so every downstream stage —
     *  the category classifier and the temporal by-name paths — sees one spelling. */
    private static String normalizeTypeAlias(final String baseType) {
        switch (baseType) {
            // CHAR is VARCHAR under another name, as its siblings are: without the fold it matched no
            // category, so 123::CHAR(2) passed the number through untouched rather than converting it.
            case "CHAR": case "NVARCHAR": case "NCHAR": case "CHARACTER":
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
