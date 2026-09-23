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

import dev.frostlake.types.NumericType;
import dev.frostlake.values.ValueRange;

/**
 * A SUM the account's plan rewrites over a shifted column — {@code SUM(c + k)}, {@code SUM(k + c)} or
 * {@code SUM(c - k)}: the column as the relation it is read from declares it, its type and the interval its
 * statistics hold, and the constant it is shifted by, as written. A merged derived relation's column standing for
 * such a shift is one too, its column read in the relation beneath ({@link MergedDerivedColumn}).
 */
final class SumShift {

    private final NumericType columnType;
    private final ValueRange columnRange;
    private final Expression constant;
    private final BinaryOperator operator;
    private final boolean columnLeft;

    /**
     * @param columnType  the column's declared type
     * @param columnRange the interval the column's statistics hold
     * @param constant    the constant, as written
     * @param operator    + or -
     * @param columnLeft  whether the column is the left operand
     */
    SumShift(final NumericType columnType, final ValueRange columnRange, final Expression constant,
             final BinaryOperator operator, final boolean columnLeft) {
        this.columnType = columnType;
        this.columnRange = columnRange;
        this.constant = constant;
        this.operator = operator;
        this.columnLeft = columnLeft;
    }

    /** @return the column's declared type */
    NumericType getColumnType() {
        return columnType;
    }

    /** @return the interval the column's statistics hold */
    ValueRange getColumnRange() {
        return columnRange;
    }

    /** @return the constant, as written */
    Expression getConstant() {
        return constant;
    }

    /** @return + or - */
    BinaryOperator getOperator() {
        return operator;
    }

    /** @return whether the column is the left operand */
    boolean isColumnLeft() {
        return columnLeft;
    }
}
