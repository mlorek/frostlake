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

package dev.frostlake.executor;

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BinaryWidthSpelling;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.NonFiniteDoubles;
import dev.frostlake.values.VariantValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * An argument of a SQL function as the text its body reads where it names the parameter.
 *
 * <p>A SQL function's body is run with each parameter's name written out as its argument, so the text has to
 * answer the very value the caller passed, of the very type — whatever the session's output formats say, however
 * many digits the value carries, whatever it holds. A FLOAT is therefore the cast of its shortest exact spelling
 * and never a bare number, which would compute as a NUMBER; a TIME and a timestamp keep all nine digits and their
 * offset; a BINARY is a hex literal; a semi-structured value is the SQL that makes it again, its members' types
 * included; and a negative number stands in parentheses, so that what the body writes around the name applies to
 * the whole of it.
 *
 * <p>Inside a scalar function a parameter is more than its argument (live-verified): a NULL is the NULL of the
 * parameter's type; an argument of another family arrives converted; and an exact number takes the parameter's
 * scale only when its own is smaller, else it stays the number it is — {@code 1} into a NUMBER(10,2) is
 * {@code 1.00} of that type, {@code 1.005} stays a NUMBER(4,3) and {@code 7} into a NUMBER a NUMBER(1,0). A table
 * function takes only arguments of its parameters' own types and reads each as it is.
 */
public final class SqlArgumentText {

    private static final int MAX_PRECISION = 38;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS");

    private static final DateTimeFormatter NTZ = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");

    private static final DateTimeFormatter ZONED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS xxx");

    private static final String ZONED_FORMAT = "YYYY-MM-DD HH24:MI:SS.FF9 TZH:TZM";

    private SqlArgumentText() {
    }

