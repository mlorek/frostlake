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

import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantJsonFormat;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How a refusal's echo writes a block variable: as the conversion of its bound value's text to the variable's type
 * (all live-verified). A NUMBER(38,0) holding 5 is {@code TO_NUMBER('5')}, any other NUMBER carries its precision
 * and scale — {@code TO_NUMBER('1.50', 5, 2)}, a FOR counter's {@code TO_NUMBER('1', 9, 0)} — a FLOAT is {@code
 * TO_DOUBLE('1e+20')}, a BOOLEAN {@code TO_BOOLEAN('true')}, a DATE {@code TO_DATE('2024-01-01')}, a TIME and a
 * timestamp their conversions over as many fractional digits as their scale, {@code TO_TIMESTAMP_TZ} keeping its
 * offset, a BINARY {@code TO_BINARY('AB')}, an ARRAY {@code TO_ARRAY(PARSE_JSON('[\n1\n]'))} and an OBJECT
 * {@code TO_OBJECT(PARSE_JSON(…))} over the value's JSON one element to a line and unindented, a VARIANT {@code
 * TO_VARIANT(PARSE_JSON(…))} and a text its own literal. A NULL is the conversion of NULL: {@code TO_NUMBER(NULL, 5,
 * 2)}. Where the echo is the plan's — a comparison's conversion refusal — a FLOAT is the cast it stands for, {@code
 * CAST('1.5' AS FLOAT)}.
 */
final class BoundValueRendering {

    /** The spaces per level of a semi-structured value's JSON: none, though each element stands on its own line. */
    private static final int JSON_INDENT = 0;

    /** The whole number a NUMBER is bound as without its precision and scale. */
    private static final int WHOLE_PRECISION = 38;

    private BoundValueRendering() {
    }

    /**
     * The call a variable of {@code type} holding {@code value} is written out as.
     *
     * @param type    the variable's type
     * @param value   its value
     * @param planned whether the echo is the plan's, where a FLOAT is the cast it stands for
     * @return the call, or null for a type whose rendering is not known
     */
    static Expression of(final DataType type, final Object value, final boolean planned) {
        if (type instanceof NumericType) {
            return NumericType.isApproximate(type) ? floatOf(value, planned) : numberOf((NumericType) type, value);
        }
        if (type instanceof BooleanType) {
            return call("TO_BOOLEAN", value == null ? nullLiteral() : text(String.valueOf(value)));
        }
        if (type instanceof DateTimeType) {
            return temporalOf((DateTimeType) type, value);
        }
        if (type instanceof BinaryType) {
            if (value == null) {
                return call("TO_BINARY", nullLiteral());
            }
            if (value instanceof BinaryValue) {
                return call("TO_BINARY", text(((BinaryValue) value).toHex().toUpperCase(Locale.ROOT)));
            }
            return value instanceof byte[]
                ? call("TO_BINARY", text(BinaryValue.of((byte[]) value).toHex().toUpperCase(Locale.ROOT))) : null;
        }
        if (type instanceof ArrayType || type instanceof StructuredArrayType) {
            return semiStructuredOf("TO_ARRAY", value);
        }
        if (type instanceof ObjectType || type instanceof StructuredObjectType) {
            return semiStructuredOf("TO_OBJECT", value);
        }
        if (type instanceof VariantType) {
            return semiStructuredOf("TO_VARIANT", value);
        }
        if (type instanceof StringType) {
            return value == null ? null : text(value instanceof Double
                ? SharedFunctionHelpers.floatText((Double) value) : String.valueOf(value));
        }
        return null;
    }

    private static Expression numberOf(final NumericType type, final Object value) {
        final List<Expression> args = new ArrayList<Expression>();
        if (value == null) {
            args.add(nullLiteral());
        } else {
            final BigDecimal number;
            try {
                number = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
            } catch (final NumberFormatException notANumber) {
                return null;
            }
            args.add(text(number.setScale(Math.max(type.getScale(), 0), RoundingMode.HALF_UP).toPlainString()));
        }
        if (type.getPrecision() != WHOLE_PRECISION || type.getScale() != 0) {
            args.add(new LiteralExpression(Long.valueOf(type.getPrecision()), LiteralType.INTEGER));
            args.add(new LiteralExpression(Long.valueOf(type.getScale()), LiteralType.INTEGER));
        }
        return new FunctionCallExpression("TO_NUMBER", args);
    }

