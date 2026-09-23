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
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;

/**
 * A conversion the plan carries out while the statement compiles, standing where it was written as the constant
 * it folds to: {@code TO_NUMBER('1')} is the NUMBER(38,0) constant 1, {@code '1.5'::FLOAT} the FLOAT 1.5, and a
 * TRY conversion that fails the NULL of its target. It is typed by the conversion's target, not by its digits, and
 * prints as its value — a number without trailing zeros, a FLOAT with its decimal point, a NULL as {@code null}.
 * Only the folded form of a constant argument holds one ({@link ConstantRootFold}).
 */
final class FoldedConstantExpression extends LiteralExpression {

    private final DataType declaredType;

    /**
     * @param value        the folded value: a BigDecimal for an exact number, a Double for a FLOAT, or null
     * @param declaredType the conversion's target type
     */
    FoldedConstantExpression(final Object value, final DataType declaredType) {
        super(value, LiteralType.DECIMAL);
        this.declaredType = declaredType;
    }

    /** @return the conversion's target type */
    DataType getDeclaredType() {
        return declaredType;
    }

    /** @return whether the target is a FLOAT */
    boolean isApproximate() {
        return NumericType.isApproximate(declaredType);
    }

    /** @return the value as an exact number, or null for a NULL or a FLOAT */
    BigDecimal exactValue() {
        return getValue() instanceof BigDecimal ? (BigDecimal) getValue() : null;
    }

    /** @return the value as the plan prints it */
    String printedValue() {
        final Object value = getValue();
        if (value == null) {
            return "null";
        }
        if (value instanceof BigDecimal) {
            final BigDecimal number = (BigDecimal) value;
            return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    @Override
    public String toString() {
        return printedValue();
    }
}
