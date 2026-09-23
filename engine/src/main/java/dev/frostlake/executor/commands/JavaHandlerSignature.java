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

package dev.frostlake.executor.commands;

import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredTypes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A Java handler's parameter and return types against the routine's SQL types, judged when the body compiles at
 * CREATE, the way the account encodes values between the two (live-verified over every SQL type family and the
 * Java types it names). In this order:
 * <ol>
 *   <li>an argument type no SQL value converts to — {@code Object}, {@code char}, {@code byte}, a
 *       {@code java.time} class, a {@code HashMap}, an array other than {@code byte[]}, {@code String[]},
 *       {@code int[]} and {@code float[]} — is refused by name: {@code Unsupported argument type:
 *       java.lang.Object};</li>
 *   <li>then such a return type: {@code Unsupported return type: java.util.List};</li>
 *   <li>then each argument in order, then the return: an integral Java type for a SQL type with a scale (a
 *       NUMBER with decimals, a TIME or a timestamp with a fractional precision) cannot carry it —
 *       {@code Cannot encode number with a non-zero scale as a long} — and a pair the account does not map is
 *       refused naming Snowflake's own storage type — {@code Snowflake type FIXED[SB16](10,2){nullable} is not
 *       supported for Java return type double}, {@code ... is not supported as input to argument at index 0 with
 *       Java type double}.</li>
 * </ol>
 * Every sentence ends {@code in function <NAME> with handler <handler>}. A procedure's handler takes a Snowpark
 * {@code Session} first, which is judged before everything above — {@code Invalid first argument type for stored
 * procs: int Expected com.snowflake.snowpark.Session or com.snowflake.snowpark_java.Session} — and the parameters
 * after it are the SQL arguments, numbered from 0 without it. A pair the account fails on internally (a
 * {@code java.sql.Time} for most types, a {@code java.sql.Timestamp} for a DATE) is taken, as is any type the
 * account was not seen to judge, so the check refuses only what live refuses.
 */
final class JavaHandlerSignature {

    /** The Java types the account converts SQL values to and from, as its sentences name them. */
    private static final Map<String, String> SUPPORTED = Map.ofEntries(
        Map.entry("int", "int"), Map.entry("long", "long"), Map.entry("short", "short"),
        Map.entry("double", "double"), Map.entry("float", "float"), Map.entry("boolean", "boolean"),
        Map.entry("java.lang.Integer", "Integer"), Map.entry("java.lang.Long", "Long"),
        Map.entry("java.lang.Short", "Short"), Map.entry("java.lang.Double", "Double"),
        Map.entry("java.lang.Float", "Float"), Map.entry("java.lang.Boolean", "Boolean"),
        Map.entry("java.lang.String", "String"), Map.entry("java.math.BigDecimal", "java.math.BigDecimal"),
        Map.entry("java.math.BigInteger", "java.math.BigInteger"), Map.entry("java.sql.Date", "java.sql.Date"),
        Map.entry("java.sql.Time", "java.sql.Time"), Map.entry("java.sql.Timestamp", "java.sql.Timestamp"),
        Map.entry("[B", "byte[]"), Map.entry("[Ljava.lang.String;", "String[]"), Map.entry("[I", "int[]"),
        Map.entry("[F", "float[]"), Map.entry("java.util.Map", "java.util.Map<String, String>"),
        Map.entry("com.snowflake.snowpark_java.types.Variant", "com.snowflake.snowpark_java.types.Variant"),
        Map.entry("com.snowflake.snowpark_java.types.Geography", "com.snowflake.snowpark_java.types.Geography"));

    /** The integral types a scaled number cannot be encoded as. */
    private static final Set<String> INTEGRAL = Set.of("int", "long", "short", "Integer", "Long", "Short");

    /** How an integral type is named when a scale refuses it: a return names the primitive, an argument as written. */
    private static final Map<String, String> RETURN_NOUN = Map.of("int", "an int", "Integer", "an int",
        "long", "a long", "Long", "a long", "short", "a short", "Short", "a short",
        "java.math.BigInteger", "a BigInteger");
    private static final Map<String, String> ARGUMENT_NOUN = Map.of("int", "an int", "Integer", "an Integer",
        "long", "a long", "Long", "a Long", "short", "a short", "Short", "a Short",
        "java.math.BigInteger", "a BigInteger");

    /** What a String (and a Geography, converted through its text) carries back as a result. */
    private static final Set<ReturnTypeFamily> TEXT_RESULTS = Set.of(ReturnTypeFamily.FIXED, ReturnTypeFamily.REAL,
        ReturnTypeFamily.TEXT, ReturnTypeFamily.TIME, ReturnTypeFamily.VARIANT, ReturnTypeFamily.OBJECT,
        ReturnTypeFamily.ARRAY, ReturnTypeFamily.GEOGRAPHY);

