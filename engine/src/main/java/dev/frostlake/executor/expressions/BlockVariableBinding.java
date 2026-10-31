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
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * A block's variable as the block binds it into one of its own SQL expressions — a RETURN's, a LET's, an IF's
 * condition — which the expression's refusals then echo (all live-verified):
 *
 * <pre>
 *   NUMBER(10,2) holding -1.5        CAST('-1.50' AS NUMBER(10,2))
 *   INT, FLOAT, BOOLEAN              CAST('5' AS NUMBER(38,0)), CAST('1.5' AS FLOAT), CAST('true' AS BOOLEAN)
 *   DATE, TIME                       CAST('2024-01-02' AS DATE), CAST('03:04:05.000000000' AS TIME(9))
 *   TIMESTAMP_NTZ(3), TIMESTAMP_TZ   CAST('2024-01-02 03:04:05.500' AS TIMESTAMP_NTZ(3)),
 *                                    CAST('2024-01-02 03:04:05.000000000 +02:00' AS TIMESTAMP_TZ(9))
 *   BINARY                           CAST('4142' AS BINARY(67108864))
 *   VARIANT, OBJECT, ARRAY           CAST(PARSE_JSON('[\n1,\n2\n]') AS ARRAY), a member a line
 *   a text                           its literal, 'a', and a NULL the bare word, null
 *   a NULL of any other type         CAST(null AS NUMBER(10,2))
 * </pre>
 *
 * <p>An operand of an operator is the binding in parentheses, {@code (CAST('5' AS NUMBER(38,0))) + 1}, and a
 * conversion's refusal prints it as the plan converts it, {@code TO_OBJECT(TO_NUMBER('5'))}.
 */
final class BlockVariableBinding {

    /** The fractional digits a timestamp or a time spells at the most. */
    private static final int NANO_DIGITS = 9;

    private BlockVariableBinding() {
    }

    /**
     * The expression a variable is bound as.
     *
     * @param declared the variable's declared type
     * @param value    the value it holds
     * @return the binding, or null for a type the block binds otherwise
     */
    static Expression of(final DataType declared, final Object value) {
        if (declared instanceof StringType) {
            return value == null ? new LiteralExpression(null, LiteralType.NULL)
                : new LiteralExpression(String.valueOf(value), LiteralType.STRING);
        }
        if (declared == null || declared instanceof StructuredObjectType || declared instanceof StructuredArrayType
                || NumericType.isDecfloat(declared)) {
            return null;
        }
        final String type = SqlTypeNames.refusalSpelling(declared);
        if (value == null) {
            return new CastExpression(new LiteralExpression(null, LiteralType.NULL), type);
        }
        if (declared instanceof VariantType || declared instanceof ObjectType || declared instanceof ArrayType) {
            if (!(value instanceof VariantValue)) {
                return null;
            }
            final Expression parsed = new FunctionCallExpression("PARSE_JSON", Collections.<Expression>singletonList(
                new LiteralExpression(jsonLines(((VariantValue) value).node()), LiteralType.STRING)));
            return new CastExpression(parsed, type);
        }
        final String text = valueText(declared, value);
        return text == null ? null : new CastExpression(new LiteralExpression(text, LiteralType.STRING), type);
    }

    /** The text of a value the binding converts, or null for a value it writes no text of. */
    private static String valueText(final DataType declared, final Object value) {
        if (declared instanceof NumericType && !NumericType.isApproximate(declared)) {
            final BigDecimal number = value instanceof BigDecimal ? (BigDecimal) value
                : value instanceof Number ? new BigDecimal(value.toString()) : null;
            return number == null ? null
                : number.setScale(((NumericType) declared).getScale(), RoundingMode.HALF_UP).toPlainString();
        }
        if (declared instanceof NumericType) {
            if (!(value instanceof Number)) {
                return null;
            }
            final double number = ((Number) value).doubleValue();
            return Double.isNaN(number) || Double.isInfinite(number) ? null
                : String.valueOf(number).toLowerCase(Locale.ROOT);
        }
        if (declared instanceof BooleanType) {
            return value instanceof Boolean ? value.toString() : null;
        }
        if (declared instanceof BinaryType) {
            return value instanceof BinaryValue ? ((BinaryValue) value).toHex() : null;
        }
        if (declared instanceof DateTimeType) {
            return temporalText((DateTimeType) declared, value);
        }
        return null;
    }

    /** A temporal value's text at the declared precision, its offset after it for a TIMESTAMP_TZ. */
    private static String temporalText(final DateTimeType declared, final Object value) {
        final int precision = Math.min(NANO_DIGITS, Math.max(0, declared.getPrecision()));
        if (value instanceof LocalDate) {
            return value.toString();
        }
        if (value instanceof LocalTime) {
            return clockText((LocalTime) value, precision);
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime stamp = (LocalDateTime) value;
            return stamp.toLocalDate() + " " + clockText(stamp.toLocalTime(), precision);
        }
        if (value instanceof OffsetDateTime) {
            final OffsetDateTime stamp = (OffsetDateTime) value;
            return stamp.toLocalDate() + " " + clockText(stamp.toLocalTime(), precision);
        }
        if (value instanceof ZonedDateTime) {
            final ZonedDateTime stamp = (ZonedDateTime) value;
            final String offset = stamp.getOffset().getTotalSeconds() == 0 ? "+00:00" : stamp.getOffset().getId();
            return stamp.toLocalDate() + " " + clockText(stamp.toLocalTime(), precision) + " " + offset;
        }
        return null;
    }

    /** A time of day as hours, minutes and seconds, then as many fractional digits as the precision names. */
    private static String clockText(final LocalTime time, final int precision) {
        final String clock = String.format(Locale.ROOT, "%02d:%02d:%02d", time.getHour(), time.getMinute(),
            time.getSecond());
        if (precision == 0) {
            return clock;
        }
        return clock + "." + String.format(Locale.ROOT, "%09d", time.getNano()).substring(0, precision);
    }

    /** A semi-structured value's JSON as the binding writes it: each member of an object or an array a line. */
    private static String jsonLines(final JsonNode node) {
        if (node.isObject()) {
            if (node.isEmpty()) {
                return "{}";
            }
            final StringBuilder text = new StringBuilder("{");
            String separator = "\n";
            for (final Map.Entry<String, JsonNode> member : node.properties()) {
                text.append(separator).append(new StringNode(member.getKey())).append(": ")
                    .append(jsonLines(member.getValue()));
                separator = ",\n";
            }
            return text.append("\n}").toString();
        }
        if (node.isArray()) {
            if (node.isEmpty()) {
                return "[]";
            }
            final StringBuilder text = new StringBuilder("[");
            String separator = "\n";
            for (final JsonNode element : node) {
                text.append(separator).append(jsonLines(element));
                separator = ",\n";
            }
            return text.append("\n]").toString();
        }
        return node.toString();
    }
}