    private static Expression floatOf(final Object value, final boolean planned) {
        if (value == null) {
            return call("TO_DOUBLE", nullLiteral());
        }
        if (!(value instanceof Number)) {
            return null;
        }
        final LiteralExpression written = text(SharedFunctionHelpers.floatText(((Number) value).doubleValue()));
        return planned ? new CastExpression(written, "FLOAT") : call("TO_DOUBLE", written);
    }

    private static Expression temporalOf(final DateTimeType type, final Object value) {
        final String name = type.getName() == null ? "" : type.getName().toUpperCase(Locale.ROOT);
        final String function;
        if ("DATE".equals(name)) {
            function = "TO_DATE";
        } else if ("TIME".equals(name)) {
            function = "TO_TIME";
        } else if ("TIMESTAMP_LTZ".equals(name)) {
            function = "TO_TIMESTAMP_LTZ";
        } else if ("TIMESTAMP_TZ".equals(name)) {
            function = "TO_TIMESTAMP_TZ";
        } else if (name.startsWith("TIMESTAMP") || "DATETIME".equals(name)) {
            function = "TO_TIMESTAMP_NTZ";
        } else {
            return null;
        }
        if (value == null) {
            return call(function, nullLiteral());
        }
        final String written = temporalText(value, type.getPrecision(), "TO_TIMESTAMP_TZ".equals(function));
        return written == null ? null : call(function, text(written));
    }

    /** A temporal value's text over {@code scale} fractional digits, with its offset when {@code zoned}. */
    private static String temporalText(final Object value, final int scale, final boolean zoned) {
        if (value instanceof LocalDate) {
            return value.toString();
        }
        if (value instanceof LocalTime) {
            return timeText((LocalTime) value, scale);
        }
        if (value instanceof LocalDateTime) {
            final LocalDateTime at = (LocalDateTime) value;
            return at.toLocalDate() + " " + timeText(at.toLocalTime(), scale);
        }
        if (value instanceof OffsetDateTime) {
            final OffsetDateTime at = (OffsetDateTime) value;
            return at.toLocalDate() + " " + timeText(at.toLocalTime(), scale)
                + (zoned ? " " + offsetText(at.getOffset().getTotalSeconds()) : "");
        }
        if (value instanceof ZonedDateTime) {
            final ZonedDateTime at = (ZonedDateTime) value;
            return at.toLocalDate() + " " + timeText(at.toLocalTime(), scale)
                + (zoned ? " " + offsetText(at.getOffset().getTotalSeconds()) : "");
        }
        return null;
    }

    private static String timeText(final LocalTime time, final int scale) {
        final String whole = String.format(Locale.ROOT, "%02d:%02d:%02d", time.getHour(), time.getMinute(),
            time.getSecond());
        if (scale <= 0) {
            return whole;
        }
        final String nanos = String.format(Locale.ROOT, "%09d", time.getNano());
        return whole + "." + nanos.substring(0, Math.min(scale, nanos.length()));
    }

    private static String offsetText(final int totalSeconds) {
        final int minutes = Math.abs(totalSeconds) / 60;
        return String.format(Locale.ROOT, "%s%02d:%02d", totalSeconds < 0 ? "-" : "+", minutes / 60, minutes % 60);
    }

    private static Expression semiStructuredOf(final String function, final Object value) {
        if (value == null) {
            return call(function, nullLiteral());
        }
        final JsonNode node = jsonOf(value);
        return node == null ? null
            : call(function, call("PARSE_JSON", text(VariantJsonFormat.indented(node, JSON_INDENT))));
    }

    private static JsonNode jsonOf(final Object value) {
        if (value instanceof VariantValue) {
            return ((VariantValue) value).node();
        }
        if (value instanceof JsonNode) {
            return (JsonNode) value;
        }
        if (value instanceof String) {
            try {
                return ArrayFunctionHelper.MAPPER.readTree((String) value);
            } catch (final RuntimeException notJson) {
                return null;
            }
        }
        return null;
    }

    private static Expression call(final String function, final Expression argument) {
        return new FunctionCallExpression(function, Collections.<Expression>singletonList(argument));
    }

    private static LiteralExpression text(final String value) {
        return new LiteralExpression(value, LiteralType.STRING);
    }

    private static LiteralExpression nullLiteral() {
        return new LiteralExpression(null, LiteralType.NULL);
    }
}