    private JavaHandlerSignature() {
    }

    /** The Session classes a procedure's handler may take first, Scala's and Java's. */
    private static final Set<String> SESSIONS = Set.of("com.snowflake.snowpark.Session",
        "com.snowflake.snowpark_java.Session");

    /**
     * @param handler        the resolved handler method
     * @param argumentTypes  the routine's declared argument types, or null to judge none
     * @param returnType     the declared result, or null to judge none (a table result)
     * @param procedure      whether the routine is a procedure, whose handler takes a Session first
     */
    static void check(final Method handler, final List<DataType> argumentTypes, final DataType returnType,
                      final String routineName, final String handlerName, final boolean procedure) {
        final String where = " in function " + routineName + " with handler " + handlerName;
        final Class<?>[] declared = handler.getParameterTypes();
        if (procedure && declared.length > 0 && !SESSIONS.contains(declared[0].getName())) {
            throw new RuntimeException("Invalid first argument type for stored procs: " + javaName(declared[0])
                + " Expected com.snowflake.snowpark.Session or com.snowflake.snowpark_java.Session" + where);
        }
        final Class<?>[] parameters = procedure && declared.length > 0
            ? Arrays.copyOfRange(declared, 1, declared.length) : declared;
        if (argumentTypes != null) {
            for (final Class<?> parameter : parameters) {
                if (!SUPPORTED.containsKey(parameter.getName()) && !takenAsArgument(parameter)) {
                    throw new RuntimeException("Unsupported argument type: " + javaName(parameter) + where);
                }
            }
        }
        if (returnType != null && !SUPPORTED.containsKey(handler.getReturnType().getName())) {
            throw new RuntimeException("Unsupported return type: " + javaName(handler.getReturnType()) + where);
        }
        if (argumentTypes != null) {
            for (int i = 0; i < parameters.length && i < argumentTypes.size(); i++) {
                final String refusal = refusal(argumentTypes.get(i), parameters[i], true);
                if (refusal != null) {
                    throw new RuntimeException(refusal.isEmpty()
                        ? "Snowflake type " + storage(argumentTypes.get(i)) + "{nullable} is not supported as input"
                            + " to argument at index " + i + " with Java type " + SUPPORTED.get(parameters[i].getName())
                            + where
                        : refusal + where);
                }
            }
        }
        if (returnType != null) {
            final String refusal = refusal(returnType, handler.getReturnType(), false);
            if (refusal != null) {
                throw new RuntimeException(refusal.isEmpty()
                    ? "Snowflake type " + storage(returnType) + "{nullable} is not supported for Java return type "
                        + SUPPORTED.get(handler.getReturnType().getName()) + where
                    : refusal + where);
            }
        }
    }

    /** An argument type the account passes values to without judging it: a stream over a file, or a list. */
    private static boolean takenAsArgument(final Class<?> parameter) {
        return "java.io.InputStream".equals(parameter.getName()) || "java.util.List".equals(parameter.getName());
    }

    /**
     * Why a SQL type and a Java type do not pair: the scale sentence, an empty string for the not-supported one
     * (spelled by the caller, which knows the direction), or null when they pair or the pair is not judged.
     */
    private static String refusal(final DataType sqlType, final Class<?> javaType, final boolean argument) {
        final String java = SUPPORTED.get(javaType.getName());
        final ReturnTypeFamily family = sqlType == null || StructuredTypes.isStructured(sqlType) ? null
            : ReturnTypeFamily.of(sqlType);
        if (java == null || family == null || storage(sqlType) == null) {
            return null;
        }
        final boolean numeric = family == ReturnTypeFamily.FIXED || family == ReturnTypeFamily.TIME
            || family == ReturnTypeFamily.TIMESTAMP_NTZ || family == ReturnTypeFamily.TIMESTAMP_LTZ
            || family == ReturnTypeFamily.TIMESTAMP_TZ;
        final boolean timestamp = family == ReturnTypeFamily.TIMESTAMP_NTZ
            || family == ReturnTypeFamily.TIMESTAMP_LTZ || family == ReturnTypeFamily.TIMESTAMP_TZ;
        final boolean integral = INTEGRAL.contains(java);
        if ((integral || "java.math.BigInteger".equals(java)) && numeric && scaleOf(sqlType) > 0) {
            return "Cannot encode number with a non-zero scale as "
                + (argument ? ARGUMENT_NOUN : RETURN_NOUN).get(java);
        }
        return pairs(java, family, numeric, timestamp, argument) ? null : "";
    }

