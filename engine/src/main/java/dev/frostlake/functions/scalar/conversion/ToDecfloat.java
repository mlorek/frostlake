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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.DecfloatRangeException;
import dev.frostlake.values.Decfloats;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.List;

/**
 * TO_DECFLOAT(expr [, format]) — a decimal of 38 significant digits (see {@link Decfloats}). It is not TO_DOUBLE: it
 * takes a
 * BOOLEAN, where TO_DOUBLE(TRUE) is refused while the statement compiles, and words its own failures
 * (all live-verified):
 *
 * <pre>
 *   TO_DECFLOAT(TRUE)                    1           TO_DECFLOAT(FALSE)   0
 *   TO_DECFLOAT(' 1.5 ')                 1.5         the text is trimmed
 *   TO_DECFLOAT('abc')                   Numeric value 'abc' is not recognized   '', 'NaN' and 'inf' alike
 *   TO_DECFLOAT('1,234.5', '9,999.9')    1234.5      a format over a text source
 *   TO_DECFLOAT('123', '99')             Can't parse '123' as number with format '99'   the model's width
 *   TO_DECFLOAT('1e5', '9e9')            Bad input format model '9e9' for DECFLOAT: invalid numeric format
 *                                        keyword: 'e9'                                   see NumericFormatModel
 *   TO_DECFLOAT(PARSE_JSON('"1.5"'))     1.5         a VARIANT's number, boolean or numeric text
 *   TO_DECFLOAT(PARSE_JSON('[1]'))       Failed to cast variant value [1] to DECFLOAT
 *   TO_DECFLOAT(TO_VARIANT(1.5::FLOAT))  DecFloat not supported                  a VARIANT's DOUBLE
 *   TO_DECFLOAT(1e308::FLOAT * 10)       Decfloat out of representable range, operation: TO_DECFLOAT(inf)
 * </pre>
 *
 * <p>A DATE, TIME, TIMESTAMP, BINARY, OBJECT or ARRAY source is refused while the statement compiles,
 * the conversion sentence naming 'TO_DECFLOAT', and a format beside a source that is no text is too
 * many arguments; neither reaches a row.
 */
public class ToDecfloat extends BuiltInFunction {
    public ToDecfloat() { super("TO_DECFLOAT", NumericType.DECFLOAT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        if (value == null) return null;
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (value instanceof Number) {
            return representable((Number) value);
        }
        if (value instanceof VariantValue) {
            return fromVariant((VariantValue) value);
        }
        if (args.size() > 1 && args.get(1) != null) {
            final Double formatted = ToDouble.formatted(value.toString(), args.get(1).toString(),
                NumericFormatModel.DECFLOAT);
            // The model reads the text as a double; its shortest text is the decimal the text wrote.
            return formatted == null ? null : Decfloats.held(DecimalText.parse(formatted.toString()));
        }
        final BigDecimal read = parsed(value.toString());
        if (read == null) {
            // The text is echoed trimmed: TO_DECFLOAT(' ') is "Numeric value '' is not recognized".
            throw new RuntimeException(NumericRangeRefusal.unreadableText(value.toString().trim()));
        }
        return read;
    }

    /** A VARIANT's member: its number, its boolean or its numeric text; anything else is no DECFLOAT. */
    private static Object fromVariant(final VariantValue value) {
        final JsonNode node = value.node();
        if (TypedScalarNode.typedValueOf(node) == null) {
            if (node.isBoolean()) {
                return node.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
            }
            if (node.isDouble() || node.isFloat()) {
                throw new RuntimeException("DecFloat not supported");
            }
            if (node.isNumber()) {
                return Decfloats.held(node.decimalValue());
            }
            if (node.isTextual()) {
                final BigDecimal read = parsed(node.asText());
                if (read != null) {
                    return read;
                }
            }
        }
        throw new RuntimeException("Failed to cast variant value " + VariantJsonText.clientTextOf(value)
            + " to DECFLOAT");
    }

    /** A decimal text, trimmed, as a DECFLOAT holds it; null when it spells no number — NaN and inf included. */
    private static BigDecimal parsed(final String text) {
        try {
            return Decfloats.held(DecimalText.parse(text.trim()));
        } catch (final NumberFormatException | DecfloatRangeException unreadable) {
            return null;
        }
    }

    /**
     * A number as a DECFLOAT holds it; a DECFLOAT holds no infinity, so a FLOAT one is refused naming the
     * operation.
     */
    private static BigDecimal representable(final Number value) {
        if ((value instanceof Double || value instanceof Float) && Double.isInfinite(value.doubleValue())) {
            throw new RuntimeException("Decfloat out of representable range, operation: TO_DECFLOAT("
                + SharedFunctionHelpers.floatText(value.doubleValue()) + ")");
        }
        return Decfloats.of(value, "TO_DECFLOAT");
    }

    @Override
    public int getMinArgCount() { return 1; }

    @Override
    public int getMaxArgCount() { return 2; }
}
