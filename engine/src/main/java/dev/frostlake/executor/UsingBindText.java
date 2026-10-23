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

import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * What a value of a USING list is bound as. A cursor's {@code OPEN … USING (x)} and a dynamic text's
 * {@code EXECUTE IMMEDIATE … USING (x)} hand the statement the TEXT of each value, whatever its variable's type:
 * {@code SYSTEM$TYPEOF(?)} over a FLOAT holding 6.5 is {@code VARCHAR(3)}, and the statement converts the text
 * where it needs a number, a date or a boolean — so an infinity bound into {@code id < ?} is refused as
 * {@code Numeric value 'inf' is not recognized}, where {@code SELECT ?} answers {@code inf} (live-verified).
 *
 * <p>The text of each kind (all live-verified):
 * <pre>
 *   BOOLEAN          true, false
 *   NUMBER(p,s)      its digits at the declared scale: 1.50, 2.0000
 *   FLOAT            fifteen significant digits, an exponent past them or below 1e-4: 6.5, 2, 1e+20,
 *                    1.23456789012346e+17, 1.5e-07, inf, -inf, NaN
 *   DATE             2024-01-15
 *   TIME             10:20:30.000000000, at the fractional digits its type keeps: TIME(3) 10:20:30.500
 *   TIMESTAMP_NTZ    2024-01-15 10:20:30.500000000, a TIMESTAMP_LTZ alike
 *   TIMESTAMP_TZ     2024-01-15 10:20:30.000000000 +02:00
 *   BINARY           its hexadecimal digits
 *   ARRAY, OBJECT    its JSON with each member on a line of its own, nothing indented: [\n1,\n2\n] is a VARCHAR(8)
 * </pre>
 * A NULL and a VARIANT holding no container are bound as before. A statement that wants no text where the bind stands
 * refuses it in its own words: a predicate {@code Invalid data type [VARCHAR(4)] for predicate}, and a row count
 * {@code Invalid row count '?' in limit clause}.
 */
public final class UsingBindText {

    /** How many significant digits a FLOAT's text carries. */
    private static final int FLOAT_DIGITS = 15;

    /** The smallest decimal exponent written without an exponent. */
    private static final int SMALLEST_PLAIN_EXPONENT = -4;

    /** The fractional digits of a time whose type is not known: a TIME's and a TIMESTAMP's own nine. */
    private static final int FRACTION_DIGITS = 9;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);
    private static final DateTimeFormatter OFFSET = DateTimeFormatter.ofPattern("xxx", Locale.ROOT);

    private UsingBindText() {
    }

    /**
     * A USING value as what it is bound as.
     *
     * @param value    the value
     * @param declared the declared type of the variable it was read from, or null when it names none
     * @return the value's text, or the value itself where it is bound as it always was: a NULL, a semi-structured
     *         value
     */
    public static Object of(final Object value, final DataType declared) {
        final String text = textOf(value, declared);
        return text == null ? value : text;
    }

    private static String textOf(final Object value, final DataType declared) {
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "true" : "false";
        }
        if (value instanceof Number) {
            return numberText((Number) value, declared);
        }
        if (value instanceof LocalDate) {
            return value.toString();
        }
        final int digits = declared instanceof DateTimeType ? ((DateTimeType) declared).getPrecision()
            : FRACTION_DIGITS;
        if (value instanceof LocalTime) {
            return withFraction(TIME.format((LocalTime) value), ((LocalTime) value).getNano(), digits);
        }
        if (value instanceof LocalDateTime) {
            return withFraction(TIMESTAMP.format((LocalDateTime) value), ((LocalDateTime) value).getNano(), digits);
        }
        if (value instanceof OffsetDateTime) {
            final OffsetDateTime zoned = (OffsetDateTime) value;
            final String local = withFraction(TIMESTAMP.format(zoned), zoned.getNano(), digits);
            // A TIMESTAMP_LTZ is told as the session reads it, with no offset of its own.
            return declared != null && "TIMESTAMP_LTZ".equals(declared.getName()) ? local
                : local + " " + OFFSET.format(zoned);
        }
        if (value instanceof ZonedDateTime) {
            return textOf(((ZonedDateTime) value).toOffsetDateTime(), declared);
        }
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).toHex();
        }
        if (value instanceof VariantValue && ((VariantValue) value).node().isContainer()) {
            return linePerMember(((VariantValue) value).node());
        }
        return null;
    }

    /** A container's JSON as a USING list writes it: each member on a line of its own, nothing indented. */
    private static String linePerMember(final JsonNode node) {
        if (!node.isContainer() || node.size() == 0) {
            return node.toString();
        }
        final StringBuilder out = new StringBuilder(node.isArray() ? "[\n" : "{\n");
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                out.append(i > 0 ? ",\n" : "").append(linePerMember(node.get(i)));
            }
        } else {
            boolean first = true;
            for (final Map.Entry<String, JsonNode> field : node.properties()) {
                out.append(first ? "" : ",\n").append(StringNode.valueOf(field.getKey()).toString()).append(": ")
                    .append(linePerMember(field.getValue()));
                first = false;
            }
        }
        return out.append(node.isArray() ? "\n]" : "\n}").toString();
    }

    /**
     * A number's text: a FLOAT's by its magnitude, a DECFLOAT's its own ({@code 1.5e300}, not its 301 digits), an exact
     * one's digits at the scale its variable declares (live-verified).
     */
    private static String numberText(final Number value, final DataType declared) {
        if (NumericType.isDecfloat(declared)) {
            return Decfloats.text(Decfloats.of(value, "TO_DECFLOAT"));
        }
        if (value instanceof Double || value instanceof Float
                || declared instanceof NumericType && NumericType.isApproximate(declared)) {
            return floatText(value.doubleValue());
        }
        final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
        if (declared instanceof NumericType && ((NumericType) declared).getScale() >= exact.scale()) {
            return exact.setScale(((NumericType) declared).getScale()).toPlainString();
        }
        return exact.toPlainString();
    }

    /** A time's text with as many fractional digits as its type keeps, and no point when it keeps none. */
    private static String withFraction(final String seconds, final int nanos, final int digits) {
        if (digits <= 0) {
            return seconds;
        }
        final String all = String.format(Locale.ROOT, "%09d", Integer.valueOf(nanos));
        return seconds + "." + all.substring(0, Math.min(digits, all.length()));
    }

    /** A FLOAT's text: fifteen significant digits, written with an exponent where its magnitude calls for one. */
    static String floatText(final double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "inf" : "-inf";
        }
        if (value == 0) {
            return "0";
        }
        final BigDecimal rounded = new BigDecimal(value).round(new MathContext(FLOAT_DIGITS, RoundingMode.HALF_EVEN))
            .stripTrailingZeros();
        final int exponent = rounded.precision() - rounded.scale() - 1;
        if (exponent >= SMALLEST_PLAIN_EXPONENT && exponent < FLOAT_DIGITS) {
            return rounded.toPlainString();
        }
        final BigDecimal mantissa = rounded.movePointLeft(exponent);
        final int magnitude = Math.abs(exponent);
        return mantissa.toPlainString() + (exponent < 0 ? "e-" : "e+") + (magnitude < 10 ? "0" : "") + magnitude;
    }
}