    private static boolean pairs(final String java, final ReturnTypeFamily family, final boolean numeric,
                                 final boolean timestamp, final boolean argument) {
        if (INTEGRAL.contains(java)) {
            return family == ReturnTypeFamily.FIXED;
        }
        switch (java) {
            case "java.math.BigInteger":
            case "java.math.BigDecimal":
                return numeric;
            case "double":
            case "float":
            case "Double":
            case "Float":
                return family == ReturnTypeFamily.REAL;
            case "boolean":
            case "Boolean":
                return family == ReturnTypeFamily.BOOLEAN;
            case "String":
            case "com.snowflake.snowpark_java.types.Geography":
                return argument || TEXT_RESULTS.contains(family);
            case "com.snowflake.snowpark_java.types.Variant":
                return family == ReturnTypeFamily.VARIANT || family == ReturnTypeFamily.GEOGRAPHY;
            case "java.util.Map<String, String>":
                return family == ReturnTypeFamily.OBJECT
                    || (argument ? family == ReturnTypeFamily.BINARY : family == ReturnTypeFamily.TIMESTAMP_TZ);
            case "String[]":
                return family == ReturnTypeFamily.ARRAY;
            case "byte[]":
                return family == ReturnTypeFamily.BINARY;
            case "java.sql.Date":
                return family == ReturnTypeFamily.DATE;
            case "java.sql.Time":
                return family != ReturnTypeFamily.REAL && family != ReturnTypeFamily.DATE;
            case "java.sql.Timestamp":
                return numeric || timestamp || family == ReturnTypeFamily.DATE;
            default:
                return false;
        }
    }

    /** The scale a SQL value is encoded with: a NUMBER's scale, a TIME's or a timestamp's precision. */
    private static int scaleOf(final DataType type) {
        if (type instanceof NumericType) {
            return ((NumericType) type).getScale();
        }
        return type instanceof DateTimeType ? ((DateTimeType) type).getPrecision() : 0;
    }

    /**
     * Snowflake's storage type for a declared type, as these sentences print it: {@code FIXED[SB16](10,2)},
     * {@code TIME[SB4](0,3)}, {@code TIMESTAMP_NTZ[SB8](0,6)}, {@code TEXT[LOB](134217728)}; null for the kinds
     * not judged. A TIME's width holds a day at its precision, a timestamp's the year 9999, and a precision of 0
     * drops the parameters.
     */
    private static String storage(final DataType type) {
        final ReturnTypeFamily family = ReturnTypeFamily.of(type);
        if (family == null) {
            return null;
        }
        switch (family) {
            case FIXED: {
                final NumericType numeric = (NumericType) type;
                return "FIXED[SB16]" + parameters(numeric.getPrecision(), numeric.getScale());
            }
            case REAL:
                return "REAL[DOUBLE](38,0)";
            case TEXT: {
                final int length = ((StringType) type).getMaxLength();
                return "TEXT[LOB](" + (length == 16777216 ? 134217728 : length) + ")";
            }
            case BINARY: {
                final int length = ((BinaryType) type).getMaxLength();
                return "BINARY[LOB](" + (length == 8388608 ? 67108864 : length) + ")";
            }
            case BOOLEAN:
                return "BOOLEAN[SB1](38,0)";
            case DATE:
                return "DATE[SB4](38,0)";
            case TIME: {
                final int precision = ((DateTimeType) type).getPrecision();
                return "TIME[" + (precision <= 4 ? "SB4" : "SB8") + "]" + parameters(0, precision);
            }
            case TIMESTAMP_NTZ:
            case TIMESTAMP_LTZ:
            case TIMESTAMP_TZ: {
                final int precision = ((DateTimeType) type).getPrecision();
                return family.name() + "[" + (precision <= 7 ? "SB8" : "SB16") + "]" + parameters(0, precision);
            }
            case VARIANT:
            case GEOGRAPHY:
                return "VARIANT[LOB](38,0)";
            case OBJECT:
                return "OBJECT[LOB](38,0)";
            case ARRAY:
                return "ARRAY[LOB](38,0)";
            default:
                return null;
        }
    }

    private static String parameters(final int precision, final int scale) {
        return precision == 0 && scale == 0 ? "" : "(" + precision + "," + scale + ")";
    }

    /** A Java type as the unsupported-type sentences name it: {@code java.lang.Integer[]}, {@code char}. */
    private static String javaName(final Class<?> type) {
        return type.isArray() ? javaName(type.getComponentType()) + "[]" : type.getName();
    }
}