    /**
     * A value as a literal of its own type.
     *
     * @param value the value
     * @return SQL text that answers the value
     */
    public static String ofValue(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Number) {
            return number((Number) value);
        }
        if (value instanceof VariantValue) {
            return VariantSqlText.of((VariantValue) value);
        }
        if (value instanceof BinaryValue) {
            // A hex literal is as wide as its bytes; the value it stands for is a BINARY of no such width.
            return "X'" + ((BinaryValue) value).toHex() + "'::BINARY";
        }
        final String temporal = SharedFunctionHelpers.temporalSqlLiteral(value);
        return temporal != null ? temporal : SqlStringLiterals.encode(value.toString());
    }

    /**
     * A table function's argument as its parameter holds it: the argument as it is, a VARIANT parameter's as
     * the VARIANT of it.
     *
     * @param value    the argument
     * @param declared the parameter's declared type
     * @return SQL text that answers the parameter's value
     */
    public static String forTableParameter(final Object value, final DataType declared) {
        return forTableParameter(value, declared, null);
    }

    /**
     * {@link #forTableParameter(Object, DataType)}, a BINARY argument keeping the width its own type declares:
     * {@code X'AB'} is a BINARY(1) to the body, where {@code TO_BINARY('AB', 'HEX')} is a BINARY of none
     * (live-verified).
     *
     * @param value    the argument
     * @param declared the parameter's declared type
     * @param written  the argument's own static type, or null where it is not known
     * @return SQL text that answers the parameter's value
     */
    public static String forTableParameter(final Object value, final DataType declared, final DataType written) {
        final String binary = sizedBinary(value, written);
        if (binary != null) {
            return binary;
        }
        if (value != null && declared != null && declared.getCategory() == TypeCategory.SEMI_STRUCTURED
                && !(value instanceof VariantValue) && !(value instanceof String)) {
            return "TO_VARIANT(" + ofValue(value, written) + ")";
        }
        if (NumericType.isDecfloat(declared) && value instanceof Number) {
            // Read as the DECFLOAT it is, as a scalar function's parameter is (see forParameter): its plain digits
            // would be a NUMBER, or a FLOAT where they pass 38.
            return decfloat((Number) value);
        }
        if (declared instanceof DateTimeType && temporal(value) != null) {
            // The argument's digits are the parameter's, or it would not have been taken.
            return temporal(value) + "::" + SqlTypeNames.canonical(declared);
        }
        return ofValue(value);
    }

    /**
     * A scalar function's argument as its parameter holds it.
     *
     * @param value     the argument, converted to the parameter's family already
     * @param declared  the parameter's declared type
     * @param converted whether the value is one the call converted, and not the caller's own
     * @return SQL text that answers the parameter's value
     */
    public static String forParameter(final Object value, final DataType declared, final boolean converted) {
        return forParameter(value, declared, converted, null);
    }

    /**
     * {@link #forParameter(Object, DataType, boolean)}, an exact number taking its parameter's type from the
     * argument's own declared type rather than from the digits it holds: {@code 7::NUMBER(10,0)} into a NUMBER is a
     * NUMBER(10,0), and a NUMBER(10,0) column into a NUMBER(10,2) a NUMBER(12,2) (live-verified); a BINARY keeps
     * the width its type declares.
     *
     * @param value     the argument, converted to the parameter's family already
     * @param declared  the parameter's declared type
     * @param converted whether the value is one the call converted, and not the caller's own
     * @param written   the argument's own static type, or null where it is not known
     * @return SQL text that answers the parameter's value
     */
    public static String forParameter(final Object value, final DataType declared, final boolean converted,
                                      final DataType written) {
        if (declared == null) {
            return ofValue(value);
        }
        if (!converted && declared instanceof BinaryType) {
            final String binary = sizedBinary(value, written);
            if (binary != null) {
                return binary;
            }
        }
        if (!converted && value instanceof Number && !NonFiniteDoubles.isNonFinite((Number) value)
                && declared instanceof NumericType && !NumericType.isApproximate(declared)
                && !NumericType.isDecfloat(declared) && written instanceof NumericType
                && !NumericType.isApproximate(written) && !NumericType.isDecfloat(written)
                && ((NumericType) written).getPrecision() > 0) {
            final String typed = typedNumber((Number) value, (NumericType) declared, (NumericType) written);
            if (typed != null) {
                return typed;
            }
        }
        if (value == null) {
            final String typed = nullTypeOf(declared);
            return typed == null ? "NULL" : "NULL::" + typed;
        }
        if (declared.getCategory() == TypeCategory.SEMI_STRUCTURED) {
            if (value instanceof VariantValue) {
                return VariantSqlText.of((VariantValue) value);
            }
            // A text is the JSON the caller's value was carried as; anything else is the VARIANT of itself.
            return value instanceof String ? "PARSE_JSON(" + SqlStringLiterals.encode((String) value) + ")"
                : "TO_VARIANT(" + ofValue(value, converted ? null : written) + ")";
        }
        if (NumericType.isDecfloat(declared) && value instanceof Number) {
            // A DECFLOAT parameter is read as the DECFLOAT it holds, every digit kept: x + 1 over a DECFLOAT third is
            // 1.3333333333333333333333333333333333333 (live-verified).
            return decfloat((Number) value);
        }
        if (declared instanceof NumericType && value instanceof Number
                && !NonFiniteDoubles.isNonFinite((Number) value)) {
            final NumericType numeric = (NumericType) declared;
            if (NumericType.isApproximate(numeric)) {
                return number(Double.valueOf(((Number) value).doubleValue()));
            }
            return exactNumber((Number) value, numeric, converted);
        }
        if (declared instanceof DateTimeType) {
            final String temporal = temporal(value);
            if (temporal != null) {
                // The declared precision is the parameter's: a TIMESTAMP_NTZ(3) reads three digits.
                return temporal + "::" + SqlTypeNames.canonical(declared);
            }
        }
        if (declared instanceof StringType && value instanceof String && converted) {
            // A text made from a number, a date or a boolean is a text of no width, where the caller's own
            // text keeps its length.
            return SqlStringLiterals.encode((String) value) + "::VARCHAR";
        }
        return ofValue(value);
    }

    /**
     * {@link #ofValue(Object)}, a number of a DECFLOAT static type written as that DECFLOAT: a runtime DECFLOAT is a
     * decimal like a NUMBER's, so its type is known from the argument's static type alone. The VARIANT of it is
     * then a DECFLOAT member, every digit kept, as {@code TO_VARIANT(1.5::DECFLOAT)} is (see DecfloatTextNode),
     * where its plain digits would make a DECIMAL, or a DOUBLE past 38 of them.
     */
    private static String ofValue(final Object value, final DataType written) {
        if (value instanceof Number && NumericType.isDecfloat(written)) {
            return decfloat((Number) value);
        }
        return ofValue(value);
    }

    /** A number as the DECFLOAT literal of its every digit. */
    private static String decfloat(final Number value) {
        return SqlStringLiterals.encode(Decfloats.text(Decfloats.of(value, "TO_DECFLOAT"))) + "::DECFLOAT";
    }

    /**
     * A temporal argument as SQL text no session input format reads otherwise: its conversion with the format
     * spelled out, so a parameter keeps its value under {@code DATE_INPUT_FORMAT = 'DD/MM/YYYY'} (live-verified); a
     * year a four-digit format cannot spell keeps the plain literal.
     */
    private static String temporal(final Object value) {
        if (value instanceof LocalDate && fourDigitYear(((LocalDate) value).getYear())) {
            return "TO_DATE('" + value + "', 'YYYY-MM-DD')";
        }
        if (value instanceof LocalTime) {
            return "TO_TIME('" + TIME.format((LocalTime) value) + "', 'HH24:MI:SS.FF9')";
        }
        if (value instanceof LocalDateTime && fourDigitYear(((LocalDateTime) value).getYear())) {
            return "TO_TIMESTAMP_NTZ('" + NTZ.format((LocalDateTime) value) + "', 'YYYY-MM-DD HH24:MI:SS.FF9')";
        }
        if (value instanceof OffsetDateTime && fourDigitYear(((OffsetDateTime) value).getYear())) {
            return "TO_TIMESTAMP_LTZ('" + ZONED.format((OffsetDateTime) value) + "', '" + ZONED_FORMAT + "')";
        }
        if (value instanceof ZonedDateTime && fourDigitYear(((ZonedDateTime) value).getYear())) {
            return "TO_TIMESTAMP_TZ('" + ZONED.format((ZonedDateTime) value) + "', '" + ZONED_FORMAT + "')";
        }
        return SharedFunctionHelpers.temporalSqlLiteral(value);
    }

    private static boolean fourDigitYear(final int year) {
        return year >= 0 && year <= 9999;
    }

    /** The type a NULL argument is a NULL of, or null where the bare word is all there is to say. */
    private static String nullTypeOf(final DataType declared) {
        if (declared instanceof StringType) {
            // A text parameter's width is never enforced, and its NULL has none.
            return "VARCHAR";
        }
        if (declared instanceof BinaryType) {
            return "BINARY";
        }
        if (declared instanceof NumericType || declared instanceof BooleanType || declared instanceof DateTimeType) {
            return SqlTypeNames.canonical(declared);
        }
        if (declared.getClass() == VariantType.class || declared.getClass() == ArrayType.class
                || declared.getClass() == ObjectType.class) {
            return SqlTypeNames.canonical(declared);
        }
        return null;
    }

    /**
     * An exact number under an exact parameter: the caller's own number when its scale is the parameter's or
     * larger, else — and for a value converted from a text or a FLOAT — the number at the parameter's scale, wide
     * enough for its own whole digits.
     */
    private static String exactNumber(final Number value, final NumericType declared, final boolean converted) {
        final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value
            : value instanceof BigInteger ? new BigDecimal((BigInteger) value)
            : value instanceof Double || value instanceof Float ? BigDecimal.valueOf(value.doubleValue())
            : BigDecimal.valueOf(value.longValue());
        final boolean ownNumber = !converted && !(value instanceof Double || value instanceof Float);
        if (ownNumber && exact.scale() >= declared.getScale()) {
            return number(value);
        }
        final int wholeDigits = Math.max(declared.getPrecision() - declared.getScale(),
            Math.max(0, exact.precision() - exact.scale()));
        final int precision = Math.min(MAX_PRECISION, wholeDigits + declared.getScale());
        return signed(exact.toPlainString()) + "::NUMBER(" + precision + "," + declared.getScale() + ")";
    }

    /**
     * An exact argument under an exact parameter, typed by the argument's declared type: that type where its scale is
     * the parameter's or larger, else the parameter's scale over the wider of the two whole parts; null for a value
     * holding more digits than its type declares, which its digits type instead.
     */
    private static String typedNumber(final Number value, final NumericType declared, final NumericType written) {
        if (value instanceof Double || value instanceof Float) {
            return null;
        }
        final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value
            : value instanceof BigInteger ? new BigDecimal((BigInteger) value) : BigDecimal.valueOf(value.longValue());
        final int scale = Math.max(written.getScale(), declared.getScale());
        if (exact.scale() > scale) {
            return null;
        }
        final int ownScale = Math.max(exact.scale(), 0);
        final int ownPrecision = Math.max(1, exact.precision() - exact.scale() + ownScale);
        if (written.getScale() >= declared.getScale() && written.getScale() == ownScale
                && written.getPrecision() == ownPrecision) {
            // A type the digits give themselves, a literal's: the number as it is (see exactNumber).
            return null;
        }
        final int precision = written.getScale() >= declared.getScale() ? written.getPrecision()
            : Math.min(MAX_PRECISION, Math.max(declared.getPrecision() - declared.getScale(),
                written.getPrecision() - written.getScale()) + declared.getScale());
        return signed(exact.setScale(scale).toPlainString()) + "::NUMBER(" + precision + "," + scale + ")";
    }

    /** A BINARY argument as a literal of the width its own type declares; null where it declares none. */
    private static String sizedBinary(final Object value, final DataType written) {
        if (!(value instanceof BinaryValue) || !(written instanceof BinaryType)
                || ((BinaryType) written).getWidthSpelling() != BinaryWidthSpelling.DECLARED) {
            return null;
        }
        return "X'" + ((BinaryValue) value).toHex() + "'::BINARY(" + ((BinaryType) written).getMaxLength() + ")";
    }

    private static String number(final Number value) {
        final String nonFinite = NonFiniteDoubles.castOfWord(value);
        if (nonFinite != null) {
            return nonFinite;
        }
        if (value instanceof Double || value instanceof Float) {
            return signed(Double.toString(value.doubleValue())) + "::FLOAT";
        }
        return signed(value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : value.toString());
    }

    /** A negative number in parentheses: {@code 2-x} must not read {@code 2--5}, the start of a comment. */
    private static String signed(final String digits) {
        return digits.startsWith("-") ? "(" + digits + ")" : digits;
    }
}
