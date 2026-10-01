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

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which overload an argument prefers when SEVERAL of them accept it. The account does not pick the
 * nearest type: measured over pairs of one-arity overloads, an argument has an ORDER of its own, and
 * the same target sits differently in different orders - a FLOAT prefers VARCHAR to NUMBER, while a
 * VARCHAR prefers FLOAT to NUMBER, so NUMBER loses both times.
 *
 * <pre>
 *   argument     preference, best first
 *   NUMBER       NUMBER, FLOAT, BOOLEAN, VARIANT, VARCHAR, TIMESTAMP
 *   FLOAT        FLOAT, BOOLEAN, VARIANT, VARCHAR, NUMBER
 *   VARCHAR      VARCHAR, FLOAT, NUMBER, BOOLEAN, VARIANT
 *   BOOLEAN      BOOLEAN, VARCHAR, VARIANT, FLOAT, NUMBER
 *   DATE         DATE, TIMESTAMP, VARCHAR
 *   VARIANT      VARIANT, ARRAY, OBJECT, VARCHAR
 *   ARRAY        ARRAY, VARIANT, OBJECT, VARCHAR
 *   OBJECT       OBJECT, VARIANT, ARRAY, VARCHAR
 * </pre>
 *
 * <p>Every row is live-verified over overload PAIRS. A target an argument's row does not name keeps
 * the order the overloads were declared in, which is what this engine did for every pair before.
 *
 * <p>The argument's family is its expression's STATIC type where the engine can type it: an
 * {@code ARRAY_CONSTRUCT(1)} is an ARRAY and a {@code PARSE_JSON('[1]')} a VARIANT, though both carry the
 * same value. An untyped NULL literal ranks every family in one order of its own, whatever order the
 * overloads were declared in: ARRAY, BINARY, BOOLEAN, DATE, NUMBER, OBJECT, FLOAT, VARCHAR, TIME,
 * TIMESTAMP, VARIANT - the account's internal type names in alphabetical order (live-verified over all 55
 * pairs of those eleven).
 */
final class UdfOverloadPreference {

    /** How far down its argument's order a target sits; a target the order does not name is last. */
    private static final Map<String, List<String>> ORDERS = new LinkedHashMap<>();

    static {
        ORDERS.put("NUMBER", Arrays.asList("NUMBER", "FLOAT", "BOOLEAN", "VARIANT", "VARCHAR", "TIMESTAMP"));
        ORDERS.put("FLOAT", Arrays.asList("FLOAT", "BOOLEAN", "VARIANT", "VARCHAR", "NUMBER"));
        ORDERS.put("VARCHAR", Arrays.asList("VARCHAR", "FLOAT", "NUMBER", "BOOLEAN", "VARIANT"));
        ORDERS.put("BOOLEAN", Arrays.asList("BOOLEAN", "VARCHAR", "VARIANT", "FLOAT", "NUMBER"));
        ORDERS.put("DATE", Arrays.asList("DATE", "TIMESTAMP", "VARCHAR"));
        ORDERS.put("TIME", Arrays.asList("TIME", "VARCHAR"));
        ORDERS.put("TIMESTAMP", Arrays.asList("TIMESTAMP", "DATE", "VARCHAR"));
        ORDERS.put("VARIANT", Arrays.asList("VARIANT", "ARRAY", "OBJECT", "BOOLEAN", "VARCHAR",
            "NUMBER", "FLOAT"));
        ORDERS.put("ARRAY", Arrays.asList("ARRAY", "VARIANT", "OBJECT", "VARCHAR"));
        ORDERS.put("OBJECT", Arrays.asList("OBJECT", "VARIANT", "ARRAY", "VARCHAR"));
        ORDERS.put("NULL", Arrays.asList("ARRAY", "BINARY", "BOOLEAN", "DATE", "NUMBER", "OBJECT", "FLOAT",
            "VARCHAR", "TIME", "TIMESTAMP", "VARIANT"));
    }

    private UdfOverloadPreference() {
    }

    /**
     * Where this parameter type sits in the argument's own order.
     *
     * @param argument      the value being passed
     * @param parameterType the candidate overload's parameter type
     * @return the position, lower being preferred, or {@link Integer#MAX_VALUE} when the order does not
     *         name the target at all
     */
    static boolean accepts(final Object argument, final DataType parameterType) {
        return rank(argument, parameterType) != Integer.MAX_VALUE;
    }

    /**
     * Where this parameter type sits in the argument's own order.
     *
     * @param argument      the value being passed
     * @param parameterType the candidate overload's parameter type
     * @return the position, lower being preferred, or {@link Integer#MAX_VALUE} when the order does not
     *         name the target at all
     */
    static int rank(final Object argument, final DataType parameterType) {
        return rankFamily(familyOfValue(argument), parameterType);
    }

    /**
     * Where this parameter type sits in an argument family's own order.
     *
     * @param family        the argument's family (see {@link #familyOf}), or null for one no order names
     * @param parameterType the candidate overload's parameter type
     * @return the position, lower being preferred, or {@link Integer#MAX_VALUE} when the order does not
     *         name the target at all
     */
    static int rankFamily(final String family, final DataType parameterType) {
        final List<String> order = family == null ? null : ORDERS.get(family);
        if (order == null || parameterType == null) {
            return Integer.MAX_VALUE;
        }
        final int at = order.indexOf(familyOfType(parameterType));
        return at < 0 ? Integer.MAX_VALUE : at;
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
            return "NULL";
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
        if (type instanceof StringType) {
            return "VARCHAR";
        }
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) ? "FLOAT" : "NUMBER";
        }
        if (type instanceof DateTimeType) {
            final String name = type.getName() == null ? "" : type.getName().toUpperCase();
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
        final String name = type.getName() == null ? "" : type.getName().toUpperCase();
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
