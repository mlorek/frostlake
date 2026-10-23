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

import dev.frostlake.executor.OverloadConversionCosts;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What an argument weighs for each overload that accepts it, as a call's arguments are read here: the weights
 * themselves, and how a call chooses by them, are {@link OverloadConversionCosts}'.
 *
 * <p>A UUID argument's row is not live-verified: it takes the overload declaring UUID first, and after that
 * weighs as the VARCHAR it reads as. Weighed as a VARCHAR from the start, it reached a VARCHAR overload over the
 * UUID one that names its type exactly.
 *
 * <p>The argument's family is its expression's STATIC type where the engine can type it: an
 * {@code ARRAY_CONSTRUCT(1)} is an ARRAY and a {@code PARSE_JSON('[1]')} a VARIANT, though both carry the
 * same value. An untyped NULL literal weighs nothing for any parameter, so the overloads it leaves alike are
 * told apart as any others that weigh alike are (live-verified over all 55 pairs of eleven families).
 */
final class UdfOverloadPreference {

    private UdfOverloadPreference() {
    }

    /**
     * Whether the argument's row names this parameter type at all.
     *
     * @param argument      the value being passed
     * @param parameterType the candidate overload's parameter type
     * @return whether the argument weighs anything for the parameter
     */
    static boolean accepts(final Object argument, final DataType parameterType) {
        return rank(argument, parameterType) != Integer.MAX_VALUE;
    }

    /**
     * What the argument's conversion to this parameter type weighs.
     *
     * @param argument      the value being passed
     * @param parameterType the candidate overload's parameter type
     * @return the weight, lower being preferred, or {@link Integer#MAX_VALUE} when the argument's row does not
     *         name the target at all
     */
    static int rank(final Object argument, final DataType parameterType) {
        return rankFamily(familyOfValue(argument), parameterType);
    }

    /**
     * What an argument family's conversion to this parameter type weighs.
     *
     * @param family        the argument's family (see {@link #familyOf}), or null for one no row names
     * @param parameterType the candidate overload's parameter type
     * @return the weight, lower being preferred, or {@link Integer#MAX_VALUE} when the family's row does not
     *         name the target at all
     */
    static int rankFamily(final String family, final DataType parameterType) {
        if (family == null || parameterType == null) {
            return Integer.MAX_VALUE;
        }
        final int weight = OverloadConversionCosts.of(family, familyOfType(parameterType));
        return weight < 0 ? Integer.MAX_VALUE : weight;
    }

    /**
     * Where a parameter type stands in the order signatures sort by, which tells overloads apart that weigh alike
     * for a call written by position.
     *
     * @param parameterType the parameter's declared type
     * @return the position, lower first
     */
    static int signatureOrder(final DataType parameterType) {
        if (parameterType == null) {
            return Integer.MAX_VALUE;
        }
        final String family = familyOfType(parameterType);
        return OverloadConversionCosts.signatureOrder(
            "TIMESTAMP".equals(family) ? timestampKind(parameterType) : family);
    }

    /** A timestamp parameter's kind, by the name the type reports; a bare TIMESTAMP is the TIMESTAMP_NTZ it maps to. */
    private static String timestampKind(final DataType type) {
        final String name = type.getName().toUpperCase(Locale.ROOT);
        if (name.startsWith("TIMESTAMP_LTZ") || name.startsWith("TIMESTAMPLTZ")) {
            return "TIMESTAMP_LTZ";
        }
        return name.startsWith("TIMESTAMP_TZ") || name.startsWith("TIMESTAMPTZ") ? "TIMESTAMP_TZ" : "TIMESTAMP_NTZ";
    }

    /**
     * The family an argument ranks the overloads as: NULL for an untyped NULL literal, its expression's static
     * type where that is known, and its runtime value's otherwise.
     *
     * @param untypedNull whether the argument is a bare NULL literal
     * @param staticType  the argument expression's static type, or null when it is not known
     * @param value       the argument's value
     * @return the family, or null for one no order names
     */
    static String familyOf(final boolean untypedNull, final DataType staticType, final Object value) {
        if (untypedNull) {
            return OverloadConversionCosts.UNTYPED_NULL;
        }
        final String declared = familyOfStaticType(staticType);
        return declared != null ? declared : familyOfValue(value);
    }

    /** The family a static argument type belongs to, or null for a type no order names. */
    private static String familyOfStaticType(final DataType type) {
        if (type instanceof VariantType) {
            return "VARIANT";
        }
        if (type instanceof ArrayType) {
            return "ARRAY";
        }
        if (type instanceof ObjectType) {
            return "OBJECT";
        }
        if (type instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (type instanceof UuidType) {
            return "UUID";
        }
        if (type instanceof StringType) {
            return "VARCHAR";
        }
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) ? "FLOAT" : NumericType.isDecfloat(type) ? "DECFLOAT" : "NUMBER";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
            if (name.startsWith("TIMESTAMP")) {
                return "TIMESTAMP";
            }
            return name.startsWith("TIME") ? "TIME" : name.startsWith("DATE") ? "DATE" : null;
        }
        return null;
    }

    /** The family a runtime value belongs to, or null for one no order names. */
    private static String familyOfValue(final Object value) {
        if (value instanceof VariantValue) {
            return "VARIANT";
        }
        if (value instanceof Double || value instanceof Float) {
            return "FLOAT";
        }
        if (value instanceof Number || value instanceof BigDecimal) {
            return "NUMBER";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof String) {
            return "VARCHAR";
        }
        if (value instanceof LocalDate) {
            return "DATE";
        }
        if (value instanceof LocalTime) {
            return "TIME";
        }
        if (value instanceof LocalDateTime || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime) {
            return "TIMESTAMP";
        }
        if (value instanceof List) {
            return "ARRAY";
        }
        if (value instanceof Map) {
            return "OBJECT";
        }
        return null;
    }

    /** The family a declared parameter type belongs to, by the name the type reports. */
    private static String familyOfType(final DataType type) {
        final String name = type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
        if (name.startsWith("TIMESTAMP")) {
            return "TIMESTAMP";
        }
        if (name.equals("DOUBLE") || name.equals("REAL") || name.equals("FLOAT4") || name.equals("FLOAT8")) {
            return "FLOAT";
        }
        if (name.equals("INT") || name.equals("INTEGER") || name.equals("BIGINT") || name.equals("SMALLINT")
                || name.equals("TINYINT") || name.equals("BYTEINT") || name.equals("DECIMAL")
                || name.equals("NUMERIC")) {
            return "NUMBER";
        }
        if (name.equals("STRING") || name.equals("TEXT") || name.equals("CHAR")) {
            return "VARCHAR";
        }
        if (type instanceof BinaryType) {
            return "BINARY";
        }
        return name;
    }
}
