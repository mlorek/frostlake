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

package dev.frostlake.functions.aggregate;

import dev.frostlake.executor.NumericConversionException;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * One value on its way INTO a numeric aggregate, converted the way live converts it — which for text
 * that is not a number means a refusal, not a zero.
 *
 * <pre>
 *   SUM / AVG / MEDIAN / PERCENTILE_CONT / PERCENTILE_DISC / STDDEV / VARIANCE over 'a'
 *       Numeric value 'a' is not recognized
 *   SUM over a VARIANT holding "a"
 *       Failed to cast variant value "a" to REAL
 * </pre>
 *
 * <p>★ NEITHER SENTENCE CARRIES A COMPILATION PREFIX, which is the tell that this is the CONVERSION
 * failing as the rows are read rather than the plan being rejected. It follows that an empty group is
 * never refused, and that a value the column merely COULD hold is not enough — the rule is per value.
 *
 * <p>★ THE VALUE NAMED IS THE FIRST IN SCAN ORDER, even for the aggregates that sort. Over a column
 * holding 'z' then 'a' every one of them names 'z'; written the other way round they all name 'a'. So
 * the conversion happens as the rows arrive, before any ordering, which is why this is called from the
 * accumulating loop and not from a comparator.
 *
 * <p>★ THE CONVERSION ITSELF IS THE ORDINARY ONE. Surrounding spaces are trimmed and an exponent is
 * read, so {@code ' 2 '} and {@code '1e2'} are 2 and 100; an EMPTY string and a lone sign are not
 * numbers and are refused like any other text.
 *
 * <p>★ WHAT IS DELIBERATELY NOT REFUSED HERE: a BOOLEAN or a DATE argument, which live rejects at
 * COMPILE time in the function's own vocabulary — a different surface, and giving it this sentence
 * would be inventing one. Inside a VARIANT a boolean IS converted, to 1 and 0.
 *
 * <p>MIN, MAX, COUNT, MODE and ARRAY_AGG never come here: they hand back an input rather than
 * computing over it, and live answers all five over text.
 */
public final class NumericAggregateInput {

    private NumericAggregateInput() {
    }

    /**
     * One aggregated value as a double, refusing text that is not a number.
     *
     * @param value the value, never null
     * @return its double value
     */
    public static double asDouble(final Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof VariantValue) {
            return variantDouble((VariantValue) value);
        }
        if (value instanceof CharSequence) {
            final String text = value.toString();
            try {
                return new BigDecimal(text.trim()).doubleValue();
            } catch (final NumberFormatException notNumeric) {
                throw new NumericConversionException(
                    "Numeric value '" + text + "' is not recognized");
            }
        }
        // Everything else is a family live refuses before a row is read; left on the old reading so a
        // compile-time refusal is not replaced by a row-time one that live never says.
        try {
            return new BigDecimal(String.valueOf(value).trim()).doubleValue();
        } catch (final NumberFormatException notNumeric) {
            return 0.0;
        }
    }

    /**
     * Read a value for its side effect only — the refusal. PERCENTILE_DISC hands back one of its
     * inputs unconverted, but live still refuses a set it cannot order numerically, so every value is
     * put through the conversion in scan order and the result thrown away.
     *
     * @param value the value, never null
     */
    public static void requireNumeric(final Object value) {
        asDouble(value);
    }

    /**
     * The double a VARIANT member converts to. A JSON number is its own value and a JSON boolean is 1
     * or 0; a JSON string is read as a number when it can be, and everything else is refused naming
     * the member as JSON — a string keeps its quotes there, an object its braces.
     */
    private static double variantDouble(final VariantValue value) {
        final JsonNode node = value.node();
        if (node.isNumber()) {
            return node.decimalValue().doubleValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? 1.0 : 0.0;
        }
        if (node.isTextual()) {
            try {
                return new BigDecimal(node.asText().trim()).doubleValue();
            } catch (final NumberFormatException notNumeric) {
                throw failedCast(node);
            }
        }
        throw failedCast(node);
    }

    private static NumericConversionException failedCast(final JsonNode node) {
        return new NumericConversionException(
            "Failed to cast variant value " + node.toString() + " to REAL");
    }
}
