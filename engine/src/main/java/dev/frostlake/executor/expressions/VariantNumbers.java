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

import dev.frostlake.executor.NumericConversionException;
import dev.frostlake.values.VariantValue;

import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * A VARIANT read as a number wherever a number is needed: its JSON number, its JSON string when that
 * spells one, and a JSON boolean as 1 / 0. Anything else — an object, an array, a string that spells
 * no number — fails the variant CAST, naming the member as JSON and the numeric family the use asked
 * for: {@code Failed to cast variant value {"x":1} to REAL} for an operator or a numeric function and
 * the double-valued aggregates, {@code … to FIXED} for the integer ones (live-verified across + - * / %,
 * the unary sign, ABS / ROUND / FLOOR / SQRT / MOD and their family, SUM / AVG / MEDIAN / BITOR_AGG).
 */
public final class VariantNumbers {

    /** The target of a double-valued use. */
    public static final String REAL = "REAL";

    /** The target of an integer-valued use. */
    public static final String FIXED = "FIXED";

    private VariantNumbers() {
    }

    /**
     * The number a VARIANT member converts to, or null for a JSON null.
     *
     * @param variant the value
     * @param target  REAL or FIXED, named by the refusal
     * @return its number
     */
    public static Number numberOf(final VariantValue variant, final String target) {
        final JsonNode node = variant.node();
        if (node == null || node.isNull()) {
            return null;
        }
        final Number member = ExpressionArithmetic.variantAsNumber(variant);
        if (member != null) {
            return member;
        }
        if (node.isBoolean()) {
            return node.booleanValue() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        throw refusal(node, target);
    }

    /**
     * The refusal for a member that reads as no number.
     *
     * @param node   the member
     * @param target REAL or FIXED
     * @return the exception to throw
     */
    public static NumericConversionException refusal(final JsonNode node, final String target) {
        return new NumericConversionException("Failed to cast variant value " + node.toString() + " to " + target);
    }
}
