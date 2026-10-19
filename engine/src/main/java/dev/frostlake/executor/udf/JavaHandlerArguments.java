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

package dev.frostlake.executor.udf;

import com.snowflake.snowpark_java.types.Variant;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Java or Scala handler's arguments: each SQL value, already of its parameter's SQL type, as the Java type the
 * handler declares for it (live-verified).
 * <ul>
 *   <li>An {@code int}, a {@code long} or a {@code short}, boxed or not, takes a whole number in its range; one
 *       outside it is refused, never wrapped: {@code Number out of representable range: type int32, value
 *       3000000000}. A {@code BigInteger} takes any whole number, a {@code BigDecimal} the number at the
 *       parameter's declared scale, a {@code double} and a {@code float} its nearest value.</li>
 *   <li>A {@code String} takes the value's text: a NUMBER at its declared scale ({@code 7.00}), a FLOAT as a
 *       FLOAT prints, a DATE as {@code 2024-03-05}, a TIME to the nanosecond with a {@code Z}, a timestamp to
 *       the millisecond with its offset where it has one, a BINARY as hex, and a VARIANT, an ARRAY or an OBJECT
 *       as its JSON.</li>
 *   <li>A {@code byte[]} takes a BINARY's bytes. A {@code java.sql.Date} takes a DATE as its midnight at UTC, a
 *       {@code java.sql.Time} a TIME as the milliseconds of its day and a {@code java.sql.Timestamp} a timestamp
 *       without a zone as its wall clock read at UTC, nanoseconds kept, and a zoned one as its instant - so
 *       {@code getTime()} is the account's whatever zone the machine is in, and the texts those classes print
 *       are the machine's zone's. A {@code Map} takes an OBJECT's members and a {@code String[]} an ARRAY's
 *       elements, a text as it is and anything else as its JSON, a JSON null and a SQL NULL element as
 *       {@code null}; a {@code Variant} a VARIANT.</li>
 *   <li>A SQL NULL is {@code null}, which a primitive type cannot hold: {@code Cannot pass a NULL value as input
 *       to a Java type of int}.</li>
 * </ul>
 * Every refusal ends {@code  in function F with handler H.f}.
 */
public final class JavaHandlerArguments {

    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS'Z'");

    private JavaHandlerArguments() {
    }

    /**
     * @param javaTypes  the handler method's parameter types
     * @param parameters the routine's parameters, or null when their SQL types are not known
     * @param arguments  the argument values
     * @param where      the end of a refusal's sentence
     * @return the values to call the handler with
     */
    public static Object[] of(final Class<?>[] javaTypes, final List<Parameter> parameters,
                              final List<Object> arguments, final String where) {
        final Object[] converted = new Object[arguments.size()];
        for (int i = 0; i < converted.length; i++) {
            final DataType sqlType = parameters != null && i < parameters.size() ? parameters.get(i).getDataType()
                : null;
            converted[i] = i < javaTypes.length ? of(arguments.get(i), javaTypes[i], sqlType, where)
                : arguments.get(i);
        }
        return converted;
    }

    /**
     * One argument as its Java type.
     *
     * @param value    the SQL value
     * @param javaType the handler's parameter type
     * @param sqlType  the parameter's declared SQL type, or null when it is not known
     * @param where    the end of a refusal's sentence
     * @return the Java value
     */
    public static Object of(final Object value, final Class<?> javaType, final DataType sqlType, final String where) {
        if (value == null || value instanceof VariantValue && ((VariantValue) value).isJsonNull()
                && javaType != Variant.class && javaType != String.class) {
            if (javaType.isPrimitive()) {
                throw new RuntimeException("Cannot pass a NULL value as input to a Java type of " + javaType.getName()
                    + where);
            }
            return null;
        }
        if (javaType == String.class) {
            return text(value, sqlType);
        }
        if (javaType == BigDecimal.class && value instanceof Number && sqlType instanceof NumericType
                && !NumericType.isApproximate(sqlType)) {
            final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
            return exact.setScale(((NumericType) sqlType).getScale(), RoundingMode.HALF_UP);
        }
        if (javaType == Map.class && value instanceof VariantValue && ((VariantValue) value).isJsonObject()) {
            final Map<String, String> members = new HashMap<>();
            for (final Map.Entry<String, JsonNode> member : ((VariantValue) value).node().properties()) {
                members.put(member.getKey(), memberText(member.getValue()));
            }
            return members;
        }
        if (javaType == String[].class && value instanceof VariantValue && ((VariantValue) value).isJsonArray()) {
            final JsonNode array = ((VariantValue) value).node();
            final String[] elements = new String[array.size()];
            for (int i = 0; i < elements.length; i++) {
                elements[i] = memberText(array.get(i));
            }
            return elements;
        }
        if (javaType == Variant.class) {
            return SnowparkVariants.ofSql(value, where);
        }
        // A number, a BINARY, a DATE, a TIME and a timestamp become their classes as a procedure's arguments do.
        return HandlerArguments.convert(javaType, value, where);
    }

    /** A value as the text a {@code String} parameter holds. */
    private static String text(final Object value, final DataType sqlType) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof VariantValue) {
            return ((VariantValue) value).text();
        }
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).toHex();
        }
        if (value instanceof LocalTime) {
            return TIME_TEXT.format((LocalTime) value);
        }
        if (value instanceof LocalDate || value instanceof LocalDateTime || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime) {
            return SharedFunctionHelpers.variantTemporalText(value);
        }
        if (value instanceof Double || value instanceof Float) {
            return SharedFunctionHelpers.floatText(((Number) value).doubleValue());
        }
        if (value instanceof Number && sqlType instanceof NumericType && !NumericType.isApproximate(sqlType)) {
            final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
            return exact.setScale(((NumericType) sqlType).getScale(), RoundingMode.HALF_UP).toPlainString();
        }
        return String.valueOf(value);
    }

    /** A member of an OBJECT or an element of an ARRAY as a {@code Map}'s value or a {@code String[]}'s element. */
    private static String memberText(final JsonNode member) {
        if (member == null || member.isNull()) {
            return null;
        }
        return member.isString() ? member.stringValue() : VariantText.canonical(member);
    }
}
