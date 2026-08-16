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

import dev.frostlake.types.DataType;
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
        final List<String> order = ORDERS.get(familyOfValue(argument));
        if (order == null || parameterType == null) {
            return Integer.MAX_VALUE;
        }
        final int at = order.indexOf(familyOfType(parameterType));
        return at < 0 ? Integer.MAX_VALUE : at;
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
        return name;
    }
}
